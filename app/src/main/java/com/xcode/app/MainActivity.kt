package com.xcode.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xcode.app.services.XCodeKeepAliveService

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var insetsController: WindowInsetsControllerCompat

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
        setContentView(webView)

        // Aplica os insets ao WebView para não ficar por baixo das system bars
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.settings.apply {
            javaScriptEnabled             = true
            domStorageEnabled             = true
            databaseEnabled               = true
            allowFileAccess               = true
            allowContentAccess            = true
            loadWithOverviewMode          = true
            useWideViewPort               = true
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode              = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(ThemeBridge(), "XCodeTheme")

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
            val isDark = theme == "dark"
            runOnUiThread {
                insetsController.isAppearanceLightStatusBars     = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
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