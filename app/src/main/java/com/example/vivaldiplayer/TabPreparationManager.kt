package com.example.vivaldiplayer

import android.app.Activity
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Android-managed direct/network preparation fallback.
 *
 * Normal BG shares now prepare inside BackgroundShareActivity itself. WorkManager
 * remains important for process restart or an unexpectedly destroyed preparation
 * host. It can always perform the direct/network stage without any dashboard/tab
 * selection. A normal direct miss is recorded as browser-stage-needed; the richer
 * browser Activity may resume later when Android gives us an Activity lifecycle.
 */
object TabPreparationManager {
    private const val WORK_PREFIX = "prepare-video-tab-"

    private fun workName(tabId: String): String = "$WORK_PREFIX$tabId"

    fun cancelScheduled(context: Context, tabId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(tabId))
    }

    fun enqueue(context: Context, tabId: String, replace: Boolean = false) {
        val tab = VideoTabStore.get(tabId) ?: return
        val originalPageUrl = TabOriginStore.pageUrl(context, tab)
        if (originalPageUrl.isBlank() || tab.isReady) return

        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markTechnicalStage(tabId, "WORKER_ENQUEUED")

        val request = OneTimeWorkRequestBuilder<ResolveTabWorker>()
            .setInputData(workDataOf(ResolveTabWorker.KEY_TAB_ID to tabId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(tabId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun resumePending(context: Context) {
        VideoTabStore.allTabs()
            .filter { it.preparationState == VideoTabStore.PreparationState.QUEUED }
            .forEach { enqueue(context, it.id) }
    }

    fun retry(context: Context, tabId: String) {
        VideoTabStore.markQueued(tabId)
        VideoTabStore.markPreparationRequested(tabId)
        if (context is Activity) {
            UnifiedPreparationCoordinator.retry(context, tabId)
        } else {
            enqueue(context, tabId, replace = true)
        }
    }

    fun preloadNext(context: Context, currentTabId: String) {
        if (!AppSettings.preloadNextTab(context)) return
        if (context is Activity) {
            UnifiedPreparationCoordinator.preloadNext(context, currentTabId)
            return
        }

        val next = VideoTabStore.nextAfter(currentTabId) ?: return
        if (next.preparationState == VideoTabStore.PreparationState.QUEUED) {
            enqueue(context, next.id)
        }
    }
}

class ResolveTabWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TAB_ID = "tab_id"
    }

    override suspend fun doWork(): Result {
        VideoTabStore.initialize(applicationContext)

        val tabId = inputData.getString(KEY_TAB_ID).orEmpty()
        val tab = VideoTabStore.get(tabId) ?: return Result.success()
        if (tab.isReady) return Result.success()

        val originalPageUrl = TabOriginStore.pageUrl(applicationContext, tab)
        if (originalPageUrl.isBlank()) {
            VideoTabStore.markError(tabId, "Missing original page URL")
            return Result.failure()
        }

        VideoTabStore.markResolving(tabId)
        VideoTabStore.markTechnicalStage(tabId, "WORKER_STARTED")
        VideoTabStore.markDirectResolverStarted(tabId)

        return runCatching {
            withContext(Dispatchers.IO) {
                Python.getInstance()
                    .getModule("resolver")
                    .callAttr("resolve", originalPageUrl, "auto")
                    .toString()
            }
        }.fold(
            onSuccess = { json ->
                VideoTabStore.markDirectResolverFinished(tabId)

                runCatching { ResolvedMedia.fromJson(json) }
                    .onSuccess { VideoTabStore.markReady(tabId, json) }
                    .onFailure { VideoTabStore.markError(tabId, it.message ?: "Invalid resolver result") }

                if (VideoTabStore.get(tabId)?.isReady == true) {
                    /* Keep the queue moving even when no Activity is visible. */
                    TabPreparationManager.preloadNext(applicationContext, tabId)
                    Result.success()
                } else {
                    Result.failure()
                }
            },
            onFailure = { error ->
                VideoTabStore.markDirectResolverFinished(tabId)
                val technical = (error.message ?: error.toString()).take(500)

                when {
                    isHardProtectedFailure(technical) -> {
                        VideoTabStore.markError(tabId, technical)
                        Result.failure()
                    }

                    isTransientNetworkFailure(technical) &&
                        AppSettings.networkRetryEnabled(applicationContext) &&
                        runAttemptCount < AppSettings.MAX_TRANSIENT_RETRIES -> {
                        VideoTabStore.markQueued(tabId, "Temporary network failure; retry scheduled")
                        VideoTabStore.markTechnicalStage(tabId, "WORKER_NETWORK_RETRY")
                        Result.retry()
                    }

                    isTransientNetworkFailure(technical) -> {
                        /* A later explicit retry remains available; do not spin forever. */
                        VideoTabStore.markError(tabId, technical)
                        Result.failure()
                    }

                    else -> {
                        /*
                         * Includes normal extractor misses and challenge/login-like
                         * messages. The browser stage may load the ordinary page,
                         * but its consent automation still refuses CAPTCHA, login,
                         * payment, region and DRM controls.
                         *
                         * WorkManager itself does not own a WebView. Mark the exact
                         * stage and ask the coordinator for the next valid Activity
                         * lifecycle instead of pretending direct resolution was READY.
                         */
                        VideoTabStore.markBrowserStageRequested(tabId)
                        VideoTabStore.markTechnicalStage(tabId, "WORKER_BROWSER_STAGE_NEEDED")
                        UnifiedPreparationCoordinator.browserStageNeeded(tabId, technical)
                        Result.success()
                    }
                }
            }
        )
    }

    private fun isTransientNetworkFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "timed out", "timeout", "temporary failure", "connection reset",
            "connection aborted", "network is unreachable", "name or service not known",
            "http error 429", "http error 500", "http error 502", "http error 503", "http error 504"
        ).any(lower::contains)
    }

    /** Only genuinely protected access signals are terminal in a background Worker. */
    private fun isHardProtectedFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "drm", "paywall", "subscription required", "purchase required",
            "geo-restricted", "not available in your country", "regional restriction"
        ).any(lower::contains)
    }
}
