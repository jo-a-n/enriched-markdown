package com.swmansion.enriched.markdown.styles

/**
 * Colors of the overlay that conceals `||spoiler||` text. How the overlay looks otherwise is set by
 * the [com.swmansion.enriched.markdown.spoiler.SpoilerOverlay] itself.
 *
 * The default here is a placeholder for constructing a bare [StyleConfig]; the theme default comes
 * from `DefaultStyles`.
 *
 * @property color the particles' color, and the solid overlay's fill.
 */
data class SpoilerStyle(
  val color: Int = DEFAULT_COLOR,
) {
  companion object {
    internal const val DEFAULT_COLOR = 0xFF374151.toInt()
  }
}
