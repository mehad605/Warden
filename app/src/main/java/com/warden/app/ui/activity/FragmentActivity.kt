package com.warden.app.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.warden.app.R
import com.warden.app.ui.fragments.main.reducers.blockertools.keywordBlocker.KeywordBlockerFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context

class FragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BlockWords)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.visibility = android.view.View.GONE

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("agreed_to_terms", false)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disclaimer & Warning")
                .setMessage("Warning: This app is strictly for those struggling with digital habits. Once you install and enable protection, you may not be able to uninstall it. You cannot just click uninstall, and if you grant Device Administrator privileges, even standard ADB methods may fail. There is a way to uninstall it, but it is intentionally hidden. Install and use this app at your own risk.\n\nThis app does not collect or transmit any of your personal data.\n\nBy continuing, you agree not to hold the developer accountable for any issues or inability to uninstall the app.")
                .setPositiveButton("I Agree") { _, _ ->
                    prefs.edit().putBoolean("agreed_to_terms", true).apply()
                    if (savedInstanceState == null) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_holder, KeywordBlockerFragment())
                            .commit()
                    }
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        } else {
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, KeywordBlockerFragment())
                    .commit()
            }
        }
    }
}