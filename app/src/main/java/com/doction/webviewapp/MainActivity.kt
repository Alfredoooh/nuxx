package com.doction.webviewapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

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

    private var drawerOpen = false
    private var dragStartX = 0f
    private var dragStartOpen = false

    private var currentTab = 0

    private val density get() = resources.displayMetrics.density
    private val drawerWidthPx get() = (DRAWER_WIDTH_DP * density).toInt()
    private val appShiftPx get() = (APP_SHIFT_DP * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        buildLayout()
        setupWebView()
        setupBottomNav()
        setupDrawer()
        setupDragGesture()

        webView.loadUrl("https://www.pornhub.com/shorties")
    }

    // ── Layout programático ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun buildLayout() {
        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.BLACK)
        setContentView(rootLayout)

        // Scrim (overlay escuro quando drawer abre)
        drawerScrim = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }

        // Content wrapper (desloca para a direita quando drawer abre)
        contentWrapper = FrameLayout(this)

        // WebView container (ocupa tudo menos bottom nav)
        webViewContainer = FrameLayout(this)
        webView = WebView(this)
        webViewContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Bottom nav
        bottomNav = buildBottomNav()

        contentWrapper.addView(webViewContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        contentWrapper.addView(bottomNav, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = android.view.Gravity.BOTTOM })

        // AppBar flutuante sobre o WebView
        val appBar = buildAppBar()
        contentWrapper.addView(appBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = android.view.Gravity.TOP })

        // Drawer
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

        // Insets para safe area
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBarHeight = systemBars.bottom
            val statusBarHeight = systemBars.top

            // Padding top no appbar
            contentWrapper.findViewById<View>(R.id.appbar_root)
                ?.setPadding(0, statusBarHeight, 0, 0)

            // Padding bottom no bottomNav
            bottomNav.setPadding(0, 0, 0, navBarHeight)

            insets
        }
    }

    // ── AppBar ─────────────────────────────────────────────────────────────────

    private fun buildAppBar(): View {
        val appBar = FrameLayout(this).apply {
            id = R.id.appbar_root
        }

        // Gradiente topo
        val gradient = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        appBar.addView(gradient, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(80)
        ))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
        }

        // Botão hamburger
        val btnMenu = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener { toggleDrawer() }
        }
        row.addView(btnMenu, LinearLayout.LayoutParams(dp(38), dp(44)))

        // Spacer
        row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // Botão plus
        val btnPlus = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                // TODO: CreatePostPage
            }
        }
        row.addView(btnPlus, LinearLayout.LayoutParams(dp(38), dp(44)))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(44)
        ).also { it.gravity = android.view.Gravity.BOTTOM })

        return appBar
    }

    // ── Bottom Nav ─────────────────────────────────────────────────────────────

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            // Borda topo
            val border = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")),
                android.graphics.drawable.ColorDrawable(Color.parseColor("#0A0A0A"))
            ))
            background = border
        }

        val icons = listOf(
            Pair("Browse",   android.R.drawable.ic_menu_compass),
            Pair("Explore",  android.R.drawable.ic_menu_gallery),
            Pair("Search",   android.R.drawable.ic_menu_search),
            Pair("Library",  android.R.drawable.ic_menu_agenda),
        )

        icons.forEachIndexed { index, (label, icon) ->
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setOnClickListener { switchTab(index) }
            }
            val img = ImageView(this).apply {
                id = View.generateViewId()
                setImageResource(icon)
                setColorFilter(if (index == 0) Color.WHITE else Color.parseColor("#666666"))
                tag = "nav_icon_$index"
            }
            btn.addView(img, LinearLayout.LayoutParams(dp(24), dp(24)))
            nav.addView(btn, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

        return nav
    }

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        currentTab = index

        // Atualiza cor dos ícones
        for (i in 0..3) {
            val btn = bottomNav.getChildAt(i) as? LinearLayout
            val img = btn?.getChildAt(0) as? ImageView
            img?.setColorFilter(if (i == index) Color.WHITE else Color.parseColor("#666666"))
        }

        when (index) {
            0 -> {
                webViewContainer.visibility = View.VISIBLE
                // TODO: mostrar HomeTab (WebView shorties)
            }
            1 -> {
                webViewContainer.visibility = View.GONE
                // TODO: ExplorePage
            }
            2 -> {
                webViewContainer.visibility = View.GONE
                // TODO: SearchPage
            }
            3 -> {
                webViewContainer.visibility = View.GONE
                // TODO: BibliotecaPage
            }
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
            setPadding(0, 0, 0, dp(24))
        }

        // Logo row
        val logoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val logoImg = ImageView(this).apply {
            // TODO: trocar por assets/logo.png quando adicionares os assets
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(PRIMARY_COLOR)
        }
        logoRow.addView(logoImg, LinearLayout.LayoutParams(dp(28), dp(28)))
        logoRow.addView(View(this).apply{}, LinearLayout.LayoutParams(dp(10), 0))
        val logoText = TextView(this).apply {
            text = "nuxxx"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        logoRow.addView(logoText)
        col.addView(logoRow)

        // Divider
        col.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#222222"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Downloads item
        col.addView(buildDrawerItem("Downloads") {
            closeDrawer()
            // TODO: DownloadsPage
        })

        // Definições item
        col.addView(buildDrawerItem("Definições") {
            closeDrawer()
            // TODO: SettingsPage
        })

        // Spacer
        col.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Divider bottom
        col.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#222222"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Footer
        val footer = TextView(this).apply {
            text = "nuxxx"
            setTextColor(Color.parseColor("#555555"))
            textSize = 11f
            setPadding(dp(20), dp(14), dp(20), dp(24))
        }
        col.addView(footer)

        drawer.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        return drawer
    }

    private fun buildDrawerItem(label: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            foreground = android.graphics.drawable.RippleDrawable(ripple, null, null)
        }
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setColorFilter(Color.parseColor("#888888"))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(View(this), LinearLayout.LayoutParams(dp(20), 0))
        val text = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        row.addView(text)
        return row
    }

    private fun setupDrawer() {
        // Posiciona drawer fora do ecrã inicialmente
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
                    // Só inicia drag da borda esquerda (se fechado) ou qualquer ponto (se aberto)
                    dragStartOpen || event.x < dp(24)
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.x - dragStartX
                    if (dragStartOpen) {
                        val tx = (delta).coerceIn(-drawerWidthPx.toFloat(), 0f)
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
                    val velocity = delta // simplificado — sem VelocityTracker
                    if (velocity > dp(80) || (!dragStartOpen && delta > drawerWidthPx * 0.4f)) {
                        openDrawer()
                    } else if (velocity < -dp(80) || (dragStartOpen && -delta > drawerWidthPx * 0.4f)) {
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
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectCSS(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return !url.contains("pornhub.com")
            }
        }

        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY) {
                injectCSS(webView)
            }
        }
    }

    private fun injectCSS(view: WebView?) {
        val css = """
            .actionScribe, .headerLogo, .rightMenuSection,
            .flag.topMenuFlag, .joinNowWrapper,
            .externalLinkButton, .menuContainer {
                display: none !important;
            }
        """.trimIndent()
        view?.evaluateJavascript("""
            (function() {
                var s = document.getElementById('_nuxxx_hide');
                if (s) return;
                s = document.createElement('style');
                s.id = '_nuxxx_hide';
                s.textContent = `$css`;
                document.head.appendChild(s);
            })();
        """.trimIndent(), null)
    }

    // ── Back press ────────────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> closeDrawer()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private fun dp(value: Int) = (value * density).toInt()
}