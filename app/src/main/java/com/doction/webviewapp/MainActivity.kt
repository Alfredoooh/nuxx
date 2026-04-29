package com.doction.webviewapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.services.AppIconService
import com.doction.webviewapp.services.DownloadService
import com.doction.webviewapp.services.FaviconService
import com.doction.webviewapp.services.LockService
import com.doction.webviewapp.theme.AppTheme
import com.doction.webviewapp.ui.BottomNavBar
import com.doction.webviewapp.ui.BrowserPage
import com.doction.webviewapp.ui.ExibicaoPage
import com.doction.webviewapp.ui.ExploreView
import com.doction.webviewapp.ui.LibraryView
import com.doction.webviewapp.ui.SearchResultsPage
import com.doction.webviewapp.ui.SearchView
import com.doction.webviewapp.ui.SettingsPage
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout:       FrameLayout
    private lateinit var contentWrapper:   FrameLayout
    private lateinit var bottomNavBar:     BottomNavBar
    private lateinit var homeContainer:    FrameLayout
    private lateinit var exploreContainer: FrameLayout
    private lateinit var searchContainer:  FrameLayout
    private lateinit var libraryContainer: FrameLayout
    private lateinit var playerContainer:  FrameLayout
    private lateinit var webView:          WebView
    private lateinit var insetsController: WindowInsetsControllerCompat

    private var currentExibicao: ExibicaoPage? = null
    private var currentTab      = 0
    internal var statusBarHeight = 0
    private var navBarHeight    = 0
    private val bottomNavHeightDp = 48
    private val density get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        LockService.init(this)
        FaviconService.init(this)
        DownloadService.init(this)
        AppIconService.init(this)

        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        buildLayout()
        setupBackNavigation()
        setupWebView()
        webView.loadUrl("https://www.pornhub.com/shorties")
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val topOverlay = rootLayout.getChildAt(rootLayout.childCount - 1)
                if (topOverlay != contentWrapper && topOverlay != playerContainer) {
                    when (topOverlay) {
                        is SearchResultsPage -> { topOverlay.onBackPressed(); return }
                        is BrowserPage       -> { topOverlay.onBackPressed(); return }
                        else                 -> { removeContentOverlay(topOverlay); return }
                    }
                }
                if (playerContainer.visibility == View.VISIBLE) {
                    val child = playerContainer.getChildAt(0)
                    if (child is SettingsPage) { child.handleBack(); return }
                    closeVideoPlayer(); return
                }
                if (currentTab == 1) {
                    val ev = exploreContainer.getChildAt(0) as? ExploreView
                    if (ev?.isDrawerOpen() == true) { ev.closeDrawerIfOpen(); return }
                }
                if (currentTab == 0 && webView.canGoBack()) { webView.goBack(); return }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    fun setStatusBarDark(dark: Boolean) {
        insetsController.isAppearanceLightStatusBars = !dark
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

        bottomNavBar = BottomNavBar(this)
        bottomNavBar.setOnTabSelected { index -> switchTab(index) }

        contentWrapper.addView(homeContainer,    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(exploreContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(searchContainer,  FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(libraryContainer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentWrapper.addView(bottomNavBar.view, FrameLayout.LayoutParams(
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
            bottomNavBar.view.setPadding(0, 0, 0, navBarHeight)
            bottomNavBar.applyTheme(currentTab)
            insets
        }
    }

    private fun switchTab(index: Int) {
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

    fun shiftContent(toX: Float, duration: Long) {}

    fun openVideoPlayer(video: FeedVideo) {
        currentExibicao?.destroy()
        playerContainer.removeAllViews()
        val page = ExibicaoPage(this, video) { next -> openVideoPlayer(next) }
        currentExibicao = page
        playerContainer.addView(page, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.visibility   = View.VISIBLE
        playerContainer.alpha        = 0f
        playerContainer.translationY = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(420)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        setStatusBarDark(true)
    }

    fun closeVideoPlayer() {
        val h = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate()
            .translationY(h)
            .alpha(0f)
            .setDuration(320)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction {
                playerContainer.visibility = View.GONE
                playerContainer.alpha      = 1f
                currentExibicao?.destroy()
                currentExibicao = null
                playerContainer.removeAllViews()
                setStatusBarDark(currentTab == 0)
            }.start()
    }

    fun addContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        // Nova view entra da direita
        view.translationX = w
        rootLayout.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Página atual (a que está por baixo) empurrada para a esquerda
        val behind = rootLayout.getChildAt(rootLayout.childCount - 2)
        behind?.animate()
            ?.translationX(-w * 0.3f)
            ?.setDuration(350)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()

        // Nova página desliza da direita para o centro
        view.animate()
            .translationX(0f)
            .setDuration(350)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    fun removeContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()

        // Página por baixo volta ao centro
        val behind = rootLayout.getChildAt(rootLayout.childCount - 2)
        behind?.animate()
            ?.translationX(0f)
            ?.setDuration(350)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()

        // Página atual sai para a direita
        view.animate()
            .translationX(w)
            .setDuration(350)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { rootLayout.removeView(view) }
            .start()

        setStatusBarDark(currentTab == 0)
    }

    fun closeSettings() { closeVideoPlayer() }

    fun openSettings() {
        val settingsPage = SettingsPage(this)
        playerContainer.removeAllViews()
        playerContainer.addView(settingsPage, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        playerContainer.visibility   = View.VISIBLE
        playerContainer.alpha        = 0f
        playerContainer.translationY = resources.displayMetrics.heightPixels.toFloat()
        playerContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(420)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        setStatusBarDark(false)
    }

    fun openLicenses() {
        startActivity(android.content.Intent(this, OssLicensesMenuActivity::class.java))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false; loadWithOverviewMode = true
            useWideViewPort = true; setSupportZoom(false)
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
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    fun dp(value: Int) = (value * density).toInt()
}