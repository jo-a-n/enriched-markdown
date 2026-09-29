package com.swmansion.enriched.markdown.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.swmansion.enriched.markdown.spoiler.CustomSpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlayHost
import com.swmansion.enriched.markdown.spoiler.SpoilerSegment
import com.swmansion.enriched.markdown.styles.SpoilerStyle

// The Compose custom overlay example from the README, kept here so it keeps compiling. Keep the two
// in sync.

data class ShimmerSpoiler(
  val periodMillis: Long = 1_500,
) : CustomSpoilerOverlay {
  override fun createSegment(
    host: SpoilerOverlayHost,
    style: SpoilerStyle,
  ) = ShimmerSegment(host, Color(style.color), periodMillis)
}

class ShimmerSegment(
  host: SpoilerOverlayHost,
  private val color: Color,
  private val periodMillis: Long,
) : DrawScopeSpoilerSegmentOverlay(host) {
  override val isAnimated get() = true

  override fun DrawScope.draw(segment: SpoilerSegment) {
    drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx()))
    // A band of light sweeping across in reading order, once per period.
    val phase = (segment.frameTimeMillis % periodMillis) / periodMillis.toFloat()
    val band = 32.dp.toPx()
    val travelled = -band + (size.width + 2 * band) * phase
    val center = if (layoutDirection == LayoutDirection.Ltr) travelled else size.width - travelled
    drawRect(
      Brush.horizontalGradient(
        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
        startX = center - band,
        endX = center + band,
      ),
    )
  }

  // Wipes the box away in reading order, instead of the default fade.
  override fun DrawScope.drawReveal(
    segment: SpoilerSegment,
    progress: Float,
  ) {
    val covered = size.width * (1f - progress)
    val left = if (layoutDirection == LayoutDirection.Ltr) size.width - covered else 0f
    clipRect(left = left, right = left + covered) { draw(segment) }
  }
}
