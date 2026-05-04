package com.xcode.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var isDark = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDark = intent.getBooleanExtra("isDark", true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE)
        }

        // ── Toolbar ───────────────────────────────────────────────────────────
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3"))
            setPadding(0, 0, 0, 0)
        }

        val btnBack = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)
            contentDescription = "Voltar"
            setColorFilter(if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333"))
            setOnClickListener { finish() }
        }

        val titleView = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Preview"
            textSize = 13f
            setTextColor(if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333"))
            setPadding(8, 0, 8, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }

        val btnRefresh = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_rotate)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)
            contentDescription = "Recarregar"
            setColorFilter(if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333"))
            setOnClickListener { webView.reload() }
        }

        toolbar.addView(btnBack, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        toolbar.addView(titleView, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        toolbar.addView(btnRefresh, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ── WebView ───────────────────────────────────────────────────────────
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
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

        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(frame)

        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ── Carregar conteúdo ─────────────────────────────────────────────────
        val htmlContent = intent.getStringExtra("html")
        val url = intent.getStringExtra("url")

        when {
            htmlContent != null -> webView.loadDataWithBaseURL(
                "about:blank", htmlContent, "text/html", "UTF-8", null
            )
            url != null -> webView.loadUrl(url)
            else -> webView.loadData(
                "<h2 style='font-family:sans-serif;color:#888;padding:32px'>Sem conteúdo para visualizar</h2>",
                "text/html", "UTF-8"
            )
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}