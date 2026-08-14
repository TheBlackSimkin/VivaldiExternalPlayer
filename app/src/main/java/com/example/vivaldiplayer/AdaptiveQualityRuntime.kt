package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import java.util.WeakHashMap

/**
 * Runtime controller for browser adaptive quality selection.
 *
 * Requested quality and actual decoded quality are intentionally independent.
 * A button tap stores the requested manual height. Only Media3's VideoSize
 * callback stores the actual height, so the UI can never falsely claim that a
 * rendition changed merely because a menu item was selected.
 */
object AdaptiveQualityRuntime {
    private var installed = false

    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(QualityLifecycle)
    }

    private object QualityLifecycle : Application.ActivityLifecycleCallbacks {
        private val controllers = WeakHashMap<PlayerActivity, QualityController>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity !is PlayerActivity) return
            activity.window.decorView.post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    controllers.getOrPut(activity) { QualityController(activity) }.attach()
                }
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity is PlayerActivity) controllers.remove(activity)?.detach()
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    private class QualityController(
        private val activity: PlayerActivity
    ) : Player.Listener {

        private data class Option(
            val group: Tracks.Group,
            val index: Int,
            val height: Int,
            val width: Int,
            val bitrate: Int
        )

        private var player: Player? = null
        private var button: Button? = null
        private var adaptiveButtonInstalled = false
        private var manualHeight: Int? = null
        private var actualHeight: Int? = null
        private var restoredManualPreference = false
        private var reappliedAfterGroupRefresh = false

        fun attach() {
            val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
            player = activePlayer
            button = activity.findViewById(R.id.quality_button)
            activePlayer.addListener(this)

            actualHeight = activePlayer.videoSize.height.takeIf { it > 0 }
            actualHeight?.let { persistActual(it) }
            maybeInstallAdaptiveButton(activePlayer.currentTracks)
        }

        fun detach() {
            player?.removeListener(this)
            player = null
            button = null
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val height = videoSize.height.takeIf { it > 0 } ?: return
            actualHeight = height
            persistActual(height)
            updateButtonLabel()
        }

        override fun onTracksChanged(tracks: Tracks) {
            maybeInstallAdaptiveButton(tracks)

            val wanted = manualHeight ?: return
            if (options(tracks).none { it.height == wanted }) return

            /*
             * A manifest refresh can replace TrackGroup identity and silently
             * invalidate an old override. Re-apply once to the refreshed group.
             * Actual success is still confirmed only by onVideoSizeChanged.
             */
            if (!reappliedAfterGroupRefresh && actualHeight != wanted) {
                reappliedAfterGroupRefresh = true
                activity.window.decorView.postDelayed({ applyExactHeight(wanted, persist = false) }, 250L)
            } else if (actualHeight == wanted) {
                reappliedAfterGroupRefresh = false
            }
        }

        private fun maybeInstallAdaptiveButton(tracks: Tracks) {
            val available = options(tracks)
            if (available.map { it.height }.distinct().size <= 1) return

            if (!adaptiveButtonInstalled) {
                adaptiveButtonInstalled = true
                button?.setOnClickListener { showAdaptiveDialog() }
            }

            if (!restoredManualPreference) {
                restoredManualPreference = true
                val saved = tabId()?.let { VideoTabStore.get(it)?.manualQualityHeight }
                if (saved != null && available.any { it.height == saved }) {
                    manualHeight = saved
                    applyExactHeight(saved, persist = false)
                }
            }

            updateButtonLabel()
        }

        private fun showAdaptiveDialog() {
            val activePlayer = player ?: return
            val heights = options(activePlayer.currentTracks)
                .map { it.height }
                .distinct()
                .sortedDescending()
            if (heights.size <= 1) return

            val labels = mutableListOf(activity.getString(R.string.quality_auto_prefer_720))
            labels += heights.map { "${it}p" }

            val checkedIndex = manualHeight
                ?.let { selected -> heights.indexOf(selected).takeIf { it >= 0 }?.plus(1) }
                ?: 0

            AlertDialog.Builder(activity)
                .setTitle(R.string.video_quality)
                .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                    dialog.dismiss()
                    if (which == 0) applyAutomaticPolicy() else applyExactHeight(heights[which - 1])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun applyExactHeight(height: Int, persist: Boolean = true) {
            val activePlayer = player ?: return
            val target = options(activePlayer.currentTracks)
                .filter { it.height == height }
                .maxByOrNull { it.bitrate }
                ?: return

            val parameterBuilder = activePlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setOverrideForType(TrackSelectionOverride(target.group.mediaTrackGroup, target.index))

            if (target.width > 0 && target.height > 0) {
                parameterBuilder
                    .setMinVideoSize(target.width, target.height)
                    .setMaxVideoSize(target.width, target.height)
            }

            manualHeight = height
            reappliedAfterGroupRefresh = false
            if (persist) tabId()?.let { VideoTabStore.setManualQuality(it, height) }

            activePlayer.trackSelectionParameters = parameterBuilder.build()

            /* Release old buffered rendition chunks promptly without changing position perceptibly. */
            val position = activePlayer.currentPosition.coerceAtLeast(0L)
            val duration = activePlayer.duration
            val reseek = if (duration > 0L && position + 1L >= duration) position else position + 1L
            activePlayer.seekTo(reseek)
            updateButtonLabel()
        }

        private fun applyAutomaticPolicy() {
            val activePlayer = player ?: return
            val available = options(activePlayer.currentTracks)
            val heights = available.map { it.height }.distinct()
            val preferred = preferredHeight(heights) ?: return
            val group = available.firstOrNull()?.group ?: return

            val indices: List<Int> = if (group.isAdaptiveSupported) {
                available
                    .filter { it.height <= preferred }
                    .map { it.index }
                    .distinct()
                    .ifEmpty { listOf(available.first { it.height == preferred }.index) }
            } else {
                listOf(
                    available.filter { it.height == preferred }
                        .maxByOrNull { it.bitrate }
                        ?.index
                        ?: return
                )
            }

            manualHeight = null
            reappliedAfterGroupRefresh = false
            tabId()?.let { VideoTabStore.setManualQuality(it, null) }

            activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMinVideoSize(0, 0)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, indices))
                .build()

            updateButtonLabel()
        }

        private fun updateButtonLabel() {
            val target = manualHeight
            val actual = actualHeight
            button?.text = when {
                target != null && actual != null && target == actual ->
                    activity.getString(R.string.quality_manual_verified, target)
                target != null && actual != null ->
                    activity.getString(R.string.quality_manual_actual, target, actual)
                target != null ->
                    activity.getString(R.string.quality_manual_waiting, target)
                actual != null ->
                    activity.getString(R.string.quality_auto_actual, actual)
                else -> activity.getString(R.string.quality_auto)
            }
        }

        private fun persistActual(height: Int) {
            tabId()?.let { VideoTabStore.setActualQuality(it, height) }
        }

        private fun tabId(): String? =
            activity.intent.getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
                ?.takeIf { VideoTabStore.get(it) != null }

        private fun options(tracks: Tracks): List<Option> {
            val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val bestGroup = videoGroups.maxByOrNull { group ->
                (0 until group.length).count { index ->
                    group.isTrackSupported(index) && group.getTrackFormat(index).height > 0
                }
            } ?: return emptyList()

            return buildList {
                for (index in 0 until bestGroup.length) {
                    if (!bestGroup.isTrackSupported(index)) continue
                    val format = bestGroup.getTrackFormat(index)
                    if (format.height <= 0) continue
                    add(
                        Option(
                            group = bestGroup,
                            index = index,
                            height = format.height,
                            width = format.width,
                            bitrate = maxOf(format.averageBitrate, format.peakBitrate)
                        )
                    )
                }
            }
        }

        private fun preferredHeight(heights: List<Int>): Int? = when {
            720 in heights -> 720
            1080 in heights -> 1080
            heights.any { it < 1080 } -> heights.filter { it < 1080 }.maxOrNull()
            else -> heights.minOrNull()
        }
    }
}
