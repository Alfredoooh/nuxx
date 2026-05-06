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
import com.xcode.app.editor.IconPaths
import com.xcode.app.editor.XCodeIcon

class PreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isDark = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDark = intent.getBooleanExtra("isDark", true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars     = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        val bg      = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
        val toolbar = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        val txtCol  = if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        // ── Toolbar ───────────────────────────────────────────────────────
        val toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(toolbar)
        }

        val btnBack = makeTbBtn(IconPaths.CHEVRON_LEFT, txtCol) { finish() }
        toolbarRow.addView(btnBack, LinearLayout.LayoutParams(dp(44), dp(44)))

        toolbarRow.addView(TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Preview"
            textSize = 13f
            setTextColor(txtCol)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnRefresh = makeTbBtn(IconPaths.RELOAD, txtCol) { webView.reload() }
        toolbarRow.addView(btnRefresh, LinearLayout.LayoutParams(dp(44), dp(44)))

        root.addView(toolbarRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        ))

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })

        // ── WebView ───────────────────────────────────────────────────────
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled    = true
                domStorageEnabled    = true
                allowFileAccess      = true
                loadWithOverviewMode = true
                useWideViewPort      = true
                mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportZoom(true)
                builtInZoomControls  = true
                displayZoomControls  = false
                cacheMode            = WebSettings.LOAD_NO_CACHE
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
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = false
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
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

        // Load content
        val html = intent.getStringExtra("html")
        val url  = intent.getStringExtra("url")
        when {
            html != null -> webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
            url  != null -> webView.loadUrl(url)
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
        val icon = XCodeIcon(this, svgPath, tintColor, dp(18))
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