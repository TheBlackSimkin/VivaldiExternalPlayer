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
import androidx.media3.ui.PlayerView
import java.util.WeakHashMap

/**
 * Runtime installer for the browser adaptive-quality fix.
 *
 * The existing foreground privacy provider calls [install] during process start,
 * so no extra manifest component is needed.
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
            if (activity is PlayerActivity) {
                controllers.remove(activity)?.detach()
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    /**
     * Intercepts the quality button only when Media3 exposes multiple adaptive
     * video renditions. Direct/yt-dlp and sibling-URL quality switching remain in
     * PlayerActivity exactly as before.
     */
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
        private var reappliedAfterGroupRefresh = false

        fun attach() {
            val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
            player = activePlayer
            button = activity.findViewById(R.id.quality_button)
            activePlayer.addListener(this)
            maybeInstallAdaptiveButton(activePlayer.currentTracks)
        }

        fun detach() {
            player?.removeListener(this)
            player = null
            button = null
        }

        override fun onTracksChanged(tracks: Tracks) {
            maybeInstallAdaptiveButton(tracks)

            val wanted = manualHeight ?: return
            val actual = selectedVideoHeight(tracks)

            if (actual == wanted) {
                reappliedAfterGroupRefresh = false
                button?.text = activity.getString(R.string.quality_value, wanted)
                return
            }

            /*
             * Some live manifests replace their TrackGroup during a refresh.
             * Re-apply once against the new group instead of silently falling
             * back to the old/automatic rendition.
             */
            if (
                !reappliedAfterGroupRefresh &&
                options(tracks).any { it.height == wanted }
            ) {
                reappliedAfterGroupRefresh = true
                activity.window.decorView.postDelayed({
                    applyExactHeight(wanted)
                }, 250L)
            }
        }

        private fun maybeInstallAdaptiveButton(tracks: Tracks) {
            if (adaptiveButtonInstalled) return
            if (options(tracks).map { it.height }.distinct().size <= 1) return

            adaptiveButtonInstalled = true
            button?.setOnClickListener { showAdaptiveDialog() }
        }

        private fun showAdaptiveDialog() {
            val activePlayer = player ?: return
            val available = options(activePlayer.currentTracks)
            val heights = available.map { it.height }.distinct().sortedDescending()
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
                    if (which == 0) {
                        applyAutomaticPolicy()
                    } else {
                        applyExactHeight(heights[which - 1])
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        /**
         * Force one exact adaptive rendition.
         *
         * The single-track override is the primary mechanism. Exact min/max
         * video-size constraints reinforce the choice, and a practically
         * invisible re-seek discards already-buffered chunks from the previous
         * rendition so the visual change happens promptly.
         */
        private fun applyExactHeight(height: Int) {
            val activePlayer = player ?: return
            val target = options(activePlayer.currentTracks)
                .filter { it.height == height }
                .maxByOrNull { it.bitrate }
                ?: return

            val parameterBuilder = activePlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setOverrideForType(
                    TrackSelectionOverride(
                        target.group.mediaTrackGroup,
                        target.index
                    )
                )

            if (target.width > 0 && target.height > 0) {
                parameterBuilder
                    .setMinVideoSize(target.width, target.height)
                    .setMaxVideoSize(target.width, target.height)
            }

            manualHeight = height
            reappliedAfterGroupRefresh = false
            activePlayer.trackSelectionParameters = parameterBuilder.build()

            val position = activePlayer.currentPosition.coerceAtLeast(0L)
            val duration = activePlayer.duration
            val reseekPosition =
                if (duration > 0L && position + 1L >= duration) position else position + 1L
            activePlayer.seekTo(reseekPosition)

            button?.text = activity.getString(R.string.quality_value, height)
        }

        /** Restore the project default 720 -> 1080 -> best-below-1080 policy. */
        private fun applyAutomaticPolicy() {
            val activePlayer = player ?: return
            val available = options(activePlayer.currentTracks)
            val heights = available.map { it.height }.distinct()
            val preferred = preferredHeight(heights) ?: return
            val group = available.firstOrNull()?.group ?: return

            val indices: List<Int> =
                if (group.isAdaptiveSupported) {
                    available
                        .filter { it.height <= preferred }
                        .map { it.index }
                        .distinct()
                        .ifEmpty {
                            listOf(available.first { it.height == preferred }.index)
                        }
                } else {
                    listOf(
                        available
                            .filter { it.height == preferred }
                            .maxByOrNull { it.bitrate }
                            ?.index
                            ?: return
                    )
                }

            manualHeight = null
            reappliedAfterGroupRefresh = false

            activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMinVideoSize(0, 0)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, indices)
                )
                .build()

            button?.text = activity.getString(R.string.quality_auto_limit, preferred)
        }

        private fun options(tracks: Tracks): List<Option> {
            val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val bestGroup = videoGroups.maxByOrNull { group ->
                (0 until group.length).count { index ->
                    group.isTrackSupported(index) &&
                        group.getTrackFormat(index).height > 0
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

        private fun selectedVideoHeight(tracks: Tracks): Int? =
            tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group ->
                    (0 until group.length).asSequence().mapNotNull { index ->
                        if (group.isTrackSelected(index)) {
                            group.getTrackFormat(index).height.takeIf { it > 0 }
                        } else {
                            null
                        }
                    }
                }
                .maxOrNull()

        private fun preferredHeight(heights: List<Int>): Int? = when {
            720 in heights -> 720
            1080 in heights -> 1080
            heights.any { it < 1080 } -> heights.filter { it < 1080 }.maxOrNull()
            else -> heights.minOrNull()
        }
    }
}
