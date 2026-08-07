package com.example.vivaldiplayer

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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESOLVED_MEDIA =
            "resolved_media"
    }

    private lateinit var playerView:
        GesturePlayerView

    private lateinit var previewContainer:
        LinearLayout

    private lateinit var previewImage:
        ImageView

    private lateinit var previewTime:
        TextView

    private lateinit var qualityButton:
        Button

    private var player:
        ExoPlayer? = null

    private var frameExtractor:
        FrameExtractor? = null

    private var frameFuture:
        ListenableFuture<FrameExtractor.Frame>? = null

    private var lastPreviewPosition =
        Long.MIN_VALUE

    private var currentResolved:
        ResolvedMedia? = null

    private var qualityChangeInProgress =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(
            R.layout.activity_player
        )

        playerView =
            findViewById(
                R.id.player_view
            )

        previewContainer =
            findViewById(
                R.id.preview_container
            )

        previewImage =
            findViewById(
                R.id.preview_image
            )

        previewTime =
            findViewById(
                R.id.preview_time
            )

        qualityButton =
            findViewById(
                R.id.quality_button
            )

        val json =
            intent.getStringExtra(
                EXTRA_RESOLVED_MEDIA
            ) ?: return finish()

        val resolved =
            ResolvedMedia.fromJson(json)

        title = resolved.title

        createPlayer()

        attachThumbnailScrubbing()

        qualityButton.setOnClickListener {
            showQualityDialog()
        }

        loadResolvedMedia(
            resolved = resolved,
            startPositionMs = 0L,
            playWhenReady = true
        )
    }

    private fun createPlayer() {

        val exoPlayer =
            ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(
                    10_000
                )
                .setSeekForwardIncrementMs(
                    10_000
                )
                .setScrubbingModeParameters(
                    ScrubbingModeParameters
                        .Builder()
                        .setFractionalSeekTolerance(
                            0.002,
                            0.002
                        )
                        .build()
                )
                .build()

        player =
            exoPlayer

        playerView.player =
            exoPlayer
    }

    private fun loadResolvedMedia(
        resolved: ResolvedMedia,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {

        val primary =
            resolved.video
                ?: resolved.single
                ?: return

        val primaryMediaSourceFactory =
            mediaSourceFactoryFor(
                primary
            )

        val previewItem =
            primary.toMediaItem(
                resolved.title
            )

        val playbackSource:
            MediaSource =
            when (resolved.mode) {

                "merged" -> {

                    val audio =
                        requireNotNull(
                            resolved.audio
                        )

                    MergingMediaSource(
                        primaryMediaSourceFactory
                            .createMediaSource(
                                previewItem
                            ),
                        mediaSourceFactoryFor(
                            audio
                        ).createMediaSource(
                            audio.toMediaItem(
                                resolved.title
                            )
                        )
                    )
                }

                else -> {
                    primaryMediaSourceFactory
                        .createMediaSource(
                            previewItem
                        )
                }
            }

        resetFrameExtractor()

        val exoPlayer =
            player ?: return

        exoPlayer.setMediaSource(
            playbackSource
        )

        if (startPositionMs > 0L) {
            exoPlayer.seekTo(
                startPositionMs
            )
        }

        exoPlayer.prepare()

        exoPlayer.playWhenReady =
            playWhenReady

        frameExtractor =
            FrameExtractor
                .Builder(
                    this,
                    previewItem
                )
                .setMediaSourceFactory(
                    primaryMediaSourceFactory
                )
                .setSeekParameters(
                    SeekParameters.CLOSEST_SYNC
                )
                .build()

        currentResolved =
            resolved

        updateQualityButton(
            resolved
        )
    }

    private fun showQualityDialog() {

        if (qualityChangeInProgress) {
            return
        }

        val keys =
            arrayOf(
                "auto",
                "1080",
                "720",
                "480",
                "360"
            )

        val labels =
            arrayOf(
                "Auto — prefer 720p",
                "1080p",
                "720p",
                "480p",
                "360p"
            )

        val currentKey =
            currentResolved
                ?.requestedQuality
                ?: "auto"

        val checkedIndex =
            keys.indexOf(
                currentKey
            ).let {
                if (it >= 0) {
                    it
                } else {
                    0
                }
            }

        AlertDialog
            .Builder(this)
            .setTitle(
                "Video quality"
            )
            .setSingleChoiceItems(
                labels,
                checkedIndex
            ) { dialog, which ->

                dialog.dismiss()

                val selectedKey =
                    keys[which]

                if (
                    selectedKey !=
                    currentKey
                ) {
                    changeQuality(
                        selectedKey
                    )
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun changeQuality(
        quality: String
    ) {

        val resolved =
            currentResolved
                ?: return

        val webpageUrl =
            resolved.webpageUrl

        if (webpageUrl.isBlank()) {

            Toast.makeText(
                this,
                "The original webpage URL is unavailable.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val exoPlayer =
            player ?: return

        val resumePosition =
            exoPlayer.currentPosition

        val shouldResume =
            exoPlayer.playWhenReady

        qualityChangeInProgress =
            true

        qualityButton.isEnabled =
            false

        qualityButton.text =
            "Quality: loading…"

        lifecycleScope.launch {

            runCatching {

                withContext(
                    Dispatchers.IO
                ) {
                    Python
                        .getInstance()
                        .getModule(
                            "resolver"
                        )
                        .callAttr(
                            "resolve",
                            webpageUrl,
                            quality
                        )
                        .toString()
                }

            }.onSuccess { json ->

                val newResolved =
                    ResolvedMedia
                        .fromJson(
                            json
                        )

                loadResolvedMedia(
                    resolved =
                        newResolved,
                    startPositionMs =
                        resumePosition,
                    playWhenReady =
                        shouldResume
                )

                qualityChangeInProgress =
                    false

                qualityButton.isEnabled =
                    true

                val actual =
                    newResolved
                        .displayedHeight

                val message =
                    if (
                        actual != null &&
                        actual > 0
                    ) {
                        "Playing ${actual}p"
                    } else {
                        "Quality changed"
                    }

                Toast.makeText(
                    this@PlayerActivity,
                    message,
                    Toast.LENGTH_SHORT
                ).show()

            }.onFailure { error ->

                qualityChangeInProgress =
                    false

                qualityButton.isEnabled =
                    true

                updateQualityButton(
                    resolved
                )

                Toast.makeText(
                    this@PlayerActivity,
                    error.message
                        ?: "Could not change quality",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateQualityButton(
        resolved: ResolvedMedia
    ) {

        val height =
            resolved.displayedHeight

        qualityButton.text =
            if (
                height != null &&
                height > 0
            ) {
                "Quality: ${height}p"
            } else {

                when (
                    resolved.requestedQuality
                ) {
                    "1080",
                    "720",
                    "480",
                    "360" -> {
                        "Quality: ${resolved.requestedQuality}p"
                    }

                    else -> {
                        "Quality: Auto"
                    }
                }
            }
    }

    private fun attachThumbnailScrubbing() {

        val timeBar =
            playerView.findViewById<
                DefaultTimeBar
            >(
                androidx.media3.ui.R.id.exo_progress
            ) ?: return

        timeBar.addListener(
            object :
                TimeBar.OnScrubListener {

                override fun onScrubStart(
                    timeBar: TimeBar,
                    position: Long
                ) {

                    previewContainer.visibility =
                        View.VISIBLE

                    requestPreview(
                        position,
                        force = true
                    )
                }

                override fun onScrubMove(
                    timeBar: TimeBar,
                    position: Long
                ) {

                    requestPreview(
                        position,
                        force = false
                    )
                }

                override fun onScrubStop(
                    timeBar: TimeBar,
                    position: Long,
                    canceled: Boolean
                ) {

                    frameFuture
                        ?.cancel(false)

                    previewContainer.visibility =
                        View.GONE
                }
            }
        )
    }

    private fun requestPreview(
        position: Long,
        force: Boolean
    ) {

        previewTime.text =
            formatTime(position)

        if (
            !force &&
            abs(
                position -
                lastPreviewPosition
            ) < 600L
        ) {
            return
        }

        lastPreviewPosition =
            position

        frameFuture
            ?.cancel(false)

        val request =
            frameExtractor
                ?.getFrame(
                    position
                )
                ?: return

        frameFuture =
            request

        request.addListener(
            {

                if (
                    request.isCancelled ||
                    frameFuture !== request
                ) {
                    return@addListener
                }

                runCatching {
                    request.get()
                }.onSuccess { frame ->

                    previewImage
                        .setImageBitmap(
                            frame.bitmap
                        )
                }

            },
            ContextCompat
                .getMainExecutor(
                    this
                )
        )
    }

    private fun resetFrameExtractor() {

        frameFuture
            ?.cancel(true)

        frameFuture =
            null

        frameExtractor
            ?.close()

        frameExtractor =
            null

        lastPreviewPosition =
            Long.MIN_VALUE

        previewImage
            .setImageDrawable(
                null
            )

        previewContainer.visibility =
            View.GONE
    }

    private fun mediaSourceFactoryFor(
        source: StreamSource
    ): DefaultMediaSourceFactory {

        val httpFactory =
            DefaultHttpDataSource
                .Factory()
                .setAllowCrossProtocolRedirects(
                    true
                )
                .setDefaultRequestProperties(
                    source.headers
                )

        return DefaultMediaSourceFactory(
            this
        ).setDataSourceFactory(
            httpFactory
        )
    }

    private fun StreamSource.toMediaItem(
        title: String
    ): MediaItem {

        val builder =
            MediaItem
                .Builder()
                .setUri(url)
                .setMediaId(url)
                .setMediaMetadata(
                    androidx.media3.common
                        .MediaMetadata
                        .Builder()
                        .setTitle(
                            title
                        )
                        .build()
                )

        mimeType?.let {
            builder.setMimeType(
                normalizeMimeType(it)
            )
        }

        return builder.build()
    }

    private fun normalizeMimeType(
        value: String
    ): String =
        when (value) {

            "application/x-mpegURL" ->
                MimeTypes.APPLICATION_M3U8

            "application/dash+xml" ->
                MimeTypes.APPLICATION_MPD

            else ->
                value
        }

    private fun formatTime(
        ms: Long
    ): String {

        val totalSeconds =
            ms.coerceAtLeast(0) /
            1000

        val seconds =
            totalSeconds % 60

        val minutes =
            (totalSeconds / 60) % 60

        val hours =
            totalSeconds / 3600

        return if (hours > 0) {

            String.format(
                Locale.US,
                "%d:%02d:%02d",
                hours,
                minutes,
                seconds
            )

        } else {

            String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }

    override fun onDestroy() {

        resetFrameExtractor()

        playerView.player =
            null

        player
            ?.release()

        player =
            null

        super.onDestroy()
    }
}
