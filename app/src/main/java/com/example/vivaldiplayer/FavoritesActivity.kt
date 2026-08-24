package com.example.vivaldiplayer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Shared Android system-authentication wrapper for Private Favorites. */
object PrivateFavoriteAuthenticator {
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        SystemAuthGate.authenticate(
            activity = activity,
            title = activity.getString(R.string.unlock_private_favorites),
            subtitle = activity.getString(R.string.private_favorites_auth_prompt),
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}

/** Normal Favorites: original page URLs saved app-locally. */
class FavoritesActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private lateinit var emptyView: TextView
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val allEntries = FavoriteStore.all(this)
        val visible = filterEntries(allEntries, query)
        countView.text = if (visible.size == allEntries.size) visible.size.toString() else "${visible.size}/${allEntries.size}"
        listContainer.removeAllViews()
        emptyView.visibility = if (allEntries.isEmpty()) View.VISIBLE else View.GONE
        visible.forEach { entry -> listContainer.addView(favoriteRow(entry)) }
    }

    private fun favoriteRow(entry: FavoriteEntry): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.app_surface))
        }

        row.addView(Button(this).apply {
            isAllCaps = false
            text = "${entry.title}\n${entry.pageUrl}"
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 12f
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener {
                FavoriteLauncher.prepare(this@FavoritesActivity, entry)
                Toast.makeText(this@FavoritesActivity, R.string.favorite_preparing, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(60), 1f))

        row.addView(Button(this).apply {
            isAllCaps = false
            text = "×"
            textSize = 19f
            contentDescription = getString(R.string.remove_favorite)
            setTextColor(color(R.color.app_text_secondary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener {
                FavoriteStore.remove(this@FavoritesActivity, entry.id)
                refresh()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(60)).apply { marginStart = dp(6) })
        container.addView(row)
        container.addView(diagnosticsAccordion(entry.pageUrl))
        return container
    }

    private fun diagnosticsAccordion(pageUrl: String): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), dp(4))
        }
        val details = TextView(this).apply {
            visibility = View.GONE
            textSize = 10f
            setTextColor(color(R.color.app_text_secondary))
            setTextIsSelectable(true)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val toggle = Button(this).apply {
            isAllCaps = false
            text = getString(R.string.diagnostics_history)
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_secondary))
            setOnClickListener {
                val opening = details.visibility != View.VISIBLE
                if (opening) details.text = historyForPage(pageUrl)
                details.visibility = if (opening) View.VISIBLE else View.GONE
                text = (if (opening) "⌃ " else "") + getString(R.string.diagnostics_history)
            }
        }
        wrapper.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        wrapper.addView(details)
        return wrapper
    }

    private fun historyForPage(pageUrl: String): String {
        val matching = (VideoTabStore.allTabs() + VideoTabStore.recentlyClosedTabs())
            .distinctBy { it.id }
            .filter { TabOriginStore.pageUrl(this, it) == pageUrl }
        val lines = matching.flatMap { OperationLog.recentForTab(this, it.id, 8) }.distinct().takeLast(12)
        return if (lines.isEmpty()) getString(R.string.diagnostics_history_empty) else lines.joinToString("\n")
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(18))
            setBackgroundColor(color(R.color.app_background))
        }
        root.addView(header(getString(R.string.favorites)))
        root.addView(AccordionSearchFilter(this, emptyList()) { value, _ ->
            query = value
            refresh()
        }.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        emptyView = TextView(this).apply {
            text = getString(R.string.favorites_empty)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
            setTextColor(color(R.color.app_text_secondary))
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

    private fun header(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Button(this@FavoritesActivity).apply {
            isAllCaps = false
            text = "‹"
            textSize = 24f
            contentDescription = getString(R.string.close)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        addView(TextView(this@FavoritesActivity).apply {
            text = title
            textSize = 21f
            setTextColor(color(R.color.app_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        countView = TextView(this@FavoritesActivity).apply {
            gravity = Gravity.CENTER
            setTextColor(color(R.color.app_text_secondary))
        }
        addView(countView, LinearLayout.LayoutParams(dp(52), dp(44)))
    }

    private fun filterEntries(entries: List<FavoriteEntry>, value: String): List<FavoriteEntry> {
        val needle = value.trim().lowercase()
        if (needle.isBlank()) return entries
        return entries.filter { it.title.lowercase().contains(needle) || it.pageUrl.lowercase().contains(needle) }
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
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
    private lateinit var searchHost: LinearLayout
    private var unlocked = false
    private var query = ""

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
                installUnlockedSearchIfNeeded()
                renderUnlocked()
            },
            onFailure = { finish() }
        )
    }

    private fun installUnlockedSearchIfNeeded() {
        if (searchHost.childCount > 0) return
        searchHost.addView(AccordionSearchFilter(this, emptyList()) { value, _ ->
            if (!unlocked) return@AccordionSearchFilter
            query = value
            renderUnlocked()
        }.view)
        searchHost.visibility = View.VISIBLE
    }

    private fun renderUnlocked() {
        if (!unlocked || isFinishing || isDestroyed) return
        val allEntries = PrivateFavoriteStore.allAfterAuthentication(this)
        val visible = filterEntries(allEntries, query)
        countView.text = if (visible.size == allEntries.size) visible.size.toString() else "${visible.size}/${allEntries.size}"
        listContainer.removeAllViews()
        emptyView.text = getString(R.string.private_favorites_empty)
        emptyView.visibility = if (allEntries.isEmpty()) View.VISIBLE else View.GONE
        visible.forEach { entry -> listContainer.addView(privateFavoriteRow(entry)) }
    }

    private fun privateFavoriteRow(entry: FavoriteEntry): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setBackgroundColor(color(R.color.app_surface))
        }
        row.addView(Button(this).apply {
            isAllCaps = false
            text = "${entry.title}\n${entry.pageUrl}"
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 12f
            setTextColor(color(R.color.app_text_primary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener {
                if (!unlocked) return@setOnClickListener
                FavoriteLauncher.prepare(this@PrivateFavoritesActivity, entry)
                Toast.makeText(this@PrivateFavoritesActivity, R.string.favorite_preparing, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(60), 1f))
        row.addView(Button(this).apply {
            isAllCaps = false
            text = "×"
            textSize = 19f
            contentDescription = getString(R.string.remove_private_favorite)
            setTextColor(color(R.color.app_text_secondary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setOnClickListener {
                if (!unlocked) return@setOnClickListener
                PrivateFavoriteStore.removeAfterAuthentication(this@PrivateFavoritesActivity, entry.id)
                renderUnlocked()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(60)).apply { marginStart = dp(6) })
        container.addView(row)
        container.addView(privateDiagnosticsAccordion(entry.pageUrl))
        return container
    }

    private fun privateDiagnosticsAccordion(pageUrl: String): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), dp(4))
        }
        val details = TextView(this).apply {
            visibility = View.GONE
            textSize = 10f
            setTextColor(color(R.color.app_text_secondary))
            setTextIsSelectable(true)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val toggle = Button(this).apply {
            isAllCaps = false
            text = getString(R.string.diagnostics_history)
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_secondary))
            setOnClickListener {
                if (!unlocked) return@setOnClickListener
                val opening = details.visibility != View.VISIBLE
                if (opening) details.text = historyForPage(pageUrl)
                details.visibility = if (opening) View.VISIBLE else View.GONE
                text = (if (opening) "⌃ " else "") + getString(R.string.diagnostics_history)
            }
        }
        wrapper.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        wrapper.addView(details)
        return wrapper
    }

    private fun historyForPage(pageUrl: String): String {
        if (!unlocked) return getString(R.string.diagnostics_history_empty)
        val matching = (VideoTabStore.allTabs() + VideoTabStore.recentlyClosedTabs())
            .distinctBy { it.id }
            .filter { TabOriginStore.pageUrl(this, it) == pageUrl }
        val lines = matching.flatMap { OperationLog.recentForTab(this, it.id, 8) }.distinct().takeLast(12)
        return if (lines.isEmpty()) getString(R.string.diagnostics_history_empty) else lines.joinToString("\n")
    }

    private fun buildLockedScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(18))
            setBackgroundColor(color(R.color.app_background))
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
            setTextColor(color(R.color.app_text_primary))
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        countView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "•"
            setTextColor(color(R.color.app_text_secondary))
        }
        header.addView(countView, LinearLayout.LayoutParams(dp(52), dp(44)))
        root.addView(header)

        searchHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(searchHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        emptyView = TextView(this).apply {
            text = getString(R.string.private_favorites_locked)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
            setTextColor(color(R.color.app_text_secondary))
        }
        root.addView(emptyView)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun filterEntries(entries: List<FavoriteEntry>, value: String): List<FavoriteEntry> {
        val needle = value.trim().lowercase()
        if (needle.isBlank()) return entries
        return entries.filter { it.title.lowercase().contains(needle) || it.pageUrl.lowercase().contains(needle) }
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
