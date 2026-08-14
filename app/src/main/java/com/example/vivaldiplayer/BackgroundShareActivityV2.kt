package com.example.vivaldiplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
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
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Focused successor to build #192's BG share host.
 *
 * Real-device PH QA proved two important things:
 * 1. the self-owned share Activity DOES start preparation without a tab click;
 * 2. the automatic path still waited roughly four minutes and then asked for
 *    "Browser Step", while the visible Browser Step found the stream in seconds.
 *
 * This implementation keeps the good self-owned lifecycle but makes ordinary
 * browser fallback much closer to the proven visible BrowserResolverActivity:
 * - direct/yt-dlp still starts first;
 * - after a small BG budget, browser discovery starts automatically even if the
 *   blocking extractor has not returned yet;
 * - the WebView is laid out at normal Activity size (the Activity itself is
 *   behind Vivaldi), instead of being INVISIBLE and 1x1;
 * - WebView requests, Service Worker requests, DOM sources, Performance entries
 *   and normal player configuration are all observed;
 * - browser success stores READY metadata only. It NEVER launches PlayerActivity
 *   or creates ExoPlayer, so there is no background playback;
 * - NEEDS_ATTENTION is reserved for a detected protected/human-interaction case.
 *
 * ServiceWorkerController is process-wide. To avoid attributing one tab's
 * Service Worker request to another tab, hidden browser discovery is serialized.
 * Direct attempts remain independent; only the WebView discovery slot is shared.
 */
class BackgroundShareActivityV2 : Activity() {

