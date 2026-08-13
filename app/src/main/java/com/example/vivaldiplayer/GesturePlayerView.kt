package com.example.vivaldiplayer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * Project-specific PlayerView.
 *
 * It keeps the validated double-tap seek behavior and now also owns the small,
 * presentation-only loading overlay used by the normal player screen.
 *
 * Keeping this UI state here is intentionally low risk: PlayerActivity still
 * decides which source Media3 receives, and the resolver/candidate-selection
 * logic is completely untouched.
 */
class GesturePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private val loadingText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, 0)
    }

    private val loadingOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.argb(210, 0, 0, 0))
        isClickable = false
        isFocusable = false

        addView(
            ProgressBar(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            loadingText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /**
     * Observe only Media3 playback state. "Buffering…" is therefore never a
     * guessed network message: it appears only for Player.STATE_BUFFERING.
     */
    private val loadingListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateLoadingState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            // PlayerActivity owns the real error/diagnostics UI.
            loadingOverlay.visibility = View.GONE
        }
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val activePlayer = player ?: return false
                if (e.x < width / 2f) activePlayer.seekBack() else activePlayer.seekForward()
                showController()
                return true
            }
        }
    )

    init {
        /*
         * PlayerView is a FrameLayout, so this programmatic child sits above the
         * video surface without requiring any changes to the validated player XML.
         */
        addView(
            loadingOverlay,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
        showOpening()
    }

    override fun setPlayer(player: Player?) {
        // Avoid leaking listeners when PlayerActivity releases/replaces its player.
        this.player?.removeListener(loadingListener)
        super.setPlayer(player)
        player?.addListener(loadingListener)
        updateLoadingState(player)
    }

    private fun updateLoadingState(activePlayer: Player?) {
        if (activePlayer == null || activePlayer.playerError != null) {
            loadingOverlay.visibility = View.GONE
            return
        }

        when (activePlayer.playbackState) {
            Player.STATE_BUFFERING -> showBuffering()
            Player.STATE_READY,
            Player.STATE_ENDED -> loadingOverlay.visibility = View.GONE
            else -> showOpening()
        }
    }

    private fun showOpening() {
        loadingText.setText(R.string.opening_video)
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun showBuffering() {
        loadingText.setText(R.string.playback_buffering)
        loadingOverlay.visibility = View.VISIBLE
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
