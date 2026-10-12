package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R

/** Groom: cursor pad, select mode, copy / cut / paste, undo / redo. */
@SuppressLint("ViewConstructor")
class TextEditPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val actions: Actions,
) : LinearLayout(context) {

    interface Actions {
        /** dx/dy are -1, 0 or 1. [select] extends the selection instead of moving. */
        fun moveCursor(dx: Int, dy: Int, select: Boolean)
        fun home(select: Boolean)
        fun end(select: Boolean)
        /** Select mode toggled: the IME remembers where the selection is anchored. */
        fun selectionMode(on: Boolean)
        fun selectAll()
        fun copy()
        fun cut()
        fun paste()
        fun undo()
        fun redo()
        fun delete()
        fun back()
    }

    private var selecting = false
    private lateinit var selectButton: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 3f)
        setPadding(pad, pad, pad, pad)

        fun row(vararg views: Pair<View, Float>) = LinearLayout(context).apply {
            orientation = HORIZONTAL
            views.forEach { (v, w) -> addView(v, UiKit.weighted(w, 4f, context)) }
            this@TextEditPanel.addView(this, UiKit.weighted(1f, 0f, context, vertical = true))
        }
        fun icon(res: Int, desc: String, repeat: Boolean = false, background: Int = theme.key, action: () -> Unit) =
            UiKit.iconButton(context, theme, res, desc, background = background) { action() }.also {
                if (repeat) UiKit.repeatOnHold(it, action)
            }
        fun text(label: String, action: () -> Unit) = UiKit.textButton(context, theme, label, bold = false) { action() }

        selectButton = UiKit.textButton(context, theme, "Select", background = theme.key) { toggleSelect() }

        row(
            icon(R.drawable.ic_first_page, "Home") { actions.home(selecting) } to 1f,
            icon(R.drawable.ic_keyboard_arrow_up, "Up", repeat = true) { actions.moveCursor(0, -1, selecting) } to 1f,
            icon(R.drawable.ic_last_page, "End") { actions.end(selecting) } to 1f,
            text("Select all") { actions.selectAll() } to 1.3f,
            icon(R.drawable.ic_undo, "Undo", background = theme.functionKey) { actions.undo() } to 1f,
        )
        row(
            icon(R.drawable.ic_keyboard_arrow_left, "Left", repeat = true) { actions.moveCursor(-1, 0, selecting) } to 1f,
            selectButton to 1f,
            icon(R.drawable.ic_keyboard_arrow_right, "Right", repeat = true) { actions.moveCursor(1, 0, selecting) } to 1f,
            text("Copy") { actions.copy(); setSelecting(false) } to 1.3f,
            icon(R.drawable.ic_redo, "Redo", background = theme.functionKey) { actions.redo() } to 1f,
        )
        row(
            View(context) to 1f,
            icon(R.drawable.ic_keyboard_arrow_down, "Down", repeat = true) { actions.moveCursor(0, 1, selecting) } to 1f,
            View(context) to 1f,
            text("Cut") { actions.cut(); setSelecting(false) } to 1.3f,
            text("Paste") { actions.paste() } to 1f,
        )
        row(
            UiKit.textButton(context, theme, "ABC") { actions.back() } to 3f,
            icon(R.drawable.ic_backspace, "Delete", repeat = true, background = theme.functionKey) { actions.delete() } to 2f,
        )
    }

    private fun toggleSelect() = setSelecting(!selecting)

    private fun setSelecting(on: Boolean) {
        if (selecting != on) actions.selectionMode(on)
        selecting = on
        selectButton.background = UiKit.keyBackground(context, theme, if (on) theme.accent else theme.key)
        selectButton.setTextColor(if (on) theme.onAccent else theme.keyText)
    }
}
