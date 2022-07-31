package com.nikitatomilov.photogallery.web

import com.nikitatomilov.photogallery.service.MediaLibraryService
import com.nikitatomilov.photogallery.service.PreviewService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.io.File
import java.io.FileInputStream

@Controller
class ImageController(
  @Autowired private val mediaLibraryService: MediaLibraryService,
  @Autowired private val previewService: PreviewService
) {

  @GetMapping("/image/{id}", produces = [MediaType.IMAGE_JPEG_VALUE])
  fun downloadImage(@PathVariable("id") id: Long): ResponseEntity<ByteArray> {
    val entity = mediaLibraryService.find(id) ?: return ResponseEntity.notFound().build()
    val bytes = StreamUtils.copyToByteArray(FileInputStream(File(entity.fullPath)))
    return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(bytes);
  }

  @GetMapping("/image/preview/{id}", produces = [MediaType.IMAGE_JPEG_VALUE])
  fun downloadImagePreview(@PathVariable("id") id: Long): ResponseEntity<ByteArray> {
    val entity = mediaLibraryService.find(id) ?: return ResponseEntity.notFound().build()
    val bytes = StreamUtils.copyToByteArray(
        FileInputStream(
            previewService.getImagePreview(
                entity.id!!,
                entity.fullPath)))
    return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(bytes);
  }
}