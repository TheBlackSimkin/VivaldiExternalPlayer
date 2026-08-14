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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
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

        /*
         * Explicit per-app language selector requested during device QA.
         *
         * AppCompatDelegate is deliberately used instead of a hand-rolled
         * Resources override. AndroidX synchronizes the selected app locale with
         * Android 13+ and, with the manifest metadata added in this build, stores
         * it compatibly on older supported Android versions too.
         */
        content.addView(TextView(this).apply {
            text = getString(R.string.app_language)
            textSize = 14f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        content.addView(Button(this).apply {
            isAllCaps = false
            text = languageButtonText()
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener { showLanguageDialog() }
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
        content.addView(settingsCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        /*
         * Be precise about persistence: OPEN tabs restore automatically. Closed
         * tabs are not secretly hiding in the dashboard; this build adds a real
         * small Recently closed archive so the user can intentionally restore one.
         */
        content.addView(TextView(this).apply {
            text = getString(R.string.setting_persistent_tabs_info)
            setTextColor(color(R.color.app_text_secondary))
            textSize = 13f
            setPadding(dp(4), dp(16), dp(4), dp(10))
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = recentlyClosedButtonText()
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener { showRecentlyClosedDialog() }
        })

        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.clear_all_tabs)
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_accent_soft))
            setOnClickListener { confirmClearTabs() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        /*
         * Development/QA helper requested after build #202: export the ordered
         * lifecycle journal as ordinary text through Android's share sheet.
         * The log deliberately contains technical state only — no thumbnails,
         * page body, media frames, cookies, request headers or credentials.
         */
        content.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.share_operations_log)
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener { OperationLog.share(this@SettingsActivity) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
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

    private fun languageButtonText(): String =
        getString(R.string.app_language_value, getString(languageLabelRes(currentLanguageIndex())))

    private fun currentLanguageIndex(): Int {
        val locale = AppCompatDelegate.getApplicationLocales().get(0)
        return when (locale?.language) {
            "en" -> 1
            "es" -> 2
            else -> 0
        }
    }

    private fun languageLabelRes(index: Int): Int = when (index) {
        1 -> R.string.language_english
        2 -> R.string.language_spanish
        else -> R.string.language_system_default
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(R.string.language_system_default),
            getString(R.string.language_english),
            getString(R.string.language_spanish)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.app_language)
            .setSingleChoiceItems(labels, currentLanguageIndex()) { dialog, which ->
                val locales = when (which) {
                    1 -> LocaleListCompat.forLanguageTags("en")
                    2 -> LocaleListCompat.forLanguageTags("es")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }

                dialog.dismiss()

                /*
                 * This normally recreates AppCompat Activities so every screen
                 * resolves strings from the newly selected app locale.
                 */
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun recentlyClosedButtonText(): String =
        getString(R.string.recently_closed_tabs_count, VideoTabStore.recentlyClosedTabs().size)

    private fun showRecentlyClosedDialog() {
        val closed = VideoTabStore.recentlyClosedTabs()
        if (closed.isEmpty()) {
            Toast.makeText(this, R.string.no_recently_closed_tabs, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = closed.map { tab ->
            tab.title.trim().ifBlank { getString(R.string.home_brand) }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.restore_recently_closed_tab)
            .setItems(labels) { _, which ->
                val restored = VideoTabStore.restoreClosed(closed[which].id)
                if (restored != null) {
                    Toast.makeText(this, R.string.tab_restored, Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            .setNeutralButton(R.string.clear_recently_closed) { _, _ ->
                VideoTabStore.clearRecentlyClosed()
                Toast.makeText(this, R.string.recently_closed_cleared, Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearTabs() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_tabs)
            .setMessage(R.string.clear_all_tabs_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                VideoTabStore.clearAll()
                TabThumbnailCache.clear(this)
                Toast.makeText(this, R.string.all_tabs_cleared, Toast.LENGTH_SHORT).show()
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
