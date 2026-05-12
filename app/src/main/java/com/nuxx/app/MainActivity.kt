package com.nuxx.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.caverock.androidsvg.SVG
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.services.AppIconService
import com.nuxx.app.services.DownloadService
import com.nuxx.app.services.FaviconService
import com.nuxx.app.services.LockService
import com.nuxx.app.services.NuxxKeepAliveService
import com.nuxx.app.theme.AppTheme
import com.nuxx.app.ui.BottomNavBar
import com.nuxx.app.ui.BrowserPage
import com.nuxx.app.ui.ExploreView
import com.nuxx.app.ui.LibraryView
import com.nuxx.app.ui.SearchResultsPage
import com.nuxx.app.ui.SearchView
import com.nuxx.app.ui.SettingsPage
import com.nuxx.app.ui.ShortiesPage

class MainActivity : AppCompatActivity() {

    // ── Super-app root ──────────────────────────────────────────────────────────
    private lateinit var rootLayout: FrameLayout          // absolute top — nothing goes above this

    // ── Browser layer (always behind everything) ─────────────────────────────────
    private lateinit var browserLayer: FrameLayout
    private lateinit var browserWebView: WebView
    private lateinit var browserNewTab: FrameLayout       // Google-style new-tab page
    private lateinit var browserAddressBar: EditText

    // ── Floating bottom bar (browser-level) ─────────────────────────────────────
    private lateinit var floatingBar: FrameLayout

    // ── Nuxx overlay (sits directly under rootLayout, above browser) ─────────────
    // All Nuxx internals live inside nuxxOverlay
    private lateinit var nuxxOverlay: FrameLayout
    internal lateinit var contentWrapper: FrameLayout     // kept for Nuxx compat
    private lateinit var bottomNavBar: BottomNavBar
    private lateinit var homeContainer: FrameLayout
    private lateinit var exploreContainer: FrameLayout
    private lateinit var searchContainer: FrameLayout
    private lateinit var libraryContainer: FrameLayout
    private lateinit var webView: WebView                 // Nuxx internal webview
    private var shortiesPage: ShortiesPage? = null
    private var nuxxVisible = false

    private lateinit var insetsController: WindowInsetsControllerCompat

    private var currentTab = 0
    internal var statusBarHeight = 0
    internal var navBarHeight = 0
    internal val bottomNavHeightDp = 48
    private val density get() = resources.displayMetrics.density

    internal val currentTabIndex get() = currentTab
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Browser state ────────────────────────────────────────────────────────────
    private var browserMode = BrowserMode.NEW_TAB   // NEW_TAB | BROWSING
    private enum class BrowserMode { NEW_TAB, BROWSING }

    // ── Super-app tabs (bottom bar) ──────────────────────────────────────────────
    private val superTabs = listOf(
        SuperTab("Nuxx",      "phosphor-icons/fill/newspaper-clipping-fill.svg"),
        SuperTab("Browser",   "phosphor-icons/regular/globe.svg"),
        SuperTab("YouTube",   "phosphor-icons/regular/youtube-logo.svg"),
        SuperTab("WhatsApp",  "phosphor-icons/regular/whatsapp-logo.svg"),
        SuperTab("Instagram", "phosphor-icons/regular/instagram-logo.svg"),
        SuperTab("Reddit",    "phosphor-icons/regular/reddit-logo.svg"),
        SuperTab("Spotify",   "phosphor-icons/regular/spotify-logo.svg"),
        SuperTab("Gmail",     "phosphor-icons/regular/envelope-simple.svg"),
    )
    data class SuperTab(val label: String, val iconPath: String)

