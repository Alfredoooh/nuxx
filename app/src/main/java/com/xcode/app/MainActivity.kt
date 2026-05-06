// MainActivity.kt
package com.xcode.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xcode.app.services.XCodeKeepAliveService

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var insetsController: WindowInsetsControllerCompat
    private var currentTheme = "dark"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        XCodeKeepAliveService.start(this)

        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this)

        root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.settings.apply {
            javaScriptEnabled                     = true
            domStorageEnabled                     = true
            databaseEnabled                       = true
            allowFileAccess                       = true
            allowContentAccess                    = true
            loadWithOverviewMode                  = true
            useWideViewPort                       = true
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture      = false
            mixedContentMode                      = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(ThemeBridge(), "XCodeTheme")
        webView.addJavascriptInterface(PreviewBridge(), "XCodePreview")
        webView.addJavascriptInterface(DialogBridge(), "XCodeDialog")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                android.util.Log.d("WebView", "${msg.message()} [${msg.sourceId()}:${msg.lineNumber()}]")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("""
                    (function(){
                        if(window.__xcodeThemeSetup) return;
                        window.__xcodeThemeSetup = true;
                        var obs = new MutationObserver(function(){
                            var t = document.documentElement.getAttribute('data-theme') || 'dark';
                            if(window.XCodeTheme) XCodeTheme.onThemeChanged(t);
                        });
                        obs.observe(document.documentElement, {attributes:true, attributeFilter:['data-theme']});
                        var t = document.documentElement.getAttribute('data-theme') || 'dark';
                        if(window.XCodeTheme) XCodeTheme.onThemeChanged(t);
                    })();
                """.trimIndent(), null)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.loadUrl("file:///android_asset/editor.html")
    }

    inner class ThemeBridge {
        @JavascriptInterface
        fun onThemeChanged(theme: String) {
            currentTheme = theme
            val isDark = theme == "dark"
            runOnUiThread {
                root.setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)
                insetsController.isAppearanceLightStatusBars     = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    inner class PreviewBridge {
        @JavascriptInterface
        fun openHtmlPreview(title: String, html: String) {
            runOnUiThread {
                val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                    putExtra("title", title)
                    putExtra("html", html)
                    putExtra("isDark", currentTheme == "dark")
                }
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openUrlPreview(title: String, url: String) {
            runOnUiThread {
                val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                    putExtra("title", title)
                    putExtra("url", url)
                    putExtra("isDark", currentTheme == "dark")
                }
                startActivity(intent)
            }
        }
    }

    inner class DialogBridge {
        @JavascriptInterface
        fun alert(message: String) {
            runOnUiThread {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        @JavascriptInterface
        fun confirm(message: String) {
            runOnUiThread {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("Confirmar") { _, _ ->
                        webView.evaluateJavascript("confirmResult(true)", null)
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        webView.evaluateJavascript("confirmResult(false)", null)
                    }
                    .setOnCancelListener {
                        webView.evaluateJavascript("confirmResult(false)", null)
                    }
                    .show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}