package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Explicit Android share target for "Add to External Player in background".
 *
 * This Activity exists only for the hand-off from Vivaldi/Android Sharesheet.
 * It creates the persistent tab, launches the invisible preparation Activity in
 * its own excluded-from-recents task, then removes itself immediately.
 *
 * No playback object is created here.
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

        startActivity(
            Intent(this, BackgroundPreparationActivity::class.java)
                .putExtra(BackgroundPreparationActivity.EXTRA_URL, url)
                .putExtra(BackgroundPreparationActivity.EXTRA_TAB_ID, tab.id)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )

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

    /** Browsers may share "page title + URL", so extract the first HTTP(S) URL. */
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
