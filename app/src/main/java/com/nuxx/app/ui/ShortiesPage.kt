// ShortiesPage.kt
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val videos      = mutableListOf<ShortVideo>()
    private val players     = mutableMapOf<Int, ExoPlayer>()
    private var currentIdx  = 0
    private var currentPage = 1
    private var isFetching  = false
    private var firstShown  = false
    private var isMuted     = false

    // Flags de controlo de ciclo de vida
    private var isInForeground  = true   // app está visível (não em background)
    private var isOnHomeTab     = true   // utilizador está na tab home
    private var wasPlayingBeforePause = false  // estado antes de pausa externa

    private val MATCH = LayoutParams.MATCH_PARENT
    private val WRAP  = LayoutParams.WRAP_CONTENT

    private lateinit var slidesContainer: FrameLayout
    private val slideViews  = mutableListOf<PlayerView>()
    private val slideFrames = mutableListOf<FrameLayout>()

    private var swipeStartY    = 0f
    private var swipeDelta     = 0f
    private var isSwiping      = false
    private var lastTouchEndMs = 0L

    private val progressFill = View(context)
    private var progressJob: Runnable? = null
    private var isSeekingProgress = false

    private lateinit var dotsContainer: LinearLayout
    private val dotViews = mutableListOf<View>()

    private lateinit var muteIndicator: FrameLayout

    private val skeletonView = buildSkeletonView()
    private val noNetView    = buildNoNetView()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── Ciclo de vida — chamados pela MainActivity ─────────────────────────────

    /** App vai para background (onPause da Activity) */
    fun pauseForBackground() {
        if (!isInForeground) return
        isInForeground = false
        val p = players[currentIdx]
        wasPlayingBeforePause = p?.isPlaying == true
        p?.pause()
        stopProgressUpdater()
    }

    /** App volta ao foreground (onResume da Activity) — só retoma se estiver na tab home */
    fun resumeFromBackground() {
        isInForeground = true
        if (isOnHomeTab && wasPlayingBeforePause) {
            players[currentIdx]?.play()
            players[currentIdx]?.let { startProgressUpdater(it) }
        }
    }

    /** Utilizador saiu da tab home */
    fun pauseForTabSwitch() {
        isOnHomeTab = false
        val p = players[currentIdx]
        wasPlayingBeforePause = p?.isPlaying == true
        p?.pause()
        stopProgressUpdater()
    }

    /** Utilizador regressou à tab home */
    fun resumeFromTabSwitch() {
        isOnHomeTab = true
        // Só retoma se a app também estiver em foreground
        if (isInForeground && wasPlayingBeforePause) {
            players[currentIdx]?.play()
            players[currentIdx]?.let { startProgressUpdater(it) }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        slidesContainer = FrameLayout(context)
        addView(slidesContainer, LayoutParams(MATCH, MATCH))
        slidesContainer.setOnTouchListener { _, e -> handleSwipeTouch(e); true }

        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT)
            )
            isClickable = false; isFocusable = false
        }
        addView(grad, FrameLayout.LayoutParams(MATCH, dp(220)).also { it.gravity = Gravity.BOTTOM })

        buildAppBar()
        buildProgressBar()
        buildDots()
        buildMuteButton()
        buildMuteIndicator()

        addView(skeletonView, LayoutParams(MATCH, MATCH))
        addView(noNetView,    LayoutParams(MATCH, MATCH))
        noNetView.visibility = View.GONE
    }

    private fun buildAppBar() {
        val statusH = activity.statusBarHeight
        val bar     = FrameLayout(context)

        val title = TextView(context).apply {
            text     = "Shorts"
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.parseColor("#88000000"))
        }
        bar.addView(title, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity = Gravity.CENTER_VERTICAL or Gravity.START; it.leftMargin = dp(16)
        })

        val searchBtn = buildSvgButton("icons/svg/search.svg")
        searchBtn.setOnClickListener {
            activity.setStatusBarDark(true)
            activity.addContentOverlay(SearchResultsPage(activity, ""))
        }
        bar.addView(searchBtn, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.CENTER_VERTICAL or Gravity.END; it.rightMargin = dp(8)
        })

        addView(bar, FrameLayout.LayoutParams(MATCH, statusH + dp(56)).also {
            it.gravity = Gravity.TOP
        })
    }

    // ── Slides ────────────────────────────────────────────────────────────────

    private fun screenH() = resources.displayMetrics.heightPixels.toFloat()

    private fun createSlide(idx: Int): FrameLayout {
        val h     = screenH().toInt()
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            translationY = idx * screenH()
        }
        val pv = PlayerView(activity).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
        }
        frame.addView(pv, FrameLayout.LayoutParams(MATCH, MATCH))
        slidesContainer.addView(frame, FrameLayout.LayoutParams(MATCH, h))
        slideFrames.add(frame)
        slideViews.add(pv)
        return frame
    }

    private fun applyPositions(deltaY: Float = 0f, animate: Boolean = false) {
        val h = screenH()
        for (i in slideFrames.indices) {
            val target = (i - currentIdx) * h + deltaY
            if (animate) {
                slideFrames[i].animate()
                    .translationY(target).setDuration(420)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            } else {
                slideFrames[i].animate().cancel()
                slideFrames[i].translationY = target
            }
        }
    }

    // ── Swipe ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipeTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = e.rawY; swipeDelta = 0f; isSwiping = true
                slideFrames.forEach { it.animate().cancel() }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) return
                swipeDelta = e.rawY - swipeStartY
                applyPositions(deltaY = swipeDelta, animate = false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping      = false
                lastTouchEndMs = System.currentTimeMillis()
                val threshold  = screenH() * 0.25f
                when {
                    swipeDelta < -threshold && currentIdx < slideFrames.size - 1 -> {
                        currentIdx++
                        applyPositions(animate = true)
                        updateDots()
                        onPageSettled(currentIdx)
                    }
                    swipeDelta > threshold && currentIdx > 0 -> {
                        currentIdx--
                        applyPositions(animate = true)
                        updateDots()
                        onPageSettled(currentIdx)
                    }
                    else -> {
                        applyPositions(animate = true)
                        if (kotlin.math.abs(swipeDelta) < dp(8)) handleTap()
                    }
                }
                swipeDelta = 0f
            }
        }
    }

    private fun handleTap() {
        val p = players[currentIdx] ?: return
        if (p.isPlaying) p.pause() else {
            // Tap para play só funciona se estiver na tab home e em foreground
            if (isOnHomeTab && isInForeground) p.play()
        }
    }

    // ── Dots ──────────────────────────────────────────────────────────────────

    private fun buildDots() {
        dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(dotsContainer, FrameLayout.LayoutParams(dp(6), WRAP).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL; it.rightMargin = dp(10)
        })
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews(); dotViews.clear()
        for (i in videos.indices) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(2).toFloat()
                    setColor(if (i == currentIdx) Color.WHITE else Color.argb(77, 255, 255, 255))
                }
            }
            dotsContainer.addView(dot, LinearLayout.LayoutParams(dp(3), if (i == currentIdx) dp(18) else dp(3)).also {
                it.bottomMargin = dp(6)
            })
            dotViews.add(dot)
        }
    }

    private fun updateDots() {
        for (i in dotViews.indices) {
            val lp     = dotViews[i].layoutParams as LinearLayout.LayoutParams
            val active = i == currentIdx
            lp.height  = if (active) dp(18) else dp(3)
            dotViews[i].layoutParams = lp
            (dotViews[i].background as? GradientDrawable)?.setColor(
                if (active) Color.WHITE else Color.argb(77, 255, 255, 255)
            )
        }
    }

    // ── Mute ──────────────────────────────────────────────────────────────────

    private fun buildMuteButton() {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
        }
        val iconSize = dp(28)
        val iv = android.widget.ImageView(context).apply {
            tag       = "mute_icon"
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
        }
        drawVolumeIcon(iv, false)
        val label = TextView(context).apply {
            text = "Som"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity = Gravity.CENTER
        }
        col.addView(iv,    LinearLayout.LayoutParams(iconSize, iconSize))
        col.addView(label, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        col.setOnClickListener { toggleMute(iv) }
        addView(col, FrameLayout.LayoutParams(dp(64), WRAP).also {
            it.gravity = Gravity.END or Gravity.BOTTOM; it.rightMargin = dp(14); it.bottomMargin = dp(110)
        })
    }

    private fun buildMuteIndicator() {
        muteIndicator = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(166, 0, 0, 0)) }
            alpha = 0f; scaleX = 0f; scaleY = 0f
            isClickable = false; isFocusable = false
        }
        val iv = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE; setColorFilter(Color.WHITE)
        }
        drawVolumeIcon(iv, false)
        muteIndicator.addView(iv, FrameLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER })
        addView(muteIndicator, FrameLayout.LayoutParams(dp(72), dp(72)).also { it.gravity = Gravity.CENTER })
    }

    private fun drawVolumeIcon(iv: android.widget.ImageView, muted: Boolean) {
        val size   = dp(28)
        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c      = Canvas(bmp)
        val s      = size.toFloat()
        val fill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE
            strokeWidth = s * 0.08f; strokeCap = Paint.Cap.ROUND
        }
        val body = Path().apply {
            moveTo(s * 0.12f, s * 0.375f); lineTo(s * 0.12f, s * 0.625f)
            lineTo(s * 0.29f, s * 0.625f); lineTo(s * 0.50f, s * 0.833f)
            lineTo(s * 0.50f, s * 0.167f); lineTo(s * 0.29f, s * 0.375f); close()
        }
        c.drawPath(body, fill)
        if (!muted) {
            c.drawArc(RectF(s * 0.54f, s * 0.27f, s * 0.90f, s * 0.73f), -60f, 120f, false, stroke)
        } else {
            c.drawLine(s * 0.60f, s * 0.33f, s * 0.88f, s * 0.67f, stroke)
            c.drawLine(s * 0.88f, s * 0.33f, s * 0.60f, s * 0.67f, stroke)
        }
        iv.setImageBitmap(bmp)
    }

    private fun toggleMute(iv: android.widget.ImageView) {
        isMuted = !isMuted
        players[currentIdx]?.volume = if (isMuted) 0f else 1f
        drawVolumeIcon(iv, isMuted)
        val indicatorIv = muteIndicator.getChildAt(0) as? android.widget.ImageView
        indicatorIv?.let { drawVolumeIcon(it, isMuted) }
        muteIndicator.animate().cancel()
        muteIndicator.scaleX = 0f; muteIndicator.scaleY = 0f; muteIndicator.alpha = 0f
        muteIndicator.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
            .withEndAction {
                mainHandler.postDelayed({
                    muteIndicator.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).start()
                }, 900)
            }.start()
    }

    // ── Progress bar ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun buildProgressBar() {
        val barH      = dp(3)
        val touchH    = dp(28)
        val container = FrameLayout(context)

        val bg = View(context).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
        container.addView(bg, FrameLayout.LayoutParams(MATCH, barH).also { it.gravity = Gravity.CENTER_VERTICAL })
        progressFill.setBackgroundColor(Color.WHITE)
        container.addView(progressFill, FrameLayout.LayoutParams(0, barH).also { it.gravity = Gravity.CENTER_VERTICAL })

        val touchArea = View(context)
        touchArea.setOnTouchListener { _, e ->
            val player = players[currentIdx] ?: return@setOnTouchListener false
            val dur    = player.duration; if (dur <= 0) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isSeekingProgress = true
                    val pct = (e.x / container.width).coerceIn(0f, 1f)
                    player.seekTo((pct * dur).toLong())
                    updateProgressFill((pct * container.width).toInt(), container.width)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isSeekingProgress = false
            }
            true
        }
        container.addView(touchArea, FrameLayout.LayoutParams(MATCH, touchH).also { it.gravity = Gravity.CENTER_VERTICAL })
        addView(container, FrameLayout.LayoutParams(MATCH, touchH).also {
            it.gravity = Gravity.BOTTOM; it.bottomMargin = 0
        })
    }

    private fun updateProgressFill(fillPx: Int, totalPx: Int) {
        val lp    = progressFill.layoutParams as FrameLayout.LayoutParams
        lp.width  = fillPx.coerceIn(0, totalPx)
        progressFill.layoutParams = lp
    }

    // ── Rede ──────────────────────────────────────────────────────────────────

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkNetAndLoad() {
        if (!isOnline()) { showNoNet(); mainHandler.postDelayed({ checkNetAndLoad() }, 3000) }
        else             { hideNoNet(); startEverything() }
    }

    private fun showNoNet() { skeletonView.visibility = View.GONE; noNetView.visibility = View.VISIBLE }
    private fun hideNoNet() { noNetView.visibility = View.GONE; skeletonView.visibility = View.VISIBLE }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun startEverything() {
        currentPage = 1; currentIdx = 0; firstShown = false; isFetching = false
        wasPlayingBeforePause = false
        videos.clear()
        players.values.forEach { it.release() }; players.clear()
        slideFrames.clear(); slideViews.clear()
        slidesContainer.removeAllViews()
        dotsContainer.removeAllViews(); dotViews.clear()
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
                val startIdx = videos.size
                videos.addAll(result)
                currentPage = page
                for (i in startIdx until videos.size) createSlide(i)
                rebuildDots()
                if (!firstShown) {
                    firstShown = true
                    skeletonView.visibility = View.GONE
                    for (i in 0 until minOf(5, videos.size)) preloadPlayer(i)
                    awaitAndPlay(0)
                }
                if (videos.size < 20 && !isFetching)
                    mainHandler.postDelayed({ fetchPage(page + 1) }, 800)
            }
        }.start()
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private fun preloadPlayer(idx: Int) {
        val url = videos.getOrNull(idx)?.link ?: return
        if (players.containsKey(idx)) return

        val player    = ExoPlayer.Builder(context).build()
        players[idx]  = player

        val dsFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Referer" to "https://www.pornhub.com/"
            ))
        val source = HlsMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(source)
        player.repeatMode    = Player.REPEAT_MODE_ONE
        player.volume        = 0f
        player.prepare()
        player.playWhenReady = false

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    mainHandler.post { slideViews.getOrNull(idx)?.player = player }
                }
            }
        })
    }

    private fun awaitAndPlay(idx: Int) {
        val p = players[idx]
        if (p == null) { mainHandler.postDelayed({ awaitAndPlay(idx) }, 100); return }

        fun start() {
            // Só faz play se estiver na tab home E em foreground
            if (!isOnHomeTab || !isInForeground) {
                p.volume        = 0f
                p.playWhenReady = false
                slideViews.getOrNull(idx)?.player = p
                wasPlayingBeforePause = true  // regista intenção de play para quando regressar
                return
            }
            p.volume        = if (isMuted) 0f else 1f
            p.playWhenReady = true; p.play()
            slideViews.getOrNull(idx)?.player = p
            wasPlayingBeforePause = true
            startProgressUpdater(p)
        }

        if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING) {
            start()
        } else {
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) start()
                }
            })
        }
    }

    private fun onPageSettled(idx: Int) {
        // Pausa todos excepto o atual
        players.forEach { (i, p) -> if (i != idx) { p.volume = 0f; p.pause() } }

        val cur = players[idx]
        if (cur != null) {
            // Só faz play se estiver na tab home E em foreground
            if (isOnHomeTab && isInForeground) {
                cur.volume        = if (isMuted) 0f else 1f
                cur.playWhenReady = true; cur.play()
                startProgressUpdater(cur)
                wasPlayingBeforePause = true
            } else {
                cur.volume        = 0f
                cur.playWhenReady = false
                wasPlayingBeforePause = true  // intenção de play guardada
            }
        } else {
            preloadPlayer(idx)
            awaitAndPlay(idx)
        }

        for (offset in 1..3) preloadPlayer(idx + offset)
        if (idx > 0) preloadPlayer(idx - 1)

        players.keys.filter { it < idx - 2 || it > idx + 5 }.forEach { i ->
            players[i]?.release(); players.remove(i)
            slideViews.getOrNull(i)?.player = null
        }

        if (idx >= videos.size - 6 && !isFetching) fetchPage(currentPage + 1)
    }

    // ── Progress updater ──────────────────────────────────────────────────────

    private fun stopProgressUpdater() {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        progressJob = null
    }

    private fun startProgressUpdater(player: ExoPlayer) {
        stopProgressUpdater()
        val totalW = resources.displayMetrics.widthPixels
        val run    = object : Runnable {
            override fun run() {
                if (!isSeekingProgress) {
                    val dur = player.duration; val pos = player.currentPosition
                    if (dur > 0) updateProgressFill((pos.toFloat() / dur * totalW).toInt(), totalW)
                }
                mainHandler.postDelayed(this, 200)
            }
        }
        progressJob = run; mainHandler.post(run)
    }

    // ── SVG helper ────────────────────────────────────────────────────────────

    private fun buildSvgButton(path: String): FrameLayout {
        val btn = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            val iv = android.widget.ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(Color.WHITE)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            btn.addView(iv, FrameLayout.LayoutParams(px, px).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        return btn
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildSkeletonView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#18191A")) }
        val spinnerSize = dp(56)
        val spinner = object : View(context) {
            private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#242526"); style = Paint.Style.STROKE
                strokeWidth = dp(4).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private val paintFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE
                strokeWidth = dp(4).toFloat(); strokeCap = Paint.Cap.ROUND
            }
            private var angle = 0f
            private val run   = object : Runnable {
                override fun run() { angle = (angle + 8f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = cx - dp(4)
                c.drawCircle(cx, cy, r, paintBg)
                c.drawArc(cx - r, cy - r, cx + r, cy + r, angle, 90f, false, paintFg)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(spinnerSize, spinnerSize).also { it.gravity = Gravity.CENTER })

        val rightCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        repeat(4) {
            val circle = View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#242526")) }
            }
            rightCol.addView(circle, LinearLayout.LayoutParams(dp(48), dp(48)).also { it.bottomMargin = dp(16) })
        }
        frame.addView(rightCol, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity = Gravity.BOTTOM or Gravity.END; it.rightMargin = dp(16); it.bottomMargin = dp(16)
        })

        val leftCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        listOf(dp(64), dp(128), dp(96)).forEach { w ->
            val bar = View(context).apply {
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#242526"))
                }
            }
            leftCol.addView(bar, LinearLayout.LayoutParams(w, dp(14)).also { it.bottomMargin = dp(8) })
        }
        frame.addView(leftCol, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity = Gravity.BOTTOM or Gravity.START; it.leftMargin = dp(16); it.bottomMargin = dp(16)
        })

        frame.post { animateSkeleton(rightCol); animateSkeleton(leftCol) }
        return frame
    }

    private fun animateSkeleton(view: View) {
        view.animate().alpha(0.4f).setDuration(750).withEndAction {
            view.animate().alpha(1f).setDuration(750).withEndAction {
                if (view.isAttachedToWindow) animateSkeleton(view)
            }.start()
        }.start()
    }

    // ── No net ────────────────────────────────────────────────────────────────

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        ll.addView(TextView(context).apply {
            text = "Sem ligação à internet"; textSize = 15f
            setTextColor(Color.argb(180, 255, 255, 255)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.bottomMargin = dp(20) })
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
        frame.addView(ll, FrameLayout.LayoutParams(WRAP, WRAP).also { it.gravity = Gravity.CENTER })
        return frame
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun onDestroy() {
        stopProgressUpdater()
        players.values.forEach { it.release() }
        players.clear()
    }
}