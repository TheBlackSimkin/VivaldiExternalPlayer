package com.example.vivaldiplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Main ExternalPlayer screen.
 *
 * The old "open saved tabs" button has become a real dashboard. Persistent tabs
 * are visible immediately with local thumbnail, preparation state, saved
 * position, manual quality preference, actual Media3 quality, direct actions,
 * close/reorder controls and a sideways-swipe close gesture.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var settingsButton: Button
    private lateinit var aboutButton: Button
    private lateinit var dashboardContainer: LinearLayout
    private lateinit var dashboardSwipeHint: TextView

    private var lastFailedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        resolveButton = findViewById(R.id.resolve_button)
        browserResolveButton = findViewById(R.id.browser_resolve_button)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        settingsButton = findViewById(R.id.settings_button)
        aboutButton = findViewById(R.id.about_button)
        dashboardContainer = findViewById(R.id.tab_dashboard_container)
        dashboardSwipeHint = findViewById(R.id.dashboard_swipe_hint)

        resolveButton.setOnClickListener { resolveAndPlay(urlInput.text.toString()) }
        browserResolveButton.setOnClickListener {
            launchBrowserResolver(lastFailedUrl ?: urlInput.text.toString().trim())
        }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        renderDashboard()

        /* Avoid resolving the same shared URL twice after Activity recreation. */
        if (savedInstanceState == null) acceptSharedUrl(intent)
    }

    override fun onResume() {
        super.onResume()
        UnifiedPreparationCoordinator.onHostResumed(this)
        TabThumbnailWarmup.warm(this)
        renderDashboard()
    }

    override fun onPause() {
        UnifiedPreparationCoordinator.onHostPaused(this)
        super.onPause()
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

    /** Foreground Play-now flow stays yt-dlp first then normal visible browser fallback. */
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
                    Python.getInstance()
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

    private fun launchBrowserResolver(url: String, tabId: String? = null) {
        val cleanUrl = url.trim()
        if (!isHttpUrl(cleanUrl)) {
            status.text = getString(R.string.status_complete_url)
            return
        }

        startActivity(
            Intent(this, BrowserResolverActivity::class.java)
                .putExtra(BrowserResolverActivity.EXTRA_URL, cleanUrl)
                .apply {
                    if (!tabId.isNullOrBlank()) {
                        putExtra(BrowserResolverActivity.EXTRA_TAB_ID, tabId)
                    }
                }
        )
    }

    /** Build the first-class local tab dashboard from persistent state. */
    private fun renderDashboard() {
        if (!::dashboardContainer.isInitialized) return
        dashboardContainer.removeAllViews()

        val tabs = VideoTabStore.allTabs()
        dashboardSwipeHint.visibility = if (tabs.isEmpty()) View.GONE else View.VISIBLE

        if (tabs.isEmpty()) {
            dashboardContainer.addView(TextView(this).apply {
                text = getString(R.string.no_video_tabs)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_secondary))
                setPadding(dp(4), dp(16), dp(4), dp(14))
            })
            return
        }

        tabs.forEachIndexed { index, tab ->
            dashboardContainer.addView(createTabCard(tab, index, tabs.size))

            /* READY background tabs can gain a thumbnail without ever starting playback. */
            if (tab.isReady && TabThumbnailCache.load(this, tab.id) == null) {
                TabThumbnailCapture.captureResolved(this, tab) {
                    if (!isFinishing && !isDestroyed) renderDashboard()
                }
            }
        }
    }

    private fun createTabCard(
        tab: VideoTabStore.VideoTab,
        index: Int,
        total: Int
    ): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_surface))
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.app_outline)
            strokeWidth = dp(1)
            isClickable = true
        }

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        card.layoutParams = cardParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.app_surface_raised))
            TabThumbnailCache.load(this@MainActivity, tab.id)?.let { setImageBitmap(it) }
        }
        top.addView(preview, LinearLayout.LayoutParams(dp(124), dp(70)))

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        textColumn.addView(TextView(this).apply {
            text = tab.title
            maxLines = 2
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_primary))
        })

        textColumn.addView(TextView(this).apply {
            text = dashboardDetails(tab)
            textSize = 12f
            setPadding(0, dp(5), 0, 0)
            setTextColor(stateColor(tab.preparationState))
        })

        top.addView(
            textColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(top)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val primary = compactButton(primaryActionLabel(tab)).apply {
            isEnabled = tab.preparationState != VideoTabStore.PreparationState.RESOLVING
            setOnClickListener { performPrimaryAction(tab) }
        }
        actions.addView(primary, LinearLayout.LayoutParams(0, dp(42), 1f))

        if (tab.preparationState == VideoTabStore.PreparationState.ERROR) {
            actions.addView(compactButton(getString(R.string.dashboard_browser)).apply {
                setOnClickListener { launchBrowserResolver(tab.sourceUrl, tab.id) }
            })
        }

        if (index > 0) {
            actions.addView(compactButton("↑", getString(R.string.dashboard_move_up)).apply {
                setOnClickListener {
                    VideoTabStore.move(tab.id, -1)
                    renderDashboard()
                }
            })
        }
        if (index < total - 1) {
            actions.addView(compactButton("↓", getString(R.string.dashboard_move_down)).apply {
                setOnClickListener {
                    VideoTabStore.move(tab.id, 1)
                    renderDashboard()
                }
            })
        }

        actions.addView(compactButton("×", getString(R.string.dashboard_close)).apply {
            setOnClickListener { closeDashboardTab(tab.id) }
        })

        root.addView(actions)
        card.addView(root)
        installSwipeToClose(card, tab.id)
        return card
    }

    private fun dashboardDetails(tab: VideoTabStore.VideoTab): String {
        val details = mutableListOf(stateLabel(tab.preparationState))
        if (tab.positionMs > 0L) details += formatPosition(tab.positionMs)

        if (tab.isReady) {
            tab.manualQualityHeight?.let {
                details += getString(R.string.dashboard_manual_quality, it)
            } ?: run {
                details += getString(R.string.dashboard_auto_quality)
            }
            tab.actualQualityHeight?.let {
                details += getString(R.string.dashboard_actual_quality, it)
            }
        }
        return details.joinToString(" • ")
    }

    private fun primaryActionLabel(tab: VideoTabStore.VideoTab): String = when {
        tab.isReady && tab.positionMs > 0L -> getString(R.string.dashboard_continue)
        tab.isReady -> getString(R.string.dashboard_play)
        tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION ->
            getString(R.string.dashboard_browser)
        tab.preparationState == VideoTabStore.PreparationState.RESOLVING ->
            getString(R.string.tab_state_resolving)
        tab.preparationState == VideoTabStore.PreparationState.ERROR -> getString(R.string.retry)
        else -> getString(R.string.dashboard_prepare)
    }

    private fun performPrimaryAction(tab: VideoTabStore.VideoTab) {
        when {
            tab.isReady -> startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                    .putExtra(TabbedPlayerApplication.EXTRA_TAB_ID, tab.id)
            )

            tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION ->
                launchBrowserResolver(tab.sourceUrl, tab.id)

            tab.preparationState == VideoTabStore.PreparationState.ERROR -> {
                UnifiedPreparationCoordinator.retry(this, tab.id)
                renderDashboard()
            }

            tab.preparationState != VideoTabStore.PreparationState.RESOLVING -> {
                UnifiedPreparationCoordinator.prepareNow(this, tab.id)
                renderDashboard()
            }
        }
    }

    private fun closeDashboardTab(tabId: String) {
        TabPreparationManager.cancelScheduled(applicationContext, tabId)
        TabThumbnailCache.delete(this, tabId)
        VideoTabStore.close(tabId)
        renderDashboard()
    }

    private fun installSwipeToClose(card: MaterialCardView, tabId: String) {
        var downX = 0f
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val swiped = abs(event.x - downX) >= dp(90)
                    if (swiped) closeDashboardTab(tabId)
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun compactButton(text: String, description: String = text): Button = Button(this).apply {
        isAllCaps = false
        this.text = text
        contentDescription = description
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_primary))
    }

    private fun stateLabel(state: VideoTabStore.PreparationState): String = when (state) {
        VideoTabStore.PreparationState.QUEUED -> getString(R.string.tab_state_queued)
        VideoTabStore.PreparationState.RESOLVING -> getString(R.string.tab_state_resolving)
        VideoTabStore.PreparationState.READY -> getString(R.string.tab_state_ready)
        VideoTabStore.PreparationState.NEEDS_ATTENTION -> getString(R.string.tab_state_needs_attention)
        VideoTabStore.PreparationState.ERROR -> getString(R.string.tab_state_error)
    }

    private fun stateColor(state: VideoTabStore.PreparationState): Int = ContextCompat.getColor(
        this,
        when (state) {
            VideoTabStore.PreparationState.READY -> R.color.app_success
            VideoTabStore.PreparationState.ERROR -> R.color.app_warning
            VideoTabStore.PreparationState.NEEDS_ATTENTION -> R.color.app_warning
            else -> R.color.app_text_secondary
        }
    )

    private fun formatPosition(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * WorkManager is now a restart/network fallback, not a competing preparation
 * implementation. Any ordinary direct-resolver miss is re-queued and handed to
 * UnifiedPreparationCoordinator's browser-capable stage when a foreground host
 * is available.
 */
object TabPreparationManager {
    private const val WORK_PREFIX = "prepare-video-tab-"

    private fun workName(tabId: String): String = "$WORK_PREFIX$tabId"

    fun cancelScheduled(context: Context, tabId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(tabId))
    }

    fun enqueue(context: Context, tabId: String, replace: Boolean = false) {
        val tab = VideoTabStore.get(tabId) ?: return
        if (tab.sourceUrl.isBlank() || tab.isReady) return

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

    /** If the app is still closed, WorkManager can perform the cheap direct stage. */
    fun resumePending(context: Context) {
        VideoTabStore.allTabs()
            .filter { it.preparationState == VideoTabStore.PreparationState.QUEUED }
            .forEach { enqueue(context, it.id) }
    }

    fun retry(context: Context, tabId: String) {
        VideoTabStore.markQueued(tabId)
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
        if (next.preparationState == VideoTabStore.PreparationState.QUEUED) enqueue(context, next.id)
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
        if (tab.sourceUrl.isBlank()) {
            VideoTabStore.markError(tabId, "Missing source URL")
            return Result.failure()
        }

        VideoTabStore.markResolving(tabId)

        return runCatching {
            withContext(Dispatchers.IO) {
                Python.getInstance()
                    .getModule("resolver")
                    .callAttr("resolve", tab.sourceUrl, "auto")
                    .toString()
            }
        }.fold(
            onSuccess = { json ->
                runCatching { ResolvedMedia.fromJson(json) }
                    .onSuccess { VideoTabStore.markReady(tabId, json) }
                    .onFailure { VideoTabStore.markError(tabId, it.message ?: "Invalid resolver result") }
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
                        /* Continue through the SAME browser-capable stage when foreground is available. */
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

    private fun isRestrictedOrChallengeFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "drm", "captcha", "verify you are human", "anti-bot", "paywall",
            "subscription required", "login required", "sign in to confirm",
            "geo-restricted", "not available in your country"
        ).any(lower::contains)
    }
}

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
