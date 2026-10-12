package com.pawkeys.keyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.pawkeys.keyboard.engine.Suggestion
import com.pawkeys.keyboard.settings.SettingsActivity
import com.pawkeys.keyboard.settings.ShortcutsActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders every part of the keyboard UI to PNGs in build/screenshots, used for
 * docs/UI.md. Run with: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val width = 393 * 3

    private fun startIme(
        theme: String = "calico",
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        imeAction: Int = EditorInfo.IME_ACTION_SEND,
        configure: (Prefs) -> Unit = {},
    ): Pair<PawKeysIME, View> {
        val ime = Robolectric.setupService(PawKeysIME::class.java)
        val prefs = Prefs(ime)
        prefs.theme = theme
        configure(prefs)
        val view = ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { this.inputType = inputType; imeOptions = imeAction }, false)
        layout(view)
        return ime to view
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        ShadowLooper.idleMainLooper()
    }

    private fun render(view: View, name: String) {
        layout(view)
        val bmp = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        save(bmp, name)
    }

    private fun save(bmp: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderActivity(activity: Activity, name: String) {
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(852 * 3, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        ShadowLooper.idleMainLooper()
        val bmp = Bitmap.createBitmap(decor.measuredWidth, decor.measuredHeight, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bmp))
        save(bmp, name)
    }

    private fun strip(root: View) = ((root as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0) as CandidateStrip

    private fun findKeyboard(v: View): KeyboardView? = when (v) {
        is KeyboardView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findKeyboard(v.getChildAt(it)) }
        else -> null
    }

    private fun findByDescription(v: View, desc: String): View? = when {
        v.contentDescription == desc -> v
        v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findByDescription(v.getChildAt(it), desc) }
        else -> null
    }

    private fun keyboard(root: View) = findKeyboard(root)!!

    private fun touch(kv: KeyboardView, action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        kv.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun key(code: Int) = Key(code, "")

    // ---------------------------------------------------------------- keys

    @Test fun mainKeyboard() {
        val (ime, view) = startIme()
        render(view, "01_keyboard")
        ime.onKey(key(Codes.SHIFT))
        render(view, "02_shift_on")
        ime.onKey(key(Codes.SHIFT))
        render(view, "03_caps_lock")
    }

    @Test fun symbolPages() {
        val (ime, view) = startIme()
        ime.onKey(key(Codes.SYMBOLS))
        render(view, "04_symbols_1")
        ime.onKey(key(Codes.SYMBOLS_MORE))
        render(view, "05_symbols_2")
    }

    @Test fun fieldLayouts() {
        render(startIme(inputType = InputType.TYPE_CLASS_NUMBER, imeAction = EditorInfo.IME_ACTION_DONE).second, "06_number_pad")
        render(startIme(inputType = InputType.TYPE_CLASS_PHONE, imeAction = EditorInfo.IME_ACTION_GO).second, "07_phone_pad")
        render(startIme(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, imeAction = EditorInfo.IME_ACTION_NEXT).second, "08_email")
        render(startIme(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, imeAction = EditorInfo.IME_ACTION_GO).second, "09_url")
        render(startIme(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, imeAction = EditorInfo.IME_ACTION_DONE).second, "10_password")
        render(startIme(imeAction = EditorInfo.IME_ACTION_SEARCH) { it.numberRow = true }.second, "11_number_row_search")
    }

    // ---------------------------------------------------------------- strip

    @Test fun suggestionStrip() {
        val (_, view) = startIme()
        strip(view).setSuggestions(listOf(Suggestion("kitten", 0.0, true), Suggestion("kittem", 0.0), Suggestion("kittens", 0.0)), 0)
        render(view, "12_suggestions_autocorrect")
        strip(view).setSuggestions(listOf(Suggestion("purr", 0.0), Suggestion("pure", 0.0), Suggestion("purple", 0.0)), -1)
        render(view, "13_suggestions_completions")
        strip(view).setSuggestions(listOf(Suggestion("On my way! 🐾", 0.0, isShortcut = true), Suggestion("omw", 0.0)), -1)
        render(view, "14_suggestions_shortcut")
    }

    // ---------------------------------------------------------------- touch feedback

    @Test fun keyPreviewAndPopups() {
        val (_, view) = startIme()
        val kv = keyboard(view)
        var (x, y) = kv.letterCenters()['g']!!
        var down = SystemClock.uptimeMillis()
        touch(kv, MotionEvent.ACTION_DOWN, x, y, down)
        render(view, "15_key_preview")
        touch(kv, MotionEvent.ACTION_CANCEL, x, y, down)

        kv.letterCenters()['e']!!.let { x = it.first; y = it.second }
        down = SystemClock.uptimeMillis()
        touch(kv, MotionEvent.ACTION_DOWN, x, y, down)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        touch(kv, MotionEvent.ACTION_MOVE, x + kv.unitWidth * 1.2f, y - kv.unitWidth, down)
        render(view, "16_longpress_accents")
        touch(kv, MotionEvent.ACTION_CANCEL, x, y, down)

        // The full-stop key: bottom row, second from the right.
        val bottom = keyboard(view).let { it.height - it.unitWidth * 0.8f }
        val dotX = width - kv.unitWidth * 2.0f
        down = SystemClock.uptimeMillis()
        touch(kv, MotionEvent.ACTION_DOWN, dotX, bottom, down)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        render(view, "17_longpress_punctuation")
        touch(kv, MotionEvent.ACTION_CANCEL, dotX, bottom, down)
    }

    @Test fun swipeTyping() {
        val (_, view) = startIme { it.gestureMode = "glide" }
        val kv = keyboard(view)
        val down = SystemClock.uptimeMillis()
        val path = "kitten".map { kv.letterCenters()[it]!! }
        touch(kv, MotionEvent.ACTION_DOWN, path[0].first, path[0].second, down)
        for (i in 0 until path.size - 1) {
            for (s in 1..14) {
                val t = s / 14f
                // A hand-drawn stroke bows a little between keys.
                val bow = kotlin.math.sin(t * Math.PI).toFloat() * kv.unitWidth * 0.35f
                touch(kv, MotionEvent.ACTION_MOVE,
                    path[i].first + (path[i + 1].first - path[i].first) * t,
                    path[i].second + (path[i + 1].second - path[i].second) * t - bow, down)
            }
            if (i == 2) render(view, "18_swipe_typing")
        }
        render(view, "18b_swipe_typing_turn")
        touch(kv, MotionEvent.ACTION_CANCEL, 0f, 0f, down)
    }

    // ---------------------------------------------------------------- panels

    @Test fun panels() {
        fun panel(action: CandidateStrip.ToolbarAction, name: String, theme: String = "calico") {
            val (ime, view) = startIme(theme)
            ime.onToolbarAction(action)
            render(view, name)
        }
        panel(CandidateStrip.ToolbarAction.MENU, "20_paw_menu")
        panel(CandidateStrip.ToolbarAction.EMOJI, "21_emoji")
        panel(CandidateStrip.ToolbarAction.TEXT_EDIT, "24_text_editing")
        panel(CandidateStrip.ToolbarAction.SIZE, "26_keyboard_size")

        val (ime, view) = startIme()
        ime.getSystemService(ClipboardManager::class.java).apply {
            setPrimaryClip(ClipData.newPlainText("", "https://github.com/shreyashgiri/Pawkey"))
            setPrimaryClip(ClipData.newPlainText("", "Feed the cat at 6pm 🐟"))
            setPrimaryClip(ClipData.newPlainText("", "Meeting notes: ship PawKeys v1, add more themes, test swipe typing on small phones"))
        }
        ShadowLooper.idleMainLooper()
        ime.onToolbarAction(CandidateStrip.ToolbarAction.CLIPBOARD)
        render(view, "23_clipboard")
        ime.onToolbarAction(CandidateStrip.ToolbarAction.CLIPBOARD)

        // Clipboard: pin the first clip.
        ime.onToolbarAction(CandidateStrip.ToolbarAction.CLIPBOARD)
        findByDescription(view, "Pin")?.performClick()
        render(view, "23b_clipboard_pinned")

        val (ime2, v2) = startIme()
        ime2.onToolbarAction(CandidateStrip.ToolbarAction.MENU)
        layout(v2)
        findByDescription(v2, "Coats")!!.performClick()
        render(v2, "25_themes_panel")

        val (ime3, v3) = startIme()
        ime3.onToolbarAction(CandidateStrip.ToolbarAction.TEXT_EDIT)
        layout(v3)
        (v3.findViewsWithText("Select")).forEach { it.performClick() }
        render(v3, "24b_text_editing_select")
    }

    private fun View.findViewsWithText(text: String): List<View> {
        val list = ArrayList<View>()
        findViewsWithText(list, text, View.FIND_VIEWS_WITH_TEXT)
        return list.filter { (it as? android.widget.TextView)?.text == text }
    }

    @Test fun oneHanded() {
        render(startIme { it.oneHanded = "right" }.second, "27_one_handed_right")
        render(startIme("tuxedo") { it.oneHanded = "left" }.second, "28_one_handed_left")
    }

    @Test fun themes() {
        for (t in KeyboardTheme.ALL) render(startIme(t.id).second, "30_theme_${t.id}")
        val (ime, view) = startIme("tuxedo")
        ime.onToolbarAction(CandidateStrip.ToolbarAction.EMOJI)
        render(view, "22_emoji_dark")
        render(startIme("calico") { it.heightPercent = 85; it.numberRow = false }.second, "29_size_small")
    }

    @Test fun emojiSearch() {
        val (ime, view) = startIme()
        ime.onToolbarAction(CandidateStrip.ToolbarAction.EMOJI)
        ime.startEmojiSearch()
        "cat".forEach { ime.onKey(Key(it.code, it.toString())) }
        render(view, "65_emoji_search")
    }

    @Test fun voice() {
        val (ime, view) = startIme()
        org.robolectric.Shadows.shadowOf(ime.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        ime.recognitionAvailable = { true }
        ime.onToolbarAction(CandidateStrip.ToolbarAction.VOICE)
        ShadowLooper.idleMainLooper()
        val rec = org.robolectric.Shadows.shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        rec.triggerOnRmsChanged(7f)
        rec.triggerOnPartialResults(android.os.Bundle().apply {
            putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("feed the cat at six"))
        })
        render(view, "64_voice")
    }

    @Test fun translate() {
        val (ime, view) = startIme()
        ime.translator = object : com.pawkeys.keyboard.translate.TextTranslator {
            override fun prepare(from: String, to: String, done: (String?) -> Unit) = done(null)
            override fun translate(text: String, from: String, to: String, done: (String?, String?) -> Unit) =
                done(if (text == "Good morning, how are you?") "सुप्रभात, आप कैसे हैं?" else text, null)
            override fun close() = Unit
        }
        ime.onToolbarAction(CandidateStrip.ToolbarAction.TRANSLATE)
        "good morning, how are you?".forEach { ch ->
            ime.onKey(if (ch == ' ') key(Codes.SPACE) else Key(ch.code, ch.toString()))
        }
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        render(view, "62_translate")
        ime.onPickLanguage(false)
        render(view, "63_translate_languages")
    }

    @Test fun stickers() {
        val (ime, view) = startIme()
        ime.onToolbarAction(CandidateStrip.ToolbarAction.STICKERS)
        render(view, "60_stickers_panel")
        // Full sheet of the pack at send size.
        val size = 256
        val pack = com.pawkeys.keyboard.stickers.StickerArt.pack
        val sheet = Bitmap.createBitmap(size * 4, size * 3, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFFE9EAEE.toInt())
        pack.forEachIndexed { i, st ->
            c.drawBitmap(com.pawkeys.keyboard.stickers.StickerArt.render(st, size), (i % 4) * size.toFloat(), (i / 4) * size.toFloat(), null)
        }
        save(sheet, "61_sticker_pack")
    }

    @Test fun customizeBar() {
        val (ime, view) = startIme {
            it.toolbarButtons = listOf(CandidateStrip.ToolbarAction.EMOJI, CandidateStrip.ToolbarAction.CLIPBOARD,
                CandidateStrip.ToolbarAction.THEMES, CandidateStrip.ToolbarAction.SOUND)
        }
        render(view, "54_custom_bar")
        ime.onToolbarAction(CandidateStrip.ToolbarAction.MENU)
        layout(view)
        view.findViewsWithText("Customize").first().performClick()
        render(view, "55_den_customize")
    }

    @Test fun breeds() {
        for (b in Breed.entries) {
            render(startIme("system") { it.breed = b }.second, "50_breed_${b.id}")
            // Dark variant: use the breed's dark coat directly.
            render(startIme(b.darkCoat.id) { it.breed = b }.second, "51_breed_${b.id}_dark")
        }
        val (ime, view) = startIme()
        ime.onToolbarAction(CandidateStrip.ToolbarAction.MENU)
        layout(view)
        findByDescription(view, "Breeds")!!.performClick()
        render(view, "52_breeds_panel")
        val (ime2, v2) = startIme("system", imeAction = EditorInfo.IME_ACTION_SEARCH) { it.breed = Breed.IPHONE }
        ime2.onKey(key(Codes.SYMBOLS))
        render(v2, "53_breed_iphone_symbols")
    }

    // ---------------------------------------------------------------- app screens

    @Test fun settingsScreens() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        renderActivity(settings, "40_settings")
        val shortcuts = Robolectric.buildActivity(ShortcutsActivity::class.java).setup().get()
        renderActivity(shortcuts, "41_text_shortcuts")
    }
}
