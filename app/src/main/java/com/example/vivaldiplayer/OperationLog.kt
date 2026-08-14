package com.example.vivaldiplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent development log for preparation/playback lifecycle events.
 *
 * WHY THIS EXISTS
 * ---------------
 * Real-device BG testing showed that the dashboard's single `tech ...` marker
 * changes too quickly to reconstruct what happened while Vivaldi was in front.
 * This journal keeps the sequence instead of only the newest marker.
 *
 * PRIVACY / SAFETY BOUNDARIES
 * ---------------------------
 * - No thumbnails, media frames or page/body text are written here.
 * - No request headers, cookies, Authorization values or credentials are logged.
 * - Callers should record lifecycle/state/candidate metadata only.
 * - The file is local app data until the user explicitly presses Share log.
 */
object OperationLog {
    private const val FILE_NAME = "operations.log"
    private const val MAX_FILE_CHARS = 300_000L
    private const val KEEP_FILE_CHARS = 220_000

    @Synchronized
    fun record(
        context: Context,
        event: String,
        detail: String = "",
        tabId: String? = null
    ) {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS Z",
                Locale.US
            ).format(Date())

            val shortTab = tabId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.take(8)
                ?: "-"

            val safeEvent = sanitize(event, 100)
            val safeDetail = sanitize(detail, 900)

            file.appendText(
                buildString {
                    append(timestamp)
                    append(" | tab=")
                    append(shortTab)
                    append(" | ")
                    append(safeEvent)
                    if (safeDetail.isNotBlank()) {
                        append(" | ")
                        append(safeDetail)
                    }
                    append('\n')
                }
            )

            trimIfNeeded(file)
        }
    }

    /**
     * Open Android's normal text share sheet. WhatsApp can be selected there if
     * it is installed; the app does not depend on or target WhatsApp directly.
     */
    fun share(activity: Activity) {
        val file = File(activity.filesDir, FILE_NAME)
        val body = runCatching { file.readText() }.getOrDefault("")

        val export = buildString {
            appendLine("Vivaldi External Player — Operations Log")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Git: ${BuildConfig.GIT_COMMIT}")
            appendLine("GitHub Actions build: ${BuildConfig.BUILD_RUN}")
            appendLine()
            appendLine("This log contains technical lifecycle/state diagnostics only.")
            appendLine("It intentionally does not contain thumbnails, media frames, cookies, request headers or credentials.")
            appendLine()
            if (body.isBlank()) {
                appendLine("No operation events have been recorded yet.")
            } else {
                append(body)
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.operations_log_subject))
            .putExtra(Intent.EXTRA_TEXT, export)

        activity.startActivity(
            Intent.createChooser(
                shareIntent,
                activity.getString(R.string.operations_log_share_chooser)
            )
        )
    }

    /** Keep accidental error text from leaking common credential-shaped values. */
    private fun sanitize(value: String, maxLength: Int): String {
        var text = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        val redactions = listOf(
            Regex("(?i)(authorization\\s*[:=]\\s*)([^ ]+)") to "\$1<redacted>",
            Regex("(?i)(cookie\\s*[:=]\\s*)([^ ]+)") to "\$1<redacted>",
            Regex("(?i)(bearer\\s+)([A-Za-z0-9._~+\\-/=]+)") to "\$1<redacted>",
            Regex("(?i)(password\\s*[:=]\\s*)([^ ]+)") to "\$1<redacted>"
        )

        redactions.forEach { (pattern, replacement) ->
            text = text.replace(pattern, replacement)
        }

        return text.take(maxLength)
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_CHARS) return

        val text = file.readText()
        val kept = text
            .takeLast(KEEP_FILE_CHARS)
            .substringAfter('\n', missingDelimiterValue = text.takeLast(KEEP_FILE_CHARS))

        file.writeText(
            "--- older operation-log entries trimmed ---\n$kept"
        )
    }
}
