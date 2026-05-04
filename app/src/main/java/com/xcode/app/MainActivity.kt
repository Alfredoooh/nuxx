package com.xcode.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
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

        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            databaseEnabled      = true
            allowFileAccess      = true
            allowContentAccess   = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(false)
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(ThemeBridge(), "XCodeTheme")

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
        }

        webView.loadUrl("file:///android_asset/editor.html")
    }

    inner class ThemeBridge {
        @JavascriptInterface
        fun onThemeChanged(theme: String) {
            val isDark = theme == "dark"
            runOnUiThread {
                insetsController.isAppearanceLightStatusBars   = !isDark
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