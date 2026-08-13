package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Compatibility key exposed through BrowserResolverActivity's companion so
 * callers can associate foreground browser completion with an existing tab.
 */
val BrowserResolverActivity.Companion.EXTRA_TAB_ID: String
    get() = TabbedPlayerApplication.EXTRA_TAB_ID

/**
 * Registers before normal Activities are created and carries a pending tab ID
 * from BrowserResolverActivity to the PlayerActivity it launches.
 */
class PendingBrowserTabBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(PendingBrowserTabLifecycle)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
}

private object PendingBrowserTabLifecycle : Application.ActivityLifecycleCallbacks {
    private var pendingTabId: String? = null

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        when (activity) {
            is BrowserResolverActivity -> {
                /*
                 * Every browser-assisted session must begin with a clean title
                 * cache. Without this reset, a very fast second resolver session
                 * could reach PlayerActivity before WebView publishes its new
                 * page title and accidentally reuse the previous tab's title.
                 *
                 * The title field is intentionally private to the application
                 * coordinator. This small compatibility bridge uses reflection
                 * rather than widening that API only for one lifecycle reset.
                 * Failure is harmless: the normal resolver title/fallback remains.
                 */
                clearCachedBrowserTitle(activity)

                pendingTabId = activity.intent
                    .getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
                    ?.takeIf { it.isNotBlank() }
            }

            is PlayerActivity -> {
                val tabId = pendingTabId ?: return
                if (!activity.intent.hasExtra(TabbedPlayerApplication.EXTRA_TAB_ID)) {
                    activity.intent.putExtra(TabbedPlayerApplication.EXTRA_TAB_ID, tabId)
                }
                pendingTabId = null
            }
        }
    }

    private fun clearCachedBrowserTitle(activity: BrowserResolverActivity) {
        val application = activity.application as? TabbedPlayerApplication ?: return

        runCatching {
            val field = TabbedPlayerApplication::class.java
                .getDeclaredField("lastBrowserPageTitle")
            field.isAccessible = true
            field.set(application, "")
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is BrowserResolverActivity && activity.isFinishing) {
            pendingTabId = null
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
