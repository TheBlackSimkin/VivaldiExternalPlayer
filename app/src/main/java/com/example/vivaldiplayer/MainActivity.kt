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

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var browserResolveButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    private var lastFailedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        urlInput =
            findViewById(R.id.url_input)

        resolveButton =
            findViewById(R.id.resolve_button)

        browserResolveButton =
            findViewById(R.id.browser_resolve_button)

        progress =
            findViewById(R.id.progress)

        status =
            findViewById(R.id.status)

        resolveButton.setOnClickListener {
            resolveAndPlay(
                urlInput.text.toString()
            )
        }

        browserResolveButton.setOnClickListener {
            val url =
                lastFailedUrl
                    ?: urlInput.text
                        .toString()
                        .trim()

            if (
                url.startsWith("https://") ||
                url.startsWith("http://")
            ) {
                startActivity(
                    Intent(
                        this,
                        BrowserResolverActivity::class.java
                    ).putExtra(
                        BrowserResolverActivity.EXTRA_URL,
                        url
                    )
                )
            }
        }

        acceptSharedUrl(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        acceptSharedUrl(intent)
    }

    private fun acceptSharedUrl(intent: Intent) {

        if (
            intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            val sharedText =
                intent.getStringExtra(
                    Intent.EXTRA_TEXT
                ).orEmpty()

            val url =
                Regex("https?://\\S+")
                    .find(sharedText)
                    ?.value
                    ?.trimEnd(
                        '.',
                        ',',
                        ')'
                    )

            if (url != null) {
                urlInput.setText(url)

                resolveAndPlay(url)
            }
        }
    }

    private fun resolveAndPlay(url: String) {

        val cleanUrl =
            url.trim()

        if (
            !cleanUrl.startsWith("http://") &&
            !cleanUrl.startsWith("https://")
        ) {
            status.text =
                "Paste or share a complete web address."

            return
        }

        lastFailedUrl = null

        browserResolveButton.visibility =
            View.GONE

        setBusy(true)

        status.text =
            "Resolving video — 720p preferred…"

        lifecycleScope.launch {

            runCatching {

                withContext(
                    Dispatchers.IO
                ) {
                    Python
                        .getInstance()
                        .getModule("resolver")
                        .callAttr(
                            "resolve",
                            cleanUrl,
                            "auto"
                        )
                        .toString()
                }

            }.onSuccess { json ->

                setBusy(false)

                status.text = ""

                browserResolveButton.visibility =
                    View.GONE

                startActivity(
                    Intent(
                        this@MainActivity,
                        PlayerActivity::class.java
                    ).putExtra(
                        PlayerActivity.EXTRA_RESOLVED_MEDIA,
                        json
                    )
                )

            }.onFailure { error ->

                setBusy(false)

                lastFailedUrl =
                    cleanUrl

                browserResolveButton.visibility =
                    View.VISIBLE

                val technical =
                    error.message
                        ?: error.toString()

                status.text =
                    buildString {
                        appendLine(
                            "Direct resolver failed."
                        )

                        appendLine()

                        appendLine(technical)

                        appendLine()

                        append(
                            "Browser-assisted mode is available as a fallback. " +
                            "It does not automate challenges, DRM, subscriptions, " +
                            "authentication, or regional access controls."
                        )
                    }
            }
        }
    }

    private fun setBusy(busy: Boolean) {

        progress.visibility =
            if (busy) {
                View.VISIBLE
            } else {
                View.GONE
            }

        resolveButton.isEnabled =
            !busy

        browserResolveButton.isEnabled =
            !busy

        urlInput.isEnabled =
            !busy
    }
}
