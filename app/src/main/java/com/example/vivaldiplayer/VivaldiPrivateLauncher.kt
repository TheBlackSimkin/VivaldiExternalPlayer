package com.example.vivaldiplayer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Opens Vivaldi's genuine exported private-tab launcher and copies the tab's
 * permanent original page URL for the user to paste there.
 *
 * Device inspection of Vivaldi 8.1 showed that its exported private launcher
 * deliberately opens a blank private tab and ignores an incoming URL. A normal
 * ACTION_VIEW opens a regular tab instead. This helper therefore never claims
 * that it can navigate a private tab automatically; it preserves privacy by
 * opening the real private context and making the required paste step explicit.
 */
object VivaldiPrivateLauncher {
    private const val VIVALDI_PACKAGE = "com.vivaldi.browser"
    private const val PRIVATE_ACTION =
        "org.chromium.chrome.browser.incognito.OPEN_PRIVATE_TAB"
    private const val PRIVATE_ACTIVITY =
        "org.chromium.chrome.browser.incognito.IncognitoTabLauncher"

    fun openPrivateAndCopyOriginalUrl(activity: PlayerActivity) {
        val tabId = activity.intent
            .getStringExtra(TabbedPlayerApplication.EXTRA_TAB_ID)
            ?.takeIf { it.isNotBlank() }
        val tab = tabId?.let(VideoTabStore::get)
        val originalPageUrl = tab
            ?.let { TabOriginStore.pageUrl(activity, it) }
            ?.trim()
            .orEmpty()
            .ifBlank { tab?.sourceUrl?.trim().orEmpty() }

        if (!isHttpUrl(originalPageUrl)) {
            Toast.makeText(
                activity,
                R.string.vivaldi_private_original_url_unavailable,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                activity.getString(R.string.vivaldi_private_clip_label),
                originalPageUrl
            )
        )

        val privateIntent = Intent(PRIVATE_ACTION).apply {
            component = ComponentName(VIVALDI_PACKAGE, PRIVATE_ACTIVITY)
        }

        try {
            activity.startActivity(privateIntent)
            Toast.makeText(
                activity,
                R.string.vivaldi_private_url_copied_paste,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                R.string.vivaldi_private_unavailable,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                activity,
                R.string.vivaldi_private_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
