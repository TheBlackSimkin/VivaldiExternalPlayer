package com.example.vivaldiplayer

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Exported chooser target for "BG - External Player".
 *
 * Build #205 device logging proved that keeping the process alive with a
 * foreground service was not enough: once this normal share Activity moved
 * behind Vivaldi, Android destroyed the stopped Activity within ~200 ms and its
 * WebView/direct coroutine died with it.
 *
 * This class is therefore intentionally tiny. It now:
 * 1. creates the persistent tab;
 * 2. starts the short foreground-process lease;
 * 3. creates a private app-owned virtual display;
 * 4. launches BackgroundVirtualPreparationActivity on that off-screen display;
 * 5. immediately finishes this visible/share task.
 *
 * The real preparation Activity remains an actual Android Activity (required by
 * WebView), but it is resumed on the private secondary display instead of being
 * a stopped Activity behind Vivaldi on the phone's primary display.
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
        val sessionToken = "virtual-$tabId"

        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markTechnicalStage(tabId, "VIRTUAL_DISPLAY_SESSION_REQUESTED")
        OperationLog.record(
            this,
            event = "BG_SHARE_HANDOFF_STARTED",
            tabId = tabId,
            detail = "api=${Build.VERSION.SDK_INT}"
        )

        /*
         * Activities on secondary displays require API 26 and the platform
         * FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS capability. Do not silently
         * fall back to the old stopped-Activity/Worker architecture if unavailable.
         */
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
        ) {
            VideoTabStore.markError(
                tabId,
                "Automatic BG browser preparation requires Android secondary-display activity support"
            )
            VideoTabStore.markTechnicalStage(tabId, "VIRTUAL_DISPLAY_UNSUPPORTED")
            OperationLog.record(
                this,
                event = "VIRTUAL_DISPLAY_UNSUPPORTED",
                tabId = tabId
            )
            Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }

        BackgroundPreparationKeepAliveService.acquire(applicationContext, sessionToken)

        val displayId = BackgroundVirtualDisplayRegistry.create(
            context = this,
            sessionToken = sessionToken
        )

        if (displayId == null) {
            VideoTabStore.markError(tabId, "Could not create private BG preparation display")
            VideoTabStore.markTechnicalStage(tabId, "VIRTUAL_DISPLAY_CREATE_FAILED")
            BackgroundPreparationKeepAliveService.release(sessionToken)
            OperationLog.record(
                this,
                event = "VIRTUAL_DISPLAY_CREATE_FAILED",
                tabId = tabId
            )
            Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }

        val preparationIntent = Intent(this, BackgroundVirtualPreparationActivity::class.java)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_URL, sourceUrl)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_TAB_ID, tabId)
            .putExtra(BackgroundVirtualPreparationActivity.EXTRA_SESSION_TOKEN, sessionToken)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
        }

        val launched = runCatching {
            startActivity(preparationIntent, options.toBundle())
            true
        }.getOrElse { error ->
            OperationLog.record(
                this,
                event = "VIRTUAL_PREP_LAUNCH_FAILED",
                tabId = tabId,
                detail = error.message ?: error.toString()
            )
            false
        }

        if (!launched) {
            BackgroundVirtualDisplayRegistry.release(sessionToken)
            BackgroundPreparationKeepAliveService.release(sessionToken)
            VideoTabStore.markError(tabId, "Could not launch off-screen BG preparation Activity")
            VideoTabStore.markTechnicalStage(tabId, "VIRTUAL_PREP_LAUNCH_FAILED")
        } else {
            VideoTabStore.markTechnicalStage(tabId, "VIRTUAL_PREP_LAUNCH_REQUESTED")
            OperationLog.record(
                this,
                event = "VIRTUAL_PREP_LAUNCH_REQUESTED",
                tabId = tabId,
                detail = "display=$displayId"
            )
        }

        Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()

        /*
         * The exported share Activity owns no resolver work. Finishing it is now
         * safe: the foreground service holds process importance and the actual
         * preparation Activity lives on the private secondary display.
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
