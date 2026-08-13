package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
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
 * Fixes manual quality selection for adaptive browser streams.
 *
 * Build #109 could display the correct HLS/DASH quality list while Media3 kept
 * playing the previous rendition. This helper changes only track selection. It
 * does not change resolver candidates, ranking, URLs, or playback architecture.
 */
class AdaptiveQualityFixProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(AdaptiveQualityLifecycle)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null
}

private object AdaptiveQualityLifecycle : Application.ActivityLifecycleCallbacks {
    private val controllers = WeakHashMap<PlayerActivity, AdaptiveQualityController>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is PlayerActivity) return
        activity.window.decorView.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                controllers.getOrPut(activity) { AdaptiveQualityController(activity) }.attach()
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

private class AdaptiveQualityController(
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
    private var installed = false
    private var manualHeight: Int? = null
    private var reapplied = false

    fun attach() {
        val activePlayer = activity.findViewById<PlayerView>(R.id.player_view)?.player ?: return
        player = activePlayer
        button = activity.findViewById(R.id.quality_button)
        activePlayer.addListener(this)

        // Tracks may already be available by the time this lifecycle helper attaches.
        installIfAdaptive(activePlayer.currentTracks)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        button = null
    }

    override fun onTracksChanged(tracks: Tracks) {
        installIfAdaptive(tracks)

        val wanted = manualHeight ?: return
        val actual = selectedHeight(tracks)

        if (actual == wanted) {
            reapplied = false
            button?.text = activity.getString(R.string.quality_value, wanted)
        } else if (!reapplied && options(tracks).any { it.height == wanted }) {
            // Some live manifests refresh TrackGroup objects during the change.
            reapplied = true
            activity.window.decorView.postDelayed({
                applyExact(wanted)
            }, 250L)
        }
    }

    private fun installIfAdaptive(tracks: Tracks) {
        if (installed) return
        if (options(tracks).map { it.height }.distinct().size <= 1) return

        installed = true
        button?.setOnClickListener { showDialog() }
    }

    private fun showDialog() {
        val activePlayer = player ?: return
        val available = options(activePlayer.currentTracks)
        val heights = available.map { it.height }.distinct().sortedDescending()

        if (heights.size <= 1) {
            // If the source later stopped being adaptive, fall back to PlayerActivity's normal dialog.
            runCatching {
                PlayerActivity::class.java
                    .getDeclaredMethod("showQualityDialog")
                    .apply { isAccessible = true }
                    .invoke(activity)
            }
            return
        }

        val labels = mutableListOf(activity.getString(R.string.quality_auto_prefer_720))
        labels += heights.map { "${it}p" }

        val checked = manualHeight
            ?.let { heights.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
            ?: 0

        AlertDialog.Builder(activity)
            .setTitle(R.string.video_quality)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                if (which == 0) applyAuto() else applyExact(heights[which - 1])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Force one exact adaptive rendition, rather than merely updating the menu state. */
    private fun applyExact(height: Int) {
        val activePlayer = player ?: return
        val target = options(activePlayer.currentTracks)
            .filter { it.height == height }
            .maxByOrNull { it.bitrate }
            ?: return

        val builder = activePlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setOverrideForType(
                TrackSelectionOverride(target.group.mediaTrackGroup, target.index)
            )

        // Exact size constraints reinforce the single-track override.
        if (target.width > 0 && target.height > 0) {
            builder
                .setMinVideoSize(target.width, target.height)
                .setMaxVideoSize(target.width, target.height)
        }

        manualHeight = height
        reapplied = false
        activePlayer.trackSelectionParameters = builder.build()

        // A near-zero reseek makes already-buffered old-rendition chunks release promptly.
        val position = activePlayer.currentPosition.coerceAtLeast(0L)
        val duration = activePlayer.duration
        val targetPosition = if (duration > 0L && position + 1L >= duration) position else position + 1L
        activePlayer.seekTo(targetPosition)

        button?.text = activity.getString(R.string.quality_value, height)
    }

    /** Restore the project's 720 -> 1080 -> best-below-1080 automatic policy. */
    private fun applyAuto() {
        val activePlayer = player ?: return
        val available = options(activePlayer.currentTracks)
        val heights = available.map { it.height }.distinct()
        val preferred = preferredHeight(heights) ?: return
        val group = available.firstOrNull()?.group ?: return

        val indices = if (group.isAdaptiveSupported) {
            available
                .filter { it.height <= preferred }
                .map { it.index }
                .distinct()
                .ifEmpty {
                    listOf(available.first { it.height == preferred }.index)
                }
        } else {
            listOf(
                available.filter { it.height == preferred }
                    .maxByOrNull { it.bitrate }
                    ?.index
                    ?: return
            )
        }

        manualHeight = null
        reapplied = false

        activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMinVideoSize(0, 0)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, indices))
            .build()

        button?.text = activity.getString(R.string.quality_auto_limit, preferred)
    }

    private fun options(tracks: Tracks): List<Option> {
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val best = videoGroups.maxByOrNull { group ->
            (0 until group.length).count { index ->
                group.isTrackSupported(index) && group.getTrackFormat(index).height > 0
            }
        } ?: return emptyList()

        return buildList {
            for (index in 0 until best.length) {
                if (!best.isTrackSupported(index)) continue
                val format = best.getTrackFormat(index)
                if (format.height <= 0) continue
                add(
                    Option(
                        group = best,
                        index = index,
                        height = format.height,
                        width = format.width,
                        bitrate = maxOf(format.averageBitrate, format.peakBitrate)
                    )
                )
            }
        }
    }

    private fun selectedHeight(tracks: Tracks): Int? =
        tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).asSequence().mapNotNull { index ->
                    if (group.isTrackSelected(index)) {
                        group.getTrackFormat(index).height.takeIf { it > 0 }
                    } else null
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
