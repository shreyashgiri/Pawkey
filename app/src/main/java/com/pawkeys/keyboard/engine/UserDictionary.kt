package com.pawkeys.keyboard.engine

import java.io.File

/**
 * What the keyboard learns from you: new words, which word tends to follow which
 * (for next-word prediction), and words you removed from suggestions.
 * Stored as plain text files in the app's private storage.
 */
class UserDictionary(private val dir: File) {
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val blocked = HashSet<String>()
    private var dirty = false

    init {
        read("user_words.txt") { parts -> if (parts.size == 2) words[parts[0]] = parts[1].toIntOrNull() ?: 1 }
        read("user_bigrams.txt") { parts ->
            if (parts.size == 3) bigrams.getOrPut(parts[0]) { HashMap() }[parts[1]] = parts[2].toIntOrNull() ?: 1
        }
        read("blocked_words.txt") { parts -> parts.firstOrNull()?.let { if (it.isNotEmpty()) blocked += it } }
    }

    /** Learned words with a pseudo-frequency comparable to the main dictionary's. */
    val learned: Map<String, Int>
        get() = words.filterValues { it >= 2 }.mapValues { 2000 * it.value }

    fun isBlocked(word: String) = word.lowercase() in blocked

    fun learnWord(word: String, boost: Int = 1) {
        if (word.length < 2 || word.length > 32) return
        blocked -= word.lowercase()
        words[word] = (words[word] ?: 0) + boost
        dirty = true
        if (words.size > MAX_WORDS) trim(words)
    }

    fun learnBigram(previous: String, next: String) {
        if (previous.isEmpty() || next.isEmpty()) return
        val map = bigrams.getOrPut(previous.lowercase()) { HashMap() }
        map[next] = (map[next] ?: 0) + 1
        if (map.size > 24) trim(map)
        if (bigrams.size > MAX_BIGRAM_HEADS) bigrams.keys.take(bigrams.size - MAX_BIGRAM_HEADS).forEach { bigrams.remove(it) }
        dirty = true
    }

    fun predictNext(previous: String, limit: Int = 3): List<String> =
        bigrams[previous.lowercase()].orEmpty().entries
            .filter { !isBlocked(it.key) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    /** "Remove from predictions" (long-press a suggestion). */
    fun block(word: String) {
        blocked += word.lowercase()
        words.keys.filter { it.equals(word, true) }.forEach { words.remove(it) }
        bigrams.values.forEach { m -> m.keys.filter { it.equals(word, true) }.forEach { m.remove(it) } }
        dirty = true
    }

    fun clear() {
        words.clear(); bigrams.clear(); blocked.clear()
        dirty = true
        save()
    }

    fun save() {
        if (!dirty) return
        dirty = false
        write("user_words.txt", words.entries.map { "${it.key}\t${it.value}" })
        write("user_bigrams.txt", bigrams.flatMap { (a, m) -> m.map { "$a\t${it.key}\t${it.value}" } })
        write("blocked_words.txt", blocked.toList())
    }

    private fun trim(map: HashMap<String, Int>) {
        val keep = map.entries.sortedByDescending { it.value }.take(map.size * 3 / 4).associate { it.key to it.value }
        map.clear(); map.putAll(keep)
    }

    private fun read(name: String, block: (List<String>) -> Unit) {
        val f = File(dir, name)
        if (!f.exists()) return
        runCatching { f.forEachLine { block(it.split('\t')) } }
    }

    private fun write(name: String, lines: List<String>) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(lines.joinToString("\n"))
            tmp.renameTo(File(dir, name))
        }
    }

    companion object {
        private const val MAX_WORDS = 5000
        private const val MAX_BIGRAM_HEADS = 3000
    }
}
