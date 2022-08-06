package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dto.*
import com.nikitatomilov.photogallery.util.isMediaFile
import com.nikitatomilov.photogallery.util.isSoftSymlink
import com.nikitatomilov.photogallery.util.isVideo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Service
class FilesService(
  @Autowired private val mediaLibraryService: MediaLibraryService
) {

  private val photoEntitiesCache = HashMap<MediaRequest, List<MediaEntity>>()

  fun getRootDirs() = mediaLibraryService.getRootDirs().map { FolderDto(it) }

  fun getFolderContent(folder: File): FolderWithContentsDto {
    if (!(isCorrectRequest(folder) && folder.isDirectory)) return FolderWithContentsDto.empty(folder)
    val cur = FolderDto(folder)
    val subDirs = folder.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    val photos = folder.listMediaFilesExtractingSoftSymlinks().sortedBy { it.name }
    val photoEntities = photoEntitiesCache.getOrPut(FolderRequest(folder)) {
      photos.mapNotNull { mediaLibraryService.find(it) }.sortedBy { it.getDate() }
    }
    return FolderWithContentsDto(
        cur,
        getParent(folder),
        subDirs.map { FolderDto(it) },
        photoEntities.map { it.toPhotoDto() }
    )
  }

  /*
  TODO:
  1) replace FolderWithContentsDto with something
  2) create new view instead of folder.html for year request, reuse photo grid html
   */
  fun getYearContent(year: Long): FolderWithContentsDto {
    val photoEntities = photoEntitiesCache.getOrPut(YearlyRequest(year)) {
      val f = ZoneOffset.UTC
      val from = LocalDate.of(year.toInt(), 1, 1).atStartOfDay().toInstant(f)
      val to = LocalDate.of(year.toInt(), 12, 31).atTime(LocalTime.MAX).toInstant(f)
      mediaLibraryService.find(from, to).sortedBy { it.getDate() }.filter { it.isFinal }
    }
    return FolderWithContentsDto(
        FolderDto(year.toString(), "dummy-path"),
        FolderDto(year.toString(), "dummy-parent-path"),
        emptyList(),
        photoEntities.map { it.toPhotoDto() }
    )
  }

  fun getPhotoContent(
    id: Long,
    originalRequest: MediaRequest
  ): Pair<MediaFileDto, MediaFilePositionDto> {
    val entity = mediaLibraryService.find(id)
      ?: return MediaFileDto.empty(File("$id")) to MediaFilePositionDto.empty(id)
    val photoDto = entity.toPhotoDto()
    val photoPosition = getPosition(entity.id!!, originalRequest)
    return photoDto to photoPosition
  }

  private fun isCorrectRequest(f: File): Boolean {
    val roots = mediaLibraryService.getRootDirs()
    roots.forEach {
      if (it.contains(f)) return true
    }
    return false
  }

  private fun getParent(f: File): FolderDto {
    val roots = mediaLibraryService.getRootDirs().map { it.absolutePath }.toSet()
    if (roots.contains(f.absolutePath)) return FolderDto(f)
    val parent = f.parentFile
    return if (isCorrectRequest(parent)) {
      FolderDto(parent)
    } else {
      FolderDto(f)
    }
  }

  private fun getPosition(id: Long, view: MediaRequest): MediaFilePositionDto {
    val photoEntities = photoEntitiesCache[view] ?: return MediaFilePositionDto.empty(id)
    var curIdx = 0
    var prevId = id
    var nextId = id
    photoEntities.forEachIndexed { index, it ->
      if (it.id == id) {
        var prevIdx = index - 1
        var nextIdx = index + 1
        if (prevIdx < 0) prevIdx = photoEntities.size - 1
        if (nextIdx > photoEntities.size - 1) nextIdx = 0
        curIdx = index
        prevId = photoEntities[prevIdx].id!!
        nextId = photoEntities[nextIdx].id!!
      }
    }
    return MediaFilePositionDto(id, prevId, nextId, curIdx + 1, photoEntities.size)
  }

  private fun File.listMediaFilesExtractingSoftSymlinks(): List<File> {
    return this.listFiles()?.mapNotNull {
      when {
        it.isMediaFile() -> it
        it.isSoftSymlink() -> {
          val symlinkTarget = Files.readAllLines(it.toPath()).firstOrNull()
          if (symlinkTarget != null) {
            val symlinkTargetFile = File(symlinkTarget)
            if (symlinkTargetFile.isMediaFile()) symlinkTargetFile else null
          } else null
        }
        else -> null
      }
    } ?: emptyList()
  }

  private fun File.contains(f: File): Boolean {
    if (f.absolutePath == this.absolutePath) return true
    if (f.parentFile == null) return false
    var cur = f
    while (cur != cur.parentFile) {
      if (cur.absolutePath == this.absolutePath) return true
      if (cur.parentFile == null) break
      cur = cur.parentFile
    }
    return false
  }

  private fun MediaEntity.toPhotoDto() =
      MediaFileDto(
          this.id!!,
          this.fileName,
          this.fullPath,
          this.getDate(),
          if (this.asFile().isVideo()) MediaFileTypeDto.VIDEO else MediaFileTypeDto.PHOTO)
}