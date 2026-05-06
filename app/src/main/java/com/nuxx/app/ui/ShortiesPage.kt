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

    private val playerWeb    = WebView(context)
    private val mainHandler  = Handler(Looper.getMainLooper())
    private val viewKeys     = mutableListOf<String>()
    private var currentIdx   = 0
    private var isMuted      = false
    private var isLiked      = false
    private var isFetching   = false
    private var currentPage  = 1
    private val TARGET_KEYS  = 500

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = LayoutParams.WRAP_CONTENT

    private val playerFrame  = FrameLayout(context)
    private val overlayRight = LinearLayout(context)
    private val infoBottom   = LinearLayout(context)
    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()

    private val btnLike  = buildIconButton(ICON_HEART_OUTLINE, Color.WHITE)
    private val tvLikes  = buildLabel("0")
    private val btnMute  = buildIconButton(ICON_VOLUME_ON, Color.WHITE)
    private val btnShare = buildIconButton(ICON_SHARE, Color.WHITE)
    private val tvAuthor = buildBoldLabel("")
    private val tvTitle  = buildSmallLabel("")

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var isDragging  = false
    private val SWIPE_THRESH = 70f

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    private fun buildUI() {
        playerFrame.addView(playerWeb, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        overlayRight.orientation = LinearLayout.VERTICAL
        overlayRight.gravity     = Gravity.CENTER_HORIZONTAL
        overlayRight.setPadding(0, 0, dp(16), dp(140))
        overlayRight.addView(btnLike,  lp(dp(44), dp(44)))
        overlayRight.addView(tvLikes,  lp(WRAP_CONTENT, WRAP_CONTENT).also {
            it.topMargin = dp(2); it.bottomMargin = dp(18)
        })
        overlayRight.addView(btnMute,  lp(dp(44), dp(44)).also { it.bottomMargin = dp(18) })
        overlayRight.addView(btnShare, lp(dp(44), dp(44)))

        addView(overlayRight, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })

        infoBottom.orientation = LinearLayout.VERTICAL
        infoBottom.setPadding(dp(14), 0, dp(70), dp(110))
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

    // ── Rede ──────────────────────────────────────────────────────────────────

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

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    // ── Fetch viewkeys via HTTP ───────────────────────────────────────────────

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
                val url = URL("https://www.pornhub.com/shorties?page=$page")
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

                // Extrair viewkeys únicos
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
                        // Continua a buscar mais páginas
                        currentPage++
                        fetchPage(currentPage)
                    }

                    // Inicia player assim que tiver pelo menos 5 vídeos
                    if (viewKeys.size >= 5 && currentIdx == 0 && playerWeb.url == null) {
                        setupPlayerWeb()
                        loadVideo(0, animate = false)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isFetching = false
                    // Tenta novamente após 3s se falhou
                    if (viewKeys.isEmpty()) {
                        mainHandler.postDelayed({ fetchPage(currentPage) }, 3000)
                    }
                }
            }
        }.start()
    }

    // ── Player WebView ────────────────────────────────────────────────────────

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
            override fun onPageFinished(view: WebView?, url: String?) {
                injectAdBlocker(view)
                mainHandler.postDelayed({
                    loadingView.animate().alpha(0f).setDuration(300).withEndAction {
                        loadingView.visibility = GONE
                        loadingView.alpha = 1f
                    }.start()
                }, 2000)
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com/embed") &&
                       !u.contains("phncdn.com")
            }
        }
    }

    private fun injectAdBlocker(view: WebView?) {
        view?.evaluateJavascript("""
        (function(){
            // CSS: esconder anúncios
            var s = document.createElement('style');
            s.textContent = `
                .ad-container, .ad-wrap, .advert, [id*="ad_"],
                [class*="advert"], [class*="sponsor"], [class*="banner"],
                .js-singleProductBannerWrapper, .centerPipeAd,
                .poppingAd, [class*="Popup"], [class*="popup"],
                .nk-video-ad, .video-ad-ui, .preroll,
                iframe[src*="trafficjunky"], iframe[src*="contentabc"],
                div[id*="mgp_"], div[class*="mgp_"] {
                    display: none !important;
                    visibility: hidden !important;
                    pointer-events: none !important;
                }
                video { width: 100% !important; height: 100% !important; }
            `;
            document.head.appendChild(s);

            // JS: remover elementos de anúncio dinamicamente
            var obs = new MutationObserver(function(){
                document.querySelectorAll(
                    '[id*="ad_"],[class*="advert"],[class*="sponsor"],' +
                    '[class*="popup"],[class*="Popup"],.preroll,.nk-video-ad'
                ).forEach(function(el){ el.remove(); });
            });
            obs.observe(document.body, { childList: true, subtree: true });

            // Auto-play e mute conforme estado
            var tryPlay = setInterval(function(){
                var v = document.querySelector('video');
                if(v){
                    v.muted = ${isMuted};
                    v.play().catch(function(){});
                    clearInterval(tryPlay);
                }
            }, 500);
        })();
        """.trimIndent(), null)
    }

    private fun loadVideo(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= viewKeys.size) return
        val vk = viewKeys[index]

        tvAuthor.text = ""
        tvTitle.text  = "Shorty #${index + 1}"
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

        // Pré-carregar mais vídeos quando faltar 50
        if (index >= viewKeys.size - 50 && !isFetching && viewKeys.size < TARGET_KEYS) {
            currentPage++
            fetchPage(currentPage)
        }
    }

    // ── Touch / Swipe ─────────────────────────────────────────────────────────

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

    // ── Ações dos botões ──────────────────────────────────────────────────────

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
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("Partilhar vídeo")
            .setItems(arrayOf("Copiar link", "Copiar código embed", "Partilhar via...")) { _, which ->
                when (which) {
                    0 -> copyToClipboard("URL do vídeo", url)
                    1 -> copyToClipboard("Código embed", embed)
                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "Partilhar via"))
                    }
                }
            }.create()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.parseColor("#1A1A1A")))
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
    }

    // ── Ícones ────────────────────────────────────────────────────────────────

    private fun updateLikeIcon() {
        val color = if (isLiked) Color.parseColor("#FF4D4D") else Color.WHITE
        val icon  = if (isLiked) ICON_HEART_FILLED else ICON_HEART_OUTLINE
        refreshIconButton(btnLike, icon, color)
    }

    private fun updateMuteIcon() {
        refreshIconButton(btnMute, if (isMuted) ICON_VOLUME_OFF else ICON_VOLUME_ON, Color.WHITE)
    }

    private fun buildIconButton(iconType: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.TRANSPARENT)
        }
        refreshIconButton(iv, iconType, tint)
        return iv
    }

    private fun refreshIconButton(iv: ImageView, iconType: Int, tint: Int) {
        val size = dp(40)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val p    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint; style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val pf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val cx = size / 2f; val cy = size / 2f; val s = size * 0.28f

        when (iconType) {
            ICON_HEART_OUTLINE, ICON_HEART_FILLED -> {
                val path = Path().apply {
                    moveTo(cx, cy + s * 0.6f)
                    cubicTo(cx - s * 1.4f, cy - s * 0.2f, cx - s * 1.4f, cy - s * 1.1f, cx, cy - s * 0.3f)
                    cubicTo(cx + s * 1.4f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.2f, cx, cy + s * 0.6f)
                    close()
                }
                if (iconType == ICON_HEART_FILLED) c.drawPath(path, pf) else c.drawPath(path, p)
            }
            ICON_VOLUME_ON -> {
                val spk = Path().apply {
                    moveTo(cx - s * 0.8f, cy - s * 0.5f); lineTo(cx - s * 0.1f, cy - s * 0.5f)
                    lineTo(cx + s * 0.6f, cy - s * 1.1f); lineTo(cx + s * 0.6f, cy + s * 1.1f)
                    lineTo(cx - s * 0.1f, cy + s * 0.5f); lineTo(cx - s * 0.8f, cy + s * 0.5f); close()
                }
                c.drawPath(spk, pf)
                p.style = Paint.Style.STROKE
                val r1 = s * 1.0f; val r2 = s * 1.5f
                c.drawArc(cx + s*0.2f-r1, cy-r1, cx+s*0.2f+r1, cy+r1, -50f, 100f, false, p)
                c.drawArc(cx + s*0.2f-r2, cy-r2, cx+s*0.2f+r2, cy+r2, -50f, 100f, false, p)
            }
            ICON_VOLUME_OFF -> {
                val spk = Path().apply {
                    moveTo(cx - s * 0.8f, cy - s * 0.5f); lineTo(cx - s * 0.1f, cy - s * 0.5f)
                    lineTo(cx + s * 0.6f, cy - s * 1.1f); lineTo(cx + s * 0.6f, cy + s * 1.1f)
                    lineTo(cx - s * 0.1f, cy + s * 0.5f); lineTo(cx - s * 0.8f, cy + s * 0.5f); close()
                }
                c.drawPath(spk, pf)
                p.style = Paint.Style.STROKE
                c.drawLine(cx + s*0.9f, cy - s*0.8f, cx + s*1.6f, cy + s*0.8f, p)
                c.drawLine(cx + s*1.6f, cy - s*0.8f, cx + s*0.9f, cy + s*0.8f, p)
            }
            ICON_SHARE -> {
                p.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(cx - s, cy + s * 0.2f); lineTo(cx - s, cy + s * 1.1f)
                    lineTo(cx + s, cy + s * 1.1f); lineTo(cx + s, cy + s * 0.2f)
                }
                c.drawPath(path, p)
                c.drawLine(cx, cy + s * 0.6f, cx, cy - s * 0.8f, p)
                val arrow = Path().apply {
                    moveTo(cx - s * 0.55f, cy - s * 0.25f)
                    lineTo(cx, cy - s * 0.85f)
                    lineTo(cx + s * 0.55f, cy - s * 0.25f)
                }
                c.drawPath(arrow, p)
            }
        }
        iv.setImageBitmap(bmp)
    }

    // ── Loading / NoNet views ─────────────────────────────────────────────────

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
        frame.addView(spinner, FrameLayout.LayoutParams(dp(52), dp(52)).also { it.gravity = Gravity.CENTER })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY; style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 2f) % 360f; invalidate(); postDelayed(this, 32) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx * 0.7f
                c.drawArc(cx - r, cy - r, cx + r, cy + r, angle, 200f, false, paint)
            }
        }
        ll.addView(spinner, lp(dp(52), dp(52)).also { it.bottomMargin = dp(16) })
        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"; textSize = 15f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER
        }, lp(WRAP_CONTENT, WRAP_CONTENT))
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f
            setTextColor(Color.parseColor("#FF6600")); gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(12) })
        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    companion object {
        const val ICON_HEART_OUTLINE = 0
        const val ICON_HEART_FILLED  = 1
        const val ICON_VOLUME_ON     = 2
        const val ICON_VOLUME_OFF    = 3
        const val ICON_SHARE         = 4
    }
}