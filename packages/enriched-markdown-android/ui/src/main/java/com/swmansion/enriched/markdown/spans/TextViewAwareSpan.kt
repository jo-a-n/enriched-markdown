package com.swmansion.enriched.markdown.spans

import android.text.Spanned
import android.widget.TextView

/**
 * A span that needs the view showing its text, for what the text alone does not tell it: the width
 * an image may take, or the layout that draws a background.
 */
interface TextViewAwareSpan {
  /** Called with the view [registerWithSpans] gave the span's text to. */
  fun registerTextView(view: TextView)
}

/**
 * Registers this view with every [TextViewAwareSpan] in [text]. Called wherever rendered markdown is
 * given to a view: the segment creators and table cells.
 */
internal fun TextView.registerWithSpans(text: CharSequence?) {
  if (text !is Spanned) return
  for (span in text.getSpans(0, text.length, TextViewAwareSpan::class.java)) {
    span.registerTextView(this)
  }
}
