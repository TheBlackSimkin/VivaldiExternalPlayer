package com.example.vivaldiplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * User-visible browser fallback used when yt-dlp cannot resolve the page directly.
 *
 * Important design boundary:
 * - this is a real Android WebView;
 * - the user loads/interacts with the page normally;
 * - requests are OBSERVED, not replaced;
 * - Vivaldi cookies/passwords are not imported;
 * - no DRM license handling is configured here.
 *
 * The purpose is to discover an ordinary non-DRM media URL which the page itself
 * already asked the browser to use, then hand that URL to Media3.
 */
class BrowserResolverActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "browser_resolver_url"

        /** Keep the chooser small enough to remain usable on a phone. */
        private const val MAX_CANDIDATES = 16

        /**
         * The page probe is deliberately slow and lightweight. It is not trying
         * to scrape the page continuously; it only catches media URLs which may
         * appear after JavaScript initializes a player.
         */
        private const val PAGE_PROBE_INTERVAL_MS = 1_250L
    }

    /**
     * A possible media stream discovered by network observation or by examining
     * normal browser/page state. mimeType may be null for extensionless direct
     * progressive URLs; Media3 can sometimes infer those itself.
     */
    private data class StreamCandidate(
        val url: String,
        val mimeType: String?,
        val typeLabel: String,
        val requestHeaders: Map<String, String>,
        val discoveredBy: DiscoverySource
    )

    /** Used only to make candidate descriptions understandable to the user. */
    private enum class DiscoverySource {
        NETWORK,
        PAGE,
        PERFORMANCE
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var playDetectedButton: Button
    private lateinit var originalUrl: String

    /** Keep WebView's genuine user agent so Media3 can reuse it for the media request. */
    private var webViewUserAgent: String = ""

    /** `shouldInterceptRequest` can run off the UI thread, so candidate access is synchronized. */
    private val candidateLock = Any()
    private val candidates = mutableListOf<StreamCandidate>()

    /** Main-thread timer which periodically checks page-visible media sources. */
    private val probeHandler = Handler(Looper.getMainLooper())

    private val probeRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && ::webView.isInitialized) {
                probePageForMedia()
                probeHandler.postDelayed(this, PAGE_PROBE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        originalUrl = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        if (!isHttpUrl(originalUrl)) {
            finish()
            return
        }

        /*
         * Service workers can fetch manifests/segments without those requests
         * passing through the ordinary WebViewClient callback. Observing both
         * places improves coverage while still returning null so WebView handles
         * every request normally.
         */
        ServiceWorkerController.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    captureNetworkRequest(request)
                    return null
                }
            }
        )

        setContentView(R.layout.activity_browser_resolver)

        webView = findViewById(R.id.browser_web_view)
        progress = findViewById(R.id.browser_progress)
        status = findViewById(R.id.browser_status)
        playDetectedButton = findViewById(R.id.play_detected_button)

        configureWebView()

        playDetectedButton.setOnClickListener {
            showCandidateChooser()
        }

        /*
         * Back first navigates inside the embedded browser. Only when there is
         * no browser history left does it close this Activity.
         */
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        status.text = getString(R.string.browser_loading)
        webView.loadUrl(originalUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings

        /* Modern video pages usually require JavaScript and DOM storage. */
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        /* The user must intentionally start media; the app does not auto-play it. */
        settings.mediaPlaybackRequiresUserGesture = true

        /* Reduce unwanted popup/custom-scheme behavior inside the resolver. */
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        /*
         * Do NOT replace this with a forged Vivaldi/Chrome UA during this phase.
         * We intentionally use WebView's real UA and real browser engine.
         */
        webViewUserAgent = settings.userAgentString.orEmpty()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        /*
         * A video CDN may be a different host from the page. Third-party cookies
         * here belong ONLY to this app's WebView session, not to Vivaldi.
         */
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                captureNetworkRequest(request)
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme?.lowercase(Locale.US)

                // Keep normal web navigation; block custom app schemes from escaping this Activity.
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // JavaScript players may create the real media element only after page load.
                probePageForMedia()

                if (candidateCount() == 0) {
                    status.text = getString(R.string.browser_page_loaded)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    /**
     * Observe GET requests made by WebView. We intentionally ignore non-GET
     * requests and non-web schemes because they cannot be handed directly to
     * Media3 as a normal media URL.
     */
    private fun captureNetworkRequest(request: WebResourceRequest) {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return
        }

        val uri = request.url
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "https" && scheme != "http") {
            return
        }

        val classification = classifyMediaUrl(
            url = uri.toString(),
            mimeHint = null,
            allowUnknownDirect = false
        ) ?: return

        addCandidate(
            StreamCandidate(
                url = uri.toString(),
                mimeType = classification.second,
                typeLabel = classification.first,
                requestHeaders = request.requestHeaders.toMap(),
                discoveredBy = DiscoverySource.NETWORK
            )
        )
    }

    /**
     * JavaScript-side observation complements `shouldInterceptRequest`.
     *
     * Why this helps:
     * - a page can expose a direct `<video src>` URL without a helpful extension;
     * - a JavaScript player may request a manifest after page load;
     * - the browser Performance API can retain resource URLs which were easy to
     *   miss in a fast network callback.
     *
     * The script only READS normal page/browser state. It does not click, submit,
     * alter DOM content, solve a challenge, or fetch a new URL itself.
     */
    private fun probePageForMedia() {
        if (!::webView.isInitialized) {
            return
        }

        val script = """
            (function() {
                const found = [];

                function add(url, mime, source, allowUnknown) {
                    if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) {
                        return;
                    }

                    found.push({
                        url: url,
                        mime: (typeof mime === 'string') ? mime : '',
                        source: source,
                        allowUnknown: !!allowUnknown
                    });
                }

                // Strong signal: URLs actually attached to VIDEO/SOURCE elements.
                document.querySelectorAll('video').forEach(function(video) {
                    add(video.currentSrc || video.src, video.type || '', 'page', true);
                });

                document.querySelectorAll('source').forEach(function(source) {
                    add(source.src, source.type || '', 'page', true);
                });

                // Secondary signal: media/manifest-looking resources loaded by the page.
                try {
                    performance.getEntriesByType('resource').forEach(function(entry) {
                        const url = entry.name || '';
                        const initiator = String(entry.initiatorType || '').toLowerCase();
                        const looksLikeMedia =
                            /\.(m3u8|mpd|mp4|webm)(\?|$)/i.test(url) ||
                            /(?:manifest|playlist|format=m3u8|format=mpd)/i.test(url) ||
                            initiator === 'video';

                        if (looksLikeMedia) {
                            add(url, '', 'performance', initiator === 'video');
                        }
                    });
                } catch (ignored) {
                    // Some pages restrict Performance API details. Network observation still works.
                }

                // Keep the payload bounded even on pages with a large resource history.
                return found.slice(-80);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (result.isNullOrBlank() || result == "null" || isFinishing || isDestroyed) {
                return@evaluateJavascript
            }

            runCatching {
                JSONArray(result)
            }.onSuccess { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    val mime = item.optString("mime").trim().takeIf { it.isNotBlank() }
                    val allowUnknown = item.optBoolean("allowUnknown", false)
                    val source = when (item.optString("source")) {
                        "performance" -> DiscoverySource.PERFORMANCE
                        else -> DiscoverySource.PAGE
                    }

                    if (!isHttpUrl(url)) {
                        continue
                    }

                    val classification = classifyMediaUrl(
                        url = url,
                        mimeHint = mime,
                        allowUnknownDirect = allowUnknown
                    ) ?: continue

                    addCandidate(
                        StreamCandidate(
                            url = url,
                            mimeType = classification.second,
                            typeLabel = classification.first,
                            requestHeaders = emptyMap(),
                            discoveredBy = source
                        )
                    )
                }
            }
        }
    }

    /**
     * Recognize transferable top-level media resources.
     *
     * We intentionally DO NOT add individual HLS/DASH segments (`.ts`, `.m4s`),
     * because Media3 needs the manifest or complete progressive file instead.
     */
    private fun classifyMediaUrl(
        url: String,
        mimeHint: String?,
        allowUnknownDirect: Boolean
    ): Pair<String, String?>? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val full = url.lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        val query = uri.encodedQuery.orEmpty().lowercase(Locale.US)
        val mime = mimeHint.orEmpty().lowercase(Locale.US)

        return when {
            mime.contains("mpegurl") ||
                path.endsWith(".m3u8") ||
                full.contains(".m3u8?") ||
                query.contains("format=m3u8") ||
                query.contains("type=application%2fx-mpegurl") -> {
                "HLS" to "application/x-mpegURL"
            }

            mime.contains("dash+xml") ||
                path.endsWith(".mpd") ||
                full.contains(".mpd?") ||
                query.contains("format=mpd") -> {
                "DASH" to "application/dash+xml"
            }

            mime == "video/mp4" ||
                path.endsWith(".mp4") ||
                full.contains(".mp4?") ||
                query.contains("mime=video%2fmp4") ||
                query.contains("mime=video/mp4") ||
                query.contains("type=video%2fmp4") ||
                query.contains("type=video/mp4") -> {
                "MP4" to "video/mp4"
            }

            mime == "video/webm" ||
                path.endsWith(".webm") ||
                full.contains(".webm?") ||
                query.contains("mime=video%2fwebm") ||
                query.contains("mime=video/webm") -> {
                "WebM" to "video/webm"
            }

            /*
             * A URL currently attached to a VIDEO element is itself a strong
             * signal even when the CDN uses an extensionless progressive URL.
             * Leave MIME null and let Media3 attempt normal type inference.
             */
            allowUnknownDirect -> {
                "DIRECT" to mimeHint
            }

            else -> null
        }
    }

    /**
     * De-duplicate candidates and prefer the copy which contains real network
     * headers. Those headers (especially Referer/User-Agent) can be necessary
     * for a CDN to accept the exact media request outside WebView.
     */
    private fun addCandidate(candidate: StreamCandidate) {
        val count = synchronized(candidateLock) {
            val existing = candidates.firstOrNull { it.url == candidate.url }

            val preferred = when {
                candidate.requestHeaders.isNotEmpty() -> candidate
                existing != null && existing.requestHeaders.isNotEmpty() -> existing
                else -> candidate
            }

            candidates.removeAll { it.url == candidate.url }
            candidates.add(0, preferred)

            while (candidates.size > MAX_CANDIDATES) {
                candidates.removeAt(candidates.lastIndex)
            }

            candidates.size
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }

            playDetectedButton.visibility = View.VISIBLE
            playDetectedButton.isEnabled = true
            playDetectedButton.text = getString(R.string.browser_play_detected_count, count)
            status.text = getString(R.string.browser_detected_status, count)
        }
    }

    private fun candidateCount(): Int = synchronized(candidateLock) {
        candidates.size
    }

    private fun candidateSnapshot(): List<StreamCandidate> = synchronized(candidateLock) {
        candidates.toList()
    }

    /** Show a chooser when a page requested several possible manifests/files. */
    private fun showCandidateChooser() {
        val snapshot = candidateSnapshot()

        if (snapshot.isEmpty()) {
            status.text = getString(R.string.browser_no_media)
            return
        }

        if (snapshot.size == 1) {
            launchCandidate(snapshot.first())
            return
        }

        val labels = snapshot.mapIndexed { index, candidate ->
            val host = runCatching { Uri.parse(candidate.url).host }
                .getOrNull()
                ?: getString(R.string.media_host_fallback)

            val source = when (candidate.discoveredBy) {
                DiscoverySource.NETWORK -> getString(R.string.browser_source_network)
                DiscoverySource.PAGE -> getString(R.string.browser_source_page)
                DiscoverySource.PERFORMANCE -> getString(R.string.browser_source_performance)
            }

            val type = if (candidate.typeLabel == "DIRECT") {
                getString(R.string.browser_type_direct)
            } else {
                candidate.typeLabel
            }

            "${index + 1}. $type • $host • $source"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.browser_candidate_title)
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                launchCandidate(snapshot[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Convert a detected browser request into the same small JSON contract used
     * by the yt-dlp resolver. PlayerActivity therefore does not need site logic.
     */
    private fun launchCandidate(candidate: StreamCandidate) {
        val headers = linkedMapOf<String, String>()

        // Prefer headers which came from the actual WebView request.
        findHeader(candidate.requestHeaders, "User-Agent")
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["User-Agent"] = it }

        if (!headers.containsKey("User-Agent") && webViewUserAgent.isNotBlank()) {
            headers["User-Agent"] = webViewUserAgent
        }

        findHeader(candidate.requestHeaders, "Referer")
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Referer"] = it }

        if (!headers.containsKey("Referer")) {
            headers["Referer"] = originalUrl
        }

        findHeader(candidate.requestHeaders, "Origin")
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Origin"] = it }

        findHeader(candidate.requestHeaders, "Accept")
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Accept"] = it }

        findHeader(candidate.requestHeaders, "Accept-Language")
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Accept-Language"] = it }

        /*
         * Only this app's WebView cookies are used. There is no attempt to read
         * or export cookies from Vivaldi.
         */
        CookieManager.getInstance().getCookie(candidate.url)
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Cookie"] = it }

        val headerJson = JSONObject()
        headers.forEach { (key, value) ->
            headerJson.put(key, value)
        }

        val protocol = when (candidate.typeLabel) {
            "HLS" -> "m3u8_native"
            "DASH" -> "dash"
            else -> "https"
        }

        val media = JSONObject()
            .put("url", candidate.url)
            .put("mime_type", candidate.mimeType ?: JSONObject.NULL)
            .put("protocol", protocol)
            .put("height", JSONObject.NULL)
            .put("width", JSONObject.NULL)
            .put("format_id", "browser")
            .put("headers", headerJson)

        val root = JSONObject()
            .put("mode", "single")
            .put("title", getString(R.string.browser_stream_title))
            .put("webpage_url", originalUrl)
            .put("requested_quality", "browser")
            .put("resolver_mode", "browser")
            .put("media", media)

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_RESOLVED_MEDIA, root.toString())
        )
    }

    /** Header names are case-insensitive in HTTP. */
    private fun findHeader(headers: Map<String, String>, wantedName: String): String? =
        headers.entries.firstOrNull {
            it.key.equals(wantedName, ignoreCase = true)
        }?.value

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    override fun onResume() {
        super.onResume()

        // Start/restart the lightweight page probe when the resolver is visible.
        probeHandler.removeCallbacks(probeRunnable)
        probeHandler.post(probeRunnable)
    }

    override fun onPause() {
        // Do not keep evaluating page JavaScript while this Activity is hidden by the player.
        probeHandler.removeCallbacks(probeRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        probeHandler.removeCallbacksAndMessages(null)

        // ServiceWorkerController is process-wide, so remove our observer when leaving.
        ServiceWorkerController.getInstance().setServiceWorkerClient(null)

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }

        super.onDestroy()
    }
}
