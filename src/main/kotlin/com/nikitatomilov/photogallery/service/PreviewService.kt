package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.util.isVideo
import com.nikitatomilov.photogallery.util.pathWithoutName
import mu.KLogging
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.filters.ImageFilter
import net.coobird.thumbnailator.geometry.Position
import net.coobird.thumbnailator.geometry.Positions
import net.coobird.thumbnailator.util.BufferedImages
import org.jcodec.api.FrameGrab
import org.jcodec.scale.AWTUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.awt.*
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

  companion object : KLogging() {
    private val PLAY_OUTLINE_ICON_RESOURCE = this::class.java.getResource("/static/res/play.png")
    private const val FONT_SIZE = 20
    private val FONT = Font(Font.SERIF, Font.PLAIN, FONT_SIZE)
    private const val PREVIEW_WIDTH = 256
  }

  init {
    if (!File(previewLocation).exists()) File(previewLocation).mkdirs()
  }

  fun getImagePreview(entity: MediaEntity): Pair<File, Boolean> {
    val previewFile = File(previewLocation, "${entity.id}.jpg")
    if (previewFile.exists()) return previewFile to true
    return generatePreview(entity.asFile(), previewFile, entity.getInstant().toString()) to false
  }

  private fun generatePreview(source: File, target: File, caption: String): File {
    return try {
      if (source.isVideo()) {
        return generatePreviewForVideo(source, target, caption)
      }
      return generatePreviewForPhoto(source, target, caption)
    } catch (e: Exception) {
      logger.error(e) { "Error on creating thumbnail for $source" }
      source
    }
  }

  private fun generatePreviewForVideo(source: File, target: File, caption: String): File {
    val picture = FrameGrab.getFrameFromFile(source, 0)
    val bufferedImage = AWTUtil.toBufferedImage(picture)
    val tmp = Files.createTempFile("tmp", "jpg").toFile()
    ImageIO.write(bufferedImage, "jpg", tmp)
    return generatePreviewForPhoto(tmp, target, caption, ImageIO.read(PLAY_OUTLINE_ICON_RESOURCE))
  }

  private fun generatePreviewForPhoto(
    source: File,
    target: File,
    caption: String,
    watermark: BufferedImage? = null,
  ): File {
    val rect = getImageDimensions(source)!!
    val dim = min(rect.width, rect.height)
    var thumb = Thumbnails.of(source)
        .sourceRegion(Positions.CENTER, dim, dim)
        .size(PREVIEW_WIDTH, PREVIEW_WIDTH)
        .addFilter(
            CaptionWithBackground(
                caption,
                FONT,
                Color.WHITE,
                Color.BLACK,
                1.0f,
                Positions.BOTTOM_LEFT,
                0))
    if (watermark != null) {
      thumb = thumb.watermark(Positions.TOP_LEFT, watermark, 0.8f)
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

  class CaptionWithBackground(
    private val caption: String,
    private val font: Font,
    private val foreColor: Color,
    private val bgColor: Color,
    private val alpha: Float,
    private val position: Position,
    private val insets: Int
  ) : ImageFilter {
    override fun apply(input: BufferedImage): BufferedImage? {
      val result = BufferedImages.copy(input)
      val graphics = result.createGraphics()
      graphics.font = font
      graphics.composite = AlphaComposite.getInstance(3, alpha)
      val w = input.width
      val h = input.height
      val sW = graphics.fontMetrics.stringWidth(caption)
      val fSH = graphics.fontMetrics.height
      val sH = fSH / 2
      val pos: Point = position.calculate(
          w, h, sW, 0,
          insets, insets, insets, insets)
      val relH = pos.y.toDouble() / input.height.toDouble()
      val absH = ((1.0 - relH) * sH.toDouble()).toInt()
      graphics.color = bgColor
      graphics.fillRect(pos.x, pos.y - fSH, sW, fSH)
      graphics.color = foreColor
      graphics.drawString(caption, pos.x, pos.y + absH)
      graphics.dispose()
      return result
    }
  }
}