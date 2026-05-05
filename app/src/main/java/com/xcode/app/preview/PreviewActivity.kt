package com.xcode.app.preview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isDark = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDark = intent.getBooleanExtra("isDark", true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE)
        }

        // ── Toolbar ───────────────────────────────────────────────────────
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3"))
            setPadding(0, 0, 0, 0)
        }

        val btnBack = makeToolbarBtn("←") { finish() }
        val titleView = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Preview"
            textSize = 13f
            setTextColor(if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333"))
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnRefresh = makeToolbarBtn("↺") { webView.reload() }

        toolbar.addView(btnBack)
        toolbar.addView(titleView)
        toolbar.addView(btnRefresh)
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        ))

        // Separator
        val sep = View(this).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
        }
        root.addView(sep, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        // ── WebView ───────────────────────────────────────────────────────
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            setBackgroundColor(if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    android.util.Log.d("Preview", "${msg.message()} [${msg.lineNumber()}]")
                    return true
                }
            }
            webViewClient = WebViewClient()
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val frame = android.widget.FrameLayout(this)
        frame.addView(root, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(frame)

        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ── Load content ──────────────────────────────────────────────────
        val html = intent.getStringExtra("html")
        val url = intent.getStringExtra("url")
        when {
            html != null -> webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
            url != null -> webView.loadUrl(url)
            else -> webView.loadData(
                "<body style='font-family:sans-serif;color:#888;padding:32px'><h2>Sem conteúdo</h2></body>",
                "text/html", "UTF-8"
            )
        }
    }

    private fun makeToolbarBtn(icon: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = icon
            textSize = 18f
            setTextColor(if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333"))
            gravity = android.view.Gravity.CENTER
            val sz = dp(44)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}