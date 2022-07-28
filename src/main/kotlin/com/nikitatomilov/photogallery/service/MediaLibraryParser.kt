package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.FilesystemMediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntityRepository
import com.nikitatomilov.photogallery.util.isPhoto
import com.nikitatomilov.photogallery.util.isVideo
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import javax.annotation.PostConstruct

@Service
class MediaLibraryParser(
  @Value("\${lib.location}") private val rootPaths: Array<String>,
  @Autowired private val mediaEntityRepository: MediaEntityRepository
) {

  //private val ex = Executors.newFixedThreadPool(THREADS)

  private lateinit var rootDirs: List<File>

  @PostConstruct
  fun postConstruct() {
    logger.info { "Launched with the following dirs: ${rootPaths.toList()}" }
    rootDirs = rootPaths.map { path ->
      File(path).also { if (!it.isDirectory) error("$it is not a directory") }
    }
    parse()
  }

  fun parse(): Map<Int, List<FilesystemMediaEntity>> {
    return parse(rootDirs.associateWith { getMediaFiles(it) })
  }

  private fun getMediaFiles(fromDir: File): List<File> {
    if (!fromDir.isDirectory) {
      error("${fromDir.absolutePath} is not a directory")
    }
    val allFiles = fromDir.walkTopDown().map { it }.toList()
    return allFiles.filter { it.isPhoto() || it.isVideo() }
  }

  private fun parse(files: Map<File, List<File>>): Map<Int, List<FilesystemMediaEntity>> {
    val mediaFiles =
        files.map { (fromDir, files) -> files.map { FilesystemMediaEntity(fromDir, it) } }.flatten()
    logger.warn { "Found ${mediaFiles.size} media files. Parsing metadata..." }
    mediaFiles.forEachIndexed { i, it -> index(it, i + 1, mediaFiles.size) }
//    val mediaFilesBatches = mediaFiles.shuffled().batched(THREADS)
//    val latch = CountDownLatch(mediaFiles.size)
//    mediaFilesBatches.forEach { mediaFilesBatch ->
//      ex.submit {
//        mediaFilesBatch.forEach {
//          parseAndConvert(it)
//          latch.countDown()
//        }
//      }
//    }
//    var finished = false
//    var prev = latch.count
//    var prevMs = System.currentTimeMillis()
//    while (!finished) {
//      val ok = latch.await(1, TimeUnit.SECONDS)
//      if (ok) finished = ok
//      val cur = latch.count
//      if (prev - cur > 1000) {
//        val curMs = System.currentTimeMillis()
//        val ms = curMs - prevMs
//        logger.warn { "$cur files left to parse out of total ${mediaFiles.size}; $ms spent for ${prev - cur} files" }
//        prev = cur
//        prevMs = curMs
//      }
//    }
//    println("Done")

    val correctDateFound = mediaFiles.filter { it.correctDateFound() }
    val correctDateNotFound = mediaFiles.filterNot { it.correctDateFound() }

    logger.warn { "Failed to find date for ${correctDateNotFound.size} files:" }
    correctDateNotFound.forEach { logger.warn { " - $it" } }

    val byYear = correctDateFound.groupBy {
      val i = Instant.ofEpochMilli(it.date).atOffset(ZoneOffset.UTC)
      i.year
    }
    return byYear
  }

  private fun index(fsEntity: FilesystemMediaEntity, i: Int, n: Int): MediaEntity? {
    val existingByName = mediaEntityRepository.findAllByFileName(fsEntity.file.name)
    if (existingByName.isNotEmpty()) {
      val existingByPath = mediaEntityRepository.findAllByFullPath(fsEntity.file.absolutePath)
      if (existingByPath.size > 1) {
        logger.error { "Too many entities: $existingByPath" }
        return existingByPath.random()
      }
      if (existingByPath.size == 1) {
        val found = existingByPath.single()
        if (found.fullPath == fsEntity.file.absolutePath) {
          logger.info { "[$i/$n] ${fsEntity.file.absolutePath} already exists with id ${found.id}" }
          return found
        }
      }
    }
    var fileSeemsBroken = false
    try {
      fsEntity.parseMetadata()
    } catch (e: Exception) {
      logger.error(e) { "Error on file ${fsEntity.file.absolutePath} " }
      fileSeemsBroken = true
    }
    val new = MediaEntity(null, fsEntity.file.name, fsEntity.file.absolutePath, fsEntity.date, null, fileSeemsBroken)
    try {
      val saved = mediaEntityRepository.save(new)
      logger.info { "[$i/$n] ${fsEntity.file.absolutePath} saved with id ${saved.id}" }
      return saved
    } catch (e: Exception) {
      logger.error(e) { "Error on saving entity $new" }
    }
    return null
  }

  companion object : KLogging()
}