package com.pawkeys.keyboard

import android.graphics.RectF

enum class KeyType { CHAR, FUNCTION, ACTION, SPACE }

/**
 * A single key. [code] is the character code for printable keys, or one of the
 * negative codes in [Codes] for special keys.
 */
class Key(
    val code: Int,
    val label: String,
    val hint: String? = null,
    popup: String = "",
    val width: Float = 1f,
    val type: KeyType = KeyType.CHAR,
    val icon: Int = 0,
    val output: String? = null,
) {
    /** Long-press alternatives; the hint (if any) always comes first. */
    val popupChars: List<String> = buildList {
        hint?.let { add(it) }
        popup.split(' ').filter { it.isNotEmpty() && it != hint }.forEach { add(it) }
    }

    /** Bounds in view pixels, assigned by [KeyboardView] during layout. */
    val bounds = RectF()

    val isLetter: Boolean get() = code > 0 && Character.isLetter(code)
    val text: String get() = output ?: if (code > 0) String(Character.toChars(code)) else ""
}

object Codes {
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val ALPHA = -3
    const val SYMBOLS_MORE = -4
    const val DELETE = -5
    const val EMOJI = -6
    const val ENTER = 10
    const val SPACE = 32
}

enum class LayoutId { QWERTY, SYMBOLS, SYMBOLS_MORE, NUMPAD, PHONE }

class KeyboardLayout(val id: LayoutId, val rows: List<List<Key>>)

/** Input variants that slightly change the bottom row (like Samsung does). */
enum class FieldVariant { TEXT, EMAIL, URL }

object Layouts {
    private val accents = mapOf(
        'a' to "à á â ä æ ã å ā", 'c' to "ç ć č", 'e' to "è é ê ë ē ė ę",
        'g' to "ğ", 'i' to "î ï í ī į ì", 'l' to "ł", 'n' to "ñ ń",
        'o' to "ô ö ò ó œ ø ō õ", 's' to "ß ś š", 'u' to "û ü ù ú ū",
        'y' to "ÿ ý", 'z' to "ž ź ż",
    )

    // Samsung-style hint characters shown on the letter keys.
    private val row1Hints = "1234567890"
    private val row2Hints = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
    private val row3Hints = listOf("*", "\"", "'", ":", ";", "!", "?")

    private fun letter(c: Char, hint: String?) =
        Key(c.code, c.toString(), hint = hint, popup = accents[c] ?: "")

    fun build(id: LayoutId, numberRow: Boolean, variant: FieldVariant): KeyboardLayout = when (id) {
        LayoutId.QWERTY -> qwerty(numberRow, variant)
        LayoutId.SYMBOLS -> symbols(variant)
        LayoutId.SYMBOLS_MORE -> symbolsMore(variant)
        LayoutId.NUMPAD -> numpad(phone = false)
        LayoutId.PHONE -> numpad(phone = true)
    }

    private fun qwerty(numberRow: Boolean, variant: FieldVariant): KeyboardLayout {
        val rows = mutableListOf<List<Key>>()
        if (numberRow) rows.add(numberRowKeys())
        rows.add("qwertyuiop".mapIndexed { i, c -> letter(c, if (numberRow) null else row1Hints[i].toString()) })
        rows.add("asdfghjkl".mapIndexed { i, c -> letter(c, row2Hints[i]) })
        rows.add(buildList {
            add(Key(Codes.SHIFT, "", width = 1.5f, type = KeyType.FUNCTION, icon = R.drawable.ic_shift))
            "zxcvbnm".forEachIndexed { i, c -> add(letter(c, row3Hints[i])) }
            add(Key(Codes.DELETE, "", width = 1.5f, type = KeyType.FUNCTION, icon = R.drawable.ic_backspace))
        })
        rows.add(bottomRow("!#1", Codes.SYMBOLS, variant))
        return KeyboardLayout(LayoutId.QWERTY, rows)
    }

    private fun numberRowKeys() = "1234567890".map { c ->
        Key(c.code, c.toString(), popup = when (c) {
            '1' -> "¹ ½ ⅓ ¼"; '2' -> "² ⅔"; '3' -> "³ ¾"; '0' -> "ⁿ ∅"; else -> ""
        })
    }

