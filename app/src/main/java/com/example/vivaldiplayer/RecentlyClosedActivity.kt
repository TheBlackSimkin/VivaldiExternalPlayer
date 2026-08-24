package com.example.vivaldiplayer

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Dedicated Recently Closed screen with compact search and per-tab diagnostics. */
class RecentlyClosedActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RecentlyClosedAdapter
    private lateinit var count: TextView
    private lateinit var emptyState: View
    private lateinit var recoverAll: Button
    private lateinit var deleteAll: Button
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recently_closed)

        findViewById<ImageButton>(R.id.recent_back).setOnClickListener { finish() }
        recycler = findViewById(R.id.recent_grid)
        count = findViewById(R.id.recent_count)
        emptyState = findViewById(R.id.recent_empty_state)
        recoverAll = findViewById(R.id.recover_all_button)
        deleteAll = findViewById(R.id.delete_all_button)

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        recycler.layoutManager = GridLayoutManager(this, spanCount)
        recycler.itemAnimator = null
        adapter = RecentlyClosedAdapter(this) { tab -> restoreOne(tab.id) }
        recycler.adapter = adapter

        installSearchAccordion()
        recoverAll.setOnClickListener { restoreAll() }
        deleteAll.setOnClickListener { confirmDeleteAll() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun installSearchAccordion() {
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? LinearLayout ?: return
        val recyclerIndex = root.indexOfChild(recycler)
        val search = AccordionSearchFilter(this, emptyList()) { value, _ ->
            query = value
            refresh()
        }.view
        root.addView(search, recyclerIndex, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(5)
            topMargin = dp(10)
            marginEnd = dp(5)
        })
    }

    private fun refresh() {
        val allClosed = VideoTabStore.recentlyClosedTabs()
        val needle = query.trim().lowercase()
        val visible = if (needle.isBlank()) allClosed else allClosed.filter { tab ->
            val origin = TabOriginStore.pageUrl(this, tab)
            tab.title.lowercase().contains(needle) ||
                tab.sourceUrl.lowercase().contains(needle) ||
                origin.lowercase().contains(needle)
        }
        adapter.submit(visible)
        count.text = if (visible.size == allClosed.size) {
            getString(R.string.recently_closed_count_short, allClosed.size)
        } else {
            "${visible.size}/${allClosed.size}"
        }

        val hasAny = allClosed.isNotEmpty()
        recycler.visibility = if (visible.isNotEmpty()) View.VISIBLE else View.GONE
        emptyState.visibility = if (!hasAny) View.VISIBLE else View.GONE
        recoverAll.isEnabled = hasAny
        deleteAll.isEnabled = hasAny
        recoverAll.alpha = if (hasAny) 1f else 0.5f
        deleteAll.alpha = if (hasAny) 1f else 0.5f
    }

    private fun restoreOne(id: String) {
        if (VideoTabStore.restoreClosed(id) != null) {
            Toast.makeText(this, R.string.tab_restored, Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    private fun restoreAll() {
        val closed = VideoTabStore.recentlyClosedTabs()
        if (closed.isEmpty()) return
        closed.asReversed().forEach { VideoTabStore.restoreClosed(it.id) }
        Toast.makeText(this, R.string.recently_closed_all_recovered, Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun confirmDeleteAll() {
        val closed = VideoTabStore.recentlyClosedTabs()
        if (closed.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_all_recently_closed)
            .setMessage(R.string.delete_all_recently_closed_confirmation)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                val ids = closed.map { it.id }
                VideoTabStore.clearRecentlyClosed()
                ids.forEach { TabThumbnailCache.delete(this, it) }
                Toast.makeText(this, R.string.recently_closed_cleared, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
