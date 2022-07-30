package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.FilesystemMediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntityRepository
import com.nikitatomilov.photogallery.dto.FolderDto
import com.nikitatomilov.photogallery.util.isPhoto
import com.nikitatomilov.photogallery.util.isVideo
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.File
import javax.annotation.PostConstruct

@Service
class MediaLibraryService(
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
    val existing = ensureIndexedEntitiesExistInFilesystem()
    val new = tryAddNewEntities()
    logger.warn { "${existing.size} entities already in DB, ${new.size} entities added" }
    val all = mediaEntityRepository.count()
    if (all != (existing.size.toLong() + new.size)) {
      error("Entities count mismatch: $all != ${existing.size} + ${new.size}")
    }
  }

  fun getRootDirs(): List<File> = rootDirs

  fun find(id: Long): MediaEntity? {
    if (id < 0) return null
    return mediaEntityRepository.findByIdOrNull(id)
  }

  fun find(file: File): MediaEntity? {
    val existingByName = mediaEntityRepository.findAllByFileName(file.name)
    if (existingByName.isNotEmpty()) {
      val existingByPath = mediaEntityRepository.findAllByFullPath(file.absolutePath)
      if (existingByPath.size > 1) {
        logger.error { "Too many entities: $existingByPath" }
        return existingByPath.random()
      }
      if (existingByPath.size == 1) {
        val found = existingByPath.single()
        if (found.fullPath == file.absolutePath) {
          return found
        }
      }
    }
    return null
  }

  private fun tryAddNewEntities(): List<MediaEntity> {
    return tryAddNewEntities(rootDirs.associateWith { getMediaFiles(it) })
  }

  private fun getMediaFiles(fromDir: File): List<File> {
    if (!fromDir.isDirectory) {
      error("${fromDir.absolutePath} is not a directory")
    }
    val allFiles = fromDir.walkTopDown().map { it }.toList()
    return allFiles.filter { it.isPhoto() || it.isVideo() }
  }

  private fun tryAddNewEntities(files: Map<File, List<File>>): List<MediaEntity> {
    val mediaFiles =
        files.map { (fromDir, files) -> files.map { FilesystemMediaEntity(fromDir, it) } }.flatten()
    logger.warn { "Found ${mediaFiles.size} media files in the filesystem. Parsing metadata..." }
    val newEntities = ArrayList<MediaEntity>()
    mediaFiles.forEachIndexed { i, it ->
      val existing = find(it.file)
      if (existing == null) {
        val new = indexNew(it, i, mediaFiles.size)
        if (new != null) newEntities.add(new)
      }
    }
    return newEntities
  }

  private fun ensureIndexedEntitiesExistInFilesystem(): List<MediaEntity> {
    val existing = mediaEntityRepository.findAll()
    logger.warn { "${existing.size} entities exist in the DB" }
    val nonExistingAnymore = ArrayList<MediaEntity>()
    existing.forEach {
      if (!File(it.fullPath).exists()) {
        nonExistingAnymore.add(it)
      }
    }
    if (nonExistingAnymore.isNotEmpty()) {
      logger.warn { "${nonExistingAnymore.size} entities were not found in the FS, removing from DB" }
      nonExistingAnymore.forEach { logger.warn { " - ${it.fullPath}" } }
      mediaEntityRepository.deleteAll(nonExistingAnymore)
    }
    return existing
  }

  private fun indexNew(fsEntity: FilesystemMediaEntity, i: Int, n: Int): MediaEntity? {
    var fileSeemsBroken = false
    try {
      fsEntity.parseMetadata()
    } catch (e: Exception) {
      logger.error(e) { "Error on file ${fsEntity.file.absolutePath} " }
      fileSeemsBroken = true
    }
    val new = MediaEntity(
        null,
        fsEntity.file.name,
        fsEntity.file.absolutePath,
        fsEntity.date,
        null,
        fileSeemsBroken)
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