package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Explicit Android share target for BG - External Player.
 *
 * It creates the persistent tab and hands it to the same unified preparation
 * coordinator used by preload, retry and restart recovery. No playback object is
 * ever created here.
 */
class BackgroundShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(sharedIntent: Intent) {
        VideoTabStore.initialize(applicationContext)

        val url = extractSharedUrl(sharedIntent)
        if (url == null) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            closeShareTask()
            return
        }

        val tab = VideoTabStore.createPendingTab(url)
        UnifiedPreparationCoordinator.startFromShare(this, tab.id)

        Toast.makeText(
            applicationContext,
            R.string.added_to_external_player,
            Toast.LENGTH_SHORT
        ).show()

        closeShareTask()
    }

    private fun closeShareTask() {
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    private fun extractSharedUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null

        return Regex("https?://\\S+")
            .find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
            ?.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
            }
    }
}
