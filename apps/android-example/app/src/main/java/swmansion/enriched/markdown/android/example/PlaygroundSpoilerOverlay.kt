package swmansion.enriched.markdown.android.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.swmansion.enriched.markdown.compose.DrawScopeSpoilerSegmentOverlay
import com.swmansion.enriched.markdown.spoiler.CustomSpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlay
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlayHost
import com.swmansion.enriched.markdown.spoiler.SpoilerSegment
import com.swmansion.enriched.markdown.spoiler.SpoilerSegmentOverlay
import com.swmansion.enriched.markdown.styles.SpoilerStyle

/**
 * The overlays the Playground's "Spoiler" button cycles through. Each entry holds one overlay
 * instance, so recomposing the screen passes the same value and running overlays keep going.
 */
enum class PlaygroundSpoilerOverlay(
  val label: String,
  val overlay: SpoilerOverlay,
) {
  Particles("Particles", SpoilerOverlay.Particles()),
  DenseParticles("Dense particles", SpoilerOverlay.Particles(density = 20f, speed = 45f)),
  Solid("Solid", SpoilerOverlay.Solid(cornerRadius = 6f)),
  Gradient("Gradient", GradientSpoiler()),
  Pixelated("Pixelated", PixelatedSpoiler()),
  ;

  val next: PlaygroundSpoilerOverlay get() = entries[(ordinal + 1) % entries.size]
}

/** Markdown with spoilers in the places overlays have to cope with. */
fun spoilerSampleMarkdown(inlineImageUri: String): String =
  """
  ## A heading with a ||hidden|| word

  A long spoiler wraps, and every line gets its own overlay: ||the oldest known living tree is a Great Basin bristlecone pine in the White Mountains of California, over 4,850 years old||.

  Styled spoilers: ||**Methuselah**, *the tree's nickname*|| and ||a [link](https://en.wikipedia.org/wiki/Bristlecone_pine) inside||.

  Emoji and an inline image: ||🌲🌳 ![icon]($inlineImageUri) forest||.

  Right to left: ||שלום עולם, זהו ספוילר||.
  """.trimIndent()

// A custom overlay drawn with Compose (DrawScope, Brush, Color).

/**
 * A rounded box with a gradient drifting through it in reading order. When revealed, it wipes
 * away line after line.
 */
data class GradientSpoiler(
  val periodMillis: Long = 2_000,
) : CustomSpoilerOverlay {
  override fun createSegment(
    host: SpoilerOverlayHost,
    style: SpoilerStyle,
  ) = GradientSegment(host, Color(style.color), periodMillis)
}

class GradientSegment(
  host: SpoilerOverlayHost,
  color: Color,
  private val periodMillis: Long,
) : DrawScopeSpoilerSegmentOverlay(host) {
  // Made once and slid with translate(): a new Brush each frame is a new shader.
  private val stripe = 40 * host.density
  private val gradient =
    Brush.horizontalGradient(
      listOf(color, lerp(color, Color.White, 0.45f)),
      startX = 0f,
      endX = stripe,
      tileMode = TileMode.Mirror,
    )

  override val isAnimated get() = true

  override fun DrawScope.draw(segment: SpoilerSegment) {
    // One period moves the mirrored gradient by a full repeat, so the loop is seamless.
    val phase = (segment.frameTimeMillis % periodMillis) / periodMillis.toFloat()
    val offset = 2 * stripe * phase * if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    translate(left = offset) {
      drawRoundRect(gradient, topLeft = Offset(-offset, 0f), size = size, cornerRadius = CornerRadius(4.dp.toPx()))
    }
  }

  override fun DrawScope.drawReveal(
    segment: SpoilerSegment,
    progress: Float,
  ) {
    // Every segment gets progress at once; give each line its own slice of it, in reading order.
    val lineProgress = (progress * segment.count - segment.index).coerceIn(0f, 1f)
    val covered = size.width * (1f - lineProgress)
    val left = if (layoutDirection == LayoutDirection.Ltr) size.width - covered else 0f
    clipRect(left = left, right = left + covered) { draw(segment) }
  }
}

// A custom overlay drawn on the text view's Canvas, showing the text through.

/** Pixelates the hidden text, so its shape shows but the words don't. */
data class PixelatedSpoiler(
  val blockSize: Float = 6f, // dp
) : CustomSpoilerOverlay {
  override fun createSegment(
    host: SpoilerOverlayHost,
    style: SpoilerStyle,
  ) = PixelatedSegment(blockSize * host.density, style.color)
}

class PixelatedSegment(
  private val blockSize: Float,
  color: Int,
) : SpoilerSegmentOverlay() {
  // A faint tint, so the segment reads as hidden even where the text is sparse.
  private val backdrop =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      alpha = 40
    }
  private val pixelPaint = Paint().apply { isFilterBitmap = false } // hard-edged blocks
  private val bounds = RectF()
  private var pixels: Bitmap? = null

  override fun draw(
    canvas: Canvas,
    segment: SpoilerSegment,
  ) {
    bounds.set(0f, 0f, segment.width, segment.height)
    canvas.drawRoundRect(bounds, blockSize / 2, blockSize / 2, backdrop)
    canvas.drawBitmap(pixelsFor(segment), null, bounds, pixelPaint)
  }

  // drawText lays out the line each time it's called, so the pixels are made once per size.
  private fun pixelsFor(segment: SpoilerSegment): Bitmap {
    val columns = (segment.width / blockSize).toInt().coerceAtLeast(1)
    val rows = (segment.height / blockSize).toInt().coerceAtLeast(1)
    pixels?.let { if (it.width == columns && it.height == rows) return it }
    return Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888).also { bitmap ->
      // The text, drawn shrunk to one pixel per block.
      Canvas(bitmap).apply {
        scale(columns / segment.width, rows / segment.height)
        segment.drawText(this)
      }
      pixels = bitmap
    }
  }

  override fun onRemoved() {
    pixels = null
  }
}
