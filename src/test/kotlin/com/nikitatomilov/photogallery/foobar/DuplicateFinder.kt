package com.nikitatomilov.photogallery.foobar

import com.nikitatomilov.photogallery.dao.TimestampSource
import com.nikitatomilov.photogallery.service.FileMetadataExtractorService
import com.nikitatomilov.photogallery.util.isMediaFile
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import kotlin.math.min

class DuplicateFinder {

  val immichFolder = File("/media/hotaro/SanDisk500/immich/admin")
  val immichFilesList =
      File("/media/hotaro/SanDisk500/immich-to-be-uploaded/immich-files-list.txt")

  val googlePhotosFolder =
      File("/home/hotaro/Downloads/photos-for-immich-migration/takeout-2022-07-16")
  val googlePhotosFilesList =
      File("/media/hotaro/SanDisk500/immich-to-be-uploaded/google-photos-files-list.txt")

  data class ImageInfo(
    val fullPath: String,
    val size: Long,
    val camera: String,
    val timestamp: Long,
    val timestampSource: TimestampSource,
    val w: Int,
    val h: Int,
    val date: Instant = Instant.ofEpochMilli(timestamp)
  ) {

    fun getFileName(): String {
      return File(fullPath).name
    }

    fun toLine(): String {
      return "$fullPath¿$size¿$camera¿$timestamp¿$timestampSource¿$w¿$h"
    }

    companion object {
      fun fromLine(it: String): ImageInfo {
        val sp = it.split("¿")
        return ImageInfo(
            sp[0],
            sp[1].toLong(),
            sp[2],
            sp[3].toLong(),
            TimestampSource.valueOf(sp[4]),
            sp[5].toInt(),
            sp[6].toInt()
        )
      }
    }
  }

  val service = FileMetadataExtractorService()

  @Test
  fun foo() {
    //parseAndStore(immichFolder, immichFilesList)
    //parseAndStore(googlePhotosFolder, googlePhotosFilesList)

    val existingList = retrieve(immichFilesList)
    val existingByTimestamp = HashMap<Long, MutableList<ImageInfo>>()
    val existingByFileName = HashMap<String, MutableList<ImageInfo>>()

    existingList.forEach {
      if (existingByTimestamp.containsKey(it.timestamp)) {
        existingByTimestamp[it.timestamp]!!.add(it)
      } else {
        existingByTimestamp[it.timestamp] = ArrayList<ImageInfo>().apply { add(it) }
      }

      if (existingByFileName.containsKey(it.getFileName())) {
        existingByFileName[it.getFileName()]!!.add(it)
      } else {
        existingByFileName[it.getFileName()] = ArrayList<ImageInfo>().apply { add(it) }
      }
    }

    val newList = retrieve(googlePhotosFilesList)
    var newUniques = ArrayList<ImageInfo>()
    newList.forEach { new ->
      val existingByTs = existingByTimestamp[new.timestamp]
      var matchingByTimestamp: ImageInfo? = null
      existingByTs?.forEach { e -> if (matchesByTimestamp(e, new)) matchingByTimestamp = new }

      val existingByFname = existingByFileName[new.getFileName()]
      var matchingByFileName: ImageInfo? = null
      existingByFname?.forEach { e -> if (matchesByFname(e, new)) matchingByFileName = new }

      if ((matchingByFileName == null) && (matchingByTimestamp == null)) {
        newUniques.add(new)
      } else {
        if (matchingByTimestamp == null) {
          val i = 1
        }
      }
    }

    println("Existing: ${existingList.size}")
    println("New: ${newList.size}")
    println("New uniques: ${newUniques.size}")

    newUniques = ArrayList(newUniques
        .filter { !it.camera.lowercase().contains("nikon") }
        .filter { !it.camera.lowercase().contains("canon") })

    println("New uniques on phone: ${newUniques.size}")
    val screenshots = newUniques.filter { it.fullPath.contains("Screenshot") }
    println("Screenshots: ${screenshots.size}")

    newUniques = ArrayList(newUniques
        .filter { it.w != it.h })
    println("New uniques on phone that are not square: ${newUniques.size}")

    newUniques = ArrayList(newUniques
        .filter { it.getFileName().split("_")[0].length != 6 })
    println("New uniques on phone that are not square and not part of 'album': ${newUniques.size}")

    newUniques = ArrayList(newUniques
        .filter { min(it.w, it.h) > 600 })
    println("New uniques on phone that are not square and not part of 'album' and not previews: ${newUniques.size}")

    val picasa = ArrayList(newUniques
        .filter { !it.camera.contains("Picasa") })
        //.filter { it.camera.contains("unknown-camera") }
    println("Marked as picasa: ${picasa.size}")

    val targetPath = File("/media/hotaro/SanDisk500/immich-to-be-uploaded/new-uniques")
    newUniques.forEachIndexed { index, it ->
      val source = File(it.fullPath)
      val year = Instant.ofEpochMilli(it.timestamp).atOffset(java.time.ZoneOffset.UTC).year
      val yearDir = File(targetPath, year.toString())
      if (!yearDir.exists()) {
        yearDir.mkdir()
      }
      val target = File(yearDir, source.name)
      if (target.exists()) {
        println("Exists $index/${newUniques.size}: already in target ${source.name}")
      } else {
        source.copyTo(target)
        println("Copied $index/${newUniques.size}: ${source.name}")
      }
    }

    val targetPathCount = targetPath.listFiles()?.size ?: 0
    println("Target path count: $targetPathCount")
  }

  fun matchesByTimestamp(existing: ImageInfo, new: ImageInfo): Boolean {
    if (existing.getFileName() == new.getFileName()) {
      return true
    }
    return false
  }

  fun matchesByFname(existing: ImageInfo, new: ImageInfo): Boolean {
    if (existing.getFileName() != new.getFileName()) {
      return false
    }
    if (existing.timestamp != new.timestamp) {
      if (existing.fullPath.contains("IMG20")) {
        return existing.camera == new.camera
      }
      return false
    }
    return existing.camera == new.camera
  }

  fun parseAndStore(sourceFolder: File, targetTxt: File) {
    val totalList = ArrayList<ImageInfo>()
    sourceFolder.walk().forEach {
      if (it.isFile && it.isMediaFile()) {
        val (timestamp, timestampSource) = service.extractTimestamp(it)
        val camera = service.extractCameraInfo(it)
        val dimension = service.extractDimension(it)
        val info = ImageInfo(
            it.absolutePath,
            it.length(),
            camera,
            timestamp,
            timestampSource,
            dimension.width,
            dimension.height)
        totalList.add(info)
      }
    }
    val output = File(targetTxt.absolutePath)
    output.printWriter().use { out ->
      totalList.forEach {
        out.println(it.toLine())
      }
    }
  }

  fun retrieve(input: File): List<ImageInfo> {
    val totalList = ArrayList<ImageInfo>()
    input.readLines().forEach {
      val info = ImageInfo.fromLine(it)
      totalList.add(info)
    }
    return totalList
  }
}