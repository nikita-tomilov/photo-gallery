package com.nikitatomilov.photogallery.web

import com.nikitatomilov.photogallery.service.MediaLibraryService
import com.nikitatomilov.photogallery.service.PreviewService
import com.nikitatomilov.photogallery.util.SecurityUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.UrlResource
import org.springframework.core.io.support.ResourceRegion
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import java.io.FileInputStream
import java.io.IOException
import java.lang.Long.min
import java.security.Principal

@Controller
class MediaController(
  @Autowired private val mediaLibraryService: MediaLibraryService,
  @Autowired private val previewService: PreviewService
) {

  @GetMapping("/photo/{id}", produces = [MediaType.IMAGE_JPEG_VALUE])
  fun downloadImage(@PathVariable("id") id: Long, principal: Principal): ResponseEntity<ByteArray> {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val entity = mediaLibraryService.find(email, id) ?: return ResponseEntity.notFound().build()
    val bytes = StreamUtils.copyToByteArray(FileInputStream(entity.asFile()))
    return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(bytes);
  }

  @GetMapping("/preview/{id}", produces = [MediaType.IMAGE_JPEG_VALUE])
  fun downloadPreview(@PathVariable("id") id: Long, principal: Principal): ResponseEntity<ByteArray> {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val entity = mediaLibraryService.find(email, id) ?: return ResponseEntity.notFound().build()
    val (file, _) = previewService.getImagePreview(entity)
    val bytes = StreamUtils.copyToByteArray(FileInputStream(file))
    return ResponseEntity
        .ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(bytes);
  }

  @GetMapping("/video/{id}", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
  fun streamVideo(
    @PathVariable("id") id: Long,
    @RequestHeader(value = "Range", required = false) rangeHeader: String?,
    principal: Principal
  ): ResponseEntity<ResourceRegion>? {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val entity = mediaLibraryService.find(email, id) ?: return ResponseEntity.notFound().build()
    val videoResource = UrlResource("file://" + entity.fullPath)
    val resourceRegion: ResourceRegion = getResourceRegion(videoResource, rangeHeader)

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(
            MediaTypeFactory.getMediaType(videoResource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(resourceRegion)
  }

  @Throws(IOException::class)
  private fun getResourceRegion(video: UrlResource, httpHeaders: String?): ResourceRegion {
    val chunkSize = 1000000L
    val contentLength = video.contentLength()
    var fromRange = 0
    var toRange = 0
    if (httpHeaders != null && httpHeaders.isNotBlank()) {
      val ranges = httpHeaders.replace("bytes=", "")
          .split("-")
          .filter { it.isNotEmpty() }
      fromRange = Integer.valueOf(ranges[0])
      toRange = if (ranges.size > 1) {
        Integer.valueOf(ranges[1])
      } else {
        (contentLength - 1).toInt()
      }
    }
    return if (fromRange > 0) {
      val rangeLength: Long = min(chunkSize, (toRange - fromRange + 1).toLong())
      ResourceRegion(video, fromRange.toLong(), rangeLength)
    } else {
      val rangeLength: Long = min(chunkSize, contentLength)
      ResourceRegion(video, 0, rangeLength)
    }
  }
}