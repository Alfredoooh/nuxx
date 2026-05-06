package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nuxx.app.MainActivity
import kotlin.math.abs
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("ViewConstructor", "SetJavaScriptEnabled", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val playerWeb   = WebView(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val viewKeys    = mutableListOf<String>()
    private var currentIdx  = 0
    private var isMuted     = false
    private var isLiked     = false
    private var isFetching  = false
    private var currentPage = 1
    private val TARGET_KEYS = 500
    private var playerReady = false

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = LayoutParams.WRAP_CONTENT

    private val playerFrame  = FrameLayout(context)
    private val overlayRight = LinearLayout(context)
    private val infoBottom   = LinearLayout(context)
    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()

    private val btnLike  = buildSvgButton(SvgIcon.HEART_OUTLINE)
    private val tvLikes  = buildLabel("0")
    private val btnMute  = buildSvgButton(SvgIcon.VOLUME_ON)
    private val btnShare = buildSvgButton(SvgIcon.SHARE)
    private val tvAuthor = buildBoldLabel("")
    private val tvTitle  = buildSmallLabel("")

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var isDragging  = false
    private val SWIPE_THRESH = 70f

    init {
        setBackgroundColor(Color.BLACK)
        setupPlayerWeb()
        buildUI()
        checkNetAndLoad()
    }

    private fun buildUI() {
        playerFrame.addView(playerWeb, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        overlayRight.orientation = LinearLayout.VERTICAL
        overlayRight.gravity     = Gravity.CENTER_HORIZONTAL
        overlayRight.setPadding(0, 0, dp(14), dp(80))

        val likeWrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(20))
        }
        likeWrap.addView(btnLike,  lp(dp(48), dp(48)))
        likeWrap.addView(tvLikes,  lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(4) })
        overlayRight.addView(likeWrap)

        overlayRight.addView(btnMute,  lp(dp(48), dp(48)).also {
            it.bottomMargin = dp(20)
        })
        overlayRight.addView(btnShare, lp(dp(48), dp(48)))

        addView(overlayRight, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.BOTTOM
        })

        infoBottom.orientation = LinearLayout.VERTICAL
        infoBottom.setPadding(dp(14), 0, dp(80), dp(32))
        infoBottom.addView(tvAuthor, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) })
        infoBottom.addView(tvTitle,  lp(WRAP_CONTENT, WRAP_CONTENT))
        addView(infoBottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(loadingView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,   lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility = GONE

        playerFrame.setOnTouchListener  { _, e -> handleTouch(e) }
        overlayRight.setOnTouchListener { _, e -> handleTouch(e) }
        infoBottom.setOnTouchListener   { _, e -> handleTouch(e) }

        btnLike.setOnClickListener  { toggleLike() }
        btnMute.setOnClickListener  { toggleMute() }
        btnShare.setOnClickListener { showShareDialog() }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) {
            showNoNet()
            mainHandler.postDelayed({
                if (isOnline()) { hideNoNet(); startFetching() } else checkNetAndLoad()
            }, 3000)
        } else {
            startFetching()
        }
    }

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility   = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    private fun startFetching() {
        currentPage = 1
        viewKeys.clear()
        fetchPage(currentPage)
    }

    private fun fetchPage(page: Int) {
        if (isFetching) return
        isFetching = true
        Thread {
            try {
                val url  = URL("https://www.pornhub.com/shorties?page=$page")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout    = 10000
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                conn.setRequestProperty("Cookie",
                    "age_verified=1; accessAgeDisclaimerPH=1; il=1; platform=pc; " +
                    "cookiesAccepted=1; cookiesBannerSeen=1; cookieConsent=3")
                val html = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val regex = Regex("viewkey=([a-zA-Z0-9]+)")
                val found = regex.findAll(html)
                    .map { it.groupValues[1] }
                    .filter { it.length > 8 }
                    .distinct()
                    .toList()

                mainHandler.post {
                    isFetching = false
                    val newKeys = found.filter { it !in viewKeys }
                    viewKeys.addAll(newKeys)

                    if (viewKeys.size < TARGET_KEYS && newKeys.isNotEmpty()) {
                        currentPage++
                        fetchPage(currentPage)
                    }

                    if (viewKeys.size >= 5 && currentIdx == 0 && !playerReady) {
                        playerReady = true
                        loadVideo(0, animate = false)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isFetching = false
                    if (viewKeys.isEmpty()) {
                        mainHandler.postDelayed({ fetchPage(currentPage) }, 3000)
                    }
                }
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayerWeb() {
        playerWeb.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort                  = true
            loadWithOverviewMode             = true
            setSupportZoom(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        playerWeb.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(playerWeb, true)
        listOf("age_verified=1","accessAgeDisclaimerPH=1","il=1",
               "platform=pc","cookieConsent=3","cookiesAccepted=1").forEach {
            cm.setCookie(".pornhub.com", "$it; path=/; domain=.pornhub.com")
        }
        cm.flush()

        playerWeb.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val u = request.url.toString()
                val blocked = listOf(
                    "trafficjunky", "contentabc", "adroll",
                    "ads2.", "cdn1.ads", "doubleclick",
                    "googlesyndication", "adnxs", "pubmatic"
                )
                if (blocked.any { u.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8",
                        "".byteInputStream())
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectStyles(view)
                mainHandler.postDelayed({
                    loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                        loadingView.visibility = GONE
                        loadingView.alpha      = 1f
                    }.start()
                }, 1800)
            }

            override fun shouldOverrideUrlLoading(
                v: WebView?, r: WebResourceRequest?
            ): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com/embed") &&
                       !u.contains("phncdn.com")
            }
        }
    }

    private fun injectStyles(view: WebView?) {
        view?.evaluateJavascript("""
        (function(){
            var s = document.createElement('style');
            s.textContent = `
                .top-controls, .bottom-controls, .middle-controls,
                .topBar, .topBarBackground, .contextMenu,
                .copyMenu, .settings-menu-wrapper,
                .nextVideoOverlay, .upNext, .gridMenu,
                .slideout-outer-wrapper, .like-dislike-fav,
                .shortcuts, .watchHD, .unmute,
                .adRollContainer, .adRollEventCatcher,
                .adRollSkipButton, .adRollCTA, .adRollLink,
                .adRollTitleText, .adRollTitle,
                .overlayContainer > *:not(.ccContainer),
                [class*="advert"], [id*="ad_"],
                iframe[src*="trafficjunky"],
                iframe[src*="contentabc"] {
                    display: none !important;
                    visibility: hidden !important;
                    pointer-events: none !important;
                }
                body, html { background: #000 !important; overflow: hidden !important; }
                .videoWrapper, #player { width: 100vw !important; height: 100vh !important; }
                video {
                    width: 100% !important; height: 100% !important;
                    object-fit: cover !important;
                }
            `;
            document.head.appendChild(s);

            var obs = new MutationObserver(function(){
                document.querySelectorAll(
                    '.adRollContainer,.adRollEventCatcher,[id*="ad_"],' +
                    '[class*="advert"],[class*="Popup"],.preroll'
                ).forEach(function(el){ el.remove(); });
            });
            obs.observe(document.body, { childList: true, subtree: true });

            var t = setInterval(function(){
                var v = document.querySelector('video');
                if(v){
                    v.muted = ${isMuted};
                    v.play().catch(function(){});
                    clearInterval(t);
                }
            }, 400);
        })();
        """.trimIndent(), null)
    }

    private fun loadVideo(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= viewKeys.size) return
        val vk = viewKeys[index]

        tvAuthor.text = ""
        tvTitle.text  = "Shorty #${index + 1}"
        isLiked = false
        updateLikeIcon()
        updateMuteIcon()

        loadingView.visibility = VISIBLE
        loadingView.alpha      = 1f

        val embedUrl = "https://www.pornhub.com/embed/$vk"

        if (animate) {
            animateTransition { playerWeb.loadUrl(embedUrl) }
        } else {
            playerWeb.loadUrl(embedUrl)
        }

        if (index >= viewKeys.size - 50 && !isFetching && viewKeys.size < TARGET_KEYS) {
            currentPage++
            fetchPage(currentPage)
        }
    }

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = e.y; touchStartX = e.x; isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - touchStartY
                val dx = e.x - touchStartX
                if (!isDragging && abs(dy) > 12 && abs(dy) > abs(dx)) isDragging = true
                if (isDragging) playerFrame.translationY = dy * 0.25f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = e.y - touchStartY
                if (isDragging) {
                    when {
                        dy < -SWIPE_THRESH && currentIdx < viewKeys.size - 1 ->
                            snapBack { currentIdx++; loadVideo(currentIdx) }
                        dy > SWIPE_THRESH && currentIdx > 0 ->
                            snapBack { currentIdx--; loadVideo(currentIdx) }
                        else -> playerFrame.animate().translationY(0f).setDuration(200)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    isDragging = false
                }
            }
        }
        return true
    }

    private fun snapBack(onEnd: () -> Unit) {
        playerFrame.animate().translationY(0f).setDuration(120)
            .setInterpolator(DecelerateInterpolator()).withEndAction(onEnd).start()
    }

    private fun animateTransition(onMid: () -> Unit) {
        playerFrame.animate()
            .translationY(-height.toFloat() * 0.04f).alpha(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onMid()
                playerFrame.translationY = height.toFloat() * 0.06f
                playerFrame.alpha        = 0f
                playerFrame.animate()
                    .translationY(0f).alpha(1f).setDuration(240)
                    .setInterpolator(FastOutSlowInInterpolator()).start()
            }.start()
    }

    private fun toggleLike() {
        isLiked = !isLiked
        updateLikeIcon()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        updateMuteIcon()
        playerWeb.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.muted=${isMuted};})();", null
        )
    }

    private fun showShareDialog() {
        if (currentIdx >= viewKeys.size) return
        val vk    = viewKeys[currentIdx]
        val url   = "https://www.pornhub.com/view_video.php?viewkey=$vk"
        val embed = "<iframe src=\"https://www.pornhub.com/embed/$vk\" " +
            "frameborder=\"0\" width=\"560\" height=\"315\" scrolling=\"no\" allowfullscreen></iframe>"
        android.app.AlertDialog.Builder(context)
            .setTitle("Partilhar vídeo")
            .setItems(arrayOf("Copiar link", "Copiar código embed", "Partilhar via...")) { _, which ->
                when (which) {
                    0 -> copyToClipboard("URL", url)
                    1 -> copyToClipboard("Embed", embed)
                    2 -> context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
                            }, "Partilhar via"
                        )
                    )
                }
            }.create().also {
                it.window?.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
                it.show()
            }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
    }

    enum class SvgIcon { HEART_OUTLINE, HEART_FILLED, VOLUME_ON, VOLUME_OFF, SHARE }

    private fun buildSvgButton(icon: SvgIcon): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = buildCircleBg()
        }
        renderIcon(iv, icon, Color.WHITE)
        return iv
    }

    private fun buildCircleBg(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.ShapeDrawable(
            android.graphics.drawable.shapes.OvalShape()
        ).apply {
            paint.color = Color.argb(80, 0, 0, 0)
        }
    }

    private fun renderIcon(iv: ImageView, icon: SvgIcon, tint: Int) {
        val size = dp(48)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = tint
            style       = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap   = Paint.Cap.ROUND
            strokeJoin  = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint; style = Paint.Style.FILL
        }
        val cx = size / 2f; val cy = size / 2f

        when (icon) {
            SvgIcon.HEART_OUTLINE, SvgIcon.HEART_FILLED -> {
                val r = size * 0.22f
                val path = Path().apply {
                    moveTo(cx, cy + r * 1.5f)
                    cubicTo(cx - r * 2.8f, cy + r * 0.2f,
                            cx - r * 2.8f, cy - r * 1.8f,
                            cx,            cy - r * 0.5f)
                    cubicTo(cx + r * 2.8f, cy - r * 1.8f,
                            cx + r * 2.8f, cy + r * 0.2f,
                            cx,            cy + r * 1.5f)
                    close()
                }
                if (icon == SvgIcon.HEART_FILLED) {
                    c.drawPath(path, fill)
                } else {
                    c.drawPath(path, stroke)
                }
            }

            SvgIcon.VOLUME_ON -> {
                val s = size * 0.14f
                val spk = Path().apply {
                    moveTo(cx - s * 2f, cy - s)
                    lineTo(cx - s * 0.4f, cy - s)
                    lineTo(cx + s * 1.0f, cy - s * 2.2f)
                    lineTo(cx + s * 1.0f, cy + s * 2.2f)
                    lineTo(cx - s * 0.4f, cy + s)
                    lineTo(cx - s * 2f, cy + s)
                    close()
                }
                c.drawPath(spk, fill)
                stroke.strokeWidth = dp(2).toFloat()
                val r1 = s * 2.2f; val r2 = s * 3.4f
                val ox = cx + s * 0.6f
                c.drawArc(ox - r1, cy - r1, ox + r1, cy + r1, -50f, 100f, false, stroke)
                c.drawArc(ox - r2, cy - r2, ox + r2, cy + r2, -50f, 100f, false, stroke)
            }

            SvgIcon.VOLUME_OFF -> {
                val s = size * 0.14f
                val spk = Path().apply {
                    moveTo(cx - s * 2f, cy - s)
                    lineTo(cx - s * 0.4f, cy - s)
                    lineTo(cx + s * 1.0f, cy - s * 2.2f)
                    lineTo(cx + s * 1.0f, cy + s * 2.2f)
                    lineTo(cx - s * 0.4f, cy + s)
                    lineTo(cx - s * 2f, cy + s)
                    close()
                }
                c.drawPath(spk, fill)
                stroke.strokeWidth = dp(2.5f)
                c.drawLine(cx + s * 1.6f, cy - s * 1.4f, cx + s * 3.0f, cy + s * 1.4f, stroke)
                c.drawLine(cx + s * 3.0f, cy - s * 1.4f, cx + s * 1.6f, cy + s * 1.4f, stroke)
            }

            SvgIcon.SHARE -> {
                val s = size * 0.16f
                stroke.strokeWidth = dp(2).toFloat()
                val box = Path().apply {
                    moveTo(cx - s * 1.8f, cy + s * 0.4f)
                    lineTo(cx - s * 1.8f, cy + s * 2.0f)
                    lineTo(cx + s * 1.8f, cy + s * 2.0f)
                    lineTo(cx + s * 1.8f, cy + s * 0.4f)
                }
                c.drawPath(box, stroke)
                c.drawLine(cx, cy + s * 1.2f, cx, cy - s * 1.2f, stroke)
                val arrow = Path().apply {
                    moveTo(cx - s * 1.1f, cy - s * 0.2f)
                    lineTo(cx, cy - s * 1.4f)
                    lineTo(cx + s * 1.1f, cy - s * 0.2f)
                }
                c.drawPath(arrow, stroke)
            }
        }
        iv.setImageBitmap(bmp)
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun updateLikeIcon() {
        val color = if (isLiked) Color.parseColor("#FF4D4D") else Color.WHITE
        val icon  = if (isLiked) SvgIcon.HEART_FILLED else SvgIcon.HEART_OUTLINE
        renderIcon(btnLike, icon, color)
    }

    private fun updateMuteIcon() {
        renderIcon(btnMute, if (isMuted) SvgIcon.VOLUME_OFF else SvgIcon.VOLUME_ON, Color.WHITE)
    }

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx * 0.7f
                c.drawArc(cx - r, cy - r, cx + r, cy + r, angle, 260f, false, paint)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(52), dp(52)).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        ll.addView(TextView(context).apply {
            text = "Sem ligação"; textSize = 15f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER
        }, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(12) })
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f
            setTextColor(Color.parseColor("#FF6600")); gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn)
        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    private fun buildBoldLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }

    private fun buildSmallLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 13f; maxLines = 2
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }

    private fun buildLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setShadowLayer(6f, 1f, 1f, Color.BLACK)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() { playerWeb.destroy() }
}