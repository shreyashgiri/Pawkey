package com.pawkey.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers recently copied text (Samsung's clipboard panel). Pinned clips are
 * kept forever; others are capped at [MAX_ITEMS]. Clips marked sensitive
 * (passwords copied from password managers) are never stored.
 */
class ClipboardHistory(context: Context, private val prefs: Prefs) {
    data class Clip(val text: String, val pinned: Boolean, val time: Long)

    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val store = context.getSharedPreferences("clipboard", Context.MODE_PRIVATE)
    private val items = ArrayList<Clip>()
    var onChanged: (() -> Unit)? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    init {
        runCatching {
            val arr = JSONArray(store.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += Clip(o.getString("t"), o.optBoolean("p"), o.optLong("ts"))
            }
        }
        manager?.addPrimaryClipChangedListener(listener)
    }

    val clips: List<Clip> get() = items.toList()

    private fun capture() {
        if (!prefs.clipboardHistory) return
        val clip = runCatching { manager?.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val desc = clip.description
        if (Build.VERSION.SDK_INT >= 33 && desc?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        val text = clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (text.length > 5000) return
        val existing = items.indexOfFirst { it.text == text }
        val pinned = existing >= 0 && items[existing].pinned
        if (existing >= 0) items.removeAt(existing)
        items.add(0, Clip(text, pinned, System.currentTimeMillis()))
        trim()
        save()
    }

    fun togglePin(clip: Clip) {
        val i = items.indexOf(clip)
        if (i >= 0) { items[i] = clip.copy(pinned = !clip.pinned); save() }
    }

    fun remove(clip: Clip) { items.remove(clip); save() }

    fun clearUnpinned() { items.removeAll { !it.pinned }; save() }

    private fun trim() {
        while (items.count { !it.pinned } > MAX_ITEMS) {
            val last = items.indexOfLast { !it.pinned }
            if (last >= 0) items.removeAt(last) else break
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned).put("ts", it.time)) }
        store.edit().putString("items", arr.toString()).apply()
        onChanged?.invoke()
    }

    fun release() {
        manager?.removePrimaryClipChangedListener(listener)
    }

    companion object {
        const val MAX_ITEMS = 30
    }
}
