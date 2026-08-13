package com.example.vivaldiplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Local settings. Preferences remain on this Android device. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.settings_intro)
            textSize = 16f
            setPadding(0, 0, 0, dp(14))
        })

        content.addView(settingSwitch(
            R.string.setting_clear_age_prompts,
            AppSettings.clearAgePrompts(this)
        ) { AppSettings.setClearAgePrompts(this, it) })

        content.addView(settingSwitch(
            R.string.setting_clear_cookie_prompts,
            AppSettings.clearCookiePrompts(this)
        ) { AppSettings.setClearCookiePrompts(this, it) })

        content.addView(settingSwitch(
            R.string.setting_network_retry,
            AppSettings.networkRetryEnabled(this)
        ) { AppSettings.setNetworkRetryEnabled(this, it) })

        content.addView(settingSwitch(
            R.string.setting_preload_next_tab,
            AppSettings.preloadNextTab(this)
        ) { AppSettings.setPreloadNextTab(this, it) })

        content.addView(TextView(this).apply {
            text = getString(R.string.setting_persistent_tabs_info)
            setPadding(0, dp(12), 0, dp(12))
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.clear_saved_tabs)
            setOnClickListener { confirmClearTabs() }
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.about)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, AboutActivity::class.java))
            }
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun settingSwitch(labelRes: Int, initial: Boolean, onChanged: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            text = getString(labelRes)
            isChecked = initial
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }

    private fun confirmClearTabs() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_saved_tabs)
            .setMessage(R.string.clear_saved_tabs_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                VideoTabStore.clearAll()
                Toast.makeText(this, R.string.saved_tabs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** App/version/build details used to identify exactly which APK is installed. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.about)

        val textView = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(dp(20), dp(24), dp(20), dp(24))
            text = getString(
                R.string.about_build_details,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.GIT_COMMIT,
                BuildConfig.BUILD_RUN
            )
        }

        setContentView(ScrollView(this).apply { addView(textView) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
