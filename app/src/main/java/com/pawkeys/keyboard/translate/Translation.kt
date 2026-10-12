package com.pawkeys.keyboard.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** Languages offered in the picker: ML Kit code to display name. */
object Languages {
    val all: List<Pair<String, String>> = listOf(
        TranslateLanguage.ENGLISH to "English",
        TranslateLanguage.HINDI to "Hindi",
        TranslateLanguage.MARATHI to "Marathi",
        TranslateLanguage.BENGALI to "Bengali",
        TranslateLanguage.GUJARATI to "Gujarati",
        TranslateLanguage.TAMIL to "Tamil",
        TranslateLanguage.TELUGU to "Telugu",
        TranslateLanguage.KANNADA to "Kannada",
        TranslateLanguage.URDU to "Urdu",
        TranslateLanguage.SPANISH to "Spanish",
        TranslateLanguage.FRENCH to "French",
        TranslateLanguage.GERMAN to "German",
        TranslateLanguage.ITALIAN to "Italian",
        TranslateLanguage.PORTUGUESE to "Portuguese",
        TranslateLanguage.DUTCH to "Dutch",
        TranslateLanguage.RUSSIAN to "Russian",
        TranslateLanguage.UKRAINIAN to "Ukrainian",
        TranslateLanguage.TURKISH to "Turkish",
        TranslateLanguage.ARABIC to "Arabic",
        TranslateLanguage.PERSIAN to "Persian",
        TranslateLanguage.CHINESE to "Chinese",
        TranslateLanguage.JAPANESE to "Japanese",
        TranslateLanguage.KOREAN to "Korean",
        TranslateLanguage.INDONESIAN to "Indonesian",
        TranslateLanguage.VIETNAMESE to "Vietnamese",
        TranslateLanguage.THAI to "Thai",
    )

    fun name(code: String) = all.firstOrNull { it.first == code }?.second ?: code
}

/** What the keyboard needs from a translator; [MlKitTranslator] in the app, a fake in tests. */
interface TextTranslator {
    /** Gets the language model if needed (one-time download), then calls [done] with an error or null. */
    fun prepare(from: String, to: String, done: (error: String?) -> Unit)
    fun translate(text: String, from: String, to: String, done: (result: String?, error: String?) -> Unit)
    fun close()
}

/** On-device translation with Google ML Kit. Models download once per language, then work offline. */
class MlKitTranslator : TextTranslator {
    private var key = ""
    private var translator: Translator? = null

    private fun get(from: String, to: String): Translator {
        val k = "$from>$to"
        if (k != key) {
            translator?.close()
            translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
            key = k
        }
        return translator!!
    }

    override fun prepare(from: String, to: String, done: (String?) -> Unit) {
        get(from, to).downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Download failed") }
    }

    override fun translate(text: String, from: String, to: String, done: (String?, String?) -> Unit) {
        get(from, to).translate(text)
            .addOnSuccessListener { done(it, null) }
            .addOnFailureListener { done(null, it.message ?: "Translation failed") }
    }

    override fun close() {
        translator?.close()
        translator = null
        key = ""
    }
}
