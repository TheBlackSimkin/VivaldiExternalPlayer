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
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        urlInput =
            findViewById(R.id.url_input)

        resolveButton =
            findViewById(R.id.resolve_button)

        progress =
            findViewById(R.id.progress)

        status =
            findViewById(R.id.status)

        acceptSharedUrl(intent)

        resolveButton.setOnClickListener {
            resolveAndPlay(
                urlInput.text.toString()
            )
        }
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

        if (
            !url.startsWith("http://") &&
            !url.startsWith("https://")
        ) {
            status.text =
                "Paste or share a complete web address."

            return
        }

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
                            url,
                            "auto"
                        )
                        .toString()
                }

            }.onSuccess { json ->

                setBusy(false)

                startActivity(
                    Intent(
                        this@MainActivity,
                        PlayerActivity::class.java
                    ).putExtra(
                        PlayerActivity.EXTRA_RESOLVED_MEDIA,
                        json
                    )
                )

                status.text = ""

            }.onFailure { error ->

                setBusy(false)

                status.text =
                    error.message
                        ?: error.toString()
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

        urlInput.isEnabled =
            !busy
    }
}
