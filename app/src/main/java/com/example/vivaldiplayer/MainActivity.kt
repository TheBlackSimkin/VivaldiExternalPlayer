package com.example.vivaldiplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Main entry screen for the foreground "play now" workflow.
 *
 * Preferred real-world flow:
 * Vivaldi -> Android Share -> this Activity -> resolver -> PlayerActivity.
 *
 * A SECOND share target, BackgroundAddActivity, is defined below. It creates a
 * tab and starts safe background preparation without starting playback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var openTabsButton: Button
    private lateinit var settingsButton: Button
    private lateinit var aboutButton: Button

    /** Remember the most recent direct-resolver failure for a manual retry. */
    private var lastFailedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        resolveButton = findViewById(R.id.resolve_button)
        browserResolveButton = findViewById(R.id.browser_resolve_button)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        openTabsButton = findViewById(R.id.open_tabs_button)
        settingsButton = findViewById(R.id.settings_button)
        aboutButton = findViewById(R.id.about_button)

        resolveButton.setOnClickListener {
            resolveAndPlay(urlInput.text.toString())
        }

        browserResolveButton.setOnClickListener {
            val url = lastFailedUrl ?: urlInput.text.toString().trim()
            launchBrowserResolver(url)
        }

        openTabsButton.setOnClickListener {
            openSavedTab()
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        updateSavedTabsButton()

        /* Avoid resolving the same shared URL twice after Activity recreation. */
        if (savedInstanceState == null) {
            acceptSharedUrl(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateSavedTabsButton()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedUrl(intent)
    }

    /** Browsers may share "page title + URL", not only a bare URL. */
    private fun acceptSharedUrl(intent: Intent) {
        val url = extractSharedHttpUrl(intent) ?: return
        urlInput.setText(url)
        resolveAndPlay(url)
    }

    /**
     * First attempt: yt-dlp through Chaquopy. If it fails, immediately move to
     * the browser-assisted resolver. Candidate ranking is not changed here.
     */
    private fun resolveAndPlay(url: String) {
        val cleanUrl = url.trim()

        if (!isHttpUrl(cleanUrl)) {
            status.text = getString(R.string.status_complete_url)
            return
        }

        lastFailedUrl = null
        browserResolveButton.visibility = View.GONE
        setBusy(true)
        status.text = getString(R.string.opening_video)

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Python
                        .getInstance()
                        .getModule("resolver")
                        .callAttr("resolve", cleanUrl, "auto")
                        .toString()
                }
            }.onSuccess { json ->
                setBusy(false)
                status.text = ""
                browserResolveButton.visibility = View.GONE

                startActivity(
                    Intent(this@MainActivity, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, json)
                )
            }.onFailure {
                setBusy(false)
                lastFailedUrl = cleanUrl
                status.text = getString(R.string.opening_video)
                browserResolveButton.visibility = View.VISIBLE
                launchBrowserResolver(cleanUrl)
            }
        }
    }

    /** Launch foreground browser assistance for a normal web address. */
    private fun launchBrowserResolver(url: String) {
        val cleanUrl = url.trim()
        if (!isHttpUrl(cleanUrl)) {
            status.text = getString(R.string.status_complete_url)
            return
        }

        startActivity(
            Intent(this, BrowserResolverActivity::class.java)
                .putExtra(BrowserResolverActivity.EXTRA_URL, cleanUrl)
        )
    }

    /** Open the first persistent tab without re-resolving a READY tab. */
    private fun openSavedTab() {
        val tab = VideoTabStore.allTabs().firstOrNull() ?: return

        when {
            tab.isReady -> {
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                        .putExtra(TabbedPlayerApplication.EXTRA_TAB_ID, tab.id)
                )
            }

            tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> {
                startActivity(
                    Intent(this, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, tab.sourceUrl)
                        .putExtra(BrowserResolverActivity.EXTRA_TAB_ID, tab.id)
                )
            }

            else -> Toast.makeText(this, R.string.tab_not_ready_yet, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSavedTabsButton() {
        val count = VideoTabStore.allTabs().size
        openTabsButton.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            openTabsButton.text = getString(R.string.open_saved_tabs_count, count)
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        resolveButton.isEnabled = !busy
        browserResolveButton.isEnabled = !busy
        urlInput.isEnabled = !busy
    }
}

/**
 * Second Android share target: the background preparation path.
 *
 * Android share targets are Activities, so a tiny Activity must exist for the
 * share hand-off. The manifest deliberately puts this Activity in its own
 * transient task. It saves the tab, schedules WorkManager, removes that task,
 * and returns Android to the task which was previously visible (normally
 * Vivaldi). It never constructs ExoPlayer or starts playback.
 */
class BackgroundAddActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedIntent(intent)
    }

    /** singleTask makes this defensive path useful if two shares arrive rapidly. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(sharedIntent: Intent) {
        VideoTabStore.initialize(applicationContext)
        val url = extractSharedHttpUrl(sharedIntent)

        if (url == null) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            closeTransientTask()
            return
        }

        val tab = VideoTabStore.createPendingTab(url)
        TabPreparationManager.enqueue(applicationContext, tab.id)

        Toast.makeText(
            applicationContext,
            R.string.added_to_external_player,
            Toast.LENGTH_SHORT
        ).show()

        closeTransientTask()
    }

    /** Remove only the special background-add task; never touch Vivaldi's task. */
    private fun closeTransientTask() {
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }
}

