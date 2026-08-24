package com.example.vivaldiplayer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/** Thumbnail grid for the bounded Recently Closed recovery archive. */
class RecentlyClosedAdapter(
    private val context: Context,
    private val onRestore: (VideoTabStore.VideoTab) -> Unit
) : RecyclerView.Adapter<RecentlyClosedAdapter.Holder>() {

    private val items = mutableListOf<VideoTabStore.VideoTab>()

    init { setHasStableIds(true) }

    fun submit(tabs: List<VideoTabStore.VideoTab>) {
        items.clear()
        items.addAll(tabs)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val card = MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(R.color.app_surface))
            strokeColor = color(R.color.app_outline)
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        val title = TextView(context).apply {
            maxLines = 2
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(color(R.color.app_text_primary))
        }
        body.addView(title)

        val meta = TextView(context).apply {
            maxLines = 2
            textSize = 11f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(0, dp(7), 0, 0)
        }
        body.addView(meta)

        val diagnosticsButton = Button(context).apply {
            isAllCaps = false
            text = context.getString(R.string.diagnostics_history)
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_secondary))
        }
        body.addView(diagnosticsButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(8) })

        val diagnostics = TextView(context).apply {
            visibility = View.GONE
            textSize = 10f
            setTextColor(color(R.color.app_text_secondary))
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setTextIsSelectable(true)
        }
        body.addView(diagnostics)

        val restore = Button(context).apply {
            isAllCaps = false
            text = context.getString(R.string.restore)
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minHeight = 0
            minimumHeight = 0
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_accent))
            setTextColor(color(R.color.white))
        }
        body.addView(restore, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })

        root.addView(body)
        card.addView(root)
        return Holder(card, preview, title, meta, diagnosticsButton, diagnostics, restore)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tab = items[position]
        holder.title.text = displayTitle(tab)
        holder.meta.text = metadata(tab)
        holder.diagnostics.visibility = View.GONE
        holder.diagnosticsButton.text = context.getString(R.string.diagnostics_history)
        holder.diagnosticsButton.setOnClickListener {
            val opening = holder.diagnostics.visibility != View.VISIBLE
            if (opening) {
                val lines = OperationLog.recentForTab(context, tab.id)
                holder.diagnostics.text = if (lines.isEmpty()) context.getString(R.string.diagnostics_history_empty) else lines.joinToString("\n")
            }
            holder.diagnostics.visibility = if (opening) View.VISIBLE else View.GONE
            holder.diagnosticsButton.text = (if (opening) "⌃ " else "") + context.getString(R.string.diagnostics_history)
        }

        val bitmap = TabThumbnailCache.load(context, tab.id)
        if (bitmap != null) {
            holder.preview.setImageBitmap(bitmap)
            holder.preview.contentDescription = displayTitle(tab)
        } else {
            holder.preview.setImageDrawable(null)
            holder.preview.setBackgroundColor(color(R.color.app_surface_raised))
        }

        val action = {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) onRestore(items[current])
        }
        holder.restore.setOnClickListener { action() }
        holder.itemView.setOnClickListener { action() }
    }

    private fun displayTitle(tab: VideoTabStore.VideoTab): String {
        val stored = tab.title.trim()
        if (stored.isNotBlank() && !stored.equals("Video", ignoreCase = true)) return stored
        val host = runCatching { Uri.parse(tab.sourceUrl).host.orEmpty() }.getOrDefault("")
        return if (host.isNotBlank()) "Video • $host" else stored.ifBlank { "Video" }
    }

    private fun metadata(tab: VideoTabStore.VideoTab): String {
        val parts = mutableListOf<String>()
        if (tab.positionMs > 0L) parts += context.getString(R.string.recently_closed_resume_at, formatPosition(tab.positionMs))
        tab.actualQualityHeight?.takeIf { it > 0 }?.let { parts += context.getString(R.string.recently_closed_quality, it) }
        if (parts.isEmpty()) parts += context.getString(R.string.recently_closed_ready_to_restore)
        return parts.joinToString(" • ")
    }

    private fun formatPosition(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    class Holder(
        itemView: View,
        val preview: ImageView,
        val title: TextView,
        val meta: TextView,
        val diagnosticsButton: Button,
        val diagnostics: TextView,
        val restore: Button
    ) : RecyclerView.ViewHolder(itemView)
}
