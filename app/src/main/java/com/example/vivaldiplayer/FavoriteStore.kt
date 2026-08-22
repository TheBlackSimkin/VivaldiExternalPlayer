package com.example.vivaldiplayer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Permanent bookmark identity. Only original HTTP(S) page URLs belong here. */
data class FavoriteEntry(
    val id: String,
    val pageUrl: String,
    val title: String,
    val createdAtMs: Long
)

/** Plain app-private Favorites. */
object FavoriteStore {
    private const val PREFS_NAME = "favorite_store"
    private const val KEY_ENTRIES = "entries"

    fun all(context: Context): List<FavoriteEntry> =
        parseEntries(
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "[]")
                .orEmpty()
        )

    fun add(context: Context, pageUrl: String, title: String): FavoriteEntry? {
        val cleanUrl = pageUrl.trim()
        if (!isHttpUrl(cleanUrl)) return null
        val current = all(context).toMutableList()
        current.firstOrNull { it.pageUrl == cleanUrl }?.let { return it }

        val entry = FavoriteEntry(
            id = UUID.randomUUID().toString(),
            pageUrl = cleanUrl,
            title = title.trim().ifBlank { "Favorite" },
            createdAtMs = System.currentTimeMillis()
        )
        current.add(0, entry)
        save(context, current)
        return entry
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    private fun save(context: Context, entries: List<FavoriteEntry>) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, entriesToJson(entries))
            .apply()
    }
}

/**
 * Private Favorites encrypted at rest with an app-only Android Keystore AES key.
 *
 * Callers must gate every read/write behind Android system authentication. The encrypted
 * preference contains no plaintext title or URL, and the private UI never uses thumbnails.
 */
object PrivateFavoriteStore {
    private const val PREFS_NAME = "private_favorite_store"
    private const val KEY_BLOB = "encrypted_entries"
    private const val KEY_ALIAS = "vep_private_favorites_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun allAfterAuthentication(context: Context): List<FavoriteEntry> =
        decrypt(context)?.let(::parseEntries).orEmpty()

    fun addAfterAuthentication(
        context: Context,
        pageUrl: String,
        title: String
    ): FavoriteEntry? {
        val cleanUrl = pageUrl.trim()
        if (!isHttpUrl(cleanUrl)) return null
        val current = allAfterAuthentication(context).toMutableList()
        current.firstOrNull { it.pageUrl == cleanUrl }?.let { return it }

        val entry = FavoriteEntry(
            id = UUID.randomUUID().toString(),
            pageUrl = cleanUrl,
            title = title.trim().ifBlank { "Private Favorite" },
            createdAtMs = System.currentTimeMillis()
        )
        current.add(0, entry)
        encrypt(context, entriesToJson(current))
        return entry
    }

    fun removeAfterAuthentication(context: Context, id: String) {
        val filtered = allAfterAuthentication(context).filterNot { it.id == id }
        encrypt(context, entriesToJson(filtered))
    }

    private fun encrypt(context: Context, plaintext: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOB, packed)
            .apply()
    }

    private fun decrypt(context: Context): String? {
        val packed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BLOB, null)
            ?: return "[]"

        return runCatching {
            val json = JSONObject(packed)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

/** Create a fresh tab from a Favorite and send it through the protected BG service path. */
object FavoriteLauncher {
    fun prepare(context: Context, favorite: FavoriteEntry): VideoTabStore.VideoTab? {
        if (!isHttpUrl(favorite.pageUrl)) return null
        VideoTabStore.initialize(context.applicationContext)
        val tab = VideoTabStore.createPendingTab(favorite.pageUrl)
        TabOriginStore.remember(context, tab.id, favorite.pageUrl)
        VideoTabStore.markPreparationRequested(tab.id)
        VideoTabStore.markTechnicalStage(tab.id, "FAVORITE_PRIVATE_SERVICE_REQUESTED")
        val token = "favorite-${tab.id}-${System.currentTimeMillis()}"
        BackgroundPreparationKeepAliveService.acquire(
            context = context.applicationContext,
            token = token,
            tabId = tab.id,
            sourceUrl = favorite.pageUrl
        )
        return tab
    }
}

private fun entriesToJson(entries: List<FavoriteEntry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("page_url", entry.pageUrl)
                .put("title", entry.title)
                .put("created_at_ms", entry.createdAtMs)
        )
    }
    return array.toString()
}

private fun parseEntries(raw: String): List<FavoriteEntry> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("page_url").trim()
            if (!isHttpUrl(url)) continue
            add(
                FavoriteEntry(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    pageUrl = url,
                    title = item.optString("title").ifBlank { "Favorite" },
                    createdAtMs = item.optLong("created_at_ms", 0L)
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
