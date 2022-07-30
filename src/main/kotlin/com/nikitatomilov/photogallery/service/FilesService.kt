package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dto.FolderDto
import com.nikitatomilov.photogallery.dto.FolderWithContentsDto
import com.nikitatomilov.photogallery.dto.PhotoDto
import com.nikitatomilov.photogallery.util.isPhoto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File

@Service
class FilesService(
  @Autowired private val mediaLibraryService: MediaLibraryService
) {

  fun getRootDirs() = mediaLibraryService.getRootDirs().map { FolderDto(it) }

  fun getFolderContent(file: File): FolderWithContentsDto {
    if (!(isCorrectRequest(file) && file.isDirectory)) return FolderWithContentsDto.empty(file)
    val cur = FolderDto(file)
    val subDirs = file.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    val photos = file.listFiles()?.filter { it.isPhoto() }?.sortedBy { it.name } ?: emptyList()
    val photoEntities = photos.mapNotNull { mediaLibraryService.find(it) }
    return FolderWithContentsDto(cur,
        subDirs.map { FolderDto(it) },
        photoEntities.map { it.toPhotoDto() }
    )
  }

  fun getPhotoContent(id: Long): PhotoDto {
    val entity = mediaLibraryService.find(id) ?: return PhotoDto.empty(File("$id"))
    return entity.toPhotoDto()
  }

  private fun isCorrectRequest(f: File): Boolean {
    val roots = mediaLibraryService.getRootDirs()
    roots.forEach {
      if (it.contains(f)) return true
    }
    return false
  }

  fun File.contains(f: File): Boolean {
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