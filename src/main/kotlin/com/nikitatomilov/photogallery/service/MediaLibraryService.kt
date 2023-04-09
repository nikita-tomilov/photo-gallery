package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.FilesystemMediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dao.MediaEntityRepository
import com.nikitatomilov.photogallery.util.isMediaFile
import com.nikitatomilov.photogallery.util.pathWithoutName
import mu.KLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.File
import java.lang.Long.max
import java.lang.Long.min
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PostConstruct

@Service
class MediaLibraryService(
  @Value("\${lib.location}") private val rootPaths: Array<String>,
  @Autowired private val mediaEntityRepository: MediaEntityRepository,
  @Autowired private val previewService: PreviewService,
  @Autowired private val fileMetadataExtractorService: FileMetadataExtractorService,
  @Autowired private val accessRulesService: AccessRulesService
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
    val all = mediaEntityRepository.findAll()
    if (all.size != (existing.size + new.size)) {
      error("Entities count mismatch: $all != ${existing.size} + ${new.size}")
    }
    logger.warn { "Overall there are ${all.size} entities in the database that <seem> unique" }
    logger.warn { "Updating thumbnails..." }

    val counter = AtomicInteger(all.size)
    all.parallelStream().forEach {
      val (_, alreadyPresent) = previewService.getImagePreview(it)
      val remaining = counter.decrementAndGet()
      if (!alreadyPresent) {
        logger.info { "[$remaining left] Preview for ${it.fullPath} done" }
      }
    }
    logger.warn { "Updating thumbnails done" }
  }

  fun getRootDirs(): List<File> = rootDirs

  fun getYears(): List<Int> {
    val min1 = mediaEntityRepository.findTop1ByOrderByParsedDate().getDate()
    val max1 = mediaEntityRepository.findTop1ByOrderByParsedDateDesc().getDate()
    val min2 = mediaEntityRepository.findTop1ByOrderByOverrideDate().getDate()
    val max2 = mediaEntityRepository.findTop1ByOrderByOverrideDateDesc().getDate()
    val min = min(min1, min2)
    val max = max(max1, max2)
    val y1 = Instant.ofEpochMilli(min).atOffset(ZoneOffset.UTC).year
    val y2 = Instant.ofEpochMilli(max).atOffset(ZoneOffset.UTC).year
    return (y1..y2).map { it }.sorted()
  }

  fun find(file: File): MediaEntity? {
    val existingByName = mediaEntityRepository.findByFileName(file.name)
    if (existingByName.isEmpty()) return null

    val existingByPath = mediaEntityRepository.findByFullPath(file.absolutePath)
    if (existingByPath.size > 1) {
      logger.error { "Too many entities in the database for a single absolute path: $existingByPath" }
      return existingByPath.random()
    }
    if (existingByPath.size == 1) {
      val found = existingByPath.single()
      if (found.fullPath == file.absolutePath) {
        return found
      } else {
        logger.error { "Found entity with same name but different path: $found for $file" }
      }
    }

    existingByName.forEach { existing ->
      val existingFile = existing.asFile()
      val existingTimestamp = fileMetadataExtractorService.extractTimestamp(existingFile)
      val timestamp = fileMetadataExtractorService.extractTimestamp(file)
      if (existingTimestamp == timestamp) return existing
    }

    val i = 1
    return null
  }

  fun find(email: String, id: Long): MediaEntity? {
    val entity = find(id) ?: return null
    if (accessRulesService.isAllowed(email, entity)) return entity
    return null
  }

  fun find(email: String, from: Instant, to: Instant): List<MediaEntity> {
    return find(from, to).filter { accessRulesService.isAllowed(email, it) }
  }

  private fun find(from: Instant, to: Instant): List<MediaEntity> {
    return mediaEntityRepository.findAllByParsedDateBetween(from.toEpochMilli(), to.toEpochMilli())
  }

  private fun find(id: Long): MediaEntity? {
    if (id < 0) return null
    return mediaEntityRepository.findByIdOrNull(id)
  }

  private fun tryAddNewEntities(): List<MediaEntity> {
    return tryAddNewEntities(rootDirs.associateWith { getMediaFiles(it) })
  }

  private fun getMediaFiles(fromDir: File): List<File> {
    if (!fromDir.isDirectory) {
      error("${fromDir.absolutePath} is not a directory")
    }
    val allFiles = fromDir.walkTopDown().map { it }.toList()
    return allFiles.filter { it.isMediaFile() }
  }

  private fun tryAddNewEntities(files: Map<File, List<File>>): List<MediaEntity> {
    val mediaFiles =
        files.map { (fromDir, files) -> files.map { FilesystemMediaEntity(fromDir, it) } }.flatten()
    logger.warn { "Found ${mediaFiles.size} media files in the filesystem. Parsing metadata..." }
    val newEntities = ArrayList<MediaEntity>()
    val n = mediaFiles.size
    mediaFiles.forEachIndexed { i, it ->
      val existing = find(it.file)
      if (existing == null) {
        val new = indexNew(it, i, n)
        if (new != null) newEntities.add(new)
      } else {
        if (existing.asFile().absolutePath != it.file.absolutePath) {
          logger.info { "[$i/$n] ${it.file.absolutePath} seems to be a clone of ${existing.fullPath}" }
        }
      }
    }
    return newEntities
  }

  private fun ensureIndexedEntitiesExistInFilesystem(): List<MediaEntity> {
    val existing = mediaEntityRepository.findAll()
    logger.warn { "${existing.size} entities exist in the DB" }
    val nonExistingAnymore = ArrayList<MediaEntity>()
    existing.forEach {
      if (!it.asFile().exists()) {
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
    var fsEntityWithMetadata: FilesystemMediaEntity = fsEntity
    try {
      val timestamp = fileMetadataExtractorService.extractTimestamp(fsEntity.file)
      fsEntityWithMetadata =
          fsEntityWithMetadata.withNewTimestamp(timestamp.first, timestamp.second)
    } catch (e: Exception) {
      logger.error(e) { "Error on file ${fsEntity.file.absolutePath} " }
      fileSeemsBroken = true
    }
    val new = MediaEntity(
        null,
        fsEntityWithMetadata.file.name,
        fsEntityWithMetadata.file.absolutePath,
        fsEntityWithMetadata.timestamp,
        null,
        fileSeemsBroken,
        determineIfFileIsFinal(fsEntity.file))
    try {
      val saved = mediaEntityRepository.saveAndFlush(new)
      logger.info { "[$i/$n] ${fsEntityWithMetadata.file.absolutePath} saved with id ${saved.id}" }
      return saved
    } catch (e: Exception) {
      logger.error(e) { "Error on saving entity $new" }
    }
    return null
  }

  private fun determineIfFileIsFinal(file: File): Boolean {
    //if "burned" directory exists next to a photo, this means, that this file is just a 'dummy'
    //jpeg, and later for the calendar views we shall look for photos only inside this 'burned' dir
    val burnedDirectory = File(file.pathWithoutName(), "burned")
    if (burnedDirectory.exists() && burnedDirectory.isDirectory) {
      return false
    }
    return true
  }

  companion object : KLogging()
}