/**
 * WorkManager coordinator for tab preparation.
 *
 * Preparation resolves URLs and stores metadata only. It never constructs an
 * ExoPlayer, never starts audio/video, and therefore remains separate from the
 * one-foreground-player playback rule.
 */
object TabPreparationManager {
    private const val WORK_PREFIX = "prepare-video-tab-"

    fun enqueue(context: Context, tabId: String, replace: Boolean = false) {
        val tab = VideoTabStore.get(tabId) ?: return
        if (tab.sourceUrl.isBlank() || tab.isReady) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ResolveTabWorker>()
            .setInputData(workDataOf(ResolveTabWorker.KEY_TAB_ID to tabId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "$WORK_PREFIX$tabId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Resume queued work after process restart. */
    fun resumePending(context: Context) {
        VideoTabStore.allTabs()
            .filter { it.preparationState == VideoTabStore.PreparationState.QUEUED }
            .forEach { enqueue(context, it.id) }
    }

    /** Explicit retry used by the tab/error recovery UI. */
    fun retry(context: Context, tabId: String) {
        VideoTabStore.markQueued(tabId)
        enqueue(context, tabId, replace = true)
    }

    /**
     * Feature 29: proactively pre-resolve the next queued tab. READY tabs need no
     * work. NEEDS_ATTENTION means the safe background stage already finished and
     * the remaining browser/WebView step must wait for the foreground.
     */
    fun preloadNext(context: Context, currentTabId: String) {
        if (!AppSettings.preloadNextTab(context)) return
        val next = VideoTabStore.nextAfter(currentTabId) ?: return
        if (next.preparationState == VideoTabStore.PreparationState.QUEUED) {
            enqueue(context, next.id)
        }
    }
}

/**
 * Direct background resolver.
 *
 * A direct success makes the tab READY. A normal non-transient direct failure is
 * interpreted as "browser assistance is the next normal step" and is stored as
 * NEEDS_ATTENTION internally. The user-facing label describes this as a browser
 * step rather than an error. Explicit access-control/DRM/challenge errors become
 * ERROR and are never automated around.
 */
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
        if (tab.sourceUrl.isBlank()) {
            VideoTabStore.markError(tabId, "Missing source URL")
            return Result.failure()
        }

        VideoTabStore.markResolving(tabId)

        return runCatching {
            withContext(Dispatchers.IO) {
                Python
                    .getInstance()
                    .getModule("resolver")
                    .callAttr("resolve", tab.sourceUrl, "auto")
                    .toString()
            }
        }.fold(
            onSuccess = { json ->
                /* Parse once here so corrupt/incomplete resolver output is not marked READY. */
                runCatching { ResolvedMedia.fromJson(json) }
                    .onSuccess {
                        VideoTabStore.markReady(tabId, json)
                    }
                    .onFailure { error ->
                        VideoTabStore.markError(tabId, error.message ?: "Invalid resolver result")
                    }

                if (VideoTabStore.get(tabId)?.isReady == true) Result.success() else Result.failure()
            },
            onFailure = { error ->
                val technical = (error.message ?: error.toString()).take(500)

                when {
                    isRestrictedOrChallengeFailure(technical) -> {
                        VideoTabStore.markError(tabId, technical)
                        Result.failure()
                    }

                    isTransientNetworkFailure(technical) &&
                        AppSettings.networkRetryEnabled(applicationContext) &&
                        runAttemptCount < AppSettings.MAX_TRANSIENT_RETRIES -> {
                        VideoTabStore.markQueued(tabId, "Temporary network failure; retry scheduled")
                        Result.retry()
                    }

                    isTransientNetworkFailure(technical) -> {
                        VideoTabStore.markError(tabId, technical)
                        Result.failure()
                    }

                    else -> {
                        /*
                         * Safe background work ends here. Browser-assisted
                         * discovery remains a foreground-only user-visible step.
                         */
                        VideoTabStore.markNeedsAttention(tabId, technical)
                        Result.success()
                    }
                }
            }
        )
    }

    private fun isTransientNetworkFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "timed out",
            "timeout",
            "temporary failure",
            "connection reset",
            "connection aborted",
            "network is unreachable",
            "name or service not known",
            "http error 429",
            "http error 500",
            "http error 502",
            "http error 503",
            "http error 504"
        ).any(lower::contains)
    }

    /** Never retry or automate around explicit protected-access/challenge signals. */
    private fun isRestrictedOrChallengeFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "drm",
            "captcha",
            "verify you are human",
            "anti-bot",
            "paywall",
            "subscription required",
            "login required",
            "sign in to confirm",
            "geo-restricted",
            "not available in your country"
        ).any(lower::contains)
    }
}

/** Shared extraction for both Android share targets. */
private fun extractSharedHttpUrl(intent: Intent): String? {
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
