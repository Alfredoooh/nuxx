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
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity
import kotlin.math.abs

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val videos      = mutableListOf<ShortVideo>()
    private val players     = mutableMapOf<Int, ExoPlayer>()
    private var currentIdx  = 0
    private var currentPage = 1
    private var isFetching  = false
    private var firstShown  = false

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
    private val skeletonView = buildSkeletonView()
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

        // Gradiente bottom
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(180, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(160)).also {
            it.gravity = Gravity.BOTTOM
        })

        // AppBar: "Shorts" + search icon
        buildAppBar()

        // Barra de progresso
        val progressBg = View(context).apply { setBackgroundColor(Color.argb(50, 255, 255, 255)) }
        progressFill.setBackgroundColor(Color.WHITE)
        val prog = FrameLayout(context)
        prog.addView(progressBg,   lp(MATCH_PARENT, dp(2)))
        prog.addView(progressFill, FrameLayout.LayoutParams(0, dp(2)))
        addView(prog, FrameLayout.LayoutParams(MATCH_PARENT, dp(2)).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(skeletonView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(loadingView,  lp(MATCH_PARENT, MATCH_PARENT))
        addView(noNetView,    lp(MATCH_PARENT, MATCH_PARENT))
        noNetView.visibility    = GONE
        loadingView.visibility  = GONE

        setOnTouchListener { _, e -> handleTouch(e) }
    }

    private fun buildAppBar() {
        val statusH = activity.statusBarHeight
        val bar = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val title = TextView(context).apply {
            text      = "Shorts"
            textSize  = 20f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        bar.addView(title, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
            it.gravity    = Gravity.CENTER_VERTICAL or Gravity.START
            it.leftMargin = dp(16)
        })

        // Ícone search SVG
        val searchBtn = buildSvgButton("icons/svg/search.svg")
        searchBtn.setOnClickListener {
    activity.addContentOverlay(SearchResultsPage(activity, ""))
}
        bar.addView(searchBtn, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity     = Gravity.CENTER_VERTICAL or Gravity.END
            it.rightMargin = dp(12)
        })

        addView(bar, FrameLayout.LayoutParams(MATCH_PARENT, statusH + dp(52)).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildSvgButton(path: String): FrameLayout {
        val btn = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, path)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            val iv = android.widget.ImageView(context).apply {
                setImageBitmap(bmp)
                setColorFilter(Color.WHITE)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            btn.addView(iv, FrameLayout.LayoutParams(px, px).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        return btn
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildSkeletonView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#111111")) }

        // Avatar skeleton direita
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, dp(16), dp(140))
        }
        listOf(50 to 50, 44 to 8, 44 to 44, 44 to 44).forEachIndexed { i, (w, h) ->
            val v = View(context).apply {
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = if (i == 0) dp(25).toFloat() else dp(8).toFloat()
                    setColor(Color.parseColor("#2A2A2A"))
                }
            }
            rightCol.addView(v, LinearLayout.LayoutParams(dp(w), dp(h)).also {
                it.bottomMargin = dp(if (i == 0) 20 else 14)
            })
        }
        frame.addView(rightCol, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })

        // Info skeleton bottom
        val bottomCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(80), dp(110))
        }
        listOf(140 to 14, 200 to 13, 160 to 12).forEach { (w, _) ->
            val v = View(context).apply {
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#2A2A2A"))
                }
            }
            bottomCol.addView(v, LinearLayout.LayoutParams(dp(w), dp(14)).also {
                it.bottomMargin = dp(8)
            })
        }
        frame.addView(bottomCol, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        // Pulse animation
        frame.post { animateSkeleton(frame) }
        return frame
    }

    private fun animateSkeleton(view: View) {
        view.animate().alpha(0.5f).setDuration(800).withEndAction {
            view.animate().alpha(1f).setDuration(800).withEndAction {
                if (view.visibility == VISIBLE) animateSkeleton(view)
            }.start()
        }.start()
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

    private fun showNoNet() { skeletonView.visibility = GONE; noNetView.visibility = VISIBLE }
    private fun hideNoNet() { noNetView.visibility = GONE; skeletonView.visibility = VISIBLE }

    // ── Fetch via API ─────────────────────────────────────────────────────────

    private fun startEverything() {
        currentPage = 1; currentIdx = 0; firstShown = false; isFetching = false
        videos.clear()
        players.values.forEach { it.release() }; players.clear()
        fetchPage(1)
    }

    private fun fetchPage(page: Int) {
        if (isFetching) return
        isFetching = true
        Thread {
            val result = ShortiesApi.fetchVideos(page)
            mainHandler.post {
                isFetching = false
                if (result.isEmpty()) {
                    if (videos.isEmpty()) mainHandler.postDelayed({ startEverything() }, 3000)
                    return@post
                }
                videos.addAll(result)
                if (!firstShown) {
                    firstShown = true
                    skeletonView.visibility = GONE
                    activatePlayer(0)
                }
                // Pré-fetch próximas páginas em background
                if (videos.size < 200) {
                    mainHandler.postDelayed({ fetchPage(page + 1) }, 2000)
                }
            }
        }.start()
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private fun preloadPlayer(idx: Int): ExoPlayer? {
        val url = videos.getOrNull(idx)?.link ?: return null
        players[idx]?.let { return it }
        val player = ExoPlayer.Builder(context).build()
        players[idx] = player
        val dsFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Referer"    to "https://www.pornhub.com/"
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
        if (idx < 0 || idx >= videos.size) return

        players.forEach { (i, p) -> if (i != idx) { p.volume = 0f; p.pause() } }

        val player = preloadPlayer(idx) ?: return
        playerViewCurrent.player = player
        player.volume        = 1f
        player.playWhenReady = true
        player.play()

        val nextPlayer = preloadPlayer(idx + 1)
        if (nextPlayer != null) playerViewNext.player = nextPlayer

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY   -> hideLoader()
                    Player.STATE_ENDED   -> goToNext()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { goToNext() }
        })

        startProgressUpdater(player)
        cleanupDistantPlayers(idx)
    }

    private fun cleanupDistantPlayers(idx: Int) {
        players.keys.filter { it < idx - 1 || it > idx + 3 }.forEach { i ->
            players[i]?.release(); players.remove(i)
        }
    }

    private fun goToNext() {
        if (currentIdx >= videos.size - 1) {
            if (!isFetching) fetchPage(currentPage + 1)
            return
        }
        currentIdx++
        showLoader()
        activatePlayer(currentIdx)
        if (currentIdx >= videos.size - 5 && !isFetching) fetchPage(currentPage + 1)
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
                        height + dragOffset * 0.3f else -height + dragOffset * 0.3f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000)
                    val vy = velocityTracker.yVelocity
                    val threshold = height * 0.28f
                    when {
                        (dragOffset < -threshold || vy < -900f) && currentIdx < videos.size - 1 ->
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

    // ── Loader ────────────────────────────────────────────────────────────────

    private fun showLoader() { loadingView.alpha = 1f; loadingView.visibility = VISIBLE }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(200).withEndAction {
            loadingView.visibility = GONE; loadingView.alpha = 1f
        }.start()
    }

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val spinner = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE
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
        frame.addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"; textSize = 15f
            setTextColor(Color.argb(180, 255, 255, 255)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(20) })
        val btn = TextView(context).apply {
            text = "Tentar novamente"; textSize = 14f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(dp(28), dp(12), dp(28), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(Color.argb(255, 40, 40, 40))
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    fun onDestroy() {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        velocityTracker.recycle()
        players.values.forEach { it.release() }
        players.clear()
    }
}