package com.example.vivaldiplayer

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Dedicated Recently Closed screen.
 *
 * The layout mirrors the open-tab grid instead of using a stock AlertDialog.
 * Thumbnails are app-private cache files keyed by tab ID; this screen only displays
 * them and never inspects or classifies their contents.
 */
class RecentlyClosedActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RecentlyClosedAdapter
    private lateinit var count: TextView
    private lateinit var emptyState: View
    private lateinit var recoverAll: Button
    private lateinit var deleteAll: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recently_closed)

        findViewById<ImageButton>(R.id.recent_back).setOnClickListener { finish() }
        recycler = findViewById(R.id.recent_grid)
        count = findViewById(R.id.recent_count)
        emptyState = findViewById(R.id.recent_empty_state)
        recoverAll = findViewById(R.id.recover_all_button)
        deleteAll = findViewById(R.id.delete_all_button)

        val spanCount =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        recycler.layoutManager = GridLayoutManager(this, spanCount)
        recycler.itemAnimator = null

        adapter = RecentlyClosedAdapter(this) { tab -> restoreOne(tab.id) }
        recycler.adapter = adapter

        recoverAll.setOnClickListener { restoreAll() }
        deleteAll.setOnClickListener { confirmDeleteAll() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val closed = VideoTabStore.recentlyClosedTabs()
        adapter.submit(closed)
        count.text = getString(R.string.recently_closed_count_short, closed.size)

        val hasItems = closed.isNotEmpty()
        recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
        emptyState.visibility = if (hasItems) View.GONE else View.VISIBLE
        recoverAll.isEnabled = hasItems
        deleteAll.isEnabled = hasItems
        recoverAll.alpha = if (hasItems) 1f else 0.5f
        deleteAll.alpha = if (hasItems) 1f else 0.5f
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

        /* Oldest-first keeps the visible recent ordering intuitive in the open list. */
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
}
