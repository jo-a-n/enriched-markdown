package com.swmansion.enriched.markdown.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.swmansion.enriched.markdown.spoiler.CustomSpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlayHost
import com.swmansion.enriched.markdown.spoiler.SpoilerSegment
import com.swmansion.enriched.markdown.spoiler.SpoilerSegmentOverlay
import android.graphics.Canvas as NativeCanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/**
 * A [SpoilerSegmentOverlay] drawn with Compose: subclass it, implement [DrawScope.draw], and return
 * instances from [CustomSpoilerOverlay.createSegment], passing on the host.
 *
 * Every call gets a [DrawScope] over the segment: its origin is the segment's top-left corner, its
 * [DrawScope.size] the segment's size, and drawing is clipped to it. Its [Density] is the host's
 * display density and font scale, and its [LayoutDirection] follows the direction of the segment's
 * paragraph ([SpoilerSegment.isRtl]).
 *
 * [isAnimated] and [onRemoved] work as they do on any [SpoilerSegmentOverlay]. To draw the text
 * through the overlay (a blur, a pixelation), use [drawSegmentText].
 */
abstract class DrawScopeSpoilerSegmentOverlay(
  private val host: SpoilerOverlayHost,
) : SpoilerSegmentOverlay() {
  private var density = Density(host.density, host.fontScale)
  private val drawScope = CanvasDrawScope()

  // The view hands over the same canvas frame after frame, so its wrapper is kept until it changes.
  private var nativeCanvas: NativeCanvas? = null
  private var composeCanvas: ComposeCanvas? = null

  /** Draws the concealed segment. [segment] describes it as of this frame. */
  abstract fun DrawScope.draw(segment: SpoilerSegment)

  /**
   * Draws the segment as it is revealed, with [progress] rising from 0 towards 1; see
   * [SpoilerSegmentOverlay.drawReveal] for how reveals run.
   *
   * The default is [drawFadingOut]. Override it for an effect of your own, and call
   * [drawFadingOut] from the override to keep the fade.
   */
  open fun DrawScope.drawReveal(
    segment: SpoilerSegment,
    progress: Float,
  ) {
    drawFadingOut(segment, progress)
  }

  /**
   * Draws [DrawScope.draw] faded out to how far the reveal at [progress] has got, on the curve the
   * text fades in on underneath.
   */
  protected fun DrawScope.drawFadingOut(
    segment: SpoilerSegment,
    progress: Float,
  ) {
    // The fade lives in the base class, which draws back through draw(canvas, segment).
    super.drawReveal(drawContext.canvas.nativeCanvas, segment, progress)
  }

  final override fun draw(
    canvas: NativeCanvas,
    segment: SpoilerSegment,
  ) {
    drawScope.draw(currentDensity(), segment.layoutDirection, wrap(canvas), Size(segment.width, segment.height)) {
      draw(segment)
    }
  }

  final override fun drawReveal(
    canvas: NativeCanvas,
    segment: SpoilerSegment,
    progress: Float,
  ) {
    drawScope.draw(currentDensity(), segment.layoutDirection, wrap(canvas), Size(segment.width, segment.height)) {
      drawReveal(segment, progress)
    }
  }

  // The host's density and font scale can change while the segment lives; the Density is only
  // remade when one of them does.
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

  private val SpoilerSegment.layoutDirection: LayoutDirection
    get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/**
 * Draws [segment]'s text as it looks once revealed, each glyph where the text view draws it; see
 * [SpoilerSegment.drawText]. The scope's current transform applies, so draw it inside `scale` or
 * `translate` to move it.
 */
fun DrawScope.drawSegmentText(segment: SpoilerSegment) {
  segment.drawText(drawContext.canvas.nativeCanvas)
}
