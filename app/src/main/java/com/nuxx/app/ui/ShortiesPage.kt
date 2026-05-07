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

    private val mainHandler   = Handler(Looper.getMainLooper())
    private val viewKeys      = mutableListOf<String>()
    private val resolvedUrls  = mutableMapOf<Int, String>()
    private val players       = mutableMapOf<Int, ExoPlayer>()
    private var currentIdx    = 0
    private var isFetching    = false
    private var currentPage   = 1
    private val TARGET_KEYS   = 200

    private val MATCH_PARENT  = LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT  = LayoutParams.WRAP_CONTENT

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

    private var touchStartY  = 0f
    private var touchStartX  = 0f
    private var isDragging   = false
    private var dragOffset   = 0f
    private val SWIPE_THRESH = 60f
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
                intArrayOf(Color.argb(160, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(120)).also {
            it.gravity = Gravity.BOTTOM
        })

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

    private fun showNoNet() { loadingView.visibility = GONE;  noNetView.visibility   = VISIBLE }
    private fun hideNoNet() { noNetView.visibility   = GONE;  loadingView.visibility = VISIBLE }

    // ── Fetch viewkeys — rápido, paralelo ─────────────────────────────────────

    private fun startFetching() {
        currentPage = 1
        viewKeys.clear()
        resolvedUrls.clear()

        // Fetch viewkeys e assim que tiver 3 resolve as primeiras URLs em paralelo
        Thread {
            val keys = ShortiesApi.fetchViewKeys(1)
            mainHandler.post {
                viewKeys.addAll(keys)
                if (viewKeys.isEmpty()) {
                    mainHandler.postDelayed({ startFetching() }, 3000)
                    return@post
                }
                // Resolve as primeiras 3 em paralelo imediatamente
                resolveParallel(listOf(0, 1, 2)) {
                    // Assim que a primeira estiver pronta toca
                    if (resolvedUrls.containsKey(0)) {
                        activatePlayer(0)
                    }
                    // Continua a resolver o resto em background
                    fetchMoreKeysAndResolve()
                }
            }
        }.start()
    }

    private fun fetchMoreKeysAndResolve() {
        Thread {
            // Busca mais páginas de viewkeys
            for (page in 2..5) {
                if (viewKeys.size >= TARGET_KEYS) break
                val keys = ShortiesApi.fetchViewKeys(page)
                mainHandler.post {
                    val newKeys = keys.filter { it !in viewKeys }
                    viewKeys.addAll(newKeys)
                }
                Thread.sleep(500)
            }
        }.start()

        // Resolve URLs em batches de 5 continuamente
        resolveBatchFrom(3)
    }

    private fun resolveBatchFrom(startIdx: Int) {
        val indices = (startIdx until minOf(startIdx + 5, viewKeys.size)).toList()
        if (indices.isEmpty()) return
        resolveParallel(indices) {
            // Continua para o próximo batch depois de 2 segundos
            mainHandler.postDelayed({
                resolveBatchFrom(startIdx + 5)
            }, 2000)
        }
    }

    // Resolve uma lista de índices em paralelo com threads separadas
    private fun resolveParallel(indices: List<Int>, onDone: () -> Unit) {
        if (indices.isEmpty()) { onDone(); return }
        val remaining = indices.filter { it < viewKeys.size && it !in resolvedUrls }
        if (remaining.isEmpty()) { onDone(); return }

        var completed = 0
        val total = remaining.size

        remaining.forEach { idx ->
            Thread {
                val url = ShortiesApi.fetchVideoUrl(viewKeys[idx])
                mainHandler.post {
                    if (url != null) resolvedUrls[idx] = url
                    completed++
                    if (completed >= total) onDone()
                }
            }.start()
        }
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private fun activatePlayer(idx: Int) {
        if (idx < 0 || idx >= viewKeys.size) return

        val url = resolvedUrls[idx]
        if (url == null) {
            // URL ainda não resolvida — espera e tenta de novo
            mainHandler.postDelayed({ activatePlayer(idx) }, 500)
            return
        }

        // Para todos os outros players
        players.forEach { (i, p) -> if (i != idx) { p.volume = 0f; p.pause() } }

        val player = getOrCreatePlayer(idx, url)
        playerViewCurrent.player = player
        player.volume        = 1f
        player.playWhenReady = true
        player.play()

        // Prepara o next player no playerViewNext
        val nextIdx = idx + 1
        if (nextIdx < viewKeys.size) {
            val nextUrl = resolvedUrls[nextIdx]
            if (nextUrl != null) {
                val nextPlayer = getOrCreatePlayer(nextIdx, nextUrl)
                playerViewNext.player = nextPlayer
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) hideLoader()
                if (state == Player.STATE_ENDED) goToNext()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                goToNext()
            }
        })

        startProgressUpdater(player)
        cleanupDistantPlayers(idx)
    }

    private fun getOrCreatePlayer(idx: Int, url: String): ExoPlayer {
        players[idx]?.let { return it }
        val player = ExoPlayer.Builder(context).build()
        players[idx] = player

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Cookie"     to "age_verified=1; accessAgeDisclaimerPH=1; il=1; " +
                    "platform=pc; cookiesAccepted=1; cookieConsent=3"
            ))

        val source = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(source)
        player.repeatMode    = Player.REPEAT_MODE_ONE
        player.volume        = 0f
        player.prepare()
        player.playWhenReady = false
        return player
    }

    private fun cleanupDistantPlayers(idx: Int) {
        val toRelease = players.keys.filter { it < idx - 2 || it > idx + 3 }
        toRelease.forEach { i ->
            players[i]?.release()
            players.remove(i)
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
                        height + dragOffset * 0.25f
                    else
                        -height + dragOffset * 0.25f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000)
                    val vy = velocityTracker.yVelocity
                    val threshold = height * 0.3f
                    when {
                        (dragOffset < -threshold || vy < -800f) && currentIdx < viewKeys.size - 1 ->
                            animateCommit(toTop = true) { goToNext() }
                        (dragOffset > threshold || vy > 800f) && currentIdx > 0 ->
                            animateCommit(toTop = false) { goToPrev() }
                        else -> animateCancel()
                    }
                    isDragging = false
                } else {
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
            .translationY(targetY).setDuration(260)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                containerCurrent.translationY = 0f
                containerNext.translationY    = 0f
                onEnd()
            }.start()
        containerNext.animate()
            .translationY(0f).setDuration(260)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
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

    private fun showLoader() { loadingView.alpha = 1f; loadingView.visibility = VISIBLE }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(200).withEndAction {
            loadingView.visibility = GONE; loadingView.alpha = 1f
        }.start()
    }

    // ── Loading & NoNet ───────────────────────────────────────────────────────

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
            text = "Tentar novamente"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
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
        velocityTracker.recycle()
        players.values.forEach { it.release() }
        players.clear()
    }
}