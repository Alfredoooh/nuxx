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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    private val loadingView:  FrameLayout
    private val errorView:    LinearLayout
    private val scrollTopBtn: FrameLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private var isFetching  = false
    private var page        = 1

    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira"
    )

    private val adapter = VideoAdapter(shownVideos) { video ->
        activity.openVideoPlayer(video)
    }

    private lateinit var appBarBg:    View
    private lateinit var appBarTitle: TextView
    private lateinit var menuIcon:    ImageView

    private lateinit var drawerView:  FrameLayout
    private lateinit var drawerScrim: View
    private var drawerOpen    = false
    private var dragStartX    = 0f
    private var dragStartOpen = false

    private val drawerWidthPx get() = dp(260)
    private val appShiftPx    get() = dp(110)
    private val drawerDuration = 280L

    private lateinit var scrollTopIcon: ImageView

    init {
        setBackgroundColor(AppTheme.bg)
        applyLightStatusBar()

        swipeRefresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(AppTheme.ytRed)
            setProgressBackgroundColorSchemeColor(AppTheme.bg)
            setOnRefreshListener { doRefresh() }
        }

        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_ALWAYS
        }
        recycler.adapter = adapter

        swipeRefresh.addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(swipeRefresh, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity   = Gravity.TOP
            it.topMargin = dp(52)
        })

        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        scrollTopBtn.scaleX = 0f; scrollTopBtn.scaleY = 0f
        addView(scrollTopBtn, LayoutParams(dp(40), dp(40)).also {
            it.gravity      = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(72)
            it.rightMargin  = dp(16)
        })

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm   = rv.layoutManager as StaggeredGridLayoutManager
                val last = lm.findLastVisibleItemPositions(null).maxOrNull() ?: 0
                if (last >= shownVideos.size - 6) fetchMore()
                val off = rv.computeVerticalScrollOffset()
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

        loadingView = buildLoadingView()
        addView(loadingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        buildAppBar()
        buildDrawer()
        setupDragGesture()

        fetch()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyLightStatusBar()
    }

    private fun applyLightStatusBar() {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = true
    }

    fun closeDrawerIfOpen() { if (drawerOpen) closeDrawer() }

    private fun buildAppBar() {
        val appBar = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        appBar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(14), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        menuIcon = svgView("icons/svg/hamburger.svg", 22, AppTheme.text).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { toggleDrawer() }
        }
        row.addView(menuIcon, LinearLayout.LayoutParams(dp(38), dp(44)))

        appBarTitle = TextView(context).apply {
            text = "Explorar"
            setTextColor(AppTheme.text)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
            setPadding(dp(4), 0, 0, 0)
        }
        row.addView(appBarTitle, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        appBar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).also { it.gravity = Gravity.TOP })
    }

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        chipLabels.forEachIndexed { i, label ->
            val sel = i == 0
            val chip = TextView(context).apply {
                text     = label
                textSize = 12f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(11), 0, dp(11), 0)
                gravity = Gravity.CENTER
                tag     = "chip_$i"
                includeFontPadding = false
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
        cornerRadius = dp(6).toFloat()
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

    private fun buildDrawer() {
        val root = activity.findViewById<FrameLayout>(android.R.id.content)

        drawerScrim = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(drawerScrim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        drawerView = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.drawerBg)
            elevation = dp(16).toFloat()
            translationX = -drawerWidthPx.toFloat()
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "drawer_col"
        }
        buildDrawerContent(col)
        drawerView.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(drawerView, FrameLayout.LayoutParams(
            drawerWidthPx, FrameLayout.LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.START
        })
    }

    private fun buildDrawerContent(col: LinearLayout) {
        col.removeAllViews()
        val logoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoImg = ImageView(context).apply {
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open("logo.png"))) }
            catch (_: Exception) {}
            scaleType = ImageView.ScaleType.FIT_CENTER
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
            closeDrawer(); activity.openSettings()
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
            setPadding(dp(20), dp(14), dp(20), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33000000")), null, null)
            setOnClickListener { onClick() }
            addView(svgView(svgPath, 20, AppTheme.iconSub),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(View(context), LinearLayout.LayoutParams(dp(20), 0))
            addView(TextView(context).apply {
                text = label; setTextColor(AppTheme.text); textSize = 14f
            })
        }
    }

    private fun toggleDrawer() = if (drawerOpen) closeDrawer() else openDrawer()

    private fun openDrawer() {
        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerView.animate().translationX(0f).setDuration(drawerDuration)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        activity.shiftContent(appShiftPx.toFloat(), drawerDuration)
        drawerScrim.animate().alpha(0.5f).setDuration(drawerDuration).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        drawerView.animate().translationX(-drawerWidthPx.toFloat()).setDuration(drawerDuration)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction { drawerScrim.visibility = View.GONE }.start()
        activity.shiftContent(0f, drawerDuration)
        drawerScrim.animate().alpha(0f).setDuration(drawerDuration).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragGesture() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX    = event.x
                    dragStartOpen = drawerOpen
                    dragStartOpen || event.x < dp(24)
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.x - dragStartX
                    if (dragStartOpen) {
                        val tx = delta.coerceIn(-drawerWidthPx.toFloat(), 0f)
                        drawerView.translationX = tx
                        drawerScrim.alpha = 0.5f * (1f + tx / drawerWidthPx)
                        activity.shiftContent(appShiftPx + tx, 0)
                    } else {
                        val tx = delta.coerceIn(0f, drawerWidthPx.toFloat())
                        drawerView.translationX = -drawerWidthPx + tx
                        drawerScrim.visibility = View.VISIBLE
                        drawerScrim.alpha = 0.5f * (tx / drawerWidthPx)
                        activity.shiftContent(tx * appShiftPx.toFloat() / drawerWidthPx, 0)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val delta = event.x - dragStartX
                    when {
                        delta > dp(80) || (!dragStartOpen && delta > drawerWidthPx * 0.4f) -> openDrawer()
                        delta < -dp(80) || (dragStartOpen && -delta > drawerWidthPx * 0.4f) -> closeDrawer()
                        else -> if (dragStartOpen) openDrawer() else closeDrawer()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyFilter() {
        val filtered: List<FeedVideo> = when (currentChip) {
            2    -> allVideos.sortedByDescending { parseViews(it.views) }
            3    -> allVideos.reversed()
            else -> {
                val kws: List<String>? = when (currentChip) {
                    4 -> listOf("amador","amateur","caseiro","homemade")
                    5 -> listOf("milf","mature","maduro","cougar","mom","mãe")
                    6 -> listOf("asian","asiática","japanese","korean","chinese","thai","japan")
                    7 -> listOf("latina","latin","brazilian","brasileiro","colombiana","mexico")
                    8 -> listOf("blonde","loira","blond","blondie")
                    else -> null
                }
                if (kws == null) allVideos.toList()
                else allVideos.filter { v -> val t = v.title.lowercase(); kws.any { t.contains(it) } }
            }
        }
        shownVideos.clear(); shownVideos.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun parseViews(raw: String) =
        try { raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L } catch (_: Exception) { 0L }

    private fun fetch() {
        isLoading = true; page = 1
        loadingView.visibility = View.VISIBLE
        errorView.visibility   = View.GONE
        recycler.visibility    = View.GONE
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    loadingView.visibility = View.GONE
                    if (result.isEmpty()) {
                        errorView.visibility = View.VISIBLE
                    } else {
                        allVideos.clear(); allVideos.addAll(result); page++
                        applyFilter()
                        recycler.visibility = View.VISIBLE
                        isLoading = false
                    }
                }
            } catch (_: Exception) {
                handler.post {
                    loadingView.visibility = View.GONE
                    errorView.visibility   = View.VISIBLE
                    isLoading = false
                }
            }
        }
    }

    private fun doRefresh() {
        thread {
            try {
                val result = FeedFetcher.fetchAll((1..20).random())
                handler.post {
                    if (result.isNotEmpty()) {
                        allVideos.addAll(0, result); page++; applyFilter()
                    }
                    swipeRefresh.isRefreshing = false
                }
            } catch (_: Exception) {
                handler.post { swipeRefresh.isRefreshing = false }
            }
        }
    }

    private fun fetchMore() {
        if (isFetching || isLoading) return
        isFetching = true
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    if (result.isNotEmpty()) { allVideos.addAll(result); page++; applyFilter() }
                    isFetching = false
                }
            } catch (_: Exception) {
                handler.post { isFetching = false }
            }
        }
    }

    private fun buildLoadingView(): FrameLayout {
        val root = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val sv = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
        }
        val screenW = resources.displayMetrics.widthPixels
        val colW = (screenW - dp(10 + 10 + 8)) / 2

        val col1 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (i in 0 until 8) {
            val ratio = kRatios[i % kRatios.size]
            val h     = (colW / ratio).toInt()
            val tile  = buildSkeletonTile(h)
            val lp    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
            if (i % 2 == 0) col1.addView(tile, lp) else col2.addView(tile, lp)
        }

        row.addView(col1, LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(View(context), LinearLayout.LayoutParams(dp(8), 0))
        row.addView(col2, LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT))
        sv.addView(row)
        root.addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return root
    }

    private fun buildSkeletonTile(thumbH: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, thumbH))
            addView(View(context), LinearLayout.LayoutParams(0, dp(6)))
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(11)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                    setColor(AppTheme.thumbShimmer1)
                }
            }, LinearLayout.LayoutParams(dp(100), dp(10)))
            addView(View(context), LinearLayout.LayoutParams(0, dp(4)))
        }
    }

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
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3).toFloat()
            setOnClickListener { recycler.smoothScrollToPosition(0) }
        }
        scrollTopIcon = svgView("icons/svg/back_arrow.svg", 24, AppTheme.ytRed).apply {
            rotation = 90f
        }
        btn.addView(scrollTopIcon, FrameLayout.LayoutParams(dp(24), dp(24)).also {
            it.gravity = Gravity.CENTER
        })
        return btn
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}