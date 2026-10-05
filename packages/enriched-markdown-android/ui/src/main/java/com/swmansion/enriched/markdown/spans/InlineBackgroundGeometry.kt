package com.swmansion.enriched.markdown.spans

import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * Where an inline background (code, highlight) goes horizontally on each line its span covers. A
 * span owns one and passes on the view it registers with, whose layout places the background
 * exactly for any alignment and direction.
 */
internal class InlineBackgroundGeometry {
  // Weak, so a rendered text that outlives its view does not keep the view alive.
  private var textViewRef: WeakReference<TextView>? = null

  fun registerTextView(view: TextView) {
    if (textViewRef?.get() !== view) textViewRef = WeakReference(view)
  }

  /**
   * Sets [out]'s left and right to the x range of [spanStart]..[spanEnd] on [line], which holds
   * [lineStart]..[lineEnd] of [text] and is drawn between [left] and [right]. On a line the span
   * continues onto or past, the range runs to the line's glyphs, which only a layout knows for every
   * alignment and direction; without one it runs to the view edges.
   */
  fun horizontalBounds(
    text: Spanned,
    line: Int,
    lineStart: Int,
    lineEnd: Int,
    spanStart: Int,
    spanEnd: Int,
    left: Int,
    right: Int,
    paint: Paint,
    out: RectF,
  ) {
    val isFirst = spanStart >= lineStart
    val isLast = spanEnd <= lineEnd
    // The layout drawing this line, when the view showing the text registered with this span. Its
    // x positions are in the same frame as left and right, which the layout draws from.
    val layout = textViewRef?.get()?.layout?.takeIf { it.text === text }
    val startX =
      when {
        layout != null && isFirst -> layout.horizontalOnLine(spanStart, line)
        layout != null -> layout.leadingEdge(line)
        isFirst -> left + measuredOffset(text, lineStart, spanStart, paint)
        else -> left.toFloat() + leadingMarginAt(text, lineStart)
      }
    val endX =
      when {
        layout != null && isLast -> layout.horizontalOnLine(spanEnd, line)
        layout != null -> layout.trailingEdge(line)
        isLast -> left + measuredOffset(text, lineStart, spanEnd, paint)
        else -> right.toFloat()
      }
    out.left = min(startX, endX)
    out.right = max(startX, endX)
  }

  /**
   * The x of [offset] on [line]. An offset at the end of a wrapped line also starts the next
   * line, where getPrimaryHorizontal would place it, so the line's trailing edge is used instead.
   */
  private fun Layout.horizontalOnLine(
    offset: Int,
    line: Int,
  ): Float {
    if (offset < getLineEnd(line) || line == lineCount - 1) return getPrimaryHorizontal(offset)
    return trailingEdge(line)
  }

  /** The x where [line]'s first character is drawn: its right edge in right-to-left text. */
  private fun Layout.leadingEdge(line: Int): Float = getPrimaryHorizontal(getLineStart(line))

  /**
   * The x where [line]'s last glyph ends, leaving out trailing whitespace: its left edge in
   * right-to-left text. It is read where the whitespace starts, as getLineLeft and getLineRight
   * round centered lines differently from where the layout draws them. A line broken mid-word has
   * no whitespace to read, and its offset there would start the next line.
   */
  private fun Layout.trailingEdge(line: Int): Float {
    val lineEnd = getLineEnd(line)
    var glyphsEnd = lineEnd
    while (glyphsEnd > getLineStart(line) && text[glyphsEnd - 1].isWhitespace()) glyphsEnd--
    if (glyphsEnd < lineEnd) return getPrimaryHorizontal(glyphsEnd)
    return if (getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT) getLineLeft(line) else getLineRight(line)
  }

  /**
   * The x of [index] relative to the line's left edge, for text drawn by a view that did not
   * register with the span. It measures the line from its start, so it is exact only for
   * left-to-right text aligned to the start; a registered view's layout is exact for any
   * alignment and direction.
   */
  private fun measuredOffset(
    text: Spanned,
    lineStart: Int,
    index: Int,
    paint: Paint,
  ): Float {
    if (index <= lineStart) return leadingMarginAt(text, lineStart).toFloat()
    val textPaint = paint as? TextPaint ?: TextPaint(paint)
    // getDesiredWidth already adds the paragraph's leading margin.
    return Layout.getDesiredWidth(text, lineStart, index, textPaint)
  }

  private fun leadingMarginAt(
    text: Spanned,
    lineStart: Int,
  ): Int {
    if (lineStart >= text.length) return 0
    val spans = text.getSpans(lineStart, lineStart + 1, LeadingMarginSpan::class.java)
    var margin = 0
    for (span in spans) {
      margin += span.getLeadingMargin(false)
    }
    return margin
  }
}
