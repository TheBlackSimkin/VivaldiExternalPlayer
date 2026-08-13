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
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.chaquo.python.android.PyApplication
import java.util.WeakHashMap

/**
 * Application-level coordinator for the first multi-video tab implementation.
 *
 * Why this lives above PlayerActivity:
 * - Batch 4 PlayerActivity/resolver behavior is already validated on the real
 *   playback targets, so the tab layer should not rewrite that logic.
 * - Every PlayerActivity still owns exactly one ExoPlayer at a time.
 * - Switching tabs recreates playback from the stored resolved-media payload,
 *   then restores position and play/pause state.
 *
 * Tabs are intentionally process-local in this first implementation. Whether
 * tabs should survive a complete process/app restart remains a separate product
 * decision, exactly as requested in PROJECT_STATE.md.
 */
class TabbedPlayerApplication : PyApplication(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val EXTRA_TAB_ID = "video_tab_id"
        private const val TAB_BUTTON_TAG = "vivaldi_external_player_tabs_button"
    }

    /** Associate each live PlayerActivity instance with its logical video tab. */
    private val activityTabs = WeakHashMap<Activity, String>()

    /**
     * Browser-assisted resolution already has the real page title locally inside
     * its WebView. Keep only the latest local value long enough to name the next
     * video tab. The title is never uploaded or sent outside the app.
     */
    private var lastBrowserPageTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is PlayerActivity) return

        val suppliedTabId = activity.intent.getStringExtra(EXTRA_TAB_ID)
        val originalJson = activity.intent.getStringExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA)
            ?: return

        /*
         * A newly created browser-assisted tab should prefer the page title that
         * was already available in the resolver WebView. Existing tabs already
         * carry their saved title in their stored JSON and must not be renamed.
         */
        val tabJson = if (suppliedTabId == null) {
            withLocalBrowserTitle(originalJson)
        } else {
            originalJson
        }

        val tabId = suppliedTabId
            ?.takeIf { VideoTabStore.get(it) != null }
            ?: VideoTabStore.createTab(tabJson).id

        activityTabs[activity] = tabId

        // Add a small tab-switcher button without changing the validated player XML.
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
         * PlayerActivity prepares its source during onCreate. Media3 accepts a
         * seek before STATE_READY, so restoring here works for both progressive
         * and adaptive sources without waiting for a second callback.
         */
        activity.window.decorView.post {
            val player = findPlayer(activity) ?: return@post
            if (tab.positionMs > 0L) {
                player.seekTo(tab.positionMs)
            }
            player.playWhenReady = tab.playWhenReady
        }
    }

    override fun onActivityPaused(activity: Activity) {
        when (activity) {
            is BrowserResolverActivity -> captureBrowserPageTitle(activity)
            is PlayerActivity -> saveActivityTab(activity)
        }
    }

    /**
     * Read only the normal WebView page title which is already present on-device.
     * This is metadata for the tab label; no page/video title is transmitted.
     */
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

    /** Prefer the locally captured WebView page title for a new browser tab. */
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

    /** Save position/play state and, when available, the currently selected quality source. */
    private fun saveActivityTab(activity: PlayerActivity) {
        val tabId = activityTabs[activity] ?: return
        val player = findPlayer(activity)

        /*
         * PlayerActivity keeps the current ResolvedMedia private because tabs did
         * not exist when it was written. Reading that one model field reflectively
         * lets this compatibility layer preserve a quality-switched source without
         * changing the validated playback class in the first tab batch.
         *
         * This is deliberately isolated here and can be replaced by an explicit
         * PlayerActivity session API in a later cleanup batch.
         */
        val resolved = runCatching {
            val field = PlayerActivity::class.java.getDeclaredField("currentResolved")
            field.isAccessible = true
            field.get(activity) as? ResolvedMedia
        }.getOrNull()

        val existing = VideoTabStore.get(tabId) ?: return
        val currentJson = resolved?.toJson() ?: existing.resolvedMediaJson

        /*
         * PlayerActivity may still hold the old generic browser title during the
         * first visit because this compatibility coordinator runs after its
         * onCreate. Preserve the better tab title already captured from WebView
         * instead of accidentally overwriting it during onPause.
         */
        val json = runCatching {
            val current = ResolvedMedia.fromJson(currentJson)
            if (
                current.resolverMode == "browser" &&
                existing.title.isNotBlank() &&
                current.title != existing.title
            ) {
                current.copy(title = existing.title).toJson()
            } else {
                currentJson
            }
        }.getOrDefault(currentJson)

        VideoTabStore.update(
            id = tabId,
            resolvedMediaJson = json,
            positionMs = player?.currentPosition ?: existing.positionMs,
            playWhenReady = player?.playWhenReady ?: existing.playWhenReady
        )
    }

    /** Find the Media3 Player through the public PlayerView API. */
    private fun findPlayer(activity: PlayerActivity): Player? =
        activity.findViewById<PlayerView>(R.id.player_view)?.player

    /** Overlay a compact tab button at the top center of the existing player. */
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
        button.text = activity.getString(R.string.tabs_button_count, VideoTabStore.allTabs().size)
    }

    /**
     * Vivaldi-like conceptual tab list: select a row to switch; use the × button
     * on that row to close only that video tab.
     */
    private fun showTabSwitcher(activity: PlayerActivity) {
        saveActivityTab(activity)

        val currentId = activityTabs[activity] ?: return
        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(activity), 8.dp(activity), 12.dp(activity), 8.dp(activity))
        }

        fun rebuildRows(dialog: AlertDialog?) {
            rows.removeAllViews()
            val tabs = VideoTabStore.allTabs()

            tabs.forEach { tab ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val selectButton = Button(activity).apply {
                    isAllCaps = false
                    text = if (tab.id == currentId) {
                        activity.getString(R.string.tab_active_title, tab.title)
                    } else {
                        tab.title
                    }
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setOnClickListener {
                        dialog?.dismiss()
                        if (tab.id != currentId) {
                            openTab(activity, tab)
                        }
                    }
                }

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
                            rebuildRows(dialog)
                            updateTabButton(activity)
                        }
                    }
                }

                row.addView(
                    selectButton,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(
                    closeButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                rows.addView(row)
            }
        }

        val scroll = ScrollView(activity).apply {
            addView(rows)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.video_tabs)
            .setView(scroll)
            .setNegativeButton(R.string.close, null)
            .create()

        rebuildRows(dialog)
        dialog.show()
    }

    private fun openTab(activity: PlayerActivity, tab: VideoTabStore.VideoTab) {
        saveActivityTab(activity)

        activity.startActivity(
            Intent(activity, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, tab.resolvedMediaJson)
                .putExtra(EXTRA_TAB_ID, tab.id)
        )
        activity.finish()
    }

    private fun closeActiveTab(activity: PlayerActivity, currentId: String) {
        val next = VideoTabStore.neighborAfterClose(currentId)

        if (next == null) {
            /*
             * The resolver/MainActivity can still exist underneath the player in
             * Android's task stack. Merely calling finish() exposed that old
             * resolver screen again, which looked like the closed tab was being
             * reopened. Clear back to MainActivity instead, using a neutral
             * Intent with no ACTION_SEND so the old shared URL is not resolved
             * again.
             */
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            activity.finish()
            return
        }

        activity.startActivity(
            Intent(activity, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, next.resolvedMediaJson)
                .putExtra(EXTRA_TAB_ID, next.id)
        )
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
