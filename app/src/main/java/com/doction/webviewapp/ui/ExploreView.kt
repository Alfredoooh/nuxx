package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import kotlin.math.min

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

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private var isFetching  = false
    private var page        = 1

    // ── PTR elástico ─────────────────────────────────────────────────────────
    // Espaço vazio no topo que esconde o indicador
    private val PTR_SPACE_H  get() = dp(64)   // altura do espaço escondido no topo
    private val PTR_TRIGGER  = dp(52)
    private var ptrRefreshing = false
    private var ptrTouchStartY = 0f
    private var ptrTouchActive = false
    private var ptrCurrentOffset = 0f          // offset atual do recycler (positivo = desceu)

    // Wrapper que contém o espaço PTR + recycler
    private val ptrWrapper = FrameLayout(context)
    private val ptrSpace   = FrameLayout(context)  // espaço vazio com indicador
    private lateinit var ptrSpinner: View

    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira","Gay","Lésbicas","BDSM","Anal","Teen"
    )

    private val adapter = VideoAdapter(shownVideos) { video -> activity.openVideoPlayer(video) }

    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerScrim:   View
    private lateinit var drawerPanel:   FrameLayout
    private var drawerOpen = false

    private val drawerWidthPx  get() = (resources.displayMetrics.widthPixels * 0.70f).toInt()
    private val drawerDuration = 280L
    private lateinit var scrollTopIcon: ImageView
    private val colGapPx  get() = dp(8)
    private val sidePadPx get() = dp(10)

    init {
        setBackgroundColor(AppTheme.bg)

        // ── PTR space — fica ACIMA do recycler, escondido por translationY negativo
        ptrSpinner = buildPtrSpinner()
        ptrSpace.addView(ptrSpinner, FrameLayout.LayoutParams(dp(36), dp(36)).also {
            it.gravity = Gravity.CENTER
        })
        ptrSpace.setBackgroundColor(AppTheme.bg)

        // ── RecyclerView
        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            setPadding(sidePadPx, dp(8), sidePadPx, dp(80))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
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

        // ptrWrapper: [ptrSpace (64dp)] + [recycler (resto)]
        // Começa com translationY = -PTR_SPACE_H para esconder o espaço
        val ptrSpaceLp = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, PTR_SPACE_H)
        val recyclerLp = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = PTR_SPACE_H
        }
        ptrWrapper.addView(ptrSpace,  ptrSpaceLp)
        ptrWrapper.addView(recycler,  recyclerLp)
        ptrWrapper.translationY = -PTR_SPACE_H.toFloat()

        addView(ptrWrapper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = dp(52 + 40)
        })

        setupPtrTouch()

        // Scroll listener
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm   = rv.layoutManager as StaggeredGridLayoutManager
                val last = lm.findLastVisibleItemPositions(null).maxOrNull() ?: 0
                if (last >= shownVideos.size - 6) fetchMore()
                val off  = rv.computeVerticalScrollOffset()
                val show = off > dp(600)
                if (show && scrollTopBtn.visibility != View.VISIBLE) {
                    scrollTopBtn.visibility = View.VISIBLE
                    scrollTopBtn.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(DecelerateInterpolator(1.5f)).start()
                } else if (!show && scrollTopBtn.visibility == View.VISIBLE) {
                    scrollTopBtn.animate().scaleX(0f).scaleY(0f).setDuration(180)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction { scrollTopBtn.visibility = View.GONE }.start()
                }
            }
        })

        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity = Gravity.TOP; it.topMargin = dp(52)
        })

        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        scrollTopBtn.scaleX = 0f; scrollTopBtn.scaleY = 0f
        addView(scrollTopBtn, LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(72); it.rightMargin = dp(16)
        })

        footerLoader = buildFooterLoader()
        footerLoader.visibility = View.GONE
        addView(footerLoader, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).also {
            it.gravity = Gravity.BOTTOM
        })

        skeletonView = buildSkeletonView()
        addView(skeletonView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = dp(52 + 40)
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

    // ── PTR elástico ──────────────────────────────────────────────────────────

    private fun buildPtrSpinner(): View {
        return object : View(context) {
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = android.graphics.Paint.Style.FILL
            }
            val arcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AppTheme.ytRed; style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(3).toFloat(); strokeCap = android.graphics.Paint.Cap.ROUND
            }
            var arcAngle = 0f
            var arcSweep = 30f
            val spinRun  = object : Runnable {
                override fun run() {
                    arcAngle = (arcAngle + 10f) % 360f; invalidate(); postDelayed(this, 16)
                }
            }
            override fun onDraw(c: android.graphics.Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = width / 2f - dp(3)
                c.drawCircle(cx, cy, r, bgPaint)
                val pad = dp(6).toFloat()
                c.drawArc(pad, pad, width - pad, height - pad, arcAngle, arcSweep, false, arcPaint)
            }
            fun startSpin() { arcSweep = 270f; handler.post(spinRun) }
            fun stopSpin()  { handler.removeCallbacks(spinRun) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPtrTouch() {
        recycler.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val lm    = recycler.layoutManager as StaggeredGridLayoutManager
                    val first = lm.findFirstCompletelyVisibleItemPositions(null).minOrNull() ?: -1
                    if (!ptrRefreshing && (first == 0 || shownVideos.isEmpty())) {
                        ptrTouchStartY = event.rawY
                        ptrTouchActive = false
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ptrRefreshing) return@setOnTouchListener false
                    val lm    = recycler.layoutManager as StaggeredGridLayoutManager
                    val first = lm.findFirstCompletelyVisibleItemPositions(null).minOrNull() ?: -1
                    val dy    = event.rawY - ptrTouchStartY
                    if (dy > dp(6) && (first == 0 || shownVideos.isEmpty())) {
                        ptrTouchActive = true
                        // Elástico: quanto mais puxa mais resiste
                        val drag = elasticDrag(dy)
                        ptrCurrentOffset = drag
                        // Wrapper desce revelando o espaço PTR
                        ptrWrapper.translationY = -PTR_SPACE_H + drag
                        // Indicador aparece conforme o pull
                        val progress = (drag / PTR_TRIGGER).coerceIn(0f, 1f)
                        ptrSpinner.alpha  = progress
                        ptrSpinner.scaleX = 0.5f + 0.5f * progress
                        ptrSpinner.scaleY = 0.5f + 0.5f * progress
                        // arcSweep cresce com o progresso
                        try {
                            val f = ptrSpinner.javaClass.getDeclaredField("arcSweep")
                            f.isAccessible = true; f.setFloat(ptrSpinner, progress * 270f)
                            ptrSpinner.invalidate()
                        } catch (_: Exception) {}
                        return@setOnTouchListener true
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

    // Fórmula elástica — resiste exponencialmente
    private fun elasticDrag(dy: Float): Float {
        val max = PTR_SPACE_H.toFloat() * 1.5f
        return max * (1 - Math.exp((-dy / (max * 1.5f)).toDouble()).toFloat())
    }

    private fun triggerRefresh() {
        ptrRefreshing = true
        // Mantém o espaço aberto no topo mostrando o spinner
        ptrWrapper.animate()
            .translationY(-PTR_SPACE_H + PTR_SPACE_H * 0.85f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        ptrSpinner.alpha = 1f; ptrSpinner.scaleX = 1f; ptrSpinner.scaleY = 1f
        try {
            val m = ptrSpinner.javaClass.getDeclaredMethod("startSpin")
            m.isAccessible = true; m.invoke(ptrSpinner)
        } catch (_: Exception) {}
        doRefresh()
    }

    private fun snapPtrBack(animate: Boolean) {
        val dur = if (animate) 350L else 0L
        ptrWrapper.animate()
            .translationY(-PTR_SPACE_H.toFloat())
            .setDuration(dur)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        ptrSpinner.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(200).start()
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
            if (::drawerOverlay.isInitialized && drawerOverlay.parent != null) {
                decorView.removeView(drawerOverlay)
            }
        } catch (_: Exception) {}
    }

    fun isDrawerOpen() = drawerOpen
    fun closeDrawerIfOpen() { if (drawerOpen) closeDrawer() }

    // ── AppBar ────────────────────────────────────────────────────────────────

    private fun buildAppBar() {
        val appBar = FrameLayout(context)
        val appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        appBar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(14), 0); gravity = Gravity.CENTER_VERTICAL
        }
        val menuIcon = svgView("icons/svg/hamburger.svg", 22, AppTheme.text).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { toggleDrawer() }
        }
        row.addView(menuIcon, LinearLayout.LayoutParams(dp(38), dp(44)))
        row.addView(TextView(context).apply {
            text = "Explorar"; setTextColor(AppTheme.text)
            textSize = 22f; setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f; setPadding(dp(4), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        appBar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).also { it.gravity = Gravity.TOP })
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private fun buildDrawer() {
        val decorView = activity.window.decorView as ViewGroup
        drawerOverlay = FrameLayout(context).apply { visibility = View.GONE }
        drawerScrim = View(context).apply {
            setBackgroundColor(Color.BLACK); alpha = 0f
            setOnClickListener { closeDrawer() }
        }
        drawerOverlay.addView(drawerScrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        drawerPanel = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.drawerBg); elevation = dp(16).toFloat()
            translationX = -drawerWidthPx.toFloat()
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))
        }
        buildDrawerContent(col)
        drawerPanel.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        drawerOverlay.addView(drawerPanel, FrameLayout.LayoutParams(
            drawerWidthPx, FrameLayout.LayoutParams.MATCH_PARENT).also { it.gravity = Gravity.START })
        decorView.addView(drawerOverlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildDrawerContent(col: LinearLayout) {
        val logoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16)); gravity = Gravity.CENTER_VERTICAL
        }
        val logoImg = android.widget.ImageView(context).apply {
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        logoRow.addView(logoImg, LinearLayout.LayoutParams(dp(28), dp(28)))
        logoRow.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        logoRow.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(AppTheme.text)
            textSize = 18f; setTypeface(null, Typeface.BOLD); letterSpacing = -0.03f
        })
        col.addView(logoRow)
        col.addView(View(context).apply { setBackgroundColor(AppTheme.drawerDivider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        col.addView(drawerItem("icons/svg/drawer_download.svg", "Downloads") { closeDrawer() })
        col.addView(drawerItem("icons/svg/drawer_settings.svg", "Definições") {
            closeDrawer()
            handler.postDelayed({ activity.openSettings() }, drawerDuration + 50)
        })
        col.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(View(context).apply { setBackgroundColor(AppTheme.drawerDivider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        col.addView(TextView(context).apply {
            text = "nuxxx"; setTextColor(AppTheme.textTertiary)
            textSize = 11f; setPadding(dp(20), dp(14), dp(20), dp(24))
        })
    }

    private fun drawerItem(svgPath: String, label: String, onClick: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(14)); gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33000000")), null, null)
            setOnClickListener { onClick() }
            addView(svgView(svgPath, 20, AppTheme.iconSub), LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(View(context), LinearLayout.LayoutParams(dp(20), 0))
            addView(TextView(context).apply {
                text = label; setTextColor(AppTheme.text); textSize = 14f
            })
        }
    }

    private fun toggleDrawer() = if (drawerOpen) closeDrawer() else openDrawer()

    private fun openDrawer() {
        drawerOpen = true; drawerOverlay.visibility = View.VISIBLE
        drawerPanel.animate().translationX(0f).setDuration(drawerDuration)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        drawerScrim.animate().alpha(0.5f).setDuration(drawerDuration).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        drawerPanel.animate().translationX(-drawerWidthPx.toFloat()).setDuration(drawerDuration)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction { drawerOverlay.visibility = View.GONE }.start()
        drawerScrim.animate().alpha(0f).setDuration(drawerDuration).start()
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg); overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        chipLabels.forEachIndexed { i, label ->
            val sel = i == 0
            val chip = TextView(context).apply {
                text = label; textSize = 12f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(11), 0, dp(11), 0)
                gravity = Gravity.CENTER; tag = "chip_$i"; includeFontPadding = false
                setOnClickListener { selectChip(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)
            ).also { if (i > 0) it.leftMargin = dp(6) })
        }
        scroll.addView(row); return scroll
    }

    private fun makeChipBg(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
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
                        errorView.visibility = View.VISIBLE; isLoading = false
                    }
                }
            }
        }
    }

    private fun doRefresh() {
        thread {
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
        sv.addView(row); root.addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        animateSkeleton(root)
        return root
    }

    private fun buildSkeletonTile(colW: Int, thumbH: Int): LinearLayout {
        fun shimmerView(w: Int, h: Int) = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }.also { v -> v.layoutParams = LinearLayout.LayoutParams(w, h) }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(shimmerView(LinearLayout.LayoutParams.MATCH_PARENT, thumbH))
            addView(View(context), LinearLayout.LayoutParams(0, dp(7)))
            addView(shimmerView(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
            addView(shimmerView((colW * 0.75f).toInt(), dp(12)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(6)))
            addView(shimmerView((colW * 0.5f).toInt(), dp(10)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
        }
    }

    private fun animateSkeleton(root: View) {
        val run = object : Runnable {
            var phase = 0.0
            override fun run() {
                if (root.visibility != View.VISIBLE) return
                phase = (phase + 0.04) % (Math.PI * 2)
                root.alpha = (0.55f + 0.45f * Math.sin(phase).toFloat()).coerceIn(0.3f, 1f)
                handler.postDelayed(this, 40)
            }
        }
        handler.post(run)
    }

    // ── Footer loader ─────────────────────────────────────────────────────────

    private fun buildFooterLoader(): FrameLayout {
        val frame = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        val spinner = object : View(context) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AppTheme.ytRed; style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(2).toFloat(); strokeCap = android.graphics.Paint.Cap.ROUND
            }
            var a = 0f
            val r = object : Runnable {
                override fun run() { a = (a + 6f) % 360f; invalidate(); postDelayed(this, 16) }
            }
            init { post(r) }
            override fun onDraw(c: android.graphics.Canvas) {
                val cx = width / 2f; val cy = height / 2f; val rd = cx * 0.6f
                c.drawArc(cx - rd, cy - rd, cx + rd, cy + rd, a, 240f, false, p)
            }
        }
        frame.addView(spinner, FrameLayout.LayoutParams(dp(28), dp(28)).also {
            it.gravity = Gravity.CENTER
        })
        return frame
    }

    // ── Error / ScrollTop ─────────────────────────────────────────────────────

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
                setPadding(dp(20), dp(10), dp(20), dp(10)); gravity = Gravity.CENTER
                setOnClickListener { fetch() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        }
    }

    private fun buildScrollTopBtn(): FrameLayout {
        val btn = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dp(3).toFloat()
            setOnClickListener { recycler.smoothScrollToPosition(0) }
        }
        scrollTopIcon = svgView("icons/svg/back_arrow.svg", 24, AppTheme.ytRed).apply { rotation = 90f }
        btn.addView(scrollTopIcon, FrameLayout.LayoutParams(dp(24), dp(24)).also { it.gravity = Gravity.CENTER })
        return btn
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}