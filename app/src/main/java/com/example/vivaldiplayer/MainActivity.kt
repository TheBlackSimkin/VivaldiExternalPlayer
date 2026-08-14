package com.example.vivaldiplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ExternalPlayer screen and first-class persistent tab dashboard.
 *
 * The dashboard is OBSERVATION/PLAYBACK UI, not the normal BG-preparation
 * trigger. A BG share must already have requested and started preparation before
 * this Activity exists. Selecting a QUEUED/RESOLVING card therefore does not
 * call prepareNow(). READY plays, NEEDS_ATTENTION exposes the genuine Browser
 * Step, and ERROR keeps an explicit recovery retry.
 *
 * Dashboard gestures remain RecyclerView + ItemTouchHelper:
 * - long-press a card and drag vertically to reorder;
 * - swipe a card sideways to close;
 * - no arrow buttons.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val DASHBOARD_REFRESH_MS = 1_250L
    }

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var settingsButton: Button
    private lateinit var aboutButton: Button
    private lateinit var dashboard: RecyclerView
    private lateinit var dashboardSwipeHint: TextView
    private lateinit var dashboardAdapter: TabDashboardAdapter

    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var lastFailedUrl: String? = null

    private val dashboardRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                refreshDashboard()
                dashboardHandler.postDelayed(this, DASHBOARD_REFRESH_MS)
            }
        }
    }

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
        dashboard = findViewById(R.id.tab_dashboard_container)
        dashboardSwipeHint = findViewById(R.id.dashboard_swipe_hint)

        configureDashboard()

        resolveButton.setOnClickListener { resolveAndPlay(urlInput.text.toString()) }
        browserResolveButton.setOnClickListener {
            launchBrowserResolver(lastFailedUrl ?: urlInput.text.toString().trim())
        }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        aboutButton.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        refreshDashboard()

        /* Avoid resolving the same shared URL twice after Activity recreation. */
        if (savedInstanceState == null) acceptSharedUrl(intent)
    }

    override fun onResume() {
        super.onResume()

        /*
         * This host registration is only for retry/preload/process-recovery work.
         * Normal BG shares already own their preparation in BackgroundShareActivity.
         */
        UnifiedPreparationCoordinator.onHostResumed(this)
        TabThumbnailWarmup.warm(this)
        refreshDashboard()
        dashboardHandler.removeCallbacks(dashboardRefreshRunnable)
        dashboardHandler.postDelayed(dashboardRefreshRunnable, DASHBOARD_REFRESH_MS)
    }

    override fun onPause() {
        dashboardHandler.removeCallbacks(dashboardRefreshRunnable)
        UnifiedPreparationCoordinator.onHostPaused(this)
        super.onPause()
    }

    override fun onDestroy() {
        dashboardHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedUrl(intent)
    }

    private fun configureDashboard() {
        dashboard.layoutManager = LinearLayoutManager(this)
        dashboard.isNestedScrollingEnabled = false
        dashboard.itemAnimator = null

        dashboardAdapter = TabDashboardAdapter(
            context = this,
            onPrimary = ::performPrimaryAction,
            onBrowser = { tab -> launchBrowserResolver(tab.sourceUrl, tab.id) },
            onClose = { tab ->
                TabPreparationManager.cancelScheduled(applicationContext, tab.id)
                TabThumbnailCache.delete(this, tab.id)
                VideoTabStore.close(tab.id)
                dashboard.post { refreshDashboard() }
            },
            onMove = { tab, delta ->
                VideoTabStore.move(tab.id, delta)
            },
            onThumbnailNeeded = { tab ->
                TabThumbnailCapture.captureResolved(this, tab) {
                    if (!isFinishing && !isDestroyed) refreshDashboard()
                }
            }
        )

        dashboard.adapter = dashboardAdapter
        dashboardAdapter.attachTouchHelper(dashboard)
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

    private fun refreshDashboard() {
        if (!::dashboardAdapter.isInitialized) return
        val tabs = VideoTabStore.allTabs()
        dashboardAdapter.submitTabs(tabs)
        dashboardSwipeHint.visibility = if (tabs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun performPrimaryAction(tab: VideoTabStore.VideoTab) {
        when {
            tab.isReady -> startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                    .putExtra(TabbedPlayerApplication.EXTRA_TAB_ID, tab.id)
            )

            /* Genuine interaction may be required only after automatic browser work stopped safely. */
            tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION ->
                launchBrowserResolver(tab.sourceUrl, tab.id)

            /* ERROR keeps an explicit recovery path, but normal BG preparation never depends on it. */
            tab.preparationState == VideoTabStore.PreparationState.ERROR -> {
                UnifiedPreparationCoordinator.retry(this, tab.id)
                refreshDashboard()
            }

            /*
             * QUEUED/RESOLVING cards are deliberately inert. A card click must
             * never be the event which makes ordinary BG preparation begin.
             */
            else -> Unit
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
