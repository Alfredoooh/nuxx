package com.xcode.app.preview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xcode.app.editor.SvgIconView

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

        val bg = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
        val toolbar = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        val textColor = if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // ── Toolbar ───────────────────────────────────────────────────────
        val toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(toolbar)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            )
        }

        // Back button — chevron left
        val btnBack = makeTbBtn(
            "M11.354 1.646a.5.5 0 0 1 0 .708L5.707 8l5.647 5.646a.5.5 0 0 1-.708.708l-6-6a.5.5 0 0 1 0-.708l6-6a.5.5 0 0 1 .708 0z",
            textColor
        ) { finish() }
        toolbarRow.addView(btnBack, LinearLayout.LayoutParams(dp(44), dp(44)))

        val titleView = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Preview"
            textSize = 13f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        toolbarRow.addView(titleView)

        // Refresh — arrow clockwise
        val btnRefresh = makeTbBtn(
            "M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9zM8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z",
            textColor
        ) { webView.reload() }
        toolbarRow.addView(btnRefresh, LinearLayout.LayoutParams(dp(44), dp(44)))

        root.addView(toolbarRow)

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })

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
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            setBackgroundColor(bg)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    android.util.Log.d("Preview", "[${msg.lineNumber()}] ${msg.message()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(frame)

        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Load
        val html = intent.getStringExtra("html")
        val url = intent.getStringExtra("url")
        when {
            html != null -> webView.loadDataWithBaseURL(
                "about:blank", html, "text/html", "UTF-8", null
            )
            url != null -> webView.loadUrl(url)
            else -> webView.loadData(
                "<body style='font-family:monospace;padding:32px;background:${if (isDark) "#1e1e1e" else "#fff"};color:${if (isDark) "#cccccc" else "#333"}'><p>Sem conteudo para visualizar.</p></body>",
                "text/html", "UTF-8"
            )
        }
    }

    private fun makeTbBtn(svgPath: String, tintColor: Int, onClick: () -> Unit): FrameLayout {
        val frame = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
        }
        val icon = SvgIconView(this, svgPath, tintColor, dp(18))
        frame.addView(icon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        return frame
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}