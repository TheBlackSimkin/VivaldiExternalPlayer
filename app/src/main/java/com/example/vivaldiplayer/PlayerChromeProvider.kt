package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * UI-only player chrome coordinator.
 *
 * The actual playback session remains owned by PlayerActivity. This provider
 * deliberately works only with the Player already attached to GesturePlayerView:
 * it never creates another ExoPlayer, never resolves media during background
 * preparation, and never changes the protected #234 private-display architecture.
 *
 * Responsibilities:
 * - keep the agreed double-tap seek gestures while hiding visible +/-10s buttons;
 * - place [tab count] [gear] [fullscreen] in Media3's own lower controller row;
 * - provide one compact anchored gear for Quality, Audio, Volume/Mute,
 *   Playback speed, Favorites and Diagnostics;
 * - make every simple choice submenu compact instead of using centered dialogs;
 * - verify manual video quality against Media3's ACTIVE video format rather than
 *   treating a requested/declared height as proof that the renderer switched;
 * - restore a functional Media3 fullscreen button;
 * - suppress only the Android Recents snapshot of the dashboard on Android 13+.
 *
 * PlayerActivity predates the compact menu provider and intentionally remains the
 * owner of the actual quality-switch algorithms. A few private quality methods are
 * invoked reflectively here, just as existing session/recovery code already reads
 * PlayerActivity.currentResolved reflectively. This avoids duplicating resolver,
 * MediaSource and thumbnail-preview logic while the UI is being polished.
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
        private const val MENU_WIDTH_DP = 236
        private const val MENU_ROW_HEIGHT_DP = 44
        private const val MENU_VERTICAL_INSET_DP = 4
        private const val MENU_SCREEN_MARGIN_DP = 8
        private const val MUTED_EPSILON = 0.001f
        private const val QUALITY_VERIFY_DELAY_MS = 2_500L

        /** Fullscreen state belongs to one visible PlayerActivity only. */
        private val fullscreenStates = WeakHashMap<PlayerActivity, Boolean>()

        /** Remember the most recent non-zero app volume for a convenient unmute. */
        private val lastNonZeroVolumes = WeakHashMap<PlayerActivity, Float>()

        /** Last height Media3 reported as the active/rendered video format. */
        private val observedActualHeights = WeakHashMap<PlayerActivity, Int>()

        /** Manual height which is waiting for Media3 confirmation. */
        private val pendingQualityRequests = WeakHashMap<PlayerActivity, Int>()

        /** Listener binding so repeated attach() calls never duplicate Player listeners. */
        private data class QualityObserverBinding(
            val player: Player,
            val listener: Player.Listener
        )

        private val qualityObservers = WeakHashMap<PlayerActivity, QualityObserverBinding>()

        /** One concrete supported Media3 audio track shown by the Audio submenu. */
        private data class AudioChoice(
            val group: Tracks.Group,
            val trackIndex: Int,
            val label: String,
            val selected: Boolean
        )

        /** One row in our small anchored settings popup. */
        private data class CompactMenuItem(
            val label: String,
            val enabled: Boolean = true,
            val secondary: Boolean = false,
            val action: (() -> Unit)? = null
        )

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            /*
             * Android 13 introduced a Recents-only screenshot switch. It protects
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
                /* attach() is idempotent: controls/listeners are reused, not duplicated. */
                activity.window.decorView.postDelayed({ attach(activity) }, 80L)
            }
        }

        private fun attach(activity: PlayerActivity) {
            if (activity.isFinishing || activity.isDestroyed) return

            val decor = activity.window.decorView as? ViewGroup ?: return
            val playerView = activity.findViewById<GesturePlayerView>(R.id.player_view) ?: return
            val qualityButton = activity.findViewById<Button>(R.id.quality_button) ?: return
            val diagnosticsButton = activity.findViewById<Button>(R.id.diagnostics_button) ?: return

            /* Existing PlayerActivity handlers remain available internally. */
            qualityButton.visibility = View.GONE
            diagnosticsButton.visibility = View.GONE

            configureMedia3Controls(playerView)
            configureFullscreen(activity, playerView)
            bindQualityObserver(activity, playerView.player)

            val tabButton = decor.findViewWithTag<Button>(EXISTING_TAB_BUTTON_TAG) ?: return
            styleTabCountButton(activity, tabButton)

            val gear =
                decor.findViewWithTag<AppCompatImageButton>(GEAR_BUTTON_TAG)
                    ?: createGearButton(activity)

            gear.setOnClickListener {
                playerView.showController()
                showPlayerMenu(
                    activity = activity,
                    anchor = gear,
                    diagnosticsButton = diagnosticsButton,
                    playerView = playerView
                )
            }

            /*
             * Build #249 showed that screen-margin placement was visually wrong.
             * Re-parent directly into Media3's real lower controller row.
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

            /* The stock gear is replaced by the one combined ExternalPlayer gear. */
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                ?.visibility = View.GONE
        }

        /** Register Media3's fullscreen callback without changing player ownership/orientation. */
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

        /** Insert [tab count] [gear] immediately before Media3's fullscreen slot. */
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

        private fun createGearButton(activity: PlayerActivity): AppCompatImageButton =
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
         * One compact anchored gear. All simple choice screens use the same 44dp
         * row popup, including Video Quality. Diagnostics remains a full dialog
         * because its long selectable/copyable text genuinely needs space.
         */
        private fun showPlayerMenu(
            activity: PlayerActivity,
            anchor: View,
            diagnosticsButton: Button,
            playerView: GesturePlayerView
        ) {
            val activePlayer = playerView.player
            val qualityBusy = readPrivateBoolean(activity, "qualityChangeInProgress") == true

            showCompactMenu(
                activity = activity,
                anchor = anchor,
                items = listOf(
                    CompactMenuItem(
                        label = activity.getString(R.string.video_quality),
                        enabled = activePlayer != null && !qualityBusy
                    ) {
                        anchor.post { showQualityMenu(activity, anchor, activePlayer) }
                    },
                    CompactMenuItem(
                        label = activity.getString(R.string.player_audio),
                        enabled = activePlayer != null
                    ) {
                        anchor.post { showAudioMenu(activity, anchor, activePlayer) }
                    },
                    CompactMenuItem(
                        label = activity.getString(R.string.player_volume),
                        enabled = activePlayer != null
                    ) {
                        anchor.post { showVolumeMenu(activity, anchor, activePlayer) }
                    },
                    CompactMenuItem(
                        label = activity.getString(R.string.playback_speed),
                        enabled = activePlayer != null
                    ) {
                        anchor.post { showPlaybackSpeedMenu(activity, anchor, activePlayer) }
                    },
                    CompactMenuItem(activity.getString(R.string.add_favorite)) {
                        saveCurrentFavorite(activity, privateFavorite = false)
                    },
                    CompactMenuItem(activity.getString(R.string.add_private_favorite)) {
                        saveCurrentFavorite(activity, privateFavorite = true)
                    },
                    CompactMenuItem(activity.getString(R.string.player_diagnostics)) {
                        diagnosticsButton.performClick()
                    }
                )
            )
        }

        /** Save only the tab's permanent original page URL, never a temporary media URL. */
        private fun saveCurrentFavorite(activity: PlayerActivity, privateFavorite: Boolean) {
            val tabId = currentTabId(activity)
            val tab = tabId?.let(VideoTabStore::get)
            val originalPageUrl = tab
                ?.let { TabOriginStore.pageUrl(activity, it) }
                ?.trim()
                .orEmpty()
                .ifBlank { tab?.sourceUrl?.trim().orEmpty() }
            val title = tab?.title
                ?.trim()
                .orEmpty()
                .ifBlank { currentResolved(activity)?.title?.trim().orEmpty() }
                .ifBlank { "Favorite" }

            if (!isHttpUrl(originalPageUrl)) {
                Toast.makeText(
                    activity,
                    R.string.favorite_original_url_unavailable,
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            if (tab != null) {
                TabOriginStore.remember(activity, tab.id, originalPageUrl)
            }

            if (!privateFavorite) {
                val saved = FavoriteStore.add(activity, originalPageUrl, title)
                Toast.makeText(
                    activity,
                    if (saved != null) R.string.favorite_saved
                    else R.string.favorite_original_url_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            PrivateFavoriteAuthenticator.authenticate(
                activity,
                onSuccess = {
                    val saved = PrivateFavoriteStore.addAfterAuthentication(
                        activity,
                        originalPageUrl,
                        title
                    )
                    Toast.makeText(
                        activity,
                        if (saved != null) R.string.private_favorite_saved
                        else R.string.favorite_original_url_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        private fun isHttpUrl(value: String): Boolean =
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true)

        /**
         * Compact quality submenu which delegates the actual switching algorithms
         * back to PlayerActivity. The first disabled row reports Media3's current
         * ACTIVE output height; this is deliberately independent of the request.
         */
        private fun showQualityMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val resolved = currentResolved(activity) ?: return
            if (readPrivateBoolean(activity, "qualityChangeInProgress") == true) {
                Toast.makeText(activity, R.string.quality_loading, Toast.LENGTH_SHORT).show()
                return
            }

            val actual = actualVideoHeight(activePlayer) ?: observedActualHeights[activity]
            val statusLabel = actual
                ?.let { activity.getString(R.string.dashboard_actual_quality, it) }
                ?: activity.getString(R.string.quality_actual_pending)

            val choices = mutableListOf(
                CompactMenuItem(
                    label = statusLabel,
                    enabled = false,
                    secondary = true
                )
            )

            if (resolved.resolverMode == "browser") {
                val adaptiveHeights = collectSupportedVideoHeights(activePlayer.currentTracks)

                if (adaptiveHeights.size > 1) {
                    val manualHeight = readPrivateInt(activity, "browserManualHeight")
                    choices += CompactMenuItem(
                        selectedLabel(
                            activity.getString(R.string.quality_auto_prefer_720),
                            manualHeight == null
                        )
                    ) {
                        pendingQualityRequests.remove(activity)
                        currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, null) }
                        invokePlayerMethod(activity, "applyBrowserAutoPolicy")
                    }

                    adaptiveHeights.forEach { height ->
                        choices += CompactMenuItem(
                            selectedLabel("${height}p", manualHeight == height)
                        ) {
                            beginManualQualityVerification(activity, activePlayer, height)
                            currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, height) }
                            writePrivateField(activity, "browserManualHeight", height)
                            writePrivateField(activity, "browserAutoTargetHeight", null)
                            val applied = invokePlayerMethod(
                                activity,
                                "applyBrowserTrackOverride",
                                height,
                                false
                            )
                            invokePlayerMethod(activity, "updateBrowserQualityButton")
                            if (!applied) failPendingQualityChange(activity)
                        }
                    }

                    showCompactMenu(activity, anchor, choices)
                    return
                }

                val variants = resolved.browserVariants
                    .filter { it.height != null && it.height > 0 }
                    .distinctBy { it.height }
                val variantHeights = variants
                    .mapNotNull { it.height }
                    .distinct()
                    .sortedDescending()

                if (variantHeights.size > 1) {
                    val currentRequested = currentResolved(activity)?.requestedQuality.orEmpty()
                    val currentManualHeight = currentRequested.toIntOrNull()

                    choices += CompactMenuItem(
                        selectedLabel(
                            activity.getString(R.string.quality_auto_prefer_720),
                            currentManualHeight == null
                        )
                    ) {
                        pendingQualityRequests.remove(activity)
                        currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, null) }
                        val target = preferredHeight(variantHeights) ?: return@CompactMenuItem
                        val source = variants
                            .filter { it.height == target }
                            .maxByOrNull { it.width ?: 0 }
                            ?: return@CompactMenuItem
                        if (invokePlayerMethod(activity, "switchBrowserVariant", source)) {
                            /* Preserve the user's Auto intent after the private method switches URL. */
                            currentResolved(activity)?.let { refreshed ->
                                writePrivateField(
                                    activity,
                                    "currentResolved",
                                    refreshed.copy(requestedQuality = "auto")
                                )
                            }
                        }
                    }

                    variantHeights.forEach { height ->
                        choices += CompactMenuItem(
                            selectedLabel("${height}p", currentManualHeight == height)
                        ) {
                            val source = variants
                                .filter { it.height == height }
                                .maxByOrNull { it.width ?: 0 }
                                ?: return@CompactMenuItem
                            beginManualQualityVerification(activity, activePlayer, height)
                            currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, height) }
                            if (!invokePlayerMethod(activity, "switchBrowserVariant", source)) {
                                failPendingQualityChange(activity)
                            }
                        }
                    }

                    showCompactMenu(activity, anchor, choices)
                    return
                }

                Toast.makeText(
                    activity,
                    R.string.browser_quality_unavailable,
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            /* yt-dlp path: preserve the exact existing list and resolver method. */
            val currentKey = resolved.requestedQuality
            val directChoices = listOf(
                "auto" to activity.getString(R.string.quality_auto_prefer_720),
                "1080" to "1080p",
                "720" to "720p",
                "480" to "480p",
                "360" to "360p"
            )

            directChoices.forEach { (key, label) ->
                choices += CompactMenuItem(selectedLabel(label, currentKey == key)) {
                    if (key == currentKey) return@CompactMenuItem

                    if (key == "auto") {
                        pendingQualityRequests.remove(activity)
                        currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, null) }
                    } else {
                        val height = key.toInt()
                        beginManualQualityVerification(activity, activePlayer, height)
                        currentTabId(activity)?.let { VideoTabStore.setManualQuality(it, height) }
                    }

                    if (!invokePlayerMethod(activity, "changeYtDlpQuality", key)) {
                        failPendingQualityChange(activity)
                    }
                }
            }

            showCompactMenu(activity, anchor, choices)
        }

        /** Audio selection on the SAME Media3 Player. */
        private fun showAudioMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val audioChoices = collectAudioChoices(activity, activePlayer.currentTracks)
            if (audioChoices.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.audio_tracks_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val anyExplicitSelection = audioChoices.any { it.selected }
            val items = mutableListOf(
                CompactMenuItem(
                    selectedLabel(
                        activity.getString(R.string.audio_auto),
                        !anyExplicitSelection
                    )
                ) {
                    activePlayer.trackSelectionParameters =
                        activePlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .build()
                }
            )

            audioChoices.forEach { choice ->
                items += CompactMenuItem(selectedLabel(choice.label, choice.selected)) {
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
            }

            showCompactMenu(activity, anchor, items)
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

        /** App-level volume only; never changes Android's global media volume. */
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
            val items = mutableListOf(
                CompactMenuItem(
                    activity.getString(
                        if (currentVolume <= MUTED_EPSILON) R.string.unmute else R.string.mute
                    )
                ) {
                    if (currentVolume <= MUTED_EPSILON) {
                        activePlayer.volume =
                            (lastNonZeroVolumes[activity] ?: 1.0f).coerceIn(0f, 1f)
                    } else {
                        lastNonZeroVolumes[activity] = currentVolume
                        activePlayer.volume = 0f
                    }
                }
            )

            levels.forEach { level ->
                val percent = "${(level * 100).toInt()}%"
                items += CompactMenuItem(
                    selectedLabel(percent, abs(currentVolume - level) < 0.01f)
                ) {
                    activePlayer.volume = level
                    lastNonZeroVolumes[activity] = level
                }
            }

            showCompactMenu(activity, anchor, items)
        }

        /** Familiar Media3-style speed choices in the same compact popup family. */
        private fun showPlaybackSpeedMenu(
            activity: PlayerActivity,
            anchor: View,
            activePlayer: Player?
        ) {
            if (activePlayer == null || activity.isFinishing || activity.isDestroyed) return

            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            val currentSpeed = activePlayer.playbackParameters.speed
            val items = speeds.map { speed ->
                CompactMenuItem(
                    selectedLabel(speedLabel(speed), abs(speed - currentSpeed) < 0.01f)
                ) {
                    activePlayer.setPlaybackSpeed(speed)
                }
            }

            showCompactMenu(activity, anchor, items)
        }

        /**
         * Small custom PopupWindow rather than PopupMenu. Android's stock PopupMenu
         * controls its own generous row height, so it cannot satisfy the requested
         * slightly denser gear/submenu layout without private framework internals.
         */
        private fun showCompactMenu(
            activity: PlayerActivity,
            anchor: View,
            items: List<CompactMenuItem>
        ) {
            if (items.isEmpty() || activity.isFinishing || activity.isDestroyed) return

            val width = dp(activity, MENU_WIDTH_DP)
            val rowHeight = dp(activity, MENU_ROW_HEIGHT_DP)
            val verticalInset = dp(activity, MENU_VERTICAL_INSET_DP)
            val popupHeight = (items.size * rowHeight) + (verticalInset * 2)
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, verticalInset, 0, verticalInset)
            }

            val popupBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 10).toFloat()
                setColor(color(activity, R.color.app_surface_raised))
                setStroke(dp(activity, 1), color(activity, R.color.app_outline))
            }

            /*
             * Build #275 used WRAP_CONTENT height and showAsDropDown from Media3's
             * bottom controller row. Some Android builds clipped that popup to the
             * few pixels remaining below the gear instead of flipping it upward.
             * Give the popup an explicit height so placement is deterministic.
             */
            val popup = PopupWindow(
                container,
                width,
                popupHeight,
                true
            ).apply {
                setBackgroundDrawable(popupBackground)
                isOutsideTouchable = true
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                elevation = dp(activity, 10).toFloat()
            }

            items.forEach { item ->
                val row = TextView(activity).apply {
                    text = item.label
                    textSize = if (item.secondary) 12.5f else 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(activity, 14), 0, dp(activity, 14), 0)
                    setTextColor(
                        color(
                            activity,
                            if (item.enabled) R.color.app_text_primary else R.color.app_text_secondary
                        )
                    )
                    isEnabled = item.enabled
                    alpha = if (item.enabled) 1f else 0.78f
                    contentDescription = item.label

                    if (item.enabled && item.action != null) {
                        val outValue = TypedValue()
                        if (
                            activity.theme.resolveAttribute(
                                android.R.attr.selectableItemBackground,
                                outValue,
                                true
                            ) && outValue.resourceId != 0
                        ) {
                            setBackgroundResource(outValue.resourceId)
                        }

                        setOnClickListener {
                            popup.dismiss()
                            item.action.invoke()
                        }
                    }
                }

                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        rowHeight
                    )
                )
            }

            /*
             * Position the complete menu above the lower-right gear ourselves.
             * This avoids device-specific PopupWindow dropdown clipping while
             * keeping the menu visually anchored to the same controller control.
             */
            val anchorLocation = IntArray(2)
            anchor.getLocationOnScreen(anchorLocation)
            val visibleFrame = android.graphics.Rect()
            activity.window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
            val margin = dp(activity, MENU_SCREEN_MARGIN_DP)

            val minX = visibleFrame.left + margin
            val maxX = (visibleFrame.right - width - margin).coerceAtLeast(minX)
            val desiredX = anchorLocation[0] + anchor.width - width
            val popupX = desiredX.coerceIn(minX, maxX)

            val minY = visibleFrame.top + margin
            val maxY = (visibleFrame.bottom - popupHeight - margin).coerceAtLeast(minY)
            val desiredAboveY = anchorLocation[1] - popupHeight - margin
            val desiredBelowY = anchorLocation[1] + anchor.height + margin
            val popupY = if (desiredAboveY >= minY) {
                desiredAboveY.coerceAtMost(maxY)
            } else {
                desiredBelowY.coerceIn(minY, maxY)
            }

            popup.showAtLocation(
                activity.window.decorView,
                Gravity.TOP or Gravity.START,
                popupX,
                popupY
            )
        }

        /** Attach exactly one observer to the existing player for actual-quality proof. */
        private fun bindQualityObserver(activity: PlayerActivity, activePlayer: Player?) {
            if (activePlayer == null) return

            val existing = qualityObservers[activity]
            if (existing?.player === activePlayer) {
                recordActualQuality(activity, activePlayer)
                return
            }

            existing?.player?.removeListener(existing.listener)

            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    recordActualQuality(
                        activity,
                        activePlayer,
                        videoSize.height.takeIf { it > 0 }
                    )
                }

                override fun onTracksChanged(tracks: Tracks) {
                    recordActualQuality(activity, activePlayer)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        recordActualQuality(activity, activePlayer)
                    }
                }
            }

            activePlayer.addListener(listener)
            qualityObservers[activity] = QualityObserverBinding(activePlayer, listener)
            recordActualQuality(activity, activePlayer)
        }

        /**
         * Media3 Player.videoFormat is the currently active video format. This is
         * stronger evidence than the resolver's declared height or requested mode.
         */
        private fun actualVideoHeight(activePlayer: Player): Int? =
            activePlayer.videoFormat?.height?.takeIf { it > 0 }

        private fun recordActualQuality(
            activity: PlayerActivity,
            activePlayer: Player,
            fallbackHeight: Int? = null
        ) {
            val height = actualVideoHeight(activePlayer)
                ?: fallbackHeight?.takeIf { it > 0 }
                ?: return

            observedActualHeights[activity] = height
            currentTabId(activity)?.let { VideoTabStore.setActualQuality(it, height) }

            val requested = pendingQualityRequests[activity]
            if (requested != null && requested == height) {
                pendingQualityRequests.remove(activity)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.quality_manual_verified, requested),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        /** Begin a manual request but do not claim success until Media3 confirms it. */
        private fun beginManualQualityVerification(
            activity: PlayerActivity,
            activePlayer: Player,
            requestedHeight: Int
        ) {
            pendingQualityRequests[activity] = requestedHeight
            Toast.makeText(
                activity,
                activity.getString(R.string.quality_manual_waiting, requestedHeight),
                Toast.LENGTH_SHORT
            ).show()

            val alreadyActual = actualVideoHeight(activePlayer)
                ?: observedActualHeights[activity]
            if (alreadyActual == requestedHeight) {
                recordActualQuality(activity, activePlayer, alreadyActual)
                return
            }

            activity.window.decorView.postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                if (pendingQualityRequests[activity] != requestedHeight) return@postDelayed

                val actual = actualVideoHeight(activePlayer)
                    ?: observedActualHeights[activity]

                if (actual != null) {
                    pendingQualityRequests.remove(activity)
                    currentTabId(activity)?.let { VideoTabStore.setActualQuality(it, actual) }
                    Toast.makeText(
                        activity,
                        activity.getString(
                            R.string.quality_manual_actual,
                            requestedHeight,
                            actual
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, QUALITY_VERIFY_DELAY_MS)
        }

        private fun failPendingQualityChange(activity: PlayerActivity) {
            pendingQualityRequests.remove(activity)
            Toast.makeText(activity, R.string.quality_change_failed, Toast.LENGTH_LONG).show()
        }

        private fun collectSupportedVideoHeights(tracks: Tracks): List<Int> =
            tracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group ->
                    (0 until group.length).mapNotNull { index ->
                        if (!group.isTrackSupported(index)) null
                        else group.getTrackFormat(index).height.takeIf { it > 0 }
                    }
                }
                .distinct()
                .sortedDescending()

        private fun preferredHeight(heights: List<Int>): Int? = when {
            720 in heights -> 720
            1080 in heights -> 1080
            heights.any { it < 1080 } -> heights.filter { it < 1080 }.maxOrNull()
            else -> heights.minOrNull()
        }

        private fun currentTabId(activity: PlayerActivity): String? =
            activity.intent
                .getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
                ?.takeIf { it.isNotBlank() }

        /** Same established reflection pattern already used by session/recovery code. */
        private fun currentResolved(activity: PlayerActivity): ResolvedMedia? =
            readPrivateField(activity, "currentResolved") as? ResolvedMedia

        private fun readPrivateBoolean(activity: PlayerActivity, name: String): Boolean? =
            readPrivateField(activity, name) as? Boolean

        private fun readPrivateInt(activity: PlayerActivity, name: String): Int? =
            readPrivateField(activity, name) as? Int

        private fun readPrivateField(activity: PlayerActivity, name: String): Any? =
            runCatching {
                PlayerActivity::class.java.getDeclaredField(name).apply {
                    isAccessible = true
                }.get(activity)
            }.getOrNull()

        private fun writePrivateField(
            activity: PlayerActivity,
            name: String,
            value: Any?
        ): Boolean = runCatching {
            PlayerActivity::class.java.getDeclaredField(name).apply {
                isAccessible = true
            }.set(activity, value)
            true
        }.getOrDefault(false)

        private fun invokePlayerMethod(
            activity: PlayerActivity,
            name: String,
            vararg args: Any?
        ): Boolean {
            val method = PlayerActivity::class.java.declaredMethods.firstOrNull { candidate ->
                candidate.name == name && candidate.parameterTypes.size == args.size
            } ?: return false

            return runCatching {
                method.isAccessible = true
                method.invoke(activity, *args)
                true
            }.getOrDefault(false)
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
                observedActualHeights.remove(activity)
                pendingQualityRequests.remove(activity)
                qualityObservers.remove(activity)?.let { binding ->
                    binding.player.removeListener(binding.listener)
                }
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
