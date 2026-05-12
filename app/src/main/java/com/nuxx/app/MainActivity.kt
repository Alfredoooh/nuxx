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
import android.view.Gravity
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
import com.nuxx.app.ui.VideoPreviewModal

class MainActivity : AppCompatActivity() {

    // ── Super-app root ───────────────────────────────────────────────────────────
    private lateinit var rootLayout: FrameLayout

    // ── Browser layer ────────────────────────────────────────────────────────────
    private lateinit var browserLayer:      FrameLayout
    private lateinit var browserWebView:    WebView
    private lateinit var browserNewTab:     FrameLayout
    private lateinit var browserAddressBar: EditText

    // ── Floating bottom bar ──────────────────────────────────────────────────────
    private lateinit var floatingBar: FrameLayout

    // ── Nuxx overlay ─────────────────────────────────────────────────────────────
    private lateinit var nuxxOverlay:      FrameLayout
    internal lateinit var contentWrapper:  FrameLayout
    private lateinit var bottomNavBar:     BottomNavBar
    private lateinit var homeContainer:    FrameLayout
    private lateinit var exploreContainer: FrameLayout
    private lateinit var searchContainer:  FrameLayout
    private lateinit var libraryContainer: FrameLayout
    private lateinit var webView:          WebView
    private var shortiesPage: ShortiesPage? = null
    private var nuxxVisible = false

    private lateinit var insetsController: WindowInsetsControllerCompat

    private var currentTab         = 0
    internal var statusBarHeight   = 0
    internal var navBarHeight      = 0
    internal val bottomNavHeightDp = 48
    private val density get()      = resources.displayMetrics.density
    internal val currentTabIndex get() = currentTab
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Browser state ─────────────────────────────────────────────────────────────
    private enum class BrowserMode { NEW_TAB, BROWSING }
    private var browserMode = BrowserMode.NEW_TAB

    // ── Caminhos Phosphor ─────────────────────────────────────────────────────────
    private fun ph(name: String, fill: Boolean = false): String {
        val variant = if (fill) "fill" else "regular"
        return "icons/svg/phosphor-icons/$variant/$name.svg"
    }

    // ── Super-app tabs ────────────────────────────────────────────────────────────
    data class SuperTab(val label: String, val icon: String, val iconActive: String = "")
    private val superTabs = listOf(
        SuperTab("Nuxx",      ph("newspaper-clipping", fill = true),  ph("newspaper-clipping", fill = true)),
        SuperTab("Browser",   ph("globe"),                            ph("globe",        fill = true)),
        SuperTab("YouTube",   ph("youtube-logo"),                     ph("youtube-logo", fill = true)),
        SuperTab("WhatsApp",  ph("whatsapp-logo"),                    ph("whatsapp-logo",fill = true)),
        SuperTab("Instagram", ph("instagram-logo"),                   ph("instagram-logo",fill = true)),
        SuperTab("Reddit",    ph("reddit-logo"),                      ph("reddit-logo",  fill = true)),
        SuperTab("Spotify",   ph("spotify-logo"),                     ph("spotify-logo", fill = true)),
        SuperTab("Gmail",     ph("envelope-simple"),                  ph("envelope-simple-fill", fill = true)),
    )

    // ── Quick sites ───────────────────────────────────────────────────────────────
    data class QuickSite(val label: String, val url: String, val icon: String)
    private val quickSites = listOf(
        QuickSite("YouTube",   "https://m.youtube.com",       ph("youtube-logo")),
        QuickSite("Reddit",    "https://reddit.com",          ph("reddit-logo")),
        QuickSite("WhatsApp",  "https://web.whatsapp.com",    ph("whatsapp-logo")),
        QuickSite("Instagram", "https://instagram.com",       ph("instagram-logo")),
        QuickSite("Spotify",   "https://open.spotify.com",    ph("spotify-logo")),
        QuickSite("Gmail",     "https://mail.google.com",     ph("envelope-simple")),
        QuickSite("Twitter",   "https://twitter.com",         ph("twitter-logo")),
        QuickSite("GitHub",    "https://github.com",          ph("github-logo")),
        QuickSite("LinkedIn",  "https://linkedin.com",        ph("linkedin-logo")),
        QuickSite("TikTok",    "https://tiktok.com",          ph("tiktok-logo")),
    )

    // ─────────────────────────────────────────────────────────────────────────────
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
        insetsController.isAppearanceLightStatusBars = true

        buildBrowserLayer()
        buildNuxxOverlay()
        buildFloatingBar()
        setupBrowserWebView()
        setupNuxxWebView()
        setupBackNavigation()

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            navBarHeight    = bars.bottom
            applyInsetsToNuxx()
            applyInsetsToBrowser()
            positionFloatingBar()
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shortiesPage?.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  BROWSER LAYER
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildBrowserLayer() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(rootLayout)

