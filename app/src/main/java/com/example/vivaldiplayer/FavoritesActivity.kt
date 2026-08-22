package com.example.vivaldiplayer

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Reusable Android-system authentication gate for Private Favorites reads/writes. */
object PrivateFavoriteAuthenticator {
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        if (BiometricManager.from(activity).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Toast.makeText(activity, R.string.private_favorites_auth_unavailable, Toast.LENGTH_LONG).show()
            onFailure?.invoke()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure?.invoke()
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.unlock_private_favorites))
                .setSubtitle(activity.getString(R.string.private_favorites_auth_prompt))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}

/** Normal Favorites: original page URLs saved app-locally. */
class FavoritesActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen(getString(R.string.favorites), privateScreen = false))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val entries = FavoriteStore.all(this)
        renderEntries(entries, privateEntries = false)
    }

    private fun renderEntries(entries: List<FavoriteEntry>, privateEntries: Boolean) {
        countView.text = entries.size.toString()
        listContainer.removeAllViews()
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        entries.forEach { entry -> listContainer.addView(favoriteRow(entry, privateEntries)) }
    }

    private fun favoriteRow(entry: FavoriteEntry, privateEntries: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_surface))
        }

        val open = Button(this).apply {
            isAllCaps = false
            text = buildString {
                append(entry.title)
                append("\n")
                append(entry.pageUrl)
            }
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_text_primary))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@FavoritesActivity, R.color.app_surface_raised)
            )
            setOnClickListener {
                FavoriteLauncher.prepare(this@FavoritesActivity, entry)
                Toast.makeText(this@FavoritesActivity, R.string.favorite_preparing, Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(open, LinearLayout.LayoutParams(0, dp(60), 1f))

        val delete = Button(this).apply {
            isAllCaps = false
            text = "×"
            textSize = 19f
            contentDescription = getString(R.string.remove_favorite)
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_text_secondary))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@FavoritesActivity, R.color.app_surface_raised)
            )
            setOnClickListener {
                if (privateEntries) PrivateFavoriteStore.removeAfterAuthentication(this@FavoritesActivity, entry.id)
                else FavoriteStore.remove(this@FavoritesActivity, entry.id)
                refresh()
            }
        }
        row.addView(delete, LinearLayout.LayoutParams(dp(48), dp(60)).apply { marginStart = dp(6) })
        return row
    }

    private fun buildScreen(title: String, privateScreen: Boolean): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_background))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            isAllCaps = false
            text = "‹"
            textSize = 24f
            contentDescription = getString(R.string.close)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        header.addView(TextView(this).apply {
            text = title
            textSize = 21f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        countView = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_text_secondary))
        }
        header.addView(countView, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        emptyView = TextView(this).apply {
            text = getString(if (privateScreen) R.string.private_favorites_empty else R.string.favorites_empty)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_text_secondary))
        }
        root.addView(emptyView)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerPadding = dp(3)
        }
        root.addView(ScrollView(this).apply { addView(listContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Private Favorites never render or decrypt anything until Android system authentication passes.
 * FLAG_SECURE prevents screenshots/Recents snapshots while unlocked, and leaving the screen
 * finishes it so the collection is locked again on the next visit.
 */
class PrivateFavoritesActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private lateinit var emptyView: TextView
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(buildLockedScreen())
        authenticateAndRender()
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
        if (!isChangingConfigurations) finish()
    }

    private fun authenticateAndRender() {
        PrivateFavoriteAuthenticator.authenticate(
            this,
            onSuccess = {
                unlocked = true
                renderUnlocked()
            },
            onFailure = { finish() }
        )
    }

    private fun renderUnlocked() {
        if (!unlocked || isFinishing || isDestroyed) return
        val entries = PrivateFavoriteStore.allAfterAuthentication(this)
        countView.text = entries.size.toString()
        listContainer.removeAllViews()
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        entries.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(4), dp(6))
                setBackgroundColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_surface))
            }

            row.addView(Button(this).apply {
                isAllCaps = false
                text = "${entry.title}\n${entry.pageUrl}"
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_text_primary))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_surface_raised)
                )
                setOnClickListener {
                    FavoriteLauncher.prepare(this@PrivateFavoritesActivity, entry)
                    Toast.makeText(this@PrivateFavoritesActivity, R.string.favorite_preparing, Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, dp(60), 1f))

            row.addView(Button(this).apply {
                isAllCaps = false
                text = "×"
                textSize = 19f
                contentDescription = getString(R.string.remove_private_favorite)
                setTextColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_text_secondary))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_surface_raised)
                )
                setOnClickListener {
                    if (!unlocked) return@setOnClickListener
                    PrivateFavoriteStore.removeAfterAuthentication(this@PrivateFavoritesActivity, entry.id)
                    renderUnlocked()
                }
            }, LinearLayout.LayoutParams(dp(48), dp(60)).apply { marginStart = dp(6) })

            listContainer.addView(row)
        }
    }

    private fun buildLockedScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_background))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            isAllCaps = false
            text = "‹"
            textSize = 24f
            contentDescription = getString(R.string.close)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        header.addView(TextView(this).apply {
            text = getString(R.string.private_favorites)
            textSize = 21f
            setTextColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        countView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "•"
            setTextColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_text_secondary))
        }
        header.addView(countView, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        emptyView = TextView(this).apply {
            text = getString(R.string.private_favorites_locked)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
            setTextColor(ContextCompat.getColor(this@PrivateFavoritesActivity, R.color.app_text_secondary))
        }
        root.addView(emptyView)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
