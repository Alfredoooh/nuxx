// ExploreView.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.models.VideoSource
import com.doction.webviewapp.theme.AppTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private val RATIOS = listOf(
    16f/9, 4f/3, 16f/9, 16f/9, 4f/3,
    16f/9, 16f/9, 4f/3, 16f/9, 16f/9
)

private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private val crossFade = DrawableCrossFadeFactory.Builder(180)
    .setCrossFadeEnabled(true).build()

private fun referer(src: VideoSource) = when (src) {
    VideoSource.EPORNER        -> "https://www.eporner.com/"
    VideoSource.PORNHUB        -> "https://www.pornhub.com/"
    VideoSource.REDTUBE        -> "https://www.redtube.com/"
    VideoSource.YOUPORN        -> "https://www.youporn.com/"
    VideoSource.XVIDEOS        -> "https://www.xvideos.com/"
    VideoSource.XHAMSTER       -> "https://xhamster.com/"
    VideoSource.SPANKBANG      -> "https://spankbang.com/"
    VideoSource.BRAVOTUBE      -> "https://www.bravotube.net/"
    VideoSource.DRTUBER        -> "https://www.drtuber.com/"
    VideoSource.TXXX           -> "https://www.txxx.com/"
    VideoSource.GOTPORN        -> "https://www.gotporn.com/"
    VideoSource.PORNDIG        -> "https://www.porndig.com/"
    VideoSource.BEEG           -> "https://beeg.com/"
    VideoSource.TUBE8          -> "https://www.tube8.com/"
    VideoSource.TNAFLIX        -> "https://www.tnaflix.com/"
    VideoSource.EMPFLIX        -> "https://www.empflix.com/"
    VideoSource.PORNTREX       -> "https://www.porntrex.com/"
    VideoSource.HCLIPS         -> "https://hclips.com/"
    VideoSource.TUBEDUPE       -> "https://www.tubedupe.com/"
    VideoSource.NUVID          -> "https://www.nuvid.com/"
    VideoSource.SUNPORNO       -> "https://www.sunporno.com/"
    VideoSource.PORNONE        -> "https://pornone.com/"
    VideoSource.SLUTLOAD       -> "https://www.slutload.com/"
    VideoSource.ICEPORN        -> "https://www.iceporn.com/"
    VideoSource.VJAV           -> "https://vjav.com/"
    VideoSource.JIZZBUNKER     -> "https://jizzbunker.com/"
    VideoSource.CLIPHUNTER     -> "https://www.cliphunter.com/"
    VideoSource.SEXVID         -> "https://sexvid.xxx/"
    VideoSource.YEPTUBE        -> "https://www.yeptube.com/"
    VideoSource.XNXX           -> "https://www.xnxx.com/"
    VideoSource.PORNOXO        -> "https://www.pornoxo.com/"
    VideoSource.ANYSEX         -> "https://anysex.com/"
    VideoSource.FUQER          -> "https://fuqer.com/"
    VideoSource.FAPSTER        -> "https://fapster.xxx/"
    VideoSource.PROPORN        -> "https://proporn.com/"
    VideoSource.H2PORN         -> "https://www.h2porn.com/"
    VideoSource.ALPHAPORNO     -> "https://www.alphaporno.com/"
    VideoSource.WATCHMYGF      -> "https://watchmygf.me/"
    VideoSource.XCAFE          -> "https://xcafe.com/"
    VideoSource.TUBECUP        -> "https://tubecup.com/"
    VideoSource.VIDLOX         -> "https://vidlox.me/"
    VideoSource.NAUGHTYAMERICA -> "https://www.naughtyamerica.com/"
}

