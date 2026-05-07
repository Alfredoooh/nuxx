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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.nuxx.app.MainActivity
import kotlin.math.abs

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

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
    private val WRAP_CONTENT  = LayoutParams.WRAP_CONTENT

    // ExoPlayer
    private var exoPlayer: ExoPlayer? = null
    private val playerView = androidx.media3.ui.PlayerView(activity).apply {
        useController = false
        setShutterBackgroundColor(Color.BLACK)
    }

    private val playerFrame  = FrameLayout(context)
    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()

    // Overlay lateral
    private val sidePanel  = LinearLayout(context)
    private val btnLike    = buildCircleBtn()
    private val tvLikes    = buildCountLabel("0")
    private val btnComment = buildCircleBtn()
    private val tvComments = buildCountLabel("")
    private val btnShare   = buildCircleBtn()
    private val tvShares   = buildCountLabel("")

    // Info em baixo
    private val infoPanel = LinearLayout(context)
    private val tvAuthor  = buildAuthorLabel("@shorty")
    private val tvTitle   = buildTitleLabel("")

    // Barra de progresso
    private val progressFill = View(context)
    private var progressJob: Runnable? = null

    private var touchStartY = 0f
    private var touchStartX = 0f
    private var isDragging  = false
    private val SWIPE_THRESH = 80f

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        playerFrame.addView(playerView, lp(MATCH_PARENT, MATCH_PARENT))
        addView(playerFrame, lp(MATCH_PARENT, MATCH_PARENT))

        // Gradiente inferior
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(200, 0, 0, 0), Color.argb(80, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH_PARENT, dp(280)).also {
            it.gravity = Gravity.BOTTOM
        })

        // Painel lateral
        sidePanel.orientation = LinearLayout.VERTICAL
        sidePanel.gravity     = Gravity.CENTER_HORIZONTAL
        sidePanel.setPadding(0, 0, dp(10), dp(100))

        renderSvg(btnLike, SvgIcon.HEART_OUTLINE, Color.WHITE)
        sidePanel.addView(sideBtnCol(btnLike, tvLikes), sideLp().also { it.bottomMargin = dp(18) })

        renderSvg(btnComment, SvgIcon.COMMENT, Color.WHITE)
        sidePanel.addView(sideBtnCol(btnComment, tvComments), sideLp().also { it.bottomMargin = dp(18) })

        renderSvg(btnShare, SvgIcon.SHARE, Color.WHITE)
        sidePanel.addView(sideBtnCol(btnShare, tvShares), sideLp())

        addView(sidePanel, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).also {
            it.gravity = Gravity.END or Gravity.BOTTOM
        })

        // Info em baixo esquerdo
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(dp(14), 0, dp(90), dp(28))
        infoPanel.addView(tvAuthor, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.bottomMargin = dp(6) })
        infoPanel.addView(tvTitle,  lp(WRAP_CONTENT, WRAP_CONTENT))
        addView(infoPanel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })

        // Barra de progresso
        val progressBar = View(context).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
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

        playerFrame.setOnTouchListener { _, e -> handleTouch(e) }
        infoPanel.setOnTouchListener   { _, e -> handleTouch(e) }

        btnLike.setOnClickListener    { toggleLike() }
        btnShare.setOnClickListener   { showShareDialog() }
        btnComment.setOnClickListener { }
    }

    private fun sideBtnCol(btn: ImageView, label: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity     = Gravity.CENTER_HORIZONTAL
        addView(btn,   lp(dp(52), dp(52)))
        addView(label, lp(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(4) })
    }

    private fun sideLp() = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

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

    // ── Fetch viewkeys ────────────────────────────────────────────────────────

    private fun startFetching() { currentPage = 1; viewKeys.clear(); fetchPage(1) }

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
                if (viewKeys.size >= 3 && !playerReady) {
                    playerReady = true
                    loadVideo(0)
                }
            }
        }.start()
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private fun loadVideo(index: Int) {
        if (index < 0 || index >= viewKeys.size) return
        val vk = viewKeys[index]

        tvAuthor.text = "@shorty_${index + 1}"
        tvTitle.text  = ""
        isLiked = false
        updateLikeIcon()

        showLoader()

        // Pré-carrega mais viewkeys
        if (index >= viewKeys.size - 50 && !isFetching && viewKeys.size < TARGET_KEYS) {
            fetchPage(++currentPage)
        }

        Thread {
            val url = ShortiesApi.fetchVideoUrl(vk)
            mainHandler.post {
                if (url == null) {
                    // Salta para o próximo se falhar
                    currentIdx++
                    loadVideo(currentIdx)
                    return@post
                }
                playUrl(url)
            }
        }.start()
    }

    private fun playUrl(url: String) {
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            playerView.player = player

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
            player.repeatMode  = Player.REPEAT_MODE_ONE
            player.volume      = if (isMuted) 0f else 1f
            player.prepare()
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) hideLoader()
                    if (state == Player.STATE_ENDED) {
                        currentIdx++
                        loadVideo(currentIdx)
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    currentIdx++
                    loadVideo(currentIdx)
                }
            })

            startProgressUpdater(player)
        }
    }

    private fun startProgressUpdater(player: ExoPlayer) {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        val run = object : Runnable {
            override fun run() {
                val duration = player.duration
                val pos      = player.currentPosition
                if (duration > 0) {
                    val pct   = (pos.toFloat() / duration * width).toInt()
                    progressFill.layoutParams = (progressFill.layoutParams as FrameLayout.LayoutParams).also {
                        it.width = pct
                    }
                    progressFill.requestLayout()
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        progressJob = run
        mainHandler.post(run)
    }

    private fun showLoader() {
        loadingView.alpha      = 1f
        loadingView.visibility = VISIBLE
    }

    private fun hideLoader() {
        loadingView.animate().alpha(0f).setDuration(250).withEndAction {
            loadingView.visibility = GONE
            loadingView.alpha = 1f
        }.start()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    private fun handleTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = e.y; touchStartX = e.x; isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - touchStartY
                val dx = e.x - touchStartX
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
                } else {
                    // Tap = pause/play
                    exoPlayer?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                }
            }
        }
        return true
    }

    private fun snapTo(onEnd: () -> Unit) {
        playerFrame.animate().translationY(0f).setDuration(100)
            .setInterpolator(DecelerateInterpolator()).withEndAction(onEnd).start()
    }

    // ── Acções ────────────────────────────────────────────────────────────────

    private fun toggleLike() { isLiked = !isLiked; updateLikeIcon() }

    private fun toggleMute() {
        isMuted = !isMuted
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    private fun showShareDialog() {
        if (currentIdx >= viewKeys.size) return
        val vk  = viewKeys[currentIdx]
        val url = "https://www.pornhub.com/view_video.php?viewkey=$vk"
        android.app.AlertDialog.Builder(context)
            .setTitle("Partilhar")
            .setItems(arrayOf("Copiar link", "Partilhar via...")) { _, w ->
                when (w) {
                    0 -> {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("url", url))
                        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                    }
                    1 -> context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
                            }, "Partilhar"
                        )
                    )
                }
            }.create().also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#1A1A1A")))
                it.show()
            }
    }

    // ── Ícones ────────────────────────────────────────────────────────────────

    enum class SvgIcon { HEART_OUTLINE, HEART_FILLED, COMMENT, SHARE }

    private fun buildCircleBtn() = ImageView(context).apply {
        scaleType  = ImageView.ScaleType.CENTER_INSIDE
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
                val path = Path().apply {
                    moveTo(cx, cy + u * 2.8f)
                    cubicTo(cx - u * 0.8f, cy + u * 1.8f, cx - u * 3.2f, cy + u * 0.8f, cx - u * 3.2f, cy - u * 0.6f)
                    cubicTo(cx - u * 3.2f, cy - u * 2.4f, cx - u * 1.2f, cy - u * 3.0f, cx, cy - u * 1.4f)
                    cubicTo(cx + u * 1.2f, cy - u * 3.0f, cx + u * 3.2f, cy - u * 2.4f, cx + u * 3.2f, cy - u * 0.6f)
                    cubicTo(cx + u * 3.2f, cy + u * 0.8f, cx + u * 0.8f, cy + u * 1.8f, cx, cy + u * 2.8f)
                    close()
                }
                if (icon == SvgIcon.HEART_FILLED) c.drawPath(path, fi) else c.drawPath(path, st)
            }
            SvgIcon.COMMENT -> {
                val r    = u * 2.6f
                val rect = RectF(cx - r, cy - r * 1.1f, cx + r, cy + r * 0.8f)
                c.drawRoundRect(rect, u * 0.8f, u * 0.8f, st)
                val tail = Path().apply {
                    moveTo(cx - u * 0.4f, cy + r * 0.8f)
                    lineTo(cx - u * 1.4f, cy + r * 1.6f)
                    lineTo(cx + u * 0.8f, cy + r * 0.8f)
                }
                c.drawPath(tail, st)
                st.strokeWidth = dp(1.5f).toFloat()
                c.drawLine(cx - u * 1.6f, cy - u * 0.6f, cx + u * 1.6f, cy - u * 0.6f, st)
                c.drawLine(cx - u * 1.6f, cy + u * 0.6f, cx + u * 0.8f, cy + u * 0.6f, st)
            }
            SvgIcon.SHARE -> {
                st.strokeWidth = dp(2).toFloat()
                c.drawLine(cx, cy + u * 2.0f, cx, cy - u * 1.8f, st)
                val arrow = Path().apply {
                    moveTo(cx - u * 1.6f, cy - u * 0.4f)
                    lineTo(cx, cy - u * 2.2f)
                    lineTo(cx + u * 1.6f, cy - u * 0.4f)
                }
                c.drawPath(arrow, st)
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
                override fun run() { angle = (angle + 8f) % 360f; invalidate(); postDelayed(this, 14) }
            }
            init { post(run) }
            override fun onDraw(c: Canvas) {
                val r = width / 2f * 0.65f
                c.drawArc(width / 2f - r, height / 2f - r, width / 2f + r, height / 2f + r, angle, 270f, false, paint)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).also { it.gravity = Gravity.CENTER })
        return frame
    }

    private fun buildNoNetView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val ll    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
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
        progressJob?.let { mainHandler.removeCallbacks(it) }
        exoPlayer?.release()
        exoPlayer = null
    }
}