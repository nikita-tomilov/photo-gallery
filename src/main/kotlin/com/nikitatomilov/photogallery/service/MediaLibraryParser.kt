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
import javax.annotation.PostConstruct

@Service
class MediaLibraryParser(
  @Value("\${lib.location}") private val rootPaths: Array<String>,
  @Autowired private val mediaEntityRepository: MediaEntityRepository
) {

  private lateinit var rootDirs: List<File>

  @PostConstruct
  fun reindexDatabase() {
    logger.info { "Launched reindexing with the following dirs: ${rootPaths.toList()}" }
    rootDirs = rootPaths.map { path ->
      File(path).also { if (!it.isDirectory) error("$it is not a directory") }
    }
    ensureAlreadyExistingFiles()
    parse()
  }

  fun parse(): Map<Int, List<MediaEntity>> {
    return parse(rootDirs.associateWith { getMediaFiles(it) })
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
        files.map { (fromDir, files) -> files.map { FilesystemMediaEntity(fromDir, it) } }.flatten()
    logger.warn { "Found ${mediaFiles.size} media files. Parsing metadata..." }
    val entities = mediaFiles
        .mapIndexed { i, it -> index(it, i + 1, mediaFiles.size) }
        .filterNotNull()

    val byYear = entities.groupBy {
      val i = Instant.ofEpochMilli(it.parsedDate).atOffset(ZoneOffset.UTC)
      i.year
    }
    return byYear
  }

  private fun ensureAlreadyExistingFiles() {
    val existing = mediaEntityRepository.findAll()
    logger.warn { "${existing.size} entities exist in the DB" }
    val nonExistingAnymore = ArrayList<MediaEntity>()
    existing.forEach {
      if (!File(it.fullPath).exists()) {
        nonExistingAnymore.add(it)
      }
    }
    if (nonExistingAnymore.isNotEmpty()) {
      logger.warn { "${nonExistingAnymore.size} entities were not found, removing from DB" }
      nonExistingAnymore.forEach { logger.warn { " - ${it.fullPath}" } }
      mediaEntityRepository.deleteAll(nonExistingAnymore)
    }
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