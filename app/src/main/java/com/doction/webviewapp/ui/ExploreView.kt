package com.doction.webviewapp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.adapters.VideoAdapter
import com.doction.webviewapp.models.FeedVideo

class ExploreView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val recycler: RecyclerView
    private val chipBar: HorizontalScrollView
    private val loadingView: FrameLayout
    private val errorView: LinearLayout
    private val scrollTopBtn: FrameLayout

    private val videos = mutableListOf<FeedVideo>()
    private var currentChip = 0
    private var isLoading = true
    private var isFetching = false
    private var page = 1

    private val chipLabels = listOf(
        "Todos", "Recentes", "Mais vistos", "Mais antigos",
        "Amador", "MILF", "Asiática", "Latina", "Loira"
    )

    private val adapter = VideoAdapter(videos) { video ->
        // TODO: ExibicaoPage — abrir vídeo
    }

    init {
        setBackgroundColor(Color.parseColor("#0A0A0A"))

        // Chip bar
        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).also {
            it.gravity = Gravity.TOP
            it.topMargin = dp(52)
        })

        // RecyclerView — masonry 2 colunas
        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setHasFixedSize(false)
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), dp(32))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        recycler.adapter = adapter
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Scroll to top FAB — inicializado ANTES do scroll listener
        scrollTopBtn = buildScrollTopBtn()
        scrollTopBtn.visibility = View.GONE
        addView(scrollTopBtn, LayoutParams(dp(40), dp(40)).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(16)
            it.rightMargin = dp(16)
        })

        // Scroll listener para infinite load
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as StaggeredGridLayoutManager
                val lastVisible = lm.findLastVisibleItemPositions(null).max()
                if (lastVisible >= videos.size - 6) fetchMore()
                val offset = rv.computeVerticalScrollOffset()
                scrollTopBtn.visibility = if (offset > dp(600)) View.VISIBLE else View.GONE
            }
        })

        // Loading skeleton
        loadingView = buildLoadingView()
        addView(loadingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Error view
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.gravity = Gravity.CENTER
        })

        fetch()
    }

    // ── Chip bar ───────────────────────────────────────────────────────────────

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        chipLabels.forEachIndexed { i, label ->
            val chip = TextView(context).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, if (i == currentChip) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (i == currentChip) Color.BLACK else Color.parseColor("#AAAAAA"))
                setBackgroundColor(if (i == currentChip) Color.WHITE else Color.parseColor("#2A2A2A"))
                setPadding(dp(11), dp(5), dp(11), dp(5))
                gravity = Gravity.CENTER
                tag = "chip_$i"
                setOnClickListener { selectChip(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)
            ).also { if (i > 0) it.leftMargin = dp(6) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun selectChip(index: Int) {
        val row = (chipBar.getChildAt(0) as LinearLayout)
        val prev = row.findViewWithTag<TextView>("chip_$currentChip")
        prev?.setTextColor(Color.parseColor("#AAAAAA"))
        prev?.setBackgroundColor(Color.parseColor("#2A2A2A"))
        prev?.setTypeface(prev.typeface, Typeface.NORMAL)

        currentChip = index
        val curr = row.findViewWithTag<TextView>("chip_$index")
        curr?.setTextColor(Color.BLACK)
        curr?.setBackgroundColor(Color.WHITE)
        curr?.setTypeface(curr.typeface, Typeface.BOLD)

        updateList()
    }

    // ── Fetch ──────────────────────────────────────────────────────────────────

    private fun fetch() {
        isLoading = true
        loadingView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        page = 1
        // TODO: FeedFetcher.fetchAll(page) { result ->
        //   videos.clear()
        //   videos.addAll(result)
        //   page++
        //   isLoading = false
        //   loadingView.visibility = View.GONE
        //   adapter.notifyDataSetChanged()
        // }
    }

    private fun fetchMore() {
        if (isFetching || isLoading) return
        isFetching = true
        // TODO: FeedFetcher.fetchAll(page) { result ->
        //   val start = videos.size
        //   videos.addAll(result)
        //   page++
        //   isFetching = false
        //   adapter.notifyItemRangeInserted(start, result.size)
        // }
    }

    private fun updateList() {
        // TODO: filtrar por chip e chamar adapter.notifyDataSetChanged()
        adapter.notifyDataSetChanged()
    }

    // ── Loading skeleton ───────────────────────────────────────────────────────

    private fun buildLoadingView(): FrameLayout {
        val f = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(52 + 40 + 8), dp(10), 0)
        }
        val col1 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        repeat(3) { i ->
            col1.addView(buildSkeletonTile(if (i % 2 == 0) dp(120) else dp(90)))
            col2.addView(buildSkeletonTile(if (i % 2 == 0) dp(90) else dp(120)))
        }
        grid.addView(col1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        grid.addView(col2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        f.addView(grid)
        return f
    }

    private fun buildSkeletonTile(height: Int): View {
        val v = View(context).apply {
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        lp.bottomMargin = dp(12)
        v.layoutParams = lp
        return v
    }

    // ── Error view ─────────────────────────────────────────────────────────────

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val msg = TextView(context).apply {
                text = "Sem ligação à internet"
                setTextColor(Color.parseColor("#888888"))
                textSize = 13f
                gravity = Gravity.CENTER
            }
            addView(msg)
            val btn = TextView(context).apply {
                text = "Tentar novamente"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#FF0000"))
                setPadding(dp(20), dp(10), dp(20), dp(10))
                gravity = Gravity.CENTER
                setOnClickListener { fetch() }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            addView(btn, lp)
        }
    }

    // ── Scroll to top ──────────────────────────────────────────────────────────

    private fun buildScrollTopBtn(): FrameLayout {
        val btn = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setOnClickListener {
                recycler.smoothScrollToPosition(0)
            }
        }
        val icon = activity.svgImageView("icons/svg/arrow_up.svg", 20, Color.WHITE)
        btn.addView(icon, FrameLayout.LayoutParams(dp(20), dp(20)).also {
            it.gravity = Gravity.CENTER
        })
        return btn
    }

    private fun dp(v: Int) = activity.dp(v)
}