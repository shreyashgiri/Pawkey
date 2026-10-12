package com.pawkeys.keyboard.gifs

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** One GIF: a small [preview] to show in the panel and a [full] version to send. */
data class Gif(val id: String, val title: String, val preview: String, val full: String, val width: Int, val height: Int)

/** Where GIFs come from; GIPHY in the app, a fake in tests. */
interface GifSource {
    /** Trending when [query] is null. Calls back on the main thread. */
    fun list(query: String?, done: (gifs: List<Gif>?, error: String?) -> Unit)
    fun fetch(url: String, done: (bytes: ByteArray?) -> Unit)
}

/** GIPHY's public API, using the key the user pasted into Settings. */
class GiphySource(private val apiKey: String) : GifSource {
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    override fun list(query: String?, done: (List<Gif>?, String?) -> Unit) {
        val base = "https://api.giphy.com/v1/gifs/"
        val url = if (query.isNullOrBlank()) "${base}trending?api_key=$apiKey&limit=30&rating=pg-13"
        else "${base}search?api_key=$apiKey&limit=30&rating=pg-13&q=${URLEncoder.encode(query, "UTF-8")}"
        io.execute {
            val result = runCatching { parse(String(get(url))) }
            main.post {
                result.fold(
                    onSuccess = { done(it, null) },
                    onFailure = { done(null, if (it.message?.contains("401") == true || it.message?.contains("403") == true) "GIPHY didn't accept the key" else "Couldn't reach GIPHY") },
                )
            }
        }
    }

    override fun fetch(url: String, done: (ByteArray?) -> Unit) {
        io.execute {
            val bytes = runCatching { get(url) }.getOrNull()
            main.post { done(bytes) }
        }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Reads GIPHY's response: small animated preview for the grid, a downsized GIF to send. */
        fun parse(json: String): List<Gif> {
            val data = JSONObject(json).getJSONArray("data")
            return (0 until data.length()).mapNotNull { i ->
                val o = data.getJSONObject(i)
                val images = o.optJSONObject("images") ?: return@mapNotNull null
                val small = images.optJSONObject("fixed_width_small") ?: images.optJSONObject("fixed_width") ?: return@mapNotNull null
                val full = images.optJSONObject("downsized") ?: images.optJSONObject("original") ?: small
                Gif(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    preview = small.optString("url"),
                    full = full.optString("url"),
                    width = small.optString("width").toIntOrNull() ?: 100,
                    height = small.optString("height").toIntOrNull() ?: 100,
                )
            }.filter { it.preview.isNotEmpty() && it.full.isNotEmpty() }
        }
    }
}
