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
    private val resolvedUrls = mutableMapOf<Int, String>() // index → m3u8 url
    private val players      = mutableMapOf<Int, ExoPlayer>() // index → player
    private var currentIdx   = 0
    private var isFetching   = false
    private var currentPage  = 1
    private val TARGET_KEYS  = 500
    private val PRELOAD_AHEAD = 2 // quantos vídeos à frente pré-carregar
    private val PRELOAD_BEHIND = 1
    private val MAX_PLAYERS  = 4 // máximo de ExoPlayers vivos ao mesmo tempo

    private val MATCH_PARENT = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT  = LayoutParams.WRAP_CONTENT

    // Dois PlayerViews — current e next — para transição física
    private val containerCurrent = FrameLayout(context)
    private val containerNext    = FrameLayout(context)
    private val playerViewCurrent = PlayerView(activity).apply {
        useController = false; setShutterBackgroundColor(Color.BLACK)
    }
    private val playerViewNext = PlayerView(activity).apply {
        useController = false; setShutterBackgroundColor(Color.BLACK)
    }

    private val loadingView = buildLoadingView()
    private val noNetView   = buildNoNetView()

    // Barra de progresso
    private val progressFill = View(context)
    private var progressJob: Runnable? = null

    // Swipe
    private var touchStartY   = 0f
    private var touchStartX   = 0f
    private var isDragging    = false
    private var dragOffset    = 0f
    private val SWIPE_THRESH  = 60f
    private val velocityTracker = VelocityTracker.obtain()

    // Pool fetch timer
    private var poolTimer: Runnable? = null

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        // Container next fica por baixo (vai aparecer ao deslizar)
        containerNext.addView(playerViewNext, lp(MATCH_PARENT, MATCH_PARENT))
        addView(containerNext, lp(MATCH_PARENT, MATCH_PARENT))

        // Container current fica por cima
        containerCurrent.addView(playerViewCurrent, lp(MATCH_PARENT, MATCH_PARENT))
        addView(containerCurrent, lp(MATCH_PARENT, MATCH_PARENT))

        // Gradiente inferior subtil
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(160, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(120)).also {
            it.gravity = Gravity.BOTTOM
        })

        // Barra de progresso
        val progressBar = View(context).apply { setBackgroundColor(Color.argb(50, 255, 255, 255)) }
        progressFill.setBackgroundColor(Color.WHITE)
        val progContainer = FrameLayout(context)
        progContainer.addView(progressBar,  lp(MATCH_PARENT, dp(2)))
        progContainer.addView(progressFill, FrameLayout.LayoutParams(0, dp(2)))
        addView(progContainer, FrameLayout.LayoutParams(MATCH_PARENT, dp(2)).also {
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
        if (!isOnline()) {
            showNoNet()
            mainHandler.postDelayed({ checkNetAndLoad() }, 3000)
        } else {
            hideNoNet()
            startFetching()
        }
    }

    private fun showNoNet() { loadingView.visibility = GONE; noNetView.visibility = VISIBLE }
    private fun hideNoNet() { noNetView.visibility = GONE; loadingView.visibility = VISIBLE }

    // ── Fetch viewkeys ────────────────────────────────────────────────────────

    private fun startFetching() {
        currentPage = 1
        viewKeys.clear()
        fetchPage(1)
        startPoolTimer()
    }

    private fun fetchPage(page: Int) {
        if (isFetching) return
        isFetching = true
        Thread {
            val keys = ShortiesApi.fetchViewKeys(page)
            mainHandler.post {
                isFetching = false
                val newKeys = keys.filter { it !in viewKeys }
                viewKeys.addAll(newKeys)
                if (viewKeys.size < TARGET_KEYS && newKeys.isNotEmpty()) fetchPage(page + 1)
                if (viewKeys.size >= 3) {
                    preloadAround(currentIdx)
                }
            }
        }.start()
    }

    // ── Pool Timer — resolve 10 URLs a cada 5 segundos ────────────────────────

    private fun startPoolTimer() {
        poolTimer?.let { mainHandler.removeCallbacks(it) }
        val run = object : Runnable {
            override fun run() {
                resolveNextBatch()
                mainHandler.postDelayed(this, 5000)
            }
        }
        poolTimer = run
        mainHandler.post(run)
    }

    private fun resolveNextBatch() {
        // Encontra os próximos 10 índices sem URL resolvida
        val toResolve = (0 until minOf(viewKeys.size, currentIdx + 30))
            .filter { it !in resolvedUrls && it < viewKeys.size }
            .take(10)
        if (toResolve.isEmpty()) return

        Thread {
            toResolve.forEach { idx ->
                if (idx >= viewKeys.size) return@forEach
                val url = ShortiesApi.fetchVideoUrl(viewKeys[idx]) ?: return@forEach
                mainHandler.post {
                    resolvedUrls[idx] = url
                    // Se é um índice que precisamos de pré-carregar, fá-lo agora
                    if (idx in (currentIdx - PRELOAD_BEHIND)..(currentIdx + PRELOAD_AHEAD)) {
                        preloadPlayer(idx)
                    }
                }
            }
        }.start()
    }

    // ── Pré-carregamento de players ───────────────────────────────────────────

    private fun preloadAround(idx: Int) {
        val range = (idx - PRELOAD_BEHIND)..(idx + PRELOAD_AHEAD)
        range.forEach { i ->
            if (i >= 0 && i < viewKeys.size && i !in players) {
                val url = resolvedUrls[i]
                if (url != null) preloadPlayer(i)
                else resolveAndPreload(i)
            }
        }
        // Liberta players fora do range
        val toRelease = players.keys.filter { it < idx - PRELOAD_BEHIND - 1 || it > idx + PRELOAD_AHEAD + 1 }
        toRelease.forEach { i ->
            players[i]?.release()
            players.remove(i)
        }
        // Garante que não passa MAX_PLAYERS
        if (players.size > MAX_PLAYERS) {
            val oldest = players.keys.minOrNull() ?: return
            if (oldest != currentIdx) {
                players[oldest]?.release()
                players.remove(oldest)
            }
        }
    }

    private fun resolveAndPreload(idx: Int) {
        if (idx >= viewKeys.size) return
        Thread {
            val url = ShortiesApi.fetchVideoUrl(viewKeys[idx]) ?: return@Thread
            mainHandler.post {
                resolvedUrls[idx] = url
                preloadPlayer(idx)
            }
        }.start()
    }

    private fun preloadPlayer(idx: Int): ExoPlayer {
        players[idx]?.let { return it }
        val player = ExoPlayer.Builder(context).build()
        players[idx] = player

        val url = resolvedUrls[idx] ?: return player
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Cookie" to "age_verified=1; accessAgeDisclaimerPH=1; il=1; " +
                    "platform=pc; cookiesAccepted=1; cookieConsent=3"
            ))
        val source = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(source)
        player.repeatMode    = Player.REPEAT_MODE_ONE
        player.volume        = 0f // silencioso até ser o current
        player.prepare()
        player.playWhenReady = false // não toca ainda
        return player
    }

    // ── Activar vídeo actual ──────────────────────────────────────────────────

    private fun activatePlayer(idx: Int) {
        if (idx < 0 || idx >= viewKeys.size) return

        // Para todos os outros
        players.forEach { (i, p) ->
            if (i != idx) { p.volume = 0f; p.pause() }
        }

        val player = players[idx] ?: preloadPlayer(idx)
        playerViewCurrent.player = player
        player.volume        = 1f
        player.playWhenReady = true
        player.play()

        // Next player no playerViewNext para transição visual
        val nextIdx = idx + 1
        if (nextIdx < viewKeys.size) {
            val nextPlayer = players[nextIdx] ?: preloadPlayer(nextIdx)
            playerViewNext.player = nextPlayer
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) hideLoader()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Salta para o próximo
                goToNext()
            }
        })

        startProgressUpdater(player)
        preloadAround(idx)

        // Pré-carrega mais viewkeys se necessário
        if (idx >= viewKeys.size - 50 && !isFetching && viewKeys.size < TARGET_KEYS) {
            fetchPage(++currentPage)
        }
    }

    // ── Progresso ─────────────────────────────────────────────────────────────

    private fun startProgressUpdater(player: ExoPlayer) {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        val run = object : Runnable {
            override fun run() {
                val duration = player.duration
                val pos      = player.currentPosition
                if (duration > 0) {
                    val pct = (pos.toFloat() / duration * width).toInt()
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

    // ── Touch — swipe físico suave ────────────────────────────────────────────

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
                    // Current acompanha o dedo
                    containerCurrent.translationY = dragOffset
                    // Next aparece por baixo/cima com parallax suave
                    val parallax = dragOffset * 0.25f
                    containerNext.translationY = if (dragOffset < 0)
                        height + parallax  // próximo vem de baixo
                    else
                        -height + parallax // anterior vem de cima
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000)
                    val vy = velocityTracker.yVelocity
                    val threshold = height * 0.3f

                    when {
                        // Swipe para cima — próximo vídeo
                        (dragOffset < -threshold || vy < -800f) && currentIdx < viewKeys.size - 1 -> {
                            animateCommit(toTop = true) { goToNext() }
                        }
                        // Swipe para baixo — vídeo anterior
                        (dragOffset > threshold || vy > 800f) && currentIdx > 0 -> {
                            animateCommit(toTop = false) { goToPrev() }
                        }
                        // Cancela — volta ao lugar
                        else -> animateCancel()
                    }
                    isDragging = false
                } else {
                    // Tap — pause/play
                    players[currentIdx]?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun animateCommit(toTop: Boolean, onEnd: () -> Unit) {
        val targetY = if (toTop) -height.toFloat() else height.toFloat()
        containerCurrent.animate()
            .translationY(targetY)
            .setDuration(280)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                containerCurrent.translationY = 0f
                containerNext.translationY    = 0f
                onEnd()
            }.start()
        containerNext.animate()
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun animateCancel() {
        containerCurrent.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        containerNext.animate()
            .translationY(if (dragOffset < 0) height.toFloat() else -height.toFloat())
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun goToNext() {
        currentIdx++
        showLoader()
        activatePlayer(currentIdx)
    }

    private fun goToPrev() {
        currentIdx--
        showLoader()
        activatePlayer(currentIdx)
    }

    // ── Loader ────────────────────────────────────────────────────────────────

    private fun showLoader() {
        loadingView.alpha      = 1f
        loadingView.visibility = VISIBLE
    }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(220).withEndAction {
            loadingView.visibility = GONE
            loadingView.alpha = 1f
        }.start()
    }

    // ── Loading & NoNet views ─────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE
                strokeWidth = dp(2.5f).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run = object : Runnable {
                override fun run() { angle = (angle + 8f) % 360f; invalidate(); postDelayed(this, 14) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val r = width / 2f * 0.65f
                c.drawArc(width/2f - r, height/2f - r, width/2f + r, height/2f + r, angle, 270f, false, paint)
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

    private fun dp(v: Int)   = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        poolTimer?.let   { mainHandler.removeCallbacks(it) }
        velocityTracker.recycle()
        players.values.forEach { it.release() }
        players.clear()
    }
}