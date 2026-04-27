// ExploreView.kt
package com.doction.webviewapp.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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

    // ── Recycler / adapter ────────────────────────────────────────────────────
    private val recycler:     RecyclerView
    private val chipBar:      HorizontalScrollView
    private val loadingView:  FrameLayout
    private val errorView:    LinearLayout
    private val scrollTopBtn: FrameLayout

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private var isFetching  = false
    private var page        = 1

    // Aspect ratios do Flutter — ciclo de 10
    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos", "Recentes", "Mais vistos", "Mais antigos",
        "Amador", "MILF", "Asiática", "Latina", "Loira"
    )

    private val adapter = VideoAdapter(shownVideos) { video ->
        activity.openVideoPlayer(video)
    }

    // ── AppBar ────────────────────────────────────────────────────────────────
    private lateinit var appBarBg:    View
    private lateinit var appBarTitle: TextView
    private lateinit var menuIcon:    ImageView

    // ── Drawer ────────────────────────────────────────────────────────────────
    private lateinit var drawerView:  FrameLayout
    private lateinit var drawerScrim: View
    private var drawerOpen    = false
    private var dragStartX    = 0f
    private var dragStartOpen = false

    private val drawerWidthPx get() = dp(260)
    private val appShiftPx    get() = dp(110)
    private val drawerDuration = 280L   // igual ao reverseTransitionDuration do Flutter

    // ── Scroll top ────────────────────────────────────────────────────────────
    private lateinit var scrollTopIcon: ImageView

    // ── Shimmer animators ─────────────────────────────────────────────────────
    private val shimmerAnimators = mutableListOf<ValueAnimator>()

    // ── Tema ──────────────────────────────────────────────────────────────────
    private val themeListener: () -> Unit = { applyTheme() }

    init {
        setBackgroundColor(AppTheme.bg)

        // ── Recycler (StaggeredGrid 2 cols, padding espelha Flutter) ──────────
        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(false)
            // Flutter: padding fromLTRB(10, 8, 10, 32) + appBar 52 + chips 40
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        recycler.adapter = adapter
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // ── Chips (altura 40dp, fixo sobre o recycler) ────────────────────────
        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity   = Gravity.TOP
            it.topMargin = dp(52)
        })

        // ── Scroll-to-top FAB ─────────────────────────────────────────────────
        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        scrollTopBtn.scaleX = 0f
        scrollTopBtn.scaleY = 0f
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
                val shouldShow = off > dp(600)
                if (shouldShow && scrollTopBtn.visibility != View.VISIBLE) {
                    scrollTopBtn.visibility = View.VISIBLE
                    scrollTopBtn.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()
                } else if (!shouldShow && scrollTopBtn.visibility == View.VISIBLE) {
                    scrollTopBtn.animate()
                        .scaleX(0f).scaleY(0f)
                        .setDuration(180)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction { scrollTopBtn.visibility = View.GONE }
                        .start()
                }
            }
        })

        // ── Loading skeleton ──────────────────────────────────────────────────
        loadingView = buildLoadingView()
        addView(loadingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // ── Error view ────────────────────────────────────────────────────────
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        // ── AppBar (por cima de tudo) ─────────────────────────────────────────
        buildAppBar()

        // ── Drawer + scrim ────────────────────────────────────────────────────
        buildDrawer()
        setupDragGesture()

        AppTheme.addThemeListener(themeListener)
        fetch()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimators.forEach { it.cancel() }
        shimmerAnimators.clear()
        AppTheme.removeThemeListener(themeListener)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AppBar — espelha SliverAppBar Flutter (expandedHeight 44, toolbarHeight 0)
    // Total visível: 52dp (inclui padding vertical da row)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAppBar() {
        val appBar = FrameLayout(context)

        appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        appBar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        // Row: hambúrguer | título a crescer
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // padding que alinha com o titlePadding do Flutter (left:16, bottom:10)
            setPadding(dp(8), 0, dp(14), 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Hambúrguer — 38×44dp com padding interno 8dp (área de toque)
        menuIcon = svgView("icons/svg/hamburger.svg", 22, AppTheme.text).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { toggleDrawer() }
        }
        row.addView(menuIcon, LinearLayout.LayoutParams(dp(38), dp(44)))

        // Título — fontSize 22, w800, letterSpacing -0.5
        appBarTitle = TextView(context).apply {
            text = "Explorar"
            setTextColor(AppTheme.text)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f          // -0.5sp ≈ -0.03em
            setPadding(dp(4), 0, 0, 0)
        }
        row.addView(appBarTitle, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))

        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).also {
            it.gravity = Gravity.TOP
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chips — SliverPersistentHeader pinned, height 40dp
    // Espelha _ChipDelegate do Flutter ao pixel
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // Flutter: padding vertical: 6  → row height 40 - 12 = 28 chip height
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
                // Flutter: padding horizontal: 11
                setPadding(dp(11), 0, dp(11), 0)
                gravity = Gravity.CENTER
                tag     = "chip_$i"
                setOnClickListener { selectChip(i) }
                includeFontPadding = false
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)   // altura exacta do chip
            ).also { if (i > 0) it.leftMargin = dp(6) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun makeChipBg(selected: Boolean) = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(6).toFloat()   // Flutter: BorderRadius.circular(6)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Drawer — 260dp largura, shift 110dp no conteúdo, scrim alpha 0.18
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildDrawer() {
        drawerScrim = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        addView(drawerScrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        drawerView = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.drawerBg)
            elevation = dp(8).toFloat()
            translationX = -drawerWidthPx.toFloat()
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "drawer_col"
        }
        buildDrawerContent(col)
        drawerView.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        addView(drawerView, LayoutParams(drawerWidthPx, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.START
        })
    }

    private fun buildDrawerContent(col: LinearLayout) {
        col.removeAllViews()

        // Logo row
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
            text = "nuxxx"
            setTextColor(AppTheme.text)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
        })
        col.addView(logoRow)

        col.addView(View(context).apply {
            setBackgroundColor(AppTheme.drawerDivider)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        col.addView(drawerItem("icons/svg/drawer_download.svg", "Downloads") { closeDrawer() })
        col.addView(drawerItem("icons/svg/drawer_settings.svg", "Definições") {
            closeDrawer()
            activity.openSettings()
        })

        col.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(View(context).apply {
            setBackgroundColor(AppTheme.drawerDivider)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        col.addView(TextView(context).apply {
            text = "nuxxx"
            setTextColor(AppTheme.textTertiary)
            textSize = 11f
            setPadding(dp(20), dp(14), dp(20), dp(24))
        })
    }

    private fun drawerItem(svgPath: String, label: String, onClick: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, null)
            setOnClickListener { onClick() }
            addView(svgView(svgPath, 20, AppTheme.iconSub),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(View(context), LinearLayout.LayoutParams(dp(20), 0))
            addView(TextView(context).apply {
                text = label
                setTextColor(AppTheme.text)
                textSize = 14f
            })
        }
    }

    private fun toggleDrawer() = if (drawerOpen) closeDrawer() else openDrawer()

    private fun openDrawer() {
        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerView.animate().translationX(0f)
            .setDuration(drawerDuration)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        activity.shiftContent(appShiftPx.toFloat(), drawerDuration)
        drawerScrim.animate().alpha(0.18f).setDuration(drawerDuration).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        drawerView.animate().translationX(-drawerWidthPx.toFloat())
            .setDuration(drawerDuration)
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
                        drawerScrim.alpha = 0.18f * (1f + tx / drawerWidthPx)
                        activity.shiftContent(appShiftPx + tx, 0)
                    } else {
                        val tx = delta.coerceIn(0f, drawerWidthPx.toFloat())
                        drawerView.translationX = -drawerWidthPx + tx
                        drawerScrim.visibility = View.VISIBLE
                        drawerScrim.alpha = 0.18f * (tx / drawerWidthPx)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Tema
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        appBarBg.setBackgroundColor(AppTheme.bg)
        appBarTitle.setTextColor(AppTheme.text)
        menuIcon.setColorFilter(AppTheme.text)
        chipBar.setBackgroundColor(AppTheme.bg)
        loadingView.setBackgroundColor(AppTheme.bg)
        drawerView.setBackgroundColor(AppTheme.drawerBg)

        val drawerCol = drawerView.findViewWithTag<LinearLayout>("drawer_col")
        if (drawerCol != null) buildDrawerContent(drawerCol)

        val row = chipBar.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as? TextView ?: continue
            val sel = i == currentChip
            chip.setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
            chip.background = makeChipBg(sel)
        }

        scrollTopBtn.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(if (AppTheme.isDark) Color.parseColor("#2A2A2A") else Color.WHITE)
        }
        scrollTopIcon.setColorFilter(if (AppTheme.isDark) Color.WHITE else AppTheme.ytRed)

        adapter.notifyDataSetChanged()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filtro
    // ─────────────────────────────────────────────────────────────────────────

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
                else allVideos.filter { v ->
                    val t = v.title.lowercase(); kws.any { t.contains(it) }
                }
            }
        }
        shownVideos.clear()
        shownVideos.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun parseViews(raw: String) =
        try { raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L }
        catch (_: Exception) { 0L }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetch() {
        isLoading = true
        page      = 1
        loadingView.visibility = View.VISIBLE
        errorView.visibility   = View.GONE
        recycler.visibility    = View.GONE
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    if (result.isEmpty()) {
                        loadingView.visibility = View.GONE
                        errorView.visibility   = View.VISIBLE
                    } else {
                        allVideos.clear()
                        allVideos.addAll(result)
                        page++
                        applyFilter()
                        loadingView.visibility = View.GONE
                        recycler.visibility    = View.VISIBLE
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

    // ─────────────────────────────────────────────────────────────────────────
    // Skeleton loader — espelha _buildSkeletons() + _SkeletonTile do Flutter
    // MasonryGridView 2 colunas, mainAxisSpacing 12, crossAxisSpacing 8
    // Cada tile: imagem (aspect ratio do kRatios) + 2 linhas de texto
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.bg)
        }

        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // Espelha padding fromLTRB(10, 8, 10, 32) + appBar 52 + chips 40
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
            weightSum = 2f
        }

        val col1 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, 0, 0)   // crossAxisSpacing 8 / 2 = 4 de cada lado
        }
        col1.setPadding(0, 0, dp(4), 0)

        // 8 skeleton tiles — 4 por coluna, alternando ratios do kRatios
        for (i in 0 until 8) {
            val ratio  = kRatios[i % kRatios.size]
            val tile   = buildSkeletonTile(ratio)
            val lp     = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }

            if (i % 2 == 0) col1.addView(tile, lp)
            else             col2.addView(tile, lp)
        }

        container.addView(col1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(col2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        scroll.addView(container)
        root.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return root
    }

    // Espelha _SkeletonTile: imagem com ratio + linha título + linha meta
    private fun buildSkeletonTile(ratio: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            // Thumbnail — aspect ratio dinâmico
            val thumb = View(context).apply {
                background = shimmerDrawable()
                tag = "shimmer"
            }
            // Calculamos a altura baseada na largura disponível
            // Usamos post para ter a largura real; enquanto isso, estimamos
            val colW = (resources.displayMetrics.widthPixels - dp(10 + 10 + 8)) / 2
            val thumbH = (colW / ratio).toInt()
            val thumbLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, thumbH)
            // ClipRRect cornerRadius 6 via background
            thumb.background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
            addView(thumb, thumbLp)

            // SizedBox(height: 6)
            addView(View(context), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)))

            // Linha título — w: match, h: 11dp, r: 4
            addView(shimmerBox(w = null, h = dp(11), r = dp(4)), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(11)))

            // SizedBox(height: 4)
            addView(View(context), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))

            // Linha meta — w: 100dp, h: 10dp, r: 4
            addView(shimmerBox(w = dp(100), h = dp(10), r = dp(4)), LinearLayout.LayoutParams(
                dp(100), dp(10)))

            // SizedBox(height: 4)
            addView(View(context), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))
        }
    }

    private fun shimmerBox(w: Int?, h: Int, r: Int): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = r.toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }
    }

    private fun shimmerDrawable() = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(6).toFloat()
        setColor(AppTheme.thumbShimmer1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error view — espelha _buildError() do Flutter
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)

            // Icon(Icons.wifi_off_rounded, size: 40)
            val icon = svgView("icons/svg/wifi_off.svg", 40, AppTheme.iconSub)
            addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            })

            // SizedBox(height: 12)
            addView(View(context), LinearLayout.LayoutParams(0, dp(12)))

            // 'Sem ligação à internet'
            addView(TextView(context).apply {
                text = "Sem ligação à internet"
                setTextColor(AppTheme.textSecondary)
                textSize = 13f
                gravity  = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            })

            // SizedBox(height: 16)
            addView(View(context), LinearLayout.LayoutParams(0, dp(16)))

            // Botão 'Tentar novamente'
            addView(TextView(context).apply {
                text = "Tentar novamente"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = dp(100).toFloat()
                    setColor(AppTheme.ytRed)
                }
                setPadding(dp(20), dp(10), dp(20), dp(10))
                gravity = Gravity.CENTER
                setOnClickListener { fetch() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scroll-to-top FAB — espelha FloatingActionButton.small do Flutter
    // Cor dark: Color(0xFF2A2A2A) / light: Colors.white
    // Ícone: keyboard_arrow_up (seta para cima), cor: white/ytRed
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildScrollTopBtn(): FrameLayout {
        val btn = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (AppTheme.isDark) Color.parseColor("#2A2A2A") else Color.WHITE)
            }
            elevation = dp(3).toFloat()
            setOnClickListener {
                recycler.smoothScrollToPosition(0)
            }
        }
        scrollTopIcon = svgView("icons/svg/back_arrow.svg", 24, 
            if (AppTheme.isDark) Color.WHITE else AppTheme.ytRed).apply {
            rotation = 90f
        }
        btn.addView(scrollTopIcon, FrameLayout.LayoutParams(dp(24), dp(24)).also {
            it.gravity = Gravity.CENTER
        })
        return btn
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SVG helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp)
            iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}