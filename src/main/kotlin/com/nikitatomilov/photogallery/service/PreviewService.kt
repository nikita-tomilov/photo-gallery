package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.util.isVideo
import mu.KLogging
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.geometry.Positions
import org.jcodec.api.FrameGrab
import org.jcodec.scale.AWTUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import kotlin.math.min

@Service
class PreviewService(
  @Value("\${lib.previewLocation}") private val previewLocation: String
) {

  private val PLAY_OUTLINE_ICON_RESOURCE = this::class.java.getResource("/static/res/play.png")

  fun getImagePreview(entity: MediaEntity): File {
    val previewFile = File(previewLocation, "${entity.id}.jpg")
    if (previewFile.exists()) return previewFile
    return generatePreview(entity.asFile(), previewFile)
  }

  @Suppress("UnnecessaryVariable")
  private fun generatePreview(source: File, target: File): File {
    return try {
      if (source.isVideo()) {
        return generatePreviewForVideo(source, target)
      }
      return generatePreviewForPhoto(source, target)
    } catch (e: Exception) {
      logger.error(e) { "Error on creating thumbnail for $source" }
      source
    }
  }

  private fun generatePreviewForVideo(source: File, target: File): File {
    val picture = FrameGrab.getFrameFromFile(source, 0)
    val bufferedImage = AWTUtil.toBufferedImage(picture)
    val tmp = Files.createTempFile("tmp", "jpg").toFile()
    ImageIO.write(bufferedImage, "jpg", tmp)
    return generatePreviewForPhoto(tmp, target, ImageIO.read(PLAY_OUTLINE_ICON_RESOURCE))
  }

  private fun generatePreviewForPhoto(source: File, target: File, watermark: BufferedImage? = null): File {
    val rect = getImageDimensions(source)!!
    val dim = min(rect.width, rect.height)
    var thumb = Thumbnails.of(source)
        .sourceRegion(Positions.CENTER, dim, dim)
        .size(256, 256)
    if (watermark != null) {
      thumb = thumb.watermark(Positions.BOTTOM_LEFT, watermark, 0.8f)
    }
    thumb.toFile(target)
    return target
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