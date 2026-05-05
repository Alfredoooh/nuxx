package com.xcode.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.view.WindowCompat

object XCodeDialog {

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    private fun createDialog(ctx: Context): Dialog {
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }
        return dialog
    }

    private fun buildCard(ctx: Context): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#252526"))
            cornerRadius = dp(ctx, 10).toFloat()
            setStroke(dp(ctx, 1), Color.parseColor("#3e3e42"))
        }
        card.background = bg
        return card
    }

    fun alert(ctx: Context, message: String, onOk: (() -> Unit)? = null) {
        val dialog = createDialog(ctx)
        val root = FrameLayout(ctx).apply {
            setPadding(dp(ctx, 32), 0, dp(ctx, 32), 0)
        }
        val card = buildCard(ctx)

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
        }
        val headerIcon = TextView(ctx).apply {
            text = "ℹ"
            textSize = 14f
            setTextColor(Color.parseColor("#0e7af0"))
            setPadding(0, 0, dp(ctx, 8), 0)
        }
        val headerTitle = TextView(ctx).apply {
            text = "XCode"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val div1 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        header.addView(headerIcon)
        header.addView(headerTitle)
        card.addView(header)
        card.addView(div1)

        // Body
        val body = TextView(ctx).apply {
            text = message
            textSize = 13f
            setTextColor(Color.parseColor("#cccccc"))
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
            lineSpacingMultiplier = 1.4f
        }
        card.addView(body)

        // Footer div
        val div2 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        card.addView(div2)

        // Footer
        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
        }
        val okBtn = buildButton(ctx, "OK", "#0e7af0", "#0e1a2e", true)
        okBtn.setOnClickListener {
            dialog.dismiss()
            onOk?.invoke()
        }
        footer.addView(okBtn)
        card.addView(footer)

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
    }

    fun confirm(
        ctx: Context,
        message: String,
        confirmText: String = "Confirmar",
        cancelText: String = "Cancelar",
        destructive: Boolean = false,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialog = createDialog(ctx)
        val root = FrameLayout(ctx).apply {
            setPadding(dp(ctx, 32), 0, dp(ctx, 32), 0)
        }
        val card = buildCard(ctx)

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
        }
        val headerIcon = TextView(ctx).apply {
            text = if (destructive) "⚠" else "?"
            textSize = 14f
            setTextColor(if (destructive) Color.parseColor("#f44747") else Color.parseColor("#e2c08d"))
            setPadding(0, 0, dp(ctx, 8), 0)
        }
        val headerTitle = TextView(ctx).apply {
            text = "Confirmar"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val div1 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        header.addView(headerIcon)
        header.addView(headerTitle)
        card.addView(header)
        card.addView(div1)

        val body = TextView(ctx).apply {
            text = message
            textSize = 13f
            setTextColor(Color.parseColor("#cccccc"))
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
            lineSpacingMultiplier = 1.4f
        }
        card.addView(body)

        val div2 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        card.addView(div2)

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
            weightSum = 0f
        }

        val cancelBtn = buildButton(ctx, cancelText, "#858585", "#2d2d30", false)
        cancelBtn.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }
        val confirmColor = if (destructive) "#f44747" else "#0e7af0"
        val confirmBgColor = if (destructive) "#2a0d0d" else "#0e1a2e"
        val confirmBtn = buildButton(ctx, confirmText, confirmColor, confirmBgColor, true)
        confirmBtn.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(ctx, 8)
        confirmBtn.layoutParams = lp

        footer.addView(cancelBtn)
        footer.addView(confirmBtn)
        card.addView(footer)

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
    }

    fun input(
        ctx: Context,
        title: String,
        hint: String = "",
        initial: String = "",
        inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
        onConfirm: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialog = createDialog(ctx)
        val root = FrameLayout(ctx).apply {
            setPadding(dp(ctx, 32), 0, dp(ctx, 32), 0)
        }
        val card = buildCard(ctx)

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
        }
        val headerTitle = TextView(ctx).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val div1 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        header.addView(headerTitle)
        card.addView(header)
        card.addView(div1)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
        }

        val inputField = EditText(ctx).apply {
            this.hint = hint
            setText(initial)
            this.inputType = inputType
            setTextColor(Color.parseColor("#cccccc"))
            setHintTextColor(Color.parseColor("#555558"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            setBackgroundColor(Color.TRANSPARENT)
            setSelectAllOnFocus(true)
        }
        val inputBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#3c3c3c"))
            cornerRadius = dp(ctx, 5).toFloat()
            setStroke(dp(ctx, 1), Color.parseColor("#3e3e42"))
        }
        inputField.background = inputBg

        body.addView(inputField)
        card.addView(body)

        val div2 = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
        }
        card.addView(div2)

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
        }
        val cancelBtn = buildButton(ctx, "Cancelar", "#858585", "#2d2d30", false)
        cancelBtn.setOnClickListener { dialog.dismiss(); onCancel?.invoke() }
        val confirmBtn = buildButton(ctx, "OK", "#0e7af0", "#0e1a2e", true)
        confirmBtn.setOnClickListener {
            dialog.dismiss()
            onConfirm(inputField.text.toString())
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(ctx, 8)
        confirmBtn.layoutParams = lp

        footer.addView(cancelBtn)
        footer.addView(confirmBtn)
        card.addView(footer)

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()

        inputField.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun buildButton(
        ctx: Context,
        text: String,
        textColor: String,
        bgColor: String,
        bold: Boolean
    ): TextView {
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor(textColor))
            if (bold) typeface = android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL
            )
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(ctx, 5).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#3e3e42"))
            }
            background = bg
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#22ffffff")
            )
            foreground = android.graphics.drawable.RippleDrawable(ripple, null, null)
        }
    }
}