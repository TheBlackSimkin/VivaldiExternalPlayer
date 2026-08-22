package com.example.vivaldiplayer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/**
 * Manually activated privacy curtain for the main dashboard.
 *
 * The app starts normally unlocked. Once the user explicitly hides it, the state
 * persists across process recreation so the real dashboard cannot flash on screen.
 * The curtain intentionally looks like a neutral ExternalPlayer landing surface:
 * it must not advertise that tabs, history, or private content exist underneath.
 *
 * This is a UI/privacy gate, not encrypted storage for ordinary tabs.
 */
object AppPrivacyController {
    private const val PREFS = "app_privacy"
    private const val KEY_LOCKED = "dashboard_locked"
    private const val OVERLAY_TAG = "vep_privacy_shield"

    fun isLocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCKED, false)

    /**
     * [onRevealed] is used by MainActivity to consume a share that arrived while
     * hidden. Revealing happens in-place; we deliberately do not restart/finish
     * the Activity because doing so caused some devices to minimize the app after
     * successful system authentication.
     */
    fun attachIfNeeded(
        activity: AppCompatActivity,
        onRevealed: (() -> Unit)? = null
    ) {
        if (isLocked(activity)) showCurtain(activity, onRevealed)
    }

    fun lock(activity: AppCompatActivity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCKED, true)
            .apply()
        showCurtain(activity, null)
    }

    private fun reveal(
        activity: AppCompatActivity,
        onRevealed: (() -> Unit)?
    ) {
        SystemAuthGate.authenticate(
            activity = activity,
            title = activity.getString(R.string.privacy_auth_title),
            subtitle = activity.getString(R.string.privacy_auth_prompt),
            onSuccess = {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LOCKED, false)
                    .apply()
                removeCurtain(activity)
                onRevealed?.invoke()
            }
        )
    }

    private fun showCurtain(
        activity: AppCompatActivity,
        onRevealed: (() -> Unit)?
    ) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (root.findViewWithTag<View>(OVERLAY_TAG) != null) return

        val overlay = FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            setBackgroundColor(color(activity, R.color.app_background))
            isClickable = true
            isFocusable = true
            elevation = dp(activity, 40).toFloat()
        }

        val card = MaterialCardView(activity).apply {
            radius = dp(activity, 24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(activity, 1)
            strokeColor = color(activity, R.color.app_outline)
            setCardBackgroundColor(color(activity, R.color.app_surface))
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(activity, 24), dp(activity, 28), dp(activity, 24), dp(activity, 24))
        }

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.privacy_neutral_title)
            textSize = 23f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(color(activity, R.color.app_text_primary))
        })

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.privacy_neutral_summary)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(activity, R.color.app_text_secondary))
            setPadding(0, dp(activity, 10), 0, dp(activity, 22))
        })

        content.addView(Button(activity).apply {
            isAllCaps = false
            text = activity.getString(R.string.privacy_neutral_open)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(activity, R.color.white))
            backgroundTintList = ColorStateList.valueOf(color(activity, R.color.app_accent))
            setOnClickListener { reveal(activity, onRevealed) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)))

        card.addView(content)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dp(activity, 28)
                marginEnd = dp(activity, 28)
            }
        )

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun removeCurtain(activity: AppCompatActivity) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        root.findViewWithTag<View>(OVERLAY_TAG)?.let(root::removeView)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun color(context: Context, resId: Int): Int = ContextCompat.getColor(context, resId)
    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
