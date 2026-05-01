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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private lateinit var drawerView: DrawerView

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private val isFetching  = AtomicBoolean(false)
    private var page        = 1
    private var showingFooterLoader = false

    // ── dimensões fixas ──────────────────────────────────────────────────────
    private val ptrSpaceH:  Int
    private val ptrTrigger: Int
    private val appBarH:    Int
    private val chipBarH:   Int
    private val contentTop: Int
    private val colGapPx:   Int
    private val sidePadPx:  Int

    // ── PTR ──────────────────────────────────────────────────────────────────
    private var ptrRefreshing    = false
    private var ptrSnapAnimating = false
    private var ptrTouchStartY   = 0f
    private var ptrTouchActive   = false
    private var ptrCurrentOffset = 0f
    private val ptrWrapper       = FrameLayout(context)
    private val ptrSpace         = FrameLayout(context)
    private val ptrSpinner: OrbitSpinner

    private var skeletonRunnable: Runnable? = null

    private val kRatios = floatArrayOf(
        16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f
    )

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira","Gay","Lésbicas","BDSM","Anal","Teen"
    )

    // ── loader CSS convertido para Canvas ────────────────────────────────────

    inner class CssLoader(ctx: android.content.Context) : View(ctx) {
        private val paintBefore1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintBefore2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintAfter1  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintAfter2  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        private var phase = 0f   // 0..1 loop de 2s
        private var running = false
        private val runner = object : Runnable {
            override fun run() {
                if (!running) return
                phase = (phase + (16f / 2000f)) % 1f
                invalidate()
                postDelayed(this, 16)
            }
        }

        fun startAnim() { if (running) return; running = true; post(runner) }
        fun stopAnim()  { running = false; removeCallbacks(runner) }

        // interpola keyframes idênticos ao CSS
        private fun lerpKeyframes(t: Float, k0: Float, k35: Float, k70: Float, k100: Float): Float {
            return when {
                t < 0.35f -> k0   + (k35  - k0)   * (t / 0.35f)
                t < 0.70f -> k35  + (k70  - k35)  * ((t - 0.35f) / 0.35f)
                else      -> k70  + (k100 - k70)   * ((t - 0.70f) / 0.30f)
            }
        }

        override fun onDraw(c: Canvas) {
            val cx   = width  / 2f
            val cy   = height / 2f
            val em   = width  / 2.5f   // 1em = largura/2.5
            val r    = em * 0.25f      // 0.25em raio da pílula
            val t    = phase

            // ── :before ──────────────────────────────────────────────────
            // width keyframes: 0.5em → 2.5em → 0.5em → 0.5em
            val bw = lerpKeyframes(t, em * 0.5f, em * 2.5f, em * 0.5f, em * 0.5f)
            val bh = em * 0.5f

            // box-shadow dot 1 (rosa): (1em,-0.5em) → (0,-0.5em) → (-1em,-0.5em) → (1em,-0.5em)
            val bs1x = lerpKeyframes(t,  em,  0f, -em,  em)
            val bs1y = -em * 0.5f
            // box-shadow dot 2 (azul): (-1em,0.5em) → (0,0.5em) → (1em,0.5em) → (-1em,0.5em)
            val bs2x = lerpKeyframes(t, -em,  0f,  em, -em)
            val bs2y =  em * 0.5f

            // pílula central :before
            paintBefore1.color = Color.argb(190, 225, 20, 98)
            c.drawRoundRect(cx - bw/2, cy - bh/2, cx + bw/2, cy + bh/2, r, r, paintBefore1)

            // dots sombra :before
            paintBefore1.color = Color.argb(190, 225, 20, 98)
            c.drawRoundRect(cx + bs1x - em*0.25f, cy + bs1y - em*0.25f,
                            cx + bs1x + em*0.25f, cy + bs1y + em*0.25f, r, r, paintBefore1)
            paintBefore2.color = Color.argb(190, 111, 202, 220)
            c.drawRoundRect(cx + bs2x - em*0.25f, cy + bs2y - em*0.25f,
                            cx + bs2x + em*0.25f, cy + bs2y + em*0.25f, r, r, paintBefore2)

            // ── :after ───────────────────────────────────────────────────
            // height keyframes: 0.5em → 2.5em → 0.5em → 0.5em
            val ah = lerpKeyframes(t, em * 0.5f, em * 2.5f, em * 0.5f, em * 0.5f)
            val aw = em * 0.5f

            // box-shadow dot 1 (verde): (0.5em,1em) → (0.5em,0) → (0.5em,-1em) → (0.5em,1em)
            val as1x =  em * 0.5f
            val as1y = lerpKeyframes(t,  em,  0f, -em,  em)
            // box-shadow dot 2 (amarelo): (-0.5em,-1em) → (-0.5em,0) → (-0.5em,1em) → (-0.5em,-1em)
            val as2x = -em * 0.5f
            val as2y = lerpKeyframes(t, -em,  0f,  em, -em)

            // pílula central :after (rodada 165deg — deslocamos o rect)
            paintAfter1.color = Color.argb(190, 61, 184, 143)
            c.drawRoundRect(cx - aw/2, cy - ah/2, cx + aw/2, cy + ah/2, r, r, paintAfter1)

            // dots sombra :after
            paintAfter1.color = Color.argb(190, 61, 184, 143)
            c.drawRoundRect(cx + as1x - em*0.25f, cy + as1y - em*0.25f,
                            cx + as1x + em*0.25f, cy + as1y + em*0.25f, r, r, paintAfter1)
            paintAfter2.color = Color.argb(190, 233, 169, 32)
            c.drawRoundRect(cx + as2x - em*0.25f, cy + as2y - em*0.25f,
                            cx + as2x + em*0.25f, cy + as2y + em*0.25f, r, r, paintAfter2)
        }

        fun detach() { stopAnim() }
    }

    // ── OrbitSpinner PTR ─────────────────────────────────────────────────────

    inner class OrbitSpinner(ctx: android.content.Context) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var phase = 0f
        private var spinning = false
        private val runner = object : Runnable {
            override fun run() {
                if (!spinning) return
                phase = (phase + 3f) % 360f
                invalidate()
                postDelayed(this, 16)
            }
        }
        fun startSpin() { if (spinning) return; spinning = true; post(runner) }
        fun stopSpin()  { spinning = false; removeCallbacks(runner) }
        fun detach()    { stopSpin() }

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

    // ── Adapter com footer loader embutido ───────────────────────────────────

    private val VTYPE_VIDEO  = 0
    private val VTYPE_LOADER = 1

    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class VideoVH(val root: View) : RecyclerView.ViewHolder(root)
        inner class LoaderVH(val loader: CssLoader) : RecyclerView.ViewHolder(
            FrameLayout(context).apply {
                addView(loader, FrameLayout.LayoutParams(dp(50), dp(50)).also {
                    it.gravity = Gravity.CENTER
                })
                layoutParams = StaggeredGridLayoutManager.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(90)
                ).also { (it as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true }
            }
        )

        private val loaderView = CssLoader(context)

        override fun getItemCount() = shownVideos.size + if (showingFooterLoader) 1 else 0

        override fun getItemViewType(pos: Int) =
            if (showingFooterLoader && pos == shownVideos.size) VTYPE_LOADER else VTYPE_VIDEO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VTYPE_LOADER) {
                LoaderVH(loaderView)
            } else {
                // delega ao VideoAdapter original mas precisamos de um ViewHolder compatível
                val vh = videoAdapterDelegate.onCreateViewHolder(parent, viewType)
                VideoVH(vh.itemView)
            }
        }

        // delegate para reutilizar a lógica de bind do VideoAdapter
        private val videoAdapterDelegate = VideoAdapter(shownVideos) { video, thumbView ->
            activity.openVideoPlayer(video, thumbView)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is LoaderVH -> holder.loader.startAnim()
                is VideoVH  -> videoAdapterDelegate.onBindViewHolder(
                    // wrap: reutilizamos o mesmo itemView, apenas chamamos bind
                    object : RecyclerView.ViewHolder(holder.root) {},
                    position
                )
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
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

        fun getDelegate() = videoAdapterDelegate
    }

    private val feedAdapter = FeedAdapter()

    init {
        val density = context.resources.displayMetrics.density
        fun dpI(v: Int) = (v * density).toInt()

        ptrSpaceH  = dpI(64)
        ptrTrigger = dpI(50)
        appBarH    = dpI(52)
        chipBarH   = dpI(40)
        contentTop = appBarH + chipBarH
        colGapPx   = dpI(8)
        sidePadPx  = dpI(10)

        setBackgroundColor(AppTheme.bg)

        ptrSpinner = OrbitSpinner(context).apply {
            alpha  = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }
        ptrSpace.addView(ptrSpinner, FrameLayout.LayoutParams(dpI(40), dpI(40)).also {
            it.gravity = Gravity.CENTER
        })
        ptrSpace.setBackgroundColor(AppTheme.bg)

        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            setPadding(sidePadPx, dpI(8), sidePadPx, dpI(90))
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
                    outRect.left = half; outRect.right = half; outRect.bottom = dpI(10)
                }
            })
        }
        recycler.adapter = feedAdapter

        ptrWrapper.addView(ptrSpace, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, ptrSpaceH))
        ptrWrapper.addView(recycler, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = ptrSpaceH
        })
        ptrWrapper.translationY = -ptrSpaceH.toFloat()

        addView(ptrWrapper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = contentTop
        })

        setupPtrTouch()

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
            it.gravity = Gravity.TOP
            it.topMargin = appBarH
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

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    // ── PTR ──────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPtrTouch() {
        recycler.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!ptrRefreshing && !ptrSnapAnimating) {
                        ptrTouchStartY   = event.rawY
                        ptrTouchActive   = false
                        ptrCurrentOffset = 0f
                        ptrSpinner.animate().cancel()
                        ptrSpinner.alpha  = 0f
                        ptrSpinner.scaleX = 0.4f
                        ptrSpinner.scaleY = 0.4f
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ptrRefreshing || ptrSnapAnimating) return@setOnTouchListener false
                    // usa scrollOffset em vez do layoutManager para evitar leituras stale
                    val atTop = recycler.computeVerticalScrollOffset() == 0
                    val dy    = event.rawY - ptrTouchStartY
                    if (dy > dp(8) && (atTop || shownVideos.isEmpty())) {
                        val drag = elasticDrag(dy)
                        if (drag > dp(4)) {
                            ptrTouchActive   = true
                            ptrCurrentOffset = drag
                            ptrWrapper.translationY = -ptrSpaceH + drag
                            val progress = (drag / ptrTrigger).coerceIn(0f, 1f)
                            ptrSpinner.alpha  = progress
                            ptrSpinner.scaleX = 0.4f + 0.6f * progress
                            ptrSpinner.scaleY = 0.4f + 0.6f * progress
                            (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                    if (ptrTouchActive) {
                        ptrTouchActive = false
                        if (ptrCurrentOffset >= ptrTrigger && !ptrRefreshing) {
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
        val max = ptrSpaceH.toFloat() * 1.4f
        return max * (1f - Math.exp((-dy / (max * 1.6f)).toDouble()).toFloat())
    }

    private fun triggerRefresh() {
        if (ptrRefreshing) return
        ptrRefreshing = true
        feedAdapter.hideLoader()
        ptrWrapper.animate()
            .translationY((-ptrSpaceH + ptrSpaceH * 0.9f).toFloat())
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
        ptrSpinner.alpha  = 1f
        ptrSpinner.scaleX = 1f
        ptrSpinner.scaleY = 1f
        ptrSpinner.startSpin()
        doRefresh()
    }

    private fun snapPtrBack(animate: Boolean) {
        if (ptrSnapAnimating) return
        ptrSnapAnimating = true
        ptrSpinner.stopSpin()
        val dur = if (animate) 320L else 0L
        ptrWrapper.animate()
            .translationY(-ptrSpaceH.toFloat())
            .setDuration(dur)
            .setInterpolator(DecelerateInterpolator(2.5f))
            .withEndAction {
                ptrRefreshing    = false
                ptrSnapAnimating = false
            }
            .start()
        ptrSpinner.animate()
            .alpha(0f).scaleX(0.4f).scaleY(0.4f)
            .setDuration(180)
            .start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        skeletonRunnable?.let { handler.removeCallbacks(it) }
        skeletonRunnable = null
        ptrSpinner.detach()
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (::drawerView.isInitialized && drawerView.parent === decorView) {
                decorView.removeView(drawerView)
            }
        } catch (_: Exception) {}
    }

    fun isDrawerOpen() = ::drawerView.isInitialized && drawerView.isDrawerOpen()
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
                setImageBitmap(bmp)
                setColorFilter(AppTheme.text)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            menuBtn.addView(iv, FrameLayout.LayoutParams(dp(22), dp(22)).also {
                it.gravity = Gravity.CENTER
            })
        } catch (_: Exception) {}

        row.addView(menuBtn, LinearLayout.LayoutParams(dp(40), appBarH))
        row.addView(TextView(context).apply {
            text = "Explorar"
            setTextColor(AppTheme.text)
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
            setPadding(dp(2), 0, 0, 0)
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
            val sel  = i == 0
            val chip = TextView(context).apply {
                text = label
                textSize = 12f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(12), 0, dp(12), 0)
                gravity = Gravity.CENTER
                tag = "chip_$i"
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
        cornerRadius = dp(8).toFloat()
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
        skeletonView.visibility = View.VISIBLE
        skeletonView.alpha      = 1f
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

        val total     = fetchers.size
        val completed = AtomicInteger(0)
        val anyShown  = AtomicBoolean(false)

        fetchers.forEach { fetcher ->
            thread {
                val result = try { fetcher() } catch (_: Exception) { emptyList() }
                handler.post {
                    val done = completed.incrementAndGet()
                    if (result.isNotEmpty()) {
                        allVideos.addAll(result)
                        allVideos.shuffle()
                        if (anyShown.compareAndSet(false, true)) {
                            skeletonView.animate().alpha(0f).setDuration(300).withEndAction {
                                skeletonView.visibility = View.GONE
                                skeletonView.alpha = 1f
                            }.start()
                            recycler.visibility = View.VISIBLE
                            isLoading = false
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
                        if (newVideos.isNotEmpty()) {
                            allVideos.clear()
                            newVideos.shuffle()
                            allVideos.addAll(newVideos)
                            page = 1
                            applyFilter()
                            recycler.scrollToPosition(0)
                        }
                        snapPtrBack(animate = true)
                    }
                }
            }
        }
    }

    private fun fetchMore() {
        if (!isFetching.compareAndSet(false, true)) return
        if (isLoading || ptrRefreshing) { isFetching.set(false); return }
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
                handler.post {
                    feedAdapter.hideLoader()
                    isFetching.set(false)
                }
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
                else allVideos.filter { v ->
                    val t = v.title.lowercase(); kws.any { t.contains(it) }
                }
            }
        }
        val prevSize = shownVideos.size
        shownVideos.clear()
        shownVideos.addAll(filtered)
        when {
            prevSize == 0 || filtered.size < prevSize -> feedAdapter.notifyDataSetChanged()
            filtered.size > prevSize -> {
                val added = filtered.size - prevSize
                feedAdapter.notifyItemRangeInserted(prevSize, added)
            }
            else -> feedAdapter.notifyDataSetChanged()
        }
    }

    private fun parseViews(raw: String) =
        try { raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L } catch (_: Exception) { 0L }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildSkeletonView(): FrameLayout {
        val root = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val sv   = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
                if (root.visibility != View.VISIBLE || root.parent == null) return
                phase = (phase + 0.05) % (Math.PI * 2)
                root.alpha = (0.5f + 0.5f * Math.sin(phase).toFloat()).coerceIn(0.25f, 1f)
                handler.postDelayed(this, 40)
                skeletonRunnable = this
            }
        }
        skeletonRunnable = run
        handler.post(run)
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    private fun buildErrorView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            addView(TextView(context).apply {
                text = "Sem ligação à internet"
                setTextColor(AppTheme.textSecondary)
                textSize = 13f; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
            addView(TextView(context).apply {
                text = "Tentar novamente"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
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

    // ── ScrollTop ─────────────────────────────────────────────────────────────

    private fun buildScrollTopBtn(): FrameLayout {
        return FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(4).toFloat()
            setOnClickListener { recycler.smoothScrollToPosition(0) }
            try {
                val px  = dp(20)
                val svg = SVG.getFromAsset(context.assets, "icons/svg/back_arrow.svg")
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(Canvas(bmp))
                val iv = android.widget.ImageView(context).apply {
                    setImageBitmap(bmp)
                    setColorFilter(AppTheme.ytRed)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    rotation  = 90f
                }
                addView(iv, FrameLayout.LayoutParams(dp(20), dp(20)).also {
                    it.gravity = Gravity.CENTER
                })
            } catch (_: Exception) {}
        }
    }
}