    companion object {
        private const val DIRECT_BROWSER_FALLBACK_BUDGET_MS = 12_000L
        private const val BROWSER_SLOT_RETRY_MS = 500L
        private const val AUTO_DEBOUNCE_MS = 2_500L
        private const val PROBE_INTERVAL_MS = 1_250L
        private const val BROWSER_TIMEOUT_MS = 30_000L
        private const val MAX_CANDIDATES = 80
        private const val MAX_CONSENT_PASSES = 18

        private val browserSlotLock = Any()
        private var browserSlotOwner: WeakReference<BackgroundShareActivityV2>? = null

        /**
         * One process-wide router, rebound whenever a hidden browser owner gets
         * the serialized slot. The current owner alone receives SW requests.
         */
        private fun bindServiceWorkerRouter(owner: BackgroundShareActivityV2) {
            synchronized(browserSlotLock) {
                browserSlotOwner = WeakReference(owner)
                ServiceWorkerController.getInstance().setServiceWorkerClient(
                    object : ServiceWorkerClient() {
                        override fun shouldInterceptRequest(
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val current = synchronized(browserSlotLock) {
                                browserSlotOwner?.get()
                            }
                            current?.captureServiceWorkerRequest(request)
                            return null
                        }
                    }
                )
            }
        }

        private fun tryAcquireBrowserSlot(owner: BackgroundShareActivityV2): Boolean {
            synchronized(browserSlotLock) {
                val current = browserSlotOwner?.get()
                if (current != null && current !== owner && !current.isFinishing && !current.isDestroyed) {
                    return false
                }
                bindServiceWorkerRouter(owner)
                return true
            }
        }

        private fun releaseBrowserSlot(owner: BackgroundShareActivityV2) {
            synchronized(browserSlotLock) {
                if (browserSlotOwner?.get() === owner) {
                    browserSlotOwner = null
                }
            }
        }
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

    private var currentPageUrl = ""
    private var userAgent = ""
    private var nextCandidateOrder = 1L
    private var pageFinished = false
    private var browserRequested = false
    private var browserStarted = false
    private var ownsBrowserSlot = false
    private var completionStarted = false
    private var consentPasses = 0

    private val directBudgetRunnable = Runnable {
        if (!completionStarted && !browserRequested) {
            VideoTabStore.markTechnicalStage(tabId, "DIRECT_BUDGET_EXPIRED")
            requestAutomaticBrowserFallback("Direct resolver exceeded the BG time budget")
        }
    }

    private val browserSlotRunnable = object : Runnable {
        override fun run() {
            if (completionStarted || isFinishing || isDestroyed || browserStarted) return
            if (tryAcquireBrowserSlot(this@BackgroundShareActivityV2)) {
                ownsBrowserSlot = true
                beginBrowserDiscovery()
            } else {
                VideoTabStore.markTechnicalStage(tabId, "BROWSER_WAITING_FOR_SLOT")
                handler.postDelayed(this, BROWSER_SLOT_RETRY_MS)
            }
        }
    }

    private val autoRunnable = Runnable {
        completeFromBestCandidate()
    }

    private val timeoutRunnable = Runnable {
        if (completionStarted) return@Runnable

        if (candidateCount() > 0) {
            completeFromBestCandidate()
            return@Runnable
        }

        /*
         * An ordinary discovery timeout is a technical failure, NOT proof that a
         * human step is required. The dashboard may still offer explicit browser
         * recovery, but NEEDS_ATTENTION remains reserved for real interaction.
         */
        finishError("Automatic browser discovery timed out without a playable candidate")
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
        VideoTabStore.initialize(applicationContext)

        sourceUrl = extractSharedUrl(intent).orEmpty()
        if (!isHttpUrl(sourceUrl)) {
            Toast.makeText(applicationContext, R.string.status_complete_url, Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }

        val tab = VideoTabStore.createPendingTab(sourceUrl)
        tabId = tab.id
        VideoTabStore.markPreparationRequested(tabId)
        VideoTabStore.markPreparationHostCreated(tabId)
        VideoTabStore.markResolving(tabId)

        Toast.makeText(applicationContext, R.string.added_to_external_player, Toast.LENGTH_SHORT).show()

        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        /*
         * Keep the WebView technically VISIBLE and normally laid out. The whole
         * Activity task is moved behind Vivaldi before browser loading begins,
         * so the user still sees Vivaldi and never sees/uses this hidden browser.
         * A 1x1 INVISIBLE WebView can cause responsive players to skip the same
         * initialization which works in the visible Browser Step.
         */
        webView = WebView(this).apply {
            visibility = View.VISIBLE
            alpha = 0.01f
            setBackgroundColor(Color.TRANSPARENT)
        }
        setContentView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        VideoTabStore.markBrowserWebViewCreated(tabId)
        configureWebView()

        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                moveTaskToBack(true)
            }
        }

        attemptDirectFirst()
        handler.postDelayed(directBudgetRunnable, DIRECT_BROWSER_FALLBACK_BUDGET_MS)
    }

    /** Start direct yt-dlp first, but do not let it postpone browser fallback for minutes. */
    private fun attemptDirectFirst() {
        VideoTabStore.markDirectResolverStarted(tabId)

        scope.launch {
            var attempt = 0
            var lastFailure = ""

            while (!completionStarted) {
                val direct = runCatching {
                    withContext(Dispatchers.IO) {
                        Python.getInstance()
                            .getModule("resolver")
                            .callAttr("resolve", sourceUrl, "auto")
                            .toString()
                    }
                }

                if (direct.isSuccess) {
                    val json = direct.getOrThrow()
                    if (runCatching { ResolvedMedia.fromJson(json) }.isSuccess) {
                        handler.removeCallbacks(directBudgetRunnable)
                        VideoTabStore.markDirectResolverFinished(tabId)
                        completeReady(json)
                        return@launch
                    }
                    lastFailure = "Invalid direct resolver result"
                    break
                }

                val failure = direct.exceptionOrNull()
                lastFailure = (failure?.message ?: failure.toString()).take(500)

                if (isHardProtectedFailure(lastFailure)) {
                    handler.removeCallbacks(directBudgetRunnable)
                    VideoTabStore.markDirectResolverFinished(tabId)
                    finishError(lastFailure)
                    return@launch
                }

                if (
                    isTransientNetworkFailure(lastFailure) &&
                    AppSettings.networkRetryEnabled(applicationContext) &&
                    attempt < AppSettings.MAX_TRANSIENT_RETRIES &&
                    !browserRequested
                ) {
                    attempt += 1
                    delay(1_500L * attempt)
                    continue
                }

                break
            }

            if (!completionStarted) {
                handler.removeCallbacks(directBudgetRunnable)
                VideoTabStore.markDirectResolverFinished(tabId)
                requestAutomaticBrowserFallback(lastFailure)
            }
        }
    }

