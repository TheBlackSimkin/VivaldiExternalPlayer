package com.example.vivaldiplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.inspector.frame.FrameExtractor
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import com.chaquo.python.Python
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * Media3/ExoPlayer playback screen.
 *
 * Batch 2 has two important goals in this class:
 *
 * 1. Never leave the user with only a mysterious black/empty player. Media3
 *    playback failures are captured and shown as copyable diagnostics.
 *
 * 2. Browser-assisted HLS/DASH streams can now expose their actual video tracks
 *    to the quality button. The automatic browser policy prefers 720p, then
 *    1080p, then the best quality below 1080p, matching the project requirement.
 *
 * This class still does not contain any site-specific adult-content logic.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESOLVED_MEDIA = "resolved_media"
    }

    /** One selectable video track discovered inside an adaptive Media3 source. */
    private data class BrowserVideoTrack(
        val group: Tracks.Group,
        val trackIndex: Int,
        val height: Int,
        val width: Int,
        val bitrate: Int
    )

    private lateinit var playerView: GesturePlayerView
    private lateinit var previewContainer: LinearLayout
    private lateinit var previewImage: ImageView
    private lateinit var previewTime: TextView
    private lateinit var qualityButton: Button
    private lateinit var diagnosticsButton: Button
    private lateinit var playbackStatus: TextView

    private var player: ExoPlayer? = null
    private var frameExtractor: FrameExtractor? = null
    private var frameFuture: ListenableFuture<FrameExtractor.Frame>? = null
    private var lastPreviewPosition = Long.MIN_VALUE

    private var currentResolved: ResolvedMedia? = null
    private var qualityChangeInProgress = false

    /** Last copyable diagnostic report. The button is useful even after a dialog is dismissed. */
    private var lastDiagnostics: String = ""

    /**
     * Browser quality state. A master HLS/DASH source can contain many video
     * tracks. Media3 only knows them after preparation, so these fields are
     * populated from Player.Listener.onTracksChanged.
     */
    private var browserVideoTracks: List<BrowserVideoTrack> = emptyList()
    private var browserAutoPolicyApplied = false
    private var browserAutoTargetHeight: Int? = null
    private var browserManualHeight: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * This app is intended for private playback. FLAG_SECURE prevents the
         * player surface from appearing in screenshots and recent-app previews.
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        previewContainer = findViewById(R.id.preview_container)
        previewImage = findViewById(R.id.preview_image)
        previewTime = findViewById(R.id.preview_time)
        qualityButton = findViewById(R.id.quality_button)
        diagnosticsButton = findViewById(R.id.diagnostics_button)
        playbackStatus = findViewById(R.id.playback_status)

        val json = intent.getStringExtra(EXTRA_RESOLVED_MEDIA) ?: return finish()
        val resolved = ResolvedMedia.fromJson(json)

        title = resolved.title

        createPlayer()
        attachThumbnailScrubbing()

        qualityButton.setOnClickListener {
            showQualityDialog()
        }

        diagnosticsButton.setOnClickListener {
            showDiagnosticsDialog()
        }

        loadResolvedMedia(
            resolved = resolved,
            startPositionMs = 0L,
            playWhenReady = true
        )
    }

    /** Create ExoPlayer once and attach the listeners used throughout the screen. */
    private fun createPlayer() {
        val exoPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setScrubbingModeParameters(
                ScrubbingModeParameters.Builder()
                    .setFractionalSeekTolerance(0.002, 0.002)
                    .build()
            )
            .build()

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    handlePlaybackError(error)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    handleTracksChanged(tracks)
                }
            }
        )

        player = exoPlayer
        playerView.player = exoPlayer
    }

    /**
     * Replace the currently playing source while preserving the same ExoPlayer.
     * This method is used both for initial playback and yt-dlp quality changes.
     */
    private fun loadResolvedMedia(
        resolved: ResolvedMedia,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        val primary = resolved.video ?: resolved.single ?: return
        val primaryMediaSourceFactory = mediaSourceFactoryFor(primary)
        val previewItem = primary.toMediaItem(resolved.title)

        val playbackSource: MediaSource = when (resolved.mode) {
            "merged" -> {
                val audio = requireNotNull(resolved.audio)

                MergingMediaSource(
                    primaryMediaSourceFactory.createMediaSource(previewItem),
                    mediaSourceFactoryFor(audio).createMediaSource(
                        audio.toMediaItem(resolved.title)
                    )
                )
            }

            else -> primaryMediaSourceFactory.createMediaSource(previewItem)
        }

        resetFrameExtractor()
        resetPlaybackUiForNewSource(resolved)

        currentResolved = resolved

        val exoPlayer = player ?: return
        exoPlayer.setMediaSource(playbackSource)

        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady

        /*
         * FrameExtractor uses the primary video source only. For a merged
         * video+audio yt-dlp result, thumbnails do not need the audio source.
         */
        frameExtractor = FrameExtractor.Builder(this, previewItem)
            .setMediaSourceFactory(primaryMediaSourceFactory)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()

        updateQualityButton(resolved)
        updateIdleDiagnostics(resolved)
    }

    /** Reset only source-specific state; keep the ExoPlayer instance itself. */
    private fun resetPlaybackUiForNewSource(resolved: ResolvedMedia) {
        playbackStatus.visibility = View.GONE
        playbackStatus.text = ""
        diagnosticsButton.text = getString(R.string.player_diagnostics)

        browserVideoTracks = emptyList()
        browserAutoPolicyApplied = false
        browserAutoTargetHeight = null
        browserManualHeight = null

        if (resolved.resolverMode == "browser") {
            qualityButton.isEnabled = false
            qualityButton.text = getString(R.string.quality_detecting)
        }
    }

    /**
     * Media3 reports available adaptive tracks only after the source prepares.
     * This is what turns "Quality: stream" into real browser-stream qualities.
     */
    private fun handleTracksChanged(tracks: Tracks) {
        val resolved = currentResolved ?: return

        if (resolved.resolverMode != "browser") {
            return
        }

        browserVideoTracks = collectBrowserVideoTracks(tracks)

        if (browserVideoTracks.isEmpty()) {
            qualityButton.text = getString(R.string.quality_stream)
            qualityButton.isEnabled = false
            return
        }

        if (!browserAutoPolicyApplied) {
            browserAutoPolicyApplied = true
            applyBrowserAutoPolicy()
        } else {
            updateBrowserQualityButton()
        }
    }

    /**
     * Find the most useful Media3 video TrackGroup and expose its supported
     * resolution tracks. HLS master playlists commonly provide exactly such a
     * group, with several heights which Media3 can switch between adaptively.
     */
    private fun collectBrowserVideoTracks(tracks: Tracks): List<BrowserVideoTrack> {
        val videoGroups = tracks.groups.filter { group ->
            group.type == C.TRACK_TYPE_VIDEO
        }

        val bestGroup = videoGroups.maxByOrNull { group ->
            (0 until group.length).count { index ->
                group.isTrackSupported(index) && group.getTrackFormat(index).height > 0
            }
        } ?: return emptyList()

        return buildList {
            for (index in 0 until bestGroup.length) {
                if (!bestGroup.isTrackSupported(index)) {
                    continue
                }

                val format = bestGroup.getTrackFormat(index)
                if (format.height <= 0) {
                    continue
                }

                add(
                    BrowserVideoTrack(
                        group = bestGroup,
                        trackIndex = index,
                        height = format.height,
                        width = format.width,
                        bitrate = maxOf(format.averageBitrate, format.peakBitrate)
                    )
                )
            }
        }
    }

    /** Apply the project's 720 -> 1080 -> best-below-1080 policy to browser tracks. */
    private fun applyBrowserAutoPolicy() {
        val heights = browserVideoTracks
            .map { it.height }
            .distinct()

        if (heights.isEmpty()) {
            updateBrowserQualityButton()
            return
        }

        val target = when {
            720 in heights -> 720
            1080 in heights -> 1080
            heights.any { it < 1080 } -> heights.filter { it < 1080 }.maxOrNull()

            /*
             * Rare safety fallback: if the manifest only contains resolutions
             * above 1080p, choose its smallest supported track rather than make
             * the video completely unplayable.
             */
            else -> heights.minOrNull()
        } ?: return

        browserAutoTargetHeight = target
        browserManualHeight = null
        applyBrowserTrackOverride(targetHeight = target, adaptive = true)
        updateBrowserQualityButton()
    }

    /**
     * Select browser video tracks.
     *
     * Auto mode keeps all supported renditions up to the preferred target when
     * the TrackGroup is adaptive. This gives Media3 room to drop temporarily to
     * a lower rendition when bandwidth is poor, preserving efficient buffering.
     *
     * A manual quality choice selects one exact rendition instead.
     */
    private fun applyBrowserTrackOverride(targetHeight: Int, adaptive: Boolean) {
        val exoPlayer = player ?: return
        val group = browserVideoTracks.firstOrNull()?.group ?: return

        val selectedIndices: List<Int> = if (adaptive && group.isAdaptiveSupported) {
            val atOrBelowTarget = browserVideoTracks
                .filter { track -> track.height <= targetHeight }
                .map { track -> track.trackIndex }
                .distinct()

            if (atOrBelowTarget.isNotEmpty()) {
                atOrBelowTarget
            } else {
                listOf(bestTrackForHeight(targetHeight)?.trackIndex ?: return)
            }
        } else {
            listOf(bestTrackForHeight(targetHeight)?.trackIndex ?: return)
        }

        val override = TrackSelectionOverride(
            group.mediaTrackGroup,
            selectedIndices
        )

        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setOverrideForType(override)
            .build()
    }

    /** When a height has several bitrates/codecs, prefer the highest bitrate rendition. */
    private fun bestTrackForHeight(height: Int): BrowserVideoTrack? =
        browserVideoTracks
            .filter { track -> track.height == height }
            .maxByOrNull { track -> track.bitrate }

    /** Main quality button dispatcher: browser tracks and yt-dlp use different mechanisms. */
    private fun showQualityDialog() {
        if (qualityChangeInProgress) {
            return
        }

        val resolved = currentResolved ?: return

        if (resolved.resolverMode == "browser") {
            showBrowserQualityDialog()
        } else {
            showYtDlpQualityDialog()
        }
    }

    /**
     * Browser streams already contain their alternative tracks inside Media3,
     * so changing quality does NOT send the webpage back through yt-dlp.
     */
    private fun showBrowserQualityDialog() {
        val heights = browserVideoTracks
            .map { it.height }
            .distinct()
            .sortedDescending()

        if (heights.isEmpty()) {
            Toast.makeText(
                this,
                R.string.browser_quality_unavailable,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val labels = mutableListOf(getString(R.string.quality_auto_prefer_720))
        labels += heights.map { height -> "${height}p" }

        val checkedIndex = browserManualHeight
            ?.let { selected -> heights.indexOf(selected).takeIf { it >= 0 }?.plus(1) }
            ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.video_quality)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()

                if (which == 0) {
                    applyBrowserAutoPolicy()
                    return@setSingleChoiceItems
                }

                val selectedHeight = heights[which - 1]
                browserManualHeight = selectedHeight
                browserAutoTargetHeight = null
                applyBrowserTrackOverride(selectedHeight, adaptive = false)
                updateBrowserQualityButton()

                Toast.makeText(
                    this,
                    getString(R.string.playing_quality, selectedHeight),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Existing yt-dlp quality picker, now fully localized. */
    private fun showYtDlpQualityDialog() {
        val resolved = currentResolved ?: return

        val keys = arrayOf("auto", "1080", "720", "480", "360")
        val labels = arrayOf(
            getString(R.string.quality_auto_prefer_720),
            "1080p",
            "720p",
            "480p",
            "360p"
        )

        val currentKey = resolved.requestedQuality
        val checkedIndex = keys.indexOf(currentKey).let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
            .setTitle(R.string.video_quality)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()

                val selectedKey = keys[which]
                if (selectedKey != currentKey) {
                    changeYtDlpQuality(selectedKey)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Re-run yt-dlp for a manually selected direct-resolver quality. */
    private fun changeYtDlpQuality(quality: String) {
        val resolved = currentResolved ?: return
        if (resolved.resolverMode != "ytdlp") {
            return
        }

        val webpageUrl = resolved.webpageUrl
        if (webpageUrl.isBlank()) {
            Toast.makeText(
                this,
                R.string.original_webpage_unavailable,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val exoPlayer = player ?: return
        val resumePosition = exoPlayer.currentPosition
        val shouldResume = exoPlayer.playWhenReady

        qualityChangeInProgress = true
        qualityButton.isEnabled = false
        qualityButton.text = getString(R.string.quality_loading)

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Python.getInstance()
                        .getModule("resolver")
                        .callAttr("resolve", webpageUrl, quality)
                        .toString()
                }
            }.onSuccess { json ->
                val newResolved = ResolvedMedia.fromJson(json)
                qualityChangeInProgress = false

                loadResolvedMedia(
                    resolved = newResolved,
                    startPositionMs = resumePosition,
                    playWhenReady = shouldResume
                )

                val actual = newResolved.displayedHeight
                val message = if (actual != null && actual > 0) {
                    getString(R.string.playing_quality, actual)
                } else {
                    getString(R.string.quality_changed)
                }

                Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                qualityChangeInProgress = false
                updateQualityButton(resolved)

                Toast.makeText(
                    this@PlayerActivity,
                    error.message ?: getString(R.string.quality_change_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Update the top-right quality button for either resolver mode. */
    private fun updateQualityButton(resolved: ResolvedMedia) {
        if (resolved.resolverMode == "browser") {
            updateBrowserQualityButton()
            return
        }

        qualityButton.isEnabled = !qualityChangeInProgress

        val height = resolved.displayedHeight
        qualityButton.text = if (height != null && height > 0) {
            getString(R.string.quality_value, height)
        } else {
            when (resolved.requestedQuality) {
                "1080", "720", "480", "360" ->
                    getString(R.string.quality_value, resolved.requestedQuality.toInt())

                else -> getString(R.string.quality_auto)
            }
        }
    }

    /** Browser quality UI after Media3 has parsed the manifest/track list. */
    private fun updateBrowserQualityButton() {
        if (browserVideoTracks.isEmpty()) {
            qualityButton.text = getString(R.string.quality_stream)
            qualityButton.isEnabled = false
            return
        }

        val distinctHeights = browserVideoTracks.map { it.height }.distinct()
        qualityButton.isEnabled = distinctHeights.size > 1

        qualityButton.text = when {
            browserManualHeight != null ->
                getString(R.string.quality_value, browserManualHeight!!)

            browserAutoTargetHeight != null ->
                getString(R.string.quality_auto_limit, browserAutoTargetHeight!!)

            distinctHeights.size == 1 ->
                getString(R.string.quality_value, distinctHeights.first())

            else -> getString(R.string.quality_auto)
        }
    }

    /**
     * Convert a Media3 playback failure into a useful report rather than leaving
     * the user staring at an empty player. Android's official Media3 guidance
     * recommends Player.Listener.onPlayerError for this purpose.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        val report = buildPlaybackDiagnostics(error)
        lastDiagnostics = report

        playbackStatus.text = getString(R.string.playback_failed_tap_diagnostics)
        playbackStatus.visibility = View.VISIBLE
        diagnosticsButton.text = getString(R.string.playback_error_button)

        // During development an automatic dialog saves several taps and makes failures obvious.
        showDiagnosticsDialog()
    }

    /** Build a privacy-conscious technical report. URL query tokens are intentionally omitted. */
    private fun buildPlaybackDiagnostics(error: PlaybackException): String {
        val resolved = currentResolved
        val source = resolved?.primarySource
        val uri = source?.url?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val httpError = findCause<HttpDataSource.InvalidResponseCodeException>(error)

        return buildString {
            appendLine(getString(R.string.diagnostics_title))
            appendLine("------------------------------")
            appendLine("Resolver: ${resolved?.resolverMode ?: "unknown"}")
            appendLine("Mode: ${resolved?.mode ?: "unknown"}")
            appendLine("Source host: ${uri?.host ?: "unknown"}")
            appendLine("Source path: ${uri?.path ?: "unknown"}")
            appendLine("MIME: ${source?.mimeType ?: "unknown"}")
            appendLine("Protocol: ${source?.protocol ?: "unknown"}")
            appendLine("Extension: ${source?.extension ?: "unknown"}")
            appendLine("Container: ${source?.container ?: "unknown"}")
            appendLine("Format ID: ${source?.formatId ?: "unknown"}")
            appendLine("Video codec: ${source?.videoCodec ?: "unknown"}")
            appendLine("Audio codec: ${source?.audioCodec ?: "unknown"}")
            appendLine("Declared size: ${source?.width ?: "?"}x${source?.height ?: "?"}")
            appendLine()
            appendLine("Media3 error: ${error.errorCodeName} (${error.errorCode})")
            appendLine("Message: ${error.message ?: "none"}")

            if (httpError != null) {
                appendLine("HTTP status: ${httpError.responseCode}")
                appendLine("HTTP message: ${httpError.responseMessage ?: "none"}")
            }

            appendLine()
            appendLine("Cause chain:")

            var cause: Throwable? = error
            var depth = 0
            while (cause != null && depth < 8) {
                appendLine("$depth. ${cause.javaClass.simpleName}: ${cause.message ?: ""}")
                cause = cause.cause
                depth += 1
            }
        }.trim()
    }

    /**
     * When there is no failure yet, Diagnostics still shows what source the app
     * received. This is useful for a working HH/Bitmovin stream too.
     */
    private fun updateIdleDiagnostics(resolved: ResolvedMedia) {
        val source = resolved.primarySource
        val uri = source?.url?.let { runCatching { Uri.parse(it) }.getOrNull() }

        lastDiagnostics = buildString {
            appendLine(getString(R.string.diagnostics_title))
            appendLine("------------------------------")
            appendLine("Status: waiting for playback result")
            appendLine("Resolver: ${resolved.resolverMode}")
            appendLine("Mode: ${resolved.mode}")
            appendLine("Source host: ${uri?.host ?: "unknown"}")
            appendLine("Source path: ${uri?.path ?: "unknown"}")
            appendLine("MIME: ${source?.mimeType ?: "unknown"}")
            appendLine("Protocol: ${source?.protocol ?: "unknown"}")
            appendLine("Extension: ${source?.extension ?: "unknown"}")
            appendLine("Container: ${source?.container ?: "unknown"}")
            appendLine("Format ID: ${source?.formatId ?: "unknown"}")
            appendLine("Video codec: ${source?.videoCodec ?: "unknown"}")
            appendLine("Audio codec: ${source?.audioCodec ?: "unknown"}")
            appendLine("Declared size: ${source?.width ?: "?"}x${source?.height ?: "?"}")
        }.trim()
    }

    /** Show the diagnostic report in selectable text with a one-tap copy action. */
    private fun showDiagnosticsDialog() {
        if (lastDiagnostics.isBlank()) {
            lastDiagnostics = getString(R.string.no_diagnostics_yet)
        }

        val textView = TextView(this).apply {
            text = lastDiagnostics
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics_title)
            .setView(textView)
            .setPositiveButton(R.string.copy_diagnostics) { _, _ ->
                copyDiagnosticsToClipboard()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Copy only the generated diagnostic text; media cookies/request headers are not included. */
    private fun copyDiagnosticsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.diagnostics_title),
                lastDiagnostics
            )
        )

        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }

    /** Find a specific nested exception type anywhere in a Media3 cause chain. */
    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error

        while (current != null) {
            if (current is T) {
                return current
            }
            current = current.cause
        }

        return null
    }

    /** Attach the existing frame-preview implementation to Media3's time bar. */
    private fun attachThumbnailScrubbing() {
        val timeBar = playerView.findViewById<DefaultTimeBar>(
            androidx.media3.ui.R.id.exo_progress
        ) ?: return

        timeBar.addListener(
            object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    previewContainer.visibility = View.VISIBLE
                    requestPreview(position, force = true)
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    requestPreview(position, force = false)
                }

                override fun onScrubStop(
                    timeBar: TimeBar,
                    position: Long,
                    canceled: Boolean
                ) {
                    frameFuture?.cancel(false)
                    previewContainer.visibility = View.GONE
                }
            }
        )
    }

    /** Request a nearby frame, with light throttling while the finger is moving. */
    private fun requestPreview(position: Long, force: Boolean) {
        previewTime.text = formatTime(position)

        if (!force && abs(position - lastPreviewPosition) < 600L) {
            return
        }

        lastPreviewPosition = position
        frameFuture?.cancel(false)

        val request = frameExtractor?.getFrame(position) ?: return
        frameFuture = request

        request.addListener(
            {
                if (request.isCancelled || frameFuture !== request) {
                    return@addListener
                }

                runCatching {
                    request.get()
                }.onSuccess { frame ->
                    previewImage.setImageBitmap(frame.bitmap)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    /** Release frame-preview resources before loading another source or closing the Activity. */
    private fun resetFrameExtractor() {
        frameFuture?.cancel(true)
        frameFuture = null

        frameExtractor?.close()
        frameExtractor = null

        lastPreviewPosition = Long.MIN_VALUE
        previewImage.setImageDrawable(null)
        previewContainer.visibility = View.GONE
    }

    /** Build a Media3 factory with the exact request headers supplied by the resolver. */
    private fun mediaSourceFactoryFor(source: StreamSource): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(source.headers)

        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)
    }

    /** Convert one resolver source into Media3's MediaItem model. */
    private fun StreamSource.toMediaItem(title: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )

        mimeType?.let { value ->
            builder.setMimeType(normalizeMimeType(value))
        }

        return builder.build()
    }

    /** Normalize resolver spelling to the constants expected by Media3. */
    private fun normalizeMimeType(value: String): String = when (value) {
        "application/x-mpegURL" -> MimeTypes.APPLICATION_M3U8
        "application/dash+xml" -> MimeTypes.APPLICATION_MPD
        else -> value
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        resetFrameExtractor()

        playerView.player = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
