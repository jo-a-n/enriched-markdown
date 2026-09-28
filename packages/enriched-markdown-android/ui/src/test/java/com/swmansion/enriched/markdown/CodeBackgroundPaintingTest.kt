package com.swmansion.enriched.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Spannable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swmansion.enriched.markdown.parser.MarkdownASTNode
import com.swmansion.enriched.markdown.spans.CodeBackgroundSpan
import com.swmansion.enriched.markdown.styles.StyleConfig
import com.swmansion.enriched.markdown.styles.TextAlignment
import com.swmansion.enriched.markdown.test.MarkdownRenderTestSupport
import com.swmansion.enriched.markdown.test.MarkdownRenderTestSupport.render
import com.swmansion.enriched.markdown.test.TestAstFactory.code
import com.swmansion.enriched.markdown.test.TestAstFactory.document
import com.swmansion.enriched.markdown.test.TestAstFactory.listItem
import com.swmansion.enriched.markdown.test.TestAstFactory.paragraph
import com.swmansion.enriched.markdown.test.TestAstFactory.text
import com.swmansion.enriched.markdown.test.TestAstFactory.unorderedList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers where an inline code background is painted: over the code's glyphs, as the layout that
 * draws the text places them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
// Robolectric's legacy graphics report zero text widths; the native runtime measures for real.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CodeBackgroundPaintingTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  private companion object {
    const val WIDTH = 400
    const val BACKGROUND = 0xFF224488.toInt()
  }

  /** A [Canvas] that remembers the bounds of every path filled in [BACKGROUND]. */
  private class RecordingCanvas(
    bitmap: Bitmap,
  ) : Canvas(bitmap) {
    val backgrounds = mutableListOf<RectF>()

    override fun drawPath(
      path: Path,
      paint: Paint,
    ) {
      if (paint.style == Paint.Style.FILL && paint.color == BACKGROUND) {
        backgrounds.add(RectF().also { path.computeBounds(it, true) })
      }
      super.drawPath(path, paint)
    }
  }

  private fun style(textAlign: TextAlignment? = null): StyleConfig =
    MarkdownRenderTestSupport.styleWithCode(
      MarkdownRenderTestSupport.defaultStyle.codeStyle.copy(backgroundColor = BACKGROUND),
      textAlign,
    )

  private class Drawn(
    val textView: TextView,
    val rendered: Spannable,
    val backgrounds: List<RectF>,
  ) {
    private val span = rendered.getSpans(0, rendered.length, CodeBackgroundSpan::class.java).single()
    val codeStart: Int get() = rendered.getSpanStart(span)
    val codeEnd: Int get() = rendered.getSpanEnd(span)
  }

  private fun draw(
    document: MarkdownASTNode,
    style: StyleConfig = style(),
    textView: TextView = EnrichedMarkdownInternalText(context),
  ): Drawn {
    val rendered = render(document, style)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.paragraphStyle.fontSize)
    textView.text = rendered
    textView.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    textView.layout(0, 0, WIDTH, textView.measuredHeight)

    val canvas = RecordingCanvas(Bitmap.createBitmap(WIDTH, maxOf(textView.height, 1), Bitmap.Config.ARGB_8888))
    requireNotNull(textView.layout).draw(canvas)
    return Drawn(textView, rendered, canvas.backgrounds)
  }

  private fun assertCoversTheCode(drawn: Drawn) {
    val layout = requireNotNull(drawn.textView.layout)
    val background = drawn.backgrounds.single()
    assertEquals(layout.getPrimaryHorizontal(drawn.codeStart), background.left, 0.01f)
    assertEquals(layout.getPrimaryHorizontal(drawn.codeEnd), background.right, 0.01f)
  }

  @Test
  fun theBackgroundCoversTheCode() {
    assertCoversTheCode(draw(document(paragraph(text("call "), code("render()"), text(" once")))))
  }

  @Test
  fun theBackgroundFollowsCenteredText() {
    val drawn = draw(document(paragraph(text("call "), code("render()"), text(" once"))), style(TextAlignment.CENTER))

    assertTrue("The line must be centered for this to be meaningful", drawn.backgrounds.single().left > WIDTH / 4f)
    assertCoversTheCode(drawn)
  }

  @Test
  fun theBackgroundFollowsTheListIndent() {
    val drawn = draw(document(unorderedList(listItem(paragraph(code("render()"))))))

    assertTrue("The code must be indented for this to be meaningful", drawn.backgrounds.single().left > 0f)
    assertCoversTheCode(drawn)
  }

  @Test
  fun aViewThatDidNotRegisterStillPlacesStartAlignedCode() {
    assertCoversTheCode(
      draw(document(paragraph(text("call "), code("render()"), text(" once"))), textView = TextView(context)),
    )
  }
}
