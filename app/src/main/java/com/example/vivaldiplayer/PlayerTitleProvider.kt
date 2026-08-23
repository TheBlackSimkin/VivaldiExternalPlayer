package com.example.vivaldiplayer

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/** Runtime bridge so an in-player source refresh can update the visible title. */
object PlayerTitleRuntime {
    private val titles = WeakHashMap<PlayerActivity, TextView>()

    fun register(activity: PlayerActivity, view: TextView) {
        titles[activity] = view
    }

    fun update(activity: PlayerActivity, title: String) {
        activity.title = title
        titles[activity]?.text = title
    }

    fun unregister(activity: PlayerActivity) {
        titles.remove(activity)
    }
}

/** Adds the current source-provided video title to non-fullscreen Player UI. */
class PlayerTitleProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(PlayerTitleLifecycle)
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
}

private object PlayerTitleLifecycle : Application.ActivityLifecycleCallbacks {
    private const val TITLE_TAG = "external_player_video_title"

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is PlayerActivity) return
        activity.window.decorView.post { attach(activity) }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is PlayerActivity) PlayerTitleRuntime.unregister(activity)
    }

    private fun attach(activity: PlayerActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val existing = decor.findViewWithTag<TextView>(TITLE_TAG)
        val titleView = existing ?: TextView(activity).apply {
            tag = TITLE_TAG
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary))
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 14).toFloat()
                setColor(ColorUtils.setAlphaComponent(ContextCompat.getColor(activity, R.color.app_surface), 220))
                setStroke(dp(activity, 1), ContextCompat.getColor(activity, R.color.app_outline))
            }
            activity.addContentView(this, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(activity, 12)
                marginStart = dp(activity, 56)
                marginEnd = dp(activity, 56)
            })
        }

        val currentTitle = activity.title?.toString().orEmpty().ifBlank { activity.getString(R.string.home_brand) }
        titleView.text = currentTitle
        PlayerTitleRuntime.register(activity, titleView)

        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView) { _, insets ->
            titleView.visibility = if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) View.VISIBLE else View.GONE
            insets
        }
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
