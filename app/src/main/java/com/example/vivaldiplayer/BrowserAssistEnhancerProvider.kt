package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Early app initializer for browser-assisted presentation/consent behavior.
 *
 * BrowserResolverActivity itself remains unchanged, which deliberately protects
 * the Batch 4 candidate ranking and request-observation code. This provider only
 * registers Activity lifecycle callbacks and adds a presentation layer above the
 * existing resolver.
 */
class BrowserAssistEnhancerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(BrowserAssistLifecycle)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null
}

/** Watches only BrowserResolverActivity instances. */
private object BrowserAssistLifecycle : Application.ActivityLifecycleCallbacks {
    private val sessions = WeakHashMap<BrowserResolverActivity, BrowserAssistSession>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is BrowserResolverActivity) return
        activity.window.decorView.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                sessions.getOrPut(activity) { BrowserAssistSession(activity) }.attach()
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is BrowserResolverActivity) return
        val session = sessions[activity] ?: return
        session.onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity !is BrowserResolverActivity) return
        sessions[activity]?.onPause()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity !is BrowserResolverActivity) return
        sessions.remove(activity)?.destroy()
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/**
 * Presentation-only cover plus narrow local DOM consent helper.
 *
 * The cover prevents WebView/debug/candidate details flashing during successful
 * automatic resolution. It is removed when interaction is actually needed.
 */
private class BrowserAssistSession(
    private val activity: BrowserResolverActivity
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 700L
        private const val ATTENTION_GRACE_MS = 5_000L
        private const val MAX_CONSENT_PASSES = 10
    }

    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private var revealed = false
    private var leftForegroundOnce = false
    private var pageCompleteAt = 0L
    private var lastConsentActionAt = 0L
    private var consentPasses = 0

    private val message = TextView(activity).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, 0)
        setText(R.string.opening_video)
    }

    private val overlay = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        addView(ProgressBar(activity))
        addView(message)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (activity.isFinishing || activity.isDestroyed || revealed) return

            val webView = activity.findViewById<WebView>(R.id.browser_web_view)
            val candidateButton = activity.findViewById<Button>(R.id.play_detected_button)

            if (webView == null) {
                reveal()
                return
            }

            val hasCandidate = candidateButton?.visibility == View.VISIBLE && candidateButton.isEnabled

            if (webView.progress >= 100) {
                if (pageCompleteAt == 0L) pageCompleteAt = System.currentTimeMillis()

                if (consentPasses < MAX_CONSENT_PASSES) {
                    consentPasses += 1
                    runConservativeConsentPass(webView)
                }

                /*
                 * Once a candidate exists, keep the clean cover while the
                 * resolver's existing debounce chooses its best candidate.
                 */
                if (!hasCandidate) {
                    val reference = maxOf(pageCompleteAt, lastConsentActionAt)
                    if (System.currentTimeMillis() - reference >= ATTENTION_GRACE_MS) {
                        reveal()
                        return
                    }
                }
            }

            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    fun attach() {
        if (attached) return
        attached = true
        activity.addContentView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        handler.post(checkRunnable)
    }

    fun onResume() {
        if (!attached || revealed) return

        /*
         * Returning from PlayerActivity means the automatic candidate may have
         * been wrong. Reveal the resolver so its existing manual chooser remains
         * accessible instead of hiding it again.
         */
        if (leftForegroundOnce) {
            reveal()
        } else {
            handler.removeCallbacks(checkRunnable)
            handler.post(checkRunnable)
        }
    }

    fun onPause() {
        leftForegroundOnce = true
        handler.removeCallbacks(checkRunnable)
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun reveal() {
        if (revealed) return
        revealed = true
        overlay.visibility = View.GONE
        handler.removeCallbacks(checkRunnable)
    }

    /**
     * Click only exact, clearly identified cookie or adult-age confirmations.
     *
     * Important exclusions are embedded in the script itself: ambiguous text,
     * login/payment/subscription/region/DRM controls and challenge/CAPTCHA pages
     * are never clicked. If an anti-bot challenge is detected, the WebView is
     * revealed for the user instead.
     */
    private fun runConservativeConsentPass(webView: WebView) {
        val allowAge = AppSettings.clearAgePrompts(activity)
        val allowCookies = AppSettings.clearCookiePrompts(activity)
        if (!allowAge && !allowCookies) return

        val script = """
            (function() {
                const allowAge = ${if (allowAge) "true" else "false"};
                const allowCookies = ${if (allowCookies) "true" else "false"};

                function norm(value) {
                    return String(value || '')
                        .toLowerCase()
                        .replace(/\s+/g, ' ')
                        .trim();
                }

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
                    'accept cookies',
                    'accept all cookies',
                    'allow all cookies',
                    'aceptar cookies',
                    'aceptar todas las cookies',
                    'permitir todas las cookies'
                ]);

                const cookieGeneric = new Set([
                    'accept all',
                    'allow all',
                    'aceptar todas',
                    'permitir todas'
                ]);

                const ageExact = new Set([
                    'i am 18 or older',
                    "i'm 18 or older",
                    'yes, i am 18 or older',
                    'i am over 18',
                    'yes, i am over 18',
                    'i am 18+',
                    'tengo 18 años o más',
                    'tengo más de 18 años',
                    'soy mayor de 18 años',
                    'sí, soy mayor de 18 años',
                    'si, soy mayor de 18 años'
                ]);

                const controls = Array.from(document.querySelectorAll(
                    'button,[role="button"],input[type="button"],input[type="submit"],a'
                ));

                for (const el of controls) {
                    if (!visible(el)) continue;

                    const text = norm(
                        el.innerText || el.value || el.getAttribute('aria-label') || ''
                    );
                    if (!text) continue;

                    const scopeEl = el.closest('dialog,[role="dialog"],form') || el.parentElement;
                    const scope = norm(scopeEl ? scopeEl.innerText : '');

                    /* Never act inside protected/ambiguous access-control UI. */
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
            when (value) {
                "age", "cookie" -> {
                    lastConsentActionAt = System.currentTimeMillis()
                    pageCompleteAt = lastConsentActionAt
                }
                "blocked" -> reveal()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
