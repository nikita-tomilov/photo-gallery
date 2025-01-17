package com.nikitatomilov.photogallery.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.nikitatomilov.photogallery.dao.TimestampSource
import com.nikitatomilov.photogallery.util.*
import mu.KLogging
import org.mp4parser.IsoFile
import org.springframework.stereotype.Service
import java.awt.Dimension
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.time.*
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi

@Service
class FileMetadataExtractorService {

  private fun valid(timestamp: Long): Boolean {
    val upperThreshold = System.currentTimeMillis()
    return (timestamp > lowerThreshold) && (timestamp < upperThreshold)
  }

  fun extractCameraInfo(file: File): String {
    if (file.isVideo()) return "unknown-video"
    val meta =
        ImageMetadataReader.readMetadata(file)
    val make = meta.directories.map { d -> d.getString(ExifSubIFDDirectory.TAG_MAKE) }
        .firstOrNull { it != null }
    val model = meta.directories.map { d -> d.getString(ExifSubIFDDirectory.TAG_MODEL) }
        .firstOrNull { it != null }

    val software = meta.directories.map { d -> d.getString(ExifSubIFDDirectory.TAG_SOFTWARE) }
        .firstOrNull { it != null }

    var ans = "unknown-camera"
    if (model != null) {
      if (make != null) {
        ans = if (model.contains(make)) {
          model
        } else {
          "$make $model"
        }
      }
    }
    if (software != null) {
      ans = "$ans ($software)"
      if (make == null && model == null) {
        val i = 1
      }
    }
    return ans
  }

  fun extractDimension(file: File): Dimension {
    ImageIO.createImageInputStream(file)
        .use { stream ->  // accepts File, InputStream, RandomAccessFile
          if (stream != null) {
            val iioRegistry = IIORegistry.getDefaultInstance()
            val iter =
                iioRegistry.getServiceProviders(
                    ImageReaderSpi::class.java, true)
            while (iter.hasNext()) {
              val readerSpi = iter.next()
              if (readerSpi.canDecodeInput(stream)) {
                val reader: ImageReader = readerSpi.createReaderInstance()
                try {
                  reader.setInput(stream)
                  val width: Int = reader.getWidth(reader.getMinIndex())
                  val height: Int = reader.getHeight(reader.getMinIndex())
                  return Dimension(width, height)
                } finally {
                  reader.dispose()
                }
              }
            }
            return Dimension(-1, -2)
          } else {
            throw IllegalArgumentException("Can't open stream for this image")
          }
        }
  }

  val exifFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ssXXX")

  fun extractTimestampExifTool(file: File): Long? {
    val command = "exiftool '${file.absolutePath}' | grep Date"
    val proc = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", command))
    val out = proc.inputStream.bufferedReader().readText()
    proc.waitFor()
    val lines = out.split("\n")
    val dates = lines.filter { it.isNotBlank() }.map { it.split(": ")[1] }
    val datesParsed = dates.mapNotNull {
      try {
        exifFormat.parse(it)
      } catch (e: Exception) {
        null
      }
    }
    return datesParsed.map { it.toInstant().toEpochMilli() }.minOrNull()
  }

  fun extractTimestamp(file: File): Pair<Long, TimestampSource> {
    val timestampFromFilename = parseFilename(file)
    val timestampFromMetadata = extractTimestampFromMetadata(file)

    if (timestampFromMetadata != null) {
      /*if (timestampFromFilename != null) {
        val fromFname = LocalDate.ofInstant(Instant.ofEpochMilli(timestampFromFilename), ZoneOffset.UTC)
        val fromMeta = LocalDate.ofInstant(Instant.ofEpochMilli(timestampFromMetadata), ZoneOffset.UTC)
        if (fromFname == fromMeta) {
          return timestampFromMetadata to TimestampSource.FILE_METADATA
        }
      }*/
      return timestampFromMetadata to TimestampSource.FILE_METADATA
    }

    if (timestampFromFilename != null) {
      return timestampFromFilename to TimestampSource.FILENAME
    }

    val timestampWithYearOnly = tryExtractYearFromFullFilename(file)
    if (timestampWithYearOnly != null) {
      return timestampWithYearOnly to TimestampSource.PATH
    }

    val timestampFromFilesystemMetadata = parseCommonFileMetadata(file)
    if (timestampFromFilesystemMetadata != null) {
      return timestampFromFilesystemMetadata to TimestampSource.FILESYSTEM_METADATA
    }

    return file.lastModified() to TimestampSource.FILESYSTEM_METADATA
  }

