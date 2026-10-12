package com.pawkeys.keyboard

import android.content.Context
import android.content.res.Configuration

/**
 * Colour palette ("coat") for the keyboard: quiet neutrals, flat keys and a single
 * accent colour, each named after a cat coat.
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Int,
    val key: Int,
    val functionKey: Int,
    val keyPressed: Int,
    val keyText: Int,
    val hintText: Int,
    val accent: Int,
    val onAccent: Int,
    val border: Int,
    val stripText: Int,
    val popup: Int,
) {
    companion object {
        val CALICO = KeyboardTheme(
            id = "calico", name = "Calico", isDark = false,
            background = 0xFFF1F2F5.toInt(), key = 0xFFFFFFFF.toInt(), functionKey = 0xFFE2E4E9.toInt(),
            keyPressed = 0xFFD3D6DD.toInt(), keyText = 0xFF1A1B1F.toInt(), hintText = 0xFF9A9CA5.toInt(),
            accent = 0xFFD9773A.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x14000000,
            stripText = 0xFF55575E.toInt(), popup = 0xFFFFFFFF.toInt(),
        )
        val TUXEDO = KeyboardTheme(
            id = "tuxedo", name = "Tuxedo", isDark = true,
            background = 0xFF111113.toInt(), key = 0xFF242428.toInt(), functionKey = 0xFF1A1A1D.toInt(),
            keyPressed = 0xFF36363C.toInt(), keyText = 0xFFEDEDF0.toInt(), hintText = 0xFF7D7D86.toInt(),
            accent = 0xFFEFA262.toInt(), onAccent = 0xFF1A1208.toInt(), border = 0x1FFFFFFF,
            stripText = 0xFFB9B9C0.toInt(), popup = 0xFF2E2E33.toInt(),
        )
        val SIAMESE = KeyboardTheme(
            id = "siamese", name = "Siamese", isDark = false,
            background = 0xFFF4EFE7.toInt(), key = 0xFFFFFDF9.toInt(), functionKey = 0xFFE8DFD1.toInt(),
            keyPressed = 0xFFDACDB9.toInt(), keyText = 0xFF2E2621.toInt(), hintText = 0xFFA39383.toInt(),
            accent = 0xFF8A5A3B.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x142E2621,
            stripText = 0xFF5E5047.toInt(), popup = 0xFFFFFDF9.toInt(),
        )
        val RAGDOLL = KeyboardTheme(
            id = "ragdoll", name = "Ragdoll", isDark = false,
            background = 0xFFEEF1F7.toInt(), key = 0xFFFFFFFF.toInt(), functionKey = 0xFFDCE2EE.toInt(),
            keyPressed = 0xFFC9D2E2.toInt(), keyText = 0xFF18202E.toInt(), hintText = 0xFF8E97A8.toInt(),
            accent = 0xFF4A78E0.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x1418202E,
            stripText = 0xFF4A5366.toInt(), popup = 0xFFFFFFFF.toInt(),
        )
        val PANTHER = KeyboardTheme(
            id = "panther", name = "Panther", isDark = true,
            background = 0xFF000000.toInt(), key = 0xFF141416.toInt(), functionKey = 0xFF0A0A0B.toInt(),
            keyPressed = 0xFF27272B.toInt(), keyText = 0xFFE8E8EA.toInt(), hintText = 0xFF6E6E75.toInt(),
            accent = 0xFFC6A6FF.toInt(), onAccent = 0xFF0E0718.toInt(), border = 0x26FFFFFF,
            stripText = 0xFFA9A9B0.toInt(), popup = 0xFF222226.toInt(),
        )
        val HIGH_CONTRAST = KeyboardTheme(
            id = "contrast", name = "High contrast", isDark = true,
            background = 0xFF000000.toInt(), key = 0xFF000000.toInt(), functionKey = 0xFF000000.toInt(),
            keyPressed = 0xFF333300.toInt(), keyText = 0xFFFFE600.toInt(), hintText = 0xFFFFFFFF.toInt(),
            accent = 0xFFFFE600.toInt(), onAccent = 0xFF000000.toInt(), border = 0xFFFFE600.toInt(),
            stripText = 0xFFFFE600.toInt(), popup = 0xFF1A1A1A.toInt(),
        )

        val ALL = listOf(CALICO, TUXEDO, SIAMESE, RAGDOLL, PANTHER, HIGH_CONTRAST)

        /** "system" (Auto) follows the phone's dark mode using the breed's native colours. */
        fun resolve(context: Context, id: String, breed: Breed = Breed.PAWKEYS): KeyboardTheme {
            if (id == "system") {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                return if (night == Configuration.UI_MODE_NIGHT_YES) breed.darkCoat else breed.lightCoat
            }
            return ALL.firstOrNull { it.id == id }
                ?: Breed.entries.flatMap { listOf(it.lightCoat, it.darkCoat) }.firstOrNull { it.id == id }
                ?: CALICO
        }
    }
}
