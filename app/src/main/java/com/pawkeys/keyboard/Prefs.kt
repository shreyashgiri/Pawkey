package com.pawkeys.keyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Typed access to the settings shown in [com.pawkeys.keyboard.settings.SettingsActivity]. */
class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var theme: String
        get() = sp.getString(THEME, "system")!!
        set(v) = sp.edit().putString(THEME, v).apply()
    /** Keyboard style (PawKeys, Samsung, iPhone, Google). */
    var breed: Breed
        get() = Breed.from(sp.getString(BREED, Breed.PAWKEYS.id))
        set(v) = sp.edit().putString(BREED, v.id).apply()
    val keyBorders get() = sp.getBoolean(KEY_BORDERS, false)
    var numberRow: Boolean
        get() = sp.getBoolean(NUMBER_ROW, false)
        set(v) = sp.edit().putBoolean(NUMBER_ROW, v).apply()
    val hintChars get() = sp.getBoolean(HINT_CHARS, false)

    /** Keyboard height in percent of the default (Stretch). */
    var heightPercent: Int
        get() = sp.getInt(HEIGHT, 100)
        set(v) = sp.edit().putInt(HEIGHT, v.coerceIn(MIN_HEIGHT, MAX_HEIGHT)).apply()

    /** "off", "left" or "right". */
    var oneHanded: String
        get() = sp.getString(ONE_HANDED, "off")!!
        set(v) = sp.edit().putString(ONE_HANDED, v).apply()

    var sound: Boolean
        get() = sp.getBoolean(SOUND, false)
        set(v) = sp.edit().putBoolean(SOUND, v).apply()
    var vibrate: Boolean
        get() = sp.getBoolean(VIBRATE, true)
        set(v) = sp.edit().putBoolean(VIBRATE, v).apply()
    val vibrateMs get() = sp.getInt(VIBRATE_MS, 12)
    val keyPreview get() = sp.getBoolean(KEY_PREVIEW, true)
    val longPressMs get() = sp.getInt(LONG_PRESS_MS, 300).toLong()

    val suggestions get() = sp.getBoolean(SUGGESTIONS, true)
    val autocorrect get() = sp.getBoolean(AUTOCORRECT, true)
    val autoCap get() = sp.getBoolean(AUTO_CAP, true)
    val doubleSpacePeriod get() = sp.getBoolean(DOUBLE_SPACE_PERIOD, true)
    val smartPunctuation get() = sp.getBoolean(SMART_PUNCT, true)
    val learnWords get() = sp.getBoolean(LEARN_WORDS, true)
    val blockOffensive get() = sp.getBoolean(BLOCK_OFFENSIVE, true)
    val nextWordPrediction get() = sp.getBoolean(NEXT_WORD, true)

    /** "fleksy" (flick gestures), "glide" (swipe typing) or "off". */
    var gestureMode: String
        get() = sp.getString(GESTURE_MODE, "fleksy")!!
        set(v) = sp.edit().putString(GESTURE_MODE, v).apply()
    val spaceCursor get() = sp.getBoolean(SPACE_CURSOR, true)
    val deleteSwipe get() = sp.getBoolean(DELETE_SWIPE, true)
    val pawTrail get() = sp.getBoolean(PAW_TRAIL, true)
    val clipboardHistory get() = sp.getBoolean(CLIPBOARD_HISTORY, true)

    companion object {
        const val THEME = "theme"
        const val BREED = "breed"
        const val KEY_BORDERS = "key_borders"
        const val NUMBER_ROW = "number_row"
        const val HINT_CHARS = "hint_chars"
        const val HEIGHT = "keyboard_height"
        const val ONE_HANDED = "one_handed"
        const val SOUND = "sound"
        const val VIBRATE = "vibrate"
        const val VIBRATE_MS = "vibrate_ms"
        const val KEY_PREVIEW = "key_preview"
        const val LONG_PRESS_MS = "long_press_ms"
        const val SUGGESTIONS = "suggestions"
        const val AUTOCORRECT = "autocorrect"
        const val AUTO_CAP = "auto_cap"
        const val DOUBLE_SPACE_PERIOD = "double_space_period"
        const val SMART_PUNCT = "smart_punctuation"
        const val LEARN_WORDS = "learn_words"
        const val BLOCK_OFFENSIVE = "block_offensive"
        const val NEXT_WORD = "next_word"
        const val GESTURE_MODE = "gesture_mode"
        const val SPACE_CURSOR = "space_cursor"
        const val DELETE_SWIPE = "delete_swipe"
        const val PAW_TRAIL = "paw_trail"
        const val CLIPBOARD_HISTORY = "clipboard_history"

        const val MIN_HEIGHT = 70
        const val MAX_HEIGHT = 140

        /** Settings that change how the keyboard looks and require rebuilding the view. */
        val VISUAL_KEYS = setOf(THEME, BREED, KEY_BORDERS, NUMBER_ROW, HINT_CHARS, HEIGHT, ONE_HANDED)
    }
}
