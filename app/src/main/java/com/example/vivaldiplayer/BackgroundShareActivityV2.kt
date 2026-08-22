package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Exported chooser target for "BG - External Player".
 *
 * Device QA established that the physical/default-display preparation Activity
 * is not reliable enough for this job:
 * - #205: when intentionally stopped behind Vivaldi, Android destroyed it almost
 *   immediately;
 * - #225: alpha=0 removed the flash, but the focusable Activity still blocked
 *   Vivaldi for about seven seconds;
 * - #227: NOT_TOUCHABLE + NOT_FOCUSABLE could look clean for one share, but
 *   repeated shares still reproduced Vivaldi freezing.
 *
 * Therefore this exported Activity is now only a very short user-share handoff.
 * It creates the persistent tab and asks the foreground service to own the real
 * preparation session. The service creates a WebView inside a Presentation on an
 * app-private virtual display; no preparation Activity/window is added to the
 * phone's physical display.
 *
 * This Activity never resolves media and never creates PlayerActivity/ExoPlayer.
 */
class BackgroundShareActivityV2 : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoTabStore.initialize(applicationContext)

        val sourceUrl = extractSharedUrl(intent).orEmpty()
        if (!isHttpUrl(sourceUrl)) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }

        val tab = VideoTabStore.createPendingTab(sourceUrl)
        val tabId = tab.id

        /*
         * Store the exact shared page URL before any resolver can update temporary
         * playback metadata. This value is the permanent identity used by revival
         * and Favorites; it is never replaced by a media/CDN URL.
         */
        TabOriginStore.remember(applicationContext, tabId, sourceUrl)

        val sessionToken = "private-$tabId-${System.currentTimeMillis()}"

        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markTechnicalStage(tabId, "PRIVATE_PRESENTATION_SERVICE_REQUESTED")

        OperationLog.record(
            this,
            event = "BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED",
            tabId = tabId,
            detail = "token=$sessionToken"
        )

        /*
         * The foreground service now owns both process importance and the
         * non-Activity private-display preparation host. Supplying tabId + URL is
         * what distinguishes this normal V2 path from older lease-only callers.
         */
        BackgroundPreparationKeepAliveService.acquire(
            context = applicationContext,
            token = sessionToken,
            tabId = tabId,
            sourceUrl = sourceUrl
        )

        OperationLog.record(
            this,
            event = "PRIVATE_PRESENTATION_SERVICE_REQUESTED",
            tabId = tabId
        )

        Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()

        /*
         * There is no longer a preparation Activity in this task. Remove the
         * share handoff task immediately so Android can restore Vivaldi normally.
         */
        finishAndRemoveTask()
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
