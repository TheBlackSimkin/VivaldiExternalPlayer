package com.example.vivaldiplayer

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Local settings grouped into clear visual sections rather than a stack of
 * unrelated Android buttons. Preferences remain on this Android device.
 */
class SettingsActivity : AppCompatActivity() {

    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }

        content.addView(topBar(getString(R.string.settings)))
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_intro)
            textSize = 13f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(dp(4), dp(8), dp(4), dp(22))
        })

        content.addView(sectionTitle(R.string.settings_section_general))
        content.addView(sectionCard(
            actionRow(
                title = getString(R.string.app_language),
                value = getString(languageLabelRes(currentLanguageIndex())),
                onClick = ::showLanguageDialog
            )
        ))

        content.addView(sectionTitle(R.string.settings_section_browser))
        content.addView(sectionCard(
            switchRow(
                R.string.setting_clear_age_prompts,
                AppSettings.clearAgePrompts(this)
            ) { AppSettings.setClearAgePrompts(this, it) },
            divider(),
            switchRow(
                R.string.setting_clear_cookie_prompts,
                AppSettings.clearCookiePrompts(this)
            ) { AppSettings.setClearCookiePrompts(this, it) },
            divider(),
            switchRow(
                R.string.setting_network_retry,
                AppSettings.networkRetryEnabled(this)
            ) { AppSettings.setNetworkRetryEnabled(this, it) },
            divider(),
            switchRow(
                R.string.setting_preload_next_tab,
                AppSettings.preloadNextTab(this)
            ) { AppSettings.setPreloadNextTab(this, it) }
        ))

        content.addView(sectionTitle(R.string.settings_section_tabs))
        content.addView(sectionCard(
            actionRow(
                title = getString(R.string.recently_closed_title),
                value = VideoTabStore.recentlyClosedTabs().size.toString(),
                subtitle = getString(R.string.settings_recently_closed_summary),
                onClick = {
                    startActivity(Intent(this, RecentlyClosedActivity::class.java))
                }
            ),
            divider(),
            actionRow(
                title = getString(R.string.clear_all_tabs),
                subtitle = getString(R.string.settings_clear_tabs_summary),
                destructive = true,
                onClick = ::confirmClearTabs
            )
        ))

        content.addView(sectionTitle(R.string.settings_section_support))
        content.addView(sectionCard(
            actionRow(
                title = getString(R.string.share_operations_log),
                subtitle = getString(R.string.settings_diagnostics_summary),
                onClick = { OperationLog.share(this@SettingsActivity) }
            ),
            divider(),
            actionRow(
                title = getString(R.string.about),
                subtitle = getString(R.string.settings_about_summary),
                onClick = { startActivity(Intent(this, AboutActivity::class.java)) }
            )
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background))
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        /* Refresh the Recently Closed count after returning from its dedicated screen. */
        if (firstResume) {
            firstResume = false
        } else {
            recreate()
        }
    }

    private fun topBar(title: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_24)
            contentDescription = getString(R.string.back)
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.icon_button_background)
            imageTintList = ColorStateList.valueOf(color(R.color.app_text_primary))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        row.addView(TextView(this).apply {
            text = title
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.app_text_primary))
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        return row
    }

    private fun sectionTitle(resId: Int): TextView = TextView(this).apply {
        text = getString(resId)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(color(R.color.app_text_secondary))
        setPadding(dp(4), dp(20), dp(4), dp(8))
    }

    private fun sectionCard(vararg rows: View): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.app_outline)
            setCardBackgroundColor(color(R.color.app_surface))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { column.addView(it) }
        card.addView(column)
        return card
    }

    private fun actionRow(
        title: String,
        value: String = "",
        subtitle: String = "",
        destructive: Boolean = false,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(if (destructive) R.color.app_accent else R.color.app_text_primary))
        })
        if (subtitle.isNotBlank()) {
            textColumn.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(color(R.color.app_text_secondary))
                setPadding(0, dp(4), dp(10), 0)
            })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (value.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(color(R.color.app_text_secondary))
                setPadding(dp(8), 0, dp(6), 0)
            })
        }
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(color(R.color.app_text_secondary))
        })

        return row
    }

    private fun switchRow(labelRes: Int, initial: Boolean, onChanged: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(10), dp(12))
        }
        row.addView(TextView(this).apply {
            text = getString(labelRes)
            textSize = 14f
            setTextColor(color(R.color.app_text_primary))
            setPadding(0, 0, dp(12), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SwitchMaterial(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        })
        return row
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(color(R.color.app_outline))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        }
    }

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
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearTabs() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_tabs)
            .setMessage(R.string.clear_all_tabs_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                val openIds = VideoTabStore.allTabs().map { it.id }
                VideoTabStore.clearAll()

                /* Recently Closed thumbnails are intentionally preserved. */
                openIds.forEach { TabThumbnailCache.delete(this, it) }
                Toast.makeText(this, R.string.all_tabs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** Polished About page; detailed build identifiers remain available for QA. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(32))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_24)
            contentDescription = getString(R.string.back)
            background = ContextCompat.getDrawable(this@AboutActivity, R.drawable.icon_button_background)
            imageTintList = ColorStateList.valueOf(color(R.color.app_text_primary))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        top.addView(TextView(this).apply {
            text = getString(R.string.home_brand)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.app_text_primary))
            setPadding(dp(14), 0, 0, 0)
        })
        content.addView(top)

        content.addView(TextView(this).apply {
            text = getString(R.string.about_summary)
            textSize = 14f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(dp(4), dp(12), dp(4), dp(18))
        })

        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(R.color.app_outline)
            setCardBackgroundColor(color(R.color.app_surface))
        }
        card.addView(TextView(this).apply {
            textSize = 14f
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
        content.addView(card)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background))
            addView(content)
        })
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
