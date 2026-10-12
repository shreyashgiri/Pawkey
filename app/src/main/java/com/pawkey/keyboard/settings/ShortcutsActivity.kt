package com.pawkey.keyboard.settings

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pawkey.keyboard.ShortcutStore

/** Add, edit and delete Samsung-style text shortcuts. */
class ShortcutsActivity : AppCompatActivity() {
    private lateinit var store: ShortcutStore
    private lateinit var list: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShortcutStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MaterialToolbar(this).apply {
            title = "Text shortcuts"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(MaterialButton(this).apply {
            text = "Add shortcut"
            setOnClickListener { edit(null, null) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val all = store.all()
        if (all.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No shortcuts yet"
                gravity = Gravity.CENTER
                setPadding(0, (48 * dp).toInt(), 0, 0)
            })
        }
        all.forEach { (short, expansion) ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * dp).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { edit(short, expansion) }
                addView(TextView(context).apply { text = short; textSize = 16f })
                addView(TextView(context).apply { text = expansion; alpha = 0.7f })
            })
        }
    }

    private fun edit(shortcut: String?, expansion: String?) {
        val pad = (20 * dp).toInt()
        fun field(hint: String, value: String?) = TextInputLayout(this).apply {
            this.hint = hint
            addView(TextInputEditText(context).apply {
                setText(value)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            })
        }
        val shortField = field("Shortcut (e.g. omw)", shortcut)
        val expField = field("Expanded phrase", expansion)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(shortField)
            addView(expField)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (shortcut == null) "Add shortcut" else "Edit shortcut")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val s = shortField.editText?.text?.toString()?.trim().orEmpty()
                val e = expField.editText?.text?.toString().orEmpty()
                if (s.isNotEmpty() && e.isNotEmpty() && !s.contains(' ')) {
                    if (shortcut != null && shortcut != s.lowercase()) store.remove(shortcut)
                    store.put(s, e)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (shortcut != null) dialog.setNeutralButton("Delete") { _, _ -> store.remove(shortcut); refresh() }
        dialog.show()
    }
}