    /**
     * Ordinary direct misses/deadlines automatically request the hidden browser.
     * This is the programmatic equivalent of the useful part of Browser Step;
     * it is not a fake UI click and it does not launch playback.
     */
    private fun requestAutomaticBrowserFallback(directFailure: String) {
        if (browserRequested || completionStarted) return
        browserRequested = true
        VideoTabStore.markBrowserStageRequested(tabId)
        VideoTabStore.markResolving(tabId)
        VideoTabStore.markTechnicalStage(
            tabId,
            if (directFailure.isBlank()) "BROWSER_AUTO_REQUESTED" else "BROWSER_AUTO_AFTER_DIRECT_MISS"
        )
        handler.post(browserSlotRunnable)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true

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

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
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
                if (candidateCount() > 0) scheduleAutomaticCompletion()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && !completionStarted) {
                    val technical = "WebView ${error.errorCode}: ${error.description}".take(500)
                    if (isTransientNetworkFailure(technical)) finishError(technical)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 100 && pageFinished) probePageForMedia()
            }
        }
    }

    private fun beginBrowserDiscovery() {
        if (browserStarted || completionStarted) return
        browserStarted = true
        currentPageUrl = sourceUrl
        VideoTabStore.markTechnicalStage(tabId, "BROWSER_SLOT_ACQUIRED")
        VideoTabStore.markBrowserDiscoveryStarted(tabId)
        handler.post(probeRunnable)
        handler.postDelayed(timeoutRunnable, BROWSER_TIMEOUT_MS)
        webView.loadUrl(sourceUrl)
    }

    /** Service Worker callback is routed only to the serialized current owner. */
    private fun captureServiceWorkerRequest(request: WebResourceRequest) {
        if (!ownsBrowserSlot || !browserStarted || completionStarted) return
        captureNetworkRequest(request)
    }

    private fun captureNetworkRequest(request: WebResourceRequest) {
        if (!browserStarted || completionStarted) return
        if (!request.method.equals("GET", ignoreCase = true)) return
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") return
        if (shouldIgnoreCandidate(url, DiscoverySource.NETWORK, null, false)) return
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
     * Read exactly technical browser/page signals: VIDEO/SOURCE URLs, Performance
     * resource URLs, and normal player config URLs/declared qualities. No imagery
     * or page-content classification is used.
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
                        if (looksLikeMedia) add(url, '', 'performance', initiator === 'video', 0, '');
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
                            add(definition.videoUrl, '', 'config', true, definition.quality, 'config:' + key);
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

    /** Conservative exact consent handling; protected controls are never automated. */
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
                    if (/captcha|challenge|sign in|log in|login|subscribe|subscription|payment|billing|purchase|regional|region|country|drm/.test(scope)) continue;
                    if (allowCookies) {
                        const explicitCookie = cookieExact.has(text);
                        const scopedGeneric = cookieGeneric.has(text) && /cookie|cookies|galletas/.test(scope);
                        if (explicitCookie || scopedGeneric) { el.click(); return 'cookie'; }
                    }
                    if (allowAge && ageExact.has(text) && /18|age|edad|adult/.test(text + ' ' + scope)) {
                        el.click(); return 'age';
                    }
                }
                return 'none';
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (result.orEmpty().trim('"') == "blocked" && !completionStarted) {
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
                    requestHeaders = if (candidate.requestHeaders.isNotEmpty()) candidate.requestHeaders else existing.requestHeaders,
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
        if (changed && pageFinished && !completionStarted) scheduleAutomaticCompletion()
    }

    private fun scheduleAutomaticCompletion() {
        if (candidateCount() == 0 || completionStarted) return
        handler.removeCallbacks(autoRunnable)
        handler.postDelayed(autoRunnable, AUTO_DEBOUNCE_MS)
    }

    private fun completeFromBestCandidate() {
        if (completionStarted) return
        val best = candidateSnapshot().firstOrNull() ?: return
        val pageUrl = currentPageUrl.takeIf(::isHttpUrl) ?: sourceUrl
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
            finishError("Automatic browser candidate could not be converted to playable metadata")
        }
    }

    private fun completeReady(json: String) {
        if (completionStarted) return
        completionStarted = true
        handler.removeCallbacks(directBudgetRunnable)
        VideoTabStore.markReady(tabId, json)
        closeBackgroundTask()
    }

    private fun finishNeedsAttention(message: String) {
        if (completionStarted) return
        completionStarted = true
        handler.removeCallbacks(directBudgetRunnable)
        VideoTabStore.markNeedsAttention(tabId, message.take(500))
        closeBackgroundTask()
    }

    private fun finishError(message: String) {
        if (completionStarted) return
        completionStarted = true
        handler.removeCallbacks(directBudgetRunnable)
        VideoTabStore.markError(tabId, message.take(500))
        closeBackgroundTask()
    }

    private fun closeBackgroundTask() {
        handler.removeCallbacksAndMessages(null)
        if (ownsBrowserSlot) {
            releaseBrowserSlot(this)
            ownsBrowserSlot = false
        }
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

    /** Same protected ranking policy: exact 720 -> 1080 -> best below 1080. */
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
        if (!headers.containsKey("Origin") && (candidate.typeLabel == "HLS" || candidate.typeLabel == "DASH" || candidate.discoveredBy == DiscoverySource.PAGE_CONFIG)) {
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
        if (discoveredBy != DiscoverySource.PAGE_CONFIG && discoveredBy != DiscoverySource.NETWORK && isTraditionalDocumentPath(path)) return true

        if (allowUnknownDirect) {
            val mime = mimeHint.orEmpty().lowercase(Locale.US)
            if (
                path.endsWith(".ogv") || path.endsWith(".avi") ||
                (path.endsWith(".ogg") && (mime.startsWith("video/") || discoveredBy == DiscoverySource.PAGE)) ||
                mime == "video/ogg" || mime == "video/x-msvideo"
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

    /** Only hard protected failures stop before the ordinary browser fallback. */
    private fun isHardProtectedFailure(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "drm", "paywall", "subscription required", "purchase required",
            "geo-restricted", "not available in your country", "regional restriction"
        ).any(lower::contains)
    }

    private fun extractSharedUrl(sharedIntent: Intent): String? {
        if (sharedIntent.action != Intent.ACTION_SEND || sharedIntent.type != "text/plain") return null
        return Regex("https?://\\S+")
            .find(sharedIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            ?.value
            ?.trimEnd('.', ',', ')', ']', '}')
            ?.takeIf(::isHttpUrl)
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()

        if (ownsBrowserSlot) {
            releaseBrowserSlot(this)
            ownsBrowserSlot = false
        }

        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.clearHistory() }
            runCatching { webView.removeAllViews() }
            runCatching { webView.destroy() }
        }

        if (::tabId.isInitialized) {
            val tab = VideoTabStore.get(tabId)
            when {
                tab?.isReady == true && TabThumbnailCache.load(applicationContext, tabId) == null ->
                    TabThumbnailCapture.captureResolved(applicationContext, tab)

                tab?.preparationState == VideoTabStore.PreparationState.RESOLVING && !isChangingConfigurations -> {
                    VideoTabStore.markQueued(tabId, "BG preparation host was destroyed; direct recovery queued")
                    VideoTabStore.markTechnicalStage(tabId, "BG_HOST_DESTROYED_RECOVERY_QUEUED")
                    TabPreparationManager.enqueue(applicationContext, tabId, replace = true)
                }
            }
        }

        super.onDestroy()
    }
}
