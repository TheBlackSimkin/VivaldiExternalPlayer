package com.example.vivaldiplayer

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * Compact, collapsed-by-default search/filter control used by tab collections.
 * It only filters metadata already stored by the app; it never reads media pixels.
 */
class AccordionSearchFilter(
    private val context: Context,
    options: List<Option>,
    private val onChanged: (query: String, filterId: String) -> Unit
) {
    data class Option(val id: String, val label: String)

    val view: View

    private var query = ""
    private var selectedFilterId = options.firstOrNull()?.id.orEmpty()
    private val optionButtons = linkedMapOf<String, Button>()

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toggle = Button(context).apply {
            isAllCaps = false
            text = context.getString(R.string.search_filter)
            textSize = 12f
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_surface_raised))
            setTextColor(color(R.color.app_text_primary))
        }
        root.addView(toggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44)
        ))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(4))
        }

        val search = EditText(context).apply {
            hint = context.getString(R.string.search_tabs_hint)
            isSingleLine = true
            setTextColor(color(R.color.app_text_primary))
            setHintTextColor(color(R.color.app_text_secondary))
            backgroundTintList = ColorStateList.valueOf(color(R.color.app_text_secondary))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty().trim()
                    notifyChanged()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        panel.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ))

        if (options.isNotEmpty()) {
            val filterRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }

            options.forEach { option ->
                val button = Button(context).apply {
                    isAllCaps = false
                    text = option.label
                    textSize = 11f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener {
                        selectedFilterId = option.id
                        updateOptionStyles()
                        notifyChanged()
                    }
                }
                optionButtons[option.id] = button
                filterRow.addView(button, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40)
                ).apply { marginEnd = dp(5) })
            }

            panel.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(filterRow)
            })
            updateOptionStyles()
        }

        toggle.setOnClickListener {
            val opening = panel.visibility != View.VISIBLE
            panel.visibility = if (opening) View.VISIBLE else View.GONE
            toggle.text = if (opening) "⌃ ${context.getString(R.string.search_filter)}" else context.getString(R.string.search_filter)
            if (opening) search.requestFocus()
        }

        root.addView(panel)
        view = root
    }

    private fun updateOptionStyles() {
        optionButtons.forEach { (id, button) ->
            val selected = id == selectedFilterId
            button.backgroundTintList = ColorStateList.valueOf(
                color(if (selected) R.color.app_accent_soft else R.color.app_surface_raised)
            )
            button.setTextColor(color(R.color.app_text_primary))
        }
    }

    private fun notifyChanged() {
        onChanged(query, selectedFilterId)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
