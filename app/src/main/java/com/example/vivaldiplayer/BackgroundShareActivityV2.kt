package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Exported chooser target for "BG - External Player".
 *
 * Builds #205 and #212 taught us two separate Android lifecycle limits on the
 * real test phone:
 *
 * 1. moving the preparation Activity behind Vivaldi makes it STOPPED, and this
 *    phone destroys that stopped Activity almost immediately;
 * 2. a normal third-party app cannot launch the first Activity onto its own
 *    untrusted virtual display without privileged activity-embedding rights.
 *
 * The current handoff therefore uses the ordinary/default display, but does NOT
 * move the real preparation Activity behind Vivaldi. Instead the preparation
 * Activity remains RESUMED with a nearly-transparent, non-touchable window. The
 * browser underneath remains what the user sees while the WebView keeps a real
 * Activity context and a normal full-size viewport.
 *
 * This exported Activity itself is intentionally tiny. It only:
 * - validates the shared URL;
 * - creates the persistent tab immediately at share time;
 * - starts the short foreground process lease;
 * - starts BackgroundVirtualPreparationActivity in this same excluded task;
 * - finishes itself (but does NOT remove the task, because the preparer now owns it).
 *
 * Despite its historical class name, BackgroundVirtualPreparationActivity is no
 * longer launched on a virtual display by the normal BG path.
 */
class BackgroundShareActivityV2 : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoTabStore.initialize(applicationContext)

        val sourceUrl = extractSharedUrl(intent).orEmpty()
        if (!isHttpUrl(sourceUrl)) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val tab = VideoTabStore.createPendingTab(sourceUrl)
        val tabId = tab.id
        val sessionToken = "overlay-$tabId-${System.currentTimeMillis()}"

        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markTechnicalStage(tabId, "PRIMARY_OVERLAY_SESSION_REQUESTED")

        OperationLog.record(
            this,
            event = "BG_SHARE_OVERLAY_HANDOFF_STARTED",
            tabId = tabId,
            detail = "token=$sessionToken"
        )

        /*
         * The foreground service does not resolve or play anything. It only keeps
         * the process important while the user-requested preparation Activity owns
         * yt-dlp/WebView work.
         */
        BackgroundPreparationKeepAliveService.acquire(applicationContext, sessionToken)

        val preparationIntent = Intent(this, BackgroundVirtualPreparationActivity::class.java)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_URL, sourceUrl)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_TAB_ID, tabId)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_SESSION_TOKEN, sessionToken)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

        val launched = runCatching {
            /*
             * Deliberately launch in this same task on the DEFAULT display.
             * Do not use ActivityOptions.launchDisplayId and do not move the task
             * behind Vivaldi. Keeping the preparer top/resumed is the lifecycle fix.
             */
            startActivity(preparationIntent)
            true
        }.getOrElse { error ->
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_LAUNCH_FAILED",
                tabId = tabId,
                detail = error.message ?: error.toString()
            )
            false
        }

        if (!launched) {
            BackgroundPreparationKeepAliveService.release(sessionToken)
            VideoTabStore.markError(tabId, "Could not launch transparent BG preparation Activity")
            VideoTabStore.markTechnicalStage(tabId, "PRIMARY_OVERLAY_PREP_LAUNCH_FAILED")
        } else {
            VideoTabStore.markTechnicalStage(tabId, "PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED")
            OperationLog.record(
                this,
                event = "PRIMARY_OVERLAY_PREP_LAUNCH_REQUESTED",
                tabId = tabId
            )
        }

        Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()

        /*
         * IMPORTANT: use finish(), NOT finishAndRemoveTask(). The preparation
         * Activity was just launched into this task and must remain alive as its
         * new root until it reaches READY/ERROR/NEEDS_ATTENTION.
         */
        finish()
        overridePendingTransition(0, 0)
    }

    private fun extractSharedUrl(sharedIntent: Intent): String? {
        if (sharedIntent.action != Intent.ACTION_SEND || sharedIntent.type != "text/plain") return null

        return Regex("https?://\\S+")
            .find(sharedIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
            ?.takeIf(::isHttpUrl)
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
