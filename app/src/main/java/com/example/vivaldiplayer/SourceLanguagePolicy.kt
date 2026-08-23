package com.example.vivaldiplayer

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Prefer a legitimate site-language URL variant before resolving metadata.
 *
 * This does not translate or rewrite title text. It only changes the host for a
 * small allow-list of source families whose language-specific hosts are known to
 * represent the same ordinary page. Path, query and fragment remain unchanged.
 */
object SourceLanguagePolicy {

    fun preferAppLanguage(context: Context, value: String): String {
        val input = value.trim()
        val uri = runCatching { Uri.parse(input) }.getOrNull() ?: return input
        val host = uri.host?.lowercase(Locale.US) ?: return input
        val language = selectedLanguage(context)

        val preferredHost = when {
            isPhHost(host) && language == "es" -> "es.pornhub.com"
            isPhHost(host) && language == "en" -> "www.pornhub.com"
            else -> return input
        }

        if (host == preferredHost) return input
        return runCatching {
            uri.buildUpon().authority(preferredHost).build().toString()
        }.getOrDefault(input)
    }

    private fun selectedLanguage(context: Context): String {
        val explicit = AppCompatDelegate.getApplicationLocales().get(0)?.language
        if (!explicit.isNullOrBlank()) return explicit.lowercase(Locale.US)

        val configuration = context.resources.configuration
        val system = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.locales.get(0)?.language
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.language
        }
        return system.orEmpty().lowercase(Locale.US)
    }

    private fun isPhHost(host: String): Boolean =
        host == "pornhub.com" ||
            host == "www.pornhub.com" ||
            host == "en.pornhub.com" ||
            host == "es.pornhub.com"
}
