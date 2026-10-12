package com.pawkeys.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/** Sends images (stickers, GIFs) into the app the way keyboards are meant to. */
object RichContent {
    /** Folder in the cache that the FileProvider shares (see res/xml/file_paths.xml). */
    fun shareDir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    fun accepts(info: EditorInfo?, mime: String): Boolean =
        info != null && EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }

    enum class Result { SENT, COPIED, FAILED }

    /**
     * Commits [file] to the editor if it accepts [mime]. Otherwise puts the image on
     * the clipboard so it can be pasted (many chat apps accept pasted images).
     */
    fun send(context: Context, ic: InputConnection?, info: EditorInfo?, file: File, mime: String, description: String): Result {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        if (ic != null && info != null && accepts(info, mime)) {
            val content = InputContentInfoCompat(uri, ClipDescription(description, arrayOf(mime)), null)
            val ok = InputConnectionCompat.commitContent(ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            if (ok) return Result.SENT
        }
        return runCatching {
            info?.packageName?.let { context.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newUri(context.contentResolver, description, uri))
            Result.COPIED
        }.getOrDefault(Result.FAILED)
    }
}
