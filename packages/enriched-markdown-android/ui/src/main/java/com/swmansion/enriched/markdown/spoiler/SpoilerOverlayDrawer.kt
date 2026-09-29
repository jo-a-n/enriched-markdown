package com.swmansion.enriched.markdown.spoiler

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.graphics.withTranslation
import com.swmansion.enriched.markdown.spans.SpoilerSpan
import com.swmansion.enriched.markdown.styles.SpoilerStyle
import java.lang.ref.WeakReference

/**
 * Keeps one [SpoilerSegmentOverlay] per line segment of every concealed spoiler in a text view,
 * draws them over the text, and runs reveals. Overlays only draw: this owns their lifecycle, the
 * reveal clock, and the text fading in underneath.
 */
internal class SpoilerOverlayDrawer(
  textView: TextView,
) : SpoilerOverlayHost {
  private class LiveSegment(
    val span: SpoilerSpan,
    val overlay: SpoilerSegmentOverlay,
    val segment: SpoilerSegment,
  )

  private class Reveal(
    val startTime: Long,
    val onComplete: () -> Unit,
  ) {
    fun progressAt(time: Long): Float = ((time - startTime).toFloat() / REVEAL_DURATION_MS).coerceIn(0f, 1f)
  }

  private class LineSegment(
    val line: Int,
    val start: Int,
    val end: Int,
    val rect: SegmentRect,
  )

  private val textViewReference = WeakReference(textView)
  private val animator = SpoilerAnimator(::onFrame)

  private val segments = LinkedHashMap<SegmentKey, LiveSegment>()
  private val reveals = LinkedHashMap<SpoilerSpan, Reveal>()

  private val activeKeys = HashSet<SegmentKey>()
  private val lineSegments = ArrayList<LineSegment>()
  private val metricsPaint = TextPaint()
  private val fontMetrics = Paint.FontMetrics()

  private var style: SpoilerStyle? = null

  override val density: Float = textView.resources.displayMetrics.density

  var spoilerOverlay: SpoilerOverlay = SpoilerOverlay.Particles()
    set(value) {
      if (field == value) return
      field = value
      rebuild()
    }

  override fun invalidate() {
    textViewReference.get()?.postInvalidateOnAnimation()
  }

  fun registerSpans(spans: Array<SpoilerSpan>) {
    if (spans.isEmpty()) return
    val newStyle = spans[0].styleCache.spoilerStyle
    if (style == newStyle) return
    val restyled = style != null
    style = newStyle
    // Overlays take the style when they are created, so a new one needs new overlays.
    if (restyled) rebuild()
  }

  fun draw(canvas: Canvas) {
    val ctx = buildContext() ?: return
    val style = style ?: return
    // One clock for the whole frame: the text fade, every reveal and every animated overlay.
    val now = AnimationUtils.currentAnimationTimeMillis()
    advanceReveals(now)

    activeKeys.clear()
    for (span in ctx.spans) {
      if (span.revealed) continue
      val spanStart = ctx.text.getSpanStart(span)
      val spanEnd = ctx.text.getSpanEnd(span)
      if (spanStart < 0 || spanEnd < 0 || spanStart >= spanEnd) continue

      collectLineSegments(ctx, span, spanStart, spanEnd)
      val reveal = reveals[span]
      for ((index, lineSegment) in lineSegments.withIndex()) {
        val key = SegmentKey(span, lineSegment.line, lineSegment.start, lineSegment.end)
        val existing = segments[key]
        // A reveal in flight only fades out the segments it started with.
        if (existing == null && reveal != null) continue
        val live =
          existing ?: LiveSegment(
            span = span,
            overlay = spoilerOverlay.createSegmentOverlay(this, style),
            segment = SpoilerSegment(spanStart, spanEnd, lineSegment.start, lineSegment.end, ctx.text),
          ).also { segments[key] = it }
        activeKeys.add(key)

        live.segment.place(
          layout = ctx.layout,
          rect = lineSegment.rect,
          lineBaseline = ctx.layout.getLineBaseline(lineSegment.line).toFloat(),
          paddingLeft = ctx.paddingLeft,
          paddingTop = ctx.paddingTop,
          isRtl = ctx.layout.getParagraphDirection(lineSegment.line) == Layout.DIR_RIGHT_TO_LEFT,
          index = index,
          count = lineSegments.size,
          frameTimeMillis = now,
        )
        drawSegment(canvas, live, lineSegment.rect, reveal?.progressAt(now))
      }
    }

    pruneStaleSegments()

    if (reveals.isNotEmpty() || segments.values.any { it.overlay.isAnimated }) {
      animator.requestFrame()
    }
  }

  fun revealSpan(
    span: SpoilerSpan,
    onAllComplete: () -> Unit,
  ) {
    if (span.revealed) {
      onAllComplete()
      return
    }
    val inFlight = reveals[span]
    if (inFlight != null) {
      reveals[span] =
        Reveal(inFlight.startTime) {
          inFlight.onComplete()
          onAllComplete()
        }
      return
    }
    // Nothing on screen to fade out: the span was never drawn, or has no area.
    if (segments.keys.none { it.span === span }) {
      span.markRevealed()
      refreshText(span)
      onAllComplete()
      return
    }
    span.markRevealing()
    reveals[span] = Reveal(AnimationUtils.currentAnimationTimeMillis(), onAllComplete)
    textViewReference.get()?.invalidate()
  }

  fun stop() {
    animator.stop()
    segments.keys.toList().forEach(::dropSegment)
    reveals.keys.toList().forEach(::finishReveal)
  }

  private fun onFrame() {
    val textView = textViewReference.get() ?: return stop()
    // Ahead of the draw, so the text is drawn at the alpha the overlays fade by this frame.
    advanceReveals(AnimationUtils.currentAnimationTimeMillis())
    textView.invalidate()
  }

  private fun collectLineSegments(
    ctx: SpoilerDrawContext,
    span: SpoilerSpan,
    spanStart: Int,
    spanEnd: Int,
  ) {
    lineSegments.clear()

    // The span may be set in a different size than the view (e.g. in a heading), so measure the
    // band it covers with its block's metrics rather than the view's.
    metricsPaint.set(ctx.layout.paint)
    metricsPaint.textSize = span.blockStyle.fontSize
    metricsPaint.getFontMetrics(fontMetrics)

    val firstLine = ctx.layout.getLineForOffset(spanStart)
    val lastLine = ctx.layout.getLineForOffset(spanEnd)
    for (line in firstLine..lastLine) {
      val segmentStart = maxOf(spanStart, ctx.layout.getLineStart(line))
      val segmentEnd = minOf(spanEnd, ctx.layout.getLineEnd(line))
      if (segmentStart >= segmentEnd) continue

      val rect =
        computeSegmentRect(
          ctx.layout,
          line,
          segmentStart,
          segmentEnd,
          fontMetrics,
          ctx.paddingLeft,
          ctx.paddingTop,
        ) ?: continue
      lineSegments.add(LineSegment(line, segmentStart, segmentEnd, rect))
    }
  }

  private fun drawSegment(
    canvas: Canvas,
    live: LiveSegment,
    rect: SegmentRect,
    revealProgress: Float?,
  ) {
    canvas.withTranslation(rect.left, rect.top) {
      clipRect(0f, 0f, rect.width, rect.height)
      if (revealProgress != null) {
        live.overlay.drawReveal(this, live.segment, revealProgress)
      } else {
        live.overlay.draw(this, live.segment)
      }
    }
  }

  // The text fades in as the overlay fades out, on the same curve, and a reveal ends by removing
  // its segments.
  private fun advanceReveals(now: Long) {
    if (reveals.isEmpty()) return
    val finished = mutableListOf<SpoilerSpan>()
    for ((span, reveal) in reveals.entries.toList()) {
      val progress = reveal.progressAt(now)
      val textAlpha = 1f - overlayAlphaAt(progress)
      if (span.textAlpha != textAlpha) {
        span.textAlpha = textAlpha
        refreshText(span)
      }
      if (progress >= 1f) finished.add(span)
    }
    finished.forEach(::finishReveal)
  }

  private fun finishReveal(span: SpoilerSpan) {
    val reveal = reveals.remove(span) ?: return
    segments.keys.filter { it.span === span }.forEach { key -> segments.remove(key)?.overlay?.onRemoved() }
    span.markRevealed()
    refreshText(span)
    reveal.onComplete()
  }

  private fun pruneStaleSegments() {
    if (segments.size == activeKeys.size) return
    segments.keys.filter { it !in activeKeys }.forEach(::dropSegment)
  }

  // A reflow, an overlay switch or a detach can drop a segment mid-reveal; finishing the reveal
  // keeps the span from being stuck in `revealing` with nothing left to complete it.
  private fun dropSegment(key: SegmentKey) {
    val live = segments.remove(key) ?: return
    live.overlay.onRemoved()
    finishReveal(live.span)
  }

  private fun rebuild() {
    segments.keys.toList().forEach(::dropSegment)
    textViewReference.get()?.invalidate()
  }

  // A selectable TextView caches its rendered text per block, which a plain invalidate() does not
  // refresh; setting the span again marks its range dirty.
  private fun refreshText(span: SpoilerSpan) {
    val textView = textViewReference.get() ?: return
    val text = textView.text as? Spannable
    val start = text?.getSpanStart(span) ?: -1
    if (text != null && start >= 0) {
      text.setSpan(span, start, text.getSpanEnd(span), text.getSpanFlags(span))
    }
    textView.invalidate()
  }

  private fun buildContext(): SpoilerDrawContext? {
    val textView = textViewReference.get() ?: return null
    val layout = textView.layout ?: return null
    val text = textView.text as? Spanned ?: return null
    val spans = text.getSpans(0, text.length, SpoilerSpan::class.java)
    if (spans.isEmpty()) return null
    return SpoilerDrawContext(
      textView = textView,
      layout = layout,
      text = text,
      spans = spans,
      paddingLeft = textView.totalPaddingLeft.toFloat(),
      paddingTop = textView.totalPaddingTop.toFloat(),
    )
  }

  companion object {
    fun setupIfNeeded(
      textView: TextView,
      styledText: CharSequence,
      existing: SpoilerOverlayDrawer?,
      spoilerOverlay: SpoilerOverlay = SpoilerOverlay.Particles(),
    ): SpoilerOverlayDrawer? {
      if (styledText !is Spanned) return tearDown(existing)
      val spans = styledText.getSpans(0, styledText.length, SpoilerSpan::class.java)
      if (spans.isEmpty()) return tearDown(existing)
      val drawer = existing ?: SpoilerOverlayDrawer(textView)
      drawer.spoilerOverlay = spoilerOverlay
      drawer.registerSpans(spans)
      return drawer
    }

    /**
     * A restyle renders the same content again with fresh spans, so reveals the reader already
     * made are carried over by position whenever the text itself is unchanged.
     */
    fun carryOverReveals(
      previousText: CharSequence?,
      nextText: CharSequence,
    ) {
      if (previousText !is Spanned || nextText !is Spanned) return
      if (previousText.toString() != nextText.toString()) return
      val previous = previousText.spoilerSpansInOrder()
      val next = nextText.spoilerSpansInOrder()
      if (previous.size != next.size) return
      previous.zip(next).forEach { (old, new) ->
        if (old.revealed || old.revealing) new.markRevealed()
      }
    }

    private fun Spanned.spoilerSpansInOrder(): List<SpoilerSpan> =
      getSpans(0, length, SpoilerSpan::class.java).sortedBy { getSpanStart(it) }

    private fun tearDown(existing: SpoilerOverlayDrawer?): Nothing? {
      existing?.stop()
      return null
    }
  }
}
