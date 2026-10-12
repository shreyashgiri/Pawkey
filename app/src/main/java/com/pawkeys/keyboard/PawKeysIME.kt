package com.pawkeys.keyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.icu.text.BreakIterator
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.pawkeys.keyboard.engine.Dictionary
import com.pawkeys.keyboard.engine.GlideDecoder
import com.pawkeys.keyboard.engine.Offensive
import com.pawkeys.keyboard.engine.Suggestion
import com.pawkeys.keyboard.engine.UserDictionary
import com.pawkeys.keyboard.gifs.Gif
import com.pawkeys.keyboard.gifs.GifPanel
import com.pawkeys.keyboard.gifs.GifSource
import com.pawkeys.keyboard.gifs.GiphySource
import com.pawkeys.keyboard.panels.BreedPanel
import com.pawkeys.keyboard.translate.LanguagePanel
import com.pawkeys.keyboard.translate.Languages
import com.pawkeys.keyboard.translate.MlKitTranslator
import com.pawkeys.keyboard.translate.TextTranslator
import com.pawkeys.keyboard.translate.TranslateBar
import com.pawkeys.keyboard.voice.MicPermissionActivity
import com.pawkeys.keyboard.voice.VoiceBar
import com.pawkeys.keyboard.stickers.Sticker
import com.pawkeys.keyboard.stickers.StickerArt
import com.pawkeys.keyboard.stickers.StickerPanel
import java.io.File
import com.pawkeys.keyboard.panels.ClipboardPanel
import com.pawkeys.keyboard.panels.EmojiData
import com.pawkeys.keyboard.panels.EmojiSearch
import com.pawkeys.keyboard.panels.EmojiSearchBar
import com.pawkeys.keyboard.panels.EmojiPanel
import com.pawkeys.keyboard.panels.OneHandedRail
import com.pawkeys.keyboard.panels.PawMenuPanel
import com.pawkeys.keyboard.panels.PerchContainer
import com.pawkeys.keyboard.panels.SizeBar
import com.pawkeys.keyboard.panels.TextEditPanel
import com.pawkeys.keyboard.panels.ThemePanel
import com.pawkeys.keyboard.settings.SettingsActivity
import com.pawkeys.keyboard.settings.ShortcutsActivity
import kotlin.concurrent.thread
import kotlin.math.abs

