package com.pawkeys.keyboard

/**
 * A Breed is a keyboard style: key shapes, spacing, labels, bottom row and native
 * colours modelled on a familiar keyboard. Features stay PawKeys' own whatever the
 * breed. With the "Auto" coat each breed uses its own light/dark colours.
 */
enum class Breed(
    val id: String,
    val label: String,
    val radiusDp: Float,
    val gapHDp: Float,
    val gapVDp: Float,
    val rowDp: Float,
    val padSideDp: Float,
    val padTopDp: Float,
    val padBottomDp: Float,
    /** iOS-style 1dp darker edge under each key. */
    val keyShadow: Boolean,
    val enter: EnterStyle,
    val bottomRow: BottomRow,
    /** Spacebar text; null = PawKeys wordmark. */
    val spaceLabel: String?,
    val symbolsLabel: String,
    val moreSymbolsLabel: String,
    val alphaLabel: String,
    val lightCoat: KeyboardTheme,
    val darkCoat: KeyboardTheme,
) {
    PAWKEYS(
        "pawkeys", "PawKeys", radiusDp = 10f, gapHDp = 3f, gapVDp = 5f, rowDp = 56f,
        padSideDp = 5f, padTopDp = 6f, padBottomDp = 10f, keyShadow = false,
        enter = EnterStyle.ACCENT, bottomRow = BottomRow.STANDARD, spaceLabel = null,
        symbolsLabel = "123", moreSymbolsLabel = "=\\<", alphaLabel = "ABC",
        lightCoat = KeyboardTheme.CALICO, darkCoat = KeyboardTheme.TUXEDO,
    ),
    SAMSUNG(
        "samsung", "Samsung", radiusDp = 8f, gapHDp = 2.5f, gapVDp = 4.5f, rowDp = 54f,
        padSideDp = 3f, padTopDp = 4f, padBottomDp = 6f, keyShadow = false,
        enter = EnterStyle.FUNCTION, bottomRow = BottomRow.STANDARD, spaceLabel = "English (US)",
        symbolsLabel = "!#1", moreSymbolsLabel = "1/2", alphaLabel = "ABC",
        lightCoat = KeyboardTheme(
            id = "samsung_light", name = "Samsung light", isDark = false,
            background = 0xFFF0F1F3.toInt(), key = 0xFFFFFFFF.toInt(), functionKey = 0xFFDDDFE4.toInt(),
            keyPressed = 0xFFC8CBD2.toInt(), keyText = 0xFF111111.toInt(), hintText = 0xFF8A8C93.toInt(),
            accent = 0xFF3E91FF.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x14000000,
            stripText = 0xFF3C3D42.toInt(), popup = 0xFFFFFFFF.toInt(),
        ),
        darkCoat = KeyboardTheme(
            id = "samsung_dark", name = "Samsung dark", isDark = true,
            background = 0xFF171717.toInt(), key = 0xFF2F2F2F.toInt(), functionKey = 0xFF242424.toInt(),
            keyPressed = 0xFF454545.toInt(), keyText = 0xFFF5F5F5.toInt(), hintText = 0xFF8C8C8C.toInt(),
            accent = 0xFF4E9BFF.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x1FFFFFFF,
            stripText = 0xFFD6D6D6.toInt(), popup = 0xFF3A3A3A.toInt(),
        ),
    ),
    IPHONE(
        "iphone", "iPhone", radiusDp = 5.5f, gapHDp = 3f, gapVDp = 6f, rowDp = 54f,
        padSideDp = 3f, padTopDp = 6f, padBottomDp = 8f, keyShadow = true,
        enter = EnterStyle.IOS_TEXT, bottomRow = BottomRow.IOS, spaceLabel = "space",
        symbolsLabel = "123", moreSymbolsLabel = "#+=", alphaLabel = "ABC",
        lightCoat = KeyboardTheme(
            id = "ios_light", name = "iPhone light", isDark = false,
            background = 0xFFD1D3D9.toInt(), key = 0xFFFFFFFF.toInt(), functionKey = 0xFFABB0BA.toInt(),
            keyPressed = 0xFFABB0BA.toInt(), keyText = 0xFF000000.toInt(), hintText = 0xFF8A8A8E.toInt(),
            accent = 0xFF007AFF.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0xFF898A8D.toInt(),
            stripText = 0xFF2C2C2E.toInt(), popup = 0xFFFFFFFF.toInt(),
        ),
        darkCoat = KeyboardTheme(
            id = "ios_dark", name = "iPhone dark", isDark = true,
            background = 0xFF2B2B2D.toInt(), key = 0xFF6B6B6D.toInt(), functionKey = 0xFF464648.toInt(),
            keyPressed = 0xFF464648.toInt(), keyText = 0xFFFFFFFF.toInt(), hintText = 0xFFAEAEB2.toInt(),
            accent = 0xFF0A84FF.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0xFF1C1C1E.toInt(),
            stripText = 0xFFE5E5EA.toInt(), popup = 0xFF6B6B6D.toInt(),
        ),
    ),
    GOOGLE(
        "google", "Google", radiusDp = 7f, gapHDp = 3f, gapVDp = 5f, rowDp = 54f,
        padSideDp = 4f, padTopDp = 6f, padBottomDp = 8f, keyShadow = false,
        enter = EnterStyle.PILL, bottomRow = BottomRow.GBOARD, spaceLabel = "English",
        symbolsLabel = "?123", moreSymbolsLabel = "=\\<", alphaLabel = "ABC",
        lightCoat = KeyboardTheme(
            id = "gboard_light", name = "Google light", isDark = false,
            background = 0xFFE9EEF6.toInt(), key = 0xFFFFFFFF.toInt(), functionKey = 0xFFD3DCEC.toInt(),
            keyPressed = 0xFFC3CEE2.toInt(), keyText = 0xFF1F1F1F.toInt(), hintText = 0xFF747775.toInt(),
            accent = 0xFF0B57D0.toInt(), onAccent = 0xFFFFFFFF.toInt(), border = 0x141F1F1F,
            stripText = 0xFF444746.toInt(), popup = 0xFFFFFFFF.toInt(),
        ),
        darkCoat = KeyboardTheme(
            id = "gboard_dark", name = "Google dark", isDark = true,
            background = 0xFF1B1C1F.toInt(), key = 0xFF35373B.toInt(), functionKey = 0xFF2A2C30.toInt(),
            keyPressed = 0xFF4A4C51.toInt(), keyText = 0xFFE3E3E3.toInt(), hintText = 0xFF9AA0A6.toInt(),
            accent = 0xFFA8C7FA.toInt(), onAccent = 0xFF062E6F.toInt(), border = 0x1FFFFFFF,
            stripText = 0xFFC4C7C5.toInt(), popup = 0xFF3C3E42.toInt(),
        ),
    );

    enum class EnterStyle { ACCENT, FUNCTION, IOS_TEXT, PILL }
    enum class BottomRow { STANDARD, IOS, GBOARD }

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: PAWKEYS
    }
}
