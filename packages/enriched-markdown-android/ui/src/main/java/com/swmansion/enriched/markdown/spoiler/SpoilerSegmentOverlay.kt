package com.swmansion.enriched.markdown.spoiler

import android.graphics.Canvas
import kotlin.math.roundToInt

/**
 * Draws the effect over one line segment of a concealed spoiler, so a spoiler that wraps gets one
 * per line. Subclass it and return instances from [CustomSpoilerOverlay.createSegment].
 *
 * The text view creates and discards these as segments come and go, and runs every reveal: an
 * overlay only draws. It paints on the text view's canvas, after the text, with the canvas moved
 * to the segment's top-left corner and clipped to its size. The text under it is already drawn
 * transparent, emoji and inline images included, so the overlay needs no opaque backdrop.
 *
 * All calls come on the main thread.
 */
abstract class SpoilerSegmentOverlay {
  /** Draws the concealed segment. [segment] describes it as of this frame. */
  abstract fun draw(
    canvas: Canvas,
    segment: SpoilerSegment,
  )

  /**
   * Draws the segment as it is revealed, once per frame, with [progress] rising from 0 towards 1;
   * the overlay is removed when it gets there. The text fades in underneath on the same clock, and
   * every segment of the spoiler reveals together: to go line by line, stagger by
   * [SpoilerSegment.index]. The duration is the view's, so shape the effect from [progress].
   *
   * The default draws [draw] fading out, as the text fades in. Override it for an effect of your
   * own; calling `super` keeps the fade.
   */
  open fun drawReveal(
    canvas: Canvas,
    segment: SpoilerSegment,
    progress: Float,
  ) {
    val alpha = (overlayAlphaAt(progress) * 255f).roundToInt()
    if (alpha <= 0) return
    val saveCount = canvas.saveLayerAlpha(0f, 0f, segment.width, segment.height, alpha)
    draw(canvas, segment)
    canvas.restoreToCount(saveCount)
  }

  /**
   * Whether the overlay moves on its own. While any overlay in the view says so, the view draws
   * every frame; read [SpoilerSegment.frameTimeMillis] to advance the animation. Reveals are
   * animated regardless.
   */
  open val isAnimated: Boolean get() = false

  /** The segment left the layout, was revealed, or the overlay was replaced: release resources. */
  open fun onRemoved() {}
}
