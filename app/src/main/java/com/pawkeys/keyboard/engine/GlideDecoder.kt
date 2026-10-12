package com.pawkeys.keyboard.engine

import kotlin.math.hypot
import kotlin.math.ln

/**
 * Swipe ("glide") typing like Samsung's: compares the finger trace with the ideal
 * path through each candidate word's keys and ranks by closeness and frequency.
 */
class GlideDecoder(private val dictionary: Dictionary) {
    private class Entry(val word: String, val letters: String, val logFreq: Double)

    /** Candidates grouped by "first letter + last letter" to keep decoding fast. */
    private val byEnds = HashMap<String, MutableList<Entry>>()

    init {
        dictionary.forEachWord { word, key, freq -> index(word, key, freq) }
    }

    private fun index(word: String, key: String, freq: Int) {
        if (key.length < 2 || !key.all { it in 'a'..'z' }) return
        val letters = dedupe(key)
        byEnds.getOrPut("${key.first()}${key.last()}") { ArrayList() } += Entry(word, letters, ln(freq.toDouble()))
    }

    /**
     * @param xs, ys the trace in keyboard pixels
     * @param keyCenters centre of each letter key in the same coordinates
     * @param keyWidth width of one letter key, used to normalise distances
     */
    fun decode(
        xs: FloatArray,
        ys: FloatArray,
        keyCenters: Map<Char, Pair<Float, Float>>,
        keyWidth: Float,
        extra: Map<String, Int> = emptyMap(),
        limit: Int = 4,
    ): List<String> {
        if (xs.size < 2 || keyWidth <= 0f) return emptyList()
        val trace = resample(xs, ys)
        val starts = nearestKeys(xs.first(), ys.first(), keyCenters, keyWidth)
        val ends = nearestKeys(xs.last(), ys.last(), keyCenters, keyWidth)
        val traceLen = pathLength(xs, ys) / keyWidth

        val scored = ArrayList<Pair<String, Double>>()
        fun consider(word: String, letters: String, logFreq: Double) {
            val ideal = idealPath(letters, keyCenters) ?: return
            val idealLen = pathLength(ideal.first, ideal.second) / keyWidth
            if (traceLen < idealLen * 0.45 - 1 || traceLen > idealLen * 2.2 + 1.5) return
            val resampled = resample(ideal.first, ideal.second)
            var sum = 0.0
            for (i in 0 until N) sum += hypot((trace.first[i] - resampled.first[i]).toDouble(), (trace.second[i] - resampled.second[i]).toDouble())
            val avg = sum / N / keyWidth
            scored += word to (avg * 4.0 - logFreq * 0.12)
        }

        for (s in starts) for (e in ends) {
            byEnds["$s$e"]?.forEach { consider(it.word, it.letters, it.logFreq) }
        }
        for ((word, freq) in extra) {
            val key = Dictionary.normalize(word)
            if (key.length >= 2 && key.first() in starts && key.last() in ends && key.all { it in 'a'..'z' }) {
                consider(word, dedupe(key), ln(freq.toDouble()))
            }
        }
        return scored.sortedBy { it.second }.map { it.first }.distinct().take(limit)
    }

    private fun nearestKeys(x: Float, y: Float, centers: Map<Char, Pair<Float, Float>>, keyWidth: Float): Set<Char> {
        val sorted = centers.entries.sortedBy { hypot(it.value.first - x, it.value.second - y) }
        val nearest = sorted.first()
        val d0 = hypot(nearest.value.first - x, nearest.value.second - y)
        return sorted.filter { hypot(it.value.first - x, it.value.second - y) <= maxOf(d0 * 1.6f, keyWidth * 0.9f) }
            .take(4).map { it.key }.toSet()
    }

    private fun idealPath(letters: String, centers: Map<Char, Pair<Float, Float>>): Pair<FloatArray, FloatArray>? {
        val x = FloatArray(letters.length)
        val y = FloatArray(letters.length)
        for (i in letters.indices) {
            val c = centers[letters[i]] ?: return null
            x[i] = c.first; y[i] = c.second
        }
        return x to y
    }

    companion object {
        private const val N = 40

        /** "hello" -> "helo": double letters don't change the swipe path. */
        fun dedupe(s: String): String {
            val sb = StringBuilder()
            for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
            return sb.toString()
        }

        fun pathLength(xs: FloatArray, ys: FloatArray): Float {
            var len = 0f
            for (i in 1 until xs.size) len += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return len
        }

        /** Resamples a polyline to [N] equally spaced points. */
        fun resample(xs: FloatArray, ys: FloatArray): Pair<FloatArray, FloatArray> {
            val outX = FloatArray(N)
            val outY = FloatArray(N)
            val total = pathLength(xs, ys)
            if (total == 0f || xs.size == 1) {
                outX.fill(xs[0]); outY.fill(ys[0])
                return outX to outY
            }
            val step = total / (N - 1)
            outX[0] = xs[0]; outY[0] = ys[0]
            var idx = 1
            var acc = 0f
            var px = xs[0]
            var py = ys[0]
            var i = 1
            while (i < xs.size && idx < N) {
                val seg = hypot(xs[i] - px, ys[i] - py)
                if (acc + seg >= step && seg > 0f) {
                    val t = (step - acc) / seg
                    px += t * (xs[i] - px)
                    py += t * (ys[i] - py)
                    outX[idx] = px; outY[idx] = py
                    idx++
                    acc = 0f
                } else {
                    acc += seg
                    px = xs[i]; py = ys[i]
                    i++
                }
            }
            while (idx < N) { outX[idx] = xs.last(); outY[idx] = ys.last(); idx++ }
            return outX to outY
        }
    }
}
