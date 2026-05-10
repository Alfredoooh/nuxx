package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private val MATCH = LayoutParams.MATCH_PARENT
    private val WRAP  = LayoutParams.WRAP_CONTENT

    private lateinit var recycler:      RecyclerView
    private lateinit var videoAdapter:  ShortiesAdapter
    private lateinit var layoutManager: LinearLayoutManager

    // Swipe manual (igual ao HTML)
    private var swipeStartY    = 0f
    private var swipeDelta     = 0f
    private var isSwiping      = false
    private var lastTouchEndMs = 0L

    // Progress bar
    private val progressBar  = View(context)
    private val progressFill = View(context)
    private var progressJob: Runnable? = null
    private var isSeekingProgress = false

    // Dots laterais (igual ao HTML)
    private lateinit var dotsContainer: LinearLayout
    private val dotViews = mutableListOf<View>()

    // Mute indicator (igual ao HTML)
    private lateinit var muteIndicator: FrameLayout
    private lateinit var muteIconPath: String // controlo via tag

    private val skeletonView = buildSkeletonView()
    private val noNetView    = buildNoNetView()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        videoAdapter  = ShortiesAdapter()

        // RecyclerView SEM PagerSnapHelper — swipe é manual como no HTML
        recycler = RecyclerView(context).apply {
            layoutManager  = this@ShortiesPage.layoutManager
            adapter        = videoAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
        }

        // Intercept todos os touch events para swipe manual
        recycler.setOnTouchListener { _, e -> handleSwipeTouch(e); true }

        addView(recycler, LayoutParams(MATCH, MATCH))

        // Gradiente fundo
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT)
            )
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
        val bar = FrameLayout(context)

        val title = TextView(context).apply {
            text      = "Shorts"
            textSize  = 26f
            typeface  = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 2f, Color.parseColor("#88000000"))
        }
        bar.addView(title, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity    = Gravity.CENTER_VERTICAL or Gravity.START
            it.leftMargin = dp(16)
        })

        val searchBtn = buildSvgButton("icons/svg/search.svg")
        searchBtn.setOnClickListener {
            activity.setStatusBarDark(true)
            activity.addContentOverlay(SearchResultsPage(activity, ""))
        }
        bar.addView(searchBtn, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity     = Gravity.CENTER_VERTICAL or Gravity.END
            it.rightMargin = dp(8)
        })

        addView(bar, FrameLayout.LayoutParams(MATCH, statusH + dp(56)).also {
            it.gravity = Gravity.TOP
        })
    }

    // Dots laterais — igual ao HTML (3px largura, 3px altura inactivo, 18px activo)
    private fun buildDots() {
        dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
        }
        addView(dotsContainer, FrameLayout.LayoutParams(dp(6), WRAP).also {
            it.gravity      = Gravity.END or Gravity.CENTER_VERTICAL
            it.rightMargin  = dp(10)
        })
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        dotViews.clear()
        for (i in videos.indices) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(2).toFloat()
                    setColor(if (i == currentIdx) Color.WHITE else Color.argb(77, 255, 255, 255))
                }
            }
            val lp = LinearLayout.LayoutParams(dp(3), if (i == currentIdx) dp(18) else dp(3)).also {
                it.bottomMargin = dp(6)
            }
            dotsContainer.addView(dot, lp)
            dotViews.add(dot)
        }
    }

    private fun updateDots() {
        for (i in dotViews.indices) {
            val dot = dotViews[i]
            val lp  = dot.layoutParams as LinearLayout.LayoutParams
            val isActive = i == currentIdx
            lp.height = if (isActive) dp(18) else dp(3)
            dot.layoutParams = lp
            (dot.background as? GradientDrawable)?.setColor(
                if (isActive) Color.WHITE else Color.argb(77, 255, 255, 255)
            )
        }
    }

    // Botão mute lateral (igual ao HTML — lado direito, acima dos dots)
    private fun buildMuteButton() {
        val btn = FrameLayout(context).apply {
            isClickable  = true
            isFocusable  = true
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
        }

        // Ícone SVG de som — fallback para canvas se SVG falhar
        val iconSize = dp(28)
        val iv = android.widget.ImageView(context).apply {
            tag       = "mute_icon"
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
        }
        drawVolumeIcon(iv, muted = false)

        val label = TextView(context).apply {
            text      = "Som"
            textSize  = 11f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(3f, 0f, 1f, Color.parseColor("#B3000000"))
            gravity   = Gravity.CENTER
        }

        col.addView(iv,    LinearLayout.LayoutParams(iconSize, iconSize))
        col.addView(label, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(4) })
        btn.addView(col,   FrameLayout.LayoutParams(WRAP, WRAP).also { it.gravity = Gravity.CENTER })

        btn.setOnClickListener { toggleMute(iv) }

        addView(btn, FrameLayout.LayoutParams(dp(56), WRAP).also {
            it.gravity      = Gravity.END or Gravity.BOTTOM
            it.rightMargin  = dp(14)
            it.bottomMargin = dp(110)
        })
    }

    // Indicador de mute central (igual ao HTML)
    private fun buildMuteIndicator() {
        muteIndicator = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.OVAL
                setColor(Color.argb(166, 0, 0, 0))
            }
            alpha      = 0f
            scaleX     = 0f
            scaleY     = 0f
        }
        val iconSize = dp(36)
        val iv = android.widget.ImageView(context).apply {
            tag       = "mute_indicator_icon"
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
        }
        drawVolumeIcon(iv, muted = false)
        muteIndicator.addView(iv, FrameLayout.LayoutParams(iconSize, iconSize).also {
            it.gravity = Gravity.CENTER
        })
        addView(muteIndicator, FrameLayout.LayoutParams(dp(72), dp(72)).also {
            it.gravity = Gravity.CENTER
        })
    }

    private fun drawVolumeIcon(iv: android.widget.ImageView, muted: Boolean) {
        val size = dp(28)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val p    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val s = size.toFloat()
        if (!muted) {
            // Forma de altifalante simples
            val path = Path().apply {
                moveTo(s * 0.12f, s * 0.375f)
                lineTo(s * 0.12f, s * 0.625f)
                lineTo(s * 0.29f, s * 0.625f)
                lineTo(s * 0.50f, s * 0.833f)
                lineTo(s * 0.50f, s * 0.167f)
                lineTo(s * 0.29f, s * 0.375f)
                close()
            }
            c.drawPath(path, p)
            // Onda curta
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.WHITE
                style     = Paint.Style.STROKE
                strokeWidth = s * 0.07f
                strokeCap = Paint.Cap.ROUND
            }
            val oval = RectF(s * 0.54f, s * 0.29f, s * 0.88f, s * 0.71f)
            c.drawArc(oval, -60f, 120f, false, wavePaint)
        } else {
            // Altifalante mudo (X)
            val path = Path().apply {
                moveTo(s * 0.12f, s * 0.375f)
                lineTo(s * 0.12f, s * 0.625f)
                lineTo(s * 0.29f, s * 0.625f)
                lineTo(s * 0.50f, s * 0.833f)
                lineTo(s * 0.50f, s * 0.167f)
                lineTo(s * 0.29f, s * 0.375f)
                close()
            }
            c.drawPath(path, p)
            val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = Color.WHITE
                style     = Paint.Style.STROKE
                strokeWidth = s * 0.09f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(s * 0.60f, s * 0.35f, s * 0.88f, s * 0.65f, xPaint)
            c.drawLine(s * 0.88f, s * 0.35f, s * 0.60f, s * 0.65f, xPaint)
        }
        iv.setImageBitmap(bmp)
    }

    private fun toggleMute(iv: android.widget.ImageView) {
        isMuted = !isMuted
        players[currentIdx]?.volume = if (isMuted) 0f else 1f
        drawVolumeIcon(iv, isMuted)

        // Actualiza indicador central
        val indicatorIv = muteIndicator.getChildAt(0) as? android.widget.ImageView
        if (indicatorIv != null) drawVolumeIcon(indicatorIv, isMuted)

        // Mostra indicador (igual ao HTML: scale+opacity)
        muteIndicator.animate().cancel()
        muteIndicator.scaleX = 0f; muteIndicator.scaleY = 0f; muteIndicator.alpha = 0f
        muteIndicator.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(150)
            .withEndAction {
                mainHandler.postDelayed({
                    muteIndicator.animate().alpha(0f).setDuration(300).start()
                }, 900)
            }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildProgressBar() {
        val screenW = resources.displayMetrics.widthPixels
        val barH    = dp(3)
        val touchH  = dp(28)

        val container = FrameLayout(context)

        progressBar.setBackgroundColor(Color.argb(60, 255, 255, 255))
        container.addView(progressBar, FrameLayout.LayoutParams(MATCH, barH).also {
            it.gravity = Gravity.CENTER_VERTICAL
        })

        progressFill.setBackgroundColor(Color.WHITE)
        container.addView(progressFill, FrameLayout.LayoutParams(0, barH).also {
            it.gravity = Gravity.CENTER_VERTICAL
        })

        val touchArea = View(context)
        touchArea.setOnTouchListener { _, e ->
            val player = players[currentIdx] ?: return@setOnTouchListener false
            val dur = player.duration
            if (dur <= 0) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isSeekingProgress = true
                    val pct    = (e.x / container.width).coerceIn(0f, 1f)
                    player.seekTo((pct * dur).toLong())
                    updateProgressFill((pct * container.width).toInt(), container.width)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSeekingProgress = false
                }
            }
            true
        }
        container.addView(touchArea, FrameLayout.LayoutParams(MATCH, touchH).also {
            it.gravity = Gravity.CENTER_VERTICAL
        })

        addView(container, FrameLayout.LayoutParams(MATCH, touchH).also {
            it.gravity      = Gravity.BOTTOM
            it.bottomMargin = dp(0)
        })
    }

    private fun updateProgressFill(fillPx: Int, totalPx: Int) {
        val lp = progressFill.layoutParams as FrameLayout.LayoutParams
        lp.width = fillPx.coerceIn(0, totalPx)
        progressFill.layoutParams = lp
    }

    // ── Swipe manual (igual ao HTML) ──────────────────────────────────────────

    private fun handleSwipeTouch(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = e.rawY
                swipeDelta  = 0f
                isSwiping   = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) return
                swipeDelta = e.rawY - swipeStartY
                applyDragOffset(swipeDelta)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                lastTouchEndMs = System.currentTimeMillis()
                val threshold = resources.displayMetrics.heightPixels * 0.25f
                when {
                    swipeDelta < -threshold && currentIdx < videos.size - 1 -> goTo(currentIdx + 1)
                    swipeDelta >  threshold && currentIdx > 0               -> goTo(currentIdx - 1)
                    else -> {
                        snapToCurrentWithAnim()
                        if (kotlin.math.abs(swipeDelta) < dp(8)) handleTap()
                    }
                }
                swipeDelta = 0f
            }
        }
    }

    private fun applyDragOffset(deltaY: Float) {
        val h = resources.displayMetrics.heightPixels.toFloat()
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            val pos   = recycler.getChildAdapterPosition(child)
            val base  = (pos - currentIdx) * h
            child.translationY = base + deltaY
        }
    }

    private fun snapToCurrentWithAnim() {
        val h = resources.displayMetrics.heightPixels.toFloat()
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            val pos   = recycler.getChildAdapterPosition(child)
            val base  = (pos - currentIdx) * h
            child.animate()
                .translationY(base)
                .setDuration(420)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .start()
        }
    }

    private fun goTo(idx: Int) {
        if (idx < 0 || idx >= videos.size) return
        players[currentIdx]?.also { it.volume = 0f; it.pause() }
        currentIdx = idx

        val h = resources.displayMetrics.heightPixels.toFloat()
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            val pos   = recycler.getChildAdapterPosition(child)
            val base  = (pos - currentIdx) * h
            child.animate()
                .translationY(base)
                .setDuration(420)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .start()
        }

        // Faz scroll no recycler para manter o item visível
        layoutManager.scrollToPositionWithOffset(idx, 0)

        updateDots()
        onPageSettled(idx)
    }

    private fun handleTap() {
        val p = players[currentIdx] ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    // ── Page settled ──────────────────────────────────────────────────────────

    private fun onPageSettled(idx: Int) {
        players.forEach { (i, p) ->
            if (i != idx) { p.volume = 0f; p.pause() }
        }

        val cur = players[idx]
        if (cur != null) {
            cur.volume        = if (isMuted) 0f else 1f
            cur.playWhenReady = true
            cur.play()
            startProgressUpdater(cur)
        }

        for (offset in 1..3) preloadPlayer(idx + offset)
        if (idx > 0) preloadPlayer(idx - 1)

        players.keys.filter { it < idx - 2 || it > idx + 5 }.forEach { i ->
            players[i]?.release(); players.remove(i)
        }

        if (idx >= videos.size - 6 && !isFetching) fetchPage(currentPage + 1)
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
        videos.clear()
        players.values.forEach { it.release() }; players.clear()
        videoAdapter.clear()
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
                currentPage = page
                videoAdapter.addVideos(result)
                rebuildDots()

                if (!firstShown) {
                    firstShown = true
                    skeletonView.visibility = View.GONE
                    for (i in 0 until minOf(5, videos.size)) preloadPlayer(i)
                    awaitAndPlay(0)
                }

                if (videos.size < 20 && !isFetching) {
                    mainHandler.postDelayed({ fetchPage(page + 1) }, 800)
                }
            }
        }.start()
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private fun preloadPlayer(idx: Int) {
        val url = videos.getOrNull(idx)?.link ?: return
        if (players.containsKey(idx)) return

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

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    mainHandler.post { videoAdapter.bindPlayer(idx, player) }
                }
            }
        })
    }

    private fun awaitAndPlay(idx: Int) {
        val p = players[idx]
        if (p == null) { mainHandler.postDelayed({ awaitAndPlay(idx) }, 100); return }
        if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING) {
            p.volume = if (isMuted) 0f else 1f
            p.playWhenReady = true; p.play()
            videoAdapter.bindPlayer(idx, p)
            startProgressUpdater(p)
        } else {
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        p.volume = if (isMuted) 0f else 1f
                        p.playWhenReady = true; p.play()
                        videoAdapter.bindPlayer(idx, p)
                        startProgressUpdater(p)
                    }
                }
            })
        }
    }

    // ── Progress updater ──────────────────────────────────────────────────────

    private fun startProgressUpdater(player: ExoPlayer) {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        val totalW = resources.displayMetrics.widthPixels
        val run = object : Runnable {
            override fun run() {
                if (!isSeekingProgress) {
                    val dur = player.duration
                    val pos = player.currentPosition
                    if (dur > 0) updateProgressFill((pos.toFloat() / dur * totalW).toInt(), totalW)
                }
                mainHandler.postDelayed(this, 200)
            }
        }
        progressJob = run
        mainHandler.post(run)
    }

    // ── SVG helper ────────────────────────────────────────────────────────────

    private fun buildSvgButton(path: String): FrameLayout {
        val btn = FrameLayout(context).apply { isClickable = true; isFocusable = true }
        try {
            val px  = dp(24)
            val svg = SVG.getFromAsset(activity.assets, path)
            svg.documentWidth  = px.toFloat(); svg.documentHeight = px.toFloat()
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
            private val run = object : Runnable {
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
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
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
        val ll = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
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
        progressJob?.let { mainHandler.removeCallbacks(it) }
        players.values.forEach { it.release() }
        players.clear()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ══════════════════════════════════════════════════════════════════════════

    inner class ShortiesAdapter : RecyclerView.Adapter<ShortiesAdapter.VH>() {

        private val items       = mutableListOf<ShortVideo>()
        private val playerViews = mutableMapOf<Int, PlayerView>()

        fun addVideos(list: List<ShortVideo>) {
            val start = items.size; items.addAll(list)
            notifyItemRangeInserted(start, list.size)
        }

        fun clear() { items.clear(); playerViews.clear(); notifyDataSetChanged() }

        fun bindPlayer(idx: Int, player: ExoPlayer) {
            playerViews[idx]?.let { pv -> if (pv.player != player) pv.player = player }
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val screenH = resources.displayMetrics.heightPixels
            val root = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, screenH)
                setBackgroundColor(Color.BLACK)
            }
            val pv = PlayerView(activity).apply {
                useController = false
                setShutterBackgroundColor(Color.BLACK)
            }
            root.addView(pv, FrameLayout.LayoutParams(MATCH, MATCH))
            return VH(root, pv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            playerViews[position] = holder.playerView
            players[position]?.let { p -> holder.playerView.player = p }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            holder.playerView.player = null
        }

        inner class VH(root: FrameLayout, val playerView: PlayerView) :
            RecyclerView.ViewHolder(root)
    }
}