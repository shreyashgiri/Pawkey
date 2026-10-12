package com.pawkeys.keyboard.panels

import java.util.Locale

/**
 * Finds emoji by name. Names come from the Unicode character names built into
 * Android ("GRINNING CAT FACE WITH SMILING EYES"), flags from country names, plus
 * everyday words people actually search for.
 */
object EmojiSearch {
    private val synonyms: Map<String, String> = mapOf(
        "lol" to "😂 🤣 😹 😆", "haha" to "😂 🤣 😹 😆 😄", "laugh" to "😂 🤣 😹 😆",
        "love" to "❤️ 😍 😻 🥰 😘 💕 💖", "heart" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💕 💖 😻",
        "kiss" to "😘 😗 😙 😚 😽 💋", "sad" to "😢 😭 😿 😞 😔 🥺", "cry" to "😢 😭 😿",
        "angry" to "😠 😡 🤬 😾", "mad" to "😠 😡 🤬 😾", "happy" to "😀 😃 😄 😊 😸 😺",
        "smile" to "😀 😃 😄 😊 🙂 😺", "ok" to "👌 👍 🆗", "yes" to "👍 ✅ 👌", "no" to "👎 ❌ 🙅",
        "thanks" to "🙏 😊 💐", "please" to "🙏 🥺", "party" to "🥳 🎉 🎊 🎈", "birthday" to "🎂 🥳 🎉 🎁",
        "fire" to "🔥", "lit" to "🔥", "cool" to "😎 🆒", "hi" to "👋 😊", "hello" to "👋 😊", "bye" to "👋",
        "wow" to "😮 😲 🤩 🙀", "omg" to "😱 🙀 😲", "sleep" to "😴 💤", "tired" to "😴 🥱 😩",
        "think" to "🤔", "clap" to "👏", "strong" to "💪", "money" to "💰 💸 💵 🤑", "food" to "🍕 🍔 🍟 🍜 🍣",
        "coffee" to "☕", "tea" to "🍵 🫖", "beer" to "🍺 🍻", "drink" to "🍹 🍸 🍷 🍺", "music" to "🎵 🎶 🎧 🎸",
        "sun" to "☀️ 🌞", "rain" to "🌧️ ☔", "cold" to "🥶 ❄️", "hot" to "🥵 🔥", "cat" to "🐱 🐈 🐈‍⬛ 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🐾",
        "kitty" to "🐱 😺 🐈", "paw" to "🐾", "dog" to "🐶 🐕", "good" to "👍 👌 💯", "perfect" to "💯 👌",
        "sorry" to "😔 🙏 🥺", "shock" to "😱 😲 🙀", "scared" to "😨 😱 🙀", "sick" to "🤒 🤢 🤮 😷",
        "dead" to "💀 ☠️", "eyes" to "👀", "star" to "⭐ 🌟 ✨", "sparkle" to "✨", "flower" to "🌸 🌹 🌻 🌷 💐",
    )

    /** Searchable text per emoji, built once. */
    private var index: List<Pair<String, String>>? = null

    private fun build(all: List<String>): List<Pair<String, String>> = all.map { e -> e to describe(e) }

    /** Unicode names of the emoji's characters (or the country, for flags), lower-cased. */
    fun describe(emoji: String): String {
        val cps = emoji.codePoints().toArray().filter { it != 0xFE0F && it != 0x200D }
        if (cps.size == 2 && cps.all { it in 0x1F1E6..0x1F1FF }) {
            val code = cps.joinToString("") { ((it - 0x1F1E6) + 'A'.code).toChar().toString() }
            return (Locale("", code).getDisplayCountry(Locale.ENGLISH) + " flag " + code).lowercase()
        }
        return cps.mapNotNull { Character.getName(it) }.joinToString(" ").lowercase()
    }

    fun search(query: String, all: List<String>, limit: Int = 40): List<String> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val idx = index ?: build(all).also { index = it }
        val results = LinkedHashSet<String>()
        // Everyday words first, then Unicode names where every word matches the start of a name word.
        if (words.size == 1) synonyms.forEach { (k, v) -> if (k.startsWith(words[0])) results += v.split(' ') }
        for ((emoji, name) in idx) {
            val nameWords = name.split(' ')
            if (words.all { w -> nameWords.any { it.startsWith(w) } }) results += emoji
            if (results.size >= limit) break
        }
        return results.take(limit)
    }
}
