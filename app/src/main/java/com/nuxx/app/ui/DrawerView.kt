// DrawerView.kt
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
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity
import com.nuxx.app.theme.AppTheme

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class DrawerView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private lateinit var scrim: View
    private lateinit var panel: FrameLayout
    private var isOpen = false

    private val panelWidth get() = (resources.displayMetrics.widthPixels * 0.82f).toInt()
    private val animDuration = 280L

    private var touchStartX   = 0f
    private var touchCurrentX = 0f
    private var isSwiping     = false

    init {
        visibility = View.GONE
        buildScrim()
        buildPanel()
    }

    private fun buildScrim() {
        scrim = View(context).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener { close() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        panel = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.drawerBg)
            elevation    = dp(20).toFloat()
            translationX = -panelWidth.toFloat()
        }

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // ── Status bar spacer ─────────────────────────────────────────────────
        val statusBarSpacer = View(context).apply { setBackgroundColor(AppTheme.drawerBg) }
        col.addView(statusBarSpacer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.statusBarHeight.coerceAtLeast(dp(28))
        ))

        panel.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val h = activity.statusBarHeight.coerceAtLeast(dp(28))
                (statusBarSpacer.layoutParams as LinearLayout.LayoutParams).height = h
                statusBarSpacer.requestLayout()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        // ── Logo row ──────────────────────────────────────────────────────────
        val logoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            gravity     = Gravity.CENTER_VERTICAL
        }
        try {
            val bmp  = BitmapFactory.decodeStream(context.assets.open("logo.png"))
            val logo = ImageView(context).apply {
                setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER
            }
            logoRow.addView(logo, LinearLayout.LayoutParams(dp(32), dp(32)))
            logoRow.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))
        } catch (_: Exception) {}
        logoRow.addView(TextView(context).apply {
            text          = "nuxx"
            setTextColor(AppTheme.text)
            textSize      = 20f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
        })
        col.addView(logoRow)
        col.addView(makeDivider())

        // ── Vídeos guardados ──────────────────────────────────────────────────
        col.addView(drawerItem(
            "icons/svg/phosphor-icons/regular/bookmark.svg",
            "Vídeos guardados"
        ) {
            close()
            handler.postDelayed({
                activity.addContentOverlay(SavedVideosPage(context))
            }, animDuration + 60)
        })

        // ── Histórico ─────────────────────────────────────────────────────────
        col.addView(drawerItem(
            "icons/svg/phosphor-icons/regular/clock-counter-clockwise.svg",
            "Histórico"
        ) {
            close()
            handler.postDelayed({
                activity.addContentOverlay(HistoryPage(context))
            }, animDuration + 60)
        })

        // ── Definições ────────────────────────────────────────────────────────
        col.addView(drawerItem(
            "icons/svg/phosphor-icons/regular/gear.svg",
            "Definições"
        ) {
            close()
            handler.postDelayed({ activity.openSettings() }, animDuration + 60)
        })

        // ── Spacer flex ───────────────────────────────────────────────────────
        col.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Nav bar bottom padding
        col.addView(View(context).apply { setBackgroundColor(AppTheme.drawerBg) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.navBarHeight.coerceAtLeast(0)
            ))

        panel.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ── Swipe-to-close ────────────────────────────────────────────────────
        panel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX; touchCurrentX = event.rawX; isSwiping = false; false
                }
                MotionEvent.ACTION_MOVE -> {
                    touchCurrentX = event.rawX
                    val dx = touchStartX - touchCurrentX
                    if (!isSwiping && dx > dp(8)) isSwiping = true
                    if (isSwiping && dx > 0) {
                        panel.translationX = -dx.coerceAtLeast(0f)
                        val fraction = (dx / panelWidth).coerceIn(0f, 1f)
                        scrim.alpha = 0.52f * (1f - fraction)
                        activity.contentWrapper.translationX = (panelWidth - dx).coerceAtLeast(0f)
                    }
                    isSwiping
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = touchStartX - touchCurrentX
                    if (isSwiping) { if (dx > panelWidth * 0.3f) close() else snapOpen() }
                    isSwiping = false; false
                }
                else -> false
            }
        }

        addView(panel, LayoutParams(panelWidth, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.START
        })
    }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(AppTheme.drawerDivider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun drawerItem(svgPath: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            gravity     = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            foreground  = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#11000000")),
                null, ColorDrawable(Color.WHITE)
            )
            setOnClickListener { onClick() }
            addView(svgView(svgPath, 22, AppTheme.iconSub),
                LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(View(context), LinearLayout.LayoutParams(dp(18), 0))
            addView(TextView(context).apply {
                text = label; setTextColor(AppTheme.text); textSize = 15f
            })
        }
    }

    private fun snapOpen() {
        panel.animate().translationX(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator(1.5f)).start()
        scrim.animate().alpha(0.52f).setDuration(200).start()
        activity.contentWrapper.animate()
            .translationX(panelWidth.toFloat())
            .setDuration(200).setInterpolator(DecelerateInterpolator(1.5f)).start()
    }

    fun open() {
        if (isOpen) return
        isOpen     = true
        visibility = View.VISIBLE
        panel.translationX = -panelWidth.toFloat()

        val col = panel.getChildAt(0) as? LinearLayout
        (col?.getChildAt(0))?.let {
            val h = activity.statusBarHeight.coerceAtLeast(dp(28))
            (it.layoutParams as? LinearLayout.LayoutParams)?.height = h
            it.requestLayout()
        }

        // Animação suave e sincronizada
        activity.contentWrapper.animate()
            .translationX(panelWidth.toFloat())
            .setDuration(animDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        panel.animate()
            .translationX(0f)
            .setDuration(animDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        scrim.alpha = 0f
        scrim.animate().alpha(0.45f).setDuration(animDuration).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false

        activity.contentWrapper.animate()
            .translationX(0f)
            .setDuration(animDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        panel.animate()
            .translationX(-panelWidth.toFloat())
            .setDuration(animDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { visibility = View.GONE }
            .start()

        scrim.animate().alpha(0f).setDuration(animDuration).start()
    }

    fun toggle() = if (isOpen) close() else open()
    fun isDrawerOpen() = isOpen

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}