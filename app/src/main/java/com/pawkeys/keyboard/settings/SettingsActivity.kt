package com.pawkeys.keyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pawkeys.keyboard.R
import com.pawkeys.keyboard.engine.UserDictionary

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(MaterialToolbar(this).apply {
            title = "PawKeys"
            subtitle = "Settings"
        })
        root.addView(TextInputLayout(this).apply {
            hint = "Try PawKeys here"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(TextInputEditText(context))
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        })
        val container = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(container.id, SettingsFragment()).commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            click("setup_enable") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            click("setup_select") {
                requireContext().getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
            click("open_shortcuts") { startActivity(Intent(requireContext(), ShortcutsActivity::class.java)) }
            click("clear_learned") {
                AlertDialog.Builder(requireContext())
                    .setTitle("Clear memory?")
                    .setMessage("PawKeys will forget the words and phrases it learned from your typing.")
                    .setPositiveButton("Clear") { _, _ ->
                        UserDictionary(requireContext().filesDir).clear()
                        // Tell a running keyboard to drop its in-memory copy too.
                        preferenceManager.sharedPreferences?.edit()?.putLong(KEY_LEARNED_RESET, System.currentTimeMillis())?.apply()
                        Toast.makeText(requireContext(), "Memory cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        override fun onResume() {
            super.onResume()
            val ctx = requireContext()
            val imm = ctx.getSystemService(InputMethodManager::class.java)
            val enabled = imm?.enabledInputMethodList?.any { it.packageName == ctx.packageName } == true
            val current = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val selected = current?.startsWith(ctx.packageName + "/") == true
            findPreference<Preference>("setup_enable")?.summary =
                if (enabled) "✓ Enabled" else "Tap to turn on PawKeys in your phone's keyboard list"
            findPreference<Preference>("setup_select")?.summary =
                if (selected) "✓ PawKeys is your keyboard" else "Tap to pick PawKeys as the current keyboard"
            findPreference<Preference>("setup")?.isVisible = !(enabled && selected)
        }

        private fun click(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }
    }

    companion object {
        const val KEY_LEARNED_RESET = "learned_reset"
    }
}
