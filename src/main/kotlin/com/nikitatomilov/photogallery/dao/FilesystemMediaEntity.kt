package com.nikitatomilov.photogallery.dao

import com.nikitatomilov.photogallery.util.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import org.mp4parser.IsoFile

data class FilesystemMediaEntity(
  val rootFile: File,
  val file: File,
  var date: Long = Long.MAX_VALUE,
  var dateFoundInMetadata: Boolean = false,
  var dateFoundInFilename: Boolean = false
) {
  override fun toString(): String {
    var from = "fs"
    if (dateFoundInMetadata) from = "metadata"
    if (dateFoundInFilename) from = "fname"
    return relativePath() + "         " + Instant.ofEpochMilli(date) + " $from"
  }

  fun correctDateFound() = this.dateFoundInMetadata || this.dateFoundInFilename

  fun parseMetadata(): FilesystemMediaEntity {
    date = file.lastModified()
    if (file.isPhoto()) parsePhotoMetadata()
    if (file.isVideo()) parseVideoMetadata()
    if (!dateFoundInMetadata) {
      parseFilename()
      if (!dateFoundInFilename) {
        tryExtractYearFromFullFilename()
        parseCommonFileMetadata()
      }
    }
    return this
  }

  private fun parseFilename() {
    val name = file.nameWithoutExtension
    if (name.removeNonDigits() == name) {
      val number = name.toLong()
      if ((number > lowerThreshold) && (number < System.currentTimeMillis())) {
        date = nullableMin(date, number)
        dateFoundInFilename = true
        return
      }
    }
    filenameRegexes.forEach { (r, f) ->
      if (r.containsMatchIn(name)) {
        val match = r.find(name)!!.value
        val parsed = f(match)
        if (parsed > lowerThreshold) {
          date = nullableMin(date, parsed)
          dateFoundInFilename = true
          return@forEach
        }
      }
    }
  }

  private fun tryExtractYearFromFullFilename() {
    val fullPath = file.absolutePath
    val r = Regex("\\d{4}")
    val matches = r.findAll(fullPath)
    matches.forEach {
      try {
        val s = it.value
        val year = s.toInt()
        val nowYear = ZonedDateTime.now(ZoneOffset.UTC).year
        if ((year >= 2000) && (year <= nowYear)) {
          val assumption = LocalDate.of(year, 1, 1)
          date = nullableMin(date, assumption.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        }
      } catch (e: Exception) {
        val i = 1
      }
    }
  }

  private fun parseCommonFileMetadata() {
    try {
      val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
      val minTime = listOf(
          attr.creationTime(), attr.lastAccessTime(), attr.lastModifiedTime()
      ).minOfOrNull { it.toInstant().toEpochMilli() }
      date = nullableMin(date, minTime)
    } catch (ex: IOException) {
      val i = 1
      //nothing
    }
  }

  private fun parsePhotoMetadata() {
    val meta = ImageMetadataReader.readMetadata(file) //https://github.com/drewnoakes/metadata-extractor/
    val exifDates = listOf(
        ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
        ExifSubIFDDirectory.TAG_DATETIME,
    ).mapNotNull { meta.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)?.getDate(it) } + listOf(
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
    if (earliestExifDate != null) {
      date = nullableMin(date, earliestExifDate)
      dateFoundInMetadata = true
    }
  }

  private fun parseVideoMetadata() {
    try {
      val isoFile = IsoFile(this.file)
      val moov = isoFile.movieBox
      val movieHeaderBox = moov.movieHeaderBox
      val creationDate = movieHeaderBox.creationTime.toInstant().toEpochMilli()
      if (creationDate > 0) {
        date = nullableMin(date, creationDate)
        dateFoundInMetadata = true
      }
    } catch (e: Exception) {
      val i = 1
    }
  }

  private fun relativePath() = "." + file.absolutePath.replace(rootFile.absolutePath, "")
}