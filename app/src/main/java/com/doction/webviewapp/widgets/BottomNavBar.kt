package com.doction.webviewapp.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.theme.AppTheme

class BottomNavBar(private val activity: MainActivity) {

    val view: LinearLayout

    private val navItems = listOf(
        Pair("icons/svg/browse_filled.svg",  "icons/svg/browse_outline.svg"),
        Pair("icons/svg/explore_filled.svg", "icons/svg/explore_outline.svg"),
        Pair("icons/svg/search_filled.svg",  "icons/svg/search_outline.svg"),
        Pair("icons/svg/library_filled.svg", "icons/svg/library_outline.svg"),
    )

    private val navHeightDp = 48

    // No tab home (index 0) o fundo é escuro; nos restantes é claro
    private fun bgFor(tabIndex: Int) =
        if (tabIndex == 0) Color.parseColor("#0A0A0A") else AppTheme.navBg

    private fun activeIconFor(tabIndex: Int, isHomeTab: Boolean) =
        if (isHomeTab) Color.WHITE else AppTheme.navActive

    private fun inactiveIconFor(tabIndex: Int, isHomeTab: Boolean) =
        if (isHomeTab) Color.parseColor("#888888") else AppTheme.navInactive

    init {
        view = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgFor(0))
        }

        navItems.forEachIndexed { index, item ->
            val isActive = index == 0
            val btn = FrameLayout(activity).apply {
                tag = "nav_btn_$index"
                isClickable = true
                isFocusable = true
                foreground = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33000000")), null, null
                )
            }
            val icon = buildIcon(
                if (isActive) item.first else item.second,
                if (isActive) activeIconFor(0, true) else inactiveIconFor(0, true)
            ).apply { tag = "nav_icon_$index" }

            btn.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24)).also {
                it.gravity = Gravity.CENTER
            })
            view.addView(btn, LinearLayout.LayoutParams(0, dp(navHeightDp), 1f))
        }
    }

    fun applyTheme(currentTab: Int) {
        val isHomeTab = currentTab == 0
        val bg = bgFor(currentTab)
        view.setBackgroundColor(bg)
        activity.window.navigationBarColor = bg

        // Ripple adapta-se ao fundo
        val rippleColor = if (isHomeTab)
            Color.parseColor("#33FFFFFF") else Color.parseColor("#33000000")

        for (i in navItems.indices) {
            val btn = view.findViewWithTag<FrameLayout>("nav_btn_$i") ?: continue
            btn.foreground = RippleDrawable(
                ColorStateList.valueOf(rippleColor), null, null
            )
            updateIcon(i, i == currentTab, isHomeTab)
        }
    }

    fun updateIcon(index: Int, active: Boolean, isHomeTab: Boolean) {
        val btn  = view.findViewWithTag<FrameLayout>("nav_btn_$index") ?: return
        val icon = btn.findViewWithTag<android.widget.ImageView>("nav_icon_$index") ?: return
        val tint = if (active) activeIconFor(index, isHomeTab) else inactiveIconFor(index, isHomeTab)
        val svgPath = if (active) navItems[index].first else navItems[index].second
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, svgPath)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            icon.setImageBitmap(bmp)
            icon.setColorFilter(tint)
        } catch (_: Exception) {}
    }

    fun setOnTabSelected(listener: (Int) -> Unit) {
        navItems.forEachIndexed { index, _ ->
            view.findViewWithTag<FrameLayout>("nav_btn_$index")
                ?.setOnClickListener { listener(index) }
        }
    }

    private fun buildIcon(svgPath: String, tint: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(activity).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, svgPath)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp)
            iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}