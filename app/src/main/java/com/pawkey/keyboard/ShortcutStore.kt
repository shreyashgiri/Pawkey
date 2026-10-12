package com.pawkey.keyboard

import android.content.Context
import org.json.JSONObject

/** Samsung "Text shortcuts": type a short code, get the expansion offered in the suggestion bar. */
class ShortcutStore(context: Context) {
    private val sp = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    fun all(): Map<String, String> {
        val json = sp.getString("map", null) ?: return DEFAULTS
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }.toSortedMap()
        }.getOrDefault(DEFAULTS)
    }

    fun expansionFor(word: String): String? = all()[word.lowercase()]

    fun put(shortcut: String, expansion: String) = save(all() + (shortcut.lowercase().trim() to expansion))

    fun remove(shortcut: String) = save(all() - shortcut)

    private fun save(map: Map<String, String>) {
        sp.edit().putString("map", JSONObject(map).toString()).apply()
    }

    companion object {
        val DEFAULTS = sortedMapOf(
            "omw" to "On my way! 🐾",
            "brb" to "Be right back",
            "ty" to "Thank you!",
            "meow" to "😺 meow~",
        )
    }
}
