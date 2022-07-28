package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.util.batched
import com.nikitatomilov.photogallery.util.isPhoto
import com.nikitatomilov.photogallery.util.isVideo
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MediaLibraryParser {

  private val ex = Executors.newFixedThreadPool(THREADS)

  fun parse(fromDir: File): Map<Int, List<MediaEntity>> {
    return parse(listOf(fromDir))
  }

  fun parse(fromDirs: List<File>): Map<Int, List<MediaEntity>> {
    return parse(fromDirs.associateWith { getMediaFiles(it) })
  }

  private fun getMediaFiles(fromDir: File): List<File> {
    if (!fromDir.isDirectory) {
      error("${fromDir.absolutePath} is not a directory")
    }
    val allFiles = fromDir.walkTopDown().map { it }.toList()
    return allFiles.filter { it.isPhoto() || it.isVideo() }
  }

  private fun parse(files: Map<File, List<File>>): Map<Int, List<MediaEntity>> {
    val mediaFiles =
        files.map { (fromDir, files) -> files.map { MediaEntity(fromDir, it) } }.flatten()
    println("Found ${mediaFiles.size} media files. Parsing metadata...")
    val mediaFilesBatches = mediaFiles.shuffled().batched(THREADS)
    val latch = CountDownLatch(mediaFiles.size)
    mediaFilesBatches.forEach { mediaFilesBatch ->
      ex.submit {
        mediaFilesBatch.forEach {
          try {
            it.parseMetadata()
          } catch (e: Exception) {
            println("Error on ${it.file.absolutePath} : $e")
          } finally {
            latch.countDown()
          }
        }
      }
    }
    var finished = false
    var prev = latch.count
    var prevMs = System.currentTimeMillis()
    while (!finished) {
      val ok = latch.await(1, TimeUnit.SECONDS)
      if (ok) finished = ok
      val cur = latch.count
      if (prev - cur > 1000) {
        val curMs = System.currentTimeMillis()
        val ms = curMs - prevMs
        println("$cur files left to parse out of total ${mediaFiles.size}; $ms spent for ${prev - cur} files")
        prev = cur
        prevMs = curMs
      }
    }
    println("Done")

    val correctDateFound = mediaFiles.filter { it.correctDateFound() }
    val correctDateNotFound = mediaFiles.filterNot { it.correctDateFound() }

    println("Failed to find date for ${correctDateNotFound.size} files:")
    correctDateNotFound.forEach { println(" - $it") }

    val byYear = correctDateFound.groupBy {
      val i = Instant.ofEpochMilli(it.date).atOffset(ZoneOffset.UTC)
      i.year
    }
    return byYear
  }

  companion object {
    private const val THREADS = 3
  }
}