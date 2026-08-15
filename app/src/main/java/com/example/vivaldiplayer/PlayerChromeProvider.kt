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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale
import kotlin.math.abs

/**
 * UI-only player chrome coordinator.
 *
 * The player itself remains owned by PlayerActivity/Media3. This provider only
 * arranges ExternalPlayer-specific controls inside Media3's existing controller:
 *
 * - visible ±10 second buttons are removed because GesturePlayerView already
 *   provides the agreed left/right double-tap seek behavior;
 * - the saved-tab count and one ExternalPlayer gear are inserted directly into
 *   Media3's real lower control row, immediately before fullscreen;
 * - because these controls become children of Media3's controller, they inherit
 *   its normal show/auto-hide/end-of-video behavior without a parallel timer;
 * - the one gear combines ExternalPlayer Quality/Diagnostics with the Audio and
 *   Playback speed functions which were previously supplied by Media3's own gear.
 *
 * Audio and speed operate on the SAME Player instance exposed by PlayerView.
 * Nothing here creates a second ExoPlayer, changes resolver policy, starts
 * background playback, or touches the protected private-display preparation path.
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
        private const val CONTROL_SIZE_DP = 44
        private const val CONTROL_GAP_DP = 2

        /** One concrete supported Media3 audio track shown by the Audio chooser. */
        private data class AudioChoice(
            val group: Tracks.Group,
            val trackIndex: Int,
            val label: String,
            val selected: Boolean
        )

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is PlayerActivity) {
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is PlayerActivity) {
                /*
                 * Refresh count/order after returning from the dashboard or from
                 * a dialog. Re-running attach is idempotent: existing controls are
                 * re-parented rather than duplicated.
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

            /* Existing PlayerActivity handlers remain the owners of these actions. */
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
                    )

            /*
             * IMPORTANT: do not position by screen margins. The #249 device pass
             * showed that margin-based placement did not visually land in the
             * requested corner. Re-parent both controls into Media3's actual
             * horizontal control row immediately before fullscreen instead.
             */
            installBeforeFullscreen(
                activity = activity,
                playerView = playerView,
                tabButton = tabButton,
                gearButton = gear
            )
        }

        /** Keep gesture seeking but remove the two dedicated visible seek buttons. */
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
             * Media3's old gear provided Audio and Playback speed. We hide that
             * duplicate gear only after explicitly restoring those two functions
             * in ExternalPlayer's combined gear menu below.
             */
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                ?.visibility = View.GONE
        }

        private fun hideControlAndWrapper(playerView: GesturePlayerView, controlId: Int) {
            val control = playerView.findViewById<View>(controlId) ?: return
            control.visibility = View.GONE
            (control.parent as? View)?.visibility = View.GONE
        }

        /**
         * Insert [tab count] [gear] immediately before Media3's fullscreen slot.
         *
         * Some Media3 layouts wrap a button in a small FrameLayout. To remain
         * robust across its normal/minimal controller layouts, walk upward from
         * the fullscreen view until we reach the nearest horizontal LinearLayout,
         * then insert before the direct child branch containing fullscreen.
         */
        private fun installBeforeFullscreen(
            activity: PlayerActivity,
            playerView: GesturePlayerView,
            tabButton: Button,
            gearButton: AppCompatImageButton
        ) {
            val fullscreen =
                playerView.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)
                    ?: return

            val row = nearestHorizontalRow(playerView, fullscreen) ?: return
            val fullscreenSlot = directChildUnder(row, fullscreen) ?: return

            removeFromCurrentParent(tabButton)
            removeFromCurrentParent(gearButton)

            val fullscreenIndex = row.indexOfChild(fullscreenSlot)
            if (fullscreenIndex < 0) return

            row.addView(
                tabButton,
                fullscreenIndex,
                controlLayoutParams(activity)
            )
            row.addView(
                gearButton,
                fullscreenIndex + 1,
                controlLayoutParams(activity)
            )
        }

        private fun nearestHorizontalRow(
            playerView: GesturePlayerView,
            descendant: View
        ): LinearLayout? {
            var parent = descendant.parent as? ViewGroup
            while (parent != null && parent !== playerView) {
                if (parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL) {
                    return parent
                }
                parent = parent.parent as? ViewGroup
            }
            return null
        }

        private fun directChildUnder(ancestor: ViewGroup, descendant: View): View? {
            var current: View = descendant
            var parent = current.parent as? ViewGroup

            while (parent != null && parent !== ancestor) {
                current = parent
                parent = current.parent as? ViewGroup
            }

            return current.takeIf { parent === ancestor }
        }

        private fun removeFromCurrentParent(view: View) {
            (view.parent as? ViewGroup)?.removeView(view)
        }

        private fun controlLayoutParams(activity: Activity): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                dp(activity, CONTROL_SIZE_DP),
                dp(activity, CONTROL_SIZE_DP)
            ).apply {
                marginEnd = dp(activity, CONTROL_GAP_DP)
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
                    playerView.showController()
                    showPlayerMenu(
                        activity = activity,
                        anchor = this,
                        qualityButton = qualityButton,
                        diagnosticsButton = diagnosticsButton,
                        playerView = playerView
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

        /** One combined gear: Quality, Audio, Playback speed, Diagnostics. */
        private fun showPlayerMenu(
            activity: PlayerActivity,
            anchor: View,
            qualityButton: Button,
            diagnosticsButton: Button,
            playerView: GesturePlayerView
        ) {
            val activePlayer = playerView.player

            PopupMenu(activity, anchor).apply {
                val quality = menu.add(0, 1, 0, activity.getString(R.string.video_quality))
                quality.isEnabled = qualityButton.isEnabled

                val audio = menu.add(0, 2, 1, activity.getString(R.string.player_audio))
                audio.isEnabled = activePlayer != null

                val speed = menu.add(0, 3, 2, activity.getString(R.string.playback_speed))
                speed.isEnabled = activePlayer != null

                menu.add(0, 4, 3, activity.getString(R.string.player_diagnostics))

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> qualityButton.performClick()
                        2 -> {
                            showAudioDialog(activity, activePlayer)
                            true
                        }
                        3 -> {
                            showPlaybackSpeedDialog(activity, activePlayer)
                            true
                        }
                        4 -> diagnosticsButton.performClick()
                        else -> false
                    }
                }
                show()
            }
        }

        /**
         * Restore Media3-style audio selection on the existing Player instance.
         * "Auto" simply removes ExternalPlayer's audio override and lets Media3
         * choose according to its normal track-selection parameters again.
         */
        private fun showAudioDialog(activity: PlayerActivity, activePlayer: Player?) {
            if (activePlayer == null) return

            val choices = collectAudioChoices(activity, activePlayer.currentTracks)
            if (choices.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.audio_tracks_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val labels = mutableListOf(activity.getString(R.string.audio_auto))
            labels += choices.map { it.label }

            val selectedChoiceIndex = choices.indexOfFirst { it.selected }
            val checkedIndex = if (selectedChoiceIndex >= 0) selectedChoiceIndex + 1 else 0

            AlertDialog.Builder(activity)
                .setTitle(R.string.player_audio)
                .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                    dialog.dismiss()

                    if (which == 0) {
                        activePlayer.trackSelectionParameters =
                            activePlayer.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .build()
                        return@setSingleChoiceItems
                    }

                    val choice = choices[which - 1]
                    val override = TrackSelectionOverride(
                        choice.group.mediaTrackGroup,
                        listOf(choice.trackIndex)
                    )

                    activePlayer.trackSelectionParameters =
                        activePlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setOverrideForType(override)
                            .build()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun collectAudioChoices(
            activity: PlayerActivity,
            tracks: Tracks
        ): List<AudioChoice> {
            val result = mutableListOf<AudioChoice>()
            var fallbackNumber = 1

            tracks.groups
                .filter { group -> group.type == C.TRACK_TYPE_AUDIO }
                .forEach { group ->
                    for (index in 0 until group.length) {
                        if (!group.isTrackSupported(index)) continue

                        val format = group.getTrackFormat(index)
                        val explicitLabel = format.label?.trim().orEmpty().takeIf { it.isNotBlank() }
                        val languageLabel = format.language
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && it != "und" }
                            ?.let { tag -> Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()) }
                            ?.takeIf { it.isNotBlank() }

                        val labelParts = listOfNotNull(explicitLabel, languageLabel).distinct()
                        val label = if (labelParts.isNotEmpty()) {
                            labelParts.joinToString(" • ")
                        } else {
                            activity.getString(R.string.audio_track_number, fallbackNumber)
                        }

                        result += AudioChoice(
                            group = group,
                            trackIndex = index,
                            label = label,
                            selected = group.isTrackSelected(index)
                        )
                        fallbackNumber += 1
                    }
                }

            return result
        }

        /** Restore the familiar Media3 playback-speed choices in the combined gear. */
        private fun showPlaybackSpeedDialog(activity: PlayerActivity, activePlayer: Player?) {
            if (activePlayer == null) return

            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            val labels = speeds.map { speed -> speedLabel(speed) }.toTypedArray()
            val currentSpeed = activePlayer.playbackParameters.speed
            val checkedIndex = speeds.indices.minByOrNull { index ->
                abs(speeds[index] - currentSpeed)
            } ?: speeds.indexOf(1.0f)

            AlertDialog.Builder(activity)
                .setTitle(R.string.playback_speed)
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    activePlayer.setPlaybackSpeed(speeds[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun speedLabel(speed: Float): String =
            if (speed % 1f == 0f) {
                "${speed.toInt()}×"
            } else {
                "${speed}×"
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
