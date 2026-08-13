package com.example.vivaldiplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Invisible/background preparation Activity used only by the explicit
 * "Add in background" share action.
 *
 * Why an Activity instead of putting WebView in WorkManager?
 * ---------------------------------------------------------
 * Android WebView is a UI View and should be constructed with an Activity
 * context on the thread which owns it (normally the main thread). This Activity
 * gives WebView the environment it expects, but immediately moves its tiny,
 * excluded-from-recents task behind the browser. The user can therefore keep
 * using Vivaldi while technical media discovery continues for a short period.
 *
 * Important boundaries:
 * - yt-dlp remains the first resolver attempt.
 * - No ExoPlayer is created here, so this Activity can NEVER start playback.
 * - No Vivaldi cookies/passwords are imported.
 * - Only this app's own WebView cookie jar may be used.
 * - Clear age/cookie prompts may be handled conservatively when enabled.
 * - CAPTCHA/challenge/login/payment/DRM/regional controls are never bypassed.
 * - Media imagery is never inspected.
 * - If automatic browser discovery genuinely needs interaction, the persistent
 *   tab becomes NEEDS_ATTENTION and waits for the normal foreground resolver.
 */
class BackgroundPreparationActivity : Activity() {

    companion object {
        const val EXTRA_URL = "background_prepare_url"
        const val EXTRA_TAB_ID = "background_prepare_tab_id"

        private const val AUTO_DEBOUNCE_MS = 2_500L
        private const val PROBE_INTERVAL_MS = 1_250L
        private const val BROWSER_TIMEOUT_MS = 22_000L
        private const val MAX_CANDIDATES = 80
        private const val MAX_CONSENT_PASSES = 12
    }

    private enum class DiscoverySource {
        NETWORK,
        PAGE,
        PERFORMANCE,
        PAGE_CONFIG
    }

