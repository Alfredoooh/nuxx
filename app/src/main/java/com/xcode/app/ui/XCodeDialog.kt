package com.xcode.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

object XCodeDialog {

    fun alert(
        ctx: Context,
        message: String,
        title: String = "XCode",
        onOk: (() -> Unit)? = null
    ) {
        val dialog = makeDialog(ctx)
        val root = makeRoot(ctx)
        val card = makeCard(ctx)

        card.addView(makeHeader(ctx, title, "#0e7af0"))
        card.addView(makeDivider(ctx))
        card.addView(makeBody(ctx, message))
        card.addView(makeDivider(ctx))

        val footer = makeFooterRow(ctx)
        footer.addView(makeBtn(ctx, "OK", "#0e7af0", "#0a1628") {
            dialog.dismiss(); onOk?.invoke()
        })
        card.addView(footer)

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
    }

    fun confirm(
        ctx: Context,
        message: String,
        title: String = "Confirmar",
        confirmText: String = "Confirmar",
        cancelText: String = "Cancelar",
        destructive: Boolean = false,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialog = makeDialog(ctx)
        val root = makeRoot(ctx)
        val card = makeCard(ctx)

        val headerColor = if (destructive) "#f44747" else "#e2c08d"
        card.addView(makeHeader(ctx, title, headerColor))
        card.addView(makeDivider(ctx))
        card.addView(makeBody(ctx, message))
        card.addView(makeDivider(ctx))

        val footer = makeFooterRow(ctx)
        footer.addView(makeBtn(ctx, cancelText, "#858585", "#2d2d30") {
            dialog.dismiss(); onCancel?.invoke()
        })

        val confirmColor = if (destructive) "#f44747" else "#0e7af0"
        val confirmBg = if (destructive) "#1a0a0a" else "#0a1628"
        val confirmBtn = makeBtn(ctx, confirmText, confirmColor, confirmBg) {
            dialog.dismiss(); onConfirm()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(ctx, 8)
        confirmBtn.layoutParams = lp
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
        password: Boolean = false,
        multiLine: Boolean = false,
        onConfirm: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialog = makeDialog(ctx)
        val root = makeRoot(ctx)
        val card = makeCard(ctx)

        card.addView(makeHeader(ctx, title, "#0e7af0"))
        card.addView(makeDivider(ctx))

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
        }
        val inputField = EditText(ctx).apply {
            this.hint = hint
            setText(initial)
            textSize = 13f
            setTextColor(Color.parseColor("#cccccc"))
            setHintTextColor(Color.parseColor("#444448"))
            typeface = Typeface.MONOSPACE
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            setSelectAllOnFocus(true)
            this.inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            if (multiLine) minLines = 3
        }
        val inputBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#2d2d30"))
            cornerRadius = dp(ctx, 5).toFloat()
            setStroke(dp(ctx, 1), Color.parseColor("#3e3e42"))
        }
        inputField.background = inputBg
        body.addView(inputField)
        card.addView(body)
        card.addView(makeDivider(ctx))

        val footer = makeFooterRow(ctx)
        footer.addView(makeBtn(ctx, "Cancelar", "#858585", "#2d2d30") {
            dialog.dismiss(); onCancel?.invoke()
        })
        val okBtn = makeBtn(ctx, "OK", "#0e7af0", "#0a1628") {
            dialog.dismiss(); onConfirm(inputField.text.toString())
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(ctx, 8)
        okBtn.layoutParams = lp
        footer.addView(okBtn)

        card.addView(footer)
        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
        inputField.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    fun choice(
        ctx: Context,
        title: String,
        options: List<String>,
        onChoice: (Int, String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialog = makeDialog(ctx)
        val root = makeRoot(ctx)
        val card = makeCard(ctx)

        card.addView(makeHeader(ctx, title, "#0e7af0"))
        card.addView(makeDivider(ctx))

        val scroll = ScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val listCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        }
        options.forEachIndexed { i, opt ->
            val item = TextView(ctx).apply {
                text = opt
                textSize = 13f
                setTextColor(Color.parseColor("#cccccc"))
                setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
                isClickable = true
                isFocusable = true
                val ripple = RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
                )
                foreground = ripple
                setOnClickListener { dialog.dismiss(); onChoice(i, opt) }
            }
            listCol.addView(item)
            if (i < options.size - 1) listCol.addView(makeDivider(ctx))
        }
        scroll.addView(listCol)
        card.addView(scroll)
        card.addView(makeDivider(ctx))

        val footer = makeFooterRow(ctx)
        footer.addView(makeBtn(ctx, "Cancelar", "#858585", "#2d2d30") {
            dialog.dismiss(); onCancel?.invoke()
        })
        card.addView(footer)

        root.addView(card)
        dialog.setContentView(root)
        dialog.show()
    }

    // ── Builders ──────────────────────────────────────────────────────────

    private fun makeDialog(ctx: Context): Dialog {
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }
        return dialog
    }

    private fun makeRoot(ctx: Context): FrameLayout = FrameLayout(ctx).apply {
        setPadding(dp(ctx, 28), 0, dp(ctx, 28), 0)
    }

    private fun makeCard(ctx: Context): LinearLayout {
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

    private fun makeHeader(ctx: Context, title: String, accentColor: String): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 12))
        }
        val accent = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 16)).apply {
                marginEnd = dp(ctx, 10)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(accentColor))
                cornerRadius = dp(ctx, 2).toFloat()
            }
        }
        val titleView = TextView(ctx).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(accent)
        row.addView(titleView)
        return row
    }

    private fun makeBody(ctx: Context, message: String): TextView = TextView(ctx).apply {
        text = message
        textSize = 13f
        setTextColor(Color.parseColor("#cccccc"))
        setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
        lineSpacingMultiplier = 1.45f
    }

    private fun makeFooterRow(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
    }

    private fun makeBtn(
        ctx: Context,
        text: String,
        textColor: String,
        bgColor: String,
        onClick: () -> Unit
    ): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor(textColor))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(bgColor))
            cornerRadius = dp(ctx, 5).toFloat()
            setStroke(dp(ctx, 1), Color.parseColor("#3e3e42"))
        }
        isClickable = true
        isFocusable = true
        foreground = RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
        )
        setOnClickListener { onClick() }
    }

    private fun makeDivider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#2d2d30"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
        )
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}