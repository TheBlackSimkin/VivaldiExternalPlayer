package com.example.vivaldiplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry screen for the app.
 *
 * Preferred real-world flow:
 * Vivaldi -> Android Share -> this Activity -> resolver -> PlayerActivity.
 *
 * The manual URL field remains because it is useful while debugging websites
 * whose own player does not expose an "open in external app" action.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    /**
     * Remember the most recent URL which failed in yt-dlp. If the user returns
     * from the WebView fallback, the visible retry button can reopen that same URL.
     */
    private var lastFailedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind XML views once so the rest of the code can use readable names.
        urlInput = findViewById(R.id.url_input)
        resolveButton = findViewById(R.id.resolve_button)
        browserResolveButton = findViewById(R.id.browser_resolve_button)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        resolveButton.setOnClickListener {
            resolveAndPlay(urlInput.text.toString())
        }

        browserResolveButton.setOnClickListener {
            val url = lastFailedUrl ?: urlInput.text.toString().trim()
            launchBrowserResolver(url)
        }

        /*
         * Process the original share Intent only on the first creation. This
         * prevents an Activity recreation from resolving the same shared URL twice.
         */
        if (savedInstanceState == null) {
            acceptSharedUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedUrl(intent)
    }

    /**
     * Browsers may share "page title + URL", not just a bare URL. Extract the
     * first normal HTTP(S) address from the shared text.
     */
    private fun acceptSharedUrl(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") {
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        val url = Regex("https?://\\S+")
            .find(sharedText)
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
            ?: return

        urlInput.setText(url)
        resolveAndPlay(url)
    }

    /**
     * First attempt: ask yt-dlp (running through Chaquopy) to resolve the page.
     *
     * IMPORTANT Batch 1 change:
     * If yt-dlp fails, we no longer wait for the user to discover a fallback
     * button. We automatically open BrowserResolverActivity. That is a DIFFERENT
     * resolver path, so it is not simply repeating the same 410/403 request.
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
        status.text = getString(R.string.status_resolving)

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
            }.onFailure { error ->
                setBusy(false)
                lastFailedUrl = cleanUrl
                browserResolveButton.visibility = View.VISIBLE

                /*
                 * Keep the raw technical error selectable on MainActivity. It is
                 * valuable when the user copies a phone failure back into chat.
                 */
                val technical = error.message ?: error.toString()

                status.text = buildString {
                    appendLine(getString(R.string.status_direct_failed))
                    appendLine()
                    appendLine(technical)
                    appendLine()
                    appendLine(getString(R.string.status_browser_fallback_explanation))
                    appendLine()
                    append(getString(R.string.status_opening_browser_fallback))
                }

                // Open the NEW fallback path immediately instead of waiting for another tap.
                launchBrowserResolver(cleanUrl)
            }
        }
    }

    /** Launch the user-driven WebView resolver for a normal web address. */
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

    /** Shared URL validation helper. */
    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    /** Disable editable controls while Python/yt-dlp is doing network work. */
    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        resolveButton.isEnabled = !busy
        browserResolveButton.isEnabled = !busy
        urlInput.isEnabled = !busy
    }
}
