package com.nikitatomilov.photogallery.foobar

import com.nikitatomilov.photogallery.dao.TimestampSource
import com.nikitatomilov.photogallery.foobar.DuplicateFinder.ImageInfo
import com.nikitatomilov.photogallery.service.FileMetadataExtractorService
import com.nikitatomilov.photogallery.util.isMediaFile
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

class PerYearMoverExifChanger {

  val sourceFolder = File("/run/user/1000/gvfs/smb-share:server=192.168.123.1,share=mirror6tb/PhotosPhoneImmichSrc")
  val targetFolder =
      File("/run/user/1000/gvfs/smb-share:server=192.168.123.1,share=mirror6tb/PhotosFixedTimestamps")
  val targetUncertainFolder =
      File("/run/user/1000/gvfs/smb-share:server=192.168.123.1,share=mirror6tb/PhotosNoTimestamps")

  val service = FileMetadataExtractorService()

  @Test
  fun foo() {
    val sourceFiles = ArrayList<File>()
    sourceFolder.walk().forEach {
      if (it.isFile && it.isMediaFile()) {
        sourceFiles.add(it)
        if (sourceFiles.size % 1000 == 0) {
          println("Found ${sourceFiles.size} files and growing")
        }
      }
    }

    println("Found ${sourceFiles.size} files")

    val trustedInformationSets = setOf(TimestampSource.FILE_METADATA, TimestampSource.FILENAME)
    sourceFiles.mapIndexed { index, it ->
      var (timestamp, source) = service.extractTimestamp(it)
      var trustedInfo: Boolean = true

      if (!trustedInformationSets.contains(source)) {
        trustedInfo = false
        val exifInfo = service.extractTimestampExifTool(it)

        val i = 1
        if (exifInfo != null && exifInfo < timestamp) {
          timestamp = exifInfo
          trustedInfo = true
          println("Recovered timestamp from exiftool for ${it.absolutePath}: ${Instant.ofEpochMilli(timestamp)}")
        }
      }

      val targetFile: File
      if (!trustedInfo) {
        println("Skipped ${it.absolutePath}")
        targetFile = File(targetUncertainFolder, it.name)
      } else {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val targetFolder = File(targetFolder, date.year.toString())
        targetFolder.mkdirs()

        targetFile = File(targetFolder, it.name)
      }

      if (targetFile.exists()) {
        println("Target file ${targetFile.absolutePath} already exists")
        val f = it.delete()
        if (!f) {
          println("Failed to delete ${it.absolutePath}")
        }
      } else {
        val f = it.renameTo(targetFile)
        if (!f) {
          println("Failed to move ${it.absolutePath} to ${targetFile.absolutePath}")
        }
      }

      if (index % 1000 == 0) {
        println("Processed $index/${sourceFiles.size} files")
      }
    }

    val i = 1
  }
}