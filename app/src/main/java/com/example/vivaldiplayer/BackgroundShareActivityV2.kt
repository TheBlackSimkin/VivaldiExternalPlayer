package com.example.vivaldiplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Exported chooser target for "BG - External Player". */
class BackgroundShareActivityV2 : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoTabStore.initialize(applicationContext)

        val sharedUrl = extractSharedUrl(intent).orEmpty()
        if (!isHttpUrl(sharedUrl)) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }

        /*
         * Keep the exact shared page as permanent tab identity, but resolve through
         * the legitimate app-language site variant when one is known. This can
         * obtain source-provided localized metadata without translating titles.
         */
        val resolverUrl = SourceLanguagePolicy.preferAppLanguage(this, sharedUrl)
        val tab = VideoTabStore.createPendingTab(sharedUrl)
        val tabId = tab.id
        TabOriginStore.remember(applicationContext, tabId, sharedUrl)

        val sessionToken = "private-$tabId-${System.currentTimeMillis()}"
        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markTechnicalStage(tabId, "PRIVATE_PRESENTATION_SERVICE_REQUESTED")

        OperationLog.record(
            this,
            event = "BG_SHARE_PRIVATE_SERVICE_HANDOFF_STARTED",
            tabId = tabId,
            detail = "token=$sessionToken"
        )

        BackgroundPreparationKeepAliveService.acquire(
            context = applicationContext,
            token = sessionToken,
            tabId = tabId,
            sourceUrl = resolverUrl
        )

        OperationLog.record(
            this,
            event = "PRIVATE_PRESENTATION_SERVICE_REQUESTED",
            tabId = tabId
        )

        Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()
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
