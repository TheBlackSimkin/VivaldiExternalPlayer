package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Explicit Android share target for BG - External Player.
 *
 * Expected UX:
 * 1. create the persistent tab immediately;
 * 2. establish BackgroundPreparationActivity inside this already-isolated task;
 * 3. finish only this transparent share Activity;
 * 4. BackgroundPreparationActivity moves the tiny task behind Vivaldi and keeps
 *    preparing without creating ExoPlayer or starting playback.
 *
 * The short post below is intentional. Build #162 removed the task immediately
 * after startActivity(), which could race Android's Activity launch and leave the
 * new tab permanently QUEUED until the user opened ExternalPlayer manually.
 */
class BackgroundShareActivity : Activity() {

    companion object {
        private const val HANDOFF_GRACE_MS = 120L
    }

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
            closeFailedShareTask()
            return
        }

        val tab = VideoTabStore.createPendingTab(url)
        val launched = UnifiedPreparationCoordinator.startFromShare(this, tab.id)

        Toast.makeText(
            applicationContext,
            R.string.added_to_external_player,
            Toast.LENGTH_SHORT
        ).show()

        if (launched) {
            /*
             * Do NOT remove the whole task: the preparer is becoming its new top
             * Activity and still needs the task while Vivaldi remains foreground.
             */
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            }, HANDOFF_GRACE_MS)
        } else {
            /* WorkManager fallback was queued by the coordinator. */
            closeFailedShareTask()
        }
    }

    private fun closeFailedShareTask() {
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