    private fun bottomRow(switchLabel: String, switchCode: Int, variant: FieldVariant): List<Key> {
        val (secondary, secondaryPopup) = when (variant) {
            FieldVariant.EMAIL -> "@" to ", _ -"
            FieldVariant.URL -> "/" to ", : _ -"
            FieldVariant.TEXT -> "," to ""
        }
        val dotPopup = when (variant) {
            FieldVariant.TEXT -> "? ! ' \" - : ; / @ # …"
            else -> ".com .net .org .io - _ : /"
        }
        return listOf(
            Key(switchCode, switchLabel, width = 1.5f, type = KeyType.FUNCTION),
            Key(secondary.codePointAt(0), secondary, popup = secondaryPopup, hint = if (variant == FieldVariant.TEXT) "☺" else null),
            Key(Codes.SPACE, "", width = 5f, type = KeyType.SPACE),
            Key('.'.code, ".", popup = dotPopup),
            Key(Codes.ENTER, "", width = 1.5f, type = KeyType.ACTION, icon = R.drawable.ic_keyboard_return),
        )
    }

    private fun chars(s: String, popups: Map<Char, String> = emptyMap()) =
        s.split(' ').filter { it.isNotEmpty() }.map { Key(it.codePointAt(0), it, popup = popups[it[0]] ?: "") }

    private fun symbols(variant: FieldVariant): KeyboardLayout {
        val rows = listOf(
            chars("1 2 3 4 5 6 7 8 9 0"),
            chars("+ × ÷ = / _ < > [ ]", mapOf('=' to "≠ ≈ ±", '<' to "≤ « ‹", '>' to "≥ » ›")),
            chars("! @ # $ % ^ & * ( )", mapOf('$' to "€ £ ¥ ₹ ₩ ¢", '%' to "‰", '*' to "★ †", '!' to "¡")),
            buildList {
                add(Key(Codes.SYMBOLS_MORE, "1/2", width = 1.5f, type = KeyType.FUNCTION))
                addAll(chars("- ' \" : ; , ?", mapOf('-' to "– — ·", '\'' to "‘ ’ ‚", '"' to "“ ” „", '?' to "¿")))
                add(Key(Codes.DELETE, "", width = 1.5f, type = KeyType.FUNCTION, icon = R.drawable.ic_backspace))
            },
            bottomRow("ABC", Codes.ALPHA, variant),
        )
        return KeyboardLayout(LayoutId.SYMBOLS, rows)
    }

    private fun symbolsMore(variant: FieldVariant): KeyboardLayout {
        val rows = listOf(
            chars("` ~ \\ | { } € £ ¥ ₩"),
            chars("° • ○ ● □ ■ ♤ ♡ ◇ ♧"),
            chars("☆ ▪ ¤ 《 》 ¡ ¿ ※ § ¶"),
            buildList {
                add(Key(Codes.SYMBOLS, "2/2", width = 1.5f, type = KeyType.FUNCTION))
                addAll(chars("© ® ™ ✓ … ¢ 🐾"))
                add(Key(Codes.DELETE, "", width = 1.5f, type = KeyType.FUNCTION, icon = R.drawable.ic_backspace))
            },
            bottomRow("ABC", Codes.ALPHA, variant),
        )
        return KeyboardLayout(LayoutId.SYMBOLS_MORE, rows)
    }

    private fun numpad(phone: Boolean): KeyboardLayout {
        fun n(c: Char, popup: String = "", hint: String? = null) =
            Key(c.code, c.toString(), popup = popup, hint = hint, width = 1.5f)
        val rows = listOf(
            listOf(n('1'), n('2', hint = if (phone) "ABC" else null), n('3', hint = if (phone) "DEF" else null),
                Key('-'.code, "-", width = 1f, type = KeyType.FUNCTION, popup = "+ ( ) /")),
            listOf(n('4'), n('5'), n('6'), Key(Codes.SPACE, "", width = 1f, type = KeyType.FUNCTION, icon = R.drawable.ic_space_bar)),
            listOf(n('7'), n('8'), n('9'), Key(Codes.DELETE, "", width = 1f, type = KeyType.FUNCTION, icon = R.drawable.ic_backspace)),
            listOf(
                if (phone) n('*', popup = "# + ( ) ,") else n(',', popup = "+ - /"),
                n('0', popup = "+"),
                if (phone) n('#', popup = "* + ;") else n('.', popup = ": ;"),
                Key(Codes.ENTER, "", width = 1f, type = KeyType.ACTION, icon = R.drawable.ic_keyboard_return),
            ),
        )
        return KeyboardLayout(if (phone) LayoutId.PHONE else LayoutId.NUMPAD, rows)
    }
}
