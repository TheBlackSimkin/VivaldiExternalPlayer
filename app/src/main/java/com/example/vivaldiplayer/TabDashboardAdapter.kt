package com.example.vivaldiplayer

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * RecyclerView-backed persistent tab dashboard.
 *
 * Why RecyclerView/ItemTouchHelper instead of the old card OnTouchListener?
 * - NestedScrollView could steal the old horizontal gesture, so swipe-close was
 *   unreliable on real devices.
 * - ItemTouchHelper has native long-press drag and horizontal swipe arbitration.
 * - Reordering can happen directly on the card, with no tiny arrow buttons.
 *
 * The dashboard also refreshes preparation states while visible. `gestureActive`
 * prevents that periodic refresh from calling notifyDataSetChanged in the middle
 * of a long-press drag or swipe, which would otherwise cancel the gesture.
 *
 * For the focused BG lifecycle QA, cards also show one compact local `tech ...`
 * marker. It is based only on persisted lifecycle timestamps/stage names; it does
 * not expose media content, thumbnails, page text or credentials.
 */
class TabDashboardAdapter(
    private val context: Context,
    private val onPrimary: (VideoTabStore.VideoTab) -> Unit,
    private val onBrowser: (VideoTabStore.VideoTab) -> Unit,
    private val onClose: (VideoTabStore.VideoTab) -> Unit,
    private val onMove: (VideoTabStore.VideoTab, Int) -> Unit,
    private val onThumbnailNeeded: (VideoTabStore.VideoTab) -> Unit
) : RecyclerView.Adapter<TabDashboardAdapter.TabViewHolder>() {

    private val items = mutableListOf<VideoTabStore.VideoTab>()
    private var gestureActive = false

    init {
        setHasStableIds(true)
    }

    fun isUserInteracting(): Boolean = gestureActive

    fun submitTabs(tabs: List<VideoTabStore.VideoTab>) {
        if (gestureActive) return
        items.clear()
        items.addAll(tabs)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.app_surface))
            strokeColor = ContextCompat.getColor(context, R.color.app_outline)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
        }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface_raised))
            contentDescription = context.getString(R.string.tab_thumbnail_pending)
        }
        top.addView(preview, LinearLayout.LayoutParams(dp(124), dp(70)))

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        val title = TextView(context).apply {
            maxLines = 2
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
        }
        textColumn.addView(title)

        val details = TextView(context).apply {
            textSize = 12f
            setPadding(0, dp(5), 0, 0)
        }
        textColumn.addView(details)

        top.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(top)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val primary = compactButton("")
        actions.addView(primary, LinearLayout.LayoutParams(0, dp(42), 1f))

        val browser = compactButton(context.getString(R.string.dashboard_browser)).apply {
            visibility = View.GONE
        }
        actions.addView(browser)

        val close = compactButton("×", context.getString(R.string.dashboard_close))
        actions.addView(close)

        root.addView(actions)
        card.addView(root)

        return TabViewHolder(card, preview, title, details, primary, browser, close)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = items[position]

        holder.title.text = displayTitle(tab)
        holder.details.text = dashboardDetails(tab)
        holder.details.setTextColor(stateColor(tab.preparationState))

        val cached = TabThumbnailCache.load(context, tab.id)
        if (cached != null) {
            holder.preview.setImageBitmap(cached)
            holder.preview.contentDescription = displayTitle(tab)
        } else {
            holder.preview.setImageDrawable(null)
            holder.preview.setBackgroundColor(ContextCompat.getColor(context, R.color.app_surface_raised))
            if (tab.isReady) onThumbnailNeeded(tab)
        }

        holder.primary.text = primaryActionLabel(tab)
        holder.primary.isEnabled = when (tab.preparationState) {
            VideoTabStore.PreparationState.READY,
            VideoTabStore.PreparationState.NEEDS_ATTENTION,
            VideoTabStore.PreparationState.ERROR -> true
            VideoTabStore.PreparationState.QUEUED,
            VideoTabStore.PreparationState.RESOLVING -> false
        }
        holder.primary.setOnClickListener { onPrimary(items.getOrNull(holder.bindingAdapterPosition) ?: tab) }

        val showBrowser = tab.preparationState == VideoTabStore.PreparationState.ERROR
        holder.browser.visibility = if (showBrowser) View.VISIBLE else View.GONE
        holder.browser.setOnClickListener { onBrowser(items.getOrNull(holder.bindingAdapterPosition) ?: tab) }

        holder.close.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) removeAt(current)
        }
    }

    /** Called from ItemTouchHelper while a long-press drag crosses another card. */
    fun moveItem(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val moved = items[from]
        val delta = to - from
        items.removeAt(from)
        items.add(to, moved)
        onMove(moved, delta)
        notifyItemMoved(from, to)
        return true
    }

    /** Called from both swipe-to-close and the explicit × affordance. */
    fun removeAt(position: Int) {
        if (position !in items.indices) return
        val removed = items.removeAt(position)
        onClose(removed)
        notifyItemRemoved(position)
    }

    fun attachTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END
        ) {
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = true

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

        /*
         * Pending tabs may not have a page title yet. Showing only the local host
         * is technical metadata and makes several pending tabs distinguishable
         * without pretending we already resolved their media/title.
         */
        val host = runCatching { Uri.parse(tab.sourceUrl).host.orEmpty() }.getOrDefault("")
        return if (host.isNotBlank()) "Video • $host" else stored.ifBlank { "Video" }
    }

    private fun dashboardDetails(tab: VideoTabStore.VideoTab): String {
        val details = mutableListOf(stateLabel(tab.preparationState))
        if (tab.positionMs > 0L) details += formatPosition(tab.positionMs)

        technicalStageLabel(tab)?.let(details::add)

        if (tab.isReady) {
            tab.manualQualityHeight?.let {
                details += context.getString(R.string.dashboard_manual_quality, it)
            } ?: run {
                details += context.getString(R.string.dashboard_auto_quality)
            }
            tab.actualQualityHeight?.let {
                details += context.getString(R.string.dashboard_actual_quality, it)
            }
        }
        return details.joinToString(" • ")
    }

    /**
     * Example: `tech DIRECT_STARTED +1s`.
     * The relative time is measured from tab creation and remains useful even if
     * the app is opened much later, after preparation has already completed.
     */
    private fun technicalStageLabel(tab: VideoTabStore.VideoTab): String? {
        val stage = tab.lastTechnicalPreparationStage.trim().takeIf { it.isNotBlank() } ?: return null
        val at = tab.lastTechnicalStageAtMs.takeIf { it > 0L } ?: return "tech $stage"
        val elapsedSeconds = ((at - tab.createdAtMs).coerceAtLeast(0L) / 1000L)
        return "tech $stage +${elapsedSeconds}s"
    }

    private fun primaryActionLabel(tab: VideoTabStore.VideoTab): String = when {
        tab.isReady && tab.positionMs > 0L -> context.getString(R.string.dashboard_continue)
        tab.isReady -> context.getString(R.string.dashboard_play)
        tab.preparationState == VideoTabStore.PreparationState.NEEDS_ATTENTION ->
            context.getString(R.string.dashboard_browser)
        tab.preparationState == VideoTabStore.PreparationState.RESOLVING ->
            context.getString(R.string.tab_state_resolving)
        tab.preparationState == VideoTabStore.PreparationState.ERROR -> context.getString(R.string.retry)
        else -> context.getString(R.string.tab_state_queued)
    }

    private fun stateLabel(state: VideoTabStore.PreparationState): String = when (state) {
        VideoTabStore.PreparationState.QUEUED -> context.getString(R.string.tab_state_queued)
        VideoTabStore.PreparationState.RESOLVING -> context.getString(R.string.tab_state_resolving)
        VideoTabStore.PreparationState.READY -> context.getString(R.string.tab_state_ready)
        VideoTabStore.PreparationState.NEEDS_ATTENTION -> context.getString(R.string.tab_state_needs_attention)
        VideoTabStore.PreparationState.ERROR -> context.getString(R.string.tab_state_error)
    }

    private fun stateColor(state: VideoTabStore.PreparationState): Int = ContextCompat.getColor(
        context,
        when (state) {
            VideoTabStore.PreparationState.READY -> R.color.app_success
            VideoTabStore.PreparationState.ERROR,
            VideoTabStore.PreparationState.NEEDS_ATTENTION -> R.color.app_warning
            else -> R.color.app_text_secondary
        }
    )

    private fun formatPosition(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun compactButton(textValue: String, description: String = textValue): Button = Button(context).apply {
        isAllCaps = false
        text = textValue
        contentDescription = description
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    class TabViewHolder(
        itemView: View,
        val preview: ImageView,
        val title: TextView,
        val details: TextView,
        val primary: Button,
        val browser: Button,
        val close: Button
    ) : RecyclerView.ViewHolder(itemView)
}
