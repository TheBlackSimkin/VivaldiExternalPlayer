package com.example.vivaldiplayer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/** Grid adapter for persistent open tabs, including Candidate 6 multi-select mode. */
class TabDashboardAdapter(
    private val context: Context,
    private val onPrimary: (VideoTabStore.VideoTab) -> Unit,
    private val onBrowser: (VideoTabStore.VideoTab) -> Unit,
    private val onClose: (VideoTabStore.VideoTab) -> Unit,
    private val onMove: (VideoTabStore.VideoTab, Int) -> Unit,
    private val onThumbnailNeeded: (VideoTabStore.VideoTab) -> Unit,
    private val onSelectionChanged: (List<VideoTabStore.VideoTab>) -> Unit = {}
) : RecyclerView.Adapter<TabDashboardAdapter.TabViewHolder>() {

    private val items = mutableListOf<VideoTabStore.VideoTab>()
    private val selectedIds = linkedSetOf<String>()
    private var gestureActive = false
    private var selectionMode = false

    init { setHasStableIds(true) }

    fun submitTabs(tabs: List<VideoTabStore.VideoTab>) {
        if (gestureActive) return
        items.clear()
        items.addAll(tabs)
        selectedIds.retainAll(tabs.map { it.id }.toSet())
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun selectedTabs(): List<VideoTabStore.VideoTab> =
        items.filter { it.id in selectedIds }

    fun isSelectionMode(): Boolean = selectionMode

    fun clearSelection() {
        if (!selectionMode && selectedIds.isEmpty()) return
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(R.color.app_surface))
            strokeColor = color(R.color.app_outline)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
                bottomMargin = dp(10)
            }
        }

        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(color(R.color.app_surface_raised))
            contentDescription = context.getString(R.string.tab_thumbnail_pending)
        }
        root.addView(preview)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(10))
        }
        val title = TextView(context).apply {
            maxLines = 2
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.app_text_primary))
        }
        body.addView(title)

        val status = TextView(context).apply {
            textSize = 11f
            setPadding(dp(8), dp(3), dp(8), dp(3))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        body.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val meta = TextView(context).apply {
            maxLines = 2
            textSize = 11f
            setTextColor(color(R.color.app_text_secondary))
        }
        body.addView(meta, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7) })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val primary = compactButton("").apply {
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_accent))
            setTextColor(color(R.color.white))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        actions.addView(primary, LinearLayout.LayoutParams(0, dp(42), 1f))

        val browser = compactButton(context.getString(R.string.dashboard_browser)).apply {
            visibility = View.GONE
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
        }

        val close = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.dashboard_close)
            background = ContextCompat.getDrawable(context, R.drawable.icon_button_background)
            imageTintList = ColorStateList.valueOf(color(R.color.app_text_secondary))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        actions.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(6) })

        body.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(11) })
        body.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(40)
        ).apply { topMargin = dp(6) })

        root.addView(body)
        card.addView(root)
        return TabViewHolder(card, preview, title, status, meta, actions, primary, browser, close)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = items[position]
        val health = TabHealthStore.get(context, tab.id)
        val selected = tab.id in selectedIds

        holder.card.strokeColor = color(if (selected) R.color.app_accent else R.color.app_outline)
        holder.card.strokeWidth = dp(if (selected) 2 else 1)
        holder.title.text = displayTitle(tab)
        holder.status.text = stateLabel(tab, health)
        holder.status.setTextColor(stateColor(tab, health))
        holder.status.background = statusBackground(tab, health)
        holder.meta.text = dashboardMetadata(tab)
        holder.meta.visibility = if (holder.meta.text.isBlank()) View.GONE else View.VISIBLE

        val cached = TabThumbnailCache.load(context, tab.id)
        if (cached != null) {
            holder.preview.setImageBitmap(cached)
            holder.preview.contentDescription = displayTitle(tab)
        } else {
            holder.preview.setImageDrawable(null)
            holder.preview.setBackgroundColor(color(R.color.app_surface_raised))
            if (tab.isReady) onThumbnailNeeded(tab)
        }

        holder.actions.visibility = if (selectionMode) View.GONE else View.VISIBLE
        holder.primary.text = primaryActionLabel(tab, health)
        holder.primary.isEnabled = when (tab.preparationState) {
            VideoTabStore.PreparationState.READY,
            VideoTabStore.PreparationState.NEEDS_ATTENTION,
            VideoTabStore.PreparationState.ERROR -> true
            VideoTabStore.PreparationState.QUEUED,
            VideoTabStore.PreparationState.RESOLVING -> false
        }
        holder.primary.alpha = if (holder.primary.isEnabled) 1f else 0.58f
        holder.primary.setOnClickListener {
            if (selectionMode) toggleSelection(tab.id) else onPrimary(currentTab(holder, tab))
        }

        val showBrowser = !selectionMode && tab.preparationState == VideoTabStore.PreparationState.ERROR
        holder.browser.visibility = if (showBrowser) View.VISIBLE else View.GONE
        holder.browser.setOnClickListener {
            if (selectionMode) toggleSelection(tab.id) else onBrowser(currentTab(holder, tab))
        }

        holder.close.visibility = if (selectionMode) View.GONE else View.VISIBLE
        holder.close.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) removeAt(current)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(currentTab(holder, tab).id)
            } else if (holder.primary.isEnabled) {
                onPrimary(currentTab(holder, tab))
            }
        }
        holder.itemView.setOnLongClickListener {
            selectionMode = true
            toggleSelection(currentTab(holder, tab).id)
            true
        }
    }

    private fun currentTab(holder: TabViewHolder, fallback: VideoTabStore.VideoTab): VideoTabStore.VideoTab {
        val position = holder.bindingAdapterPosition
        return if (position != RecyclerView.NO_POSITION) items[position] else fallback
    }

    private fun toggleSelection(tabId: String) {
        selectionMode = true
        if (!selectedIds.add(tabId)) selectedIds.remove(tabId)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        onSelectionChanged(selectedTabs())
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (selectionMode || from !in items.indices || to !in items.indices || from == to) return false
        val moved = items[from]
        val delta = to - from
        items.removeAt(from)
        items.add(to, moved)
        onMove(moved, delta)
        notifyItemMoved(from, to)
        return true
    }

    fun removeAt(position: Int) {
        if (selectionMode || position !in items.indices) return
        val removed = items.removeAt(position)
        onClose(removed)
        notifyItemRemoved(position)
    }

    fun attachTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ItemTouchHelper.START or ItemTouchHelper.END
        ) {
            override fun isLongPressDragEnabled(): Boolean = !selectionMode
            override fun isItemViewSwipeEnabled(): Boolean = !selectionMode

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                gestureActive = actionState != ItemTouchHelper.ACTION_STATE_IDLE
                super.onSelectedChanged(viewHolder, actionState)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                gestureActive = false
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return moveItem(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) removeAt(position)
                gestureActive = false
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun displayTitle(tab: VideoTabStore.VideoTab): String {
        val stored = tab.title.trim()
        if (stored.isNotBlank() && !stored.equals("Video", ignoreCase = true)) return stored
        val host = runCatching { Uri.parse(tab.sourceUrl).host.orEmpty() }.getOrDefault("")
        return if (host.isNotBlank()) "Video • $host" else stored.ifBlank { "Video" }
    }

    private fun dashboardMetadata(tab: VideoTabStore.VideoTab): String {
        val details = mutableListOf<String>()
        if (tab.positionMs > 0L) details += formatPosition(tab.positionMs)
        if (tab.isReady) {
            val actual = tab.actualQualityHeight?.takeIf { it > 0 }
            val manual = tab.manualQualityHeight?.takeIf { it > 0 }
            details += when {
                manual != null && actual != null -> context.getString(R.string.dashboard_quality_manual_compact, manual, actual)
                manual != null -> context.getString(R.string.dashboard_manual_quality, manual)
                actual != null -> context.getString(R.string.dashboard_quality_auto_compact, actual)
                else -> context.getString(R.string.dashboard_auto_quality)
            }
        }
        return details.joinToString(" • ")
    }

    private fun primaryActionLabel(tab: VideoTabStore.VideoTab, health: TabHealthStore.Status): String = when {
        tab.preparationState == VideoTabStore.PreparationState.READY && health.state == TabHealthStore.State.NEEDS_REFRESH -> context.getString(R.string.dashboard_revive)
        tab.isReady && tab.positionMs > 0L -> context.getString(R.string.dashboard_continue)
        tab.isReady -> context.getString(R.string.dashboard_play)
        tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> context.getString(R.string.dashboard_browser)
        tab.preparationState == VideoTabStore.PreparationState.RESOLVING -> context.getString(R.string.tab_state_resolving)
        tab.preparationState == VideoTabStore.PreparationState.ERROR -> context.getString(R.string.dashboard_revive)
        else -> context.getString(R.string.tab_state_queued)
    }

    private fun stateLabel(tab: VideoTabStore.VideoTab, health: TabHealthStore.Status): String {
        if (tab.preparationState != VideoTabStore.PreparationState.READY) {
            return when (tab.preparationState) {
                VideoTabStore.PreparationState.QUEUED -> context.getString(R.string.tab_state_queued)
                VideoTabStore.PreparationState.RESOLVING -> context.getString(R.string.tab_state_resolving)
                VideoTabStore.PreparationState.READY -> context.getString(R.string.tab_state_ready)
                VideoTabStore.PreparationState.NEEDS_ATTENTION -> context.getString(R.string.tab_state_needs_attention)
                VideoTabStore.PreparationState.ERROR -> context.getString(R.string.tab_state_error)
            }
        }
        return when (health.state) {
            TabHealthStore.State.CHECKING -> context.getString(R.string.tab_health_checking)
            TabHealthStore.State.READY -> context.getString(R.string.tab_state_ready)
            TabHealthStore.State.NEEDS_REFRESH -> context.getString(R.string.tab_health_needs_refresh)
            TabHealthStore.State.UNAVAILABLE -> context.getString(R.string.tab_health_unavailable)
            TabHealthStore.State.NEEDS_ATTENTION -> context.getString(R.string.tab_state_needs_attention)
            TabHealthStore.State.UNKNOWN -> context.getString(R.string.tab_state_ready)
        }
    }

    private fun stateColor(tab: VideoTabStore.VideoTab, health: TabHealthStore.Status): Int = color(
        when {
            tab.preparationState == VideoTabStore.PreparationState.READY && health.state == TabHealthStore.State.READY -> R.color.app_success
            tab.preparationState == VideoTabStore.PreparationState.READY && health.state in setOf(TabHealthStore.State.NEEDS_REFRESH, TabHealthStore.State.UNAVAILABLE, TabHealthStore.State.NEEDS_ATTENTION) -> R.color.app_warning
            tab.preparationState == VideoTabStore.PreparationState.ERROR || tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION -> R.color.app_warning
            else -> R.color.app_text_secondary
        }
    )

    private fun statusBackground(tab: VideoTabStore.VideoTab, health: TabHealthStore.Status): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(50).toFloat()
        setColor(color(R.color.app_surface_raised))
        setStroke(dp(1), stateColor(tab, health))
    }

    private fun formatPosition(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun compactButton(textValue: String, description: String = textValue): Button = Button(context).apply {
        isAllCaps = false
        text = textValue
        contentDescription = description
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(color(R.color.app_text_primary))
        textSize = 12f
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    class TabViewHolder(
        itemView: View,
        val preview: ImageView,
        val title: TextView,
        val status: TextView,
        val meta: TextView,
        val actions: LinearLayout,
        val primary: Button,
        val browser: Button,
        val close: ImageButton
    ) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView get() = itemView as MaterialCardView
    }
}
