package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dto.FolderDto
import com.nikitatomilov.photogallery.dto.FolderWithContentsDto
import com.nikitatomilov.photogallery.dto.PhotoDto
import com.nikitatomilov.photogallery.dto.PhotoPositionDto
import com.nikitatomilov.photogallery.util.isPhoto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File

@Service
class FilesService(
  @Autowired private val mediaLibraryService: MediaLibraryService
) {

  private val photoEntitiesCache = HashMap<File, List<MediaEntity>>()

  fun getRootDirs() = mediaLibraryService.getRootDirs().map { FolderDto(it) }

  fun getFolderContent(file: File): FolderWithContentsDto {
    if (!(isCorrectRequest(file) && file.isDirectory)) return FolderWithContentsDto.empty(file)
    val cur = FolderDto(file)
    val subDirs = file.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    val photos = file.listFiles()?.filter { it.isPhoto() }?.sortedBy { it } ?: emptyList()
    val photoEntities = photoEntitiesCache.getOrPut(file) {
      photos.mapNotNull { mediaLibraryService.find(it) }.sortedBy { it.getDate() }
    }
    return FolderWithContentsDto(
        cur,
        getParent(file),
        subDirs.map { FolderDto(it) },
        photoEntities.map { it.toPhotoDto() }
    )
  }

  fun getPhotoContent(id: Long, back: File): Pair<PhotoDto, PhotoPositionDto> {
    val entity = mediaLibraryService.find(id)
      ?: return PhotoDto.empty(File("$id")) to PhotoPositionDto.empty(id)
    val photoDto = entity.toPhotoDto()
    val photoPosition = getPosition(entity.id!!, back)
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

  private fun getPosition(id: Long, view: File): PhotoPositionDto {
    val photoEntities = photoEntitiesCache[view] ?: return PhotoPositionDto.empty(id)
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
    return PhotoPositionDto(id, prevId, nextId, curIdx + 1, photoEntities.size)
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
      PhotoDto(this.id!!, this.fileName, this.fullPath, this.getDate())
}