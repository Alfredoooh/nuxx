package com.doction.webviewapp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.adapters.VideoAdapter
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.theme.AppTheme
import kotlin.concurrent.thread

class ExploreView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private val recycler:     RecyclerView
    private val chipBar:      HorizontalScrollView
    private val loadingView:  FrameLayout
    private val errorView:    LinearLayout
    private val scrollTopBtn: FrameLayout

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip  = 0
    private var isLoading    = true
    private var isFetching   = false
    private var page         = 1

    private val chipLabels = listOf(
        "Todos", "Recentes", "Mais vistos", "Mais antigos",
        "Amador", "MILF", "Asiática", "Latina", "Loira"
    )

    private val adapter = VideoAdapter(shownVideos) { video ->
        activity.openVideoPlayer(video)
    }

    // Referências para redesenho de tema
    private lateinit var scrollTopIcon: android.widget.ImageView
    private val themeListener: () -> Unit = { applyTheme() }

    init {
        setBackgroundColor(AppTheme.bg)

        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(false)
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        recycler.adapter = adapter
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity   = Gravity.TOP
            it.topMargin = dp(52)
        })

        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
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
                scrollTopBtn.visibility = if (off > dp(600)) View.VISIBLE else View.GONE
            }
        })

        loadingView = buildLoadingView()
        addView(loadingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        AppTheme.addThemeListener(themeListener)
        fetch()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppTheme.removeThemeListener(themeListener)
    }

    // ── Aplicar tema em runtime ────────────────────────────────────────────────

    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        chipBar.setBackgroundColor(AppTheme.bg)
        loadingView.setBackgroundColor(AppTheme.bg)

        // Redesenha chips
        val row = chipBar.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as? TextView ?: continue
            val selected = i == currentChip
            chip.setTextColor(if (selected) AppTheme.chipTextActive else AppTheme.textSecondary)
            chip.background = makeChipBg(selected)
        }

        // Ícone scroll-to-top
        scrollTopBtn.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(AppTheme.cardAlt)
        }
        scrollTopIcon.setColorFilter(AppTheme.icon)
    }

    // ── Chip bar ───────────────────────────────────────────────────────────────

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        chipLabels.forEachIndexed { i, label ->
            val selected = i == 0
            val chip = TextView(context).apply {
                text     = label
                textSize = 12f
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (selected) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(selected)
                setPadding(dp(11), dp(5), dp(11), dp(5))
                gravity = Gravity.CENTER
                tag     = "chip_$i"
                setOnClickListener { selectChip(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)
            ).also { if (i > 0) it.leftMargin = dp(6) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun makeChipBg(selected: Boolean): GradientDrawable {
        val gd = GradientDrawable()
        gd.shape        = GradientDrawable.RECTANGLE
        gd.cornerRadius = dp(6).toFloat()
        gd.setColor(if (selected) AppTheme.chipBgActive else AppTheme.chipBg)
        return gd
    }

    private fun selectChip(index: Int) {
        val row  = chipBar.getChildAt(0) as LinearLayout
        val prev = row.findViewWithTag<TextView>("chip_$currentChip")
        prev?.setTextColor(AppTheme.textSecondary)
        prev?.background = makeChipBg(false)
        prev?.setTypeface(prev.typeface, Typeface.NORMAL)

        currentChip = index
        val curr = row.findViewWithTag<TextView>("chip_$index")
        curr?.setTextColor(AppTheme.chipTextActive)
        curr?.background = makeChipBg(true)
        curr?.setTypeface(curr.typeface, Typeface.BOLD)

        applyFilter()
    }

    // ── Filtro ─────────────────────────────────────────────────────────────────

    private fun applyFilter() {
        val filtered: List<FeedVideo> = when (currentChip) {
            2    -> allVideos.sortedByDescending { parseViews(it.views) }
            3    -> allVideos.reversed()
            else -> {
                val kws: List<String>? = when (currentChip) {
                    4    -> listOf("amador","amateur","caseiro","homemade")
                    5    -> listOf("milf","mature","maduro","cougar","mom","mãe")
                    6    -> listOf("asian","asiática","japanese","korean","chinese","thai","japan")
                    7    -> listOf("latina","latin","brazilian","brasileiro","colombiana","mexico")
                    8    -> listOf("blonde","loira","blond","blondie")
                    else -> null
                }
                if (kws == null) allVideos.toList()
                else allVideos.filter { v -> val t = v.title.lowercase(); kws.any { t.contains(it) } }
            }
        }
        shownVideos.clear()
        shownVideos.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun parseViews(raw: String) =
        try { raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L } catch (_: Exception) { 0L }

    // ── Fetch ──────────────────────────────────────────────────────────────────

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
                    if (result.isNotEmpty()) {
                        allVideos.addAll(result)
                        page++
                        applyFilter()
                    }
                    isFetching = false
                }
            } catch (_: Exception) {
                handler.post { isFetching = false }
            }
        }
    }

    // ── Loading skeleton ───────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val f = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.bg)
        }
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), 0)
        }
        val col1 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        repeat(4) { i ->
            col1.addView(buildSkeletonTile(if (i % 2 == 0) dp(120) else dp(90)))
            col2.addView(buildSkeletonTile(if (i % 2 == 0) dp(90) else dp(120)))
        }
        grid.addView(col1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        grid.addView(col2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        f.addView(grid)
        return f
    }

    private fun buildSkeletonTile(height: Int): View {
        val gd = GradientDrawable()
        gd.shape        = GradientDrawable.RECTANGLE
        gd.cornerRadius = dp(6).toFloat()
        gd.setColor(AppTheme.thumbShimmer1)
        val v = View(context).apply { background = gd }
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height
        ).also { it.bottomMargin = dp(12) }
        return v
    }

    // ── Error ──────────────────────────────────────────────────────────────────

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER

            addView(TextView(context).apply {
                text = "Sem ligação à internet"
                setTextColor(AppTheme.textSecondary)
                textSize = 13f
                gravity  = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Tentar novamente"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                val gd = GradientDrawable()
                gd.shape        = GradientDrawable.RECTANGLE
                gd.cornerRadius = dp(100).toFloat()
                gd.setColor(AppTheme.ytRed)
                background = gd
                setPadding(dp(20), dp(10), dp(20), dp(10))
                gravity = Gravity.CENTER
                setOnClickListener { fetch() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(16) })
        }
    }

    // ── Scroll to top ──────────────────────────────────────────────────────────

    private fun buildScrollTopBtn(): FrameLayout {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.setColor(AppTheme.cardAlt)
        val btn = FrameLayout(context).apply {
            background = gd
            elevation  = dp(4).toFloat()
            setOnClickListener { recycler.smoothScrollToPosition(0) }
        }
        // Usa back_arrow.svg rotacionado 180° para apontar para cima
        scrollTopIcon = activity.svgImageView("icons/svg/back_arrow.svg", 20, AppTheme.icon).apply {
            rotation = 90f
        }
        btn.addView(scrollTopIcon, FrameLayout.LayoutParams(dp(20), dp(20)).also {
            it.gravity = Gravity.CENTER
        })
        return btn
    }

    private fun dp(v: Int) = activity.dp(v)
}