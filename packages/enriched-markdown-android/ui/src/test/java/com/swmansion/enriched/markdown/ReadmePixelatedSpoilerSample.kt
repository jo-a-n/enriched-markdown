package com.swmansion.enriched.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.swmansion.enriched.markdown.spoiler.CustomSpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerLine
import com.swmansion.enriched.markdown.spoiler.SpoilerLineOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlayHost
import com.swmansion.enriched.markdown.styles.SpoilerStyle

// The Canvas custom overlay example from the README, kept here so it keeps compiling. Keep it in
// sync with the README and with the example app's copy in PlaygroundSpoilerOverlay.kt.

data class PixelatedSpoiler(
  val blockSize: Float = 6f,
) : CustomSpoilerOverlay {
  override fun createLineOverlay(
    host: SpoilerOverlayHost,
    style: SpoilerStyle,
  ) = PixelatedLine(blockSize * host.density)
}

class PixelatedLine(
  private val blockSize: Float,
) : SpoilerLineOverlay() {
  // Paint filters bitmaps by default since Android 10; turn it off so the blocks keep hard edges.
  private val paint = Paint().apply { isFilterBitmap = false }
  private val bounds = RectF()
  private var pixels: Bitmap? = null

  override fun draw(
    canvas: Canvas,
    line: SpoilerLine,
  ) {
    val columns = (line.width / blockSize).toInt().coerceAtLeast(1)
    val rows = (line.height / blockSize).toInt().coerceAtLeast(1)
    val image =
      pixels?.takeIf { it.width == columns && it.height == rows }
        ?: Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888).also { bitmap ->
          // The text, shrunk to one pixel per block.
          Canvas(bitmap).apply {
            scale(columns / line.width, rows / line.height)
            line.drawText(this)
          }
          pixels = bitmap
        }
    bounds.set(0f, 0f, line.width, line.height)
    canvas.drawBitmap(image, null, bounds, paint)
  }

  override fun onRemoved() {
    pixels = null
  }
}
