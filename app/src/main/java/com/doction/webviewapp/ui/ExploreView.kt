package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.adapters.VideoAdapter
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.theme.AppTheme
import kotlin.concurrent.thread

@SuppressLint("ViewConstructor")
class ExploreView(context: android.content.Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private val recycler:     RecyclerView
    private val chipBar:      HorizontalScrollView
    private val skeletonView: FrameLayout
    private val errorView:    LinearLayout
    private val scrollTopBtn: FrameLayout
    private val footerLoader: FrameLayout
    private lateinit var drawerView: DrawerView

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip  = 0
    private var isLoading    = true
    private var isFetching   = false
    private var page         = 1

    // ── PTR ──────────────────────────────────────────────────────────────────
    private val PTR_SPACE_H  get() = dp(64)
    private val PTR_TRIGGER  get() = dp(50)
    private var ptrRefreshing    = false
    private var ptrTouchStartY   = 0f
    private var ptrTouchActive   = false
    private var ptrCurrentOffset = 0f
    private val ptrWrapper       = FrameLayout(context)
    private val ptrSpace         = FrameLayout(context)
    private lateinit var ptrSpinner: View

    // Flag para ignorar toques imediatamente após abrir o app
    private var ptrReady = false

    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira","Gay","Lésbicas","BDSM","Anal","Teen"
    )

    private val adapter = VideoAdapter(shownVideos) { video, thumbView ->
        activity.openVideoPlayer(video, thumbView)
    }

    private val colGapPx    get() = dp(8)
    private val sidePadPx   get() = dp(10)
    private val APP_BAR_H   get() = dp(52)
    private val CHIP_BAR_H  get() = dp(40)
    private val CONTENT_TOP get() = APP_BAR_H + CHIP_BAR_H

    init {
        setBackgroundColor(AppTheme.bg)

        // PTR spinner
        ptrSpinner = buildPtrSpinner()
        ptrSpace.addView(ptrSpinner, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.CENTER
        })
        ptrSpace.setBackgroundColor(AppTheme.bg)

        // RecyclerView
        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            // paddingBottom grande o suficiente para não ser tapado por nada
            setPadding(sidePadPx, dp(8), sidePadPx, dp(90))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect, view: View,
                    parent: RecyclerView, state: RecyclerView.State
                ) {
                    val half = colGapPx / 2
                    outRect.left = half; outRect.right = half; outRect.bottom = dp(10)
                }
            })
        }
        recycler.adapter = adapter

        // ptrWrapper: espaço PTR escondido + recycler
        ptrWrapper.addView(ptrSpace, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, PTR_SPACE_H))
        ptrWrapper.addView(recycler, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = PTR_SPACE_H
        })
        ptrWrapper.translationY = -PTR_SPACE_H.toFloat()

        // ptrWrapper ocupa tudo abaixo do appBar+chipBar — SEM bottomMargin extra
        addView(ptrWrapper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = CONTENT_TOP
        })

        setupPtrTouch()

        // Scroll listener
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
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

        // ChipBar
        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, CHIP_BAR_H).also {
            it.gravity = Gravity.TOP
            it.topMargin = APP_BAR_H
        })

        // ScrollTop
        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        scrollTopBtn.scaleX = 0f; scrollTopBtn.scaleY = 0f
        addView(scrollTopBtn, LayoutParams(dp(42), dp(42)).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(82); it.rightMargin = dp(16)
        })

        // FooterLoader — o spinner CSS/HTML que me enviaste, renderizado inline
        footerLoader = buildFooterLoader()
        footerLoader.visibility = View.GONE
        addView(footerLoader, LayoutParams(LayoutParams.MATCH_PARENT, dp(72)).also {
            it.gravity = Gravity.BOTTOM
            it.bottomMargin = dp(90)
        })

        // Skeleton
        skeletonView = buildSkeletonView()
        addView(skeletonView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = CONTENT_TOP
        })

        // Error
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        // AppBar por cima
        buildAppBar()

        // Drawer no decorView
        buildDrawer()

        // Só activar PTR após o layout estar pronto (evita trigger ao abrir app)
        handler.postDelayed({ ptrReady = true }, 600)

        fetch()
    }

    // ── PTR ──────────────────────────────────────────────────────────────────

    private fun buildPtrSpinner(): View {
        return object : View(context) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            private var phase = 0f
            private val runner = object : Runnable {
                override fun run() {
                    phase = (phase + 3f) % 360f
                    invalidate()
                    postDelayed(this, 16)
                }
            }

            fun startSpin() { handler.post(runner) }
            fun stopSpin()  { handler.removeCallbacks(runner) }

            override fun onDraw(c: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val em = width / 2.5f
                val a1 = Math.toRadians(phase.toDouble())
                val a2 = Math.toRadians((phase + 180f).toDouble())
                val a3 = Math.toRadians((phase * 0.7f).toDouble())
                val a4 = Math.toRadians((phase * 0.7f + 180f).toDouble())
                paint.color = Color.argb(190, 225, 20, 98)
                c.drawCircle(cx + (em * Math.cos(a1)).toFloat(), cy + (em * 0.5f * Math.sin(a1 * 0.5f)).toFloat(), em * 0.22f, paint)
                paint.color = Color.argb(190, 111, 202, 220)
                c.drawCircle(cx + (em * Math.cos(a2)).toFloat(), cy + (em * 0.5f * Math.sin(a2 * 0.5f)).toFloat(), em * 0.22f, paint)
                paint.color = Color.argb(190, 61, 184, 143)
                c.drawCircle(cx + (em * 0.5f * Math.cos(a3 * 0.5f)).toFloat(), cy + (em * Math.sin(a3)).toFloat(), em * 0.22f, paint)
                paint.color = Color.argb(190, 233, 169, 32)
                c.drawCircle(cx + (em * 0.5f * Math.cos(a4 * 0.5f)).toFloat(), cy + (em * Math.sin(a4)).toFloat(), em * 0.22f, paint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPtrTouch() {
        recycler.setOnTouchListener { _, event ->
            // Ignora toques enquanto ptrReady=false (primeiros 600ms após abrir)
            if (!ptrReady) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!ptrRefreshing) {
                        ptrTouchStartY   = event.rawY
                        ptrTouchActive   = false
                        ptrCurrentOffset = 0f
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ptrRefreshing) return@setOnTouchListener false
                    val lm    = recycler.layoutManager as StaggeredGridLayoutManager
                    val tops  = lm.findFirstCompletelyVisibleItemPositions(null)
                    val first = tops?.minOrNull() ?: -1
                    // Só activa PTR se estiver mesmo no topo E a arrastar para baixo
                    val dy = event.rawY - ptrTouchStartY
                    if (dy > dp(12) && (first == 0 || shownVideos.isEmpty())) {
                        val drag = elasticDrag(dy)
                        if (drag > dp(6)) {
                            ptrTouchActive   = true
                            ptrCurrentOffset = drag
                            ptrWrapper.translationY = -PTR_SPACE_H + drag
                            val progress = (drag / PTR_TRIGGER).coerceIn(0f, 1f)
                            ptrSpinner.alpha  = progress
                            ptrSpinner.scaleX = 0.4f + 0.6f * progress
                            ptrSpinner.scaleY = 0.4f + 0.6f * progress
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (ptrTouchActive) {
                        ptrTouchActive = false
                        if (ptrCurrentOffset >= PTR_TRIGGER && !ptrRefreshing) {
                            triggerRefresh()
                        } else {
                            snapPtrBack(animate = true)
                        }
                        ptrCurrentOffset = 0f
                        return@setOnTouchListener true
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun elasticDrag(dy: Float): Float {
        val max = PTR_SPACE_H.toFloat() * 1.4f
        return max * (1f - Math.exp((-dy / (max * 1.6f)).toDouble()).toFloat())
    }

    private fun triggerRefresh() {
        ptrRefreshing = true
        ptrWrapper.animate()
            .translationY((-PTR_SPACE_H + PTR_SPACE_H * 0.9f).toFloat())
            .setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        ptrSpinner.alpha = 1f; ptrSpinner.scaleX = 1f; ptrSpinner.scaleY = 1f
        try {
            val m = ptrSpinner.javaClass.getDeclaredMethod("startSpin")
            m.isAccessible = true; m.invoke(ptrSpinner)
        } catch (_: Exception) {}
        doRefresh()
    }

    private fun snapPtrBack(animate: Boolean) {
        val dur = if (animate) 320L else 0L
        ptrWrapper.animate()
            .translationY(-PTR_SPACE_H.toFloat())
            .setDuration(dur).setInterpolator(DecelerateInterpolator(2.5f)).start()
        ptrSpinner.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(180).start()
        try {
            val m = ptrSpinner.javaClass.getDeclaredMethod("stopSpin")
            m.isAccessible = true; m.invoke(ptrSpinner)
        } catch (_: Exception) {}
        ptrRefreshing = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (::drawerView.isInitialized && drawerView.parent != null)
                decorView.removeView(drawerView)
        } catch (_: Exception) {}
    }

    fun isDrawerOpen()    = ::drawerView.isInitialized && drawerView.isDrawerOpen()
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
            val iv = android.widget.ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(AppTheme.text)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            menuBtn.addView(iv, FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        row.addView(menuBtn, LinearLayout.LayoutParams(dp(40), APP_BAR_H))
        row.addView(TextView(context).apply {
            text = "Explorar"
            setTextColor(AppTheme.text)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
            setPadding(dp(2), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        appBar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, APP_BAR_H))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, APP_BAR_H).also { it.gravity = Gravity.TOP })
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
            val sel  = i == 0
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
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(if (selected) AppTheme.chipBgActive else AppTheme.chipBg)
    }

    private fun selectChip(index: Int) {
        val row  = chipBar.getChildAt(0) as LinearLayout
        val prev = row.findViewWithTag<TextView>("chip_$currentChip")
        prev?.setTextColor(AppTheme.textSecondary); prev?.background = makeChipBg(false)
        prev?.setTypeface(null, Typeface.NORMAL)
        currentChip = index
        val curr = row.findViewWithTag<TextView>("chip_$index")
        curr?.setTextColor(AppTheme.chipTextActive); curr?.background = makeChipBg(true)
        curr?.setTypeface(null, Typeface.BOLD)
        applyFilter()
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private fun fetch() {
        isLoading = true; page = 1
        skeletonView.visibility = View.VISIBLE
        errorView.visibility    = View.GONE
        recycler.visibility     = View.GONE

        val fetchers: List<() -> List<FeedVideo>> = listOf(
            { FeedFetcher.fetchRedTube() }, { FeedFetcher.fetchEporner() },
            { FeedFetcher.fetchPornHub() }, { FeedFetcher.fetchXVideos() },
            { FeedFetcher.fetchXHamster() }, { FeedFetcher.fetchYouPorn() },
            { FeedFetcher.fetchSpankBang() }, { FeedFetcher.fetchBravoTube() },
            { FeedFetcher.fetchDrTuber() }, { FeedFetcher.fetchTXXX() },
            { FeedFetcher.fetchGotPorn() }, { FeedFetcher.fetchPornDig() },
        )
        var completed = 0; val total = fetchers.size; var anyShown = false
        fetchers.forEach { fetcher ->
            thread {
                val result = try { fetcher() } catch (_: Exception) { emptyList() }
                handler.post {
                    completed++
                    if (result.isNotEmpty()) {
                        allVideos.addAll(result); allVideos.shuffle()
                        if (!anyShown) {
                            anyShown = true
                            skeletonView.animate().alpha(0f).setDuration(300).withEndAction {
                                skeletonView.visibility = View.GONE; skeletonView.alpha = 1f
                            }.start()
                            recycler.visibility = View.VISIBLE; isLoading = false
                        }
                        applyFilter()
                    }
                    if (completed == total && !anyShown) {
                        skeletonView.visibility = View.GONE
                        errorView.visibility    = View.VISIBLE; isLoading = false
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
        var completed = 0; val total = fetchers.size
        val newVideos = mutableListOf<FeedVideo>()
        fetchers.forEach { fetcher ->
            thread {
                val result = try { fetcher() } catch (_: Exception) { emptyList() }
                synchronized(newVideos) { newVideos.addAll(result) }
                val done = synchronized(newVideos) { ++completed }
                if (done == total) {
                    handler.post {
                        if (newVideos.isNotEmpty()) {
                            allVideos.clear(); newVideos.shuffle()
                            allVideos.addAll(newVideos); page = 1
                            applyFilter(); recycler.scrollToPosition(0)
                        }
                        snapPtrBack(animate = true)
                    }
                }
            }
        }
    }

    private fun fetchMore() {
        if (isFetching || isLoading) return
        isFetching = true; footerLoader.visibility = View.VISIBLE
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    footerLoader.visibility = View.GONE
                    if (result.isNotEmpty()) { allVideos.addAll(result); page++; applyFilter() }
                    isFetching = false
                }
            } catch (_: Exception) {
                handler.post { footerLoader.visibility = View.GONE; isFetching = false }
            }
        }
    }

    private fun applyFilter() {
        val filtered: List<FeedVideo> = when (currentChip) {
            2    -> allVideos.sortedByDescending { parseViews(it.views) }
            3    -> allVideos.reversed()
            else -> {
                val kws: List<String>? = when (currentChip) {
                    4  -> listOf("amador","amateur","caseiro","homemade")
                    5  -> listOf("milf","mature","maduro","cougar","mom","mãe")
                    6  -> listOf("asian","asiática","japanese","korean","chinese","thai","japan")
                    7  -> listOf("latina","latin","brazilian","brasileiro","colombiana","mexico")
                    8  -> listOf("blonde","loira","blond","blondie")
                    9  -> listOf("gay","gays","homosexual","twink","bareback")
                    10 -> listOf("lesbian","lésbica","lesbians","lesbo","girl on girl")
                    11 -> listOf("bdsm","bondage","fetish","dominat","submiss","slave")
                    12 -> listOf("anal","ass fuck","butt","booty")
                    13 -> listOf("teen","18","young","college","novinha")
                    else -> null
                }
                if (kws == null) allVideos.toList()
                else allVideos.filter { v -> val t = v.title.lowercase(); kws.any { t.contains(it) } }
            }
        }
        val prevSize = shownVideos.size
        shownVideos.clear(); shownVideos.addAll(filtered)
        if (prevSize == 0) adapter.notifyDataSetChanged()
        else {
            val added = shownVideos.size - prevSize
            if (added > 0) adapter.notifyItemRangeInserted(prevSize, added)
            else adapter.notifyDataSetChanged()
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
            val tile  = buildSkeletonTile(colW, h)
            val lp    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
            if (i % 2 == 0) col1.addView(tile, lp) else col2.addView(tile, lp)
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
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
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
                if (root.visibility != View.VISIBLE) return
                phase = (phase + 0.05) % (Math.PI * 2)
                root.alpha = (0.5f + 0.5f * Math.sin(phase).toFloat()).coerceIn(0.25f, 1f)
                handler.postDelayed(this, 40)
            }
        }
        handler.post(run)
    }

    // ── Footer loader — spinner uiverse (SchawnnahJ) em WebView leve ──────────

    private fun buildFooterLoader(): FrameLayout {
        val frame = FrameLayout(context)
        val wv = android.webkit.WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled   = false
            settings.javaScriptEnabled   = true
            loadDataWithBaseURL(null, """
                <!DOCTYPE html>
                <html>
                <head>
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>
                *{margin:0;padding:0;box-sizing:border-box}
                html,body{width:100%;height:100%;background:transparent;display:flex;align-items:center;justify-content:center}
                .loader{position:relative;width:2.5em;height:2.5em;transform:rotate(165deg)}
                .loader:before,.loader:after{content:"";position:absolute;top:50%;left:50%;display:block;width:.5em;height:.5em;border-radius:.25em;transform:translate(-50%,-50%)}
                .loader:before{animation:before8 2s infinite}
                .loader:after{animation:after6 2s infinite}
                @keyframes before8{
                  0%{width:.5em;box-shadow:1em -.5em rgba(225,20,98,.75),-1em .5em rgba(111,202,220,.75)}
                  35%{width:2.5em;box-shadow:0 -.5em rgba(225,20,98,.75),0 .5em rgba(111,202,220,.75)}
                  70%{width:.5em;box-shadow:-1em -.5em rgba(225,20,98,.75),1em .5em rgba(111,202,220,.75)}
                  100%{box-shadow:1em -.5em rgba(225,20,98,.75),-1em .5em rgba(111,202,220,.75)}
                }
                @keyframes after6{
                  0%{height:.5em;box-shadow:.5em 1em rgba(61,184,143,.75),-.5em -1em rgba(233,169,32,.75)}
                  35%{height:2.5em;box-shadow:.5em 0 rgba(61,184,143,.75),-.5em 0 rgba(233,169,32,.75)}
                  70%{height:.5em;box-shadow:.5em -1em rgba(61,184,143,.75),-.5em 1em rgba(233,169,32,.75)}
                  100%{box-shadow:.5em 1em rgba(61,184,143,.75),-.5em -1em rgba(233,169,32,.75)}
                }
                </style>
                </head>
                <body><div class="loader"></div></body>
                </html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        frame.addView(wv, FrameLayout.LayoutParams(dp(56), dp(56)).also {
            it.gravity = Gravity.CENTER
        })
        return frame
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
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
                    setColor(AppTheme.ytRed)
                }
                setPadding(dp(20), dp(10), dp(20), dp(10)); gravity = Gravity.CENTER
                setOnClickListener { fetch() }
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
                val iv = android.widget.ImageView(context).apply {
                    setImageBitmap(bmp); setColorFilter(AppTheme.ytRed)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE; rotation = 90f
                }
                addView(iv, FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
            } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}