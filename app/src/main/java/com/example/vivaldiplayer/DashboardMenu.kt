package com.example.vivaldiplayer

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Consolidated dashboard menu. Global maintenance and app destinations live
 * here so the main surface can remain a clean tab browser.
 */
object DashboardMenu {
    data class Actions(
        val checkStatus: () -> Unit,
        val reviveExpired: () -> Unit,
        val closeAll: () -> Unit,
        val lockApp: () -> Unit
    )

    fun show(activity: AppCompatActivity, actions: Actions) {
        val dialog = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 28))
            setBackgroundColor(color(activity, R.color.app_surface))
        }

        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.dashboard_menu_title)
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(activity, R.color.app_text_primary))
            setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 12))
        })

        section(content, activity, R.string.dashboard_menu_tabs)
        row(content, activity, R.string.update_tab_status) {
            dialog.dismiss(); actions.checkStatus()
        }
        row(content, activity, R.string.revive_expired_tabs) {
            dialog.dismiss(); actions.reviveExpired()
        }
        row(content, activity, R.string.recently_closed_title) {
            dialog.dismiss()
            activity.startActivity(Intent(activity, RecentlyClosedActivity::class.java))
        }
        row(content, activity, R.string.close_all_tabs_main, destructive = true) {
            dialog.dismiss(); actions.closeAll()
        }

        section(content, activity, R.string.dashboard_menu_library)
        row(content, activity, R.string.favorites) {
            dialog.dismiss(); activity.startActivity(Intent(activity, FavoritesActivity::class.java))
        }
        row(content, activity, R.string.private_favorites) {
            dialog.dismiss(); activity.startActivity(Intent(activity, PrivateFavoritesActivity::class.java))
        }

        section(content, activity, R.string.dashboard_menu_privacy)
        row(content, activity, R.string.hide_and_lock_app) {
            dialog.dismiss(); actions.lockApp()
        }

        section(content, activity, R.string.dashboard_menu_app)
        row(content, activity, R.string.settings) {
            dialog.dismiss(); activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        row(content, activity, R.string.report_issue_github) {
            dialog.dismiss(); GitHubIssueReporter.showReview(activity)
        }
        row(content, activity, R.string.share_operations_log) {
            dialog.dismiss(); OperationLog.share(activity)
        }

        dialog.setContentView(content)
        dialog.show()
    }

    private fun section(parent: LinearLayout, context: Context, labelRes: Int) {
        parent.addView(TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(context, R.color.app_text_secondary))
            setPadding(dp(context, 4), dp(context, 15), dp(context, 4), dp(context, 5))
        })
    }

    private fun row(
        parent: LinearLayout,
        context: Context,
        labelRes: Int,
        destructive: Boolean = false,
        onClick: () -> Unit
    ) {
        parent.addView(TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(color(context, if (destructive) R.color.app_warning else R.color.app_text_primary))
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)))
    }

    private fun color(context: Context, resId: Int): Int = ContextCompat.getColor(context, resId)
    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
