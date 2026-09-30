package com.swmansion.enriched.markdown.spoiler

import com.swmansion.enriched.markdown.styles.SpoilerStyle

/**
 * How unrevealed `||spoiler||` text is concealed: one of the built-ins, or a [CustomSpoilerOverlay].
 *
 * Colors come from the theme's [SpoilerStyle]; the tuning of each effect is the overlay's own. A
 * view rebuilds its overlays only when the new value is not `==` to the previous one.
 */
sealed interface SpoilerOverlay {
  /**
   * A field of drifting particles, the default.
   *
   * @property density how thickly the field is populated; the particle count scales linearly
   *   with it, so 16 is twice the default.
   * @property speed how fast the particles drift; their velocity scales linearly with it, so 40 is
   *   twice the default.
   */
  data class Particles(
    val density: Float = DEFAULT_DENSITY,
    val speed: Float = DEFAULT_SPEED,
  ) : SpoilerOverlay {
    internal companion object {
      const val DEFAULT_DENSITY = 8f
      const val DEFAULT_SPEED = 20f
    }
  }

  /**
   * A solid rounded box in the style's color.
   *
   * @property cornerRadius in dp.
   */
  data class Solid(
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
  ) : SpoilerOverlay {
    internal companion object {
      const val DEFAULT_CORNER_RADIUS = 4f
    }
  }
}

/**
 * An overlay of the app's own: builds one [SpoilerSegmentOverlay] for each line segment of every
 * concealed spoiler, which draws the effect on the text view's canvas.
 *
 * A view rebuilds its overlays only when the new value is not `==` to the previous one, so make
 * implementations data classes or objects and keep their parameters in properties. A plain class
 * created anew on each call (such as in every recomposition) restarts every overlay each time.
 */
interface CustomSpoilerOverlay : SpoilerOverlay {
  /**
   * Called on the main thread whenever a segment comes into view: on the first draw, and again
   * after the text reflows or the overlay or style changes. Keep it cheap.
   */
  fun createSegment(
    host: SpoilerOverlayHost,
    style: SpoilerStyle,
  ): SpoilerSegmentOverlay
}

/** The text view a [SpoilerSegmentOverlay] draws into. */
interface SpoilerOverlayHost {
  /** Pixels per dp on the view's display. */
  val density: Float

  /** The user's font scale: pixels per sp are [density] times this. */
  val fontScale: Float

  /**
   * Asks for one more draw, for example after an asset the overlay needs has loaded. Safe to call
   * from any thread. An overlay that animates sets [SpoilerSegmentOverlay.isAnimated] instead.
   */
  fun invalidate()
}

internal fun SpoilerOverlay.createSegmentOverlay(
  host: SpoilerOverlayHost,
  style: SpoilerStyle,
): SpoilerSegmentOverlay =
  when (this) {
    is SpoilerOverlay.Particles -> ParticleSegmentOverlay(style.color, density, speed)

    // Given in dp like the rest of the overlay's public tuning; drawn in pixels.
    is SpoilerOverlay.Solid -> SolidSegmentOverlay(style.color, cornerRadius * host.density)

    is CustomSpoilerOverlay -> createSegment(host, style)
  }
