package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.theme.AppTheme
import kotlin.concurrent.thread

private enum class WebTab { TUDO, IMAGENS, VIDEOS }

@SuppressLint("ViewConstructor")
class SearchResultsPage(
    context: Context,
    private val initialQuery: String = ""
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val history     = mutableListOf<String>()
    private val suggestions = mutableListOf<String>()

    private var activeTab    = WebTab.TUDO
    private var isSearching  = false
    private var isEditing    = false
    private var currentQuery = initialQuery
    private var statusBarHeight = 0

    private lateinit var appBarBg:          View
    private lateinit var searchField:       EditText
    private lateinit var clearBtn:          ImageView
    private lateinit var tabBar:            LinearLayout
    private lateinit var tabIndicatorView:  View
    private lateinit var bodyFrame:         FrameLayout
    private lateinit var webViewTudo:       WebView
    private lateinit var webViewImagens:    WebView
    private lateinit var videosCol:         LinearLayout
    private lateinit var videosScroll:      NestedScrollView
    private lateinit var suggestionsScroll: NestedScrollView
    private lateinit var suggestionsCol:    LinearLayout
    private lateinit var emptyState:        LinearLayout
    private lateinit var progressBar:       View
    private lateinit var appBarContainer:   FrameLayout

    private val tabViews = mutableMapOf<WebTab, TextView>()

    init {
        setBackgroundColor(AppTheme.bg)
        loadHistory()

        // Mede status bar antes de construir UI
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (top > 0 && statusBarHeight == 0) {
                statusBarHeight = top
                rebuildWithInsets()
            }
            insets
        }

        buildUI()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                activity.window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        activity.window.statusBarColor = AppTheme.bg

        // Animação de entrada estilo iOS — slide from right
        translationX = context.resources.displayMetrics.widthPixels.toFloat()
        animate()
            .translationX(0f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator(2.5f))
            .start()

        if (initialQuery.isNotEmpty()) {
            handler.post { doSearch(initialQuery) }
        } else {
            handler.post { showEditing() }
        }
    }

    private fun rebuildWithInsets() {
        // Atualiza margens do appBar e bodyFrame com altura real da status bar
        appBarContainer.setPadding(0, statusBarHeight, 0, 0)
        val appBarH = statusBarHeight + dp(48)
        (bodyFrame.layoutParams as LayoutParams).topMargin = appBarH
        bodyFrame.requestLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webViewTudo.destroy()
        webViewImagens.destroy()
    }

    // ─── Back press ──────────────────────────────────────────────────────────
    fun onBackPressed(): Boolean {
        dismiss()
        return true
    }

    // ─── Prefs ───────────────────────────────────────────────────────────────
    private fun loadHistory() {
        val size = prefs.getInt("${SEARCH_PREF}_size", 0)
        history.clear()
        history.addAll((0 until size).map { prefs.getString("${SEARCH_PREF}_$it", "") ?: "" })
    }

    private fun saveHistory(q: String) {
        if (q.isEmpty()) return
        history.remove(q); history.add(0, q)
        if (history.size > 20) history.removeAt(history.lastIndex)
        prefs.edit().apply {
            putInt("${SEARCH_PREF}_size", history.size)
            history.forEachIndexed { i, s -> putString("${SEARCH_PREF}_$i", s) }
        }.apply()
    }

    private fun removeHistory(q: String) {
        history.remove(q)
        prefs.edit().apply {
            putInt("${SEARCH_PREF}_size", history.size)
            history.forEachIndexed { i, s -> putString("${SEARCH_PREF}_$i", s) }
        }.apply()
        if (isEditing) rebuildSuggestions()
    }

    private fun clearHistory() {
        history.clear()
        prefs.edit().remove("${SEARCH_PREF}_size").apply()
        if (isEditing) rebuildSuggestions()
    }

    // ─── UI ──────────────────────────────────────────────────────────────────
    private fun buildUI() {
        buildAppBar()

        bodyFrame = FrameLayout(context)
        addView(bodyFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = dp(48) // será corrigido pelo insets
        })

        buildWebViews()
        buildVideosPane()
        buildSuggestionsView()
        buildEmptyState()
    }

    private fun buildAppBar() {
        appBarContainer = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        appBarContainer.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(8), dp(6))
        }

        val backBtn = svgView("icons/svg/back_arrow.svg", 20, AppTheme.icon).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { dismiss() }
        }
        row.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(36)))
        row.addView(View(context), LinearLayout.LayoutParams(dp(2), 0))

        val searchBar = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat() // bordas mais curvas
                setColor(Color.parseColor("#E8E8E8"))
            }
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        val searchIcon = svgView("icons/svg/search.svg", 16, Color.argb(100, 0, 0, 0))
        inner.addView(searchIcon, LinearLayout.LayoutParams(dp(16), dp(16)))
        inner.addView(View(context), LinearLayout.LayoutParams(dp(6), 0))

        searchField = EditText(context).apply {
            setText(initialQuery)
            setTextColor(AppTheme.text)
            setHintTextColor(Color.argb(100, 0, 0, 0))
            hint = "Pesquisar..."
            textSize = 15f; background = null; maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(text.toString().trim()); true } else false
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    val q = s?.toString()?.trim() ?: ""
                    clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                    if (!isEditing) { isEditing = true; showEditing() }
                    if (q.length >= 2) fetchSuggestions(q) else { suggestions.clear(); rebuildSuggestions() }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus && !isEditing) { isEditing = true; showEditing() } }
        }
        inner.addView(searchField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        clearBtn = svgView("icons/svg/close.svg", 14, Color.argb(150, 0, 0, 0)).apply {
            visibility = if (initialQuery.isNotEmpty()) View.VISIBLE else View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { searchField.setText(""); suggestions.clear(); rebuildSuggestions() }
        }
        inner.addView(clearBtn, LinearLayout.LayoutParams(dp(28), dp(28)))
        searchBar.addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(36)))
        row.addView(searchBar, LinearLayout.LayoutParams(0, dp(36), 1f))
        col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

        tabBar = buildTabBar()
        col.addView(tabBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        appBarContainer.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(appBarContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildTabBar(): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppTheme.bg)
            visibility = View.GONE
        }
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        listOf(WebTab.TUDO to "Tudo", WebTab.IMAGENS to "Imagens", WebTab.VIDEOS to "Vídeos")
            .forEach { (tab, label) ->
                val isActive = tab == activeTab
                val tv = TextView(context).apply {
                    text = label; textSize = 13f; gravity = Gravity.CENTER
                    setPadding(dp(12), 0, dp(12), 0)
                    setTextColor(if (isActive) AppTheme.ytRed else Color.argb(115, 0, 0, 0))
                    setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                    setOnClickListener { switchTab(tab) }
                }
                tabViews[tab] = tv
                tabRow.addView(tv, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)))
            }
        bar.addView(tabRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

        val indicatorContainer = FrameLayout(context)
        tabIndicatorView = View(context).apply { setBackgroundColor(AppTheme.ytRed) }
        indicatorContainer.addView(tabIndicatorView, FrameLayout.LayoutParams(dp(40), dp(2)).also {
            it.gravity = Gravity.BOTTOM or Gravity.START; it.leftMargin = dp(16)
        })
        bar.addView(indicatorContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(2)))
        return bar
    }

    private fun switchTab(tab: WebTab) {
        if (tab == activeTab) return
        activeTab = tab
        tabViews.forEach { (t, tv) ->
            val isActive = t == tab
            tv.setTextColor(if (isActive) AppTheme.ytRed else Color.argb(115, 0, 0, 0))
            tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        updateTabIndicator()
        webViewTudo.visibility    = if (tab == WebTab.TUDO)    View.VISIBLE else View.GONE
        webViewImagens.visibility = if (tab == WebTab.IMAGENS) View.VISIBLE else View.GONE
        videosScroll.visibility   = if (tab == WebTab.VIDEOS)  View.VISIBLE else View.GONE
    }

    private fun updateTabIndicator() {
        val tv = tabViews[activeTab] ?: return
        tv.post {
            val lp = tabIndicatorView.layoutParams as FrameLayout.LayoutParams
            tabIndicatorView.animate()
                .translationX((tv.left + dp(4) - (tabIndicatorView.layoutParams as FrameLayout.LayoutParams).leftMargin).toFloat())
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator(2f))
                .withEndAction {
                    lp.leftMargin = tv.left + dp(4)
                    lp.width = tv.width - dp(8)
                    tabIndicatorView.layoutParams = lp
                    tabIndicatorView.translationX = 0f
                }
                .start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebViews() {
        webViewTudo    = makeWebView()
        webViewImagens = makeWebView()

        progressBar = View(context).apply {
            setBackgroundColor(AppTheme.ytRed); visibility = View.GONE
        }

        bodyFrame.addView(webViewTudo, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        bodyFrame.addView(webViewImagens, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        bodyFrame.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(2)).also { it.gravity = Gravity.TOP })

        webViewTudo.visibility    = View.GONE
        webViewImagens.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                useWideViewPort = true; loadWithOverviewMode = true; setSupportZoom(false)
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                // Força modo claro no WebView
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                }
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    injectDdgCss(view)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return true
                    if (isDdgUrl(url)) return false
                    val page = BrowserPage(context, freeNavigation = true, externalUrl = url)
                    activity.addContentOverlay(page)
                    return true
                }
            }
        }
    }

    private fun isDdgUrl(url: String) = try {
        val h = android.net.Uri.parse(url).host?.lowercase() ?: ""
        h == "duckduckgo.com" || h.endsWith(".duckduckgo.com")
    } catch (_: Exception) { false }

    private fun injectDdgCss(view: WebView?) {
        view?.evaluateJavascript("""
            (function(){
                if(window.__ddgCssInjected)return;
                window.__ddgCssInjected=true;
                var s=document.createElement('style');
                s.textContent=
                    '#header_wrapper,#header,.header--aside,.js-header-wrapper,.nav-menu,#duckbar,[class*="Header"],[id*="header"]{display:none!important}' +
                    '::-webkit-scrollbar{display:none!important;width:0!important}' +
                    'html,body{background:#ffffff!important;color-scheme:light!important}' +
                    '*{color-scheme:light!important}';
                document.head.appendChild(s);
                // Força prefers-color-scheme light via meta
                var m=document.querySelector('meta[name="color-scheme"]');
                if(!m){m=document.createElement('meta');m.name='color-scheme';document.head.appendChild(m);}
                m.content='light';
            })();
        """.trimIndent(), null)
    }

    private fun buildVideosPane() {
        videosScroll = NestedScrollView(context).apply {
            isFillViewport = true
            visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        videosCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(80))
        }
        videosScroll.addView(videosCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(videosScroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildSuggestionsView() {
        suggestionsScroll = NestedScrollView(context).apply {
            isFillViewport = true; visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        suggestionsCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        suggestionsScroll.addView(suggestionsCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(suggestionsScroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildEmptyState() {
        emptyState = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE
        }
        val icon = svgView("icons/svg/search.svg", 48, Color.argb(64, 0, 0, 0))
        emptyState.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL })
        emptyState.addView(View(context), LinearLayout.LayoutParams(0, dp(14)))
        emptyState.addView(TextView(context).apply {
            text = "Pesquisa algo"
            setTextColor(Color.argb(100, 0, 0, 0))
            textSize = 14f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        bodyFrame.addView(emptyState, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ─── Estados ─────────────────────────────────────────────────────────────
    private fun showEditing() {
        isEditing = true
        tabBar.visibility            = View.GONE
        webViewTudo.visibility       = View.GONE
        webViewImagens.visibility    = View.GONE
        videosScroll.visibility      = View.GONE
        emptyState.visibility        = View.GONE
        suggestionsScroll.visibility = View.VISIBLE
        val appBarH = statusBarHeight + dp(48)
        (bodyFrame.layoutParams as LayoutParams).topMargin = appBarH
        bodyFrame.requestLayout()
        rebuildSuggestions()
    }

    private fun showResults() {
        suggestionsScroll.visibility = View.GONE
        emptyState.visibility        = View.GONE
        tabBar.visibility            = View.VISIBLE
        val appBarH = statusBarHeight + dp(86)
        (bodyFrame.layoutParams as LayoutParams).topMargin = appBarH
        bodyFrame.requestLayout()
        webViewTudo.visibility    = if (activeTab == WebTab.TUDO)    View.VISIBLE else View.GONE
        webViewImagens.visibility = if (activeTab == WebTab.IMAGENS) View.VISIBLE else View.GONE
        videosScroll.visibility   = if (activeTab == WebTab.VIDEOS)  View.VISIBLE else View.GONE
        handler.post { updateTabIndicator() }
    }

    // ─── Pesquisa ─────────────────────────────────────────────────────────────
    private fun doSearch(q: String) {
        if (q.isEmpty()) return
        currentQuery = q
        searchField.setText(q); searchField.clearFocus(); hideKeyboard()
        isEditing = false; isSearching = true
        saveHistory(q)
        activeTab = WebTab.TUDO

        val enc = android.net.Uri.encode(q)
        webViewTudo.loadUrl("https://duckduckgo.com/?q=$enc&kp=-2&kav=1&ia=web&kaj=m")
        webViewImagens.loadUrl("https://duckduckgo.com/?q=$enc&kp=-2&kav=1&iax=images&ia=images&kaj=m")

        tabViews.forEach { (t, tv) ->
            val isActive = t == WebTab.TUDO
            tv.setTextColor(if (isActive) AppTheme.ytRed else Color.argb(115, 0, 0, 0))
            tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        showResults()
    }

    // ─── Sugestões ───────────────────────────────────────────────────────────
    private fun fetchSuggestions(q: String) {
        thread {
            try {
                val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${android.net.Uri.encode(q)}"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                val body = conn.inputStream.bufferedReader().readText()
                val list = mutableListOf<String>()
                val regex = Regex(""""([^"]+)"""")
                var count = 0
                for (m in regex.findAll(body)) {
                    if (count++ == 0) continue
                    list.add(m.groupValues[1])
                    if (list.size >= 7) break
                }
                handler.post {
                    suggestions.clear()
                    suggestions.addAll(list)
                    if (isEditing) rebuildSuggestions()
                }
            } catch (_: Exception) {}
        }
    }

    private fun rebuildSuggestions() {
        suggestionsCol.removeAllViews()
        val q = searchField.text.toString().trim()
        val showSugg = q.length >= 2 && suggestions.isNotEmpty()
        val items = if (showSugg) suggestions else history

        if (!showSugg && history.isNotEmpty()) {
            val hRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            hRow.addView(TextView(context).apply {
                text = "Pesquisas recentes"
                setTextColor(Color.argb(115, 0, 0, 0))
                textSize = 12f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            hRow.addView(TextView(context).apply {
                text = "Limpar tudo"; setTextColor(AppTheme.ytRed); textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setOnClickListener { clearHistory() }
            })
            suggestionsCol.addView(hRow)
        }

        if (items.isEmpty()) {
            // Mostra histórico vazio — não mostra nada, só o header se existir
            return
        }

        val total = items.size
        items.forEachIndexed { i, label ->
            val isOnly = total == 1; val isFirst = i == 0; val isLast = i == total - 1
            val bigR = dp(12).toFloat(); val smallR = dp(6).toFloat()
            val radii = when {
                isOnly  -> floatArrayOf(bigR, bigR, bigR, bigR, bigR, bigR, bigR, bigR)
                isFirst -> floatArrayOf(bigR, bigR, bigR, bigR, smallR, smallR, smallR, smallR)
                isLast  -> floatArrayOf(smallR, smallR, smallR, smallR, bigR, bigR, bigR, bigR)
                else    -> floatArrayOf(smallR, smallR, smallR, smallR, smallR, smallR, smallR, smallR)
            }
            val item = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                    setColor(Color.parseColor("#F0F0F0"))
                }
                setOnClickListener { doSearch(label) }
                // Animação de entrada estilo iOS — stagger
                alpha = 0f
                translationY = dp(10).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setStartDelay((i * 40).toLong())
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
            }
            val mutedColor = Color.argb(70, 0, 0, 0)
            val iconPath = if (showSugg) "icons/svg/search.svg" else "icons/svg/history.svg"
            row.addView(svgView(iconPath, 16, mutedColor), LinearLayout.LayoutParams(dp(16), dp(16)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))
            row.addView(TextView(context).apply {
                text = label; setTextColor(AppTheme.text); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val actionIcon = svgView(
                if (showSugg) "icons/svg/back_arrow.svg" else "icons/svg/close.svg", 15, mutedColor
            ).apply {
                if (showSugg) rotation = -45f
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener {
                    if (showSugg) { searchField.setText(label); searchField.setSelection(label.length) }
                    else removeHistory(label)
                }
            }
            row.addView(actionIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
            item.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(58)))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (!isLast) lp.bottomMargin = dp(2)
            suggestionsCol.addView(item, lp)
        }
    }

    // ─── Dismiss com animação iOS ─────────────────────────────────────────────
    private fun dismiss() {
        hideKeyboard()
        animate()
            .translationX(context.resources.displayMetrics.widthPixels.toFloat())
            .setDuration(340)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { activity.removeContentOverlay(this) }
            .start()
    }

    // ─── Tema ─────────────────────────────────────────────────────────────────
    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        appBarBg.setBackgroundColor(AppTheme.bg)
        tabBar.setBackgroundColor(AppTheme.bg)
        suggestionsScroll.setBackgroundColor(AppTheme.bg)
        searchField.setTextColor(AppTheme.text)
        tabViews.forEach { (t, tv) ->
            val isActive = t == activeTab
            tv.setTextColor(if (isActive) AppTheme.ytRed else Color.argb(115, 0, 0, 0))
        }
        if (isEditing) rebuildSuggestions()
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val SEARCH_PREF = "search_history_v3"
    }
}