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
}