@SuppressLint("ViewConstructor")
class ExploreView(context: android.content.Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private val recycler:     RecyclerView
    private val chipBar:      HorizontalScrollView
    private val skeletonView: FrameLayout
    private val errorView:    LinearLayout
    private val scrollTopBtn: FrameLayout
    private val swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerView: DrawerView

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip         = 0
    private var isLoading           = true
    private val isFetching          = AtomicBoolean(false)
    private var page                = 1
    private var showingFooterLoader = false

    private val appBarH:    Int
    private val chipBarH:   Int
    private val contentTop: Int
    private val colGapPx:   Int
    private val sidePadPx:  Int

    private var skeletonRunnable: Runnable? = null

    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira","Gay","Lésbicas","BDSM","Anal","Teen"
    )

    // ── CssLoader ────────────────────────────────────────────────────────────

    inner class CssLoader(ctx: android.content.Context) : View(ctx) {
        private val pB1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pB2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pA1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pA2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var phase   = 0f
        private var running = false
        private val runner  = object : Runnable {
            override fun run() {
                if (!running) return
                phase = (phase + 16f / 2000f) % 1f
                invalidate()
                postDelayed(this, 16)
            }
        }
        fun startAnim() { if (running) return; running = true; post(runner) }
        fun stopAnim()  { running = false; removeCallbacks(runner) }

        private fun lerp(t: Float, k0: Float, k35: Float, k70: Float, k100: Float): Float = when {
            t < 0.35f -> k0  + (k35  - k0)  * (t / 0.35f)
            t < 0.70f -> k35 + (k70  - k35) * ((t - 0.35f) / 0.35f)
            else      -> k70 + (k100 - k70)  * ((t - 0.70f) / 0.30f)
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val em = width / 2.5f; val r = em * 0.25f; val t = phase
            val bw   = lerp(t, em*.5f, em*2.5f, em*.5f, em*.5f); val bh = em*.5f
            val bs1x = lerp(t, em, 0f, -em, em);   val bs1y = -em*.5f
            val bs2x = lerp(t, -em, 0f, em, -em);  val bs2y =  em*.5f
            pB1.color = Color.argb(190, 225, 20, 98)
            c.drawRoundRect(cx-bw/2, cy-bh/2, cx+bw/2, cy+bh/2, r, r, pB1)
            c.drawRoundRect(cx+bs1x-r, cy+bs1y-r, cx+bs1x+r, cy+bs1y+r, r, r, pB1)
            pB2.color = Color.argb(190, 111, 202, 220)
            c.drawRoundRect(cx+bs2x-r, cy+bs2y-r, cx+bs2x+r, cy+bs2y+r, r, r, pB2)
            val ah   = lerp(t, em*.5f, em*2.5f, em*.5f, em*.5f); val aw = em*.5f
            val as1x =  em*.5f; val as1y = lerp(t,  em, 0f, -em,  em)
            val as2x = -em*.5f; val as2y = lerp(t, -em, 0f,  em, -em)
            pA1.color = Color.argb(190, 61, 184, 143)
            c.drawRoundRect(cx-aw/2, cy-ah/2, cx+aw/2, cy+ah/2, r, r, pA1)
            c.drawRoundRect(cx+as1x-r, cy+as1y-r, cx+as1x+r, cy+as1y+r, r, r, pA1)
            pA2.color = Color.argb(190, 233, 169, 32)
            c.drawRoundRect(cx+as2x-r, cy+as2y-r, cx+as2x+r, cy+as2y+r, r, r, pA2)
        }
    }

    // ── FeedAdapter ──────────────────────────────────────────────────────────

    private val VTYPE_VIDEO  = 0
    private val VTYPE_LOADER = 1

    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class VideoVH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            val thumb: android.widget.ImageView = root.getChildAt(0) as android.widget.ImageView
            val title: TextView                 = root.getChildAt(1) as TextView
            val meta:  TextView                 = root.getChildAt(2) as TextView
        }

        inner class LoaderVH(val loader: CssLoader) : RecyclerView.ViewHolder(
            FrameLayout(context).apply {
                val lp = StaggeredGridLayoutManager.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(90))
                lp.isFullSpan = true
                layoutParams  = lp
                addView(loader, FrameLayout.LayoutParams(dp(50), dp(50)).also {
                    it.gravity = Gravity.CENTER
                })
            }
        )

        override fun getItemCount() = shownVideos.size + if (showingFooterLoader) 1 else 0

        override fun getItemViewType(pos: Int) =
            if (showingFooterLoader && pos == shownVideos.size) VTYPE_LOADER else VTYPE_VIDEO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VTYPE_LOADER) return LoaderVH(CssLoader(context))

            val ctx = parent.context
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(14))
                isClickable = true; isFocusable = true
            }
            val thumb = android.widget.ImageView(ctx).apply {
                scaleType     = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background    = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            col.addView(thumb, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
            val title = TextView(ctx).apply {
                setTextColor(AppTheme.text); textSize = 12f; maxLines = 2
                setPadding(0, dp(6), 0, 0)
            }
            col.addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val meta = TextView(ctx).apply {
                setTextColor(AppTheme.textSecondary); textSize = 10.5f; maxLines = 1
                setPadding(0, dp(2), 0, 0)
            }
            col.addView(meta, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            return VideoVH(col)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is LoaderVH) { holder.loader.startAnim(); return }
            holder as VideoVH
            val video = shownVideos[position]

            holder.title.text = video.title
            holder.meta.text  = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
            }
            val ratio = RATIOS[position % RATIOS.size]
            val colW  = context.resources.displayMetrics.widthPixels / 2 - dp(18)
            val h     = (colW / ratio).toInt()
            (holder.thumb.layoutParams as LinearLayout.LayoutParams).height = h
            holder.thumb.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(AppTheme.thumbBg)
            }
            holder.thumb.clipToOutline = true
            holder.title.setTextColor(AppTheme.text)
            holder.meta.setTextColor(AppTheme.textSecondary)

            // transitionName único por item para o container transform nativo
            val tn = "video_card_${video.videoUrl.hashCode()}"
            holder.root.transitionName  = tn
            holder.thumb.transitionName = "video_thumb_${video.videoUrl.hashCode()}"

            if (video.thumb.isNotEmpty()) {
                Glide.with(context)
                    .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                        .addHeader("User-Agent", UA)
                        .addHeader("Referer", referer(video.source))
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .build()))
                    .override(480).centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(crossFade))
                    .into(holder.thumb)
            } else {
                holder.thumb.setImageDrawable(null)
            }

            holder.root.setOnClickListener {
                activity.openVideoPlayer(video, holder.root)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is VideoVH) Glide.with(holder.thumb.context).clear(holder.thumb)
            if (holder is LoaderVH) holder.loader.stopAnim()
        }

        fun showLoader() {
            if (showingFooterLoader) return
            showingFooterLoader = true
            notifyItemInserted(shownVideos.size)
        }

        fun hideLoader() {
            if (!showingFooterLoader) return
            showingFooterLoader = false
            notifyItemRemoved(shownVideos.size)
        }
    }

    private val feedAdapter = FeedAdapter()

    // ── init ─────────────────────────────────────────────────────────────────

    init {
        val density = context.resources.displayMetrics.density
        fun dpI(v: Int) = (v * density).toInt()

        appBarH    = dpI(52)
        chipBarH   = dpI(40)
        contentTop = appBarH + chipBarH
        colGapPx   = dpI(8)
        sidePadPx  = dpI(10)

        setBackgroundColor(AppTheme.bg)

        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            setPadding(sidePadPx, dpI(8), sidePadPx, dpI(90))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator   = null
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect, view: View,
                    parent: RecyclerView, state: RecyclerView.State
                ) {
                    val half = colGapPx / 2
                    outRect.left = half; outRect.right = half; outRect.bottom = dpI(10)
                }
            })
        }
        recycler.adapter = feedAdapter

        // ── SwipeRefreshLayout nativo ─────────────────────────────────────────
        swipeRefresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(AppTheme.ytRed)
            setProgressBackgroundColorSchemeColor(AppTheme.bg)
            setOnRefreshListener { doRefresh() }
        }
        swipeRefresh.addView(recycler, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(swipeRefresh, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = contentTop
        })

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm   = rv.layoutManager as StaggeredGridLayoutManager
                val last = lm.findLastVisibleItemPositions(null).maxOrNull() ?: 0
                if (last >= shownVideos.size - 6) fetchMore()
                val off = rv.computeVerticalScrollOffset()
                if (off > dp(600) && scrollTopBtn.visibility != View.VISIBLE) {
                    scrollTopBtn.visibility = View.VISIBLE
                    scrollTopBtn.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(DecelerateInterpolator(1.5f)).start()
                } else if (off <= dp(600) && scrollTopBtn.visibility == View.VISIBLE) {
                    scrollTopBtn.animate().scaleX(0f).scaleY(0f).setDuration(180)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction { scrollTopBtn.visibility = View.GONE }.start()
                }
            }
        })

        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, chipBarH).also {
            it.gravity = Gravity.TOP; it.topMargin = appBarH
        })

        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        scrollTopBtn.scaleX = 0f; scrollTopBtn.scaleY = 0f
        addView(scrollTopBtn, LayoutParams(dp(42), dp(42)).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(82); it.rightMargin = dp(16)
        })

        skeletonView = buildSkeletonView()
        addView(skeletonView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = contentTop
        })

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        buildAppBar()
        buildDrawer()
        fetch()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activity.setStatusBarDark(false)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    fun isDrawerOpen()      = ::drawerView.isInitialized && drawerView.isDrawerOpen()
    fun closeDrawerIfOpen() { if (::drawerView.isInitialized) drawerView.close() }

    // ── AppBar ────────────────────────────────────────────────────────────────

    private fun buildAppBar() {
        val appBar = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.bg)
            elevation = dp(2).toFloat()
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(14), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val menuBtn = FrameLayout(context).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true; isFocusable = true
            setOnClickListener { drawerView.toggle() }
        }
        try {
            val px  = dp(22)
            val svg = SVG.getFromAsset(context.assets, "icons/svg/hamburger.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            menuBtn.addView(android.widget.ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(AppTheme.text)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }, FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}

        row.addView(menuBtn, LinearLayout.LayoutParams(dp(40), appBarH))
        row.addView(TextView(context).apply {
            text = "Explorar"; setTextColor(AppTheme.text)
            textSize = 21f; setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f; setPadding(dp(2), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, appBarH))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, appBarH).also {
            it.gravity = Gravity.TOP
        })
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private fun buildDrawer() {
        val decorView = activity.window.decorView as ViewGroup
        drawerView = DrawerView(context)
        decorView.addView(drawerView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        chipLabels.forEachIndexed { i, label ->
            val sel = i == 0
            val chip = TextView(context).apply {
                text = label; textSize = 12f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(12), 0, dp(12), 0)
                gravity = Gravity.CENTER
                tag = "chip_$i"; includeFontPadding = false
                setOnClickListener { selectChip(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)
            ).also { if (i > 0) it.leftMargin = dp(6) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun makeChipBg(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
        setColor(if (selected) AppTheme.chipBgActive else AppTheme.chipBg)
    }

    private fun selectChip(index: Int) {
        val row  = chipBar.getChildAt(0) as LinearLayout
        val prev = row.findViewWithTag<TextView>("chip_$currentChip")
        prev?.setTextColor(AppTheme.textSecondary)
        prev?.background = makeChipBg(false)
        prev?.setTypeface(null, Typeface.NORMAL)
        currentChip = index
        val curr = row.findViewWithTag<TextView>("chip_$index")
        curr?.setTextColor(AppTheme.chipTextActive)
        curr?.background = makeChipBg(true)
        curr?.setTypeface(null, Typeface.BOLD)
        applyFilter()
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun fetch() {
        isLoading = true; page = 1
        skeletonView.visibility = View.VISIBLE; skeletonView.alpha = 1f
        errorView.visibility    = View.GONE; recycler.visibility   = View.GONE

        val fetchers: List<() -> List<FeedVideo>> = listOf(
            { FeedFetcher.fetchRedTube() }, { FeedFetcher.fetchEporner() },
            { FeedFetcher.fetchPornHub() }, { FeedFetcher.fetchXVideos() },
            { FeedFetcher.fetchXHamster() }, { FeedFetcher.fetchYouPorn() },
            { FeedFetcher.fetchSpankBang() }, { FeedFetcher.fetchBravoTube() },
            { FeedFetcher.fetchDrTuber() }, { FeedFetcher.fetchTXXX() },
            { FeedFetcher.fetchGotPorn() }, { FeedFetcher.fetchPornDig() },
        )
        val total     = fetchers.size
        val completed = AtomicInteger(0)
        val anyShown  = AtomicBoolean(false)

        fetchers.forEach { fetcher ->
            thread {
                val result = try { fetcher() } catch (_: Exception) { emptyList() }
                handler.post {
                    val done = completed.incrementAndGet()
                    if (result.isNotEmpty()) {
                        allVideos.addAll(result); allVideos.shuffle()
                        if (anyShown.compareAndSet(false, true)) {
                            skeletonView.animate().alpha(0f).setDuration(300).withEndAction {
                                skeletonView.visibility = View.GONE; skeletonView.alpha = 1f
                            }.start()
                            recycler.visibility = View.VISIBLE; isLoading = false
                        }
                        applyFilter()
                    }
                    if (done == total && !anyShown.get()) {
                        skeletonView.visibility = View.GONE
                        errorView.visibility    = View.VISIBLE
                        isLoading = false
                    }
                }
            }
        }
    }

    private fun doRefresh() {
        val fetchers: List<() -> List<FeedVideo>> = listOf(
            { FeedFetcher.fetchRedTube() }, { FeedFetcher.fetchEporner() },
            { FeedFetcher.fetchPornHub() }, { FeedFetcher.fetchXVideos() },
            { FeedFetcher.fetchXHamster() }, { FeedFetcher.fetchYouPorn() },
            { FeedFetcher.fetchSpankBang() }, { FeedFetcher.fetchBravoTube() },
            { FeedFetcher.fetchDrTuber() }, { FeedFetcher.fetchTXXX() },
            { FeedFetcher.fetchGotPorn() }, { FeedFetcher.fetchPornDig() },
        )
        val total     = fetchers.size
        val completed = AtomicInteger(0)
        val newVideos = mutableListOf<FeedVideo>()

        fetchers.forEach { fetcher ->
            thread {
                val result = try { fetcher() } catch (_: Exception) { emptyList() }
                synchronized(newVideos) { newVideos.addAll(result) }
                if (completed.incrementAndGet() == total) {
                    handler.post {
                        swipeRefresh.isRefreshing = false
                        if (newVideos.isNotEmpty()) {
                            allVideos.clear(); newVideos.shuffle()
                            allVideos.addAll(newVideos); page = 1
                            applyFilter(); recycler.scrollToPosition(0)
                        }
                    }
                }
            }
        }
    }

    private fun fetchMore() {
        if (!isFetching.compareAndSet(false, true)) return
        if (isLoading || swipeRefresh.isRefreshing) { isFetching.set(false); return }
        feedAdapter.showLoader()
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    feedAdapter.hideLoader()
                    if (result.isNotEmpty()) { allVideos.addAll(result); page++; applyFilter() }
                    isFetching.set(false)
                }
            } catch (_: Exception) {
                handler.post { feedAdapter.hideLoader(); isFetching.set(false) }
            }
        }
    }

    // ── applyFilter corrigido: title + tags + categories + performer ──────────

    private fun videoMatches(v: FeedVideo, keywords: List<String>): Boolean {
        val title      = v.title.lowercase()
        val tags       = v.tags.joinToString(" ").lowercase()
        val categories = v.categories.joinToString(" ").lowercase()
        val performer  = v.performer.lowercase()
        return keywords.any { kw ->
            title.contains(kw)      ||
            tags.contains(kw)       ||
            categories.contains(kw) ||
            performer.contains(kw)
        }
    }

    private fun applyFilter() {
        val filtered: List<FeedVideo> = when (currentChip) {
            0    -> allVideos.toList()
            1    -> allVideos.sortedByDescending { it.publishedAt?.time ?: 0L }
            2    -> allVideos.sortedByDescending { parseViews(it.views) }
            3    -> allVideos.sortedBy { it.publishedAt?.time ?: Long.MAX_VALUE }
            else -> {
                val kws: List<String> = when (currentChip) {
                    4  -> listOf("amador","amateur","caseiro","homemade")
                    5  -> listOf("milf","mature","maduro","cougar","mom","mãe","mother")
                    6  -> listOf("asian","asiática","japonesa","japanese","korean","chinese","thai","japan","korea","china")
                    7  -> listOf("latina","latin","brazilian","brasileiro","colombiana","mexico","mexican","spanish")
                    8  -> listOf("blonde","loira","blond","blondie","louro")
                    9  -> listOf("gay","gays","homosexual","twink","bareback","men")
                    10 -> listOf("lesbian","lésbica","lesbians","lesbo","girl on girl","sapphic")
                    11 -> listOf("bdsm","bondage","fetish","dominat","submiss","slave","femdom","discipline")
                    12 -> listOf("anal","ass fuck","butt","booty","anale","anally")
                    13 -> listOf("teen","18","young","college","novinha","joven","teenager")
                    else -> emptyList()
                }
                if (kws.isEmpty()) allVideos.toList()
                else allVideos.filter { videoMatches(it, kws) }
            }
        }
        val prevSize = shownVideos.size
        shownVideos.clear(); shownVideos.addAll(filtered)
        when {
            prevSize == 0 || filtered.size < prevSize -> feedAdapter.notifyDataSetChanged()
            filtered.size > prevSize -> feedAdapter.notifyItemRangeInserted(prevSize, filtered.size - prevSize)
            else -> feedAdapter.notifyDataSetChanged()
        }
    }

    private fun parseViews(raw: String) =
        try { raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L } catch (_: Exception) { 0L }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildSkeletonView(): FrameLayout {
        val root = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val sv   = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(sidePadPx + colGapPx / 2, dp(8), sidePadPx + colGapPx / 2, dp(32))
        }
        val screenW = resources.displayMetrics.widthPixels
        val colW    = (screenW - sidePadPx * 2 - colGapPx) / 2
        val col1    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until 10) {
            val ratio = kRatios[i % kRatios.size]; val h = (colW / ratio).toInt()
            val lp    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) }
            if (i % 2 == 0) col1.addView(buildSkeletonTile(colW, h), lp)
            else             col2.addView(buildSkeletonTile(colW, h), lp)
        }
        row.addView(col1, LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(View(context), LinearLayout.LayoutParams(colGapPx, 0))
        row.addView(col2, LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT))
        sv.addView(row)
        root.addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        animateSkeleton(root)
        return root
    }

    private fun buildSkeletonTile(colW: Int, thumbH: Int): LinearLayout {
        fun shimmer(w: Int, h: Int) = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
            layoutParams = LinearLayout.LayoutParams(w, h)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(shimmer(LinearLayout.LayoutParams.MATCH_PARENT, thumbH))
            addView(View(context), LinearLayout.LayoutParams(0, dp(7)))
            addView(shimmer(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
            addView(shimmer((colW * 0.72f).toInt(), dp(12)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(6)))
            addView(shimmer((colW * 0.48f).toInt(), dp(10)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
        }
    }

    private fun animateSkeleton(root: View) {
        val run = object : Runnable {
            var phase = 0.0
            override fun run() {
                if (root.visibility != View.VISIBLE || root.parent == null) return
                phase = (phase + 0.05) % (Math.PI * 2)
                root.alpha = (0.5f + 0.5f * Math.sin(phase).toFloat()).coerceIn(0.25f, 1f)
                handler.postDelayed(this, 40)
                skeletonRunnable = this
            }
        }
        skeletonRunnable = run; handler.post(run)
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            addView(TextView(context).apply {
                text = "Sem ligação à internet"; setTextColor(AppTheme.textSecondary)
                textSize = 13f; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
            addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
            addView(TextView(context).apply {
                text = "Tentar novamente"; setTextColor(Color.WHITE)
                textSize = 13f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(100).toFloat(); setColor(AppTheme.ytRed)
                }
                setPadding(dp(20), dp(10), dp(20), dp(10))
                gravity = Gravity.CENTER; setOnClickListener { fetch() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        }
    }

    // ── ScrollTop ─────────────────────────────────────────────────────────────

    private fun buildScrollTopBtn(): FrameLayout {
        return FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dp(4).toFloat()
            setOnClickListener { recycler.smoothScrollToPosition(0) }
            try {
                val px  = dp(20)
                val svg = SVG.getFromAsset(context.assets, "icons/svg/back_arrow.svg")
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                addView(android.widget.ImageView(context).apply {
                    setImageBitmap(bmp); setColorFilter(AppTheme.ytRed)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE; rotation = 90f
                }, FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
            } catch (_: Exception) {}
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        skeletonRunnable?.let { handler.removeCallbacks(it) }
        skeletonRunnable = null
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (::drawerView.isInitialized && drawerView.parent === decorView)
                decorView.removeView(drawerView)
        } catch (_: Exception) {}
    }
}