        browserLayer   = FrameLayout(this)
        browserWebView = WebView(this).apply { visibility = View.GONE }
        browserLayer.addView(browserWebView, matchParent())

        browserNewTab = buildNewTabPage()
        browserLayer.addView(browserNewTab, matchParent())

        rootLayout.addView(browserLayer, matchParent())
    }

    private fun applyInsetsToBrowser() {
        val scroll = browserNewTab.getChildAt(0) as? ScrollView ?: return
        val inner  = scroll.getChildAt(0) as? LinearLayout ?: return
        inner.setPadding(dp(20), statusBarHeight + dp(16), dp(20), dp(140))
    }

    // ── New-tab page ──────────────────────────────────────────────────────────────
    private fun buildNewTabPage(): FrameLayout {
        val page   = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(60), dp(20), dp(140))
        }

        // Address bar
        val searchCard = FrameLayout(this).apply {
            background = roundRect(dp(28).toFloat(), Color.parseColor("#F2F2F7"))
            elevation  = dp(2).toFloat()
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(12), dp(13))
        }
        val searchIcon = svgImageView(ph("magnifying-glass"), 20, Color.parseColor("#8E8E93"))
        browserAddressBar = EditText(this).apply {
            hint           = "Pesquisar ou digitar endereço"
            setHintTextColor(Color.parseColor("#AEAEB2"))
            setTextColor(Color.parseColor("#1C1C1E"))
            textSize       = 16f
            background     = null
            setSingleLine()
            imeOptions     = EditorInfo.IME_ACTION_GO
            inputType      = android.text.InputType.TYPE_CLASS_TEXT or
                             android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val micIcon = svgImageView(ph("microphone"), 22, Color.parseColor("#007AFF"))

        browserAddressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateBrowserTo(browserAddressBar.text.toString().trim()); true
            } else false
        }

        searchRow.addView(searchIcon, LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(10) })
        searchRow.addView(browserAddressBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(micIcon, LinearLayout.LayoutParams(dp(22), dp(22)).also { it.marginStart = dp(8) })
        searchCard.addView(searchRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val gridLabel = sectionLabel("Sites Rápidos")
        val grid      = buildQuickSitesGrid()
        val sugLabel  = sectionLabel("Sugestões")
        val sugList   = buildSuggestionsList()

        inner.addView(searchCard,  lp(0, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(28) })
        inner.addView(gridLabel,   lp(0, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(14) })
        inner.addView(grid,        lp(0, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(28) })
        inner.addView(sugLabel,    lp(0, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) })
        inner.addView(sugList)

        scroll.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, matchParent())
        return page
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize  = 12f
        setTextColor(Color.parseColor("#8E8E93"))
        typeface  = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.05f
    }

    private fun buildQuickSitesGrid(): LinearLayout {
        val cols  = 5
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        quickSites.chunked(cols).forEach { row ->
            val rowL = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum   = cols.toFloat()
            }
            row.forEach { site ->
                rowL.addView(buildQuickCell(site),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            repeat(cols - row.size) {
                rowL.addView(FrameLayout(this),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            outer.addView(rowL, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(14) })
        }
        return outer
    }

    private fun buildQuickCell(site: QuickSite): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        val iconBg = FrameLayout(this).apply {
            background = roundRect(dp(14).toFloat(), Color.parseColor("#F2F2F7"))
        }
        val sz   = dp(52)
        val icon = svgImageView(site.icon, 26, Color.parseColor("#1C1C1E"))
        iconBg.addView(icon, FrameLayout.LayoutParams(sz, sz).also { it.gravity = Gravity.CENTER })

        val label = TextView(this).apply {
            text      = site.label
            textSize  = 10f
            setTextColor(Color.parseColor("#3C3C43"))
            gravity   = Gravity.CENTER
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        cell.addView(iconBg, LinearLayout.LayoutParams(sz, sz).also { it.bottomMargin = dp(5) })
        cell.addView(label, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        cell.setOnClickListener { navigateBrowserTo(site.url) }
        return cell
    }

    private fun buildSuggestionsList(): LinearLayout {
        val list  = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val items = listOf(
            "google.com"      to ph("google-logo"),
            "wikipedia.org"   to ph("book-open"),
            "github.com"      to ph("github-logo"),
            "news.google.com" to ph("newspaper"),
        )
        items.forEachIndexed { i, (url, icon) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(13), dp(4), dp(13))
                isClickable = true; isFocusable = true
            }
            val ic = svgImageView(icon, 18, Color.parseColor("#8E8E93"))
            val tv = TextView(this).apply {
                text = url; textSize = 15f
                setTextColor(Color.parseColor("#1C1C1E"))
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(ic, LinearLayout.LayoutParams(dp(18), dp(18)))
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { navigateBrowserTo("https://$url") }
            list.addView(row, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            if (i < items.lastIndex) {
                list.addView(
                    View(this).apply { setBackgroundColor(Color.parseColor("#E5E5EA")) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                        .also { it.leftMargin = dp(34) }
                )
            }
        }
        return list
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  NUXX OVERLAY
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildNuxxOverlay() {
        nuxxOverlay    = FrameLayout(this).apply { visibility = View.GONE }
        contentWrapper = FrameLayout(this)

        homeContainer = FrameLayout(this)
        webView       = WebView(this).apply { visibility = View.GONE }
        homeContainer.addView(webView, matchParent())
        shortiesPage = ShortiesPage(this)
        homeContainer.addView(shortiesPage, matchParent())

        exploreContainer = FrameLayout(this).apply { visibility = View.GONE }
        exploreContainer.addView(ExploreView(this), matchParent())

        searchContainer = FrameLayout(this).apply { visibility = View.GONE }
        searchContainer.addView(SearchView(this), matchParent())

        libraryContainer = FrameLayout(this).apply { visibility = View.GONE }
        libraryContainer.addView(LibraryView(this), matchParent())

        bottomNavBar = BottomNavBar(this)
        bottomNavBar.setOnTabSelected { index -> switchNuxxTab(index) }

        contentWrapper.addView(homeContainer,    matchParent())
        contentWrapper.addView(exploreContainer, matchParent())
        contentWrapper.addView(searchContainer,  matchParent())
        contentWrapper.addView(libraryContainer, matchParent())
        contentWrapper.addView(
            bottomNavBar.view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                .also { it.gravity = Gravity.BOTTOM }
        )

        nuxxOverlay.addView(contentWrapper, matchParent())
        rootLayout.addView(nuxxOverlay, matchParent())
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

    // ── Abrir / fechar Nuxx ───────────────────────────────────────────────────────
    private fun openNuxx() {
        if (nuxxVisible) return
        nuxxVisible              = true
        nuxxOverlay.visibility   = View.VISIBLE
        floatingBar.visibility   = View.GONE
        nuxxOverlay.alpha        = 0f
        nuxxOverlay.translationY = dp(32).toFloat()
        nuxxOverlay.animate()
            .alpha(1f).translationY(0f)
            .setDuration(280).setInterpolator(FastOutSlowInInterpolator())
            .start()
        setStatusBarDark(currentTab == 0)
    }

    internal fun closeNuxx() {
        if (!nuxxVisible) return
        nuxxVisible = false
        nuxxOverlay.animate()
            .alpha(0f).translationY(dp(32).toFloat())
            .setDuration(240).setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxOverlay.visibility = View.GONE
                floatingBar.visibility = View.VISIBLE
            }.start()
        insetsController.isAppearanceLightStatusBars = true
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  FLOATING BOTTOM BAR
    // ══════════════════════════════════════════════════════════════════════════════

    private fun buildFloatingBar() {
        floatingBar = FrameLayout(this)

        val pill = FrameLayout(this).apply {
            elevation  = dp(16).toFloat()
            background = roundRect(dp(40).toFloat(), Color.parseColor("#E6FFFFFF")).also {
                (it as? android.graphics.drawable.GradientDrawable)
                    ?.setStroke(dp(1), Color.parseColor("#28000000"))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pill.setRenderEffect(
                RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)
            )
            pill.background = roundRect(dp(40).toFloat(), Color.parseColor("#CCFFFFFF")).also {
                (it as? android.graphics.drawable.GradientDrawable)
                    ?.setStroke(dp(1), Color.parseColor("#28000000"))
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        superTabs.forEachIndexed { idx, tab ->
            row.addView(
                buildBarButton(tab, idx),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        pill.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        floatingBar.addView(pill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        // Adicionado POR ÚLTIMO — acima de tudo
        rootLayout.addView(floatingBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
    }

    private fun buildBarButton(tab: SuperTab, index: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
        }
        val iconColor = Color.parseColor("#3C3C43")
        val icon  = svgImageView(tab.icon, 24, iconColor)
        val label = TextView(this).apply {
            text      = tab.label
            textSize  = 9f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity   = Gravity.CENTER
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        cell.addView(icon,  LinearLayout.LayoutParams(dp(24), dp(24)))
        cell.addView(label, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        cell.setOnClickListener { onSuperTabClicked(index) }
        return cell
    }

    private fun onSuperTabClicked(index: Int) {
        when (index) {
            0 -> if (nuxxVisible) closeNuxx() else openNuxx()
            1 -> { closeNuxx(); showBrowserUI() }
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
            bottomMargin = navBarHeight + dp(14)
            leftMargin   = dp(16)
            rightMargin  = dp(16)
        }?.also { floatingBar.layoutParams = it }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  BROWSER NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════════

    private fun showBrowserUI() {
        browserNewTab.visibility  = if (browserMode == BrowserMode.NEW_TAB) View.VISIBLE else View.GONE
        browserWebView.visibility = if (browserMode == BrowserMode.BROWSING) View.VISIBLE else View.GONE
    }

    fun navigateBrowserTo(raw: String) {
        closeNuxx()
        hideKeyboard()
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.contains(".") && !raw.contains(" ")                 -> "https://$raw"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(raw)}"
        }
        browserAddressBar.setText(url)
        browserMode               = BrowserMode.BROWSING
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
                if (nuxxVisible) {
                    val top = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 1)
                    if (top != contentWrapper) {
                        when (top) {
                            is SearchResultsPage -> { top.onBackPressed(); return }
                            is BrowserPage       -> { top.onBackPressed(); return }
                            is SettingsPage      -> { top.handleBack(); return }
                            else                 -> { removeNuxxOverlay(top); return }
                        }
                    }
                    if (currentTab == 1) {
                        val ev = exploreContainer.getChildAt(0) as? ExploreView
                        if (ev?.isDrawerOpen() == true) { ev.closeDrawerIfOpen(); return }
                    }
                    if (currentTab == 0 && webView.canGoBack()) { webView.goBack(); return }
                    closeNuxx(); return
                }
                if (browserMode == BrowserMode.BROWSING && browserWebView.canGoBack()) {
                    browserWebView.goBack(); return
                }
                if (browserMode == BrowserMode.BROWSING) {
                    browserMode               = BrowserMode.NEW_TAB
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
    //  STATUS BAR  ── lógica original intacta
    // ══════════════════════════════════════════════════════════════════════════════

    fun setStatusBarDark(dark: Boolean) {
        insetsController.isAppearanceLightStatusBars = !dark
        window.statusBarColor = if (dark) Color.TRANSPARENT else AppTheme.bg
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  NUXX INTERNALS  ── todos originais, intactos
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

    // ── Vídeo ─────────────────────────────────────────────────────────────────────
    fun openVideoPlayer(video: FeedVideo, originThumb: View? = null) {
        VideoPreviewModal.show(this, video)
    }

    fun openExibicao(video: FeedVideo) {
        VideoPreviewModal.show(this, video)
    }

    fun closeVideoPlayer() {}
    fun shiftContent(toX: Float, duration: Long) {}

    // ── Overlays Nuxx ─────────────────────────────────────────────────────────────
    fun addContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = w
        nuxxOverlay.addView(view, matchParent())
        nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
            ?.animate()?.translationX(-w * 0.3f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(0f)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator()).start()
    }

    fun removeContentOverlay(view: View) = removeNuxxOverlay(view)

    private fun removeNuxxOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
            ?.animate()?.translationX(0f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(w)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxOverlay.removeView(view)
                setStatusBarDark(currentTab == 0)
            }.start()
    }

    fun closeSettings() {
        val top = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 1)
        if (top is SettingsPage) removeNuxxOverlay(top)
    }

    fun openSettings() {
        addContentOverlay(SettingsPage(this))
        setStatusBarDark(false)
    }

    fun openLicenses() {}

    fun openBrowserOverlay(url: String = "") {
        if (url.isNotEmpty()) navigateBrowserTo(url) else closeNuxx()
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────────
    fun showSnackbarGlobal(message: String) {
        val tag = "snackbar_global"
        rootLayout.findViewWithTag<View>(tag)?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        val navH  = navBarHeight + dp(bottomNavHeightDp)
        val snack = FrameLayout(this).apply {
            this.tag  = tag
            elevation = dp(8).toFloat()
            background = roundRect(dp(12).toFloat(), Color.parseColor("#1C1B1F"))
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
            it.leftMargin   = dp(12); it.rightMargin = dp(12)
        })
        snack.alpha = 0f; snack.translationY = dp(16).toFloat()
        snack.animate().alpha(1f).translationY(0f)
            .setDuration(200).setInterpolator(FastOutSlowInInterpolator()).start()
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
        browserWebView.webChromeClient = WebChromeClient()
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
    //  UTILS
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
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
    )

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun roundRect(radius: Float, color: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
}