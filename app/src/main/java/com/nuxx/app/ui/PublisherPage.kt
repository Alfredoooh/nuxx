// ui/PublisherPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.graphics.*
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.*
import com.doction.webviewapp.MainActivity

@SuppressLint("ViewConstructor", "SetJavaScriptEnabled")
class PublisherPage(
    private val activity: MainActivity,
    private val publisherKey:   String,
    private val publisherName:  String,
    private val publisherThumb: String,
    private val publisherUrl:   String,
) : FrameLayout(activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val webView     = WebView(context)
    private val headerView  = buildHeader()
    private val loadingView = buildSpinner()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        setupWebView()
        loadPublisher()
    }

    private fun buildUI() {
        // WebView abaixo do header
        val wlp = LayoutParams(MATCH_PARENT, MATCH_PARENT).also {
            it.topMargin = activity.statusBarHeight + dp(60)
        }
        addView(webView, wlp)

        // Header nativo
        addView(headerView, LayoutParams(MATCH_PARENT, activity.statusBarHeight + dp(60)))

        // Loading
        addView(loadingView, LayoutParams(MATCH_PARENT, MATCH_PARENT).also {
            it.topMargin = activity.statusBarHeight + dp(60)
        })
    }

    private fun buildHeader(): FrameLayout {
        val frame = FrameLayout(context)
        frame.setBackgroundColor(Color.BLACK)

        // Botão voltar
        val back = TextView(context).apply {
            text      = "←"
            textSize  = 22f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        back.setOnClickListener { activity.removeContentOverlay(this@PublisherPage) }

        // Avatar
        val avatar = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View?, o: Outline?) { o?.setOval(0, 0, v!!.width, v.height) }
            }
        }
        loadAvatarAsync(publisherThumb, avatar)

        // Nome
        val name = TextView(context).apply {
            text     = publisherName
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER_VERTICAL
        }

        val avlp = FrameLayout.LayoutParams(dp(36), dp(36)).also {
            it.gravity   = Gravity.CENTER_VERTICAL
            it.leftMargin  = dp(52)
        }
        val nlp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity   = Gravity.CENTER_VERTICAL
            it.leftMargin  = dp(100)
        }
        val blp = FrameLayout.LayoutParams(dp(48), MATCH_PARENT).also {
            it.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val flp = FrameLayout.LayoutParams(MATCH_PARENT, dp(60)).also {
            it.topMargin = activity.statusBarHeight
        }
        frame.addView(back,   blp)
        frame.addView(avatar, avlp)
        frame.addView(name,   nlp)

        // Linha separadora
        val line = View(context).apply { setBackgroundColor(Color.parseColor("#222222")) }
        val llp  = FrameLayout.LayoutParams(MATCH_PARENT, dp(1)).also { it.gravity = Gravity.BOTTOM }
        frame.addView(line, llp)

        return frame
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled              = true
            domStorageEnabled              = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort                = true
            loadWithOverviewMode           = true
            setSupportZoom(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectCSS(view)
                loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                    loadingView.visibility = GONE
                    loadingView.alpha = 1f
                }.start()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com")
            }
        }
    }

    private fun injectCSS(view: WebView?) {
        val css = """
          header,footer,.siteMenu,.topMenu,.menuContainer,.headerLogo,
          .rightMenuSection,.joinNowWrapper,.externalLinkButton,
          .actionScribe,.flag.topMenuFlag,.followButton,.subscribeButton,
          .addFriendButton,.blockButton,.reportButton,.sendMessageButton,
          .fanClubSection,.tippingSection,.profileBannerSection,
          .profileAwardSection,.profileSocialLinks,
          .profileBio .bioActions { display:none!important; }
          body { background:#111!important; color:#fff!important; }
          * { -webkit-user-select:none!important; user-select:none!important;
              -webkit-tap-highlight-color:transparent!important; }
          ::-webkit-scrollbar { display:none!important; }
          a { color:#FF6600!important; }
          .pcVideoListItem .wrap,.videoBlock { border-radius:8px; overflow:hidden; }
        """.trimIndent().replace("\n", " ")

        view?.evaluateJavascript("""
            (function(){
              var s=document.getElementById('_px_pub');
              if(!s){s=document.createElement('style');s.id='_px_pub';document.head.appendChild(s);}
              s.textContent='$css';
            })();
        """.trimIndent(), null)
    }

    private fun loadPublisher() {
        val url = when {
            publisherUrl.isNotEmpty() -> publisherUrl
            publisherKey.isNotEmpty() -> "https://www.pornhub.com/model/$publisherKey"
            else -> return
        }
        webView.loadUrl(url)
    }

    private fun buildSpinner(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val spinner = object : android.view.View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run   = object : Runnable {
                override fun run() { angle = (angle + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width/2f; val cy = height/2f; val r = cx*0.7f
                c.drawArc(cx-r, cy-r, cx+r, cy+r, angle, 260f, false, paint)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(52), dp(52)).also { it.gravity = Gravity.CENTER })
        return frame
    }

    private fun loadAvatarAsync(url: String, iv: ImageView) {
        if (url.isEmpty()) return
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                mainHandler.post { iv.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = LayoutParams.WRAP_CONTENT

    fun onBackPressed() = activity.removeContentOverlay(this)
}