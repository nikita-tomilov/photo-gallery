package com.nikitatomilov.photogallery.service

import mu.KLogging
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Dimension
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import kotlin.math.min

@Service
class PreviewService(
  @Value("\${lib.previewLocation}") private val previewLocation: String
) {

  fun getImagePreview(id: Long, originalPath: String): File {
    val previewFile = File(previewLocation, "$id.jpg")
    if (previewFile.exists()) return previewFile
    return generatePreview(File(originalPath), previewFile)
  }

  @Suppress("UnnecessaryVariable")
  private fun generatePreview(source: File, target: File): File {
    return try {
      val rect = getImageDimensions(source)!!
      val dim = min(rect.width, rect.height)
      Thumbnails.of(source)
          .sourceRegion(Positions.CENTER, dim, dim)
          .size(128, 128)
          .toFile(target)
      target
    } catch (e: Exception) {
      logger.error(e) { "Error on creating thumbnail for $target" }
      source
    }
  }

  private fun getImageDimensions(input: File): Dimension? {
    ImageIO.createImageInputStream(input)
        .use { stream ->
          if (stream != null) {
            val iioRegistry = IIORegistry.getDefaultInstance()
            val iter =
                iioRegistry.getServiceProviders(
                    ImageReaderSpi::class.java, true)
            while (iter.hasNext()) {
              val readerSpi = iter.next()
              if (readerSpi.canDecodeInput(stream)) {
                val reader: ImageReader = readerSpi.createReaderInstance()
                return try {
                  reader.input = stream
                  val width: Int = reader.getWidth(reader.minIndex)
                  val height: Int = reader.getHeight(reader.minIndex)
                  Dimension(width, height)
                } finally {
                  reader.dispose()
                }
              }
            }
            throw IllegalArgumentException("Can't find decoder for this image")
          } else {
            throw IllegalArgumentException("Can't open stream for this image")
          }
        }
  }

  companion object : KLogging()
}