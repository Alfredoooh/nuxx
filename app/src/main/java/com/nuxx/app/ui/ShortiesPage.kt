package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RoundRectShape
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
    private var loaderJob: Runnable? = null

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = LayoutParams.WRAP_CONTENT

    private val playerFrame = FrameLayout(context)
    private val loadingView = buildLoadingView()
    private val noNetView   = buildNoNetView()

    // Overlay direito estilo TikTok
    private val sidePanel   = LinearLayout(context)
    private val btnLike     = buildCircleBtn()
    private val tvLikes     = buildCountLabel("0")
    private val btnComment  = buildCircleBtn()
    private val tvComments  = buildCountLabel("")
    private val btnShare    = buildCircleBtn()
    private val tvShares    = buildCountLabel("")

    // Info em baixo
    private val infoPanel   = LinearLayout(context)
    private val tvAuthor    = buildAuthorLabel("@shorty")
    private val tvTitle     = buildTitleLabel("")

    // Barra de progresso
    private val progressBar = View(context)
    private val progressFill= View(context)

    private var touchStartY  = 0f
    private var touchStartX  = 0f
    private var isDragging   = false
    private val SWIPE_THRESH = 80f

    init {
        setBackgroundColor(Color.BLACK)
        setupPlayerWeb()
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        // Player ocupa tudo
        playerFrame.addView(playerWeb, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        // Gradiente inferior
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(200,0,0,0), Color.argb(80,0,0,0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(280)).also {
            it.gravity = Gravity.BOTTOM
        })

        // ── Painel lateral direito (estilo TikTok) ──
        sidePanel.orientation = LinearLayout.VERTICAL
        sidePanel.gravity     = Gravity.CENTER_HORIZONTAL
        sidePanel.setPadding(0, 0, dp(10), dp(100))

        // Like
        renderSvg(btnLike, SvgIcon.HEART_OUTLINE, Color.WHITE)
        val likeCol = sideBtnCol(btnLike, tvLikes)
        sidePanel.addView(likeCol, sideLp().also { it.bottomMargin = dp(18) })

        // Comentário (visual only)
        renderSvg(btnComment, SvgIcon.COMMENT, Color.WHITE)
        tvComments.text = ""
        val commentCol = sideBtnCol(btnComment, tvComments)
        sidePanel.addView(commentCol, sideLp().also { it.bottomMargin = dp(18) })

        // Share
        renderSvg(btnShare, SvgIcon.SHARE, Color.WHITE)
        val shareCol = sideBtnCol(btnShare, tvShares)
        sidePanel.addView(shareCol, sideLp())

        addView(sidePanel, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.BOTTOM
        })

        // ── Info em baixo esquerdo ──
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(dp(14), 0, dp(90), dp(28))
        infoPanel.addView(tvAuthor, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) })
        infoPanel.addView(tvTitle,  lp(WRAP_CONTENT, WRAP_CONTENT))
        addView(infoPanel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        // ── Barra de progresso ──
        progressBar.setBackgroundColor(Color.argb(60,255,255,255))
        progressFill.setBackgroundColor(Color.WHITE)
        val progContainer = FrameLayout(context)
        progContainer.addView(progressBar,  lp(MATCH_PARENT, dp(2)))
        progContainer.addView(progressFill, FrameLayout.LayoutParams(0, dp(2)))
        addView(progContainer, FrameLayout.LayoutParams(MATCH_PARENT, dp(2)).also {
            it.gravity = Gravity.BOTTOM
            it.bottomMargin = dp(0)
        })

        // Loading e NoNet por cima de tudo
        addView(loadingView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,   lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility = GONE

        // Touch em toda a área (não bloquear os botões laterais)
        playerFrame.setOnTouchListener { _, e -> handleTouch(e) }
        infoPanel.setOnTouchListener   { _, e -> handleTouch(e) }

        btnLike.setOnClickListener    { toggleLike() }
        btnShare.setOnClickListener   { showShareDialog() }
        btnComment.setOnClickListener { /* futuro */ }
    }

    private fun sideBtnCol(btn: ImageView, label: TextView): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            addView(btn,   lp(dp(52), dp(52)))
            addView(label, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(4) })
        }
    }

    private fun sideLp() = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

    // ── Rede ──────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) { showNoNet(); mainHandler.postDelayed({ checkNetAndLoad() }, 3000) }
        else { hideNoNet(); startFetching() }
    }

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility   = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun startFetching() { currentPage = 1; viewKeys.clear(); fetchPage(1) }

    private fun fetchPage(page: Int) {
        if (isFetching) return
        isFetching = true
        Thread {
            try {
                val conn = (URL("https://www.pornhub.com/shorties?page=$page")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 12000
                    setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    setRequestProperty("Cookie",
                        "age_verified=1; accessAgeDisclaimerPH=1; il=1; platform=pc; " +
                        "cookiesAccepted=1; cookiesBannerSeen=1; cookieConsent=3")
                }
                val html = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val found = Regex("viewkey=([a-zA-Z0-9]+)")
                    .findAll(html).map { it.groupValues[1] }
                    .filter { it.length > 8 }.distinct().toList()

                mainHandler.post {
                    isFetching = false
                    val newKeys = found.filter { it !in viewKeys }
                    viewKeys.addAll(newKeys)
                    if (viewKeys.size < TARGET_KEYS && newKeys.isNotEmpty()) fetchPage(page + 1)
                    if (viewKeys.size >= 3 && !playerReady) { playerReady = true; loadVideo(0, false) }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isFetching = false
                    if (viewKeys.isEmpty()) mainHandler.postDelayed({ fetchPage(page) }, 3000)
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
        playerWeb.setBackgroundColor(Color.BLACK)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(playerWeb, true)
            listOf("age_verified=1","accessAgeDisclaimerPH=1","il=1",
                   "platform=pc","cookieConsent=3","cookiesAccepted=1").forEach {
                setCookie(".pornhub.com", "$it; path=/; domain=.pornhub.com")
            }
            flush()
        }

        playerWeb.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView, req: WebResourceRequest
            ): WebResourceResponse? {
                val u = req.url.toString()
                val blocked = listOf("trafficjunky","contentabc","adroll","ads2.",
                    "cdn1.ads","doubleclick","googlesyndication","adnxs","pubmatic")
                if (blocked.any { u.contains(it) })
                    return WebResourceResponse("text/plain","utf-8","".byteInputStream())
                return super.shouldInterceptRequest(view, req)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectAll(view)
                // Cancela loader anterior e agenda novo com polling no vídeo
                loaderJob?.let { mainHandler.removeCallbacks(it) }
                pollVideoReady(view)
            }

            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url?.toString() ?: return true
                return !u.contains("pornhub.com/embed") && !u.contains("phncdn.com")
            }
        }
    }

    private fun pollVideoReady(view: WebView?) {
        // Verifica a cada 300ms se o vídeo tem dimensões (está a carregar de facto)
        var attempts = 0
        val check = object : Runnable {
            override fun run() {
                attempts++
                view?.evaluateJavascript("""
                    (function(){
                        var v = document.querySelector('video');
                        if(!v) return 'noVideo';
                        if(v.readyState >= 2) return 'ready';
                        return 'waiting';
                    })();
                """.trimIndent()) { result ->
                    val r = result?.trim('"') ?: "noVideo"
                    when {
                        r == "ready" -> hideLoader()
                        attempts < 30 -> mainHandler.postDelayed(this, 400) // espera até 12s
                        else -> hideLoader() // timeout — esconde loader de qualquer forma
                    }
                }
            }
        }
        loaderJob = check
        mainHandler.postDelayed(check, 500)
    }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(250).withEndAction {
            loadingView.visibility = GONE
            loadingView.alpha = 1f
        }.start()
    }

    private fun injectAll(view: WebView?) {
        view?.evaluateJavascript("""
        (function(){
            // ── CSS ──
            var s = document.createElement('style');
            s.textContent = `
                * { box-sizing: border-box; }
                html, body {
                    margin: 0 !important; padding: 0 !important;
                    width: 100vw !important; height: 100vh !important;
                    overflow: hidden !important; background: #000 !important;
                }
                /* Player ocupa tela toda */
                #player, .videoWrapper, .playerContainerElement,
                #videoPlayer, .js-player-container {
                    position: fixed !important;
                    top: 0 !important; left: 0 !important;
                    width: 100vw !important; height: 100vh !important;
                    z-index: 1 !important;
                }
                video {
                    position: fixed !important;
                    top: 0 !important; left: 0 !important;
                    width: 100vw !important; height: 100vh !important;
                    object-fit: cover !important;
                    z-index: 2 !important;
                }
                /* Esconde UI nativa */
                .top-controls, .bottom-controls, .middle-controls,
                .topBar, .topBarBackground, .topBarContent,
                .contextMenu, .copyMenu, .settings-menu-wrapper,
                .nextVideoOverlay, .upNext, .gridMenu,
                .slideout-outer-wrapper, .like-dislike-fav,
                .shortcuts, .watchHD, .unmute, .unmuteButton,
                .progressWrapper, .seekbar, .time-display,
                .bottom-bar, .apBar,
                /* Anúncios */
                .adRollContainer, .adRollEventCatcher,
                .adRollSkipButton, .adRollCTA, .adRollLink,
                .adRollTitleText, .adRollTitle, .adRollTitleText,
                [class*="advert"], [id*="ad_"],
                iframe[src*="trafficjunky"], iframe[src*="contentabc"],
                div[id*="mgp_"] {
                    display: none !important;
                }
                /* Garante que eventCatcher não bloqueia o nosso touch */
                .eventCatcher, .tap-overlay {
                    pointer-events: none !important;
                }
            `;
            document.head.appendChild(s);

            // ── Remove ads dinamicamente ──
            new MutationObserver(function(){
                document.querySelectorAll(
                    '.adRollContainer,.adRollEventCatcher,[id*="ad_"],' +
                    '[class*="advert"],[class*="Popup"],.preroll'
                ).forEach(function(el){ el.remove(); });
            }).observe(document.body, { childList:true, subtree:true });

            // ── Desactiva o eventCatcher do player (evita pause ao clicar) ──
            function disableEventCatcher(){
                document.querySelectorAll('.eventCatcher').forEach(function(el){
                    el.style.pointerEvents = 'none';
                    el.style.display = 'none';
                });
            }
            disableEventCatcher();
            setTimeout(disableEventCatcher, 1000);
            setTimeout(disableEventCatcher, 2500);

            // ── Auto-play ──
            var attempts = 0;
            var t = setInterval(function(){
                var v = document.querySelector('video');
                if(v){
                    v.muted = ${isMuted};
                    v.play().catch(function(){});
                    clearInterval(t);
                } else if(++attempts > 20) clearInterval(t);
            }, 300);

            // ── Progresso ──
            setInterval(function(){
                var v = document.querySelector('video');
                if(v && v.duration){
                    var pct = (v.currentTime / v.duration * 100).toFixed(1);
                    window.Android && window.Android.onProgress(pct);
                }
            }, 500);
        })();
        """.trimIndent(), null)
    }

    private fun loadVideo(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= viewKeys.size) return
        val vk = viewKeys[index]

        tvAuthor.text = "@shorty_${index + 1}"
        tvTitle.text  = ""
        tvLikes.text  = ""
        isLiked = false
        updateLikeIcon()
        updateMuteIcon()

        loadingView.visibility = VISIBLE
        loadingView.alpha      = 1f

        val url = "https://www.pornhub.com/embed/$vk"
        if (animate) animateTransition { playerWeb.loadUrl(url) }
        else playerWeb.loadUrl(url)

        // Pré-carrega mais
        if (index >= viewKeys.size - 50 && !isFetching && viewKeys.size < TARGET_KEYS) {
            fetchPage(++currentPage)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN  -> { touchStartY = e.y; touchStartX = e.x; isDragging = false }
            MotionEvent.ACTION_MOVE  -> {
                val dy = e.y - touchStartY; val dx = e.x - touchStartX
                if (!isDragging && abs(dy) > 10 && abs(dy) > abs(dx)) isDragging = true
                if (isDragging) playerFrame.translationY = dy * 0.22f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = e.y - touchStartY
                if (isDragging) {
                    when {
                        dy < -SWIPE_THRESH && currentIdx < viewKeys.size - 1 ->
                            snapTo { currentIdx++; loadVideo(currentIdx) }
                        dy > SWIPE_THRESH && currentIdx > 0 ->
                            snapTo { currentIdx--; loadVideo(currentIdx) }
                        else -> playerFrame.animate().translationY(0f).setDuration(180)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    isDragging = false
                }
            }
        }
        return true
    }

    private fun snapTo(onEnd: () -> Unit) {
        playerFrame.animate().translationY(0f).setDuration(100)
            .setInterpolator(DecelerateInterpolator()).withEndAction(onEnd).start()
    }

    private fun animateTransition(onMid: () -> Unit) {
        playerFrame.animate()
            .translationY(-height * 0.03f).alpha(0f).setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onMid()
                playerFrame.translationY = height * 0.05f
                playerFrame.alpha = 0f
                playerFrame.animate().translationY(0f).alpha(1f).setDuration(200)
                    .setInterpolator(FastOutSlowInInterpolator()).start()
            }.start()
    }

    // ── Acções ────────────────────────────────────────────────────────────────

    private fun toggleLike() { isLiked = !isLiked; updateLikeIcon() }

    private fun toggleMute() {
        isMuted = !isMuted; updateMuteIcon()
        playerWeb.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.muted=${isMuted};})();", null)
    }

    private fun showShareDialog() {
        if (currentIdx >= viewKeys.size) return
        val vk  = viewKeys[currentIdx]
        val url = "https://www.pornhub.com/view_video.php?viewkey=$vk"
        android.app.AlertDialog.Builder(context)
            .setTitle("Partilhar")
            .setItems(arrayOf("Copiar link", "Partilhar via...")) { _, w ->
                when (w) {
                    0 -> { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("url", url))
                        Toast.makeText(context,"Copiado!",Toast.LENGTH_SHORT).show() }
                    1 -> context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,url) },"Partilhar"))
                }
            }.create().also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#1A1A1A")))
                it.show()
            }
    }

    // ── Ícones ────────────────────────────────────────────────────────────────

    enum class SvgIcon { HEART_OUTLINE, HEART_FILLED, COMMENT, SHARE }

    private fun buildCircleBtn(): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(90, 0, 0, 0))
        }
    }

    private fun renderSvg(iv: ImageView, icon: SvgIcon, tint: Int) {
        val size = dp(52)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val sw   = dp(2).toFloat()
        val st   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint; style = Paint.Style.STROKE
            strokeWidth = sw; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val fi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val cx = size / 2f; val cy = size / 2f; val u = size * 0.13f

        when (icon) {
            SvgIcon.HEART_OUTLINE, SvgIcon.HEART_FILLED -> {
                // Coração estilo TikTok — simétrico e pleno
                val path = Path().apply {
                    moveTo(cx, cy + u * 2.8f)
                    cubicTo(cx - u * 0.8f, cy + u * 1.8f,
                            cx - u * 3.2f, cy + u * 0.8f,
                            cx - u * 3.2f, cy - u * 0.6f)
                    cubicTo(cx - u * 3.2f, cy - u * 2.4f,
                            cx - u * 1.2f, cy - u * 3.0f,
                            cx,            cy - u * 1.4f)
                    cubicTo(cx + u * 1.2f, cy - u * 3.0f,
                            cx + u * 3.2f, cy - u * 2.4f,
                            cx + u * 3.2f, cy - u * 0.6f)
                    cubicTo(cx + u * 3.2f, cy + u * 0.8f,
                            cx + u * 0.8f, cy + u * 1.8f,
                            cx,            cy + u * 2.8f)
                    close()
                }
                if (icon == SvgIcon.HEART_FILLED) c.drawPath(path, fi)
                else c.drawPath(path, st)
            }

            SvgIcon.COMMENT -> {
                // Balão de comentário
                val r = u * 2.6f
                val rect = RectF(cx - r, cy - r * 1.1f, cx + r, cy + r * 0.8f)
                c.drawRoundRect(rect, u * 0.8f, u * 0.8f, st)
                // Rabo do balão
                val tail = Path().apply {
                    moveTo(cx - u * 0.4f, cy + r * 0.8f)
                    lineTo(cx - u * 1.4f, cy + r * 1.6f)
                    lineTo(cx + u * 0.8f, cy + r * 0.8f)
                }
                c.drawPath(tail, st)
                // Linhas internas
                st.strokeWidth = dp(1.5f).toFloat()
                c.drawLine(cx - u * 1.6f, cy - u * 0.6f, cx + u * 1.6f, cy - u * 0.6f, st)
                c.drawLine(cx - u * 1.6f, cy + u * 0.6f, cx + u * 0.8f, cy + u * 0.6f, st)
            }

            SvgIcon.SHARE -> {
                // Seta curva para cima (estilo iOS share)
                st.strokeWidth = dp(2).toFloat()
                // Seta
                c.drawLine(cx, cy + u * 2.0f, cx, cy - u * 1.8f, st)
                val arrow = Path().apply {
                    moveTo(cx - u * 1.6f, cy - u * 0.4f)
                    lineTo(cx,            cy - u * 2.2f)
                    lineTo(cx + u * 1.6f, cy - u * 0.4f)
                }
                c.drawPath(arrow, st)
                // Caixa base
                val box = Path().apply {
                    moveTo(cx - u * 2.0f, cy + u * 0.6f)
                    lineTo(cx - u * 2.0f, cy + u * 2.6f)
                    lineTo(cx + u * 2.0f, cy + u * 2.6f)
                    lineTo(cx + u * 2.0f, cy + u * 0.6f)
                }
                c.drawPath(box, st)
            }
        }
        iv.setImageBitmap(bmp)
    }

    private fun updateLikeIcon() {
        val color = if (isLiked) Color.parseColor("#FF2D55") else Color.WHITE
        renderSvg(btnLike, if (isLiked) SvgIcon.HEART_FILLED else SvgIcon.HEART_OUTLINE, color)
    }

    private fun updateMuteIcon() { /* mute é silencioso, sem botão visual dedicado */ }

    // ── Loading ───────────────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE
                strokeWidth = dp(2.5f).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 8f) % 360f; invalidate(); postDelayed(this,14) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val r = width / 2f * 0.65f
                c.drawArc(width/2f-r, height/2f-r, width/2f+r, height/2f+r, angle, 270f, false, paint)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).also { it.gravity = Gravity.CENTER })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        ll.addView(TextView(context).apply {
            text = "Sem ligação"; textSize = 15f; setTextColor(Color.GRAY); gravity = Gravity.CENTER
        }, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(12) })
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.DKGRAY) }
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn)
        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildAuthorLabel(t: String) = TextView(context).apply {
        text = t; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); setShadowLayer(8f, 0f, 1f, Color.BLACK)
    }

    private fun buildTitleLabel(t: String) = TextView(context).apply {
        text = t; textSize = 13f; maxLines = 2
        setTextColor(Color.WHITE); setShadowLayer(8f, 0f, 1f, Color.BLACK)
    }

    private fun buildCountLabel(t: String) = TextView(context).apply {
        text = t; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setShadowLayer(6f, 0f, 1f, Color.BLACK)
    }

    private fun dp(v: Int)   = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() {
        loaderJob?.let { mainHandler.removeCallbacks(it) }
        playerWeb.destroy()
    }
}