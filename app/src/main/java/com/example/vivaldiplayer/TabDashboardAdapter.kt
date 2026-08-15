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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Grid adapter for persistent open tabs.
 *
 * This UI intentionally shows user-facing state only. Resolver lifecycle markers
 * such as DIRECT_STARTED/BROWSER_* remain in diagnostics and OperationLog instead
 * of occupying normal card space.
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

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(96)
            )
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
        body.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        val meta = TextView(context).apply {
            maxLines = 2
            textSize = 11f
            setTextColor(color(R.color.app_text_secondary))
        }
        body.addView(
            meta,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        )

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

        val close = compactButton("×", context.getString(R.string.dashboard_close)).apply {
            textSize = 20f
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
        }
        actions.addView(
            close,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(6) }
        )

        body.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(11) }
        )
        body.addView(
            browser,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { topMargin = dp(6) }
        )

        root.addView(body)
        card.addView(root)

        return TabViewHolder(card, preview, title, status, meta, primary, browser, close)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = items[position]

        holder.title.text = displayTitle(tab)
        holder.status.text = stateLabel(tab.preparationState)
        holder.status.setTextColor(stateColor(tab.preparationState))
        holder.status.background = statusBackground(tab.preparationState)
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

        holder.primary.text = primaryActionLabel(tab)
        holder.primary.isEnabled = when (tab.preparationState) {
            VideoTabStore.PreparationState.READY,
            VideoTabStore.PreparationState.NEEDS_ATTENTION,
            VideoTabStore.PreparationState.ERROR -> true
            VideoTabStore.PreparationState.QUEUED,
            VideoTabStore.PreparationState.RESOLVING -> false
        }
        holder.primary.alpha = if (holder.primary.isEnabled) 1f else 0.58f
        holder.primary.setOnClickListener {
            onPrimary(items.getOrNull(holder.bindingAdapterPosition) ?: tab)
        }

        val showBrowser = tab.preparationState == VideoTabStore.PreparationState.ERROR
        holder.browser.visibility = if (showBrowser) View.VISIBLE else View.GONE
        holder.browser.setOnClickListener {
            onBrowser(items.getOrNull(holder.bindingAdapterPosition) ?: tab)
        }

        holder.close.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) removeAt(current)
        }

        holder.itemView.setOnClickListener {
            if (holder.primary.isEnabled) {
                onPrimary(items.getOrNull(holder.bindingAdapterPosition) ?: tab)
            }
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
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
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

        /* Pending tabs may not have a title yet; the host is useful non-content metadata. */
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
                manual != null && actual != null ->
                    context.getString(R.string.dashboard_quality_manual_compact, manual, actual)
                manual != null -> context.getString(R.string.dashboard_manual_quality, manual)
                actual != null -> context.getString(R.string.dashboard_quality_auto_compact, actual)
                else -> context.getString(R.string.dashboard_auto_quality)
            }
        }

        return details.joinToString(" • ")
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

    private fun stateColor(state: VideoTabStore.PreparationState): Int = color(
        when (state) {
            VideoTabStore.PreparationState.READY -> R.color.app_success
            VideoTabStore.PreparationState.ERROR,
            VideoTabStore.PreparationState.NEEDS_ATTENTION -> R.color.app_warning
            else -> R.color.app_text_secondary
        }
    )

    private fun statusBackground(state: VideoTabStore.PreparationState): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(50).toFloat()
            setColor(color(R.color.app_surface_raised))
            setStroke(dp(1), stateColor(state))
        }

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

    private fun compactButton(textValue: String, description: String = textValue): Button =
        Button(context).apply {
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
        val primary: Button,
        val browser: Button,
        val close: Button
    ) : RecyclerView.ViewHolder(itemView)
}
