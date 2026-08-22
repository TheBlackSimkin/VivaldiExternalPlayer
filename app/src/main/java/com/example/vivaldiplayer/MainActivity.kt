package com.example.vivaldiplayer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ExternalPlayer screen and persistent tab dashboard.
 *
 * UI redesign notes:
 * - the tab grid is now the primary surface, inspired by browser tab switchers;
 * - normal cards intentionally omit lifecycle/technical stage strings;
 * - technical details remain available through diagnostics and the operations log;
 * - manual URL entry is still available, but collapses when not needed;
 * - playback/resolver architecture is intentionally unchanged.
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
    private lateinit var settingsButton: ImageButton
    private lateinit var dashboard: RecyclerView
    private lateinit var dashboardSwipeHint: TextView
    private lateinit var dashboardEmptyState: View
    private lateinit var dashboardCount: TextView
    private lateinit var maintenanceActions: View
    private lateinit var updateStatusButton: Button
    private lateinit var reviveExpiredButton: Button
    private lateinit var closeAllButton: Button
    private lateinit var manualToggle: Button
    private lateinit var manualCard: View
    private lateinit var dashboardAdapter: TabDashboardAdapter

    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var lastFailedUrl: String? = null
    private var statusCheckRunning = false

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
        dashboard = findViewById(R.id.tab_dashboard_container)
        dashboardSwipeHint = findViewById(R.id.dashboard_swipe_hint)
        dashboardEmptyState = findViewById(R.id.dashboard_empty_state)
        dashboardCount = findViewById(R.id.dashboard_count)
        maintenanceActions = findViewById(R.id.tab_maintenance_actions)
        updateStatusButton = findViewById(R.id.update_tab_status_button)
        reviveExpiredButton = findViewById(R.id.revive_expired_tabs_button)
        closeAllButton = findViewById(R.id.close_all_tabs_button)
        manualToggle = findViewById(R.id.manual_section_toggle)
        manualCard = findViewById(R.id.manual_url_card)

        configureDashboard()

        resolveButton.setOnClickListener { resolveAndPlay(urlInput.text.toString()) }
        browserResolveButton.setOnClickListener {
            launchBrowserResolver(lastFailedUrl ?: urlInput.text.toString().trim())
        }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        updateStatusButton.setOnClickListener { updateTabStatuses() }
        reviveExpiredButton.setOnClickListener { reviveExpiredTabs() }
        closeAllButton.setOnClickListener { confirmCloseAllTabs() }
        manualToggle.setOnClickListener { toggleManualSection() }

        refreshDashboard()

        /* Avoid resolving the same shared URL twice after Activity recreation. */
        if (savedInstanceState == null) acceptSharedUrl(intent)
    }

    override fun onResume() {
        super.onResume()

        /*
         * This host registration is only for older retry/preload/process-recovery work.
         * Normal BG shares and the new stale-tab revival path use the protected
         * foreground-service/private-display architecture.
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
        val spanCount =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

        dashboard.layoutManager = GridLayoutManager(this, spanCount)
        dashboard.isNestedScrollingEnabled = false
        dashboard.itemAnimator = null

        dashboardAdapter = TabDashboardAdapter(
            context = this,
            onPrimary = ::performPrimaryAction,
            onBrowser = { tab -> launchBrowserResolver(TabOriginStore.pageUrl(this, tab), tab.id) },
            onClose = { tab ->
                TabPreparationManager.cancelScheduled(applicationContext, tab.id)
                TabRevivalCoordinator.cancel(tab.id)

                /*
                 * Keep the thumbnail while the tab lives in Recently Closed.
                 * The cache is pruned against open + recently-closed IDs after the move,
                 * which also removes thumbnails for entries evicted by the 12-item limit.
                 */
                VideoTabStore.close(tab.id)
                TabHealthStore.clear(this, tab.id)
                pruneThumbnailCache()
                dashboard.post { refreshDashboard() }
            },
            onMove = { tab, delta -> VideoTabStore.move(tab.id, delta) },
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
            expandManualSection()
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
            expandManualSection()
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

    /**
     * Check cached playback sources one tab at a time. This is intentionally not a re-resolve;
     * it only updates the user-facing health badge and therefore cannot disturb playback state.
     */
    private fun updateTabStatuses() {
        if (statusCheckRunning) return
        val tabs = VideoTabStore.allTabs()
        if (tabs.isEmpty()) return

        statusCheckRunning = true
        refreshDashboard()

        lifecycleScope.launch {
            val summary = TabStatusChecker.checkAll(this@MainActivity, tabs) {
                if (!isFinishing && !isDestroyed) refreshDashboard()
            }
            statusCheckRunning = false
            refreshDashboard()

            Toast.makeText(
                this@MainActivity,
                getString(
                    R.string.tab_status_summary,
                    summary.ready,
                    summary.needsRefresh,
                    summary.unavailable
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Re-run only stale/failed tabs from permanent original page URLs. The dedicated revival
     * coordinator feeds them one-at-a-time to the same foreground-service/private-display
     * preparation owner used by the accepted BG share architecture.
     */
    private fun reviveExpiredTabs() {
        val candidates = VideoTabStore.allTabs().filter { tab ->
            val health = TabHealthStore.get(this, tab.id).state
            health == TabHealthStore.State.NEEDS_REFRESH ||
                tab.preparationState == VideoTabStore.PreparationState.ERROR
        }

        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.no_expired_tabs, Toast.LENGTH_SHORT).show()
            return
        }

        val usable = candidates.filter { isHttpUrl(TabOriginStore.pageUrl(this, it)) }
        val unusable = candidates.filterNot { isHttpUrl(TabOriginStore.pageUrl(this, it)) }

        unusable.forEach { tab ->
            TabHealthStore.set(
                this,
                tab.id,
                TabHealthStore.State.NEEDS_ATTENTION,
                getString(R.string.original_webpage_unavailable)
            )
        }

        usable.forEach { tab ->
            TabHealthStore.set(this, tab.id, TabHealthStore.State.UNKNOWN)
            VideoTabStore.markQueued(tab.id, getString(R.string.refresh_requested))
            TabPreparationManager.cancelScheduled(applicationContext, tab.id)
        }

        val queued = TabRevivalCoordinator.enqueue(this, usable)
        refreshDashboard()
        Toast.makeText(
            this,
            getString(R.string.revive_started_count, queued),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun confirmCloseAllTabs() {
        val tabs = VideoTabStore.allTabs()
        if (tabs.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.close_all_tabs_main)
            .setMessage(getString(R.string.close_all_tabs_confirmation_main, tabs.size))
            .setPositiveButton(R.string.close) { _, _ ->
                tabs.forEach { tab ->
                    TabPreparationManager.cancelScheduled(applicationContext, tab.id)
                    TabRevivalCoordinator.cancel(tab.id)
                    VideoTabStore.close(tab.id)
                    TabHealthStore.clear(this, tab.id)
                }
                pruneThumbnailCache()
                refreshDashboard()
                Toast.makeText(this, R.string.all_tabs_moved_recently_closed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshDashboard() {
        if (!::dashboardAdapter.isInitialized) return
        val tabs = VideoTabStore.allTabs()
        tabs.forEach { TabOriginStore.ensureFallback(this, it) }
        dashboardAdapter.submitTabs(tabs)

        dashboardCount.text = tabs.size.toString()
        dashboard.visibility = if (tabs.isEmpty()) View.GONE else View.VISIBLE
        dashboardEmptyState.visibility = if (tabs.isEmpty()) View.VISIBLE else View.GONE
        dashboardSwipeHint.visibility = if (tabs.isEmpty()) View.GONE else View.VISIBLE
        maintenanceActions.visibility = if (tabs.isEmpty()) View.GONE else View.VISIBLE

        updateStatusButton.isEnabled = tabs.isNotEmpty() && !statusCheckRunning
        closeAllButton.isEnabled = tabs.isNotEmpty()
        reviveExpiredButton.isEnabled = tabs.any { tab ->
            TabHealthStore.get(this, tab.id).state == TabHealthStore.State.NEEDS_REFRESH ||
                tab.preparationState == VideoTabStore.PreparationState.ERROR
        }
        reviveExpiredButton.alpha = if (reviveExpiredButton.isEnabled) 1f else 0.58f
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
                launchBrowserResolver(TabOriginStore.pageUrl(this, tab), tab.id)

            /* Explicit error recovery also uses the protected private-display service path. */
            tab.preparationState == VideoTabStore.PreparationState.ERROR -> {
                TabHealthStore.set(this, tab.id, TabHealthStore.State.UNKNOWN)
                VideoTabStore.markQueued(tab.id, getString(R.string.refresh_requested))
                TabPreparationManager.cancelScheduled(applicationContext, tab.id)
                TabRevivalCoordinator.enqueue(this, listOf(tab))
                refreshDashboard()
            }

            /* QUEUED/RESOLVING cards are deliberately inert. */
            else -> Unit
        }
    }

    private fun toggleManualSection() {
        val show = manualCard.visibility != View.VISIBLE
        manualCard.visibility = if (show) View.VISIBLE else View.GONE
        manualToggle.text = getString(
            if (show) R.string.manual_section_hide else R.string.home_manual_section
        )
    }

    private fun expandManualSection() {
        if (manualCard.visibility == View.VISIBLE) return
        manualCard.visibility = View.VISIBLE
        manualToggle.text = getString(R.string.manual_section_hide)
    }

    private fun pruneThumbnailCache() {
        val validIds = mutableSetOf<String>().apply {
            VideoTabStore.allTabs().forEach { add(it.id) }
            VideoTabStore.recentlyClosedTabs().forEach { add(it.id) }
        }
        TabThumbnailCache.prune(this, validIds)
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
