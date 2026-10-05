package com.swmansion.enriched.markdown.spoiler

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

internal class SolidLineOverlay(
  private val color: Int,
  private val cornerRadius: Float,
) : SpoilerLineOverlay() {
  private val paint = Paint()
  private val rect = RectF()

  override fun draw(
    canvas: Canvas,
    line: SpoilerLine,
  ) = drawBox(canvas, line, alpha = 1f)

  // Fading the paint matches the default's layer fade for a single shape, without the layer.
  override fun drawReveal(
    canvas: Canvas,
    line: SpoilerLine,
    progress: Float,
  ) = drawBox(canvas, line, overlayAlphaAt(progress))

  private fun drawBox(
    canvas: Canvas,
    line: SpoilerLine,
    alpha: Float,
  ) {
    paint.color = colorWithAlpha(color, alpha)
    rect.set(0f, 0f, line.width, line.height)
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
  }
}
