package com.doction.webviewapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.caverock.androidsvg.SVG

const val PRIMARY_COLOR = 0xFFFF9000.toInt()
const val DRAWER_WIDTH_DP = 260
const val APP_SHIFT_DP = 110

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var contentWrapper: FrameLayout
    private lateinit var drawerView: FrameLayout
    private lateinit var drawerScrim: View
    private lateinit var bottomNav: LinearLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var appBarRoot: FrameLayout

    private var drawerOpen = false
    private var dragStartX = 0f
    private var dragStartOpen = false
    private var currentTab = 0

    private val density get() = resources.displayMetrics.density
    private val drawerWidthPx get() = (DRAWER_WIDTH_DP * density).toInt()
    private val appShiftPx get() = (APP_SHIFT_DP * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        buildLayout()
        setupWebView()
        setupDrawer()
        setupDragGesture()

        webView.loadUrl("https://www.pornhub.com/shorties")
    }

    // ── SVG helper ─────────────────────────────────────────────────────────────

    private fun svgImageView(assetPath: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(this)
        try {
            val svg = SVG.getFromAsset(assets, assetPath)
            val bitmap = android.graphics.Bitmap.createBitmap(dp(sizeDp), dp(sizeDp),
                android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            svg.renderToCanvas(canvas)
            iv.setImageBitmap(bitmap)
            iv.setColorFilter(tint)
        } catch (e: Exception) {
            // fallback silencioso se asset não existir ainda
        }
        return iv
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private fun buildLayout() {
        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.BLACK)
        setContentView(rootLayout)

        drawerScrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }

        contentWrapper = FrameLayout(this)

        webViewContainer = FrameLayout(this)
        webView = WebView(this)
        webViewContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        bottomNav = buildBottomNav()
        appBarRoot = buildAppBar()

        contentWrapper.addView(webViewContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        contentWrapper.addView(bottomNav, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })
        contentWrapper.addView(appBarRoot, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP })

        drawerView = buildDrawerView()

        rootLayout.addView(contentWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(drawerScrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(drawerView, FrameLayout.LayoutParams(
            drawerWidthPx,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarRoot.setPadding(0, bars.top, 0, 0)
            bottomNav.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    // ── AppBar ─────────────────────────────────────────────────────────────────

    private fun buildAppBar(): FrameLayout {
        val appBar = FrameLayout(this)

        val gradient = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        appBar.addView(gradient, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(80)
        ))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnMenu = svgImageView("icons/svg/hamburger.svg", 22, Color.WHITE).apply {
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener { toggleDrawer() }
        }
        row.addView(btnMenu, LinearLayout.LayoutParams(dp(38), dp(44)))
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val btnPlus = svgImageView("icons/svg/plus.svg", 22, Color.WHITE).apply {
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                // TODO: CreatePostPage
            }
        }
        row.addView(btnPlus, LinearLayout.LayoutParams(dp(38), dp(44)))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(44)
        ).also { it.gravity = Gravity.BOTTOM })

        return appBar
    }

    // ── Bottom Nav ─────────────────────────────────────────────────────────────

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        data class NavItem(val filledSvg: String, val outlineSvg: String)

        val items = listOf(
            NavItem("icons/svg/browse_filled.svg",  "icons/svg/browse_outline.svg"),
            NavItem("icons/svg/explore_filled.svg", "icons/svg/explore_outline.svg"),
            NavItem("icons/svg/search_filled.svg",  "icons/svg/search_outline.svg"),
            NavItem("icons/svg/library_filled.svg", "icons/svg/library_outline.svg"),
        )

        items.forEachIndexed { index, item ->
            val isActive = index == 0
            val tint = if (isActive) Color.WHITE else Color.parseColor("#666666")
            val svgPath = if (isActive) item.filledSvg else item.outlineSvg

            val btn = FrameLayout(this).apply {
                setOnClickListener { switchTab(index) }
                isClickable = true
                isFocusable = true
                foreground = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
                tag = "nav_btn_$index"
            }
            val icon = svgImageView(svgPath, 24, tint).apply {
                tag = "nav_icon_$index"
            }
            btn.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24)).also {
                it.gravity = Gravity.CENTER
            })
            nav.addView(btn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

        return nav
    }

    private val navItems = listOf(
        Pair("icons/svg/browse_filled.svg",  "icons/svg/browse_outline.svg"),
        Pair("icons/svg/explore_filled.svg", "icons/svg/explore_outline.svg"),
        Pair("icons/svg/search_filled.svg",  "icons/svg/search_outline.svg"),
        Pair("icons/svg/library_filled.svg", "icons/svg/library_outline.svg"),
    )

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        val prev = currentTab
        currentTab = index

        // Atualiza ícones
        for (i in 0..3) {
            val btn = bottomNav.findViewWithTag<FrameLayout>("nav_btn_$i") ?: continue
            val icon = btn.findViewWithTag<ImageView>("nav_icon_$i") ?: continue
            val isActive = i == index
            val tint = if (isActive) Color.WHITE else Color.parseColor("#666666")
            val svgPath = if (isActive) navItems[i].first else navItems[i].second
            try {
                val svg = SVG.getFromAsset(assets, svgPath)
                val bmp = android.graphics.Bitmap.createBitmap(dp(24), dp(24),
                    android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                svg.renderToCanvas(canvas)
                icon.setImageBitmap(bmp)
                icon.setColorFilter(tint)
            } catch (_: Exception) {}
        }

        when (index) {
            0 -> { webViewContainer.visibility = View.VISIBLE }
            1 -> { webViewContainer.visibility = View.GONE /* TODO: ExplorePage */ }
            2 -> { webViewContainer.visibility = View.GONE /* TODO: SearchPage */ }
            3 -> { webViewContainer.visibility = View.GONE /* TODO: BibliotecaPage */ }
        }
    }

    // ── Drawer ─────────────────────────────────────────────────────────────────

    private fun buildDrawerView(): FrameLayout {
        val drawer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#111111"))
            elevation = dp(8).toFloat()
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Logo row
        val logoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoImg = ImageView(this).apply {
            try {
                setImageBitmap(android.graphics.BitmapFactory.decodeStream(
                    assets.open("logo.png")))
            } catch (_: Exception) {}
        }
        logoRow.addView(logoImg, LinearLayout.LayoutParams(dp(28), dp(28)))
        val spacer = View(this)
        logoRow.addView(spacer, LinearLayout.LayoutParams(dp(10), 0))
        val logoText = TextView(this).apply {
            text = "nuxxx"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        logoRow.addView(logoText)
        col.addView(logoRow)

        col.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#222222"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        col.addView(buildDrawerItem(
            "icons/svg/drawer_download.svg", "Downloads") {
            closeDrawer()
            // TODO: DownloadsPage
        })
        col.addView(buildDrawerItem(
            "icons/svg/drawer_settings.svg", "Definições") {
            closeDrawer()
            // TODO: SettingsPage
        })

        col.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#222222"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        col.addView(TextView(this).apply {
            text = "nuxxx"
            setTextColor(Color.parseColor("#555555"))
            textSize = 11f
            setPadding(dp(20), dp(14), dp(20), dp(24))
        })

        drawer.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        return drawer
    }

    private fun buildDrawerItem(svgPath: String, label: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
        }
        val icon = svgImageView(svgPath, 20, Color.parseColor("#888888"))
        row.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(View(this), LinearLayout.LayoutParams(dp(20), 0))
        row.addView(TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
        })
        return row
    }

    private fun setupDrawer() {
        drawerView.translationX = -drawerWidthPx.toFloat()
        drawerScrim.visibility = View.GONE
    }

    private fun toggleDrawer() = if (drawerOpen) closeDrawer() else openDrawer()

    private fun openDrawer() {
        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerView.animate().translationX(0f).setDuration(420).start()
        contentWrapper.animate().translationX(appShiftPx.toFloat()).setDuration(420).start()
        drawerScrim.animate().alpha(0.18f).setDuration(420).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        drawerView.animate().translationX(-drawerWidthPx.toFloat()).setDuration(420)
            .withEndAction { drawerScrim.visibility = View.GONE }.start()
        contentWrapper.animate().translationX(0f).setDuration(420).start()
        drawerScrim.animate().alpha(0f).setDuration(420).start()
    }

    // ── Drag gesture ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragGesture() {
        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.x
                    dragStartOpen = drawerOpen
                    dragStartOpen || event.x < dp(24)
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.x - dragStartX
                    if (dragStartOpen) {
                        val tx = delta.coerceIn(-drawerWidthPx.toFloat(), 0f)
                        drawerView.translationX = tx
                        contentWrapper.translationX = appShiftPx + tx
                        drawerScrim.alpha = 0.18f * (1f + tx / drawerWidthPx)
                    } else {
                        val tx = delta.coerceIn(0f, drawerWidthPx.toFloat())
                        drawerView.translationX = -drawerWidthPx + tx
                        contentWrapper.translationX = tx * (appShiftPx.toFloat() / drawerWidthPx)
                        drawerScrim.visibility = View.VISIBLE
                        drawerScrim.alpha = 0.18f * (tx / drawerWidthPx)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val delta = event.x - dragStartX
                    if (delta > dp(80) || (!dragStartOpen && delta > drawerWidthPx * 0.4f)) {
                        openDrawer()
                    } else if (delta < -dp(80) || (dragStartOpen && -delta > drawerWidthPx * 0.4f)) {
                        closeDrawer()
                    } else {
                        if (dragStartOpen) openDrawer() else closeDrawer()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── WebView ────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = injectCSS(view)
            override fun shouldOverrideUrlLoading(view: WebView?,
                request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return !url.contains("pornhub.com")
            }
        }
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY) injectCSS(webView)
        }
    }

    private fun injectCSS(view: WebView?) {
        val css = ".actionScribe,.headerLogo,.rightMenuSection,.flag.topMenuFlag," +
                ".joinNowWrapper,.externalLinkButton,.menuContainer{display:none!important}"
        view?.evaluateJavascript("""
            (function(){var s=document.getElementById('_nx');if(s)return;
            s=document.createElement('style');s.id='_nx';
            s.textContent='$css';document.head.appendChild(s);})();
        """.trimIndent(), null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> closeDrawer()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    private fun dp(value: Int) = (value * density).toInt()
}