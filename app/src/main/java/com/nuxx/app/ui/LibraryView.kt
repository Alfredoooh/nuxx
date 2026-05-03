package com.doction.webviewapp.ui

import android.annotation.SuppressLint
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
class LibraryView(context: android.content.Context) : FrameLayout(context) {

    private val activity = context as MainActivity

    private lateinit var appBarBg:    View
    private lateinit var appBarTitle: TextView
    private lateinit var contentBg:   View

    init {
        setBackgroundColor(AppTheme.bg)
        // Statusbar gerida exclusivamente pelo MainActivity.switchTab — não interferir aqui
        buildAppBar()
        buildContent()
    }

    private fun buildAppBar() {
        val appBar = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        appBar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        appBarTitle = TextView(context).apply {
            text = "Biblioteca"
            setTextColor(AppTheme.text)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        appBar.addView(appBarTitle, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))

        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildContent() {
        contentBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        addView(contentBg, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val placeholder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        placeholder.addView(TextView(context).apply {
            text = "Em breve"
            setTextColor(AppTheme.textSecondary)
            textSize = 14f
            gravity = Gravity.CENTER
        })
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}