class PawKeysIME : InputMethodService(),
    KeyboardView.Listener,
    CandidateStrip.Listener,
    TextEditPanel.Actions,
    TranslateBar.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var feedback: Feedback
    private lateinit var clipboard: ClipboardHistory
    private lateinit var shortcuts: ShortcutStore
    private lateinit var userDict: UserDictionary
    @Volatile private var dictionary: Dictionary? = null
    @Volatile private var glide: GlideDecoder? = null

    // ---- views ----
    private var theme = KeyboardTheme.CALICO
    private var root: View? = null
    private lateinit var strip: CandidateStrip
    private lateinit var stripFrame: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardFrame: FrameLayout
    private enum class Panel { MENU, EMOJI, STICKERS, GIFS, CLIPBOARD, TEXT_EDIT, THEMES, BREEDS, LANGUAGE }
    private var panel: Panel? = null
    private var panelView: View? = null
    private var needsRebuild = false

    // ---- editor ----
    private var baseLayout = LayoutId.QWERTY
    private var layoutId = LayoutId.QWERTY
    private var variant = FieldVariant.TEXT
    private var suggestionsOn = false
    private var autocorrectOn = false
    private var learningOn = false
    private var autoCapOn = false
    private var isPassword = false
    private var enterAction: Int? = null
    private var shift = ShiftState.OFF
    private var manualShift = false
    private var lastShiftTap = 0L

    // ---- typing state ----
    private val composing = StringBuilder()
    /** Composing word came from a suggestion, glide, undo or cursor resume: don't autocorrect it. */
    private var composingExact = false
    private var glideAlternatives: List<String> = emptyList()
    private var currentSuggestions: List<Suggestion> = emptyList()
    private var lastCorrection: Correction? = null
    private var lastCommit: CommitInfo? = null
    /** The space before the cursor was added by us (swipe right / suggestion), so punctuation may replace it. */
    private var lastAutoSpace = false
    private var lastKeyWasSpace = false
    private var punctIndex = -1
    private var selStart = -1
    private var selEnd = -1
    private var deleteAnchor = -1
    private var deleteBefore = ""

    private data class Correction(val original: String, val corrected: String, val separator: String)
    private class CommitInfo(val options: List<String>, var index: Int, val separator: String)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        feedback = Feedback(this, prefs)
        clipboard = ClipboardHistory(this, prefs)
        shortcuts = ShortcutStore(this)
        userDict = UserDictionary(filesDir)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        thread(name = "pawkeys-dictionary") {
            val d = assets.open("words_en.txt").use { Dictionary(it) }
            dictionary = d
            glide = GlideDecoder(d)
        }
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        clipboard.release()
        feedback.release()
        translator.close()
        recognizer?.destroy()
        userDict.save()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    // ---------------------------------------------------------------- views

    override fun onCreateInputView(): View {
        theme = KeyboardTheme.resolve(this, prefs.theme, prefs.breed)
        keyboardView = KeyboardView(this, this)
        strip = CandidateStrip(this, this)
        stripFrame = FrameLayout(this).apply { addView(strip) }
        keyboardFrame = FrameLayout(this).apply { clipChildren = false; addView(keyboardView) }
        panel = null
        panelView = null

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        when (val side = if (prefs.floating) "off" else prefs.oneHanded) {
            "left", "right" -> {
                val rail = OneHandedRail(this, theme, side,
                    onSwitch = { prefs.oneHanded = if (side == "left") "right" else "left" },
                    onExit = { prefs.oneHanded = "off" })
                val kb = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.82f)
                val rl = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.18f)
                if (side == "left") { body.addView(keyboardFrame, kb); body.addView(rail, rl) }
                else { body.addView(rail, rl); body.addView(keyboardFrame, kb) }
            }
            else -> body.addView(keyboardFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setBackgroundColor(theme.background)
            addView(stripFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        if (prefs.floating) {
            // Perch: the keyboard becomes a card that floats over the app.
            view.background = null
            val perch = PerchContainer(this, theme, view, prefs.floatX, prefs.floatY,
                onMoved = { x, y -> prefs.floatX = x; prefs.floatY = y },
                onDock = { prefs.floating = false })
            root = perch
            applyKeyboardSettings()
            return perch
        }
        root = view
        applyKeyboardSettings()
        return view
    }

    private val perchLocation = IntArray(2)
    private val perchRect = Rect()

    /** In Perch mode only the card takes touches, and the app isn't pushed up. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val perch = root as? PerchContainer ?: return
        if (!isInputViewShown || perch.height == 0) return
        perch.getLocationInWindow(perchLocation)
        val bottom = perchLocation[1] + perch.height
        outInsets.contentTopInsets = bottom
        outInsets.visibleTopInsets = bottom
        perch.cardBounds(perchRect)
        perchRect.offset(perchLocation[0], perchLocation[1])
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(perchRect)
    }

    private fun applyKeyboardSettings() {
        if (root == null) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        keyboardView.apply {
            theme = this@PawKeysIME.theme
            breed = prefs.breed
            showHints = prefs.hintChars
            keyBorders = prefs.keyBorders
            heightScale = prefs.heightPercent / 100f * if (landscape) 0.78f else 1f
            gestureMode = prefs.gestureMode
            spaceCursor = prefs.spaceCursor
            deleteSwipe = prefs.deleteSwipe
            keyPreview = prefs.keyPreview
            longPressMs = prefs.longPressMs
            pawTrail = prefs.pawTrail
        }
        strip.theme = theme
        strip.buttons = prefs.toolbarButtons
    }

    private fun rebuildView() {
        if (root == null) return
        if (!isInputViewShown) { needsRebuild = true; return }
        setInputView(onCreateInputView())
        setLayout(layoutId)
        updateShift()
        updateSuggestions()
        styleNavigationBar()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            in Prefs.VISUAL_KEYS -> rebuildView()
            Prefs.GESTURE_MODE, Prefs.SPACE_CURSOR, Prefs.DELETE_SWIPE, Prefs.KEY_PREVIEW,
            Prefs.LONG_PRESS_MS, Prefs.PAW_TRAIL -> applyKeyboardSettings()
            SettingsActivity.KEY_LEARNED_RESET -> userDict.clear()
            Prefs.TOOLBAR -> if (root != null) strip.buttons = prefs.toolbarButtons
        }
    }

    @Suppress("DEPRECATION")
    private fun styleNavigationBar() {
        val w = window?.window ?: return
        w.navigationBarColor = if (prefs.floating) android.graphics.Color.TRANSPARENT else theme.background
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !theme.isDark
    }

    // ---------------------------------------------------------------- editor lifecycle

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (needsRebuild) { setInputView(onCreateInputView()); needsRebuild = false }
        applyKeyboardSettings()
        configureForEditor(info)
        resetTypingState()
        closePanel()
        restoreStrip()
        setLayout(baseLayout)
        updateShift()
        updateSuggestions()
        styleNavigationBar()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (root != null) keyboardView.cancelGesture()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetTypingState()
        userDict.save()
    }

    private fun configureForEditor(info: EditorInfo) {
        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        baseLayout = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> LayoutId.NUMPAD
            InputType.TYPE_CLASS_PHONE -> LayoutId.PHONE
            else -> LayoutId.QWERTY
        }
        val isText = cls == InputType.TYPE_CLASS_TEXT
        isPassword = (isText && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        variant = when {
            !isText -> FieldVariant.TEXT
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldVariant.EMAIL
            variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldVariant.URL
            else -> FieldVariant.TEXT
        }
        val noSuggestions = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        suggestionsOn = prefs.suggestions && isText && !isPassword && !noSuggestions
        autocorrectOn = suggestionsOn && prefs.autocorrect && variant == FieldVariant.TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_PERSON_NAME &&
            variation != InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
        learningOn = prefs.learnWords && !isPassword &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        autoCapOn = prefs.autoCap && isText && !isPassword && variant == FieldVariant.TEXT

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        enterAction = if (noEnterAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) null else action
        if (root != null) {
            keyboardView.enterIsAction = enterAction != null
            keyboardView.enterLabel = when (enterAction) {
                EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_NEXT -> "next"
                EditorInfo.IME_ACTION_SEARCH -> "search"
                EditorInfo.IME_ACTION_SEND -> "send"
                EditorInfo.IME_ACTION_DONE -> "done"
                EditorInfo.IME_ACTION_PREVIOUS -> "previous"
                else -> "return"
            }
            keyboardView.enterIcon = when (enterAction) {
                EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_arrow_forward
                EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
                EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
                EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check
                EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.ic_keyboard_arrow_left
                else -> R.drawable.ic_keyboard_return
            }
        }
    }

    private fun resetTypingState() {
        composing.clear()
        composingExact = false
        glideAlternatives = emptyList()
        currentSuggestions = emptyList()
        lastCorrection = null
        lastCommit = null
        lastAutoSpace = false
        lastKeyWasSpace = false
        punctIndex = -1
        deleteAnchor = -1
        shift = ShiftState.OFF
        manualShift = false
    }

    private fun setLayout(id: LayoutId) {
        layoutId = id
        if (root == null) return
        keyboardView.layout = Layouts.build(id, prefs.numberRow || isPassword, variant, prefs.breed)
        keyboardView.shiftState = if (id == LayoutId.QWERTY) shift else ShiftState.OFF
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // The cursor moved away from the word being typed (user tapped elsewhere).
            composing.clear()
            composingExact = false
            glideAlternatives = emptyList()
            currentInputConnection?.finishComposingText()
            updateSuggestions()
        }
        if (composing.isEmpty() && newSelStart != oldSelStart && !manualShift) updateShift()
    }

    // ---------------------------------------------------------------- key handling

    override fun onKeyDown(key: Key) = feedback.onKey(key.code)

    override fun onKey(key: Key) {
        if (listening) stopVoice()
        val wasSpace = lastKeyWasSpace
        if (key.code != Codes.SPACE && key.code != Codes.SHIFT) lastKeyWasSpace = false
        when (key.code) {
            Codes.SHIFT -> handleShift()
            Codes.DELETE -> handleDelete()
            Codes.ENTER -> handleEnter()
            Codes.SPACE -> handleSpace(wasSpace)
            Codes.SYMBOLS -> setLayout(LayoutId.SYMBOLS)
            Codes.SYMBOLS_MORE -> setLayout(LayoutId.SYMBOLS_MORE)
            Codes.ALPHA -> { setLayout(LayoutId.QWERTY); updateShift() }
            Codes.EMOJI -> openPanel(Panel.EMOJI)
            else -> handleText(key.text)
        }
    }

    override fun onPopupText(key: Key, text: String) {
        if (key.code == ','.code && text == "☺") openPanel(Panel.EMOJI) else handleText(text)
    }

    private fun handleText(raw: String) {
        if (translating) { translateType(raw); return }
        if (searchingEmoji) { emojiQuery.append(raw); refreshEmojiSearch(); return }
        val ic = currentInputConnection ?: return
        punctIndex = -1
        var text = raw
        if (layoutId == LayoutId.QWERTY && shift != ShiftState.OFF && text.length == 1 && text[0].isLetter()) text = text.uppercase()
        val c = text.codePointAt(0)
        val isWordChar = text.length == Character.charCount(c) &&
            (Character.isLetterOrDigit(c) || ((c == '\''.code || c == '’'.code) && composing.isNotEmpty()))
        val isPunct = text.length == 1 && text[0] in ".,!?;:"

        when {
            isWordChar && suggestionsOn -> {
                if (composing.isEmpty()) lastCommit = null
                lastCorrection = null
                composingExact = false
                glideAlternatives = emptyList()
                composing.append(text)
                ic.setComposingText(composing, 1)
                lastAutoSpace = false
            }
            composing.isNotEmpty() -> {
                // Punctuation ends the word, and can trigger autocorrect.
                commitComposing(text, allowCorrect = text.length == 1 && text[0] in ".,!?;:)\"")
                lastAutoSpace = false
            }
            isPunct && prefs.smartPunctuation && lastAutoSpace && textBefore(1) == " " -> {
                // "word |" + "." -> "word. |" (Fleksy-style smart punctuation)
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$text ", 1)
                ic.endBatchEdit()
                lastCorrection = null
                lastCommit = null
                lastAutoSpace = true
            }
            else -> {
                ic.commitText(text, 1)
                lastAutoSpace = false
                lastCorrection = null
                lastCommit = null
            }
        }
        manualShift = false
        if (shift == ShiftState.ON) shift = ShiftState.OFF
        updateShift()
        updateSuggestions()
    }

    /** Commits the word being typed (autocorrecting it if allowed) followed by [separator]. */
    private fun commitComposing(separator: String, allowCorrect: Boolean) {
        val ic = currentInputConnection ?: return
        val typed = composing.toString()
        var final = typed
        if (allowCorrect && autocorrectOn && !composingExact) {
            dictionary?.autocorrect(typed, userDict.learned, ::isBlocked)?.let { final = it }
        }
        val prev = previousWord()
        ic.commitText(final + separator, 1)
        lastCorrection = if (final != typed) Correction(typed, final, separator) else null
        val options = (listOf(final) + currentSuggestions.filter { !it.isShortcut }.map { it.word } + glideAlternatives + typed).distinct()
        lastCommit = if (options.size > 1) CommitInfo(options, 0, separator) else null
        learn(prev, final)
        composing.clear()
        composingExact = false
        glideAlternatives = emptyList()
    }

    private fun learn(previous: String?, word: String, boost: Int = 1) {
        if (!learningOn || word.isBlank()) return
        if (dictionary?.contains(word) != true && word.any { it.isLetter() }) userDict.learnWord(word, boost)
        if (previous != null) userDict.learnBigram(previous, word)
    }

    private fun handleSpace(wasSpace: Boolean) {
        if (translating) { translateType(" "); return }
        if (searchingEmoji) { emojiQuery.append(' '); refreshEmojiSearch(); return }
        val ic = currentInputConnection ?: return
        punctIndex = -1
        if (composing.isNotEmpty()) {
            commitComposing(" ", allowCorrect = true)
        } else {
            val before = textBefore(2)
            if (prefs.doubleSpacePeriod && wasSpace && variant == FieldVariant.TEXT && !isPassword &&
                before.length == 2 && before[1] == ' ' && (before[0].isLetterOrDigit() || before[0] in ")\"'")
            ) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastCorrection = null
                lastCommit = null
            } else {
                ic.commitText(" ", 1)
            }
        }
        lastAutoSpace = false
        lastKeyWasSpace = true
        manualShift = false
        updateShift()
        updateSuggestions()
    }

    private fun handleDelete() {
        if (translating) { translateDelete(word = false); return }
        if (searchingEmoji) {
            if (emojiQuery.isNotEmpty()) emojiQuery.setLength(emojiQuery.length - 1)
            refreshEmojiSearch()
            return
        }
        val ic = currentInputConnection ?: return
        punctIndex = -1
        lastAutoSpace = false
        if (composing.isNotEmpty()) {
            if (glideAlternatives.isNotEmpty()) {
                // Like Samsung: backspace right after swiping a word removes the whole word.
                composing.clear()
            } else {
                val len = composing.length
                val cut = if (len >= 2 && Character.isSurrogatePair(composing[len - 2], composing[len - 1])) 2 else 1
                composing.setLength(len - cut)
            }
            composingExact = false
            glideAlternatives = emptyList()
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            updateShift()
            updateSuggestions()
            return
        }
        lastCorrection?.let { corr ->
            // Backspace right after an autocorrection restores what you actually typed.
            lastCorrection = null
            val expect = corr.corrected + corr.separator
            if (textBefore(expect.length) == expect) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expect.length, 0)
                composing.append(corr.original)
                composingExact = true
                ic.setComposingText(composing, 1)
                ic.endBatchEdit()
                if (learningOn) userDict.learnWord(corr.original, 2)
                lastCommit = null
                updateSuggestions()
                return
            }
        }
        lastCommit = null
        val selected = ic.getSelectedText(0)
        val before = textBefore(32)
        when {
            !selected.isNullOrEmpty() -> ic.commitText("", 1)
            // Delete one whole character (a multi-part emoji counts as one).
            before.isNotEmpty() -> ic.deleteSurroundingText(lastGraphemeLength(before), 0)
            // Empty field: send a real key so apps can react (e.g. remove a chip).
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        restartComposing()
        updateShift()
        updateSuggestions()
    }

    /** After deleting back into a word, pick it up again so suggestions show for it. */
    private fun restartComposing() {
        if (!suggestionsOn || composing.isNotEmpty()) return
        val ic = currentInputConnection ?: return
        val after = ic.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && Character.isLetterOrDigit(after[0])) return
        val before = ic.getTextBeforeCursor(48, 0)?.toString() ?: return
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        if (i == 0 && before.length >= 48) return
        val word = before.substring(i)
        if (word.isEmpty() || !word[0].isLetter()) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.setComposingText(word, 1)
        ic.endBatchEdit()
        composing.append(word)
        composingExact = true
    }

    private fun handleEnter() {
        if (translating) { onInsertTranslation(); return }
        if (searchingEmoji) { closeEmojiSearch(); return }  // GIFs: shows results
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        lastAutoSpace = false
        lastCorrection = null
        lastCommit = null
        punctIndex = -1
        val action = enterAction
        if (action != null) ic.performEditorAction(action) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updateShift()
        updateSuggestions()
    }

    private fun handleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> if (now - lastShiftTap < 350) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftTap = now
        manualShift = true
        keyboardView.shiftState = shift
    }

    private fun updateShift() {
        if (root == null || layoutId != LayoutId.QWERTY) return
        if (shift == ShiftState.LOCKED) { keyboardView.shiftState = shift; return }
        if (manualShift && shift == ShiftState.ON) return
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        val caps = autoCapOn && composing.isEmpty() && ic != null && info != null &&
            ic.getCursorCapsMode(info.inputType) != 0
        shift = if (caps) ShiftState.ON else ShiftState.OFF
        keyboardView.shiftState = shift
    }

    private fun applyShift(word: String) = when (shift) {
        ShiftState.LOCKED -> word.uppercase()
        ShiftState.ON -> word.replaceFirstChar { it.uppercaseChar() }
        ShiftState.OFF -> word
    }

    // ---------------------------------------------------------------- gestures

    override fun onFlick(flick: Flick) {
        if (searchingEmoji) {
            if (flick == Flick.RIGHT) { emojiQuery.append(' '); refreshEmojiSearch() }
            if (flick == Flick.LEFT) { emojiQuery.clear(); refreshEmojiSearch() }
            return
        }
        if (translating) {
            when (flick) {
                Flick.RIGHT -> translateType(" ")
                Flick.LEFT -> translateDelete(word = true)
                else -> Unit
            }
            return
        }
        val ic = currentInputConnection ?: return
        feedback.tick()
        manualShift = false
        when (flick) {
            Flick.RIGHT -> {
                // Fleksy: swipe right = space; swipe right again = full stop.
                val before = textBefore(2)
                when {
                    composing.isNotEmpty() -> {
                        commitComposing(" ", allowCorrect = true)
                        punctIndex = -1
                    }
                    lastAutoSpace && punctIndex < 0 && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() -> {
                        ic.beginBatchEdit()
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(PUNCTUATION[0], 1)
                        ic.endBatchEdit()
                        punctIndex = 0
                        lastCommit = null
                        lastCorrection = null
                    }
                    else -> {
                        ic.commitText(" ", 1)
                        punctIndex = -1
                    }
                }
                lastAutoSpace = true
            }
            Flick.LEFT -> deleteWordBackward()
            Flick.DOWN -> cycle(1)
            Flick.UP -> cycle(-1)
        }
        updateShift()
        updateSuggestions()
    }

    /** Swipe up/down: cycle punctuation after a full stop, or the alternatives for the last word. */
    private fun cycle(direction: Int) {
        val ic = currentInputConnection ?: return
        if (punctIndex >= 0) {
            val cur = PUNCTUATION[punctIndex]
            if (textBefore(cur.length) == cur) {
                val next = (punctIndex + direction + PUNCTUATION.size) % PUNCTUATION.size
                ic.beginBatchEdit()
                ic.deleteSurroundingText(cur.length, 0)
                ic.commitText(PUNCTUATION[next], 1)
                ic.endBatchEdit()
                punctIndex = next
            }
            return
        }
        if (composing.isNotEmpty()) {
            commitComposing(" ", allowCorrect = true)
            lastAutoSpace = true
        }
        val lc = lastCommit
        if (lc != null) {
            val cur = lc.options[lc.index] + lc.separator
            if (textBefore(cur.length) == cur) {
                val next = (lc.index + direction + lc.options.size) % lc.options.size
                ic.beginBatchEdit()
                ic.deleteSurroundingText(cur.length, 0)
                ic.commitText(lc.options[next] + lc.separator, 1)
                ic.endBatchEdit()
                lc.index = next
                lastCorrection = null
                return
            }
        }
        if (direction < 0) handleShift()
    }

    private fun deleteWordBackward() {
        val ic = currentInputConnection ?: return
        lastCorrection = null
        lastCommit = null
        punctIndex = -1
        lastAutoSpace = false
        if (composing.isNotEmpty()) {
            composing.clear()
            glideAlternatives = emptyList()
            ic.commitText("", 1)
            return
        }
        val before = textBefore(64)
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0 && isWordChar(before[i - 1])) {
            while (i > 0 && isWordChar(before[i - 1])) i--
        } else if (i > 0) {
            i -= if (i >= 2 && Character.isSurrogatePair(before[i - 2], before[i - 1])) 2 else 1
        }
        ic.deleteSurroundingText(before.length - i, 0)
    }

    override fun onSpaceCursor(steps: Int) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.finishComposingText()
            composing.clear()
            glideAlternatives = emptyList()
        }
        lastCorrection = null
        lastCommit = null
        lastAutoSpace = false
        repeat(abs(steps)) { moveCursor(if (steps > 0) 1 else -1, 0, select = false) }
    }

    override fun onSpaceLongPress() {
        feedback.tick()
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun onDeleteSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        if (deleteAnchor < 0) {
            if (composing.isNotEmpty()) {
                ic.finishComposingText()
                composing.clear()
                glideAlternatives = emptyList()
            }
            val et = ic.getExtractedText(ExtractedTextRequest(), 0)
            deleteAnchor = if (et != null) et.startOffset + et.selectionEnd else selEnd
            deleteBefore = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        }
        if (words == 0) {
            ic.setSelection(deleteAnchor, deleteAnchor)
            return
        }
        var i = deleteBefore.length
        repeat(words) {
            while (i > 0 && deleteBefore[i - 1].isWhitespace()) i--
            while (i > 0 && !deleteBefore[i - 1].isWhitespace()) i--
        }
        ic.setSelection(deleteAnchor - (deleteBefore.length - i), deleteAnchor)
        feedback.tick()
    }

    override fun onDeleteSwipeEnd(words: Int) {
        val ic = currentInputConnection
        if (deleteAnchor >= 0 && ic != null) {
            if (words > 0) ic.commitText("", 1) else ic.setSelection(deleteAnchor, deleteAnchor)
        }
        deleteAnchor = -1
        lastCorrection = null
        lastCommit = null
        updateShift()
        updateSuggestions()
    }

    override fun onGlide(xs: FloatArray, ys: FloatArray) {
        val decoder = glide ?: return
        val ic = currentInputConnection ?: return
        val words = decoder.decode(xs, ys, keyboardView.letterCenters(), keyboardView.unitWidth, userDict.learned)
            .filterNot { isBlocked(it) }
        if (words.isEmpty()) return
        if (searchingEmoji) {
            emojiQuery.setLength(0)
            emojiQuery.append(words[0])
            refreshEmojiSearch()
            return
        }
        if (translating) {
            if (translateBuffer.isNotEmpty() && !translateBuffer.endsWith(" ")) translateBuffer.append(' ')
            translateType(applyShift(words[0]))
            return
        }
        if (composing.isNotEmpty()) {
            commitComposing(" ", allowCorrect = true)
        } else {
            val b = textBefore(1)
            if (b.isNotEmpty() && !b[0].isWhitespace() && b[0] !in "([{\"'¿¡/@#") ic.commitText(" ", 1)
        }
        val cased = words.map { applyShift(it) }
        composing.append(cased[0])
        composingExact = true
        glideAlternatives = cased
        ic.setComposingText(composing, 1)
        lastAutoSpace = false
        lastCorrection = null
        lastCommit = null
        punctIndex = -1
        manualShift = false
        if (shift == ShiftState.ON) shift = ShiftState.OFF
        updateShift()
        updateSuggestions()
    }

    // ---------------------------------------------------------------- suggestions

    private fun isBlocked(word: String) = userDict.isBlocked(word) || (prefs.blockOffensive && Offensive.contains(word))

    private fun updateSuggestions() {
        if (root == null) return
        if (panel != null || !suggestionsOn) {
            currentSuggestions = emptyList()
            strip.showToolbar()
            return
        }
        val dict = dictionary
        if (composing.isNotEmpty()) {
            val typed = composing.toString()
            if (glideAlternatives.isNotEmpty()) {
                currentSuggestions = glideAlternatives.take(3).map { Suggestion(it, 0.0) }
                strip.setSuggestions(currentSuggestions, -1)
                return
            }
            val list = ArrayList<Suggestion>()
            shortcuts.expansionFor(typed)?.let { list += Suggestion(it, 0.0, isShortcut = true) }
            var correction: String? = null
            if (dict != null) {
                val found = dict.suggest(typed, 3, userDict.learned, ::isBlocked)
                correction = if (autocorrectOn && !composingExact) dict.autocorrect(typed, userDict.learned, ::isBlocked) else null
                if (correction != null) {
                    list += Suggestion(correction, 0.0, isCorrection = true)
                    list += Suggestion(typed, 0.0)
                    list += found.filter { !it.word.equals(correction, true) && !it.word.equals(typed, true) }
                } else {
                    list += found
                    if (list.none { it.word.equals(typed, true) }) list.add(minOf(1, list.size), Suggestion(typed, 0.0))
                }
            } else {
                list += Suggestion(typed, 0.0)
            }
            currentSuggestions = list.distinctBy { it.word }.take(3)
            val auto = if (correction == null) -1 else currentSuggestions.indexOfFirst { it.word == correction }
            strip.setSuggestions(currentSuggestions, auto)
            return
        }
        val prev = if (prefs.nextWordPrediction && learningOn) previousWord() else null
        val predictions = prev?.let { userDict.predictNext(it) }.orEmpty()
        if (predictions.isNotEmpty()) {
            currentSuggestions = predictions.map { Suggestion(applyShift(it), 0.0) }
            strip.setSuggestions(currentSuggestions, -1)
        } else {
            currentSuggestions = emptyList()
            strip.showToolbar()
        }
    }

    /** The word before the cursor (ignoring the word being typed), for learning and predictions. */
    private fun previousWord(): String? {
        var before = textBefore(64)
        if (composing.isNotEmpty() && before.endsWith(composing)) before = before.dropLast(composing.length)
        before = before.trimEnd(' ')
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        val word = before.substring(i)
        return word.takeIf { it.isNotEmpty() && it.any { c -> c.isLetter() } }
    }

    override fun onSuggestionPicked(index: Int, suggestion: Suggestion) {
        val ic = currentInputConnection ?: return
        feedback.tick()
        val prev = previousWord()
        if (composing.isEmpty()) {
            // Next-word prediction.
            val b = textBefore(1)
            if (b.isNotEmpty() && !b[0].isWhitespace()) ic.commitText(" ", 1)
            ic.commitText(suggestion.word + " ", 1)
            lastCommit = null
            learn(prev, suggestion.word)
        } else {
            ic.commitText(suggestion.word + " ", 1)
            if (suggestion.isShortcut) {
                lastCommit = null
            } else {
                val options = (listOf(suggestion.word) + currentSuggestions.filter { !it.isShortcut }.map { it.word }).distinct()
                lastCommit = CommitInfo(options, 0, " ")
                learn(prev, suggestion.word, boost = 2)
            }
            composing.clear()
            composingExact = false
            glideAlternatives = emptyList()
        }
        lastCorrection = null
        lastAutoSpace = true
        punctIndex = -1
        manualShift = false
        updateShift()
        updateSuggestions()
    }

    override fun onSuggestionLongPressed(suggestion: Suggestion) {
        if (suggestion.isShortcut) return
        userDict.block(suggestion.word)
        Toast.makeText(this, "“${suggestion.word}” removed from Hunches", Toast.LENGTH_SHORT).show()
        updateSuggestions()
    }

    // ---------------------------------------------------------------- toolbar & panels

    override fun onToolbarAction(action: CandidateStrip.ToolbarAction) {
        feedback.tick()
        val fromDen = panel == Panel.MENU
        when (action) {
            CandidateStrip.ToolbarAction.MENU -> togglePanel(Panel.MENU)
            CandidateStrip.ToolbarAction.EMOJI -> togglePanel(Panel.EMOJI)
            CandidateStrip.ToolbarAction.STICKERS -> togglePanel(Panel.STICKERS)
            CandidateStrip.ToolbarAction.GIFS -> { gifQuery = null; togglePanel(Panel.GIFS) }
            CandidateStrip.ToolbarAction.TRANSLATE -> if (translating) onCloseTranslate() else startTranslate()
            CandidateStrip.ToolbarAction.VOICE -> if (listening) stopVoice() else startVoice()
            CandidateStrip.ToolbarAction.CLIPBOARD -> togglePanel(Panel.CLIPBOARD)
            CandidateStrip.ToolbarAction.TEXT_EDIT -> togglePanel(Panel.TEXT_EDIT)
            CandidateStrip.ToolbarAction.THEMES -> togglePanel(Panel.THEMES)
            CandidateStrip.ToolbarAction.BREEDS -> togglePanel(Panel.BREEDS)
            CandidateStrip.ToolbarAction.ONE_HANDED -> toggleOneHanded()
            CandidateStrip.ToolbarAction.FLOAT -> prefs.floating = !prefs.floating
            CandidateStrip.ToolbarAction.SIZE -> showSizeBar()
            CandidateStrip.ToolbarAction.NUMBER_ROW -> prefs.numberRow = !prefs.numberRow
            CandidateStrip.ToolbarAction.GESTURES -> {
                prefs.gestureMode = when (prefs.gestureMode) { "fleksy" -> "glide"; "glide" -> "off"; else -> "fleksy" }
                val name = when (prefs.gestureMode) { "glide" -> "Prowl on: swipe to type"; "off" -> "Gestures off"; else -> "Flicks on" }
                Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
                if (fromDen) openPanel(Panel.MENU)
            }
            CandidateStrip.ToolbarAction.SOUND -> {
                // Cycle Silent -> Click -> Kitten -> Cat -> Duck, playing each as a preview.
                val current = if (prefs.sound) prefs.soundStyle else "off"
                val next = Feedback.STYLES[(Feedback.STYLES.indexOf(current).coerceAtLeast(0) + 1) % Feedback.STYLES.size]
                prefs.sound = next != "off"
                if (next != "off") { prefs.soundStyle = next; feedback.preview(next) }
                if (fromDen) openPanel(Panel.MENU) else Toast.makeText(this, "Key sound: ${Feedback.label(next)}", Toast.LENGTH_SHORT).show()
            }
            CandidateStrip.ToolbarAction.VIBRATION -> {
                prefs.vibrate = !prefs.vibrate
                if (fromDen) openPanel(Panel.MENU) else Toast.makeText(this, if (prefs.vibrate) "Haptics on" else "Haptics off", Toast.LENGTH_SHORT).show()
            }
            CandidateStrip.ToolbarAction.SHORTCUTS -> openActivity(ShortcutsActivity::class.java)
            CandidateStrip.ToolbarAction.SETTINGS -> openActivity(SettingsActivity::class.java)
            CandidateStrip.ToolbarAction.HIDE -> requestHideSelf(0)
        }
    }

    private fun togglePanel(p: Panel) = if (panel == p) closePanel() else openPanel(p)

    private fun openPanel(p: Panel) {
        if (root == null) return
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        closePanel()
        val height = keyboardView.height.takeIf { it > 0 } ?: keyboardView.desiredHeight()
        val view: View = when (p) {
            Panel.MENU -> PawMenuPanel(this, theme, prefs, ::onToolbarAction)
            Panel.EMOJI -> EmojiPanel(this, theme, prefs.sp, ::insertEmoji, ::handleDelete, ::closePanel, ::startEmojiSearch)
            Panel.CLIPBOARD -> ClipboardPanel(this, theme, clipboard, ::pasteClip, ::closePanel)
            Panel.TEXT_EDIT -> TextEditPanel(this, theme, this)
            Panel.THEMES -> ThemePanel(this, theme, prefs.theme, prefs.breed) { prefs.theme = it }
            Panel.BREEDS -> BreedPanel(this, theme, prefs.breed) { prefs.breed = it }
            Panel.STICKERS -> StickerPanel(this, theme, ::sendSticker, ::closePanel)
            Panel.GIFS -> GifPanel(this, theme, gifSource(), gifQuery, ::sendGif, ::startGifSearch,
                { openActivity(SettingsActivity::class.java) }, ::closePanel)
            Panel.LANGUAGE -> LanguagePanel(this, theme,
                if (pickingSource) "Translate from" else "Translate into",
                if (pickingSource) prefs.translateFrom else prefs.translateTo) { code -> pickLanguage(code) }
        }
        keyboardView.cancelGesture()
        keyboardView.visibility = View.GONE
        keyboardFrame.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height))
        panel = p
        panelView = view
        strip.activeAction = when (p) {
            Panel.MENU -> CandidateStrip.ToolbarAction.MENU
            Panel.THEMES -> CandidateStrip.ToolbarAction.THEMES
            Panel.BREEDS -> CandidateStrip.ToolbarAction.BREEDS
            Panel.EMOJI -> CandidateStrip.ToolbarAction.EMOJI
            Panel.STICKERS -> CandidateStrip.ToolbarAction.STICKERS
            Panel.GIFS -> CandidateStrip.ToolbarAction.GIFS
            Panel.LANGUAGE -> CandidateStrip.ToolbarAction.TRANSLATE
            Panel.CLIPBOARD -> CandidateStrip.ToolbarAction.CLIPBOARD
            Panel.TEXT_EDIT -> CandidateStrip.ToolbarAction.TEXT_EDIT
        }
        updateSuggestions()
    }

    private fun closePanel() {
        if (root == null) return
        panelView?.let { keyboardFrame.removeView(it) }
        panelView = null
        panel = null
        keyboardView.visibility = View.VISIBLE
        strip.activeAction = null
        updateShift()
        updateSuggestions()
    }

    private fun toggleOneHanded() {
        prefs.oneHanded = if (prefs.oneHanded == "off") "right" else "off"
    }

    private fun showSizeBar() {
        restoreStrip()
        closePanel()
        var value = prefs.heightPercent
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val bar = SizeBar(this, theme, value,
            onChange = {
                value = it
                keyboardView.heightScale = it / 100f * if (landscape) 0.78f else 1f
            },
            onDone = {
                restoreStrip()
                prefs.heightPercent = value
            })
        stripFrame.removeAllViews()
        stripFrame.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, UiDims.stripHeight(this)))
    }

    private fun restoreStrip() {
        emojiSearchBar = null
        emojiQuery.clear()
        voiceBar = null
        recognizer?.destroy()
        recognizer = null
        translateBar = null
        translateBuffer.clear()
        translated = ""
        if (root == null || strip.parent === stripFrame) return
        stripFrame.removeAllViews()
        stripFrame.addView(strip)
    }

    private fun openActivity(cls: Class<*>) {
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Renders the sticker to a PNG and sends it (or copies it if the app can't take images). */
    internal fun sendSticker(sticker: Sticker) {
        feedback.tick()
        val file = File(RichContent.shareDir(this), "pawkeys_${sticker.id}.png")
        runCatching {
            val bmp = StickerArt.render(sticker, 512)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }.onFailure { return }
        val result = RichContent.send(this, currentInputConnection, currentInputEditorInfo, file, "image/png", sticker.caption)
        when (result) {
            RichContent.Result.SENT -> Unit
            RichContent.Result.COPIED -> Toast.makeText(this, "This app doesn't take stickers directly. Copied, so you can paste it.", Toast.LENGTH_SHORT).show()
            RichContent.Result.FAILED -> Toast.makeText(this, "Couldn't send the sticker here", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun insertEmoji(emoji: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        ic.commitText(emoji, 1)
        lastAutoSpace = false
        lastCorrection = null
        lastCommit = null
        feedback.onKey(0)
    }

    private fun pasteClip(text: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        ic.commitText(text, 1)
        closePanel()
    }

    // ---------------------------------------------------------------- emoji search

    private var emojiSearchBar: EmojiSearchBar? = null
    private val emojiQuery = StringBuilder()
    /** The search bar is shared: emoji (live results) or GIFs (Enter shows results in the panel). */
    private var searchingGifs = false
    internal val searchingEmoji get() = emojiSearchBar != null
    private val allEmoji by lazy { EmojiData.categories.flatMap { it.emojis }.distinct() }

    /** Emoji panel's search: the keys come back and the bar shows query + results. */
    internal fun startEmojiSearch() {
        if (root == null) return
        closePanel()
        restoreStrip()
        val bar = EmojiSearchBar(this, theme, { e -> insertEmoji(e) }, ::closeEmojiSearch)
        emojiSearchBar = bar
        searchingGifs = false
        stripFrame.removeAllViews()
        stripFrame.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        shift = ShiftState.OFF
        keyboardView.shiftState = shift
    }

    private fun refreshEmojiSearch() {
        val q = emojiQuery.toString()
        if (searchingGifs) { emojiSearchBar?.show(q, emptyList()); return }
        emojiSearchBar?.show(q, if (q.isBlank()) emptyList() else EmojiSearch.search(q, allEmoji))
    }

    /** Back to the emoji panel, or for GIFs: show the results for what was typed. */
    internal fun closeEmojiSearch() {
        val gifs = searchingGifs
        val q = emojiQuery.toString().trim()
        restoreStrip()
        if (gifs) {
            gifQuery = q.ifEmpty { null }
            openPanel(Panel.GIFS)
        } else {
            openPanel(Panel.EMOJI)
        }
    }

    // ---------------------------------------------------------------- GIFs

    private var gifQuery: String? = null
    private var gifSourceKey = ""
    private var cachedGifSource: GifSource? = null
    /** Replaceable in tests. */
    internal var gifSourceFactory: (String) -> GifSource = { GiphySource(it) }

    private fun gifSource(): GifSource? {
        val key = prefs.giphyKey
        if (key.isEmpty()) return null
        if (key != gifSourceKey || cachedGifSource == null) {
            cachedGifSource = gifSourceFactory(key)
            gifSourceKey = key
        }
        return cachedGifSource
    }

    internal fun startGifSearch() {
        if (root == null) return
        closePanel()
        restoreStrip()
        val bar = EmojiSearchBar(this, theme, { }, ::closeEmojiSearch, hint = "Search GIPHY, then press enter", showResults = false)
        emojiSearchBar = bar
        searchingGifs = true
        stripFrame.removeAllViews()
        stripFrame.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        shift = ShiftState.OFF
        keyboardView.shiftState = shift
    }

    internal fun sendGif(gif: Gif) {
        val source = gifSource() ?: return
        feedback.tick()
        source.fetch(gif.full) { bytes ->
            if (bytes == null) {
                Toast.makeText(this, "Couldn't download that GIF", Toast.LENGTH_SHORT).show()
                return@fetch
            }
            val file = File(RichContent.shareDir(this), "gif_${gif.id.ifEmpty { "pawkeys" }}.gif")
            runCatching { file.writeBytes(bytes) }.onFailure { return@fetch }
            val result = RichContent.send(this, currentInputConnection, currentInputEditorInfo, file, "image/gif", gif.title.ifEmpty { "GIF" })
            if (result == RichContent.Result.COPIED) {
                Toast.makeText(this, "This app doesn't take GIFs directly. Copied, so you can paste it.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- voice

    private var recognizer: SpeechRecognizer? = null
    private var voiceBar: VoiceBar? = null
    private var voiceNeedsSpace = false
    private var voiceCaps = false
    internal val listening get() = voiceBar != null
    /** Overridable in tests (Robolectric has no speech service). */
    internal var recognitionAvailable: () -> Boolean = { SpeechRecognizer.isRecognitionAvailable(this) }

    internal fun startVoice() {
        if (root == null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            openActivity(MicPermissionActivity::class.java)
            return
        }
        if (!recognitionAvailable()) {
            Toast.makeText(this, "This phone has no speech recognition service", Toast.LENGTH_LONG).show()
            return
        }
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        closePanel()
        restoreStrip()
        val before = textBefore(1)
        voiceNeedsSpace = before.isNotEmpty() && !before[0].isWhitespace()
        val info = currentInputEditorInfo
        // Voice adds its own space, so "Hi.|" counts as a sentence start even before the space.
        val lastChar = textBefore(8).trimEnd().lastOrNull()
        voiceCaps = autoCapOn && (lastChar == null || lastChar in ".!?\n" ||
            (info != null && (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0))
        val bar = VoiceBar(this, theme) { stopVoice() }
        voiceBar = bar
        stripFrame.removeAllViews()
        stripFrame.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, UiDims.stripHeight(this)))

        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { voiceBar?.level(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { voiceBar?.show("Working…", hint = true) }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partial: Bundle?) {
                val words = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (words.isBlank()) return
                voiceBar?.show(words)
                // Show the words in the app as you speak.
                currentInputConnection?.setComposingText(formatVoice(words), 1)
            }

            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val ic = currentInputConnection
                if (words.isNotBlank()) ic?.commitText(formatVoice(words), 1) else ic?.finishComposingText()
                stopVoice()
            }

            override fun onError(error: Int) {
                currentInputConnection?.finishComposingText()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice typing needs a connection here"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off"
                    else -> "Voice typing stopped"
                }
                Toast.makeText(this@PawKeysIME, msg, Toast.LENGTH_SHORT).show()
                stopVoice()
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        })
    }

    private fun formatVoice(words: String): String {
        var t = words.trim()
        if (voiceCaps) t = t.replaceFirstChar { it.uppercaseChar() }
        return if (voiceNeedsSpace) " $t" else t
    }

    internal fun stopVoice() {
        recognizer?.stopListening()
        currentInputConnection?.finishComposingText()
        restoreStrip()
        updateShift()
        updateSuggestions()
    }

    // ---------------------------------------------------------------- translate

    /** Replaceable so tests can use a fake; ML Kit on device. */
    internal var translator: TextTranslator = MlKitTranslator()
    private var translateBar: TranslateBar? = null
    private val translateBuffer = StringBuilder()
    private var translated = ""
    private var translateStatus: String? = null
    private var pickingSource = true
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val translateLater = Runnable { runTranslation() }
    internal val translating get() = translateBar != null

    /** Swap the Whisker bar for the Translate bar; typing now goes into it. */
    internal fun startTranslate() {
        if (root == null) return
        if (composing.isNotEmpty()) commitComposing("", allowCorrect = false)
        closePanel()
        restoreStrip()
        val bar = TranslateBar(this, theme, this)
        translateBar = bar
        stripFrame.removeAllViews()
        stripFrame.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bar.setLanguages(prefs.translateFrom, prefs.translateTo)
        prepareTranslator()
        shift = if (autoCapOn) ShiftState.ON else ShiftState.OFF
        keyboardView.shiftState = shift
    }

    private fun prepareTranslator() {
        val to = Languages.name(prefs.translateTo)
        translateStatus = "Getting $to ready (one-time download)…"
        refreshTranslateBar()
        val from = prefs.translateFrom
        val target = prefs.translateTo
        translator.prepare(from, target) { error ->
            if (!translating || from != prefs.translateFrom || target != prefs.translateTo) return@prepare
            translateStatus = error?.let { "Couldn't get $to: $it" }
            refreshTranslateBar()
            if (error == null) runTranslation()
        }
    }

    private fun refreshTranslateBar() {
        translateBar?.show(translateBuffer.toString(), translated, translateStatus)
    }

    private fun translateType(text: String) {
        var t = text
        if (shift != ShiftState.OFF && t.length == 1 && t[0].isLetter()) t = t.uppercase()
        translateBuffer.append(t)
        if (shift == ShiftState.ON) shift = ShiftState.OFF
        if (t.endsWith(". ") || (t == " " && translateBuffer.endsWith(". "))) shift = if (autoCapOn) ShiftState.ON else shift
        keyboardView.shiftState = shift
        afterTranslateEdit()
    }

    private fun translateDelete(word: Boolean) {
        if (translateBuffer.isEmpty()) return
        if (word) {
            var i = translateBuffer.length
            while (i > 0 && translateBuffer[i - 1].isWhitespace()) i--
            while (i > 0 && !translateBuffer[i - 1].isWhitespace()) i--
            translateBuffer.setLength(i)
        } else {
            translateBuffer.setLength(translateBuffer.length - lastGraphemeLength(translateBuffer.toString()))
        }
        afterTranslateEdit()
    }

    private fun afterTranslateEdit() {
        refreshTranslateBar()
        uiHandler.removeCallbacks(translateLater)
        uiHandler.postDelayed(translateLater, 250)
    }

    private fun runTranslation() {
        val text = translateBuffer.toString().trim()
        if (text.isEmpty()) { translated = ""; refreshTranslateBar(); return }
        if (translateStatus != null && translateStatus!!.startsWith("Getting")) return
        translator.translate(text, prefs.translateFrom, prefs.translateTo) { result, error ->
            if (!translating || translateBuffer.toString().trim() != text) return@translate
            translated = result.orEmpty()
            translateStatus = error?.let { "Couldn't translate: $it" }
            refreshTranslateBar()
        }
    }

    override fun onPickLanguage(source: Boolean) {
        pickingSource = source
        openPanel(Panel.LANGUAGE)
    }

    private fun pickLanguage(code: String) {
        if (pickingSource) prefs.translateFrom = code else prefs.translateTo = code
        closePanel()
        translateBar?.setLanguages(prefs.translateFrom, prefs.translateTo)
        translated = ""
        prepareTranslator()
    }

    override fun onSwapLanguages() {
        val from = prefs.translateFrom
        prefs.translateFrom = prefs.translateTo
        prefs.translateTo = from
        translateBar?.setLanguages(prefs.translateFrom, prefs.translateTo)
        translated = ""
        prepareTranslator()
    }

    override fun onInsertTranslation() {
        val out = translated.ifEmpty { translateBuffer.toString() }.trim()
        if (out.isEmpty()) return
        currentInputConnection?.commitText(out, 1)
        translateBuffer.clear()
        translated = ""
        refreshTranslateBar()
    }

    override fun onCloseTranslate() {
        uiHandler.removeCallbacks(translateLater)
        restoreStrip()
        updateShift()
        updateSuggestions()
    }

    // ---------------------------------------------------------------- text editing panel

    private fun sendKey(code: Int, meta: Int = 0) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.finishComposingText()
            composing.clear()
            glideAlternatives = emptyList()
        }
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        feedback.tick()
    }

    // Selection and clipboard work through InputConnection.setSelection and the
    // system clipboard directly. Shift+arrow key events and context-menu actions
    // are ignored by many apps (and by Samsung's text fields), which broke Groom.

    /** Fixed end of the selection while select mode is on; the other end moves. */
    private var selectAnchor = -1
    private var selectHead = -1

    /** Absolute (start, end) of the current selection, or null if the app won't say. */
    private fun selection(): Pair<Int, Int>? {
        val ic = currentInputConnection ?: return null
        val et = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 0 }, 0)
        if (et != null && et.selectionStart >= 0) {
            return (et.startOffset + et.selectionStart) to (et.startOffset + et.selectionEnd)
        }
        return if (selStart >= 0) selStart to selEnd else null
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.clear()
            glideAlternatives = emptyList()
        }
    }

    override fun selectionMode(on: Boolean) {
        finishComposing()
        if (on) {
            val sel = selection()
            selectAnchor = sel?.first ?: -1
            selectHead = sel?.second ?: -1
        } else {
            // Leave the caret where the moving end was.
            val ic = currentInputConnection
            if (ic != null && selectHead >= 0) ic.setSelection(selectHead, selectHead)
            selectAnchor = -1
            selectHead = -1
        }
    }

    override fun moveCursor(dx: Int, dy: Int, select: Boolean) {
        val ic = currentInputConnection ?: return
        finishComposing()
        feedback.tick()
        if (dy != 0) {
            // Lines depend on the app's layout, so up/down has to go through key events.
            val t = SystemClock.uptimeMillis()
            val code = if (dy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
            val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
            if (select) selection()?.let { selectHead = if (it.first == selectAnchor) it.second else it.first }
            return
        }
        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = et.text?.toString() ?: return
        val base = et.startOffset
        val selA = base + et.selectionStart
        val selB = base + et.selectionEnd
        // One step = one whole character (emoji included).
        fun step(from: Int): Int {
            val local = (from - base).coerceIn(0, text.length)
            val it = BreakIterator.getCharacterInstance().apply { setText(text) }
            val next = if (dx > 0) it.following(local) else it.preceding(local)
            return base + if (next == BreakIterator.DONE) local else next
        }
        if (select) {
            if (selectAnchor < 0) { selectAnchor = selA; selectHead = selB }
            selectHead = step(selectHead)
            ic.setSelection(minOf(selectAnchor, selectHead), maxOf(selectAnchor, selectHead))
        } else {
            val pos = when {
                selA != selB -> if (dx > 0) selB else selA
                else -> step(selA)
            }
            ic.setSelection(pos, pos)
        }
    }

    private fun lineEdge(start: Boolean, select: Boolean) {
        val ic = currentInputConnection ?: return
        finishComposing()
        feedback.tick()
        val sel = selection() ?: return
        val target = if (start) {
            val before = textBefore(10_000)
            sel.first - (before.length - (before.lastIndexOf('\n') + 1))
        } else {
            val after = ic.getTextAfterCursor(10_000, 0)?.toString().orEmpty()
            val nl = after.indexOf('\n')
            sel.second + (if (nl >= 0) nl else after.length)
        }
        if (select) {
            if (selectAnchor < 0) selectAnchor = if (start) sel.second else sel.first
            selectHead = target
            ic.setSelection(minOf(selectAnchor, target), maxOf(selectAnchor, target))
        } else {
            ic.setSelection(target, target)
        }
    }

    override fun home(select: Boolean) = lineEdge(start = true, select = select)
    override fun end(select: Boolean) = lineEdge(start = false, select = select)

    override fun selectAll() {
        val ic = currentInputConnection ?: return
        finishComposing()
        feedback.tick()
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (et?.text != null && et.startOffset == 0 && et.partialStartOffset < 0) {
            ic.setSelection(0, et.text.length)
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
        selection()?.let { selectAnchor = it.first; selectHead = it.second }
    }

    private fun copySelection(): Boolean {
        val text = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        if (text.isEmpty()) return false
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("PawKeys", text))
        return true
    }

    override fun copy() {
        finishComposing()
        feedback.tick()
        if (copySelection()) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        selectionMode(false)
    }

    override fun cut() {
        finishComposing()
        feedback.tick()
        if (copySelection()) currentInputConnection?.commitText("", 1)
        selectAnchor = -1
        selectHead = -1
    }

    override fun paste() {
        val ic = currentInputConnection ?: return
        finishComposing()
        feedback.tick()
        val clip = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull()
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) ic.commitText(text, 1) else ic.performContextMenuAction(android.R.id.paste)
        selectAnchor = -1
        selectHead = -1
    }

    override fun undo() = sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
    override fun redo() = sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
    override fun delete() = handleDelete()
    override fun back() = closePanel()

    // ---------------------------------------------------------------- helpers

    private fun textBefore(n: Int): String = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString() ?: ""

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    private fun lastGraphemeLength(text: String): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val start = it.preceding(text.length)
        return if (start == BreakIterator.DONE) 1 else text.length - start
    }

    companion object {
        private val PUNCTUATION = listOf(". ", ", ", "? ", "! ", ": ", "; ")
    }
}

object UiDims {
    fun stripHeight(ctx: android.content.Context) = (48 * ctx.resources.displayMetrics.density).toInt()
}
