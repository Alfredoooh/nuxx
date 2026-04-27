package com.doction.webviewapp

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.services.AppIconService
import com.doction.webviewapp.services.DownloadService
import com.doction.webviewapp.services.FaviconService
import com.doction.webviewapp.services.LockService
import com.doction.webviewapp.services.ThemeService
import com.doction.webviewapp.theme.AppTheme
import com.doction.webviewapp.ui.ExibicaoPage
import com.doction.webviewapp.ui.ExploreView
import com.doction.webviewapp.ui.LibraryView
import com.doction.webviewapp.ui.SearchView
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout:        FrameLayout
    private lateinit var contentWrapper:    FrameLayout
    private lateinit var bottomNav:         LinearLayout
    private lateinit var homeContainer:     FrameLayout
    private lateinit var exploreContainer:  FrameLayout
    private lateinit var searchContainer:   FrameLayout
    private lateinit var libraryContainer:  FrameLayout
    private lateinit var playerContainer:   FrameLayout
    private lateinit var webView:           WebView
    private lateinit var insetsController:  WindowInsetsControllerCompat

    private var currentExibicao: ExibicaoPage? = null
    private var currentTab      = 0
    internal var statusBarHeight = 0
    private var navBarHeight    = 0
    private val bottomNavHeightDp = 48

    private val density get() = resources.displayMetrics.density

    private val navItems = listOf(
        Pair("icons/svg/browse_filled.svg",  "icons/svg/browse_outline.svg"),
        Pair("icons/svg/explore_filled.svg", "icons/svg/explore_outline.svg"),
        Pair("icons/svg/search_filled.svg",  "icons/svg/search_outline.svg"),
        Pair("icons/svg/library_filled.svg", "icons/svg/library_outline.svg"),
    )

    private fun applyStatusBar() {
        val lightIcons = if (currentTab == 0) false else !AppTheme.isDark
        insetsController.isAppearanceLightStatusBars = lightIcons
    }

    private fun navBg()           = if (AppTheme.isDark) Color.parseColor("#0A0A0A") else Color.WHITE
    private fun navIconActive()   = if (AppTheme.isDark) Color.WHITE else Color.parseColor("#0F0F0F")
    private fun navIconInactive() = if (AppTheme.isDark) Color.parseColor("#888888") else Color.parseColor("#606060")

    private val themeListener: () -> Unit = {
        applyNavTheme()
        applyStatusBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        ThemeService.init(this)
        LockService.init(this)
        FaviconService.init(this)
        DownloadService.init(this)
        AppIconService.init(this)
        AppTheme.isDark = ThemeService.instance.isDark

        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        buildLayout()
        setupWebView()
        webView.loadUrl("https://www.pornhub.com/shorties")

        applyStatusBar()
        AppTheme.addThemeListener(themeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppTheme.removeThemeListener(themeListener)
    }

    fun applyNavTheme() {
        val bg = navBg()
        bottomNav.setBackgroundColor(bg)
        window.navigationBarColor = bg
        for (i in navItems.indices) updateNavIcon(i, i == currentTab)
    }

    fun shiftContent(toX: Float, duration: Long) {
        if (duration == 0L) contentWrapper.translationX = toX
        else contentWrapper.animate().translationX(toX).setDuration(duration).start()
    }

    private fun buildLayout() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(rootLayout)

        contentWrapper = FrameLayout(this)

        homeContainer = FrameLayout(this)
        webView = WebView(this)
        homeContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        exploreContainer = FrameLayout(this).apply { visibility = View.GONE }
        exploreContainer.addView(ExploreView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        searchContainer = FrameLayout(this).apply { visibility = View.GONE }
        searchContainer.addView(SearchView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        libraryContainer = FrameLayout(this).apply { visibility = View.GONE }
        libraryContainer.addView(LibraryView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        bottomNav = buildBottomNav()

        contentWrapper.addView(homeContainer,    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(exploreContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(searchContainer,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(libraryContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(bottomNav, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        playerContainer = FrameLayout(this).apply { visibility = View.GONE }

        rootLayout.addView(contentWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(playerContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            navBarHeight    = bars.bottom
            val bottomTotal = dp(bottomNavHeightDp) + navBarHeight

            listOf(homeContainer, exploreContainer, searchContainer, libraryContainer).forEach { c ->
                (c.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin    = statusBarHeight
                    bottomMargin = bottomTotal
                }.also { c.layoutParams = it }
            }

            bottomNav.setPadding(0, 0, 0, navBarHeight)
            window.navigationBarColor = navBg()
            insets
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(navBg())
        }
        navItems.forEachIndexed { index, item ->
            val isActive = index == 0
            val btn = FrameLayout(this).apply {
                tag = "nav_btn_$index"
                setOnClickListener { switchTab(index) }
                isClickable = true
                isFocusable = true
                foreground = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
            }
            val icon = svgImageView(
                if (isActive) item.first else item.second,
                24,
                if (isActive) navIconActive() else navIconInactive()
            ).apply { tag = "nav_icon_$index" }
            btn.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24)).also { it.gravity = Gravity.CENTER })
            nav.addView(btn, LinearLayout.LayoutParams(0, dp(bottomNavHeightDp), 1f))
        }
        return nav
    }

    fun updateNavIcon(index: Int, active: Boolean) {
        val btn  = bottomNav.findViewWithTag<FrameLayout>("nav_btn_$index") ?: return
        val icon = btn.findViewWithTag<android.widget.ImageView>("nav_icon_$index") ?: return
        val tint    = if (active) navIconActive() else navIconInactive()
        val svgPath = if (active) navItems[index].first else navItems[index].second
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(assets, svgPath)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            icon.setImageBitmap(bmp)
            icon.setColorFilter(tint)
        } catch (_: Exception) {}
    }

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        val prev = currentTab
        currentTab = index

        (exploreContainer.getChildAt(0) as? ExploreView)?.closeDrawerIfOpen()

        homeContainer.visibility    = if (index == 0) View.VISIBLE else View.GONE
        exploreContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        searchContainer.visibility  = if (index == 2) View.VISIBLE else View.GONE
        libraryContainer.visibility = if (index == 3) View.VISIBLE else View.GONE

        updateNavIcon(prev, false)
        updateNavIcon(index, true)
        applyNavTheme()
        applyStatusBar()
    }

    fun openVideoPlayer(video: FeedVideo) {
        currentExibicao?.destroy()
        playerContainer.removeAllViews()
        val page = ExibicaoPage(this, video) { next -> openVideoPlayer(next) }
        currentExibicao = page
        playerContainer.addView(page, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.visibility   = View.VISIBLE
        playerContainer.translationY = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate().translationY(0f).setDuration(380)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    fun closeVideoPlayer() {
        val h = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate().translationY(h).setDuration(300)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction {
                playerContainer.visibility = View.GONE
                currentExibicao?.destroy()
                currentExibicao = null
                playerContainer.removeAllViews()
            }.start()
    }

    fun addContentOverlay(view: View) {
        rootLayout.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    fun removeContentOverlay(view: View) { rootLayout.removeView(view) }

    fun closeSettings() { closeVideoPlayer() }

    fun openSettings() {
        val settingsPage = com.doction.webviewapp.ui.SettingsPage(this)
        playerContainer.removeAllViews()
        playerContainer.addView(settingsPage, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.visibility   = View.VISIBLE
        playerContainer.translationY = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate().translationY(0f).setDuration(380)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    fun openLicenses() {
        startActivity(android.content.Intent(this, OssLicensesMenuActivity::class.java))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled   = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort  = true
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            playerContainer.visibility == View.VISIBLE -> closeVideoPlayer()
            webView.canGoBack() -> webView.goBack()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

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
}