package com.pawkey.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.pawkey.keyboard.engine.Suggestion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders the real keyboard views to PNGs in build/screenshots so the look can be
 * checked without a device. Run with: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun startIme(theme: String = "calico", configure: (Prefs) -> Unit = {}): Pair<PawkeyIME, View> {
        val ime = Robolectric.setupService(PawkeyIME::class.java)
        val prefs = Prefs(ime)
        prefs.theme = theme
        configure(prefs)
        val view = ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }, false)
        return ime to view
    }

    private fun render(view: View, name: String) {
        val width = (393 * 3)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        ShadowLooper.idleMainLooper()
        val bmp = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun strip(root: View) = ((root as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0) as CandidateStrip
    private fun keyboard(root: View): KeyboardView {
        fun find(v: View): KeyboardView? = when (v) {
            is KeyboardView -> v
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        return find(root)!!
    }

    private fun touch(kv: KeyboardView, action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        kv.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun keyCenter(kv: KeyboardView, c: Char) = kv.letterCenters()[c]!!

    @Test fun themes() {
        for (t in KeyboardTheme.ALL) {
            val (_, view) = startIme(t.id)
            render(view, "theme_${t.id}")
        }
    }

    @Test fun typingWithSuggestionsAndPreview() {
        val (_, view) = startIme("calico")
        render(view, "layout_measure")
        strip(view).setSuggestions(listOf(Suggestion("kitten", 0.0, true), Suggestion("kittem", 0.0), Suggestion("kittens", 0.0)), 0)
        val kv = keyboard(view)
        val (x, y) = keyCenter(kv, 'g')
        val down = SystemClock.uptimeMillis()
        touch(kv, MotionEvent.ACTION_DOWN, x, y, down)
        render(view, "typing_preview")
        touch(kv, MotionEvent.ACTION_CANCEL, x, y, down)
    }

    @Test fun longPressPopup() {
        val (_, view) = startIme("tuxedo")
        render(view, "layout_measure2")
        val kv = keyboard(view)
        val (x, y) = keyCenter(kv, 'e')
        val down = SystemClock.uptimeMillis()
        touch(kv, MotionEvent.ACTION_DOWN, x, y, down)
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        render(view, "longpress_popup")
        touch(kv, MotionEvent.ACTION_CANCEL, x, y, down)
    }

    @Test fun glideTrail() {
        val (_, view) = startIme("calico") { it.gestureMode = "glide" }
        render(view, "layout_measure3")
        val kv = keyboard(view)
        val down = SystemClock.uptimeMillis()
        val path = "kitten".map { keyCenter(kv, it) }
        touch(kv, MotionEvent.ACTION_DOWN, path[0].first, path[0].second, down)
        for (i in 0 until path.size - 1) for (s in 1..10) {
            val t = s / 10f
            touch(kv, MotionEvent.ACTION_MOVE, path[i].first + (path[i + 1].first - path[i].first) * t, path[i].second + (path[i + 1].second - path[i].second) * t, down)
        }
        render(view, "glide_trail")
        touch(kv, MotionEvent.ACTION_CANCEL, 0f, 0f, down)
    }

    @Test fun panels() {
        for (action in listOf(
            CandidateStrip.ToolbarAction.MENU, CandidateStrip.ToolbarAction.EMOJI,
            CandidateStrip.ToolbarAction.CLIPBOARD, CandidateStrip.ToolbarAction.TEXT_EDIT,
        )) {
            val (ime, view) = startIme("calico")
            render(view, "layout_measure4")
            ime.onToolbarAction(action)
            render(view, "panel_${action.name.lowercase()}")
        }
        val (ime, view) = startIme("tuxedo")
        render(view, "layout_measure5")
        ime.onToolbarAction(CandidateStrip.ToolbarAction.EMOJI)
        render(view, "panel_emoji_dark")
    }

    @Test fun variants() {
        val (_, a) = startIme("siamese") { it.numberRow = true; it.oneHanded = "right" }
        render(a, "one_handed_number_row")
        val ime = Robolectric.setupService(PawkeyIME::class.java)
        Prefs(ime).theme = "ragdoll"
        val v = ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_PHONE }, false)
        render(v, "phone_pad")
    }
}
