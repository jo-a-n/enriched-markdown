package com.swmansion.enriched.markdown.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.swmansion.enriched.markdown.spoiler.CustomSpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerLine
import com.swmansion.enriched.markdown.spoiler.SpoilerLineOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlayHost
import android.graphics.Canvas as NativeCanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * A [SpoilerLineOverlay] drawn with Compose; return instances from
 * [CustomSpoilerOverlay.createLineOverlay]. Each call gets a [DrawScope] clipped to the line,
 * with the host's [Density] and a [LayoutDirection] that follows [SpoilerLine.isRtl].
 */
abstract class DrawScopeSpoilerLineOverlay(
  private val host: SpoilerOverlayHost,
) : SpoilerLineOverlay() {
  private var density = Density(host.density, host.fontScale)
  private val drawScope = CanvasDrawScope()

  private var nativeCanvas: NativeCanvas? = null
  private var composeCanvas: ComposeCanvas? = null

  /** Draws the concealed line. [line] describes it as of this frame. */
  abstract fun DrawScope.draw(line: SpoilerLine)

  /** See [SpoilerLineOverlay.drawReveal]. The default is [drawFadingOut]. */
  open fun DrawScope.drawReveal(
    line: SpoilerLine,
    progress: Float,
  ) {
    drawFadingOut(line, progress)
  }

  /** Draws [DrawScope.draw] faded out by [progress], as the text fades in underneath. */
  protected fun DrawScope.drawFadingOut(
    line: SpoilerLine,
    progress: Float,
  ) {
    // The fade lives in the base class, which draws back through draw(canvas, line).
    super.drawReveal(drawContext.canvas.nativeCanvas, line, progress)
  }

  final override fun draw(
    canvas: NativeCanvas,
    line: SpoilerLine,
  ) {
    drawScope.draw(currentDensity(), line.layoutDirection, wrap(canvas), Size(line.width, line.height)) {
      draw(line)
    }
  }

  final override fun drawReveal(
    canvas: NativeCanvas,
    line: SpoilerLine,
    progress: Float,
  ) {
    drawScope.draw(currentDensity(), line.layoutDirection, wrap(canvas), Size(line.width, line.height)) {
      drawReveal(line, progress)
    }
  }

  private fun currentDensity(): Density {
    val hostDensity = host.density
    val fontScale = host.fontScale
    if (density.density != hostDensity || density.fontScale != fontScale) density = Density(hostDensity, fontScale)
    return density
  }

  private fun wrap(canvas: NativeCanvas): ComposeCanvas {
    val wrapped = composeCanvas
    if (wrapped != null && nativeCanvas === canvas) return wrapped
    nativeCanvas = canvas
    return ComposeCanvas(canvas).also { composeCanvas = it }
  }

  private val SpoilerLine.layoutDirection: LayoutDirection
    get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/** See [SpoilerLine.drawText]. The scope's current transform applies. */
fun DrawScope.drawLineText(line: SpoilerLine) {
  line.drawText(drawContext.canvas.nativeCanvas)
}
