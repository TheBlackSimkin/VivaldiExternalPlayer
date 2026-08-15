package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.annotation.OptIn
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * UI-only player chrome coordinator.
 *
 * PlayerActivity still owns the proven Quality and Diagnostics implementations,
 * and Media3 still owns playback, play/pause, the timeline, fullscreen and the
 * ended-state replay behavior. This provider only arranges presentation around
 * those existing pieces:
 *
 * - hide the legacy standalone Quality/Diagnostics buttons;
 * - keep Quality + Diagnostics behind one project gear menu;
 * - present the saved-tab count immediately to the left of that gear;
 * - place both controls in the same lower-right visual band as fullscreen;
 * - hide/show both controls with Media3's own controller visibility;
 * - hide Media3's visible rewind/fast-forward buttons because ±10 seconds is
 *   intentionally provided by GesturePlayerView's left/right double-tap gesture.
 *
 * Nothing here creates a Player, starts background playback, resolves a source,
 * changes track selection policy, or modifies the protected private-display BG
 * preparation architecture.
 */
@OptIn(UnstableApi::class)
class PlayerChromeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return true
        app.registerActivityLifecycleCallbacks(PlayerChromeLifecycle)
        return true
    }

    private object PlayerChromeLifecycle : Application.ActivityLifecycleCallbacks {
        private const val EXISTING_TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
        private const val GEAR_BUTTON_TAG = "vivaldi_external_player_gear_button"

        /*
         * Media3's standard fullscreen control occupies the far-right bottom slot.
         * Keep our two 44dp controls immediately to its left, matching the agreed
         * wireframe without replacing Media3's controller layout.
         */
        private const val CONTROL_SIZE_DP = 44
        private const val CONTROL_BOTTOM_MARGIN_DP = 2
        private const val FULLSCREEN_SLOT_DP = 48
        private const val CONTROL_GAP_DP = 4

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is PlayerActivity) {
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is PlayerActivity) {
                /*
                 * Re-attach on resume so tab counts and controller visibility are
                 * refreshed after returning from the dashboard or a popup/dialog.
                 */
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        private fun attach(activity: PlayerActivity) {
            if (activity.isFinishing || activity.isDestroyed) return

            val decor = activity.window.decorView as? ViewGroup ?: return
            val playerView = activity.findViewById<GesturePlayerView>(R.id.player_view) ?: return
            val qualityButton = activity.findViewById<Button>(R.id.quality_button) ?: return
            val diagnosticsButton = activity.findViewById<Button>(R.id.diagnostics_button) ?: return

            /* Existing click listeners and PlayerActivity behavior remain untouched. */
            qualityButton.visibility = View.GONE
            diagnosticsButton.visibility = View.GONE

            configureMedia3Controls(playerView)

            val tabButton = decor.findViewWithTag<Button>(EXISTING_TAB_BUTTON_TAG) ?: return
            styleTabCountButton(activity, tabButton)

            val gear =
                decor.findViewWithTag<AppCompatImageButton>(GEAR_BUTTON_TAG)
                    ?: createGearButton(
                        activity = activity,
                        qualityButton = qualityButton,
                        diagnosticsButton = diagnosticsButton,
                        playerView = playerView
                    ).also { button ->
                        activity.addContentView(button, gearLayoutParams(activity))
                    }

            /* Re-apply layout in case configuration/fullscreen changes replaced params. */
            tabButton.layoutParams = tabLayoutParams(activity)
            gear.layoutParams = gearLayoutParams(activity)

            bindToControllerVisibility(playerView, tabButton, gear)
        }

        /**
         * Keep the standard Media3 transport controller, but remove controls which
         * conflict with the agreed ExternalPlayer interaction model.
         *
         * Rewind/fast-forward remain fully functional via double-tap because
         * GesturePlayerView calls Player.seekBack()/seekForward() directly.
         */
        private fun configureMedia3Controls(playerView: GesturePlayerView) {
            hideControlAndWrapper(
                playerView,
                androidx.media3.ui.R.id.exo_rew_with_amount
            )
            hideControlAndWrapper(
                playerView,
                androidx.media3.ui.R.id.exo_ffwd_with_amount
            )

            /*
             * Media3 can expose its own settings gear. ExternalPlayer intentionally
             * has one gear whose menu is limited to Quality and Diagnostics, so the
             * library gear is hidden to avoid two adjacent settings affordances.
             */
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                ?.visibility = View.GONE
        }

        private fun hideControlAndWrapper(playerView: GesturePlayerView, controlId: Int) {
            val control = playerView.findViewById<View>(controlId) ?: return
            control.visibility = View.GONE

            /*
             * The amount-labelled seek controls sit inside a wrapping FrameLayout.
             * Hiding the wrapper as well prevents an invisible 48dp gap around the
             * center play/pause button.
             */
            (control.parent as? View)?.visibility = View.GONE
        }

        private fun createGearButton(
            activity: PlayerActivity,
            qualityButton: Button,
            diagnosticsButton: Button,
            playerView: GesturePlayerView
        ): AppCompatImageButton =
            AppCompatImageButton(activity).apply {
                tag = GEAR_BUTTON_TAG
                setImageResource(R.drawable.ic_settings_24)
                contentDescription = activity.getString(R.string.player_menu)
                imageTintList = ColorStateList.valueOf(color(activity, R.color.app_accent))
                background = ContextCompat.getDrawable(
                    activity,
                    R.drawable.player_control_button_background
                )
                setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))

                setOnClickListener {
                    /* Keep the controller alive while its child menu is being used. */
                    playerView.showController()
                    showPlayerMenu(
                        activity = activity,
                        anchor = this,
                        qualityButton = qualityButton,
                        diagnosticsButton = diagnosticsButton
                    )
                }
            }

        private fun styleTabCountButton(activity: PlayerActivity, button: Button) {
            val tabCount = VideoTabStore.allTabs().size
            button.text = tabCount.toString()
            button.contentDescription = activity.getString(
                R.string.open_saved_tabs_count,
                tabCount
            )
            button.textSize = 14f
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            button.minWidth = 0
            button.minimumWidth = 0
            button.minHeight = 0
            button.minimumHeight = 0
            button.setPadding(0, 0, 0, 0)
            button.setTextColor(color(activity, R.color.app_text_primary))
            button.background = ContextCompat.getDrawable(
                activity,
                R.drawable.player_control_button_background
            )
        }

        /**
         * Media3 automatically hides its controller while playback continues and
         * keeps it visible while paused/ended. Mirroring that exact visibility is
         * what gives the user a clean video-only state without persistent app UI.
         */
        private fun bindToControllerVisibility(
            playerView: GesturePlayerView,
            tabButton: Button,
            gearButton: AppCompatImageButton
        ) {
            fun applyVisibility(visible: Boolean) {
                val target = if (visible) View.VISIBLE else View.GONE
                tabButton.visibility = target
                gearButton.visibility = target
            }

            applyVisibility(playerView.isControllerFullyVisible)
            playerView.setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    applyVisibility(visibility == View.VISIBLE)
                }
            )
        }

        private fun tabLayoutParams(activity: Activity): FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(
                dp(activity, CONTROL_SIZE_DP),
                dp(activity, CONTROL_SIZE_DP),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = dp(activity, CONTROL_BOTTOM_MARGIN_DP)
                marginEnd = dp(
                    activity,
                    FULLSCREEN_SLOT_DP +
                        CONTROL_GAP_DP +
                        CONTROL_SIZE_DP +
                        CONTROL_GAP_DP
                )
            }

        private fun gearLayoutParams(activity: Activity): FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(
                dp(activity, CONTROL_SIZE_DP),
                dp(activity, CONTROL_SIZE_DP),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = dp(activity, CONTROL_BOTTOM_MARGIN_DP)
                marginEnd = dp(activity, FULLSCREEN_SLOT_DP + CONTROL_GAP_DP)
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
