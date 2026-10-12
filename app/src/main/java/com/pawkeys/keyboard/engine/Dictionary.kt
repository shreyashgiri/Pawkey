package com.pawkeys.keyboard.engine

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

data class Suggestion(
    val word: String,
    val score: Double,
    /** True when [word] is a fix for a typo rather than a completion of what was typed. */
    val isCorrection: Boolean = false,
    /** Set for text-shortcut expansions so the strip can show them differently. */
    val isShortcut: Boolean = false,
)

/**
 * Word list with frequency-based completion and proximity-aware typo correction.
 * Pure Kotlin so it can be unit tested on the JVM.
 */
class Dictionary(stream: InputStream) {
    private val words: Array<String>
    private val freqs: IntArray
    /** Lower-case, apostrophe-free form, used for matching "dont" against "don't". */
    private val keys: Array<String>
    /** Indices into [words], sorted by [keys] for prefix range lookups. */
    private val sorted: IntArray
    private val byKey = HashMap<String, Int>()
    private val byLower = HashMap<String, Int>()

    init {
        val w = ArrayList<String>(32000)
        val f = ArrayList<Int>(32000)
        stream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    w += line.substring(0, tab)
                    f += line.substring(tab + 1).trim().toIntOrNull() ?: 1
                }
            }
        }
        words = w.toTypedArray()
        freqs = f.toIntArray()
        keys = Array(words.size) { normalize(words[it]) }
        for (i in words.indices) {
            byKey.putIfAbsent(keys[i], i)
            byLower.putIfAbsent(words[i].lowercase(), i)
        }
        sorted = words.indices.sortedBy { keys[it] }.toIntArray()
    }

    val size get() = words.size

    fun contains(word: String) = byLower.containsKey(word.lowercase())

    fun frequency(word: String): Int = byLower[word.lowercase()]?.let { freqs[it] } ?: 0

    /** Iterates every word with its frequency and normalized key (used by glide typing). */
    fun forEachWord(block: (word: String, key: String, freq: Int) -> Unit) {
        for (i in words.indices) block(words[i], keys[i], freqs[i])
    }

    /**
     * Returns up to [limit] suggestions for [typed], best first.
     * [extra] lets the caller mix in learned words (word -> pseudo frequency).
     */
    fun suggest(
        typed: String,
        limit: Int = 3,
        extra: Map<String, Int> = emptyMap(),
        blocked: (String) -> Boolean = { false },
    ): List<Suggestion> {
        if (typed.isEmpty()) return emptyList()
        val key = normalize(typed)
        if (key.isEmpty()) return emptyList()
        val best = HashMap<String, Suggestion>()

        fun offer(word: String, score: Double, correction: Boolean) {
            if (blocked(word) && !word.equals(typed, ignoreCase = true)) return
            val prev = best[word.lowercase()]
            if (prev == null || prev.score < score) best[word.lowercase()] = Suggestion(word, score, correction)
        }

        // Exact (or apostrophe-insensitive) match.
        byLower[typed.lowercase()]?.let { offer(words[it], 100.0 + ln(freqs[it].toDouble()), false) }
        byKey[key]?.let { if (!words[it].equals(typed, true)) offer(words[it], 50.0 + ln(freqs[it].toDouble()), true) }

        // Completions: words that start with what was typed.
        val (from, to) = prefixRange(key)
        for (p in from until to) {
            val i = sorted[p]
            val extraLen = keys[i].length - key.length
            if (extraLen == 0) continue
            offer(words[i], ln(freqs[i].toDouble()) - 1.0 - extraLen * 0.35, false)
        }

        // Corrections: proximity-weighted edit distance on similar-length words.
        if (key.length >= 2) {
            val maxDist = if (key.length <= 3) 1.0 else if (key.length <= 6) 1.6 else 2.2
            for (i in words.indices) {
                val k = keys[i]
                if (abs(k.length - key.length) > 2) continue
                if (k[0] != key[0] && !KeyProximity.adjacent(k[0], key[0])) continue
                if (k == key) continue
                val d = KeyProximity.editDistance(key, k, maxDist)
                if (d <= maxDist) offer(words[i], ln(freqs[i].toDouble()) - d * 4.0, true)
            }
        }

        for ((word, freq) in extra) {
            val wk = normalize(word)
            when {
                wk == key -> offer(word, 100.0 + ln(freq.toDouble()) + 2, false)
                wk.startsWith(key) -> offer(word, ln(freq.toDouble()) + 1.0 - (wk.length - key.length) * 0.35, false)
                key.length >= 3 && abs(wk.length - key.length) <= 2 -> {
                    val d = KeyProximity.editDistance(key, wk, 1.6)
                    if (d <= 1.6) offer(word, ln(freq.toDouble()) + 1.0 - d * 4.0, true)
                }
            }
        }

        return best.values.sortedByDescending { it.score }.take(limit).map { it.copy(word = matchCase(typed, it.word)) }
    }

    /**
     * The word autocorrect should replace [typed] with, or null when [typed] should be
     * left alone (it's a known word, too short, or no candidate is convincing).
     */
    fun autocorrect(typed: String, extra: Map<String, Int> = emptyMap(), blocked: (String) -> Boolean = { false }): String? {
        if (typed.length < 2 && typed != "i") return null
        if (typed == "i") return "I"
        if (contains(typed) || extra.keys.any { it.equals(typed, true) }) return null
        if (typed.any { it.isDigit() }) return null
        // A word typed in ALL CAPS is probably an acronym the dictionary doesn't know.
        if (typed.length > 1 && typed.all { it.isUpperCase() }) return null
        val top = suggest(typed, 2, extra, blocked).firstOrNull() ?: return null
        if (!top.isCorrection) return null
        if (top.word.equals(typed, true)) return null
        return top.word
    }

    private fun prefixRange(prefix: String): Pair<Int, Int> {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[sorted[mid]] < prefix) lo = mid + 1 else hi = mid
        }
        val start = lo
        hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[sorted[mid]].startsWith(prefix)) lo = mid + 1 else hi = mid
        }
        return start to lo
    }

    companion object {
        fun normalize(word: String): String {
            val sb = StringBuilder(word.length)
            for (c in word) if (c != '\'' && c != '’') sb.append(c.lowercaseChar())
            return sb.toString()
        }

        /** Applies the capitalisation the user typed ("Hel" -> "Hello", "HEL" -> "HELLO"). */
        fun matchCase(typed: String, word: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}

/** Physical layout of a QWERTY keyboard, used to weigh "near miss" typos as cheaper. */
object KeyProximity {
    private val pos = HashMap<Char, Pair<Float, Float>>().apply {
        "qwertyuiop".forEachIndexed { i, c -> put(c, i.toFloat() to 0f) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, i + 0.5f to 1f) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, i + 1.5f to 2f) }
    }

    fun adjacent(a: Char, b: Char): Boolean {
        val pa = pos[a] ?: return false
        val pb = pos[b] ?: return false
        val dx = pa.first - pb.first
        val dy = pa.second - pb.second
        return dx * dx + dy * dy <= 1.6f * 1.6f
    }

    private fun subCost(a: Char, b: Char) = when {
        a == b -> 0.0
        adjacent(a, b) -> 0.55
        else -> 1.0
    }

    /** Damerau-Levenshtein distance where hitting a neighbouring key costs less. */
    fun editDistance(a: String, b: String, cutoff: Double = Double.MAX_VALUE): Double {
        val n = a.length
        val m = b.length
        var prev2 = DoubleArray(m + 1)
        var prev = DoubleArray(m + 1) { it.toDouble() }
        var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            for (j in 1..m) {
                var v = min(prev[j] + 1.0, cur[j - 1] + 1.0)
                v = min(v, prev[j - 1] + subCost(a[i - 1], b[j - 1]))
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prev2[j - 2] + 0.7)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > cutoff) return rowMin
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
