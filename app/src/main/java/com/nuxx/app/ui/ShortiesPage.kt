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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ShortiesPage(private val activity: MainActivity) : FrameLayout(activity) {

    private val mainHandler  = Handler(Looper.getMainLooper())
    private val videos       = mutableListOf<ShortVideo>()
    private val players      = mutableMapOf<Int, ExoPlayer>()
    private var currentIdx   = 0
    private var currentPage  = 1
    private var isFetching   = false
    private var firstShown   = false

    private val MATCH = LayoutParams.MATCH_PARENT
    private val WRAP  = LayoutParams.WRAP_CONTENT

    // ── RecyclerView (scroll nativo TikTok-style) ─────────────────────────────
    private lateinit var recycler:       RecyclerView
    private lateinit var videoAdapter:   ShortiesAdapter
    private lateinit var layoutManager:  LinearLayoutManager

    private val loadingView  = buildLoadingView()
    private val noNetView    = buildNoNetView()
    private val skeletonView = buildSkeletonView()

    init {
        setBackgroundColor(Color.BLACK)
        buildUI()
        checkNetAndLoad()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        videoAdapter  = ShortiesAdapter()

        recycler = RecyclerView(context).apply {
            layoutManager    = this@ShortiesPage.layoutManager
            adapter          = videoAdapter
            overScrollMode   = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
        }
        // Snap página inteira — o que dá o efeito TikTok
        PagerSnapHelper().attachToRecyclerView(recycler)

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val idx = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (idx >= 0 && idx != currentIdx) {
                        currentIdx = idx
                        onPageSettled(idx)
                    }
                }
            }
        })
        addView(recycler, LayoutParams(MATCH, MATCH))

        // Gradiente bottom sobreposto
        val grad = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(180, 0, 0, 0), Color.TRANSPARENT)
            )
        }
        addView(grad, FrameLayout.LayoutParams(MATCH, dp(160)).also { it.gravity = Gravity.BOTTOM })

        buildAppBar()

        addView(skeletonView, LayoutParams(MATCH, MATCH))
        addView(loadingView,  LayoutParams(MATCH, MATCH))
        addView(noNetView,    LayoutParams(MATCH, MATCH))
        noNetView.visibility   = View.GONE
        loadingView.visibility = View.GONE
    }

    private fun buildAppBar() {
        val statusH = activity.statusBarHeight
        val bar = FrameLayout(context)

        val title = TextView(context).apply {
            text     = "Shorts"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        bar.addView(title, FrameLayout.LayoutParams(WRAP, WRAP).also {
            it.gravity    = Gravity.CENTER_VERTICAL or Gravity.START
            it.leftMargin = dp(16)
        })

        val searchBtn = buildSvgButton("icons/svg/search.svg")
        searchBtn.setOnClickListener {
            activity.addContentOverlay(SearchResultsPage(activity, ""))
        }
        bar.addView(searchBtn, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity     = Gravity.CENTER_VERTICAL or Gravity.END
            it.rightMargin = dp(12)
        })

        addView(bar, FrameLayout.LayoutParams(MATCH, statusH + dp(52)).also {
            it.gravity = Gravity.TOP
        })
    }

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

    // ── Quando o scroll encaixa num vídeo ─────────────────────────────────────

    private fun onPageSettled(idx: Int) {
        // Pausa todos os outros
        players.forEach { (i, p) -> if (i != idx) { p.volume = 0f; p.pause() } }

        // Toca o atual
        players[idx]?.let { p ->
            p.volume = 1f
            p.playWhenReady = true
            p.play()
        } ?: run {
            // Ainda não estava pré-carregado (caso raro), carrega agora
            preloadPlayer(idx) { p ->
                p.volume = 1f; p.playWhenReady = true; p.play()
                videoAdapter.bindPlayer(idx, p)
            }
        }

        // Pré-carrega adjacentes
        preloadPlayer(idx + 1) { p -> videoAdapter.bindPlayer(idx + 1, p) }
        preloadPlayer(idx + 2) { p -> videoAdapter.bindPlayer(idx + 2, p) }
        if (idx > 0) preloadPlayer(idx - 1) { }

        // Limpa players distantes
        players.keys.filter { it < idx - 2 || it > idx + 4 }.forEach { i ->
            players[i]?.release(); players.remove(i)
        }

        // Mais vídeos se estiver perto do fim
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
                val startIdx = videos.size
                videos.addAll(result)
                currentPage = page
                videoAdapter.addVideos(result)

                if (!firstShown) {
                    firstShown = true
                    skeletonView.visibility = View.GONE
                    // Pré-carrega os primeiros 3
                    preloadPlayer(0) { p ->
                        p.volume = 1f; p.playWhenReady = true; p.play()
                        videoAdapter.bindPlayer(0, p)
                    }
                    preloadPlayer(1) { p -> videoAdapter.bindPlayer(1, p) }
                    preloadPlayer(2) { p -> videoAdapter.bindPlayer(2, p) }
                }

                // Continua a pré-carregar em background até ter 30 vídeos prontos
                if (videos.size < 30 && !isFetching) {
                    mainHandler.postDelayed({ fetchPage(page + 1) }, 1500)
                }
            }
        }.start()
    }

    // ── Players ───────────────────────────────────────────────────────────────

    private fun preloadPlayer(idx: Int, onReady: ((ExoPlayer) -> Unit)? = null) {
        val url = videos.getOrNull(idx)?.link ?: return
        players[idx]?.let { onReady?.invoke(it); return }

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
                if (state == Player.STATE_READY) onReady?.invoke(player)
            }
        })
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildSkeletonView(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.parseColor("#111111")) }
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
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
        frame.addView(rightCol, FrameLayout.LayoutParams(WRAP, MATCH).also {
            it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })
        val bottomCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(80), dp(110))
        }
        listOf(140, 200, 160).forEach { w ->
            val v = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#2A2A2A"))
                }
            }
            bottomCol.addView(v, LinearLayout.LayoutParams(dp(w), dp(14)).also { it.bottomMargin = dp(8) })
        }
        frame.addView(bottomCol, FrameLayout.LayoutParams(MATCH, WRAP).also { it.gravity = Gravity.BOTTOM })
        frame.post { animateSkeleton(frame) }
        return frame
    }

    private fun animateSkeleton(view: View) {
        view.animate().alpha(0.5f).setDuration(800).withEndAction {
            view.animate().alpha(1f).setDuration(800).withEndAction {
                if (view.visibility == View.VISIBLE) animateSkeleton(view)
            }.start()
        }.start()
    }

    // ── Loading / NoNet ───────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val frame = FrameLayout(context)
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
        frame.addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).also { it.gravity = Gravity.CENTER })
        return frame
    }

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
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(255, 40, 40, 40))
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
        players.values.forEach { it.release() }
        players.clear()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ══════════════════════════════════════════════════════════════════════════

    inner class ShortiesAdapter : RecyclerView.Adapter<ShortiesAdapter.VH>() {

        private val items = mutableListOf<ShortVideo>()
        // PlayerView reutilizável por posição (máx ~5 em memória via RecyclerView)
        private val playerViews = mutableMapOf<Int, PlayerView>()

        fun addVideos(newItems: List<ShortVideo>) {
            val start = items.size
            items.addAll(newItems)
            notifyItemRangeInserted(start, newItems.size)
        }

        fun clear() {
            items.clear()
            playerViews.clear()
            notifyDataSetChanged()
        }

        /** Chama do exterior para associar um player já pronto a um item visível */
        fun bindPlayer(idx: Int, player: ExoPlayer) {
            playerViews[idx]?.let { pv ->
                if (pv.player != player) pv.player = player
            }
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemRoot = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    resources.displayMetrics.heightPixels   // cada item = altura do ecrã
                )
                setBackgroundColor(Color.BLACK)
            }
            val pv = PlayerView(activity).apply {
                useController = false
                setShutterBackgroundColor(Color.BLACK)
            }
            itemRoot.addView(pv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            // Tap para pause/play
            itemRoot.setOnClickListener {
                val pos = recycler.getChildAdapterPosition(itemRoot)
                players[pos]?.let { p -> if (p.isPlaying) p.pause() else p.play() }
            }

            return VH(itemRoot, pv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            playerViews[position] = holder.playerView
            // Se o player já existe (pré-carregado), associa já
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