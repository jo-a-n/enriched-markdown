package com.swmansion.enriched.markdown.spoiler

import android.graphics.Canvas
import kotlin.math.roundToInt

/**
 * Draws the effect over one [SpoilerLine] of a concealed spoiler; return instances from
 * [CustomSpoilerOverlay.createLineOverlay]. The canvas is moved to the line's top-left corner and
 * clipped to its size, and the text under it is already transparent, so no backdrop is needed.
 * All calls come on the main thread.
 */
abstract class SpoilerLineOverlay {
  /** Draws the concealed line. [line] describes it as of this frame. */
  abstract fun draw(
    canvas: Canvas,
    line: SpoilerLine,
  )

  /**
   * Draws the line as it is revealed, with [progress] rising from 0 to 1 over
   * [CustomSpoilerOverlay.revealDurationMillis]. All lines of a spoiler reveal together; stagger by
   * [SpoilerLine.index] to go one after another. The default fades [draw] out; `super` keeps it.
   */
  open fun drawReveal(
    canvas: Canvas,
    line: SpoilerLine,
    progress: Float,
  ) {
    val alpha = (overlayAlphaAt(progress) * 255f).roundToInt()
    if (alpha <= 0) return
    val saveCount = canvas.saveLayerAlpha(0f, 0f, line.width, line.height, alpha)
    draw(canvas, line)
    canvas.restoreToCount(saveCount)
  }

  /**
   * Whether the overlay moves on its own, so the view draws every frame; read
   * [SpoilerLine.frameTimeMillis] to advance the animation.
   */
  open val isAnimated: Boolean get() = false

  /**
   * The line left the layout, was revealed, or its content, overlay or style changed: release
   * resources.
   */
  open fun onRemoved() {}
}
