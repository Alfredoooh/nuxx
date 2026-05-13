package com.nuxx.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
import com.nuxx.app.ui.SearchResultsPage
import com.nuxx.app.ui.SearchView
import com.nuxx.app.ui.SettingsPage
import com.nuxx.app.ui.ShortiesPage
import com.nuxx.app.ui.VideoPreviewModal

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    internal lateinit var contentWrapper: FrameLayout
    private lateinit var superBottomBar: LinearLayout
    private lateinit var superHomeContainer: FrameLayout
    private lateinit var superSearchContainer: FrameLayout

    private lateinit var nuxxOverlay: FrameLayout
    private lateinit var bottomNavBar: BottomNavBar
    private lateinit var homeContainer: FrameLayout
    private lateinit var exploreContainer: FrameLayout
    private lateinit var searchContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var homeScrollView: ScrollView
    private lateinit var homeInnerLayout: LinearLayout
    internal var shortiesPage: ShortiesPage? = null

    private var nuxxIsOpen = false
    private var superSearchOpen = false
    private var currentTab = 0
    internal val currentTabIndex get() = currentTab

    private lateinit var insetsController: WindowInsetsControllerCompat
    internal var statusBarHeight = 0
    internal var navBarHeight = 0
    internal val bottomNavHeightDp = 56
    private val density get() = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        LockService.init(this)
        FaviconService.init(this)
        DownloadService.init(this)
        AppIconService.init(this)
        NuxxKeepAliveService.start(this)

        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        buildLayout()
        setupBackNavigation()
        setupWebView()
    }

    override fun onPause() {
        super.onPause()
        shortiesPage?.pauseForBackground()
    }

    override fun onResume() {
        super.onResume()
        if (nuxxIsOpen && currentTab == 0) {
            shortiesPage?.resumeFromBackground()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shortiesPage?.onDestroy()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val top = rootLayout.getChildAt(rootLayout.childCount - 1)
                if (top != contentWrapper) {
                    when (top) {
                        is SearchResultsPage -> { top.onBackPressed(); return }
                        is BrowserPage -> { top.onBackPressed(); return }
                        is SettingsPage -> { top.handleBack(); return }
                        else -> { removeContentOverlay(top); return }
                    }
                }

                if (superSearchOpen) {
                    closeSuperSearch()
                    return
                }

                if (nuxxIsOpen) {
                    val nuxxChildCount = nuxxOverlay.childCount
                    if (nuxxChildCount > 4) {
                        val nuxxTop = nuxxOverlay.getChildAt(nuxxChildCount - 1)
                        when (nuxxTop) {
                            is SearchResultsPage -> { nuxxTop.onBackPressed(); return }
                            is BrowserPage -> { nuxxTop.onBackPressed(); return }
                            is SettingsPage -> { nuxxTop.handleBack(); return }
                            else -> { removeNuxxOverlay(nuxxTop); return }
                        }
                    }
                    if (currentTab == 1) {
                        val ev = exploreContainer.getChildAt(0) as? ExploreView
                        if (ev?.isDrawerOpen() == true) { ev.closeDrawerIfOpen(); return }
                    }
                    if (currentTab == 0 && webView.canGoBack()) { webView.goBack(); return }
                    closeNuxxApp()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    fun setStatusBarDark(dark: Boolean) {
        insetsController.isAppearanceLightStatusBars = !dark
        window.statusBarColor = if (dark) Color.TRANSPARENT else AppTheme.bg
    }

    private fun buildLayout() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(rootLayout)

        contentWrapper = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }

        superHomeContainer = FrameLayout(this)
        buildSuperHomeScreen()

        superSearchContainer = FrameLayout(this).apply { visibility = View.GONE }
        buildSuperSearchScreen()

        superBottomBar = buildSuperBottomBar()

        contentWrapper.addView(superHomeContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        contentWrapper.addView(superSearchContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        contentWrapper.addView(superBottomBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        rootLayout.addView(contentWrapper, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        buildNuxxOverlay()
        rootLayout.addView(nuxxOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        nuxxOverlay.visibility = View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            navBarHeight = bars.bottom

            val bottomTotal = dp(bottomNavHeightDp) + navBarHeight

            listOf(superHomeContainer, superSearchContainer).forEach { c ->
                (c.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = statusBarHeight
                    bottomMargin = bottomTotal
                }.also { c.layoutParams = it }
            }

            listOf(homeContainer, exploreContainer, searchContainer).forEach { c ->
                (c.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = statusBarHeight
                    bottomMargin = bottomTotal
                }.also { c.layoutParams = it }
            }

            superBottomBar.setPadding(0, 0, 0, navBarHeight)
            bottomNavBar.view.setPadding(0, 0, 0, navBarHeight)
            bottomNavBar.applyTheme(currentTab)
            setStatusBarDark(false)
            insets
        }
    }

    private fun buildSuperHomeScreen() {
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.WHITE)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val searchBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }
        val searchBarView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#F0F0F5"))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { openSuperSearch() }
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val googleIconContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
            }
        }
        var googleLoaded = false
        try {
            val bmp = BitmapFactory.decodeStream(assets.open("icons/google_g.png"))
            val iv = android.widget.ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            googleIconContainer.addView(iv,
                FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
            googleLoaded = true
        } catch (_: Exception) { }
        if (!googleLoaded) {
            googleIconContainer.addView(TextView(this).apply {
                text = "G"; setTextColor(Color.parseColor("#4285F4"))
                textSize = 14f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
        }

        searchRow.addView(googleIconContainer, LinearLayout.LayoutParams(dp(24), dp(24)))
        searchRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 0))
        searchRow.addView(TextView(this).apply {
            text = "Pesquisar ou digitar o endereço..."
            setTextColor(Color.parseColor("#99888888"))
            textSize = 14f; maxLines = 1
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val aiBadge = TextView(this).apply {
            text = "✦ AI"; textSize = 12f
            setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#5B4DFF"))
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        searchRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 0))
        searchRow.addView(aiBadge, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        searchBarView.addView(searchRow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        searchBarContainer.addView(searchBarView, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        inner.addView(searchBarContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        inner.addView(buildAppsGrid(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        inner.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E8E8EE"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.topMargin = dp(4); it.bottomMargin = dp(4)
        })

        scrollView.addView(inner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        superHomeContainer.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildSuperSearchScreen() {
        superSearchContainer.setBackgroundColor(Color.WHITE)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(12), dp(10))
        }

        val backBtn = FrameLayout(this).apply {
            isClickable = true; isFocusable = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { closeSuperSearch() }
        }
        backBtn.addView(svgImageView(
            "icons/svg/phosphor-icons/regular/arrow-left.svg", 24, Color.parseColor("#111111")
        ), FrameLayout.LayoutParams(dp(24), dp(24)).also { it.gravity = Gravity.CENTER })

        val editText = EditText(this).apply {
            hint = "Pesquisar ou endereço"
            setHintTextColor(Color.parseColor("#99888888"))
            setTextColor(Color.parseColor("#111111"))
            textSize = 15f
            background = null
            maxLines = 1
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, _, _ ->
                val q = text.toString().trim()
                if (q.isNotEmpty()) {
                    closeSuperSearch()
                    val url = if (q.startsWith("http://") || q.startsWith("https://")) q
                    else "https://www.google.com/search?q=${android.net.Uri.encode(q)}"
                    openSuperBrowser(url)
                }
                true
            }
        }

        inputRow.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        inputRow.addView(editText, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        val divider = View(this).apply { setBackgroundColor(Color.parseColor("#E0E0E8")) }

        val appsLabel = TextView(this).apply {
            text = "Apps"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        outer.addView(inputRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        outer.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ))
        outer.addView(appsLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        outer.addView(buildSearchAppsRow(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        superSearchContainer.addView(outer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildSearchAppsRow(): View {
        data class SearchApp(val label: String, val asset: String, val url: String)
        val apps = listOf(
            SearchApp("Google",    "icons/apps/google.png",    "https://www.google.com"),
            SearchApp("YouTube",   "icons/apps/youtube.png",   "https://m.youtube.com"),
            SearchApp("Facebook",  "icons/apps/facebook.png",  "https://m.facebook.com"),
            SearchApp("Instagram", "icons/apps/instagram.png", "https://www.instagram.com"),
            SearchApp("X",         "icons/apps/x.png",         "https://x.com"),
            SearchApp("nuxx",      "logo.png",                 "__nuxx__")
        )

        val screenW = resources.displayMetrics.widthPixels
        val cols = 5
        val cellW = screenW / cols
        val iconSz = dp(40)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }

        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        apps.forEachIndexed { i, app ->
            if (i > 0 && i % cols == 0) {
                outer.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    closeSuperSearch()
                    if (app.url == "__nuxx__") openNuxxApp()
                    else openSuperBrowser(app.url)
                }
            }

            val iconHolder = FrameLayout(this)
            var loaded = false
            try {
                val svg = SVG.getFromAsset(assets, app.asset)
                svg.documentWidth = iconSz.toFloat(); svg.documentHeight = iconSz.toFloat()
                val bmp = Bitmap.createBitmap(iconSz, iconSz, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                iconHolder.addView(android.widget.ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                }, FrameLayout.LayoutParams(iconSz, iconSz))
                loaded = true
            } catch (_: Exception) { }
            if (!loaded) {
                try {
                    val bmp = BitmapFactory.decodeStream(assets.open(app.asset))
                    iconHolder.addView(android.widget.ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    }, FrameLayout.LayoutParams(iconSz, iconSz))
                    loaded = true
                } catch (_: Exception) { }
            }
            if (!loaded) {
                iconHolder.addView(TextView(this).apply {
                    text = app.label.take(1); textSize = 18f; gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#333333"))
                }, FrameLayout.LayoutParams(iconSz, iconSz).also { it.gravity = Gravity.CENTER })
            }

            cell.addView(iconHolder, LinearLayout.LayoutParams(iconSz, iconSz))
            cell.addView(View(this), LinearLayout.LayoutParams(1, dp(4)))
            cell.addView(TextView(this).apply {
                text = app.label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#444444")); maxLines = 1
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))

            row.addView(cell, LinearLayout.LayoutParams(cellW, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        outer.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return outer
    }

    private fun openSuperSearch() {
        if (superSearchOpen) return
        superSearchOpen = true
        superHomeContainer.visibility = View.GONE
        superSearchContainer.visibility = View.VISIBLE
        mainHandler.post {
            val et = findEditTextIn(superSearchContainer)
            et?.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            et?.let { imm.showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    private fun closeSuperSearch() {
        if (!superSearchOpen) return
        superSearchOpen = false
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(superSearchContainer.windowToken, 0)
        superSearchContainer.visibility = View.GONE
        superHomeContainer.visibility = View.VISIBLE
    }

    private fun findEditTextIn(vg: android.view.ViewGroup): EditText? {
        for (i in 0 until vg.childCount) {
            val c = vg.getChildAt(i)
            if (c is EditText) return c
            if (c is android.view.ViewGroup) findEditTextIn(c)?.let { return it }
        }
        return null
    }

    private fun buildSuperBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(2).toFloat()
            gravity = Gravity.CENTER_VERTICAL

            addView(buildSuperTabItem(
                svgPath = "icons/svg/phosphor-icons/fill/house-fill.svg",
                label = "Início",
                tint = Color.parseColor("#111111")
            ) { },
                LinearLayout.LayoutParams(0, dp(bottomNavHeightDp), 1f))

            addView(buildSuperTabItem(
                svgPath = "icons/svg/phosphor-icons/regular/magnifying-glass.svg",
                label = "Pesquisar",
                tint = Color.parseColor("#555555")
            ) { openSuperSearch() },
                LinearLayout.LayoutParams(0, dp(bottomNavHeightDp), 1f))

            addView(buildSuperTabItem(
                svgPath = "icons/svg/phosphor-icons/regular/folder.svg",
                label = "Arquivos",
                tint = Color.parseColor("#555555")
            ) { showSnackbarGlobal("Em breve") },
                LinearLayout.LayoutParams(0, dp(bottomNavHeightDp), 1f))

            addView(buildSuperTabItem(
                svgPath = "icons/svg/phosphor-icons/regular/gear.svg",
                label = "Config",
                tint = Color.parseColor("#555555")
            ) { },
                LinearLayout.LayoutParams(0, dp(bottomNavHeightDp), 1f))
        }
    }

    private fun buildSuperTabItem(svgPath: String, label: String, tint: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }

            val iconSz = dp(24)
            val iconHolder = FrameLayout(this@MainActivity)
            var loaded = false
            try {
                val svg = SVG.getFromAsset(assets, svgPath)
                svg.documentWidth = iconSz.toFloat(); svg.documentHeight = iconSz.toFloat()
                val bmp = Bitmap.createBitmap(iconSz, iconSz, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                iconHolder.addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    setColorFilter(tint)
                }, FrameLayout.LayoutParams(iconSz, iconSz))
                loaded = true
            } catch (_: Exception) { }
            if (!loaded) {
                iconHolder.addView(View(this@MainActivity).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                }, FrameLayout.LayoutParams(iconSz, iconSz))
            }

            addView(iconHolder, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(3)))
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(tint); maxLines = 1
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun buildNuxxOverlay() {
        nuxxOverlay = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        homeContainer = FrameLayout(this)
        buildNuxxHomeTab()

        webView = WebView(this).apply { visibility = View.GONE }
        homeContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        exploreContainer = FrameLayout(this).apply { visibility = View.GONE }
        exploreContainer.addView(ExploreView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        searchContainer = FrameLayout(this).apply { visibility = View.GONE }
        searchContainer.addView(SearchView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        bottomNavBar = BottomNavBar(this)
        bottomNavBar.setOnTabSelected { index -> switchTab(index) }

        nuxxOverlay.addView(homeContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        nuxxOverlay.addView(exploreContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        nuxxOverlay.addView(searchContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        nuxxOverlay.addView(bottomNavBar.view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })
    }

    private fun buildNuxxHomeTab() {
        homeScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#18181C"))
        }
        homeInnerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        homeInnerLayout.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#333340"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.topMargin = dp(4); it.bottomMargin = dp(4)
        })

        homeScrollView.addView(homeInnerLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        homeContainer.addView(homeScrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        shortiesPage = ShortiesPage(this).apply { visibility = View.VISIBLE }
        homeContainer.addView(shortiesPage, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    data class AppItem(
        val label: String,
        val iconAsset: String?,
        val iconEmoji: String?,
        val bgColor: String,
        val onClick: () -> Unit
    )

    private fun buildAppsGrid(): View {
        val screenW = resources.displayMetrics.widthPixels
        val cols = 5
        val cellW = screenW / cols
        val iconSz = dp(48)
        val labelSz = 10f

        val appItems = listOf(
            AppItem("Google",    null,                       "G",  "#FFFFFF") { openSuperBrowser("https://www.google.com") },
            AppItem("YouTube",   "icons/apps/youtube.png",   null, "#FF0000") { openSuperBrowser("https://m.youtube.com") },
            AppItem("Facebook",  "icons/apps/facebook.png",  null, "#1877F2") { openSuperBrowser("https://m.facebook.com") },
            AppItem("Instagram", "icons/apps/instagram.png", null, "#E1306C") { openSuperBrowser("https://www.instagram.com") },
            AppItem("nuxx",      "logo.png",                 null, "#E01462") { openNuxxApp() },
            AppItem("X",         "icons/apps/x.png",         null, "#000000") { openSuperBrowser("https://x.com") },
            AppItem("TikTok",    "icons/apps/tiktok.png",    null, "#010101") { openSuperBrowser("https://www.tiktok.com") },
            AppItem("WhatsApp",  "icons/apps/whatsapp.png",  null, "#25D366") { openSuperBrowser("https://web.whatsapp.com") },
            AppItem("Mais",      null,                       "⋯",  "#3A3A45") { showSnackbarGlobal("Em breve") }
        )

        val rowCount = Math.ceil(appItems.size.toDouble() / cols).toInt()
        val outerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        for (row in 0 until rowCount) {
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until cols) {
                val idx = row * cols + col
                val cellFrame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cellW, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                if (idx < appItems.size) {
                    cellFrame.addView(buildAppCell(appItems[idx], iconSz, labelSz),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        ))
                }
                rowView.addView(cellFrame)
            }
            outerCol.addView(rowView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        return outerCol
    }

    private fun buildAppCell(item: AppItem, iconSz: Int, labelSz: Float): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { item.onClick() }
        }
        val iconFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor(item.bgColor))
            }
        }
        var loaded = false
        if (item.iconAsset != null) {
            try {
                val bmp = BitmapFactory.decodeStream(assets.open(item.iconAsset))
                iconFrame.addView(android.widget.ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }, FrameLayout.LayoutParams(iconSz, iconSz))
                loaded = true
            } catch (_: Exception) { }
        }
        if (!loaded) {
            iconFrame.addView(TextView(this).apply {
                text = item.iconEmoji ?: item.label.take(1)
                textSize = 20f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            }, FrameLayout.LayoutParams(iconSz, iconSz).also { it.gravity = Gravity.CENTER })
        }
        cell.addView(iconFrame, LinearLayout.LayoutParams(iconSz, iconSz))
        cell.addView(View(this), LinearLayout.LayoutParams(1, dp(6)))
        cell.addView(TextView(this).apply {
            text = item.label; textSize = labelSz; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#333333")); maxLines = 1
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return cell
    }

    private fun openNuxxApp() {
        if (nuxxIsOpen) return
        nuxxIsOpen = true
        nuxxOverlay.visibility = View.VISIBLE
        superBottomBar.visibility = View.GONE
        shortiesPage?.resumeFromTabSwitch()
        setStatusBarDark(true)

        val h = resources.displayMetrics.heightPixels.toFloat()
        nuxxOverlay.translationY = h
        nuxxOverlay.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    fun closeNuxx() = closeNuxxApp()

    private fun closeNuxxApp() {
        if (!nuxxIsOpen) return
        shortiesPage?.pauseForTabSwitch()
        val h = resources.displayMetrics.heightPixels.toFloat()
        nuxxOverlay.animate()
            .translationY(h)
            .setDuration(300)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxIsOpen = false
                nuxxOverlay.visibility = View.GONE
                nuxxOverlay.translationY = 0f
                superBottomBar.visibility = View.VISIBLE
                setStatusBarDark(false)
            }.start()
    }

    private fun openSuperBrowser(url: String) {
        addContentOverlay(BrowserPage(this, freeNavigation = true, externalUrl = url))
    }

    fun switchTabPublic(index: Int) = switchTab(index)

    private fun switchTab(index: Int) {
        if (index == currentTab) return
        val prev = currentTab
        currentTab = index

        (exploreContainer.getChildAt(0) as? ExploreView)?.closeDrawerIfOpen()

        if (prev == 0 && index != 0) {
            shortiesPage?.pauseForTabSwitch()
        } else if (index == 0 && prev != 0) {
            shortiesPage?.resumeFromTabSwitch()
        }

        homeContainer.visibility    = if (index == 0) View.VISIBLE else View.GONE
        exploreContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        searchContainer.visibility  = if (index == 2) View.VISIBLE else View.GONE

        bottomNavBar.updateIcon(prev, false, index == 0)
        bottomNavBar.updateIcon(index, true, index == 0)
        bottomNavBar.applyTheme(index)
        setStatusBarDark(true)
    }

    private fun addNuxxOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = w
        nuxxOverlay.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        bottomNavBar.view.visibility = View.GONE
        val behind = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
        behind?.animate()?.translationX(-w * 0.3f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(0f)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator()).start()
    }

    private fun removeNuxxOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val behind = nuxxOverlay.getChildAt(nuxxOverlay.childCount - 2)
        behind?.animate()?.translationX(0f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(w)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                nuxxOverlay.removeView(view)
                if (nuxxOverlay.childCount <= 4) {
                    bottomNavBar.view.visibility = View.VISIBLE
                }
                setStatusBarDark(true)
            }.start()
    }

    fun addContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = w
        rootLayout.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        superBottomBar.visibility = View.GONE
        val behind = rootLayout.getChildAt(rootLayout.childCount - 2)
        behind?.animate()?.translationX(-w * 0.3f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(0f)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator()).start()
    }

    fun removeContentOverlay(view: View) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val behind = rootLayout.getChildAt(rootLayout.childCount - 2)
        behind?.animate()?.translationX(0f)
            ?.setDuration(320)?.setInterpolator(FastOutSlowInInterpolator())?.start()
        view.animate().translationX(w)
            .setDuration(320).setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                rootLayout.removeView(view)
                if (!nuxxIsOpen) superBottomBar.visibility = View.VISIBLE
                setStatusBarDark(nuxxIsOpen)
            }.start()
    }

    fun closeSettings() {
        val top = rootLayout.getChildAt(rootLayout.childCount - 1)
        if (top is SettingsPage) removeContentOverlay(top)
    }

    fun openSettings() {
        addContentOverlay(SettingsPage(this))
        setStatusBarDark(false)
    }

    fun openLicenses() {}

    fun openBrowserOverlay(url: String = "https://www.google.com") {
        openSuperBrowser(url)
    }

    fun openVideoPlayer(video: FeedVideo, originThumb: View? = null) {
        VideoPreviewModal.show(this, video)
    }

    fun openExibicao(video: FeedVideo) {
        VideoPreviewModal.show(this, video)
    }

    fun closeVideoPlayer() {}
    fun shiftContent(toX: Float, duration: Long) {}

    fun showSnackbarGlobal(message: String) {
        val tag = "snackbar_global"
        rootLayout.findViewWithTag<View>(tag)?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        val navH = navBarHeight + dp(bottomNavHeightDp)
        val snack = FrameLayout(this).apply {
            this.tag = tag
            elevation = dp(8).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1C1B1F"))
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        snack.addView(TextView(this).apply {
            text = message; setTextColor(Color.parseColor("#F4EFF4")); textSize = 14f
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER })
        rootLayout.addView(snack, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.BOTTOM
            it.bottomMargin = navH + dp(8)
            it.leftMargin = dp(12); it.rightMargin = dp(12)
        })
        snack.alpha = 0f; snack.translationY = dp(16).toFloat()
        snack.animate().alpha(1f).translationY(0f)
            .setDuration(200).setInterpolator(FastOutSlowInInterpolator()).start()
        mainHandler.postDelayed({
            if (snack.isAttachedToWindow) {
                snack.animate().alpha(0f).translationY(dp(16).toFloat()).setDuration(160)
                    .withEndAction {
                        (snack.parent as? android.view.ViewGroup)?.removeView(snack)
                    }.start()
            }
        }, 3000)
    }

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
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
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
            val px = dp(sizeDp)
            val svg = SVG.getFromAsset(assets, assetPath)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) { }
        return iv
    }

    fun dp(value: Int) = (value * density).toInt()
}