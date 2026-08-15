package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.ColorStateList
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat

/**
 * UI-only player chrome coordinator.
 *
 * PlayerActivity's proven quality and diagnostics implementations remain untouched.
 * This provider simply hides their old top-corner buttons, exposes them through a
 * compact gear popup, and reshapes the existing tabs button into a square count.
 * It never creates playback objects or changes resolver/player state.
 */
class PlayerChromeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return true
        app.registerActivityLifecycleCallbacks(PlayerChromeLifecycle)
        return true
    }

    private object PlayerChromeLifecycle : Application.ActivityLifecycleCallbacks {
        private const val EXISTING_TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
        private const val GEAR_BUTTON_TAG = "vivaldi_external_player_gear_button"

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is PlayerActivity) {
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is PlayerActivity) {
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        private fun attach(activity: PlayerActivity) {
            if (activity.isFinishing || activity.isDestroyed) return
            val decor = activity.window.decorView as? ViewGroup ?: return

            val qualityButton = activity.findViewById<Button>(R.id.quality_button) ?: return
            val diagnosticsButton = activity.findViewById<Button>(R.id.diagnostics_button) ?: return

            /* Existing click listeners stay owned by PlayerActivity. */
            qualityButton.visibility = View.GONE
            diagnosticsButton.visibility = View.GONE

            val tabButton = decor.findViewWithTag<Button>(EXISTING_TAB_BUTTON_TAG)
            if (tabButton != null) {
                styleTabCountButton(activity, tabButton)
            }

            val existingGear = decor.findViewWithTag<View>(GEAR_BUTTON_TAG)
            if (existingGear != null) return

            val gear = AppCompatImageButton(activity).apply {
                tag = GEAR_BUTTON_TAG
                setImageResource(R.drawable.ic_settings_24)
                contentDescription = activity.getString(R.string.player_menu)
                imageTintList = ColorStateList.valueOf(color(activity, R.color.app_text_primary))
                background = ContextCompat.getDrawable(activity, R.drawable.icon_button_background)
                setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))
                setOnClickListener {
                    showPlayerMenu(activity, this, qualityButton, diagnosticsButton)
                }
            }

            activity.addContentView(
                gear,
                FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(activity, 12)
                    marginEnd = dp(activity, 12)
                }
            )
        }

        private fun styleTabCountButton(activity: PlayerActivity, button: Button) {
            button.text = VideoTabStore.allTabs().size.toString()
            button.contentDescription = activity.getString(
                R.string.open_saved_tabs_count,
                VideoTabStore.allTabs().size
            )
            button.textSize = 14f
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            button.minWidth = 0
            button.minimumWidth = 0
            button.minHeight = 0
            button.minimumHeight = 0
            button.setPadding(0, 0, 0, 0)
            button.setTextColor(color(activity, R.color.app_text_primary))
            button.background = ContextCompat.getDrawable(activity, R.drawable.icon_button_background)
            button.layoutParams = FrameLayout.LayoutParams(
                dp(activity, 44),
                dp(activity, 44),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(activity, 12)
                marginEnd = dp(activity, 64)
            }
        }

        private fun showPlayerMenu(
            activity: PlayerActivity,
            anchor: View,
            qualityButton: Button,
            diagnosticsButton: Button
        ) {
            PopupMenu(activity, anchor).apply {
                val quality = menu.add(0, 1, 0, activity.getString(R.string.video_quality))
                quality.isEnabled = qualityButton.isEnabled
                menu.add(0, 2, 1, activity.getString(R.string.player_diagnostics))

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> qualityButton.performClick()
                        2 -> diagnosticsButton.performClick()
                        else -> false
                    }
                }
                show()
            }
        }

        private fun color(activity: Activity, resId: Int): Int =
            ContextCompat.getColor(activity, resId)

        private fun dp(activity: Activity, value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
