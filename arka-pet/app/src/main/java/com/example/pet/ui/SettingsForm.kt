package com.example.pet.ui

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.example.pet.R

/** One settings screen's worth of stored values, kept crypto-free so the UI is testable. */
data class SettingsValues(
    val provider: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val wander: Boolean,
    val autoStart: Boolean,
)

/**
 * Builds the settings screen: clay header, form fields grouped into clay cards, toggles,
 * clay save button. Split from [SettingsActivity] because the Activity cannot be launched
 * in tests — Robolectric has no AndroidKeyStore for the encrypted preferences.
 */
class SettingsForm(
    private val context: Context,
    values: SettingsValues,
    onBack: () -> Unit,
    onSave: (SettingsValues) -> Unit,
) {
    val root: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val cardPad = context.resources.getDimensionPixelSize(R.dimen.card_padding)
    private val cardMargin = context.resources.getDimensionPixelSize(R.dimen.card_margin)

    private val providerInput = fieldInput(hint = "GEMINI", value = values.provider)
    private val urlInput = fieldInput(hint = "https://...", value = values.baseUrl)
    private val modelInput = fieldInput(hint = "gemini-2.0-flash", value = values.model)
    private val keyInput = fieldInput(hint = "sk-...", value = values.apiKey)
    private val wanderSwitch = toggle("Wander when idle", values.wander)
    private val autoStartSwitch = toggle("Auto-start on boot", values.autoStart)

    init {
        // Header: back affordance + screen title (same shape as the gallery header).
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, cardMargin, 0, cardMargin)
                addView(
                    ImageButton(context).apply {
                        setImageResource(android.R.drawable.ic_menu_revert)
                        contentDescription = "Kembali"
                        val ripple = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                        background = ripple.getDrawable(0)
                        ripple.recycle()
                        minimumWidth = resources.getDimensionPixelSize(R.dimen.touch_target)
                        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                        setOnClickListener { onBack() }
                    },
                )
                addView(
                    TextView(context).apply {
                        text = "Pengaturan"
                        textSize = 20f
                        setTextColor(context.getColor(R.color.clay_text))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
            },
        )

        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        content.addView(
            clayCard().apply {
                addFieldLabel("Provider (preset name)")
                addView(providerInput)
                addFieldLabel("Base URL (custom/override)")
                addView(urlInput)
                addFieldLabel("Model")
                addView(modelInput)
                addFieldLabel("API key")
                addView(keyInput)
            },
        )

        content.addView(
            clayCard().apply {
                addView(wanderSwitch)
                addView(autoStartSwitch)
            },
        )

        content.addView(
            Button(context).apply {
                text = "Save"
                background = resources.getDrawable(R.drawable.clay_button_bg, null)
                setTextColor(context.getColor(R.color.clay_on_accent))
                minHeight = resources.getDimensionPixelSize(R.dimen.button_min_height)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = cardMargin }
                setOnClickListener {
                    onSave(
                        SettingsValues(
                            provider = providerInput.text.toString().trim(),
                            baseUrl = urlInput.text.toString().trim(),
                            model = modelInput.text.toString().trim(),
                            apiKey = keyInput.text.toString().trim(),
                            wander = wanderSwitch.isChecked,
                            autoStart = autoStartSwitch.isChecked,
                        ),
                    )
                }
            },
        )

        // Tall form + keyboard: without scrolling the lower fields hide behind the keyboard.
        val scroll = ScrollView(context).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun clayCard(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.resources.getDrawable(R.drawable.clay_card_bg, null)
        setPadding(cardPad, cardPad, cardPad, cardPad)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = cardMargin }
    }

    private fun LinearLayout.addFieldLabel(text: String) {
        addView(
            TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(context.getColor(R.color.clay_text_secondary))
            },
        )
    }

    private fun fieldInput(hint: String, value: String): EditText = EditText(context).apply {
        this.hint = hint
        setText(value)
        setTextColor(context.getColor(R.color.clay_text))
        setSingleLine(true)
    }

    private fun toggle(label: String, checked: Boolean): Switch = Switch(context).apply {
        text = label
        isChecked = checked
    }
}
