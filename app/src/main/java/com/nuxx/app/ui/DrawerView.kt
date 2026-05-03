package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity
import com.nuxx.app.theme.AppTheme

@SuppressLint("ViewConstructor")
class DrawerView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private lateinit var scrim: View
    private lateinit var panel: FrameLayout
    private var isOpen = false

    private val panelWidth get() = (resources.displayMetrics.widthPixels * 0.72f).toInt()
    private val animDuration = 260L

    init {
        visibility = View.GONE
        buildScrim()
        buildPanel()
    }

    private fun buildScrim() {
        scrim = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            setOnClickListener { close() }
        }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun buildPanel() {
        panel = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.drawerBg)
            elevation = dp(20).toFloat()
            translationX = -panelWidth.toFloat()
        }

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Status bar space
        col.addView(
            View(context),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight)
        )

        // Logo row
        val logoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            gravity = Gravity.CENTER_VERTICAL
        }
        try {
            val bmp = BitmapFactory.decodeStream(context.assets.open("logo.png"))
            val logo = ImageView(context).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            logoRow.addView(logo, LinearLayout.LayoutParams(dp(28), dp(28)))
            logoRow.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        } catch (_: Exception) {}

        logoRow.addView(TextView(context).apply {
            text = "nuxxx"
            setTextColor(AppTheme.text)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
        })
        col.addView(logoRow)

        // Divider
        col.addView(makeDivider())

        // Items
        col.addView(drawerItem("icons/svg/drawer_download.svg", "Downloads") {
            close()
        })
        col.addView(drawerItem("icons/svg/drawer_settings.svg", "Definições") {
            close()
            handler.postDelayed({ activity.openSettings() }, animDuration + 60)
        })

        // Push footer to bottom
        col.addView(
            View(context),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        col.addView(makeDivider())
        col.addView(TextView(context).apply {
            text = "nuxxx"
            setTextColor(AppTheme.textTertiary)
            textSize = 11f
            setPadding(dp(20), dp(14), dp(20), dp(28))
        })

        panel.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        addView(panel, LayoutParams(panelWidth, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.START
        })
    }

    private fun makeDivider(): View {
        return View(context).apply {
            setBackgroundColor(AppTheme.drawerDivider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }
    }

    private fun drawerItem(svgPath: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#22FFFFFF")),
                null,
                ColorDrawable(Color.WHITE)
            )
            setOnClickListener { onClick() }

            val icon = svgView(svgPath, 20, AppTheme.iconSub)
            addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(View(context), LinearLayout.LayoutParams(dp(18), 0))
            addView(TextView(context).apply {
                text = label
                setTextColor(AppTheme.text)
                textSize = 14f
            })
        }
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        visibility = View.VISIBLE
        panel.translationX = -panelWidth.toFloat()
        panel.animate()
            .translationX(0f)
            .setDuration(animDuration)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
        scrim.alpha = 0f
        scrim.animate()
            .alpha(0.52f)
            .setDuration(animDuration)
            .start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        panel.animate()
            .translationX(-panelWidth.toFloat())
            .setDuration(animDuration)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction { visibility = View.GONE }
            .start()
        scrim.animate()
            .alpha(0f)
            .setDuration(animDuration)
            .start()
    }

    fun toggle() = if (isOpen) close() else open()
    fun isDrawerOpen() = isOpen

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp)
            iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}