    // quick-site shortcuts shown on new-tab page
    private val quickSites = listOf(
        QuickSite("YouTube",   "https://m.youtube.com",       "phosphor-icons/regular/youtube-logo.svg"),
        QuickSite("Reddit",    "https://reddit.com",          "phosphor-icons/regular/reddit-logo.svg"),
        QuickSite("WhatsApp",  "https://web.whatsapp.com",    "phosphor-icons/regular/whatsapp-logo.svg"),
        QuickSite("Instagram", "https://instagram.com",       "phosphor-icons/regular/instagram-logo.svg"),
        QuickSite("Spotify",   "https://open.spotify.com",    "phosphor-icons/regular/spotify-logo.svg"),
        QuickSite("Gmail",     "https://mail.google.com",     "phosphor-icons/regular/envelope-simple.svg"),
        QuickSite("Twitter",   "https://twitter.com",         "phosphor-icons/regular/twitter-logo.svg"),
        QuickSite("GitHub",    "https://github.com",          "phosphor-icons/regular/github-logo.svg"),
        QuickSite("LinkedIn",  "https://linkedin.com",        "phosphor-icons/regular/linkedin-logo.svg"),
        QuickSite("TikTok",    "https://tiktok.com",          "phosphor-icons/regular/tiktok-logo.svg"),
    )
    data class QuickSite(val label: String, val url: String, val iconPath: String)

    // ────────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        LockService.init(this)
        FaviconService.init(this)
        DownloadService.init(this)
        AppIconService.init(this)
        NuxxKeepAliveService.start(this)

        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        // Browser/super-app is light mode → dark icons on white
        insetsController.isAppearanceLightStatusBars = true