    private data class StreamCandidate(
        val url: String,
        val mimeType: String?,
        val typeLabel: String,
        val requestHeaders: Map<String, String>,
        val discoveredBy: DiscoverySource,
        val declaredHeight: Int? = null,
        val familyId: String? = null,
        val firstSeenOrder: Long = 0L
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val candidateLock = Any()
    private val candidates = mutableListOf<StreamCandidate>()

    private lateinit var sourceUrl: String
    private lateinit var tabId: String
    private lateinit var webView: WebView

    private var currentPageUrl: String = ""
    private var userAgent: String = ""
    private var nextCandidateOrder = 1L
    private var pageFinished = false
    private var browserStarted = false
    private var completionStarted = false
    private var consentPasses = 0

    private val autoRunnable = Runnable {
        completeFromBestCandidate()
    }

    private val timeoutRunnable = Runnable {
        if (!completionStarted) {
            finishNeedsAttention("Background browser preparation needs foreground interaction")
        }
    }

    private val probeRunnable = object : Runnable {
        override fun run() {
            if (completionStarted || isFinishing || isDestroyed || !browserStarted) return

            probePageForMedia()
            runConservativeConsentPass()
            handler.postDelayed(this, PROBE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sourceUrl = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        tabId = intent.getStringExtra(EXTRA_TAB_ID).orEmpty().trim()

        if (!isHttpUrl(sourceUrl) || tabId.isBlank() || VideoTabStore.get(tabId) == null) {
            finishAndRemoveTask()
            return
        }

        VideoTabStore.markResolving(tabId)

        /*
         * Keep the window completely transparent. Android still gives WebView a
         * real Activity context, but the browser underneath remains visually
         * unchanged during the very brief task hand-off.
         */
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        webView = WebView(this).apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        setContentView(
            webView,
            FrameLayout.LayoutParams(1, 1)
        )

        configureWebView()

        /*
         * Move this special task behind Vivaldi immediately. We intentionally do
         * NOT finish yet because the Activity context keeps WebView valid while
         * preparation runs. The task is excluded from Recents in the manifest.
         */
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                moveTaskToBack(true)
            }
        }

        attemptDirectFirst()
    }

    /**
     * Preserve the project's resolver order: yt-dlp first, browser second.
     * Temporary network failures get the same bounded retry count as WorkManager.
     */
    private fun attemptDirectFirst() {
        scope.launch {
            var attempt = 0
            var lastFailure = ""

            while (!completionStarted) {
                val direct = runCatching {
                    withContext(Dispatchers.IO) {
                        Python
                            .getInstance()
                            .getModule("resolver")
                            .callAttr("resolve", sourceUrl, "auto")
                            .toString()
                    }
                }

                if (direct.isSuccess) {
                    val json = direct.getOrThrow()
                    val valid = runCatching { ResolvedMedia.fromJson(json) }.isSuccess

                    if (valid) {
                        completeReady(json)
                        return@launch
                    }

                    lastFailure = "Invalid direct resolver result"
                    break
                }

                val failure = direct.exceptionOrNull()
                lastFailure = (failure?.message ?: failure.toString()).take(500)

                if (isRestrictedOrChallengeFailure(lastFailure)) {
                    finishError(lastFailure)
                    return@launch
                }

                if (
                    isTransientNetworkFailure(lastFailure) &&
                    AppSettings.networkRetryEnabled(applicationContext) &&
                    attempt < AppSettings.MAX_TRANSIENT_RETRIES
                ) {
                    attempt += 1
                    delay(1_500L * attempt)
                    continue
                }

                break
            }

            if (!completionStarted) {
                /*
                 * A normal yt-dlp miss is exactly why browser-assisted discovery
                 * exists. Do not label it as an error: continue automatically.
                 */
                startBrowserPreparation(lastFailure)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        userAgent = settings.userAgentString.orEmpty()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

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
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isHttpUrl(url)) currentPageUrl = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (isHttpUrl(url)) currentPageUrl = url

                pageFinished = true
                probePageForMedia()
                runConservativeConsentPass()

                if (candidateCount() > 0) {
                    scheduleAutomaticCompletion()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)

                if (request.isForMainFrame && !completionStarted) {
                    val technical = "WebView ${error.errorCode}: ${error.description}".take(500)
                    if (isTransientNetworkFailure(technical)) {
                        finishError(technical)
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 100 && pageFinished) {
                    probePageForMedia()
                }
            }
        }
    }

    private fun startBrowserPreparation(directFailure: String) {
        if (browserStarted || completionStarted) return

        browserStarted = true
        currentPageUrl = sourceUrl

        /*
         * The direct failure is retained only as local technical state. The user
         * sees a neutral preparation state while the normal browser fallback runs.
         */
        VideoTabStore.markResolving(tabId)

        handler.post(probeRunnable)
        handler.postDelayed(timeoutRunnable, BROWSER_TIMEOUT_MS)
        webView.loadUrl(sourceUrl)
    }

    private fun captureNetworkRequest(request: WebResourceRequest) {
        if (!request.method.equals("GET", ignoreCase = true)) return

        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") return

        if (
            shouldIgnoreCandidate(
                url = url,
                discoveredBy = DiscoverySource.NETWORK,
                mimeHint = null,
                allowUnknownDirect = false
            )
        ) return

        val classification = classifyMediaUrl(url, null, false) ?: return

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
     * Read technical media URLs already exposed by the loaded page/browser.
     * This mirrors the proven foreground resolver signals: VIDEO/SOURCE,
     * Performance API entries, and normal player configuration objects.
     */
    private fun probePageForMedia() {
        if (!browserStarted || completionStarted) return

        val script = """
            (function() {
                const found = [];

                function q(value) {
                    const parsed = parseInt(value, 10);
                    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
                }

                function add(url, mime, source, allowUnknown, quality, family) {
                    if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) return;
                    if (/\/video\/get_media(?:\?|$)/i.test(url)) return;
                    found.push({
                        url: url,
                        mime: (typeof mime === 'string') ? mime : '',
                        source: source,
                        allowUnknown: !!allowUnknown,
                        quality: q(quality),
                        family: (typeof family === 'string') ? family : ''
                    });
                }

                document.querySelectorAll('video').forEach(function(video, index) {
                    add(video.currentSrc || video.src, video.type || '', 'page', true, 0, 'video:' + index);
                });

                document.querySelectorAll('source').forEach(function(source) {
                    add(source.src, source.type || '', 'page', true, 0, '');
                });

                try {
                    performance.getEntriesByType('resource').forEach(function(entry) {
                        const url = entry.name || '';
                        const initiator = String(entry.initiatorType || '').toLowerCase();
                        const looksLikeMedia =
                            /\.(m3u8|mpd|mp4|webm)(\?|$)/i.test(url) ||
                            /(?:manifest|playlist|format=m3u8|format=mpd)/i.test(url) ||
                            initiator === 'video';
                        if (looksLikeMedia) {
                            add(url, '', 'performance', initiator === 'video', 0, '');
                        }
                    });
                } catch (ignoredPerformance) {}

                try {
                    Object.keys(window).forEach(function(key) {
                        if (!/^flashvars_\d+$/i.test(key)) return;
                        let config;
                        try { config = window[key]; } catch (ignoredProperty) { return; }
                        if (!config || typeof config !== 'object' || !Array.isArray(config.mediaDefinitions)) return;

                        config.mediaDefinitions.forEach(function(definition) {
                            if (!definition || typeof definition !== 'object') return;
                            add(
                                definition.videoUrl,
                                '',
                                'config',
                                true,
                                definition.quality,
                                'config:' + key
                            );
                        });
                    });
                } catch (ignoredConfig) {}

                return found.slice(-100);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (completionStarted || result.isNullOrBlank() || result == "null") return@evaluateJavascript

            runCatching { JSONArray(result) }.onSuccess { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    val mime = item.optString("mime").trim().takeIf { it.isNotBlank() }
                    val allowUnknown = item.optBoolean("allowUnknown", false)
                    val source = when (item.optString("source")) {
                        "performance" -> DiscoverySource.PERFORMANCE
                        "config" -> DiscoverySource.PAGE_CONFIG
                        else -> DiscoverySource.PAGE
                    }
                    val quality = item.optInt("quality", 0).takeIf { it > 0 }
                    val family = item.optString("family").trim().takeIf { it.isNotBlank() }

                    if (!isHttpUrl(url)) continue
                    if (shouldIgnoreCandidate(url, source, mime, allowUnknown)) continue

                    val classification = classifyMediaUrl(url, mime, allowUnknown) ?: continue
                    addCandidate(
                        StreamCandidate(
                            url = url,
                            mimeType = classification.second,
                            typeLabel = classification.first,
                            requestHeaders = emptyMap(),
                            discoveredBy = source,
                            declaredHeight = quality,
                            familyId = family
                        )
                    )
                }
            }
        }
    }

    /**
     * Same conservative local consent policy as the foreground browser helper.
     * Exact clear prompts only; challenge/access-control UI is never automated.
     */
    private fun runConservativeConsentPass() {
        if (!browserStarted || completionStarted || consentPasses >= MAX_CONSENT_PASSES) return

        val allowAge = AppSettings.clearAgePrompts(this)
        val allowCookies = AppSettings.clearCookiePrompts(this)
        if (!allowAge && !allowCookies) return

        consentPasses += 1

        val script = """
            (function() {
                const allowAge = ${if (allowAge) "true" else "false"};
                const allowCookies = ${if (allowCookies) "true" else "false"};
                function norm(v) { return String(v || '').toLowerCase().replace(/\s+/g, ' ').trim(); }
                function visible(el) {
                    if (!el || el.disabled) return false;
                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden') return false;
                    const rect = el.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0;
                }

                const pageText = norm(document.body ? document.body.innerText : '').slice(0, 6000);
                if (/captcha|verify you are human|checking your browser|cloudflare challenge/.test(pageText)) {
                    return 'blocked';
                }

                const cookieExact = new Set([
                    'accept cookies','accept all cookies','allow all cookies',
                    'aceptar cookies','aceptar todas las cookies','permitir todas las cookies'
                ]);
                const cookieGeneric = new Set(['accept all','allow all','aceptar todas','permitir todas']);
                const ageExact = new Set([
                    'i am 18 or older',"i'm 18 or older",'yes, i am 18 or older',
                    'i am over 18','yes, i am over 18','i am 18+',
                    'tengo 18 años o más','tengo más de 18 años','soy mayor de 18 años',
                    'sí, soy mayor de 18 años','si, soy mayor de 18 años'
                ]);

                const controls = Array.from(document.querySelectorAll(
                    'button,[role="button"],input[type="button"],input[type="submit"],a'
                ));

                for (const el of controls) {
                    if (!visible(el)) continue;
                    const text = norm(el.innerText || el.value || el.getAttribute('aria-label') || '');
                    if (!text) continue;
                    const scopeEl = el.closest('dialog,[role="dialog"],form') || el.parentElement;
                    const scope = norm(scopeEl ? scopeEl.innerText : '');

                    if (/captcha|challenge|sign in|log in|login|subscribe|subscription|payment|billing|purchase|regional|region|country|drm/.test(scope)) {
                        continue;
                    }

                    if (allowCookies) {
                        const explicitCookie = cookieExact.has(text);
                        const scopedGeneric = cookieGeneric.has(text) && /cookie|cookies|galletas/.test(scope);
                        if (explicitCookie || scopedGeneric) {
                            el.click();
                            return 'cookie';
                        }
                    }

                    if (allowAge && ageExact.has(text) && /18|age|edad|adult/.test(text + ' ' + scope)) {
                        el.click();
                        return 'age';
                    }
                }
                return 'none';
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            val value = result.orEmpty().trim('"')
            if (value == "blocked" && !completionStarted) {
                finishNeedsAttention("Browser challenge requires foreground interaction")
            }
        }
    }

    private fun addCandidate(candidate: StreamCandidate) {
        var changed = false

        synchronized(candidateLock) {
            val existingIndex = candidates.indexOfFirst { it.url == candidate.url }

            if (existingIndex >= 0) {
                val existing = candidates[existingIndex]
                val merged = existing.copy(
                    mimeType = candidate.mimeType ?: existing.mimeType,
                    typeLabel = candidate.typeLabel,
                    requestHeaders = if (candidate.requestHeaders.isNotEmpty()) {
                        candidate.requestHeaders
                    } else {
                        existing.requestHeaders
                    },
                    discoveredBy = preferredDiscoverySource(existing.discoveredBy, candidate.discoveredBy),
                    declaredHeight = candidate.declaredHeight ?: existing.declaredHeight,
                    familyId = candidate.familyId ?: existing.familyId
                )

                if (merged != existing) {
                    candidates[existingIndex] = merged
                    changed = true
                }
            } else {
                candidates += candidate.copy(firstSeenOrder = nextCandidateOrder++)
                changed = true
            }

            while (candidates.size > MAX_CANDIDATES) {
                val weakest = candidates.indices.minByOrNull { candidateScore(candidates[it]) } ?: 0
                candidates.removeAt(weakest)
            }
        }

        if (changed && pageFinished && !completionStarted) {
            scheduleAutomaticCompletion()
        }
    }

    private fun scheduleAutomaticCompletion() {
        if (candidateCount() == 0 || completionStarted) return
        handler.removeCallbacks(autoRunnable)
        handler.postDelayed(autoRunnable, AUTO_DEBOUNCE_MS)
    }

    /** Convert the strongest technical candidate into stored ResolvedMedia JSON. */
    private fun completeFromBestCandidate() {
        if (completionStarted) return
        val best = candidateSnapshot().firstOrNull() ?: return

        val pageUrl = currentPageUrl.takeIf { isHttpUrl(it) } ?: sourceUrl
        val localTitle = webView.title?.trim().orEmpty().ifBlank { getString(R.string.browser_stream_title) }

        val root = JSONObject()
            .put("mode", "single")
            .put("title", localTitle)
            .put("webpage_url", pageUrl)
            .put("requested_quality", "browser")
            .put("resolver_mode", "browser")
            .put("media", candidateToMediaJson(best, pageUrl))

        val variants = browserVariantsFor(best)
        if (variants.size > 1) {
            val array = JSONArray()
            variants.forEach { array.put(candidateToMediaJson(it, pageUrl)) }
            root.put("browser_variants", array)
        }

        val json = root.toString()
        if (runCatching { ResolvedMedia.fromJson(json) }.isSuccess) {
            completeReady(json)
        } else {
            finishNeedsAttention("Browser candidate could not be converted to playable metadata")
        }
    }

    private fun completeReady(json: String) {
        if (completionStarted) return
        completionStarted = true
        VideoTabStore.markReady(tabId, json)
        closeBackgroundTask()
    }

    private fun finishNeedsAttention(message: String) {
        if (completionStarted) return
        completionStarted = true
        VideoTabStore.markNeedsAttention(tabId, message.take(500))
        closeBackgroundTask()
    }

    private fun finishError(message: String) {
        if (completionStarted) return
        completionStarted = true
        VideoTabStore.markError(tabId, message.take(500))
        closeBackgroundTask()
    }

    private fun closeBackgroundTask() {
        handler.removeCallbacksAndMessages(null)
        runCatching { webView.stopLoading() }
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    private fun candidateCount(): Int = synchronized(candidateLock) { candidates.size }

    private fun candidateSnapshot(): List<StreamCandidate> = synchronized(candidateLock) {
        candidates.toList().sortedWith(
            compareByDescending<StreamCandidate> { candidateScore(it) }
                .thenBy { it.firstSeenOrder }
        )
    }

    private fun preferredDiscoverySource(first: DiscoverySource, second: DiscoverySource): DiscoverySource {
        fun score(source: DiscoverySource): Int = when (source) {
            DiscoverySource.PAGE_CONFIG -> 4
            DiscoverySource.PAGE -> 3
            DiscoverySource.NETWORK -> 2
            DiscoverySource.PERFORMANCE -> 1
        }
        return if (score(second) > score(first)) second else first
    }

    /** Mirrors the protected foreground browser ranking policy. */
    private fun candidateScore(candidate: StreamCandidate): Int {
        var score = when (candidate.typeLabel) {
            "HLS" -> 90
            "DASH" -> 85
            "MP4" -> 55
            "WebM" -> 50
            else -> 35
        }

        score += when (candidate.discoveredBy) {
            DiscoverySource.PAGE_CONFIG -> 80
            DiscoverySource.PAGE -> 35
            DiscoverySource.NETWORK -> 20
            DiscoverySource.PERFORMANCE -> 10
        }

        candidate.declaredHeight?.let { score += qualityPreferenceScore(it) }

        val uri = runCatching { Uri.parse(candidate.url) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase(Locale.US)
        val pageHost = runCatching { Uri.parse(currentPageUrl.ifBlank { sourceUrl }).host }
            .getOrNull().orEmpty().lowercase(Locale.US)

        if (hostsLookRelated(host, pageHost)) score += 15

        val path = uri?.path.orEmpty().lowercase(Locale.US)
        if (path.contains("master")) score += 35
        else if (path.contains("manifest")) score += 10

        if (candidate.typeLabel == "HLS" || candidate.typeLabel == "DASH") {
            score += (42 - ((candidate.firstSeenOrder - 1L) * 4L)).coerceIn(0L, 42L).toInt()
        }

        if (looksLikeAudioOnlyPath(path)) score -= 70
        else if (looksLikeVideoOnlyPath(path)) score -= 25

        if (looksLikeAdvertisingHost(host)) score -= 150
        return score
    }

    private fun qualityPreferenceScore(height: Int): Int = when {
        height == 720 -> 50
        height == 1080 -> 45
        height < 1080 -> 20 + (height / 100).coerceAtMost(10)
        else -> 5
    }

    private fun browserVariantsFor(candidate: StreamCandidate): List<StreamCandidate> {
        val family = candidate.familyId ?: return emptyList()
        return synchronized(candidateLock) {
            candidates
                .filter {
                    it.familyId == family &&
                        it.declaredHeight != null &&
                        it.typeLabel in setOf("HLS", "DASH", "MP4", "WebM", "DIRECT")
                }
                .distinctBy { it.declaredHeight }
                .sortedWith(
                    compareBy<StreamCandidate> { qualitySortBucket(it.declaredHeight ?: 0) }
                        .thenByDescending { it.declaredHeight ?: 0 }
                )
        }
    }

    private fun qualitySortBucket(height: Int): Int = when {
        height == 720 -> 0
        height == 1080 -> 1
        height < 1080 -> 2
        else -> 3
    }

    private fun candidateToMediaJson(candidate: StreamCandidate, pageUrl: String): JSONObject {
        val headersJson = JSONObject()
        buildHeadersForCandidate(candidate, pageUrl).forEach { (key, value) -> headersJson.put(key, value) }

        val protocol = when (candidate.typeLabel) {
            "HLS" -> "m3u8_native"
            "DASH" -> "dash"
            else -> "https"
        }

        val extension: Any = when (candidate.typeLabel) {
            "MP4" -> "mp4"
            "WebM" -> "webm"
            "HLS" -> "m3u8"
            "DASH" -> "mpd"
            else -> JSONObject.NULL
        }

        return JSONObject()
            .put("url", candidate.url)
            .put("mime_type", candidate.mimeType ?: JSONObject.NULL)
            .put("protocol", protocol)
            .put("ext", extension)
            .put("container", JSONObject.NULL)
            .put("height", candidate.declaredHeight ?: JSONObject.NULL)
            .put("width", JSONObject.NULL)
            .put("format_id", "browser-${candidate.typeLabel.lowercase(Locale.US)}")
            .put("vcodec", JSONObject.NULL)
            .put("acodec", JSONObject.NULL)
            .put("headers", headersJson)
    }

    private fun buildHeadersForCandidate(candidate: StreamCandidate, pageUrl: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()

        findHeader(candidate.requestHeaders, "User-Agent")?.takeIf { it.isNotBlank() }
            ?.let { headers["User-Agent"] = it }
        if (!headers.containsKey("User-Agent") && userAgent.isNotBlank()) headers["User-Agent"] = userAgent

        findHeader(candidate.requestHeaders, "Referer")?.takeIf { it.isNotBlank() }
            ?.let { headers["Referer"] = it }
        if (!headers.containsKey("Referer")) headers["Referer"] = pageUrl

        findHeader(candidate.requestHeaders, "Origin")?.takeIf { it.isNotBlank() }
            ?.let { headers["Origin"] = it }
        if (
            !headers.containsKey("Origin") &&
            (candidate.typeLabel == "HLS" || candidate.typeLabel == "DASH" || candidate.discoveredBy == DiscoverySource.PAGE_CONFIG)
        ) {
            pageOrigin(pageUrl)?.let { headers["Origin"] = it }
        }

        findHeader(candidate.requestHeaders, "Accept")?.takeIf { it.isNotBlank() }
            ?.let { headers["Accept"] = it }
        findHeader(candidate.requestHeaders, "Accept-Language")?.takeIf { it.isNotBlank() }
            ?.let { headers["Accept-Language"] = it }

        CookieManager.getInstance().getCookie(candidate.url)?.takeIf { it.isNotBlank() }
            ?.let { headers["Cookie"] = it }

        return headers
    }

    private fun pageOrigin(value: String): String? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return if (uri.port > 0) "$scheme://$host:${uri.port}" else "$scheme://$host"
    }

    private fun findHeader(headers: Map<String, String>, wanted: String): String? =
        headers.entries.firstOrNull { it.key.equals(wanted, ignoreCase = true) }?.value

    private fun shouldIgnoreCandidate(
        url: String,
        discoveredBy: DiscoverySource,
        mimeHint: String?,
        allowUnknownDirect: Boolean
    ): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") return true

        if (sameDocumentLocation(url, sourceUrl) || sameDocumentLocation(url, currentPageUrl)) return true

        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (
            discoveredBy != DiscoverySource.PAGE_CONFIG &&
            discoveredBy != DiscoverySource.NETWORK &&
            isTraditionalDocumentPath(path)
        ) return true

        if (allowUnknownDirect) {
            val mime = mimeHint.orEmpty().lowercase(Locale.US)
            if (
                path.endsWith(".ogv") ||
                path.endsWith(".avi") ||
                (path.endsWith(".ogg") && (mime.startsWith("video/") || discoveredBy == DiscoverySource.PAGE)) ||
                mime == "video/ogg" ||
                mime == "video/x-msvideo"
            ) return true
        }

        return false
    }

    private fun classifyMediaUrl(url: String, mimeHint: String?, allowUnknownDirect: Boolean): Pair<String, String?>? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val full = url.lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        val query = uri.encodedQuery.orEmpty().lowercase(Locale.US)
        val mime = mimeHint.orEmpty().lowercase(Locale.US)

        return when {
            mime.contains("mpegurl") || path.endsWith(".m3u8") || full.contains(".m3u8?") ||
                query.contains("format=m3u8") || query.contains("type=application%2fx-mpegurl") ->
                "HLS" to "application/x-mpegURL"

            mime.contains("dash+xml") || path.endsWith(".mpd") || full.contains(".mpd?") || query.contains("format=mpd") ->
                "DASH" to "application/dash+xml"

            mime == "video/mp4" || path.endsWith(".mp4") || full.contains(".mp4?") ||
                query.contains("mime=video%2fmp4") || query.contains("mime=video/mp4") ||
                query.contains("type=video%2fmp4") || query.contains("type=video/mp4") ->
                "MP4" to "video/mp4"

            mime == "video/webm" || path.endsWith(".webm") || full.contains(".webm?") ||
                query.contains("mime=video%2fwebm") || query.contains("mime=video/webm") ->
                "WebM" to "video/webm"

            allowUnknownDirect -> "DIRECT" to mimeHint
            else -> null
        }
    }

    private fun sameDocumentLocation(first: String, second: String): Boolean {
        if (!isHttpUrl(first) || !isHttpUrl(second)) return false
        val a = runCatching { Uri.parse(first) }.getOrNull() ?: return false
        val b = runCatching { Uri.parse(second) }.getOrNull() ?: return false
        val aPath = a.path.orEmpty()
        val bPath = b.path.orEmpty()
        if (aPath != bPath) return false
        if (aPath.isBlank() || aPath == "/") return normalizeFullUrl(first) == normalizeFullUrl(second)
        return a.encodedQuery.orEmpty() == b.encodedQuery.orEmpty()
    }

    private fun normalizeFullUrl(value: String): String {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return value
        return buildString {
            append(uri.scheme?.lowercase(Locale.US).orEmpty())
            append("://")
            append(uri.host?.lowercase(Locale.US).orEmpty())
            append(uri.path.orEmpty().trimEnd('/'))
            if (!uri.encodedQuery.isNullOrBlank()) {
                append('?')
                append(uri.encodedQuery)
            }
        }
    }

    private fun isTraditionalDocumentPath(path: String): Boolean =
        listOf(".html", ".htm", ".php", ".asp", ".aspx", ".jsp", ".cfm").any(path::endsWith)

    private fun hostsLookRelated(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) return false
        if (first == second || first.endsWith(".$second") || second.endsWith(".$first")) return true
        val a = first.split('.')
        val b = second.split('.')
        return a.size >= 2 && b.size >= 2 && a.takeLast(2) == b.takeLast(2)
    }

    private fun looksLikeAudioOnlyPath(path: String): Boolean =
        Regex("(^|[/_.-])(audio|aac|opus|mp3)([/_.-]|$)").containsMatchIn(path) &&
            !Regex("(^|[/_.-])video([/_.-]|$)").containsMatchIn(path)

    private fun looksLikeVideoOnlyPath(path: String): Boolean =
        Regex("(^|[/_.-])video([/_.-]|$)").containsMatchIn(path) &&
            !Regex("(^|[/_.-])audio([/_.-]|$)").containsMatchIn(path)

    private fun looksLikeAdvertisingHost(host: String): Boolean =
        listOf("doubleclick", "googlesyndication", "adservice", "adsystem", "adnxs", "adtng", "advertising")
            .any(host::contains)

    private fun isTransientNetworkFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "timed out", "timeout", "temporary failure", "connection reset", "connection aborted",
            "network is unreachable", "name or service not known", "net::err_",
            "http error 429", "http error 500", "http error 502", "http error 503", "http error 504"
        ).any(lower::contains)
    }

    private fun isRestrictedOrChallengeFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "drm", "captcha", "verify you are human", "anti-bot", "paywall",
            "subscription required", "login required", "sign in to confirm",
            "geo-restricted", "not available in your country"
        ).any(lower::contains)
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()

        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.clearHistory() }
            runCatching { webView.removeAllViews() }
            runCatching { webView.destroy() }
        }

        super.onDestroy()
    }
}
