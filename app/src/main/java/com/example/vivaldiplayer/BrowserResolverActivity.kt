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
 * Browser-assisted resolver.
 *
 * WHY THIS ACTIVITY EXISTS
 * ------------------------
 * Some websites cannot be resolved reliably by a standalone yt-dlp request,
 * but they do work inside a normal browser engine.
 *
 * Instead of attempting to defeat an access-control mechanism, this Activity
 * loads the page in a normal Android WebView and observes the ordinary media
 * resources which the page itself uses.
 *
 * IMPORTANT PROJECT BOUNDARIES
 * ----------------------------
 * - We do not import Vivaldi passwords or cookies.
 * - We do not solve anti-bot challenges automatically.
 * - We do not configure DRM license acquisition.
 * - We do not inspect or classify video imagery.
 * - We work only with technical media metadata such as URLs, manifests,
 *   declared resolutions, request headers and Media3 playback results.
 */
class BrowserResolverActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "browser_resolver_url"

        /**
         * Prevent an extremely busy page from producing an unusably large
         * candidate dialog.
         */
        private const val MAX_CANDIDATES = 20

        /**
         * Modern JavaScript video players often create their <video> element or
         * manifest request after the normal page-load callback has finished.
         *
         * A slow periodic probe catches those later changes.
         */
        private const val PAGE_PROBE_INTERVAL_MS = 1_250L
    }

    /**
     * One technically plausible media resource.
     *
     * declaredHeight:
     *     Optional quality metadata exposed directly by the webpage/player
     *     configuration. It lets us rank 720p before 1080p when possible.
     *
     * firstSeenOrder:
     *     Useful for HLS/DASH because a master manifest is commonly requested
     *     before its child audio/video renditions.
     */
    private data class StreamCandidate(
        val url: String,
        val mimeType: String?,
        val typeLabel: String,
        val requestHeaders: Map<String, String>,
        val discoveredBy: DiscoverySource,
        val declaredHeight: Int? = null,
        val firstSeenOrder: Long = 0L
    )

    /**
     * How the app learned about a candidate.
     *
     * PAGE_CONFIG is new in Batch 3. It means the already-loaded webpage exposed
     * technical player configuration containing a media URL and possibly a
     * declared resolution.
     */
    private enum class DiscoverySource {
        NETWORK,
        PAGE,
        PERFORMANCE,
        PAGE_CONFIG
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var playDetectedButton: Button
    private lateinit var originalUrl: String

    /**
     * The most recent normal page URL loaded inside this WebView.
     *
     * This is deliberately stored separately from webView.url because
     * shouldInterceptRequest can run on a background thread, where reading a
     * WebView property directly would not be appropriate.
     */
    @Volatile
    private var currentPageUrl: String = ""

    /**
     * Use the WebView's REAL User-Agent for handoff requests. Do not invent a
     * Vivaldi/Chrome identity here.
     */
    private var webViewUserAgent: String = ""

    /**
     * Media requests can arrive from several threads, so protect the candidate
     * collection with a lock.
     */
    private val candidateLock = Any()
    private val candidates = mutableListOf<StreamCandidate>()
    private var nextCandidateOrder = 1L

    /**
     * Periodic JavaScript probe executed only while this Activity is visible.
     */
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

        currentPageUrl = originalUrl

        /**
         * A Service Worker can load an HLS/DASH manifest without going through
         * the ordinary WebViewClient request callback.
         *
         * We therefore observe BOTH service-worker and WebView requests.
         *
         * Returning null is important: it means Android continues processing
         * the request normally. We are observing it, not replacing it.
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

        /**
         * The Android Back button navigates inside the temporary browser first.
         * When there is no more WebView history, it closes this Activity.
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

    /**
     * Configure the embedded browser.
     *
     * JavaScript and DOM storage are required by most modern video players.
     * File/content access and automatic popup windows are unnecessary here.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        /**
         * The webpage is not allowed to silently auto-play through our resolver.
         * The user starts its player normally.
         */
        settings.mediaPlaybackRequiresUserGesture = true

        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        /**
         * HTTPS pages should not silently load insecure HTTP media.
         */
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        webViewUserAgent = settings.userAgentString.orEmpty()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        /**
         * Some legitimate video CDNs are third-party relative to the webpage.
         * These cookies belong only to this app's WebView session.
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

                /**
                 * Keep normal web navigation inside this WebView.
                 * Custom application schemes are not launched from here.
                 */
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)

                if (isHttpUrl(url)) {
                    currentPageUrl = url
                }
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {
                super.onPageFinished(view, url)

                if (isHttpUrl(url)) {
                    currentPageUrl = url
                }

                /**
                 * JavaScript players may not create their actual media element
                 * until page initialization has finished.
                 */
                probePageForMedia()

                if (candidateCount() == 0) {
                    status.text = getString(R.string.browser_page_loaded)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(
                view: WebView,
                newProgress: Int
            ) {
                super.onProgressChanged(view, newProgress)

                progress.progress = newProgress
                progress.visibility =
                    if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    /**
     * Observe a normal GET request made by WebView.
     *
     * Individual HLS/DASH media segments are intentionally NOT candidates;
     * Media3 needs a complete file or a manifest.
     */
    private fun captureNetworkRequest(request: WebResourceRequest) {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return
        }

        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase(Locale.US)

        if (scheme != "https" && scheme != "http") {
            return
        }

        if (
            shouldIgnoreCandidate(
                url = url,
                discoveredBy = DiscoverySource.NETWORK,
                mimeHint = null,
                allowUnknownDirect = false
            )
        ) {
            return
        }

        val classification = classifyMediaUrl(
            url = url,
            mimeHint = null,
            allowUnknownDirect = false
        ) ?: return

        addCandidate(
            StreamCandidate(
                url = url,
                mimeType = classification.second,
                typeLabel = classification.first,
                requestHeaders = request.requestHeaders.toMap(),
                discoveredBy = DiscoverySource.NETWORK
            )
        )
    }

    /**
     * Inspect NORMAL browser/page state for media URLs.
     *
     * This does not click anything, submit forms, solve challenges, fetch extra
     * pages, or inspect video imagery.
     *
     * Four technical signals are read:
     *
     * 1. <video> elements.
     * 2. <source> elements.
     * 3. Browser Performance API resource entries.
     * 4. Technical player configuration already exposed as normal JavaScript
     *    objects on the loaded page.
     *
     * The fourth signal is particularly useful when a page has several quality
     * URLs available before the selected video begins downloading.
     */
    private fun probePageForMedia() {
        if (!::webView.isInitialized) {
            return
        }

        val script = """
            (function() {
                const found = [];

                function normalizeQuality(value) {
                    const parsed = parseInt(value, 10);
                    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
                }

                function add(url, mime, source, allowUnknown, quality) {
                    if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) {
                        return;
                    }

                    /*
                     * Some player configurations expose an intermediate JSON
                     * endpoint rather than the final media file. That endpoint
                     * itself is not a Media3-playable stream, so do not offer
                     * it as a candidate.
                     */
                    if (/\/video\/get_media(?:\?|$)/i.test(url)) {
                        return;
                    }

                    found.push({
                        url: url,
                        mime: (typeof mime === 'string') ? mime : '',
                        source: source,
                        allowUnknown: !!allowUnknown,
                        quality: normalizeQuality(quality)
                    });
                }

                /*
                 * Strong signal:
                 * media URL attached directly to a VIDEO element.
                 */
                document.querySelectorAll('video').forEach(function(video) {
                    add(
                        video.currentSrc || video.src,
                        video.type || '',
                        'page',
                        true,
                        0
                    );
                });

                /*
                 * Explicit SOURCE children are also strong page signals.
                 */
                document.querySelectorAll('source').forEach(function(source) {
                    add(
                        source.src,
                        source.type || '',
                        'page',
                        true,
                        0
                    );
                });

                /*
                 * Secondary signal:
                 * media-looking resources remembered by the browser's
                 * Performance API.
                 */
                try {
                    performance.getEntriesByType('resource').forEach(function(entry) {
                        const url = entry.name || '';
                        const initiator = String(entry.initiatorType || '').toLowerCase();

                        const looksLikeMedia =
                            /\.(m3u8|mpd|mp4|webm)(\?|$)/i.test(url) ||
                            /(?:manifest|playlist|format=m3u8|format=mpd)/i.test(url) ||
                            initiator === 'video';

                        if (looksLikeMedia) {
                            add(
                                url,
                                '',
                                'performance',
                                initiator === 'video',
                                0
                            );
                        }
                    });
                } catch (ignored) {
                    /*
                     * Some pages limit Performance API visibility.
                     * Network observation still remains available.
                     */
                }

                /*
                 * Batch 3:
                 * inspect technical player configuration which is already
                 * present as ordinary JavaScript data on the loaded page.
                 *
                 * We do NOT use eval, execute unknown strings, or inspect media
                 * frames. We only read object fields which contain URLs and
                 * declared quality values.
                 *
                 * Some players expose configuration objects whose names follow
                 * the "flashvars_<number>" convention. If one exists, its
                 * mediaDefinitions array may describe normal media resources.
                 */
                try {
                    Object.keys(window).forEach(function(key) {
                        if (!/^flashvars_\d+$/i.test(key)) {
                            return;
                        }

                        let config;

                        try {
                            config = window[key];
                        } catch (ignoredProperty) {
                            return;
                        }

                        if (!config || typeof config !== 'object') {
                            return;
                        }

                        const definitions = config.mediaDefinitions;

                        if (!Array.isArray(definitions)) {
                            return;
                        }

                        definitions.forEach(function(definition) {
                            if (!definition || typeof definition !== 'object') {
                                return;
                            }

                            add(
                                definition.videoUrl,
                                '',
                                'config',
                                true,
                                definition.quality
                            );
                        });
                    });
                } catch (ignoredConfig) {
                    /*
                     * Player configuration discovery is optional. A page which
                     * does not expose it still works through the other signals.
                     */
                }

                /*
                 * Keep the JavaScript -> Kotlin payload bounded.
                 */
                return found.slice(-100);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (
                result.isNullOrBlank() ||
                result == "null" ||
                isFinishing ||
                isDestroyed
            ) {
                return@evaluateJavascript
            }

            runCatching {
                JSONArray(result)
            }.onSuccess { array ->

                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue

                    val url = item.optString("url").trim()
                    val mime = item
                        .optString("mime")
                        .trim()
                        .takeIf { it.isNotBlank() }

                    val allowUnknown =
                        item.optBoolean("allowUnknown", false)

                    val source = when (item.optString("source")) {
                        "performance" -> DiscoverySource.PERFORMANCE
                        "config" -> DiscoverySource.PAGE_CONFIG
                        else -> DiscoverySource.PAGE
                    }

                    val declaredHeight = item
                        .optInt("quality", 0)
                        .takeIf { it > 0 }

                    if (!isHttpUrl(url)) {
                        continue
                    }

                    if (
                        shouldIgnoreCandidate(
                            url = url,
                            discoveredBy = source,
                            mimeHint = mime,
                            allowUnknownDirect = allowUnknown
                        )
                    ) {
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
                            discoveredBy = source,
                            declaredHeight = declaredHeight
                        )
                    )
                }
            }
        }
    }

    /**
     * Batch 3 false-positive filtering.
     *
     * The previous PH test revealed a very specific bug:
     * the webpage URL itself was returned by a VIDEO element and labelled
     * "video/mp4". Media3 then attempted to parse the HTML page as an MP4.
     *
     * Candidate validation must therefore consider the URL itself, not blindly
     * trust an element's MIME hint.
     */
    private fun shouldIgnoreCandidate(
        url: String,
        discoveredBy: DiscoverySource,
        mimeHint: String?,
        allowUnknownDirect: Boolean
    ): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true

        val scheme = uri.scheme?.lowercase(Locale.US)

        if (scheme != "http" && scheme != "https") {
            return true
        }

        /**
         * Never offer the document currently being viewed as if it were media.
         *
         * We compare path + query as well as the complete URL so localization
         * redirects such as one site host changing to another locale host do not
         * fool the check.
         */
        if (
            sameDocumentLocation(url, originalUrl) ||
            sameDocumentLocation(url, currentPageUrl)
        ) {
            return true
        }

        val path = uri.path.orEmpty().lowercase(Locale.US)

        /**
         * PAGE/PERFORMANCE observations are less authoritative than actual
         * network/config metadata. A URL ending in a traditional web-document
         * extension should not become a media candidate merely because a DOM
         * element claimed "video/mp4".
         *
         * We do not apply this blanket rule to PAGE_CONFIG because some real
         * media systems legitimately use dynamic endpoints.
         */
        if (
            discoveredBy != DiscoverySource.PAGE_CONFIG &&
            discoveredBy != DiscoverySource.NETWORK &&
            isTraditionalDocumentPath(path)
        ) {
            return true
        }

        /**
         * Media3 supports Ogg containers containing Vorbis, Opus or FLAC audio,
         * but an old Ogg/Theora-style video source is not a useful Android video
         * candidate for this project.
         *
         * Archive gave us exactly such a false path during testing.
         *
         * AVI is likewise not one of our desired Android video containers.
         */
        if (allowUnknownDirect) {
            val mime = mimeHint.orEmpty().lowercase(Locale.US)

            if (
                path.endsWith(".ogv") ||
                path.endsWith(".avi") ||
                (
                    path.endsWith(".ogg") &&
                    (
                        mime.startsWith("video/") ||
                        discoveredBy == DiscoverySource.PAGE
                    )
                ) ||
                mime == "video/ogg" ||
                mime == "video/x-msvideo"
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Compare two URLs as browser documents.
     *
     * Host differences are intentionally ignored AFTER the path/query match.
     * This handles ordinary localization redirects without hard-coding a site.
     */
    private fun sameDocumentLocation(
        first: String,
        second: String
    ): Boolean {
        if (!isHttpUrl(first) || !isHttpUrl(second)) {
            return false
        }

        val firstUri = runCatching { Uri.parse(first) }.getOrNull() ?: return false
        val secondUri = runCatching { Uri.parse(second) }.getOrNull() ?: return false

        val firstPath = firstUri.path.orEmpty()
        val secondPath = secondUri.path.orEmpty()

        if (firstPath != secondPath) {
            return false
        }

        /**
         * An empty path such as "/" is too generic to compare host-independently.
         */
        if (firstPath.isBlank() || firstPath == "/") {
            return normalizeFullUrl(first) == normalizeFullUrl(second)
        }

        return firstUri.encodedQuery.orEmpty() ==
            secondUri.encodedQuery.orEmpty()
    }

    /** Remove fragments and cosmetic trailing slash differences. */
    private fun normalizeFullUrl(value: String): String {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return value

        val normalizedPath = uri.path.orEmpty().trimEnd('/')

        return buildString {
            append(uri.scheme?.lowercase(Locale.US).orEmpty())
            append("://")
            append(uri.host?.lowercase(Locale.US).orEmpty())
            append(normalizedPath)

            if (!uri.encodedQuery.isNullOrBlank()) {
                append('?')
                append(uri.encodedQuery)
            }
        }
    }

    /** Traditional webpage/script extensions which should not masquerade as video. */
    private fun isTraditionalDocumentPath(path: String): Boolean =
        listOf(
            ".html",
            ".htm",
            ".php",
            ".asp",
            ".aspx",
            ".jsp",
            ".cfm"
        ).any { extension ->
            path.endsWith(extension)
        }

    /**
     * Recognize top-level media resources.
     *
     * Individual HLS/DASH segments such as .ts and .m4s are intentionally
     * excluded.
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

            /**
             * An extensionless URL currently attached to a VIDEO element or
             * explicitly provided by player configuration may still be a real
             * progressive media resource.
             *
             * Leave MIME unknown and let Media3 perform normal sniffing.
             */
            allowUnknownDirect -> {
                "DIRECT" to mimeHint
            }

            else -> null
        }
    }

    /**
     * Add or improve one candidate.
     *
     * Preserve the FIRST-SEEN order. Batch 1 moved duplicates to the front,
     * destroying valuable HLS master/child ordering.
     */
    private fun addCandidate(candidate: StreamCandidate) {
        val count = synchronized(candidateLock) {

            val existingIndex =
                candidates.indexOfFirst { it.url == candidate.url }

            if (existingIndex >= 0) {
                val existing = candidates[existingIndex]

                /**
                 * Network observations are valuable because they contain the
                 * request headers actually used by WebView.
                 *
                 * Page configuration is valuable because it may contain the
                 * declared resolution.
                 *
                 * Merge the best information from both observations.
                 */
                val merged = existing.copy(
                    mimeType = candidate.mimeType ?: existing.mimeType,
                    typeLabel = candidate.typeLabel,

                    requestHeaders =
                        if (candidate.requestHeaders.isNotEmpty()) {
                            candidate.requestHeaders
                        } else {
                            existing.requestHeaders
                        },

                    discoveredBy =
                        preferredDiscoverySource(
                            existing.discoveredBy,
                            candidate.discoveredBy
                        ),

                    declaredHeight =
                        candidate.declaredHeight ?: existing.declaredHeight
                )

                candidates[existingIndex] = merged
            } else {
                candidates.add(
                    candidate.copy(
                        firstSeenOrder = nextCandidateOrder++
                    )
                )
            }

            while (candidates.size > MAX_CANDIDATES) {
                candidates.removeAt(0)
            }

            candidates.size
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }

            playDetectedButton.visibility = View.VISIBLE
            playDetectedButton.isEnabled = true

            playDetectedButton.text =
                getString(
                    R.string.browser_play_detected_count,
                    count
                )

            status.text =
                getString(
                    R.string.browser_detected_status,
                    count
                )
        }
    }

    /**
     * Retain the strongest explanation of where a duplicated URL came from.
     */
    private fun preferredDiscoverySource(
        first: DiscoverySource,
        second: DiscoverySource
    ): DiscoverySource {
        fun score(source: DiscoverySource): Int =
            when (source) {
                DiscoverySource.PAGE_CONFIG -> 4
                DiscoverySource.PAGE -> 3
                DiscoverySource.NETWORK -> 2
                DiscoverySource.PERFORMANCE -> 1
            }

        return if (score(second) > score(first)) second else first
    }

    private fun candidateCount(): Int =
        synchronized(candidateLock) {
            candidates.size
        }

    /**
     * Return candidates ranked by usefulness.
     *
     * Nothing is silently deleted here. The manual chooser remains our safety
     * valve whenever the app's technical guess is wrong.
     */
    private fun candidateSnapshot(): List<StreamCandidate> =
        synchronized(candidateLock) {
            candidates
                .toList()
                .sortedWith(
                    compareByDescending<StreamCandidate> {
                        candidateScore(it)
                    }.thenBy {
                        it.firstSeenOrder
                    }
                )
        }

    /**
     * Generic technical candidate ranking.
     *
     * No video content is inspected.
     */
    private fun candidateScore(candidate: StreamCandidate): Int {
        var score = when (candidate.typeLabel) {
            "HLS" -> 80
            "DASH" -> 75
            "MP4" -> 50
            "WebM" -> 45
            else -> 35
        }

        /**
         * Technical player configuration is a particularly strong signal
         * because the webpage itself declared the resource as part of its
         * player setup.
         */
        score += when (candidate.discoveredBy) {
            DiscoverySource.PAGE_CONFIG -> 55
            DiscoverySource.PAGE -> 30
            DiscoverySource.PERFORMANCE -> 10
            DiscoverySource.NETWORK -> 0
        }

        /**
         * Apply the project's quality preference to known page-config qualities:
         *
         * 720p > 1080p > highest quality below 1080p.
         */
        candidate.declaredHeight?.let { height ->
            score += qualityPreferenceScore(height)
        }

        val candidateUri =
            runCatching { Uri.parse(candidate.url) }.getOrNull()

        val candidateHost =
            candidateUri
                ?.host
                .orEmpty()
                .lowercase(Locale.US)

        val pageHost =
            runCatching {
                Uri.parse(currentPageUrl.ifBlank { originalUrl }).host
            }
                .getOrNull()
                .orEmpty()
                .lowercase(Locale.US)

        if (hostsLookRelated(candidateHost, pageHost)) {
            score += 15
        }

        val path =
            candidateUri
                ?.path
                .orEmpty()
                .lowercase(Locale.US)

        if (
            path.contains("master") ||
            path.contains("manifest") ||
            path.contains("playlist")
        ) {
            score += 20
        }

        /**
         * Advertising candidates are demoted, not removed.
         *
         * This keeps debugging transparent and avoids hard-coding the assumption
         * that every unfamiliar CDN is an advertisement.
         */
        if (looksLikeAdvertisingHost(candidateHost)) {
            score -= 100
        }

        return score
    }

    /**
     * Convert a known resolution into a ranking bonus.
     *
     * 720 receives the strongest bonus, then 1080.
     * Lower resolutions are ordered naturally by height.
     */
    private fun qualityPreferenceScore(height: Int): Int =
        when {
            height == 720 -> 50
            height == 1080 -> 45
            height < 1080 -> 20 + (height / 100).coerceAtMost(10)
            else -> 5
        }

    /**
     * Lightweight relationship check.
     *
     * This is intentionally not presented as a full public-suffix/domain parser.
     */
    private fun hostsLookRelated(
        first: String,
        second: String
    ): Boolean {
        if (first.isBlank() || second.isBlank()) {
            return false
        }

        if (
            first == second ||
            first.endsWith(".$second") ||
            second.endsWith(".$first")
        ) {
            return true
        }

        val firstParts = first.split('.')
        val secondParts = second.split('.')

        if (firstParts.size < 2 || secondParts.size < 2) {
            return false
        }

        return firstParts.takeLast(2) ==
            secondParts.takeLast(2)
    }

    /**
     * Generic ad-host demotion markers.
     *
     * A candidate is never rejected only because of these strings.
     */
    private fun looksLikeAdvertisingHost(host: String): Boolean {
        val markers = listOf(
            "doubleclick",
            "googlesyndication",
            "adservice",
            "adsystem",
            "adnxs",
            "adtng",
            "advertising"
        )

        return markers.any { marker ->
            host.contains(marker)
        }
    }

    /**
     * Display candidates in ranked order.
     *
     * The first entry is only "Recommended", never silently selected.
     */
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

            val host =
                runCatching {
                    Uri.parse(candidate.url).host
                }
                    .getOrNull()
                    ?: getString(R.string.media_host_fallback)

            val source =
                when (candidate.discoveredBy) {
                    DiscoverySource.NETWORK ->
                        getString(R.string.browser_source_network)

                    DiscoverySource.PAGE ->
                        getString(R.string.browser_source_page)

                    DiscoverySource.PERFORMANCE ->
                        getString(R.string.browser_source_performance)

                    DiscoverySource.PAGE_CONFIG ->
                        getString(R.string.browser_source_config)
                }

            val type =
                if (candidate.typeLabel == "DIRECT") {
                    getString(R.string.browser_type_direct)
                } else {
                    candidate.typeLabel
                }

            val quality =
                candidate.declaredHeight
                    ?.let { height -> " • ${height}p" }
                    .orEmpty()

            val prefix =
                if (index == 0) {
                    getString(R.string.browser_recommended_prefix)
                } else {
                    "${index + 1}."
                }

            "$prefix $type$quality • $host • $source"
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
     * Convert one candidate into the same JSON contract used by resolver.py.
     */
    private fun launchCandidate(candidate: StreamCandidate) {
        val headers = linkedMapOf<String, String>()

        /**
         * Prefer values captured from the real browser request.
         */
        findHeader(candidate.requestHeaders, "User-Agent")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["User-Agent"] = it
            }

        if (
            !headers.containsKey("User-Agent") &&
            webViewUserAgent.isNotBlank()
        ) {
            headers["User-Agent"] = webViewUserAgent
        }

        findHeader(candidate.requestHeaders, "Referer")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Referer"] = it
            }

        /**
         * Use the current page URL rather than blindly using the URL originally
         * shared from Vivaldi. The user may have navigated inside the WebView.
         */
        val pageUrl =
            currentPageUrl.takeIf { isHttpUrl(it) }
                ?: originalUrl

        if (!headers.containsKey("Referer")) {
            headers["Referer"] = pageUrl
        }

        findHeader(candidate.requestHeaders, "Origin")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Origin"] = it
            }

        /**
         * Adaptive manifests and technical page-config candidates sometimes
         * expect the same normal Origin context the browser page used.
         *
         * We derive it from the current webpage itself.
         */
        if (
            !headers.containsKey("Origin") &&
            (
                candidate.typeLabel == "HLS" ||
                candidate.typeLabel == "DASH" ||
                candidate.discoveredBy == DiscoverySource.PAGE_CONFIG
            )
        ) {
            pageOrigin(pageUrl)?.let { origin ->
                headers["Origin"] = origin
            }
        }

        findHeader(candidate.requestHeaders, "Accept")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Accept"] = it
            }

        findHeader(candidate.requestHeaders, "Accept-Language")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Accept-Language"] = it
            }

        /**
         * Only cookies belonging to this app's own WebView session are reused.
         */
        CookieManager
            .getInstance()
            .getCookie(candidate.url)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                headers["Cookie"] = it
            }

        val headerJson = JSONObject()

        headers.forEach { (key, value) ->
            headerJson.put(key, value)
        }

        val protocol =
            when (candidate.typeLabel) {
                "HLS" -> "m3u8_native"
                "DASH" -> "dash"
                else -> "https"
            }

        val extension: Any =
            when (candidate.typeLabel) {
                "MP4" -> "mp4"
                "WebM" -> "webm"
                "HLS" -> "m3u8"
                "DASH" -> "mpd"
                else -> JSONObject.NULL
            }

        val media = JSONObject()
            .put("url", candidate.url)
            .put(
                "mime_type",
                candidate.mimeType ?: JSONObject.NULL
            )
            .put("protocol", protocol)
            .put("ext", extension)
            .put("container", JSONObject.NULL)
            .put(
                "height",
                candidate.declaredHeight ?: JSONObject.NULL
            )
            .put("width", JSONObject.NULL)
            .put(
                "format_id",
                "browser-${candidate.typeLabel.lowercase(Locale.US)}"
            )
            .put("vcodec", JSONObject.NULL)
            .put("acodec", JSONObject.NULL)
            .put("headers", headerJson)

        val root = JSONObject()
            .put("mode", "single")
            .put(
                "title",
                getString(R.string.browser_stream_title)
            )
            .put("webpage_url", pageUrl)
            .put("requested_quality", "browser")
            .put("resolver_mode", "browser")
            .put("media", media)

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(
                    PlayerActivity.EXTRA_RESOLVED_MEDIA,
                    root.toString()
                )
        )
    }

    /** Return `scheme://host[:port]` for a normal webpage URL. */
    private fun pageOrigin(value: String): String? {
        val uri =
            runCatching { Uri.parse(value) }.getOrNull()
                ?: return null

        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port

        return if (port > 0) {
            "$scheme://$host:$port"
        } else {
            "$scheme://$host"
        }
    }

    /** HTTP header names are case-insensitive. */
    private fun findHeader(
        headers: Map<String, String>,
        wantedName: String
    ): String? =
        headers.entries.firstOrNull {
            it.key.equals(
                wantedName,
                ignoreCase = true
            )
        }?.value

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith(
            "https://",
            ignoreCase = true
        ) ||
            value.startsWith(
                "http://",
                ignoreCase = true
            )

    override fun onResume() {
        super.onResume()

        probeHandler.removeCallbacks(probeRunnable)
        probeHandler.post(probeRunnable)
    }

    override fun onPause() {
        /**
         * Do not continue evaluating page JavaScript while PlayerActivity is on
         * top of this Activity.
         */
        probeHandler.removeCallbacks(probeRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        probeHandler.removeCallbacksAndMessages(null)

        /**
         * ServiceWorkerController is process-wide, so remove our observer when
         * this resolver Activity disappears.
         */
        ServiceWorkerController
            .getInstance()
            .setServiceWorkerClient(null)

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
