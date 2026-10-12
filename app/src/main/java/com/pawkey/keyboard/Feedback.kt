package com.pawkey.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Key press sound and vibration. */
class Feedback(context: Context, private val prefs: Prefs) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun onKey(code: Int) {
        if (prefs.vibrate) vibrate(prefs.vibrateMs.toLong())
        if (prefs.sound) {
            val fx = when (code) {
                Codes.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Codes.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Codes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    fun tick() {
        if (prefs.vibrate) vibrate(maxOf(5L, prefs.vibrateMs / 2L))
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
