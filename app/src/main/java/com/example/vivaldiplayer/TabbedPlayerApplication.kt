package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.chaquo.python.android.PyApplication
import java.util.Locale
import java.util.WeakHashMap

/**
 * Application-level tab coordinator.
 *
 * Responsibilities now include:
 * - initialize the persistent tab store;
 * - resume queued WorkManager preparation after process restart;
 * - map each live PlayerActivity to one logical tab;
 * - restore position/foreground play intent;
 * - display a richer tab switcher with preparation state;
 * - route unfinished tabs to background retry or foreground browser assistance;
 * - preserve the validated one-ExoPlayer-at-a-time playback architecture.
 */
class TabbedPlayerApplication : PyApplication(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val EXTRA_TAB_ID = "video_tab_id"
        private const val TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
    }

    private val activityTabs = WeakHashMap<Activity, String>()

    /** Last page title observed locally inside the resolver WebView. */
    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()

        VideoTabStore.initialize(this)
        TabPreparationManager.resumePending(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is PlayerActivity) return

        val suppliedTabId = activity.intent.getStringExtra(EXTRA_TAB_ID)
        val originalJson = activity.intent.getStringExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA)
            ?: return

        val tabJson = withLocalBrowserTitle(originalJson)

        val tabId = if (suppliedTabId != null && VideoTabStore.get(suppliedTabId) != null) {
            /*
             * A NEEDS_ATTENTION tab may have just completed through
             * BrowserResolverActivity. Store that resolved payload in the SAME
             * persistent tab instead of creating a duplicate.
             */
            VideoTabStore.markReady(suppliedTabId, tabJson)
            suppliedTabId
        } else {
            VideoTabStore.createTab(tabJson).id
        }

        activityTabs[activity] = tabId

        activity.window.decorView.post {
            attachTabButton(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is PlayerActivity) return

        val tabId = activityTabs[activity] ?: return
        val tab = VideoTabStore.get(tabId) ?: return

        updateTabButton(activity)

        /*
         * PlayerActivity prepares during onCreate. Its explicit restore method
         * accepts a seek before STATE_READY and also remembers whether playback
         * should resume only while the Activity is foregrounded.
         */
        activity.window.decorView.post {
            activity.restoreTabSession(tab.positionMs, tab.playWhenReady)
        }

        TabPreparationManager.preloadNext(activity.applicationContext, tabId)
    }

    override fun onActivityPaused(activity: Activity) {
        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    /** Read only local WebView page metadata for the tab label. */
    private fun captureBrowserPageTitle(activity: BrowserResolverActivity) {
        val title = activity
            .findViewById<WebView>(R.id.browser_web_view)
            ?.title
            ?.trim()
            .orEmpty()

        if (title.isNotBlank()) {
            lastBrowserPageTitle = title
        }
    }

    /** Prefer a useful locally captured browser title for browser-resolved JSON. */
    private fun withLocalBrowserTitle(json: String): String {
        val pageTitle = lastBrowserPageTitle.trim()
        if (pageTitle.isBlank()) return json

        return runCatching {
            val resolved = ResolvedMedia.fromJson(json)
            if (resolved.resolverMode != "browser") {
                json
            } else {
                resolved.copy(title = pageTitle).toJson()
            }
        }.getOrDefault(json)
    }

    /**
     * PlayerActivity exposes a small snapshot API so automatic lifecycle pause
     * can be distinguished from a deliberate user pause. This replaces the old
     * reflective access to private fields.
     */
    private fun saveActivityTab(activity: PlayerActivity) {
        val tabId = activityTabs[activity] ?: return
        val existing = VideoTabStore.get(tabId) ?: return
        val snapshot = activity.tabSessionSnapshot()

        val json = runCatching {
            val current = ResolvedMedia.fromJson(snapshot.resolvedMediaJson)
            if (
                current.resolverMode == "browser" &&
                existing.title.isNotBlank() &&
                current.title != existing.title
            ) {
                current.copy(title = existing.title).toJson()
            } else {
                snapshot.resolvedMediaJson
            }
        }.getOrDefault(snapshot.resolvedMediaJson)

        VideoTabStore.update(
            id = tabId,
            resolvedMediaJson = json,
            positionMs = snapshot.positionMs,
            playWhenReady = snapshot.playWhenForeground
        )
    }

    /** Overlay a compact tab button without changing PlayerActivity's validated layout. */
    private fun attachTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(TAB_BUTTON_TAG) != null) return

        val button = Button(activity).apply {
            tag = TAB_BUTTON_TAG
            isAllCaps = false
            minWidth = 0
            minHeight = 40.dp(activity)
            setPadding(12.dp(activity), 0, 12.dp(activity), 0)
            textSize = 13f
            setOnClickListener { showTabSwitcher(activity) }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = 12.dp(activity)
        }

        activity.addContentView(button, params)
        updateTabButton(activity)
    }

    private fun updateTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val button = decor.findViewWithTag<Button>(TAB_BUTTON_TAG) ?: return
        val tabs = VideoTabStore.allTabs()
        val ready = tabs.count { it.isReady }
        button.text = activity.getString(R.string.tabs_button_ready_count, ready, tabs.size)
    }

    /**
     * More browser-like tab switcher. Each row shows title plus preparation,
     * position and quality information without exposing technical URLs.
     */
    private fun showTabSwitcher(activity: PlayerActivity) {
        saveActivityTab(activity)

        val currentId = activityTabs[activity] ?: return
        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(activity), 8.dp(activity), 12.dp(activity), 8.dp(activity))
        }

        var dialog: AlertDialog? = null

        fun rebuildRows() {
            rows.removeAllViews()
            val tabs = VideoTabStore.allTabs()

            if (tabs.isEmpty()) {
                rows.addView(TextView(activity).apply {
                    text = activity.getString(R.string.no_video_tabs)
                    setPadding(12.dp(activity), 18.dp(activity), 12.dp(activity), 18.dp(activity))
                })
                return
            }

            tabs.forEach { tab ->
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(4.dp(activity), 4.dp(activity), 4.dp(activity), 8.dp(activity))
                }

                val textColumn = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val titleButton = Button(activity).apply {
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    text = if (tab.id == currentId) {
                        activity.getString(R.string.tab_active_title, tab.title)
                    } else {
                        tab.title
                    }
                    setOnClickListener {
                        dialog?.dismiss()
                        if (tab.id != currentId) {
                            openTab(activity, tab)
                        }
                    }
                }

                val details = TextView(activity).apply {
                    text = buildTabSubtitle(activity, tab)
                    textSize = 12f
                    alpha = 0.82f
                    setPadding(14.dp(activity), 0, 10.dp(activity), 4.dp(activity))
                }

                textColumn.addView(
                    titleButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                textColumn.addView(details)

                val closeButton = Button(activity).apply {
                    isAllCaps = false
                    text = "×"
                    contentDescription = activity.getString(R.string.close_tab_named, tab.title)
                    minWidth = 48.dp(activity)
                    setOnClickListener {
                        if (tab.id == currentId) {
                            dialog?.dismiss()
                            closeActiveTab(activity, currentId)
                        } else {
                            VideoTabStore.close(tab.id)
                            rebuildRows()
                            updateTabButton(activity)
                        }
                    }
                }

                card.addView(
                    textColumn,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                card.addView(closeButton)
                rows.addView(card)
            }
        }

        val scroll = ScrollView(activity).apply {
            addView(rows)
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.video_tabs)
            .setView(scroll)
            .setNeutralButton(R.string.settings) { _, _ ->
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.close, null)
            .create()

        rebuildRows()
        dialog?.show()
    }

    private fun buildTabSubtitle(activity: Activity, tab: VideoTabStore.VideoTab): String {
        val state = when (tab.preparationState) {
            VideoTabStore.PreparationState.QUEUED -> activity.getString(R.string.tab_state_queued)
            VideoTabStore.PreparationState.RESOLVING -> activity.getString(R.string.tab_state_resolving)
            VideoTabStore.PreparationState.READY -> activity.getString(R.string.tab_state_ready)
            VideoTabStore.PreparationState.NEEDS_ATTENTION -> activity.getString(R.string.tab_state_needs_attention)
            VideoTabStore.PreparationState.ERROR -> activity.getString(R.string.tab_state_error)
        }

        val details = mutableListOf(state)
        if (tab.positionMs > 0) details += formatPosition(tab.positionMs)

        if (tab.resolvedMediaJson.isNotBlank()) {
            runCatching { ResolvedMedia.fromJson(tab.resolvedMediaJson) }
                .getOrNull()
                ?.displayedHeight
                ?.takeIf { it > 0 }
                ?.let { details += "${it}p" }
        }

        return details.joinToString(" • ")
    }

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

    private fun openTab(activity: PlayerActivity, tab: VideoTabStore.VideoTab) {
        saveActivityTab(activity)

        when {
            tab.isReady -> {
                activity.startActivity(
                    Intent(activity, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                        .putExtra(EXTRA_TAB_ID, tab.id)
                )
                activity.finish()
            }

            tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> {
                activity.startActivity(
                    Intent(activity, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, tab.sourceUrl)
                        .putExtra(BrowserResolverActivity.EXTRA_TAB_ID, tab.id)
                )
                activity.finish()
            }

            tab.preparationState == VideoTabStore.PreparationState.ERROR -> {
                showTabPreparationError(activity, tab)
            }

            else -> {
                Toast.makeText(activity, R.string.tab_not_ready_yet, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Recovery for a failed background-preparation tab. */
    private fun showTabPreparationError(activity: PlayerActivity, tab: VideoTabStore.VideoTab) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.tab_state_error)
            .setMessage(R.string.tab_prepare_error_explanation)
            .setPositiveButton(R.string.retry) { _, _ ->
                TabPreparationManager.retry(activity.applicationContext, tab.id)
                Toast.makeText(activity, R.string.retry_scheduled, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.try_browser_method) { _, _ ->
                activity.startActivity(
                    Intent(activity, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, tab.sourceUrl)
                        .putExtra(BrowserResolverActivity.EXTRA_TAB_ID, tab.id)
                )
                activity.finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun closeActiveTab(activity: PlayerActivity, currentId: String) {
        val next = VideoTabStore.neighborAfterClose(currentId)

        if (next == null) {
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            activity.finish()
            return
        }

        when {
            next.isReady -> {
                activity.startActivity(
                    Intent(activity, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, next.resolvedMediaJson)
                        .putExtra(EXTRA_TAB_ID, next.id)
                )
            }

            next.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> {
                activity.startActivity(
                    Intent(activity, BrowserResolverActivity::class.java)
                        .putExtra(BrowserResolverActivity.EXTRA_URL, next.sourceUrl)
                        .putExtra(BrowserResolverActivity.EXTRA_TAB_ID, next.id)
                )
            }

            else -> {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
        activity.finish()
    }

    private fun Int.dp(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        activityTabs.remove(activity)
    }
}
