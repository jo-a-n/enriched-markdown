package com.swmansion.enriched.markdown.spoiler

import com.swmansion.enriched.markdown.spans.SpoilerSpan

/**
 * Identifies a segment across draws. A segment that keeps its line and its characters keeps its
 * overlay, even if it moves or resizes; any other change gets a new one.
 */
internal data class SegmentKey(
  val span: SpoilerSpan,
  val line: Int,
  val start: Int,
  val end: Int,
)

internal data class SegmentRect(
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
)
