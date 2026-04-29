// BrowserPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.models.SiteModel
import com.doction.webviewapp.theme.AppTheme

@SuppressLint("ViewConstructor")
class BrowserPage(
    context: Context,
    private val site: SiteModel? = null,
    private val initialQuery: String? = null,
    private val freeNavigation: Boolean = false,
    private val externalUrl: String? = null
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private lateinit var appBarBg:      View
    private lateinit var titleText:     TextView
    private lateinit var progressStrip: View
    private lateinit var webView:       WebView
    private lateinit var noConnView:    LinearLayout
    private lateinit var faviconView:   ImageView

    private var menuOpen     = false
    private var pageTitle    = site?.name ?: ""
    private var noConnection = false

    private val startUrl: String get() = when {
        externalUrl != null                  -> externalUrl
        site != null && initialQuery != null -> site.buildUrl(initialQuery)
        site != null                         -> site.baseUrl
        else                                 -> "about:blank"
    }

    init {
        setBackgroundColor(Color.WHITE)
        // Status bar branco fixo — independente do AppTheme
        activity.window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility =
            activity.window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        buildUI()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webView.destroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Força sempre branco ao reentrar na view
        activity.window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility =
            activity.window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressed(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun onBackPressed() {
        if (menuOpen) { closeMenu(); return }
        if (webView.canGoBack()) { webView.goBack(); return }
        dismiss()
    }

    private fun buildUI() {
        buildAppBar()
        buildWebView()
        buildNoConnection()
        buildMenuPopup()
    }

    private fun buildAppBar() {
        val statusH = activity.statusBarHeight
        val appBarH = dp(42)
        val totalH  = statusH + appBarH

        val appBar = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(Color.WHITE) }
        appBar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, statusH, 0, 0)
        }

        val closeBtn = svgView("icons/svg/close.svg", 16, Color.parseColor("#0F0F0F")).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { dismiss() }
        }
        row.addView(closeBtn, LinearLayout.LayoutParams(dp(48), appBarH))

        val centerCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }

        faviconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            loadGlobePlaceholder()
        }
        centerCol.addView(faviconView, LinearLayout.LayoutParams(dp(20), dp(20)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL })
        centerCol.addView(View(context), LinearLayout.LayoutParams(0, dp(2)))

        titleText = TextView(context).apply {
            text = shortLabel()
            setTextColor(Color.parseColor("#606060"))
            textSize = 11f; setTypeface(null, Typeface.NORMAL)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        centerCol.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER_HORIZONTAL })
        row.addView(centerCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val menuBtn = svgView("icons/svg/more_vert.svg", 20, Color.parseColor("#0F0F0F")).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { toggleMenu() }
        }
        row.addView(menuBtn, LinearLayout.LayoutParams(dp(48), appBarH))
        appBar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, totalH))

        progressStrip = View(context).apply {
            setBackgroundColor(Color.parseColor("#FF0000")); visibility = View.GONE
        }
        appBar.addView(progressStrip, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(2)).also { it.gravity = Gravity.BOTTOM })

        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, totalH).also { it.gravity = Gravity.TOP })
        tag = totalH
    }

    private fun loadGlobePlaceholder() {
        try {
            val svg = SVG.getFromAsset(context.assets, "icons/svg/globe.svg")
            val px  = dp(20)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            faviconView.setImageBitmap(bmp)
            faviconView.setColorFilter(Color.parseColor("#606060"))
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() {
        val topMargin = tag as? Int ?: dp(44)
        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false; useWideViewPort = true
                loadWithOverviewMode = true; setSupportZoom(true)
                builtInZoomControls = false; displayZoomControls = false
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    noConnection = false; noConnView.visibility = View.GONE
                    progressStrip.visibility = View.VISIBLE
                    handler.post { loadGlobePlaceholder() }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressStrip.visibility = View.GONE
                    if (url != null && !url.startsWith("about:")) {
                        // Tenta favicon do Google S2 primeiro
                        handler.postDelayed({ tryLoadFavicon(url) }, 300)
                    }
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        noConnection = true; noConnView.visibility = View.VISIBLE
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return true
                    if (freeNavigation || site == null || site.allowedDomain.isEmpty()) return false
                    return !site.isAllowed(url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressStrip.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrEmpty()) { pageTitle = title; titleText.text = shortLabel() }
                }
                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    // WebView icon tem má qualidade — preferimos Google S2
                    // só usa se não conseguirmos carregar via S2
                }
            }
        }
        webView.loadUrl(startUrl)
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = topMargin })
    }

    private fun tryLoadFavicon(pageUrl: String) {
        try {
            val host = android.net.Uri.parse(pageUrl).host ?: return
            val faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain=$host"
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL(faviconUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000; conn.readTimeout = 5000; conn.connect()
                    if (conn.responseCode == 200) {
                        val bmp = BitmapFactory.decodeStream(conn.inputStream)
                        if (bmp != null) handler.post {
                            faviconView.clearColorFilter()
                            faviconView.setImageBitmap(bmp)
                        }
                    } else {
                        // fallback — tenta /favicon.ico directo
                        val ico = java.net.URL("https://$host/favicon.ico").openConnection() as java.net.HttpURLConnection
                        ico.connectTimeout = 4000; ico.readTimeout = 4000
                        ico.setRequestProperty("User-Agent", "Mozilla/5.0")
                        ico.connect()
                        if (ico.responseCode == 200) {
                            val bmp2 = BitmapFactory.decodeStream(ico.inputStream)
                            if (bmp2 != null) handler.post {
                                faviconView.clearColorFilter()
                                faviconView.setImageBitmap(bmp2)
                            }
                        }
                        ico.disconnect()
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun buildNoConnection() {
        val topMargin = tag as? Int ?: dp(44)
        noConnView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE); visibility = View.GONE
            setPadding(dp(32), 0, dp(32), 0)
        }
        val lottie = LottieAnimationView(context).apply {
            try {
                setAnimation("lottie/no_connection.json")
                repeatCount = LottieDrawable.INFINITE; playAnimation()
            } catch (_: Exception) {
                val errorIcon = svgView("icons/svg/error.svg", 64, Color.parseColor("#CCCCCC"))
                noConnView.addView(errorIcon, LinearLayout.LayoutParams(dp(64), dp(64)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL })
            }
        }
        noConnView.addView(lottie, LinearLayout.LayoutParams(dp(180), dp(180)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL })
        noConnView.addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
        noConnView.addView(TextView(context).apply {
            text = "Sem ligação"; setTextColor(Color.parseColor("#0F0F0F"))
            textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        noConnView.addView(View(context), LinearLayout.LayoutParams(0, dp(8)))
        noConnView.addView(TextView(context).apply {
            text = "Verifica a tua ligação à internet."
            setTextColor(Color.parseColor("#606060")); textSize = 14f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        noConnView.addView(View(context), LinearLayout.LayoutParams(0, dp(24)))
        noConnView.addView(TextView(context).apply {
            text = "Tentar novamente"; setTextColor(Color.WHITE)
            textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat(); setColor(Color.parseColor("#0F0F0F"))
            }
            setPadding(dp(24), dp(12), dp(24), dp(12)); gravity = Gravity.CENTER
            setOnClickListener { noConnView.visibility = View.GONE; webView.reload() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        addView(noConnView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = topMargin })
    }

    private fun buildMenuPopup() {
        // Popup nativo com PopupMenu
    }

    private fun toggleMenu() {
        if (menuOpen) { closeMenu(); return }
        menuOpen = true
        val anchor = findViewWithTag<View>("menu_anchor") ?: this
        val popup  = android.widget.PopupMenu(context, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Recarregar")
        popup.menu.add(0, 2, 1, "Copiar link")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { webView.reload() }
                2 -> {
                    val url = webView.url ?: startUrl
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("url", url))
                }
            }
            menuOpen = false
            true
        }
        popup.setOnDismissListener { menuOpen = false }
        popup.show()
    }

    private fun closeMenu() { menuOpen = false }

    private fun dismiss() { activity.removeContentOverlay(this) }

    private fun shortLabel(): String {
        val t     = pageTitle.ifEmpty { site?.name ?: "" }
        val clean = t.replace(Regex("""\s*[|\-–—]\s*.*"""), "").trim()
        return if (clean.length > 16) "${clean.substring(0, 16)}…" else clean
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}