        buildSuperAppLayout()
        buildNuxxOverlay()
        buildFloatingBar()
        setupBrowserWebView()
        setupNuxxWebView()
        setupBackNavigation()
    }

    override fun onDestroy() {
        super.onDestroy()
        shortiesPage?.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  LAYOUT
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildSuperAppLayout() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(rootLayout)

        // ── Browser layer ────────────────────────────────────────────────────────
        browserLayer = FrameLayout(this)

        // WebView (hidden on new-tab)
        browserWebView = WebView(this).apply { visibility = View.GONE }
        browserLayer.addView(
            browserWebView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // New-tab page
        browserNewTab = buildNewTabPage()
        browserLayer.addView(
            browserNewTab, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        rootLayout.addView(
            browserLayer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            navBarHeight    = bars.bottom
            applyInsetsToNuxx()
            positionFloatingBar()
            insets
        }
    }

    // ── New-tab page ─────────────────────────────────────────────────────────────
    private fun buildNewTabPage(): FrameLayout {
        val page = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val inner  = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(120))
        }

        // ── Address / search bar ─────────────────────────────────────────────────
        val searchCard = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#F2F2F7"))
            }
            elevation = dp(1).toFloat()
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }
        val searchIcon = svgImageView("phosphor-icons/regular/magnifying-glass.svg", 20, Color.parseColor("#8E8E93"))
        browserAddressBar = EditText(this).apply {
            hint           = "Pesquisar ou digitar endereço"
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.parseColor("#1C1C1E"))
            textSize       = 16f
            background     = null
            setSingleLine()
            imeOptions     = EditorInfo.IME_ACTION_GO
            inputType      = android.text.InputType.TYPE_CLASS_TEXT or
                             android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val micIcon = svgImageView("phosphor-icons/regular/microphone.svg", 22, Color.parseColor("#007AFF"))

        searchRow.addView(searchIcon, LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(10) })
        searchRow.addView(browserAddressBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(micIcon, LinearLayout.LayoutParams(dp(22), dp(22)).also { it.marginStart = dp(8) })
        searchCard.addView(searchRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        browserAddressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val raw = browserAddressBar.text.toString().trim()
                navigateBrowserTo(raw)
                true
            } else false
        }

        // ── Quick-sites grid ─────────────────────────────────────────────────────
        val gridLabel = TextView(this).apply {
            text      = "Sites Rápidos"
            textSize  = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, 0)
        }

        val grid = buildQuickSitesGrid()

        // ── Recently closed / suggestions label ──────────────────────────────────
        val recentLabel = TextView(this).apply {
            text      = "Sugestões"
            textSize  = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, 0)
        }
        val suggestions = buildSuggestionsList()

        val searchCardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        searchCardParams.bottomMargin = dp(24)

        val gridLabelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        gridLabelParams.bottomMargin = dp(12)

        val gridParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        gridParams.bottomMargin = dp(24)

        val recentLabelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        recentLabelParams.bottomMargin = dp(12)

        inner.addView(searchCard,   searchCardParams)
        inner.addView(gridLabel,    gridLabelParams)
        inner.addView(grid,         gridParams)
        inner.addView(recentLabel,  recentLabelParams)
        inner.addView(suggestions)

        scroll.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        page.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        return page
    }

    private fun buildQuickSitesGrid(): LinearLayout {
        val cols   = 5
        val outer  = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chunks = quickSites.chunked(cols)
        for (row in chunks) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum   = cols.toFloat()
            }
            for (site in row) {
                val cell = buildQuickSiteCell(site)
                rowLayout.addView(cell, LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            // fill remaining slots
            repeat(cols - row.size) {
                rowLayout.addView(FrameLayout(this), LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            val rp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            rp.bottomMargin = dp(12)
            outer.addView(rowLayout, rp)
        }
        return outer
    }

    private fun buildQuickSiteCell(site: QuickSite): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        val iconBg = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2F2F7"))
            }
        }
        val icon = svgImageView(site.iconPath, 26, Color.parseColor("#1C1C1E"))
        val iconSize = dp(52)
        iconBg.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize).also { it.gravity = Gravity.CENTER })
        val label = TextView(this).apply {
            text      = site.label
            textSize  = 10f
            setTextColor(Color.parseColor("#3C3C43"))
            gravity   = Gravity.CENTER
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val bgParams = LinearLayout.LayoutParams(iconSize, iconSize)
        bgParams.bottomMargin = dp(4)
        cell.addView(iconBg, bgParams)
        cell.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        cell.setOnClickListener { navigateBrowserTo(site.url) }
        return cell
    }

    private fun buildSuggestionsList(): LinearLayout {
        val list  = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sites = listOf(
            "google.com" to "phosphor-icons/regular/globe.svg",
            "wikipedia.org" to "phosphor-icons/regular/book-open.svg",
            "github.com" to "phosphor-icons/regular/github-logo.svg",
            "news.google.com" to "phosphor-icons/regular/newspaper.svg",
        )
        for ((url, icon) in sites) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            val ic = svgImageView(icon, 18, Color.parseColor("#8E8E93"))
            val tv = TextView(this).apply {
                text      = url
                textSize  = 15f
                setTextColor(Color.parseColor("#1C1C1E"))
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(ic, LinearLayout.LayoutParams(dp(18), dp(18)))
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val divider = View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) }
            list.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            list.addView(divider, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1))

            row.setOnClickListener { navigateBrowserTo("https://$url") }
        }
        return list
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  NUXX OVERLAY  (all original logic lives here, untouched)
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildNuxxOverlay() {
        nuxxOverlay = FrameLayout(this).apply { visibility = View.GONE }
        contentWrapper = FrameLayout(this)

        homeContainer    = FrameLayout(this)
        webView          = WebView(this).apply { visibility = View.GONE }
        homeContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        shortiesPage = ShortiesPage(this)
        homeContainer.addView(shortiesPage, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        exploreContainer = FrameLayout(this).apply { visibility = View.GONE }
        exploreContainer.addView(ExploreView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        searchContainer  = FrameLayout(this).apply { visibility = View.GONE }
        searchContainer.addView(SearchView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        libraryContainer = FrameLayout(this).apply { visibility = View.GONE }
        libraryContainer.addView(LibraryView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        bottomNavBar = BottomNavBar(this)
        bottomNavBar.setOnTabSelected { index -> switchNuxxTab(index) }

        contentWrapper.addView(homeContainer,    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(exploreContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(searchContainer,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(libraryContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(bottomNavBar.view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        nuxxOverlay.addView(contentWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Nuxx overlay added to rootLayout (above browser, below floatingBar)
        rootLayout.addView(nuxxOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun applyInsetsToNuxx() {
        val bottomTotal = dp(bottomNavHeightDp) + navBarHeight
        listOf(homeContainer, exploreContainer, searchContainer, libraryContainer).forEach { c ->
            (c.layoutParams as? FrameLayout.LayoutParams)?.apply {
                topMargin    = statusBarHeight
                bottomMargin = bottomTotal
            }?.also { c.layoutParams = it }
        }
        bottomNavBar.view.setPadding(0, 0, 0, navBarHeight)
        bottomNavBar.applyTheme(currentTab)
        setStatusBarDark(currentTab == 0)
    }

    // ── Open / close Nuxx ────────────────────────────────────────────────────────
    private fun openNuxx() {
        if (nuxxVisible) return
        nuxxVisible = true
        nuxxOverlay.visibility  = View.VISIBLE
        floatingBar.visibility  = View.GONE
        nuxxOverlay.alpha       = 0f
        nuxxOverlay.translationY = dp(40).toFloat()
        nuxxOverlay.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        setStatusBarDark(currentTab == 0)
    }

    private fun closeNuxx() {
        if (!nuxxVisible) return
        nuxxVisible = false
        nuxxOverlay.animate()
            .alpha(0f)
            .translationY(dp(40).toFloat())
            .setDuration(260)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxOverlay.visibility = View.GONE
                floatingBar.visibility = View.VISIBLE
            }
            .start()
        insetsController.isAppearanceLightStatusBars = true
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  FLOATING BOTTOM BAR  (super-app level)
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildFloatingBar() {
        floatingBar = FrameLayout(this)

        // Blurred pill background
        val pill = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
                setColor(Color.parseColor("#E8FFFFFF".toInt(0x10)))   // semi-transparent white
                setStroke(dp(1), Color.parseColor("#20000000"))
            }
            elevation = dp(12).toFloat()
        }

        // Use RenderEffect blur on API 31+, else fallback to a solid light card
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pill.setRenderEffect(
                RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
            )
            pill.background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
                setColor(Color.parseColor("#CCFFFFFF"))
                setStroke(dp(1), Color.parseColor("#30000000"))
            }
        } else {
            pill.background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
                setColor(Color.parseColor("#F5F5F5"))
                setStroke(dp(1), Color.parseColor("#20000000"))
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        superTabs.forEachIndexed { idx, tab ->
            val btn = buildFloatingBarButton(tab, idx)
            row.addView(btn, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        pill.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))

        floatingBar.addView(pill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))

        // floatingBar added LAST — on top of everything including nuxxOverlay
        rootLayout.addView(floatingBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))
    }

    private fun buildFloatingBarButton(tab: SuperTab, index: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
        }

        val iconColor = if (index == 0) Color.parseColor("#007AFF") else Color.parseColor("#3C3C43")
        val icon = svgImageView(tab.iconPath, 24, iconColor)

        val label = TextView(this).apply {
            text      = tab.label
            textSize  = 9f
            setTextColor(if (index == 0) Color.parseColor("#007AFF") else Color.parseColor("#8E8E93"))
            gravity   = Gravity.CENTER
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }

        cell.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        cell.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        cell.setOnClickListener { onSuperTabClicked(index) }
        return cell
    }

    private fun onSuperTabClicked(index: Int) {
        when (index) {
            0 -> {
                // Nuxx
                if (nuxxVisible) closeNuxx() else openNuxx()
            }
            1 -> {
                // Browser tab — close nuxx, show new tab / current page
                closeNuxx()
                showBrowserMode()
            }
            2 -> { closeNuxx(); navigateBrowserTo("https://m.youtube.com") }
            3 -> { closeNuxx(); navigateBrowserTo("https://web.whatsapp.com") }
            4 -> { closeNuxx(); navigateBrowserTo("https://instagram.com") }
            5 -> { closeNuxx(); navigateBrowserTo("https://reddit.com") }
            6 -> { closeNuxx(); navigateBrowserTo("https://open.spotify.com") }
            7 -> { closeNuxx(); navigateBrowserTo("https://mail.google.com") }
        }
    }

    private fun positionFloatingBar() {
        (floatingBar.layoutParams as? FrameLayout.LayoutParams)?.apply {
            bottomMargin = navBarHeight + dp(12)
            leftMargin   = dp(16)
            rightMargin  = dp(16)
        }?.also { floatingBar.layoutParams = it }
    }

    // Also update new-tab scroll top padding when insets are known
    private fun applyInsetsToNewTab() {
        (browserNewTab.getChildAt(0) as? ScrollView)?.apply {
            val child = getChildAt(0) as? LinearLayout ?: return
            child.setPadding(dp(20), statusBarHeight + dp(20), dp(20), dp(120))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  BROWSER NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════════

    private fun showBrowserMode() {
        when (browserMode) {
            BrowserMode.NEW_TAB  -> {
                browserNewTab.visibility  = View.VISIBLE
                browserWebView.visibility = View.GONE
            }
            BrowserMode.BROWSING -> {
                browserNewTab.visibility  = View.GONE
                browserWebView.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateBrowserTo(raw: String) {
        closeNuxx()
        hideKeyboard()
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.contains(".") && !raw.contains(" ")                 -> "https://$raw"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(raw)}"
        }
        browserAddressBar.setText(url)
        browserMode = BrowserMode.BROWSING
        browserNewTab.visibility  = View.GONE
        browserWebView.visibility = View.VISIBLE
        browserWebView.loadUrl(url)
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  BACK NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════════

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Nuxx internal overlays
                if (nuxxVisible) {
                    val topOverlay = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 1)
                    if (topOverlay != contentWrapper) {
                        when (topOverlay) {
                            is SearchResultsPage -> { topOverlay.onBackPressed(); return }
                            is BrowserPage       -> { topOverlay.onBackPressed(); return }
                            is SettingsPage      -> { topOverlay.handleBack(); return }
                            else                 -> { removeNuxxOverlay(topOverlay); return }
                        }
                    }
                    if (currentTab == 1) {
                        val ev = exploreContainer.getChildAt(0) as? ExploreView
                        if (ev?.isDrawerOpen() == true) { ev.closeDrawerIfOpen(); return }
                    }
                    if (currentTab == 0 && webView.canGoBack()) { webView.goBack(); return }
                    // Close Nuxx back to browser
                    closeNuxx()
                    return
                }
                // 2. Browser webview back
                if (browserMode == BrowserMode.BROWSING && browserWebView.canGoBack()) {
                    browserWebView.goBack(); return
                }
                // 3. Browser webview → new tab
                if (browserMode == BrowserMode.BROWSING) {
                    browserMode = BrowserMode.NEW_TAB
                    browserWebView.visibility = View.GONE
                    browserNewTab.visibility  = View.VISIBLE
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  STATUS BAR  (original logic — untouched)
    // ══════════════════════════════════════════════════════════════════════════════

    fun setStatusBarDark(dark: Boolean) {
        insetsController.isAppearanceLightStatusBars = !dark
        window.statusBarColor = if (dark) Color.TRANSPARENT else AppTheme.bg
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  NUXX INTERNAL HELPERS  (all original — untouched)
    // ══════════════════════════════════════════════════════════════════════════════

    private fun switchNuxxTab(index: Int) {
        if (index == currentTab) return
        val prev = currentTab; currentTab = index
        (exploreContainer.getChildAt(0) as? ExploreView)?.closeDrawerIfOpen()
        homeContainer.visibility    = if (index == 0) View.VISIBLE else View.GONE
        exploreContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        searchContainer.visibility  = if (index == 2) View.VISIBLE else View.GONE
        libraryContainer.visibility = if (index == 3) View.VISIBLE else View.GONE
        bottomNavBar.updateIcon(prev, false, index == 0)
        bottomNavBar.updateIcon(index, true, index == 0)
        bottomNavBar.applyTheme(index)
        setStatusBarDark(index == 0)
    }

    fun openVideoPlayer(video: FeedVideo, originThumb: View? = null) {
        ExibicaoActivity.start(this, video)
    }

    fun openExibicao(video: FeedVideo) {
        ExibicaoActivity.start(this, video)
    }

    fun closeVideoPlayer() {}
    fun shiftContent(toX: Float, duration: Long) {}

    fun addContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = w
        nuxxOverlay.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val behind = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
        behind?.animate()
            ?.translationX(-w * 0.3f)
            ?.setDuration(320)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()
        view.animate()
            .translationX(0f)
            .setDuration(320)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    fun removeContentOverlay(view: View) {
        removeNuxxOverlay(view)
    }

    private fun removeNuxxOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val behind = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
        behind?.animate()
            ?.translationX(0f)
            ?.setDuration(320)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()
        view.animate()
            .translationX(w)
            .setDuration(320)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxOverlay.removeView(view)
                setStatusBarDark(currentTab == 0)
            }
            .start()
    }

    fun closeSettings() {
        val top = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 1)
        if (top is SettingsPage) removeNuxxOverlay(top)
    }

    fun openSettings() {
        val settingsPage = SettingsPage(this)
        addContentOverlay(settingsPage)
        setStatusBarDark(false)
    }

    fun openLicenses() {}

    fun showSnackbarGlobal(message: String) {
        val tag = "snackbar_global"
        rootLayout.findViewWithTag<View>(tag)?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        val navH = navBarHeight + dp(bottomNavHeightDp)
        val snack = FrameLayout(this).apply {
            this.tag  = tag
            elevation = dp(8).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1C1B1F"))
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        snack.addView(TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#F4EFF4"))
            textSize = 14f
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })

        rootLayout.addView(snack, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity      = Gravity.BOTTOM
            it.bottomMargin = navH + dp(8)
            it.leftMargin   = dp(12)
            it.rightMargin  = dp(12)
        })

        snack.alpha = 0f
        snack.translationY = dp(16).toFloat()
        snack.animate().alpha(1f).translationY(0f)
            .setDuration(200)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        mainHandler.postDelayed({
            if (snack.isAttachedToWindow)
                snack.animate().alpha(0f).translationY(dp(16).toFloat()).setDuration(160)
                    .withEndAction {
                        (snack.parent as? android.view.ViewGroup)?.removeView(snack)
                    }.start()
        }, 3000)
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  WEBVIEWS
    // ══════════════════════════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBrowserWebView() {
        browserWebView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            setSupportZoom(true)
            builtInZoomControls              = true
            displayZoomControls              = false
            cacheMode                        = WebSettings.LOAD_CACHE_ELSE_NETWORK
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        browserWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        browserWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { browserAddressBar.setText(it) }
            }
        }
        browserWebView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) { /* optional: show title */ }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupNuxxWebView() {
        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            setSupportZoom(false)
            cacheMode                        = WebSettings.LOAD_CACHE_ELSE_NETWORK
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectCSS(view); injectAgeConsent(view)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return !url.contains("pornhub.com")
            }
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

    private fun injectAgeConsent(view: WebView?) {
        view?.evaluateJavascript("""
            (function(){
              var sel=['[data-testid="age-confirmation-confirm"]','.age-gate-button',
                '#ageGate .enter','button.enterButton','a.enterButton',
                '.age-verification-confirm','button[class*="confirm"]','a[class*="confirm"]'];
              for(var i=0;i<sel.length;i++){var el=document.querySelector(sel[i]);if(el){el.click();break;}}
            })();
        """.trimIndent(), null)
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════════════════════

    fun svgImageView(assetPath: String, sizeDp: Int, tint: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(assets, assetPath)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp)
            iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    fun dp(value: Int) = (value * density).toInt()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    // Kept for compatibility with any existing callers
    private fun String.toInt(radix: Int) = android.graphics.Color.parseColor(this)
}