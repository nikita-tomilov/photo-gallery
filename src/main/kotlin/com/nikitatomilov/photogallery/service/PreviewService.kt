package com.nikitatomilov.photogallery.service

import mu.KLogging
import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class PreviewService(
  @Value("\${lib.previewLocation}") private val previewLocation: String
) {

  fun getImagePreview(id: Long, originalPath: String): File {
    val previewFile = File(previewLocation, "$id.jpg")
    if (previewFile.exists()) return previewFile
    return generatePreview(File(originalPath), previewFile)
  }

  private fun generatePreview(source: File, target: File): File {
    return try {
      Thumbnails.of(source)
          .size(128, 128)
          .toFile(target)
      target
    } catch (e: Exception) {
      logger.error(e) { "Error on creating thumbnail for $target" }
      source
    }
  }

  companion object : KLogging()
}