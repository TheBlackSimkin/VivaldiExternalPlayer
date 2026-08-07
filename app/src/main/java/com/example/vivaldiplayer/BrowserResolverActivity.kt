package com.example.vivaldiplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class BrowserResolverActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL =
            "browser_resolver_url"

        private const val MAX_CANDIDATES =
            12
    }

    private data class StreamCandidate(
        val url: String,
        val mimeType: String,
        val typeLabel: String,
        val requestHeaders: Map<String, String>
    )

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var playDetectedButton: Button

    private lateinit var originalUrl: String

    private var webViewUserAgent: String =
        ""

    private val candidateLock =
        Any()

    private val candidates =
        mutableListOf<StreamCandidate>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        originalUrl =
            intent.getStringExtra(
                EXTRA_URL
            ).orEmpty().trim()

        if (
            !originalUrl.startsWith("https://") &&
            !originalUrl.startsWith("http://")
        ) {
            finish()
            return
        }

        /*
         * Install the Service Worker observer before
         * creating/inflating the WebView.
         *
         * We never return replacement content from this
         * observer. Returning null allows the browser's
         * request to continue normally.
         */
        ServiceWorkerController
            .getInstance()
            .setServiceWorkerClient(
                object : ServiceWorkerClient() {

                    override fun shouldInterceptRequest(
                        request: WebResourceRequest
                    ): WebResourceResponse? {

                        captureRequest(
                            request
                        )

                        return null
                    }
                }
            )

        setContentView(
            R.layout.activity_browser_resolver
        )

        webView =
            findViewById(
                R.id.browser_web_view
            )

        progress =
            findViewById(
                R.id.browser_progress
            )

        status =
            findViewById(
                R.id.browser_status
            )

        playDetectedButton =
            findViewById(
                R.id.play_detected_button
            )

        configureWebView()

        playDetectedButton.setOnClickListener {
            showCandidateChooser()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false

                        onBackPressedDispatcher
                            .onBackPressed()
                    }
                }
            }
        )

        status.text =
            "Loading page…"

        webView.loadUrl(
            originalUrl
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {

        val settings =
            webView.settings

        settings.javaScriptEnabled =
            true

        settings.domStorageEnabled =
            true

        settings.mediaPlaybackRequiresUserGesture =
            true

        settings.javaScriptCanOpenWindowsAutomatically =
            false

        settings.setSupportMultipleWindows(
            false
        )

        settings.allowFileAccess =
            false

        settings.allowContentAccess =
            false

        settings.mixedContentMode =
            WebSettings.MIXED_CONTENT_NEVER_ALLOW

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            settings.safeBrowsingEnabled =
                true
        }

        webViewUserAgent =
            settings.userAgentString
                .orEmpty()

        val cookieManager =
            CookieManager.getInstance()

        cookieManager.setAcceptCookie(
            true
        )

        /*
         * Embedded media may use a different host from
         * the top-level page. This applies only to this
         * app's WebView cookie store; it does not import
         * cookies from Vivaldi.
         */
        cookieManager.setAcceptThirdPartyCookies(
            webView,
            true
        )

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {

                    captureRequest(
                        request
                    )

                    /*
                     * Observation only.
                     * WebView handles the request itself.
                     */
                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    val scheme =
                        request.url
                            .scheme
                            ?.lowercase(
                                Locale.US
                            )

                    /*
                     * Keep navigation inside this browser
                     * resolver and block custom app schemes.
                     */
                    return (
                        scheme != "http" &&
                        scheme != "https"
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    super.onPageFinished(
                        view,
                        url
                    )

                    if (
                        candidateCount() == 0
                    ) {
                        status.text =
                            "Page loaded. Start playback in this browser view. " +
                            "The app will observe normal media requests."
                    }
                }
            }

        webView.webChromeClient =
            object : WebChromeClient() {

                override fun onProgressChanged(
                    view: WebView,
                    newProgress: Int
                ) {
                    super.onProgressChanged(
                        view,
                        newProgress
                    )

                    progress.progress =
                        newProgress

                    progress.visibility =
                        if (
                            newProgress >= 100
                        ) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
                }
            }
    }

    private fun captureRequest(
        request: WebResourceRequest
    ) {

        if (
            !request.method.equals(
                "GET",
                ignoreCase = true
            )
        ) {
            return
        }

        val uri =
            request.url

        val scheme =
            uri.scheme
                ?.lowercase(
                    Locale.US
                )

        if (
            scheme != "https" &&
            scheme != "http"
        ) {
            return
        }

        val classification =
            classifyMediaUrl(uri)
                ?: return

        val candidate =
            StreamCandidate(
                url =
                    uri.toString(),
                mimeType =
                    classification.second,
                typeLabel =
                    classification.first,
                requestHeaders =
                    request.requestHeaders
                        .toMap()
            )

        val count =
            synchronized(
                candidateLock
            ) {

                candidates.removeAll {
                    it.url ==
                    candidate.url
                }

                /*
                 * Newest candidate first.
                 */
                candidates.add(
                    0,
                    candidate
                )

                while (
                    candidates.size >
                    MAX_CANDIDATES
                ) {
                    candidates.removeAt(
                        candidates.lastIndex
                    )
                }

                candidates.size
            }

        runOnUiThread {

            if (
                isFinishing ||
                isDestroyed
            ) {
                return@runOnUiThread
            }

            playDetectedButton.visibility =
                View.VISIBLE

            playDetectedButton.isEnabled =
                true

            playDetectedButton.text =
                "Play detected stream ($count)"

            status.text =
                "Detected $count media request(s). " +
                "Start playback in the page if needed, " +
                "then tap Play detected stream."
        }
    }

    private fun classifyMediaUrl(
        uri: Uri
    ): Pair<String, String>? {

        val full =
            uri.toString()
                .lowercase(
                    Locale.US
                )

        val path =
            uri.path
                .orEmpty()
                .lowercase(
                    Locale.US
                )

        val query =
            uri.encodedQuery
                .orEmpty()
                .lowercase(
                    Locale.US
                )

        return when {

            path.endsWith(".m3u8") ||
            full.contains(".m3u8?") ||
            query.contains("format=m3u8") -> {

                "HLS" to
                    "application/x-mpegURL"
            }

            path.endsWith(".mpd") ||
            full.contains(".mpd?") ||
            query.contains("format=mpd") -> {

                "DASH" to
                    "application/dash+xml"
            }

            path.endsWith(".mp4") ||
            full.contains(".mp4?") ||
            query.contains(
                "mime=video%2fmp4"
            ) ||
            query.contains(
                "mime=video/mp4"
            ) ||
            query.contains(
                "type=video%2fmp4"
            ) ||
            query.contains(
                "type=video/mp4"
            ) -> {

                "MP4" to
                    "video/mp4"
            }

            path.endsWith(".webm") ||
            full.contains(".webm?") ||
            query.contains(
                "mime=video%2fwebm"
            ) ||
            query.contains(
                "mime=video/webm"
            ) -> {

                "WebM" to
                    "video/webm"
            }

            else ->
                null
        }
    }

    private fun candidateCount():
        Int =
        synchronized(
            candidateLock
        ) {
            candidates.size
        }

    private fun candidateSnapshot():
        List<StreamCandidate> =
        synchronized(
            candidateLock
        ) {
            candidates.toList()
        }

    private fun showCandidateChooser() {

        val snapshot =
            candidateSnapshot()

        if (snapshot.isEmpty()) {
            status.text =
                "No supported media request has been detected yet."

            return
        }

        if (snapshot.size == 1) {
            launchCandidate(
                snapshot.first()
            )
            return
        }

        val labels =
            snapshot.mapIndexed {
                index,
                candidate ->

                val host =
                    runCatching {
                        Uri.parse(
                            candidate.url
                        ).host
                    }.getOrNull()
                        ?: "media host"

                "${index + 1}. " +
                    "${candidate.typeLabel} • " +
                    host
            }.toTypedArray()

        AlertDialog
            .Builder(this)
            .setTitle(
                "Detected media requests"
            )
            .setItems(
                labels
            ) { dialog, which ->

                dialog.dismiss()

                launchCandidate(
                    snapshot[which]
                )
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun launchCandidate(
        candidate: StreamCandidate
    ) {

        val headers =
            linkedMapOf<String, String>()

        findHeader(
            candidate.requestHeaders,
            "User-Agent"
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["User-Agent"] =
                    it
            }

        if (
            !headers.containsKey(
                "User-Agent"
            ) &&
            webViewUserAgent.isNotBlank()
        ) {
            headers["User-Agent"] =
                webViewUserAgent
        }

        findHeader(
            candidate.requestHeaders,
            "Referer"
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["Referer"] =
                    it
            }

        if (
            !headers.containsKey(
                "Referer"
            )
        ) {
            headers["Referer"] =
                originalUrl
        }

        findHeader(
            candidate.requestHeaders,
            "Origin"
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["Origin"] =
                    it
            }

        findHeader(
            candidate.requestHeaders,
            "Accept"
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["Accept"] =
                    it
            }

        findHeader(
            candidate.requestHeaders,
            "Accept-Language"
        )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["Accept-Language"] =
                    it
            }

        /*
         * Only use cookies belonging to this app's
         * WebView session for the media URL.
         *
         * No Vivaldi cookie import is performed.
         */
        CookieManager
            .getInstance()
            .getCookie(
                candidate.url
            )
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                headers["Cookie"] =
                    it
            }

        val headerJson =
            JSONObject()

        headers.forEach {
            key,
            value ->

            headerJson.put(
                key,
                value
            )
        }

        val media =
            JSONObject()
                .put(
                    "url",
                    candidate.url
                )
                .put(
                    "mime_type",
                    candidate.mimeType
                )
                .put(
                    "protocol",
                    when (
                        candidate.typeLabel
                    ) {
                        "HLS" ->
                            "m3u8_native"

                        "DASH" ->
                            "dash"

                        else ->
                            "https"
                    }
                )
                .put(
                    "height",
                    JSONObject.NULL
                )
                .put(
                    "width",
                    JSONObject.NULL
                )
                .put(
                    "format_id",
                    "browser"
                )
                .put(
                    "headers",
                    headerJson
                )

        val root =
            JSONObject()
                .put(
                    "mode",
                    "single"
                )
                .put(
                    "title",
                    "Browser stream"
                )
                .put(
                    "webpage_url",
                    originalUrl
                )
                .put(
                    "requested_quality",
                    "browser"
                )
                .put(
                    "resolver_mode",
                    "browser"
                )
                .put(
                    "media",
                    media
                )

        startActivity(
            Intent(
                this,
                PlayerActivity::class.java
            ).putExtra(
                PlayerActivity.EXTRA_RESOLVED_MEDIA,
                root.toString()
            )
        )
    }

    private fun findHeader(
        headers: Map<String, String>,
        wantedName: String
    ): String? {

        return headers.entries
            .firstOrNull {
                it.key.equals(
                    wantedName,
                    ignoreCase = true
                )
            }
            ?.value
    }

    override fun onDestroy() {

        ServiceWorkerController
            .getInstance()
            .setServiceWorkerClient(
                null
            )

        if (
            ::webView.isInitialized
        ) {
            webView.stopLoading()

            webView.loadUrl(
                "about:blank"
            )

            webView.clearHistory()

            webView.removeAllViews()

            webView.destroy()
        }

        super.onDestroy()
    }
}
