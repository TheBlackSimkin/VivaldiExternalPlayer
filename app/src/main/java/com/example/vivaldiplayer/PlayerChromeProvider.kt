package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * UI-only player chrome coordinator.
 *
 * The actual playback session remains owned by PlayerActivity. This provider
 * deliberately works only with the Player already attached to GesturePlayerView:
 * it never creates another ExoPlayer, never resolves a URL, and never participates
 * in background preparation.
 *
 * Responsibilities:
 * - keep the agreed double-tap seek gestures while hiding visible +/-10s buttons;
 * - place [tab count] [gear] [fullscreen] in Media3's own lower controller row;
 * - provide one compact, anchored gear for Quality, Audio, Volume/Mute,
 *   Playback speed and Diagnostics;
 * - restore a functional Media3 fullscreen button;
 * - suppress only the Android Recents snapshot of the dashboard on Android 13+.
 *
 * The last point intentionally uses the Recents-only API instead of FLAG_SECURE
 * on MainActivity, so ordinary screenshots of the dashboard remain available.
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
        private const val MUTED_EPSILON = 0.001f

        /** Fullscreen state belongs to one visible PlayerActivity only. */
        private val fullscreenStates = WeakHashMap<PlayerActivity, Boolean>()

        /**
         * Remember the most recent non-zero app volume for a convenient unmute.
         * This is session-local and never changes the phone's system media volume.
         */
        private val lastNonZeroVolumes = WeakHashMap<PlayerActivity, Float>()

        /** One concrete supported Media3 audio track shown by the Audio submenu. */
        private data class AudioChoice(
            val group: Tracks.Group,
            val trackIndex: Int,
            val label: String,
            val selected: Boolean
        )

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            /*
             * Android 13 introduced a Recents-only screenshot switch. It is a
             * better fit for the dashboard than FLAG_SECURE because it protects
             * the Overview thumbnail without unnecessarily blocking user screenshots.
             */
            if (activity is MainActivity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(false)
            }

            if (activity is PlayerActivity) {
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is PlayerActivity) {
                /*
                 * Refresh count/order after returning from the dashboard or a
                 * menu. attach() is idempotent: controls are re-parented rather
                 * than duplicated.
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
            configureFullscreen(activity, playerView)

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
             * Re-assign the listener even for an already-created gear. This keeps
             * resumed/configuration-changed players on the newest compact menu.
             */
            gear.setOnClickListener {
                playerView.showController()
                showPlayerMenu(
                    activity = activity,
                    anchor = gear,
                    qualityButton = qualityButton,
                    diagnosticsButton = diagnosticsButton,
                    playerView = playerView
                )
            }

            /*
             * Do not position by screen margins. Build #249 showed that margin
             * placement was visually wrong. Re-parent directly into Media3's
             * horizontal controller row immediately before fullscreen.
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
            hideControlAndWrapper(playerView, androidx.media3.ui.R.id.exo_rew_with_amount)
            hideControlAndWrapper(playerView, androidx.media3.ui.R.id.exo_ffwd_with_amount)

            /*
             * Media3's stock gear is hidden only because ExternalPlayer's one gear
             * below now carries its Audio and Playback speed functions as well.
             */
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                ?.visibility = View.GONE
        }

        /**
         * Register Media3's fullscreen callback. Without a listener Media3 can
         * keep its fullscreen control unavailable; Build #251 exposed exactly
         * that omission.
         *
         * PlayerActivity already supports rotation. Fullscreen here means the
         * video activity uses the whole display by hiding system bars; it does not
         * create a second player or force a particular orientation.
         */
        private fun configureFullscreen(
            activity: PlayerActivity,
            playerView: GesturePlayerView
        ) {
            val current = fullscreenStates[activity] ?: false

            playerView.setFullscreenButtonClickListener { requestedFullscreen ->
                applyFullscreen(activity, playerView, requestedFullscreen)
            }
            playerView.setFullscreenButtonState(current)
        }

        private fun applyFullscreen(
            activity: PlayerActivity,
            playerView: GesturePlayerView,
            fullscreen: Boolean
        ) {
            fullscreenStates[activity] = fullscreen
            playerView.setFullscreenButtonState(fullscreen)

            WindowCompat.setDecorFitsSystemWindows(activity.window, !fullscreen)
            val controller = WindowCompat.getInsetsController(
                activity.window,
                activity.window.decorView
            )

            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        private fun hideControlAndWrapper(playerView: GesturePlayerView, controlId: Int) {
            val control = playerView.findViewById<View>(controlId) ?: return
            control.visibility = View.GONE
            (control.parent as? View)?.visibility = View.GONE
        }

        /**
         * Insert [tab count] [gear] immediately before Media3's fullscreen slot.
         * Media3 may wrap controls, so we walk upward to its nearest horizontal
         * LinearLayout and insert before the direct child containing fullscreen.
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

            row.addView(tabButton, fullscreenIndex, controlLayoutParams(activity))
            row.addView(gearButton, fullscreenIndex + 1, controlLayoutParams(activity))
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

        /**
         * One compact anchored gear. Audio, Volume and Speed open another anchored
         * PopupMenu instead of a centered modal dialog, matching the user's
         * preference for Media3-like settings behavior.
         */
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

                val volume = menu.add(0, 3, 2, activity.getString(R.string.player_volume))
                volume.isEnabled = activePlayer != null

                val speed = menu.add(0, 4, 3, activity.getString(R.string.playback_speed))
                speed.isEnabled = activePlayer != null

                menu.add(0, 5, 4, activity.getString(R.string.player_diagnostics))

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> qualityButton.performClick()
                        2 -> {
                            anchor.post { showAudioMenu(activity, anchor, activePlayer) }
                            true
                        }
                        3 -> {
                            anchor.post { showVolumeMenu(activity, anchor, activePlayer) }
                            true
                        }
                        4 -> {
                            anchor.post { showPlaybackSpeedMenu(activity, anchor, activePlayer) }
                            true
                        }
                        5 -> diagnosticsButton.performClick()
                        else -> false
                    }
                }
                show()
            }
        }

        /**
         * Audio selection on the SAME Media3 Player. "Auto" removes only the
         * ExternalPlayer audio override and returns selection to Media3.
         */
        private fun showAudioMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val choices = collectAudioChoices(activity, activePlayer.currentTracks)
            if (choices.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.audio_tracks_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            PopupMenu(activity, anchor).apply {
                val anyExplicitSelection = choices.any { it.selected }
                menu.add(
                    0,
                    100,
                    0,
                    selectedLabel(activity.getString(R.string.audio_auto), !anyExplicitSelection)
                )

                choices.forEachIndexed { index, choice ->
                    menu.add(
                        0,
                        101 + index,
                        index + 1,
                        selectedLabel(choice.label, choice.selected)
                    )
                }

                setOnMenuItemClickListener { item ->
                    if (item.itemId == 100) {
                        activePlayer.trackSelectionParameters =
                            activePlayer.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .build()
                        return@setOnMenuItemClickListener true
                    }

                    val choiceIndex = item.itemId - 101
                    val choice = choices.getOrNull(choiceIndex)
                        ?: return@setOnMenuItemClickListener false
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
                    true
                }
                show()
            }
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
                        val explicitLabel = format.label
                            ?.trim()
                            .orEmpty()
                            .takeIf { it.isNotBlank() }
                        val languageLabel = format.language
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && it != "und" }
                            ?.let { tag ->
                                Locale.forLanguageTag(tag)
                                    .getDisplayLanguage(Locale.getDefault())
                            }
                            ?.takeIf { it.isNotBlank() }

                        val parts = listOfNotNull(explicitLabel, languageLabel).distinct()
                        val label = if (parts.isNotEmpty()) {
                            parts.joinToString(" • ")
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

        /**
         * App-level volume only. Player.volume is relative to the system media
         * volume and cannot change the phone's global volume. Muting therefore
         * affects this ExternalPlayer playback session only.
         */
        private fun showVolumeMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val currentVolume = activePlayer.volume.coerceIn(0f, 1f)
            if (currentVolume > MUTED_EPSILON) {
                lastNonZeroVolumes[activity] = currentVolume
            }

            val levels = listOf(0.25f, 0.50f, 0.75f, 1.00f)

            PopupMenu(activity, anchor).apply {
                menu.add(
                    0,
                    200,
                    0,
                    activity.getString(
                        if (currentVolume <= MUTED_EPSILON) R.string.unmute else R.string.mute
                    )
                )

                levels.forEachIndexed { index, level ->
                    val percent = "${(level * 100).toInt()}%"
                    menu.add(
                        0,
                        201 + index,
                        index + 1,
                        selectedLabel(percent, abs(currentVolume - level) < 0.01f)
                    )
                }

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        200 -> {
                            if (currentVolume <= MUTED_EPSILON) {
                                activePlayer.volume =
                                    (lastNonZeroVolumes[activity] ?: 1.0f).coerceIn(0f, 1f)
                            } else {
                                lastNonZeroVolumes[activity] = currentVolume
                                activePlayer.volume = 0f
                            }
                            true
                        }
                        in 201..204 -> {
                            val level = levels[item.itemId - 201]
                            activePlayer.volume = level
                            lastNonZeroVolumes[activity] = level
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        /** Familiar Media3-style speed choices, now presented as an anchored menu. */
        private fun showPlaybackSpeedMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            val currentSpeed = activePlayer.playbackParameters.speed

            PopupMenu(activity, anchor).apply {
                speeds.forEachIndexed { index, speed ->
                    menu.add(
                        0,
                        300 + index,
                        index,
                        selectedLabel(speedLabel(speed), abs(speed - currentSpeed) < 0.01f)
                    )
                }

                setOnMenuItemClickListener { item ->
                    val index = item.itemId - 300
                    val speed = speeds.getOrNull(index)
                        ?: return@setOnMenuItemClickListener false
                    activePlayer.setPlaybackSpeed(speed)
                    true
                }
                show()
            }
        }

        private fun selectedLabel(label: String, selected: Boolean): String =
            if (selected) "✓ $label" else label

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

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is PlayerActivity) {
                fullscreenStates.remove(activity)
                lastNonZeroVolumes.remove(activity)
            }
        }
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
