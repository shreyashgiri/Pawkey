package com.pawkeys.keyboard

import android.content.Context
import android.content.res.Configuration

/**
 * Colour palette for the keyboard. The look follows Samsung's One UI keyboard
 * (flat, softly rounded keys, muted function keys), each one named after a cat
 * coat and with a single cat-coloured accent.
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
            background = 0xFFE9EAEE.toInt(), key = 0xFFFCFCFD.toInt(), functionKey = 0xFFD5D8DF.toInt(),
            keyPressed = 0xFFC3C7D0.toInt(), keyText = 0xFF1F2024.toInt(), hintText = 0xFF8A8D96.toInt(),
            accent = 0xFFE8833A.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x1F000000,
            stripText = 0xFF3A3B40.toInt(), popup = 0xFFFFFFFF.toInt(),
        )
        val TUXEDO = KeyboardTheme(
            id = "tuxedo", name = "Tuxedo", isDark = true,
            background = 0xFF1B1B1D.toInt(), key = 0xFF323235.toInt(), functionKey = 0xFF262628.toInt(),
            keyPressed = 0xFF4A4A4E.toInt(), keyText = 0xFFF2F2F4.toInt(), hintText = 0xFF8E8E93.toInt(),
            accent = 0xFFF2A65A.toInt(), onAccent = 0xFF1B1B1D.toInt(), border = 0x26FFFFFF,
            stripText = 0xFFE2E2E6.toInt(), popup = 0xFF3C3C40.toInt(),
        )
        val SIAMESE = KeyboardTheme(
            id = "siamese", name = "Siamese", isDark = false,
            background = 0xFFF1EADF.toInt(), key = 0xFFFFFCF7.toInt(), functionKey = 0xFFE2D6C4.toInt(),
            keyPressed = 0xFFD3C3AC.toInt(), keyText = 0xFF3B2F2A.toInt(), hintText = 0xFF9C8B7C.toInt(),
            accent = 0xFF7A5238.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x1F3B2F2A,
            stripText = 0xFF4A3B33.toInt(), popup = 0xFFFFFCF7.toInt(),
        )
        val RAGDOLL = KeyboardTheme(
            id = "ragdoll", name = "Ragdoll", isDark = false,
            background = 0xFFE8EDF5.toInt(), key = 0xFFFDFEFF.toInt(), functionKey = 0xFFD0D9E8.toInt(),
            keyPressed = 0xFFBCC8DB.toInt(), keyText = 0xFF1C2433.toInt(), hintText = 0xFF8590A3.toInt(),
            accent = 0xFF3E7BFA.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x1F1C2433,
            stripText = 0xFF2E3747.toInt(), popup = 0xFFFFFFFF.toInt(),
        )
        val PANTHER = KeyboardTheme(
            id = "panther", name = "Black Panther (AMOLED)", isDark = true,
            background = 0xFF000000.toInt(), key = 0xFF161618.toInt(), functionKey = 0xFF0C0C0D.toInt(),
            keyPressed = 0xFF2C2C30.toInt(), keyText = 0xFFEDEDED.toInt(), hintText = 0xFF77777C.toInt(),
            accent = 0xFFD7B8FF.toInt(), onAccent = 0xFF000000.toInt(), border = 0x33FFFFFF,
            stripText = 0xFFDADADA.toInt(), popup = 0xFF242427.toInt(),
        )
        val HIGH_CONTRAST = KeyboardTheme(
            id = "contrast", name = "High contrast", isDark = true,
            background = 0xFF000000.toInt(), key = 0xFF000000.toInt(), functionKey = 0xFF000000.toInt(),
            keyPressed = 0xFF333300.toInt(), keyText = 0xFFFFE600.toInt(), hintText = 0xFFFFFFFF.toInt(),
            accent = 0xFFFFE600.toInt(), onAccent = 0xFF000000.toInt(), border = 0xFFFFE600.toInt(),
            stripText = 0xFFFFE600.toInt(), popup = 0xFF1A1A1A.toInt(),
        )

        val ALL = listOf(CALICO, TUXEDO, SIAMESE, RAGDOLL, PANTHER, HIGH_CONTRAST)

        /** "system" follows the phone's dark mode: Calico by day, Tuxedo by night. */
        fun resolve(context: Context, id: String): KeyboardTheme {
            if (id == "system") {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                return if (night == Configuration.UI_MODE_NIGHT_YES) TUXEDO else CALICO
            }
            return ALL.firstOrNull { it.id == id } ?: CALICO
        }
    }
}
