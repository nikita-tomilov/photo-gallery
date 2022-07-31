package com.nikitatomilov.photogallery.service

import mu.KLogging
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.makers.FixedSizeThumbnailMaker
import net.coobird.thumbnailator.resizers.DefaultResizerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import javax.imageio.ImageIO
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
      val tw = 128
      val th = tw
      val sourceImage = ImageIO.read(source)
      val targetImage = BufferedImage(tw, th, TYPE_INT_RGB)
      val g: Graphics2D = targetImage.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE)
      val sw = sourceImage.width * 1.0 / tw
      val sh = sourceImage.height * 1.0 / th
      val scale = min(sw, sh)
      val w = (sourceImage.width / scale).toInt()
      val h = (sourceImage.height / scale).toInt()

      val resizer = DefaultResizerFactory.getInstance().getResizer(
          Dimension(sourceImage.width, sourceImage.height),
          Dimension(w, h)
      )
      val scaled = FixedSizeThumbnailMaker(w, h, false, true).resizer(resizer).make(sourceImage)
      val x = (tw - w) / 2
      val y = (th - h) / 2
      g.drawImage(scaled, x, y, null)
      ImageIO.write(targetImage, "JPG", target)
      target
    } catch (e: Exception) {
      logger.error(e) { "Error on creating thumbnail for $target" }
      source
    }
  }

  companion object : KLogging()
}