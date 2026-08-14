package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.chaquo.python.android.PyApplication
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.WeakHashMap

/**
 * Application-level persistent-tab coordinator.
 *
 * Playback remains one-ExoPlayer-at-a-time. The visual refresh in this class is
 * deliberately UI-only: source selection and browser candidate ranking remain
 * in their existing validated components.
 */
class TabbedPlayerApplication : PyApplication(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val EXTRA_TAB_ID = "video_tab_id"
        private const val TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
    }

    private val activityTabs = WeakHashMap<Activity, String>()
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
            VideoTabStore.markReady(suppliedTabId, tabJson)
            suppliedTabId
        } else {
            VideoTabStore.createTab(tabJson).id
        }

        activityTabs[activity] = tabId
        activity.window.decorView.post { attachTabButton(activity) }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is PlayerActivity) return

        val tabId = activityTabs[activity] ?: return
        val tab = VideoTabStore.get(tabId) ?: return
        updateTabButton(activity)

        activity.window.decorView.post {
            activity.restoreTabSession(tab.positionMs, tab.playWhenReady)
        }

        /*
         * Generate one stable pseudo-random local frame for the tab card. A small
         * delay keeps this cosmetic work away from the most timing-sensitive part
         * of initial playback startup. Failure is harmless and retries next resume.
         */
        activity.window.decorView.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                TabThumbnailCapture.captureIfMissing(activity, tabId)
            }
        }, 1_800L)

        TabPreparationManager.preloadNext(activity.applicationContext, tabId)
    }

    override fun onActivityPaused(activity: Activity) {
        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    private fun captureBrowserPageTitle(activity: BrowserResolverActivity) {
        val title = activity.findViewById<WebView>(R.id.browser_web_view)
            ?.title?.trim().orEmpty()
        if (title.isNotBlank()) lastBrowserPageTitle = title
    }

    private fun withLocalBrowserTitle(json: String): String {
        val pageTitle = lastBrowserPageTitle.trim()
        if (pageTitle.isBlank()) return json

        return runCatching {
            val resolved = ResolvedMedia.fromJson(json)
            if (resolved.resolverMode == "browser") {
                resolved.copy(title = pageTitle).toJson()
            } else json
        }.getOrDefault(json)
    }

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
            ) current.copy(title = existing.title).toJson() else snapshot.resolvedMediaJson
        }.getOrDefault(snapshot.resolvedMediaJson)

        VideoTabStore.update(
            id = tabId,
            resolvedMediaJson = json,
            positionMs = snapshot.positionMs,
            playWhenReady = snapshot.playWhenForeground
        )
    }

    /** Compact floating tabs control styled to match the refreshed dark UI. */
    private fun attachTabButton(activity: PlayerActivity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(TAB_BUTTON_TAG) != null) return

        val button = Button(activity).apply {
            tag = TAB_BUTTON_TAG
            isAllCaps = false
            minWidth = 0
            minHeight = 42.dp(activity)
            setPadding(14.dp(activity), 0, 14.dp(activity), 0)
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.app_surface_active)
            )
            setOnClickListener { showTabSwitcher(activity) }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = 12.dp(activity) }

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

    /** Thumbnail-card tab switcher. */
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
                    setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary))
                    setPadding(12.dp(activity), 20.dp(activity), 12.dp(activity), 20.dp(activity))
                })
                return
            }

            tabs.forEach { tab ->
                val active = tab.id == currentId
                val card = MaterialCardView(activity).apply {
                    radius = 16.dp(activity).toFloat()
                    cardElevation = 0f
                    strokeWidth = if (active) 2.dp(activity) else 1.dp(activity)
                    strokeColor = ContextCompat.getColor(
                        activity,
                        if (active) R.color.app_accent else R.color.app_outline
                    )
                    setCardBackgroundColor(
                        ContextCompat.getColor(
                            activity,
                            if (active) R.color.app_surface_active else R.color.app_surface
                        )
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog?.dismiss()
                        if (!active) openTab(activity, tab)
                    }
                }

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(10.dp(activity), 10.dp(activity), 8.dp(activity), 10.dp(activity))
                }

                val thumbnailCard = MaterialCardView(activity).apply {
                    radius = 12.dp(activity).toFloat()
                    cardElevation = 0f
                    setCardBackgroundColor(ContextCompat.getColor(activity, R.color.app_surface_raised))
                }

                val thumbnail = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = tab.title
                    val bitmap = TabThumbnailCache.load(activity, tab.id)
                    if (bitmap != null) {
                        setImageBitmap(bitmap)
                    } else {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setPadding(18.dp(activity), 10.dp(activity), 18.dp(activity), 10.dp(activity))
                        setImageResource(R.drawable.ic_launcher_foreground)
                        alpha = 0.62f
                    }
                }
                thumbnailCard.addView(
                    thumbnail,
                    FrameLayout.LayoutParams(112.dp(activity), 72.dp(activity))
                )

                val textColumn = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12.dp(activity), 0, 8.dp(activity), 0)
                }

                val titleView = TextView(activity).apply {
                    text = if (active) activity.getString(R.string.tab_active_title, tab.title) else tab.title
                    setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary))
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 2
                }

                val details = TextView(activity).apply {
                    text = buildTabSubtitle(activity, tab)
                    setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary))
                    textSize = 12f
                    setPadding(0, 5.dp(activity), 0, 0)
                }

                textColumn.addView(titleView)
                textColumn.addView(details)

                val closeButton = Button(activity).apply {
                    isAllCaps = false
                    text = "×"
                    textSize = 20f
                    contentDescription = activity.getString(R.string.close_tab_named, tab.title)
                    minWidth = 44.dp(activity)
                    minHeight = 44.dp(activity)
                    setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary))
                    backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.app_surface_raised)
                    )
                    setOnClickListener {
                        if (active) {
                            dialog?.dismiss()
                            closeActiveTab(activity, currentId)
                        } else {
                            VideoTabStore.close(tab.id)
                            TabThumbnailCache.delete(activity, tab.id)
                            rebuildRows()
                            updateTabButton(activity)
                        }
                    }
                }

                row.addView(thumbnailCard)
                row.addView(
                    textColumn,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(closeButton)
                card.addView(row)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10.dp(activity) }
                rows.addView(card, params)
            }
        }

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.app_background))
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
                .getOrNull()?.displayedHeight?.takeIf { it > 0 }
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

            else -> Toast.makeText(activity, R.string.tab_not_ready_yet, Toast.LENGTH_SHORT).show()
        }
    }

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
        TabThumbnailCache.delete(activity, currentId)

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
