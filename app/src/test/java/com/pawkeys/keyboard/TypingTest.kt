package com.pawkeys.keyboard

import android.app.Activity
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Drives the real keyboard service against a real EditText. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypingTest {
    private lateinit var ime: PawKeysIME
    private lateinit var field: EditText
    private lateinit var keys: Map<Int, Key>

    @Before fun setUp() {
        // FileProvider caches its folder per authority in a static; each Robolectric
        // test gets a new data folder, so clear it (on a phone the folder never changes).
        runCatching {
            androidx.core.content.FileProvider::class.java.declaredFields
                .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type) }
                .forEach { it.isAccessible = true; (it.get(null) as MutableMap<*, *>).clear() }
        }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        field = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        activity.setContentView(field)
        field.requestFocus()

        ime = Robolectric.setupService(PawKeysIME::class.java)
        val info = EditorInfo()
        val ic = field.onCreateInputConnection(info)
        // Hook the service up to the field the way the framework would.
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(ime, ic)
        InputMethodService::class.java.getDeclaredField("mInputEditorInfo").apply { isAccessible = true }.set(ime, info)
        ime.onCreateInputView()
        ime.onStartInputView(info, false)
        waitForDictionary()
        keys = Layouts.build(LayoutId.QWERTY, false, FieldVariant.TEXT).rows.flatten().associateBy { it.code }
    }

    private fun waitForDictionary() {
        val f = PawKeysIME::class.java.getDeclaredField("glide").apply { isAccessible = true }
        repeat(200) { if (f.get(ime) != null) return; Thread.sleep(25) }
        error("dictionary didn't load")
    }

    private fun type(s: String) {
        for (ch in s) {
            when (ch) {
                ' ' -> ime.onKey(keys.getValue(Codes.SPACE))
                '⌫' -> ime.onKey(keys.getValue(Codes.DELETE))
                else -> ime.onKey(keys[ch.lowercaseChar().code] ?: Key(ch.code, ch.toString()))
            }
            ShadowLooper.idleMainLooper()
        }
    }

    private val text get() = field.text.toString()

    @Test fun autocorrectsOnSpaceAndCapitalises() {
        type("teh cat ")
        assertEquals("The cat ", text)
    }

    @Test fun backspaceUndoesAutocorrect() {
        type("teh ")
        assertEquals("The ", text)
        type("⌫")
        assertEquals("Teh", text)
        type(" ")
        assertEquals("Teh ", text)
    }

    @Test fun doubleSpaceAddsFullStop() {
        type("hi  there")
        assertEquals("Hi. There", text)
    }

    @Test fun fleksySwipeGestures() {
        type("hello")
        ime.onFlick(Flick.RIGHT)
        assertEquals("Hello ", text)
        ime.onFlick(Flick.RIGHT)
        assertEquals("Hello. ", text)
        ime.onFlick(Flick.DOWN)
        assertEquals("Hello, ", text)
        type("my friend")
        ime.onFlick(Flick.LEFT)
        assertEquals("Hello, my ", text)
        ime.onFlick(Flick.LEFT)
        assertEquals("Hello, ", text)
    }

    @Test fun smartPunctuationMovesTheSpace() {
        type("hello")
        ime.onFlick(Flick.RIGHT)
        type("!")
        assertEquals("Hello! ", text)
    }

    @Test fun pickingASuggestionAddsASpace() {
        type("kitt")
        ime.onSuggestionPicked(0, com.pawkeys.keyboard.engine.Suggestion("Kitten", 0.0))
        assertEquals("Kitten ", text)
        type("is")
        assertEquals("Kitten is", text)
    }

    @Test fun backspaceDeletesAndResumesTheWord() {
        type("cat is ")
        type("⌫⌫")
        assertEquals("Cat i", text)
        type("t ")
        assertEquals("Cat it ", text)
    }

    @Test fun textShortcutIsOffered() {
        type("omw")
        ime.onSuggestionPicked(0, com.pawkeys.keyboard.engine.Suggestion("On my way! 🐾", 0.0, isShortcut = true))
        assertEquals("On my way! 🐾 ", text)
    }

    /** Regression: an app restarting input used to leave the keys unpositioned (blank keyboard). */
    @Test fun keysStayVisibleWhenInputRestarts() {
        val view = ime.onCreateInputView()
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        ime.onStartInputView(info, false)
        fun layout() {
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        layout()
        type("a")
        ime.onStartInputView(info, true)
        layout()
        val kv = generateSequence(listOf(view)) { level ->
            level.flatMap { v -> (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } } ?: emptyList() }
                .ifEmpty { null }
        }.flatten().filterIsInstance<KeyboardView>().first()
        val q = kv.letterCenters().getValue('q')
        val p = kv.letterCenters().getValue('p')
        assert(p.first > q.first) { "keys not laid out: q=$q p=$p" }
    }

    @Test fun groomSelectCopyPaste() {
        type("hello world")
        ime.home(select = false)
        ime.selectionMode(true)
        repeat(5) { ime.moveCursor(1, 0, select = true) }
        assertEquals("Hello", field.text.substring(field.selectionStart, field.selectionEnd))
        ime.copy()
        ime.end(select = false)
        ime.paste()
        assertEquals("Hello worldHello", text)
        ime.selectAll()
        assertEquals(0, field.selectionStart)
        assertEquals(text.length, field.selectionEnd)
        ime.cut()
        assertEquals("", text)
        ime.paste()
        assertEquals("Hello worldHello", text)
    }

    @Test fun groomMovesCursorOverEmojiWhole() {
        type("a")
        ime.insertEmoji("😺")
        ime.moveCursor(-1, 0, select = false)
        assertEquals(1, field.selectionStart)
        ime.moveCursor(-1, 0, select = false)
        assertEquals(0, field.selectionStart)
    }

    @Test fun typingWithEverySoundStyle() {
        val prefs = Prefs(ime)
        prefs.sound = true
        for (style in listOf("click", "kitten", "cat", "duck", "system")) {
            prefs.soundStyle = style
            ime.onKeyDown(keys.getValue('a'.code))
            ime.onKeyDown(keys.getValue(Codes.DELETE))
        }
        type("ok")
        assertEquals("Ok", text)
    }

    @Test fun stickerFallsBackToClipboardInTextOnlyField() {
        ime.sendSticker(com.pawkeys.keyboard.stickers.StickerArt.pack.first())
        val clip = ime.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!
        val uri = clip.getItemAt(0).uri!!
        assertEquals("com.pawkeys.keyboard.files", uri.authority)
        assert(java.io.File(ime.cacheDir, "share/pawkeys_hi.png").length() > 1000)
    }

    /** Stands in for ML Kit: "translates" by tagging the text with the target language. */
    private class FakeTranslator : com.pawkeys.keyboard.translate.TextTranslator {
        override fun prepare(from: String, to: String, done: (String?) -> Unit) = done(null)
        override fun translate(text: String, from: String, to: String, done: (String?, String?) -> Unit) = done("[$to] $text", null)
        override fun close() = Unit
    }

    @Test fun translateTypesIntoTheBarAndInsertsTheTranslation() {
        ime.translator = FakeTranslator()
        ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }, false)
        ime.onToolbarAction(CandidateStrip.ToolbarAction.TRANSLATE)
        assert(ime.translating)
        type("good morning")
        assertEquals("", text) // nothing reaches the app until Insert
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        ime.onKey(keys.getValue(Codes.ENTER))
        assertEquals("[hi] Good morning", text)
        // Swap and close.
        ime.onSwapLanguages()
        assertEquals("hi", Prefs(ime).translateFrom)
        ime.onCloseTranslate()
        assert(!ime.translating)
        type(" ok")
        assertEquals("[hi] Good morning ok", text)
    }

    private fun speech(text: String) = android.os.Bundle().apply {
        putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test fun voiceTypingShowsWordsLiveAndCommitsThem() {
        org.robolectric.Shadows.shadowOf(ime.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        ime.recognitionAvailable = { true }
        ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }, false)
        type("hi.")
        ime.onToolbarAction(CandidateStrip.ToolbarAction.VOICE)
        assert(ime.listening)
        ShadowLooper.idleMainLooper()
        val rec = org.robolectric.Shadows.shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        rec.triggerOnPartialResults(speech("see you"))
        assertEquals("Hi. See you", text)
        rec.triggerOnResults(speech("see you soon"))
        assertEquals("Hi. See you soon", text)
        assert(!ime.listening)
    }

    private class FakeGifs : com.pawkeys.keyboard.gifs.GifSource {
        var lastQuery: String? = "unset"
        override fun list(query: String?, done: (List<com.pawkeys.keyboard.gifs.Gif>?, String?) -> Unit) {
            lastQuery = query
            done(listOf(com.pawkeys.keyboard.gifs.Gif("g1", "cat", "p", "f", 100, 100)), null)
        }
        override fun fetch(url: String, done: (ByteArray?) -> Unit) = done("GIF89a-fake".toByteArray())
    }

    @Test fun giphyResponseIsParsed() {
        val json = """{"data":[{"id":"abc","title":"Cat GIF","images":{
            "fixed_width_small":{"url":"https://media.giphy.com/s.gif","width":"100","height":"80"},
            "downsized":{"url":"https://media.giphy.com/d.gif"}}},
            {"id":"broken","images":{}}]}"""
        val gifs = com.pawkeys.keyboard.gifs.GiphySource.parse(json)
        assertEquals(1, gifs.size)
        assertEquals("https://media.giphy.com/d.gif", gifs[0].full)
        assertEquals(80, gifs[0].height)
    }

    @Test fun gifSearchAndSend() {
        val fake = FakeGifs()
        ime.gifSourceFactory = { fake }
        android.preference.PreferenceManager.getDefaultSharedPreferences(ime).edit().putString(Prefs.GIPHY_KEY, "test-key").commit()
        ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        ime.onToolbarAction(CandidateStrip.ToolbarAction.GIFS)
        assertEquals(null, fake.lastQuery) // trending
        ime.startGifSearch()
        type("cat")
        ime.onKey(keys.getValue(Codes.ENTER))
        assertEquals("cat", fake.lastQuery)
        assertEquals("", text) // the query never reaches the app
        ime.sendGif(com.pawkeys.keyboard.gifs.Gif("g1", "cat", "p", "f", 100, 100))
        val uri = ime.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).uri!!
        assert(uri.toString().endsWith("gif_g1.gif")) { uri.toString() }
    }

    @Test fun flicksAndProwlTogether() {
        Prefs(ime).gestureMode = "both"
        val root = ime.onCreateInputView()
        ime.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val kv = generateSequence(listOf(root)) { level ->
            level.flatMap { v -> (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } } ?: emptyList() }
                .ifEmpty { null }
        }.flatten().filterIsInstance<KeyboardView>().first()
        val c = kv.letterCenters()
        fun touch(action: Int, p: Pair<Float, Float>) {
            val e = android.view.MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, p.first, p.second, 0)
            kv.dispatchTouchEvent(e); e.recycle()
        }

        // Prowl: a long swipe through h-e-l-o draws the word.
        val path = "helo".map { c.getValue(it) }
        touch(android.view.MotionEvent.ACTION_DOWN, path[0])
        for (i in 0 until path.size - 1) for (st in 1..8) {
            val t = st / 8f
            touch(android.view.MotionEvent.ACTION_MOVE, path[i].first + (path[i + 1].first - path[i].first) * t to path[i].second + (path[i + 1].second - path[i].second) * t)
        }
        touch(android.view.MotionEvent.ACTION_UP, path.last())
        assertEquals("hello", text.lowercase())

        // Flick: a quick, short swipe right on a letter is a space.
        val g = c.getValue('g')
        touch(android.view.MotionEvent.ACTION_DOWN, g)
        touch(android.view.MotionEvent.ACTION_MOVE, g.first + kv.unitWidth * 0.8f to g.second)
        touch(android.view.MotionEvent.ACTION_UP, g.first + kv.unitWidth * 1.2f to g.second)
        assertEquals("hello ", text.lowercase())
    }
}
