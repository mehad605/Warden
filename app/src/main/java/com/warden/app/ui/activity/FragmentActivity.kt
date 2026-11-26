package com.warden.app.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.warden.app.R
import com.warden.app.feature.blocker.ui.KeywordBlockerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single launcher activity hosting the first-launch disclaimer gate and [KeywordBlockerFragment].
 *
 * Component FQCN (`com.warden.app.ui.activity.FragmentActivity`) is preserved verbatim so
 * existing home-screen shortcuts survive upgrades from `baseline-v1`.
 */
class FragmentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BlockWords)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        applySystemBarInsets()

        val shouldMountFresh = savedInstanceState == null
        lifecycleScope.launch {
            val agreed =
                withContext(Dispatchers.IO) {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_AGREED_TO_TERMS, false)
                }
            if (isFinishing || isDestroyed) return@launch
            if (!agreed) {
                showDisclaimerDialog(shouldMountFresh)
            } else if (shouldMountFresh) {
                mountMainFragment()
            }
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_holder)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }

    private fun showDisclaimerDialog(shouldMountFresh: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(R.string.disclaimer_message)
            .setPositiveButton(R.string.disclaimer_agree) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_AGREED_TO_TERMS, true)
                        .apply()
                }
                if (shouldMountFresh) mountMainFragment()
            }
            .setNegativeButton(R.string.disclaimer_exit) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun mountMainFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, KeywordBlockerFragment())
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_AGREED_TO_TERMS = "agreed_to_terms"
    }
}