  fun extractTimestampFromMetadata(file: File): Long? {
    return if (file.isPhoto()) parsePhotoExif(file) else parseVideoMetadata(file)
  }

  private fun parseFilename(file: File): Long? {
    val name = file.nameWithoutExtension
    if (name.removeNonDigits() == name) {
      val number = name.toLong()
      if (valid(number)) {
        return number
      }
    }

    //for when 190916
    //is 19 Sep 2016 and not (as would a normal person assume) 16 Sep 2019
    //this file has to be present near this "broken" file
    val dateMarker = File(file.parentFile, "date-invert-marker.txt")
    val regexes = if (dateMarker.exists()) {
      filenameRegexesInverted
    } else {
      filenameRegexes
    }

    regexes.forEach { (r, f) ->
      if (r.containsMatchIn(name)) {
        val match = r.find(name)!!.value
        val parsedByRegex = f(match)
        if (valid(parsedByRegex)) {
          return parsedByRegex
        }
      }
    }
    return null
  }

  private fun tryExtractYearFromFullFilename(file: File): Long? {
    val fullPath = file.absolutePath
    val r = Regex("\\d{4}")
    val matches = r.findAll(fullPath)
    matches.forEach {
      try {
        val s = it.value
        val year = s.toInt()
        val nowYear = ZonedDateTime.now(ZoneOffset.UTC).year
        if ((year >= 2000) && (year <= nowYear)) {
          val assumption = LocalDate.of(year, 12, 31)
          return assumption.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }
      } catch (e: Exception) {
        val i = 1
      }
    }
    return null
  }

  private fun parseCommonFileMetadata(file: File): Long? {
    try {
      val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
      return listOf(
          attr.creationTime(), attr.lastAccessTime(), attr.lastModifiedTime()
      ).minOfOrNull { it.toInstant().toEpochMilli() }
    } catch (ex: IOException) {
      val i = 1
      //nothing
    }
    return null
  }

  @Suppress("UnnecessaryVariable")
  private fun parsePhotoExif(file: File): Long? {
    try {
      val meta =
          ImageMetadataReader.readMetadata(file) //https://github.com/drewnoakes/metadata-extractor/
      val exifDates = listOf(
          ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
          ExifSubIFDDirectory.TAG_DATETIME,
      ).mapNotNull {
        meta.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            ?.getDate(it)
      } + listOf(
          ExifIFD0Directory.TAG_DATETIME_ORIGINAL,
          ExifIFD0Directory.TAG_DATETIME,
      ).mapNotNull { meta.getFirstDirectoryOfType(ExifIFD0Directory::class.java)?.getDate(it) }
      val filteredDates = exifDates.mapNotNull {
        try {
          val date = it.toInstant().toEpochMilli()
          val dateUtc = Instant.ofEpochMilli(date).atOffset(ZoneOffset.UTC)
          if (dateUtc.year == 2000 && dateUtc.month == Month.JANUARY && dateUtc.dayOfMonth == 1) {
            null
          } else {
            date
          }
        } catch (e: Exception) {
          null
        }
      }
      val earliestExifDate = filteredDates.minOrNull()
      return earliestExifDate
    } catch (e: Exception) {
      logger.error(e) { "Error on file $file" }
      return null
    }
  }

  private fun parseVideoMetadata(file: File): Long? {
    try {
      val isoFile = IsoFile(file)
      val moov = isoFile.movieBox
      val movieHeaderBox = moov.movieHeaderBox
      val creationDate = movieHeaderBox.creationTime.toInstant().toEpochMilli()
      if (valid(creationDate)) {
        return creationDate
      }
    } catch (e: Exception) {
      val i = 1
    }
    return null
  }

  companion object : KLogging()
}