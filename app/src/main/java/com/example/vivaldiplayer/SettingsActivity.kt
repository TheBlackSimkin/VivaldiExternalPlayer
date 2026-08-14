package com.example.vivaldiplayer

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/** Local settings. Preferences remain on this Android device. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(32))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.settings)
            textSize = 28f
            setTextColor(color(R.color.app_text_primary))
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_intro)
            textSize = 14f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(0, dp(6), 0, dp(18))
        })

        val settingsCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.app_outline)
            setCardBackgroundColor(color(R.color.app_surface))
        }
        val switches = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        switches.addView(settingSwitch(R.string.setting_clear_age_prompts, AppSettings.clearAgePrompts(this)) { AppSettings.setClearAgePrompts(this, it) })
        switches.addView(settingSwitch(R.string.setting_clear_cookie_prompts, AppSettings.clearCookiePrompts(this)) { AppSettings.setClearCookiePrompts(this, it) })
        switches.addView(settingSwitch(R.string.setting_network_retry, AppSettings.networkRetryEnabled(this)) { AppSettings.setNetworkRetryEnabled(this, it) })
        switches.addView(settingSwitch(R.string.setting_preload_next_tab, AppSettings.preloadNextTab(this)) { AppSettings.setPreloadNextTab(this, it) })
        settingsCard.addView(switches)
        content.addView(settingsCard)

        content.addView(TextView(this).apply {
            text = getString(R.string.setting_persistent_tabs_info)
            setTextColor(color(R.color.app_text_secondary))
            textSize = 13f
            setPadding(dp(4), dp(16), dp(4), dp(14))
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.clear_saved_tabs)
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_accent_soft))
            setOnClickListener { confirmClearTabs() }
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.about)
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background))
            addView(content)
        })
    }

    private fun settingSwitch(labelRes: Int, initial: Boolean, onChanged: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            text = getString(labelRes)
            isChecked = initial
            textSize = 14f
            setTextColor(color(R.color.app_text_primary))
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }

    private fun confirmClearTabs() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_saved_tabs)
            .setMessage(R.string.clear_saved_tabs_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                VideoTabStore.clearAll()
                TabThumbnailCache.clear(this)
                Toast.makeText(this, R.string.saved_tabs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** App/version/build details used to identify exactly which APK is installed. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.about)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(32))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.home_brand)
            textSize = 28f
            setTextColor(color(R.color.app_text_primary))
            setTypeface(typeface, Typeface.BOLD)
        })

        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.app_outline)
            setCardBackgroundColor(color(R.color.app_surface))
        }
        card.addView(TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            setTextColor(color(R.color.app_text_primary))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            text = getString(
                R.string.about_build_details,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.GIT_COMMIT,
                BuildConfig.BUILD_RUN
            )
        })
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background))
            addView(content)
        })
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
