// SettingsPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.theme.AppTheme

@SuppressLint("ViewConstructor")
class SettingsPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity

    init {
        setBackgroundColor(AppTheme.bg)
        activity.setStatusBarDark(false)
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activity.setStatusBarDark(false)
    }

    fun onBackPressed() { activity.closeSettings() }

    private fun buildUI() {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Status bar spacer
        col.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))

        // AppBar
        val appBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(AppTheme.bg)
            setPadding(dp(4), 0, dp(16), 0)
        }
        val backBtn = androidx.appcompat.widget.AppCompatImageView(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onBackPressed() }
            try {
                val svg = com.caverock.androidsvg.SVG.getFromAsset(context.assets, "icons/svg/back_arrow.svg")
                val px  = dp(20); svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(android.graphics.Canvas(bmp))
                setImageBitmap(bmp); setColorFilter(AppTheme.icon)
            } catch (_: Exception) {}
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        appBar.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(52)))
        appBar.addView(TextView(context).apply {
            text = "Definições"; setTextColor(AppTheme.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD); letterSpacing = -0.02f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(appBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

        // Divider
        col.addView(View(context).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Placeholder
        val empty = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        empty.addView(TextView(context).apply {
            text = "Em breve"; setTextColor(AppTheme.textTertiary)
            textSize = 14f; gravity = Gravity.CENTER
        })
        col.addView(empty, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
} 