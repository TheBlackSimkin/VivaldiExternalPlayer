package com.example.vivaldiplayer

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main ExternalPlayer screen and persistent tab dashboard. */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val DASHBOARD_REFRESH_MS = 1_250L
        private const val FILTER_ALL = "all"
        private const val FILTER_READY = "ready"
        private const val FILTER_QUEUED = "queued"
        private const val FILTER_ATTENTION = "attention"
    }

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var dashboard: RecyclerView
    private lateinit var dashboardScroll: NestedScrollView
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

    private lateinit var selectionActions: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var selectionReviveButton: Button

    private val dashboardHandler = Handler(Looper.getMainLooper())
    private var lastFailedUrl: String? = null
    private var statusCheckRunning = false
    private var dashboardQuery = ""
    private var dashboardFilter = FILTER_ALL
    private var visibleTabs: List<VideoTabStore.VideoTab> = emptyList()

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
        dashboardScroll = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as NestedScrollView)

        configureDashboard()
        installDashboardCollectionControls()

        resolveButton.setOnClickListener { resolveAndPlay(urlInput.text.toString()) }
        browserResolveButton.setOnClickListener {
            launchBrowserResolver(lastFailedUrl ?: urlInput.text.toString().trim())
        }
        settingsButton.setOnClickListener { showDashboardMenu() }
        updateStatusButton.setOnClickListener { updateTabStatuses() }
        reviveExpiredButton.setOnClickListener { reviveExpiredTabs() }
        closeAllButton.setOnClickListener { confirmCloseAllTabs() }
        maintenanceActions.visibility = View.GONE
        manualToggle.setOnClickListener { toggleManualSection() }

        refreshDashboard()
        attachPrivacyCurtain()
        if (savedInstanceState == null) acceptSharedUrl(intent)
    }

    override fun onResume() {
        super.onResume()
        UnifiedPreparationCoordinator.onHostResumed(this)
        TabThumbnailWarmup.warm(this)
        refreshDashboard()
        restoreDashboardAnchorIfNeeded()
        attachPrivacyCurtain()
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
        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        dashboard.layoutManager = GridLayoutManager(this, spanCount)
        dashboard.isNestedScrollingEnabled = false
        dashboard.itemAnimator = null

        dashboardAdapter = TabDashboardAdapter(
            context = this,
            onPrimary = ::performPrimaryAction,
            onBrowser = { tab -> launchBrowserResolver(TabOriginStore.pageUrl(this, tab), tab.id) },
            onClose = { tab -> closeTab(tab) },
            onMove = { tab, delta -> VideoTabStore.move(tab.id, delta) },
            onThumbnailNeeded = { tab ->
                TabThumbnailCapture.captureResolved(this, tab) {
                    if (!isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        refreshDashboard()
                    }
                }
            },
            onSelectionChanged = ::updateSelectionActions
        )
        dashboard.adapter = dashboardAdapter
        dashboardAdapter.attachTouchHelper(dashboard)
    }

    private fun installDashboardCollectionControls() {
        val parent = dashboard.parent as? LinearLayout ?: return
        val dashboardIndex = parent.indexOfChild(dashboard)

        val filter = AccordionSearchFilter(
            context = this,
            options = listOf(
                AccordionSearchFilter.Option(FILTER_ALL, getString(R.string.filter_all)),
                AccordionSearchFilter.Option(FILTER_READY, getString(R.string.filter_ready)),
                AccordionSearchFilter.Option(FILTER_QUEUED, getString(R.string.filter_queued)),
                AccordionSearchFilter.Option(FILTER_ATTENTION, getString(R.string.filter_attention))
            )
        ) { query, filterId ->
            dashboardQuery = query
            dashboardFilter = filterId
            refreshDashboard()
        }.view
        parent.addView(filter, dashboardIndex, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(5)
            topMargin = dp(10)
            marginEnd = dp(5)
        })

        selectionActions = buildSelectionActions()
        parent.addView(selectionActions, dashboardIndex + 1)
    }

    private fun buildSelectionActions(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(5), dp(8), dp(5), dp(6))
        }
        selectionCount = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_primary))
            textSize = 13f
        }
        container.addView(selectionCount)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: Int, onClick: () -> Unit): Button = Button(this).apply {
            isAllCaps = false
            text = getString(label)
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.app_surface_raised))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.app_text_primary))
            setOnClickListener { onClick() }
        }

        row.addView(action(R.string.selection_close, ::confirmCloseSelected), buttonLp())
        selectionReviveButton = action(R.string.selection_revive, ::reviveSelected)
        row.addView(selectionReviveButton, buttonLp())
        row.addView(action(R.string.selection_favorite) { saveSelectedFavorites(false) }, buttonLp())
        row.addView(action(R.string.selection_private_favorite) { saveSelectedFavorites(true) }, buttonLp())
        row.addView(action(R.string.selection_done) { dashboardAdapter.clearSelection() }, buttonLp())

        container.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(5) })
        return container
    }

    private fun buttonLp(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        dp(42)
    ).apply { marginEnd = dp(5) }

    private fun updateSelectionActions(selected: List<VideoTabStore.VideoTab>) {
        if (!::selectionActions.isInitialized) return
        selectionActions.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        if (selected.isEmpty()) return
        selectionCount.text = getString(R.string.selection_count, selected.size)
        selectionReviveButton.isEnabled = selected.any { TabMaintenanceController.canRevive(this, it) }
        selectionReviveButton.alpha = if (selectionReviveButton.isEnabled) 1f else 0.5f
    }

    private fun confirmCloseSelected() {
        val selected = dashboardAdapter.selectedTabs()
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.close_selected_confirmation, selected.size))
            .setPositiveButton(R.string.close) { _, _ ->
                selected.forEach(::closeTab)
                dashboardAdapter.clearSelection()
                pruneThumbnailCache()
                refreshDashboard()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reviveSelected() {
        val candidates = dashboardAdapter.selectedTabs().filter { TabMaintenanceController.canRevive(this, it) }
        if (candidates.isEmpty()) return
        val queued = TabMaintenanceController.reviveAll(this, candidates)
        dashboardAdapter.clearSelection()
        refreshDashboard()
        Toast.makeText(this, getString(R.string.selected_revive_started, queued), Toast.LENGTH_LONG).show()
    }

    private fun saveSelectedFavorites(privateFavorites: Boolean) {
        val selected = dashboardAdapter.selectedTabs()
        if (selected.isEmpty()) return

        val savable = selected.mapNotNull { tab ->
            val url = TabOriginStore.pageUrl(this, tab).trim()
            if (!isHttpUrl(url)) null else Triple(tab, url, tab.title.trim().ifBlank { "Favorite" })
        }
        if (savable.isEmpty()) return

        if (!privateFavorites) {
            val saved = savable.count { (_, url, title) -> FavoriteStore.add(this, url, title) != null }
            dashboardAdapter.clearSelection()
            Toast.makeText(this, getString(R.string.selected_favorites_saved, saved), Toast.LENGTH_SHORT).show()
            return
        }

        PrivateFavoriteAuthenticator.authenticate(this, onSuccess = {
            val saved = savable.count { (_, url, title) ->
                PrivateFavoriteStore.addAfterAuthentication(this, url, title) != null
            }
            dashboardAdapter.clearSelection()
            Toast.makeText(this, getString(R.string.selected_private_favorites_saved, saved), Toast.LENGTH_SHORT).show()
        })
    }

    private fun closeTab(tab: VideoTabStore.VideoTab) {
        TabPreparationManager.cancelScheduled(applicationContext, tab.id)
        TabRevivalCoordinator.cancel(tab.id)
        VideoTabStore.close(tab.id)
        TabHealthStore.clear(this, tab.id)
        pruneThumbnailCache()
        dashboard.post { refreshDashboard() }
    }

    private fun attachPrivacyCurtain() {
        AppPrivacyController.attachIfNeeded(this) { acceptSharedUrl(intent) }
    }

    private fun acceptSharedUrl(intent: Intent) {
        if (AppPrivacyController.isLocked(this)) {
            attachPrivacyCurtain()
            return
        }
        val url = extractSharedHttpUrl(intent) ?: return
        urlInput.setText(url)
        resolveAndPlay(url)
    }

    private fun resolveAndPlay(url: String) {
        val cleanUrl = SourceLanguagePolicy.preferAppLanguage(this, url.trim())
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
                    Python.getInstance().getModule("resolver").callAttr("resolve", cleanUrl, "auto").toString()
                }
            }.onSuccess { json ->
                setBusy(false)
                status.text = ""
                browserResolveButton.visibility = View.GONE
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, json))
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
        val cleanUrl = SourceLanguagePolicy.preferAppLanguage(this, url.trim())
        if (!isHttpUrl(cleanUrl)) {
            expandManualSection()
            status.text = getString(R.string.status_complete_url)
            return
        }

        startActivity(Intent(this, BrowserResolverActivity::class.java)
            .putExtra(BrowserResolverActivity.EXTRA_URL, cleanUrl)
            .apply { if (!tabId.isNullOrBlank()) putExtra(BrowserResolverActivity.EXTRA_TAB_ID, tabId) })
    }

    private fun updateTabStatuses() {
        if (statusCheckRunning) return
        val tabs = VideoTabStore.allTabs()
        if (tabs.isEmpty()) return
        statusCheckRunning = true
        refreshDashboard()

        lifecycleScope.launch {
            val summary = TabStatusChecker.checkAll(this@MainActivity, tabs) {
                if (!isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refreshDashboard()
            }
            statusCheckRunning = false
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                refreshDashboard()
                Toast.makeText(this@MainActivity, getString(R.string.tab_status_summary, summary.ready, summary.needsRefresh, summary.unavailable), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reviveExpiredTabs() {
        val candidates = VideoTabStore.allTabs().filter { TabMaintenanceController.canRevive(this, it) }
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.no_expired_tabs, Toast.LENGTH_SHORT).show()
            return
        }
        val queued = TabMaintenanceController.reviveAll(this, candidates)
        refreshDashboard()
        Toast.makeText(this, getString(R.string.revive_started_count, queued), Toast.LENGTH_LONG).show()
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

    private fun showDashboardMenu() {
        DashboardMenu.show(this, DashboardMenu.Actions(
            checkStatus = ::updateTabStatuses,
            reviveExpired = ::reviveExpiredTabs,
            closeAll = ::confirmCloseAllTabs,
            lockApp = { AppPrivacyController.lock(this) }
        ))
    }

    private fun refreshDashboard() {
        if (!::dashboardAdapter.isInitialized) return
        val tabs = VideoTabStore.allTabs()
        tabs.forEach { TabOriginStore.ensureFallback(this, it) }
        visibleTabs = filterTabs(tabs)
        dashboardAdapter.submitTabs(visibleTabs)

        dashboardCount.text = if (visibleTabs.size == tabs.size) tabs.size.toString() else "${visibleTabs.size}/${tabs.size}"
        dashboard.visibility = if (visibleTabs.isEmpty()) View.GONE else View.VISIBLE
        dashboardEmptyState.visibility = if (tabs.isEmpty()) View.VISIBLE else View.GONE
        dashboardSwipeHint.visibility = if (visibleTabs.isEmpty()) View.GONE else View.VISIBLE

        maintenanceActions.visibility = View.GONE
        updateStatusButton.isEnabled = tabs.isNotEmpty() && !statusCheckRunning
        closeAllButton.isEnabled = tabs.isNotEmpty()
        reviveExpiredButton.isEnabled = tabs.any { TabMaintenanceController.canRevive(this, it) }
        reviveExpiredButton.alpha = if (reviveExpiredButton.isEnabled) 1f else 0.58f
    }

    private fun filterTabs(tabs: List<VideoTabStore.VideoTab>): List<VideoTabStore.VideoTab> {
        val query = dashboardQuery.trim().lowercase()
        return tabs.filter { tab ->
            val health = TabHealthStore.get(this, tab.id)
            val matchesFilter = when (dashboardFilter) {
                FILTER_READY -> tab.isReady && health.state !in setOf(TabHealthStore.State.NEEDS_REFRESH, TabHealthStore.State.UNAVAILABLE, TabHealthStore.State.NEEDS_ATTENTION)
                FILTER_QUEUED -> tab.preparationState in setOf(VideoTabStore.PreparationState.QUEUED, VideoTabStore.PreparationState.RESOLVING)
                FILTER_ATTENTION -> tab.preparationState in setOf(VideoTabStore.PreparationState.ERROR, VideoTabStore.PreparationState.NEEDS_ATTENTION) || health.state in setOf(TabHealthStore.State.NEEDS_REFRESH, TabHealthStore.State.UNAVAILABLE, TabHealthStore.State.NEEDS_ATTENTION)
                else -> true
            }
            if (!matchesFilter) return@filter false
            if (query.isBlank()) return@filter true
            val origin = TabOriginStore.pageUrl(this, tab)
            tab.title.lowercase().contains(query) || tab.sourceUrl.lowercase().contains(query) || origin.lowercase().contains(query)
        }
    }

    private fun restoreDashboardAnchorIfNeeded() {
        val tabId = DashboardReturnState.consume() ?: return
        val position = visibleTabs.indexOfFirst { it.id == tabId }
        if (position < 0) return
        dashboard.post {
            val item = dashboard.layoutManager?.findViewByPosition(position)
            if (item != null) {
                val y = (dashboard.top + item.top - dp(12)).coerceAtLeast(0)
                dashboardScroll.smoothScrollTo(0, y)
            }
        }
    }

    private fun performPrimaryAction(tab: VideoTabStore.VideoTab) {
        if (TabMaintenanceController.canRevive(this, tab)) {
            TabMaintenanceController.reviveOne(this, tab)
            refreshDashboard()
            return
        }
        when {
            tab.isReady -> startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                .putExtra(TabbedPlayerApplication.EXTRA_TAB_ID, tab.id))
            tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> launchBrowserResolver(TabOriginStore.pageUrl(this, tab), tab.id)
            tab.preparationState == VideoTabStore.PreparationState.ERROR -> {
                TabMaintenanceController.reviveOne(this, tab)
                refreshDashboard()
            }
            else -> Unit
        }
    }

    private fun toggleManualSection() {
        val show = manualCard.visibility != View.VISIBLE
        manualCard.visibility = if (show) View.VISIBLE else View.GONE
        manualToggle.text = getString(if (show) R.string.manual_section_hide else R.string.home_manual_section)
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

    private fun isHttpUrl(value: String): Boolean = value.startsWith("https://", true) || value.startsWith("http://", true)

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        resolveButton.isEnabled = !busy
        browserResolveButton.isEnabled = !busy
        urlInput.isEnabled = !busy
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private fun extractSharedHttpUrl(intent: Intent): String? {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
    return Regex("https?://\\S+").find(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value
        ?.trimEnd('.', ',', ')', ']', '}')
        ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
}
