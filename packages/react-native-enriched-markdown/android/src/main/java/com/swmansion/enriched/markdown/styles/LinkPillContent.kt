package com.swmansion.enriched.markdown.styles

/**
 * What one specific link's pill shows, keyed by exact URL in the `linkPillContent`
 * prop. Empty strings mean "not set": the variant's pill label/icon, then the link
 * text, apply instead.
 */
data class LinkPillContent(
  val label: String = "",
  val iconUri: String = "",
  /** Tint for this link's icon; null means none was set for the link. */
  val iconTintColor: Int? = null,
)
