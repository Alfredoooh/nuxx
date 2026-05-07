package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.nuxx.app.MainActivity
import kotlin.math.abs

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val mainHandler  = Handler(Looper.getMainLooper())
    private val viewKeys     = mutableListOf<String>()
    private val resolvedUrls = mutableMapOf<Int, String>()
    private val players      = mutableMapOf<Int, ExoPlayer>()
    private var currentIdx   = 0
    private var currentPage  = 1
    private var resolveHead  = 0
    private var isResolving  = false
    private var firstShown   = false

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT = LayoutParams.WRAP_CONTENT

    private val containerCurrent  = FrameLayout(context)
    private val containerNext     = FrameLayout(context)
    private val playerViewCurrent = PlayerView(activity).apply {
        useController = false; setShutterBackgroundColor(Color.BLACK)
    }
    private val playerViewNext = PlayerView(activity).apply {
        useController = false; setShutterBackgroundColor(Color.BLACK)
    }

    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()
    private val progressFill = View(context)
    private var progressJob: Runnable? = null

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var isDragging  = false
    private var dragOffset  = 0f
    private val velocityTracker = VelocityTracker.obtain()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        containerNext.addView(playerViewNext, lp(MATCH_PARENT, MATCH_PARENT))
        addView(containerNext, lp(MATCH_PARENT, MATCH_PARENT))

        containerCurrent.addView(playerViewCurrent, lp(MATCH_PARENT, MATCH_PARENT))
        addView(containerCurrent, lp(MATCH_PARENT, MATCH_PARENT))

        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(180, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(140)).also {
            it.gravity = Gravity.BOTTOM
        })

        val progressBg = View(context).apply { setBackgroundColor(Color.argb(50, 255, 255, 255)) }
        progressFill.setBackgroundColor(Color.WHITE)
        val prog = FrameLayout(context)
        prog.addView(progressBg,   lp(MATCH_PARENT, dp(2)))
        prog.addView(progressFill, FrameLayout.LayoutParams(0, dp(2)))
        addView(prog, FrameLayout.LayoutParams(MATCH_PARENT, dp(2)).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(loadingView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,   lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility = GONE

        setOnTouchListener { _, e -> handleTouch(e) }
    }

    // ── Rede ──────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) { showNoNet(); mainHandler.postDelayed({ checkNetAndLoad() }, 3000) }
        else { hideNoNet(); startEverything() }
    }

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility   = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    // ── Arranque ──────────────────────────────────────────────────────────────

    private fun startEverything() {
        currentPage = 1; currentIdx = 0; resolveHead = 0
        firstShown  = false; isResolving = false
        viewKeys.clear(); resolvedUrls.clear()
        players.values.forEach { it.release() }; players.clear()

        // Fetch viewkeys e resolve o primeiro imediatamente
        Thread {
            val keys = ShortiesApi.fetchViewKeys(1)
            mainHandler.post {
                if (keys.isEmpty()) { mainHandler.postDelayed({ startEverything() }, 3000); return@post }
                viewKeys.addAll(keys)
                // Arranca pipeline sequencial — 1 resolve de cada vez
                startResolvePipeline()
                // Fetch mais viewkeys em background sem pressa
                fetchMoreKeysBackground()
            }
        }.start()
    }

    private fun fetchMoreKeysBackground() {
        Thread {
            for (page in 2..8) {
                if (viewKeys.size >= 200) break
                Thread.sleep(3000) // espera 3s entre páginas para não saturar
                val keys = ShortiesApi.fetchViewKeys(page)
                mainHandler.post {
                    val newKeys = keys.filter { it !in viewKeys }
                    viewKeys.addAll(newKeys)
                }
            }
        }.start()
    }

    // ── Pipeline sequencial — 1 resolve de cada vez ───────────────────────────

    private fun startResolvePipeline() {
        if (isResolving) return
        isResolving = true
        resolveHead = 0
        doResolveNext()
    }

    private fun doResolveNext() {
        // Não resolve mais de 5 à frente do current — preserva banda para o player
        if (resolveHead > currentIdx + 5) {
            isResolving = false
            return
        }
        if (resolveHead >= viewKeys.size) {
            mainHandler.postDelayed({ doResolveNext() }, 1500)
            return
        }
        if (resolveHead in resolvedUrls) {
            resolveHead++
            doResolveNext()
            return
        }

        val idx = resolveHead
        val vk  = viewKeys[idx]

        Thread {
            val url = ShortiesApi.fetchVideoUrl(vk)
            mainHandler.post {
                if (url != null) {
                    resolvedUrls[idx] = url
                    // Pré-carrega player imediatamente se for relevante
                    if (idx <= currentIdx + 2) preloadPlayer(idx, url)
                    // Primeiro vídeo pronto — toca já
                    if (idx == 0 && !firstShown) {
                        firstShown = true
                        activatePlayer(0)
                    }
                    // Next player — anexa ao playerViewNext para estar pronto
                    if (idx == currentIdx + 1) {
                        val np = preloadPlayer(idx, url)
                        playerViewNext.player = np
                    }
                }
                resolveHead++
                // Pausa 800ms entre resolves para não roubar banda ao player
                mainHandler.postDelayed({ doResolveNext() }, 800)
            }
        }.start()
    }

    private fun resumePipeline() {
        if (!isResolving) {
            isResolving = true
            doResolveNext()
        }
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private fun preloadPlayer(idx: Int, url: String): ExoPlayer {
        players[idx]?.let { return it }
        val player = ExoPlayer.Builder(context).build()
        players[idx] = player

        val dsFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Cookie" to "age_verified=1; accessAgeDisclaimerPH=1; il=1; " +
                    "platform=pc; cookiesAccepted=1; cookieConsent=3"
            ))

        val source = HlsMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(source)
        player.repeatMode    = Player.REPEAT_MODE_ONE
        player.volume        = 0f
        player.prepare()
        player.playWhenReady = false
        return player
    }

    private fun activatePlayer(idx: Int) {
        if (idx < 0 || idx >= viewKeys.size) return

        val url = resolvedUrls[idx]
        if (url == null) {
            // Ainda não resolvido — aguarda sem mostrar loader agressivo
            mainHandler.postDelayed({ activatePlayer(idx) }, 400)
            return
        }

        players.forEach { (i, p) -> if (i != idx) { p.volume = 0f; p.pause() } }

        val player = preloadPlayer(idx, url)
        playerViewCurrent.player = player
        player.volume        = 1f
        player.playWhenReady = true
        player.play()

        // Anexa next ao playerViewNext
        val nextUrl = resolvedUrls[idx + 1]
        if (nextUrl != null) {
            playerViewNext.player = preloadPlayer(idx + 1, nextUrl)
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY   -> hideLoader()
                    Player.STATE_BUFFERING -> { /* não mostra loader ao bufferizar — menos disruptivo */ }
                    Player.STATE_ENDED   -> goToNext()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { goToNext() }
        })

        startProgressUpdater(player)
        cleanupDistantPlayers(idx)
        resumePipeline()
    }

    private fun cleanupDistantPlayers(idx: Int) {
        players.keys.filter { it < idx - 1 || it > idx + 3 }.forEach { i ->
            players[i]?.release(); players.remove(i)
        }
    }

    private fun goToNext() {
        if (currentIdx >= viewKeys.size - 1) return
        currentIdx++
        showLoader()
        activatePlayer(currentIdx)
        resumePipeline()
    }

    private fun goToPrev() {
        if (currentIdx <= 0) return
        currentIdx--
        showLoader()
        activatePlayer(currentIdx)
    }

    // ── Progresso ─────────────────────────────────────────────────────────────

    private fun startProgressUpdater(player: ExoPlayer) {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        val run = object : Runnable {
            override fun run() {
                val dur = player.duration
                val pos = player.currentPosition
                if (dur > 0) {
                    val pct = (pos.toFloat() / dur * width).toInt()
                    progressFill.layoutParams =
                        (progressFill.layoutParams as FrameLayout.LayoutParams).also { it.width = pct }
                    progressFill.requestLayout()
                }
                mainHandler.postDelayed(this, 200)
            }
        }
        progressJob = run
        mainHandler.post(run)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("Recycle")
    private fun handleTouch(e: MotionEvent): Boolean {
        velocityTracker.addMovement(e)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = e.y; touchStartX = e.x
                isDragging = false; dragOffset = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - touchStartY
                val dx = e.x - touchStartX
                if (!isDragging && abs(dy) > 12 && abs(dy) > abs(dx)) isDragging = true
                if (isDragging) {
                    dragOffset = dy
                    containerCurrent.translationY = dragOffset
                    containerNext.translationY = if (dragOffset < 0)
                        height + dragOffset * 0.3f
                    else
                        -height + dragOffset * 0.3f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000)
                    val vy = velocityTracker.yVelocity
                    val threshold = height * 0.28f
                    when {
                        (dragOffset < -threshold || vy < -900f) && currentIdx < viewKeys.size - 1 ->
                            animateCommit(toTop = true)  { goToNext() }
                        (dragOffset > threshold  || vy >  900f) && currentIdx > 0 ->
                            animateCommit(toTop = false) { goToPrev() }
                        else -> animateCancel()
                    }
                    isDragging = false
                } else {
                    players[currentIdx]?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun animateCommit(toTop: Boolean, onEnd: () -> Unit) {
        val targetY = if (toTop) -height.toFloat() else height.toFloat()
        containerCurrent.animate()
            .translationY(targetY).setDuration(240)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                containerCurrent.translationY = 0f
                containerNext.translationY    = 0f
                onEnd()
            }.start()
        containerNext.animate()
            .translationY(0f).setDuration(240)
            .setInterpolator(FastOutSlowInInterpolator()).start()
    }

    private fun animateCancel() {
        containerCurrent.animate()
            .translationY(0f).setDuration(300)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        containerNext.animate()
            .translationY(if (dragOffset < 0) height.toFloat() else -height.toFloat())
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    // ── Loader bonito ─────────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }

        // Logo / ícone central
        val logo = object : View(context) {
            private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = dp(1.5f).toFloat()
            }
            private val paintArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = dp(3f).toFloat()
                strokeCap   = Paint.Cap.ROUND
            }
            private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }

            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r1 = width * 0.38f
                val r2 = width * 0.26f

                // Anel exterior fixo
                c.drawCircle(cx, cy, r1, paintRing)

                // Arco a rodar — anel exterior
                val oval1 = RectF(cx - r1, cy - r1, cx + r1, cy + r1)
                c.drawArc(oval1, angle, 260f, false, paintArc)

                // Arco a rodar — anel interior (sentido contrário)
                paintArc.color       = Color.argb(160, 255, 255, 255)
                paintArc.strokeWidth = dp(2f).toFloat()
                val oval2 = RectF(cx - r2, cy - r2, cx + r2, cy + r2)
                c.drawArc(oval2, -angle * 1.4f, 200f, false, paintArc)
                paintArc.color       = Color.WHITE
                paintArc.strokeWidth = dp(3f).toFloat()

                // Ponto central
                c.drawCircle(cx, cy, dp(3f).toFloat(), paintDot)
            }
        }

        val logoSize = dp(72)
        frame.addView(logo, FrameLayout.LayoutParams(logoSize, logoSize).also {
            it.gravity = Gravity.CENTER
            it.bottomMargin = dp(20)
        })

        // Texto "A carregar..." em baixo do spinner
        val tv = TextView(context).apply {
            text      = "A carregar…"
            textSize  = 13f
            setTextColor(Color.argb(120, 255, 255, 255))
            gravity   = Gravity.CENTER
            typeface  = Typeface.DEFAULT
        }
        frame.addView(tv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity    = Gravity.CENTER
            it.topMargin  = dp(80)
        })

        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }

        // Ícone sem net desenhado
        val icon = object : View(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(100, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = dp(2f).toFloat()
                strokeCap   = Paint.Cap.ROUND
            }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val u = width * 0.18f
                // Arcos wifi cortados
                c.drawArc(RectF(cx-u*3,cy-u*3,cx+u*3,cy+u*3), 210f, 120f, false, p)
                c.drawArc(RectF(cx-u*2,cy-u*2,cx+u*2,cy+u*2), 210f, 120f, false, p)
                c.drawArc(RectF(cx-u*1,cy-u*1,cx+u*1,cy+u*1), 210f, 120f, false, p)
                // Linha de corte
                p.color = Color.argb(180, 255, 80, 80)
                c.drawLine(cx - u*2.5f, cy - u*2.5f, cx + u*2.5f, cy + u*2.5f, p)
                // Ponto
                p.style = Paint.Style.FILL; p.color = Color.argb(100, 255, 255, 255)
                c.drawCircle(cx, cy + u*2f, dp(3f).toFloat(), p)
            }
        }
        ll.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)).also { it.bottomMargin = dp(16) })

        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"
            textSize = 15f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(20) })

        val btn = TextView(context).apply {
            text = "Tentar novamente"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(12), dp(28), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(255, 40, 40, 40))
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
        }
        btn.setOnClickListener { hideNoNet(); checkNetAndLoad() }
        ll.addView(btn)

        frame.addView(ll, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    private fun showLoader() { loadingView.alpha = 1f; loadingView.visibility = VISIBLE }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(200).withEndAction {
            loadingView.visibility = GONE; loadingView.alpha = 1f
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int)   = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        velocityTracker.recycle()
        players.values.forEach { it.release() }
        players.clear()
    }
}