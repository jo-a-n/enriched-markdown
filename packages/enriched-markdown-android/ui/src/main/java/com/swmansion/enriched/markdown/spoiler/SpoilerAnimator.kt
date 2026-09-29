package com.swmansion.enriched.markdown.spoiler

import android.view.Choreographer

/**
 * The frame loop behind animated overlays and reveals. It is paced by drawing: each draw that
 * still has something moving asks for the next frame, so the loop stops by itself once nothing
 * moves, or when the view stops being drawn.
 *
 * [onFrame] runs in the frame's animation phase, before the view draws.
 */
internal class SpoilerAnimator(
  private val onFrame: () -> Unit,
) {
  private var isFramePending = false

  private val frameCallback =
    Choreographer.FrameCallback {
      isFramePending = false
      onFrame()
    }

  fun requestFrame() {
    if (isFramePending) return
    isFramePending = true
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  fun stop() {
    if (!isFramePending) return
    isFramePending = false
    Choreographer.getInstance().removeFrameCallback(frameCallback)
  }
}
