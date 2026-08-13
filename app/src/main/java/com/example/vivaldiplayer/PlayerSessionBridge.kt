package com.example.vivaldiplayer

import androidx.media3.ui.PlayerView

/** Snapshot consumed by the persistent tab coordinator. */
data class PlayerSessionSnapshot(
    val resolvedMediaJson: String,
    val positionMs: Long,
    val playWhenForeground: Boolean
)

/** Restore a persistent tab without resolving it again. */
fun PlayerActivity.restoreTabSession(positionMs: Long, playWhenForeground: Boolean) {
    val player = findViewById<PlayerView>(R.id.player_view)?.player ?: return
    if (positionMs > 0L) player.seekTo(positionMs)
    player.playWhenReady = playWhenForeground
}

/**
 * Read the current player state for persistence.
 *
 * When Android is actually taking this Activity out of the foreground, window
 * focus is already gone. In that case we capture the user's play intention first
 * and then pause Media3 immediately, so no audio/video continues behind Vivaldi,
 * another app, or the lock screen.
 */
fun PlayerActivity.tabSessionSnapshot(): PlayerSessionSnapshot {
    val player = findViewById<PlayerView>(R.id.player_view)?.player
    val desiredPlayState = player?.playWhenReady ?: false
    val position = player?.currentPosition ?: 0L

    val resolvedJson = runCatching {
        val field = PlayerActivity::class.java.getDeclaredField("currentResolved")
        field.isAccessible = true
        (field.get(this) as? ResolvedMedia)?.toJson().orEmpty()
    }.getOrDefault("")

    if (!hasWindowFocus()) {
        player?.pause()
    }

    return PlayerSessionSnapshot(
        resolvedMediaJson = resolvedJson,
        positionMs = position,
        playWhenForeground = desiredPlayState
    )
}
