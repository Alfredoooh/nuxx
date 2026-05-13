package com.nuxx.app.ui

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
import com.nuxx.app.MainActivity
import com.nuxx.app.theme.AppTheme

class BottomNavBar(private val activity: MainActivity) {

    val view: LinearLayout

    private val navItems = listOf(
        Pair("icons/svg/browse_filled.svg",  "icons/svg/browse_outline.svg"),
        Pair("icons/svg/explore_filled.svg", "icons/svg/explore_outline.svg"),
        Pair("icons/svg/search_filled.svg",  "icons/svg/search_outline.svg"),
    )

    private val navHeightDp = 48

    // Rosa ativo — igual ao brand do nuxx
    private val activeColor  = Color.parseColor("#E01462")
    private val inactiveHome = Color.parseColor("#888888")
    private val inactiveLight = Color.parseColor("#AAAAAA")

    private fun bgFor(tabIndex: Int) =
        if (tabIndex == 0) Color.parseColor("#0A0A0A") else Color.WHITE

    private fun activeIconColor(isHomeTab: Boolean) = activeColor

    private fun inactiveIconColor(isHomeTab: Boolean) =
        if (isHomeTab) inactiveHome else inactiveLight

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
                    ColorStateList.valueOf(Color.parseColor("#33E01462")), null, null
                )
            }
            val icon = buildIcon(
                if (isActive) item.first else item.second,
                if (isActive) activeIconColor(true) else inactiveIconColor(true)
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

        // ripple adapta ao fundo
        val rippleColor = if (isHomeTab)
            Color.parseColor("#33E01462") else Color.parseColor("#22E01462")

        for (i in navItems.indices) {
            val btn = view.findViewWithTag<FrameLayout>("nav_btn_$i") ?: continue
            btn.foreground = RippleDrawable(ColorStateList.valueOf(rippleColor), null, null)
            updateIcon(i, i == currentTab, isHomeTab)
        }
    }

    fun updateIcon(index: Int, active: Boolean, isHomeTab: Boolean) {
        if (index >= navItems.size) return
        val btn  = view.findViewWithTag<FrameLayout>("nav_btn_$index") ?: return
        val icon = btn.findViewWithTag<android.widget.ImageView>("nav_icon_$index") ?: return
        val tint    = if (active) activeIconColor(isHomeTab) else inactiveIconColor(isHomeTab)
        val svgPath = if (active) navItems[index].first else navItems[index].second

        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, svgPath)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))

            // animação suave de escala + fade
            icon.animate().cancel()
            icon.animate()
                .scaleX(0.75f).scaleY(0.75f).alpha(0f)
                .setDuration(80)
                .withEndAction {
                    icon.setImageBitmap(bmp)
                    icon.setColorFilter(tint)
                    icon.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(160)
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }.start()

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