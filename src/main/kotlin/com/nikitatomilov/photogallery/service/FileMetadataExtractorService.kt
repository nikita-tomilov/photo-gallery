package com.nikitatomilov.photogallery.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.nikitatomilov.photogallery.dao.FilesystemMediaEntity
import com.nikitatomilov.photogallery.dao.TimestampSource
import com.nikitatomilov.photogallery.util.*
import mu.KLogging
import org.mp4parser.IsoFile
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Service
class FileMetadataExtractorService {

  private fun valid(timestamp: Long): Boolean {
    val upperThreshold = System.currentTimeMillis()
    return (timestamp > lowerThreshold) && (timestamp < upperThreshold)
  }

  fun extractTimestamp(file: File): Pair<Long, TimestampSource> {
    val timestampFromFilename = parseFilename(file)
    if (timestampFromFilename != null) {
      return timestampFromFilename to TimestampSource.FILENAME
    }

    val timestampFromFileMetadata =
        if (file.isPhoto()) parsePhotoExif(file) else parseVideoMetadata(file)
    if (timestampFromFileMetadata != null) {
      return timestampFromFileMetadata to TimestampSource.FILE_METADATA
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

  private fun parseFilename(file: File): Long? {
    val name = file.nameWithoutExtension
    if (name.removeNonDigits() == name) {
      val number = name.toLong()
      if (valid(number)) {
        return number
      }
    }
    filenameRegexes.forEach { (r, f) ->
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
      val earliestExifDate = exifDates.mapNotNull {
        try {
          it.toInstant().toEpochMilli()
        } catch (e: Exception) {
          null
        }
      }.minOrNull()
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