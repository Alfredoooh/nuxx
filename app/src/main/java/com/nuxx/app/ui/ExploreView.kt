package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import com.nuxx.app.MainActivity
import com.nuxx.app.models.FeedFetcher
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.models.VideoSource
import com.nuxx.app.theme.AppTheme
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.*

private val RATIOS = listOf(16f/9, 4f/3, 16f/9, 16f/9, 4f/3, 16f/9, 16f/9, 4f/3, 16f/9, 16f/9)

private const val UA_EV = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private val crossFade = DrawableCrossFadeFactory.Builder(180).setCrossFadeEnabled(true).build()

private fun refererEV(src: VideoSource) = when (src) {
    VideoSource.EPORNER   -> "https://www.eporner.com/"
    VideoSource.PORNHUB   -> "https://www.pornhub.com/"
    VideoSource.REDTUBE   -> "https://www.redtube.com/"
    VideoSource.YOUPORN   -> "https://www.youporn.com/"
    VideoSource.XVIDEOS   -> "https://www.xvideos.com/"
    VideoSource.XHAMSTER  -> "https://xhamster.com/"
    VideoSource.SPANKBANG -> "https://spankbang.com/"
    VideoSource.BRAVOTUBE -> "https://www.bravotube.net/"
    VideoSource.DRTUBER   -> "https://www.drtuber.com/"
    VideoSource.TXXX      -> "https://www.txxx.com/"
    VideoSource.GOTPORN   -> "https://www.gotporn.com/"
    VideoSource.PORNDIG   -> "https://www.porndig.com/"
    else                  -> "https://www.google.com/"
}

private fun fixEnc(raw: String): String {
    return try {
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val decoded = String(bytes, Charsets.UTF_8)
        if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
    } catch (_: Exception) { raw }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ExploreView(context: android.content.Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private val recycler: RecyclerView
    private val chipBar: HorizontalScrollView
    private val skeletonView: FrameLayout
    private val errorView: LinearLayout
    private val scrollTopBtn: FrameLayout
    private val swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerView: DrawerView

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private val isFetching  = AtomicBoolean(false)
    private var page        = 1
    private var showingFooterLoader = false

    private val appBarH: Int
    private val chipBarH: Int
    private val contentTop: Int
    private val colGapPx: Int
    private val sidePadPx: Int

    private var skeletonRunnable: Runnable? = null

    private val kRatios = floatArrayOf(16f/9f, 4f/3f, 16f/9f, 16f/9f, 4f/3f,
                                        16f/9f, 16f/9f, 4f/3f, 16f/9f, 16f/9f)

    private val chipLabels = listOf(
        "Todos","Recentes","Mais vistos","Mais antigos",
        "Amador","MILF","Asiática","Latina","Loira",
        "Gay","Lésbicas","BDSM","Anal","Teen"
    )

    @SuppressLint("RestrictedApi")
    private fun buildM3Loader(): CircularProgressIndicator {
        val indicator = CircularProgressIndicator(context)
        indicator.isIndeterminate   = true
        indicator.indicatorSize     = dp(30)
        indicator.trackThickness    = dp(3)
        indicator.trackCornerRadius = dp(50)
        indicator.setIndicatorColor(AppTheme.ytRed)
        indicator.trackColor = Color.parseColor("#22000000")
        try {
            val cls = indicator.javaClass.superclass
            cls?.getDeclaredMethod("setWavelength", Int::class.java)
                ?.apply { isAccessible = true }
                ?.invoke(indicator, dp(8))
            cls?.getDeclaredMethod("setWaveAmplitude", Int::class.java)
                ?.apply { isAccessible = true }
                ?.invoke(indicator, dp(2))
        } catch (_: Exception) {}
        return indicator
    }

    private val VTYPE_VIDEO  = 0
    private val VTYPE_LOADER = 1

    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class VideoVH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            val thumb: ImageView = root.getChildAt(0) as ImageView
            val title: TextView  = root.getChildAt(1) as TextView
            val meta:  TextView  = root.getChildAt(2) as TextView
        }

        inner class LoaderVH(val indicator: CircularProgressIndicator) : RecyclerView.ViewHolder(
            FrameLayout(context).apply {
                addView(indicator, FrameLayout.LayoutParams(dp(30), dp(30)).also {
                    it.gravity = Gravity.CENTER
                })
            }
        ) {
            init {
                val lp = StaggeredGridLayoutManager.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(44))
                lp.isFullSpan = true
                itemView.layoutParams = lp
            }
        }

        override fun getItemCount() = shownVideos.size + if (showingFooterLoader) 1 else 0
        override fun getItemViewType(pos: Int) =
            if (showingFooterLoader && pos == shownVideos.size) VTYPE_LOADER else VTYPE_VIDEO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VTYPE_LOADER) return LoaderVH(buildM3Loader())

            val ctx = parent.context
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(14))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (ok) foreground = ctx.getDrawable(tv.resourceId)
            }
            val thumb = ImageView(ctx).apply {
                scaleType       = ImageView.ScaleType.CENTER_CROP
                clipToOutline   = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply {
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
            if (holder is LoaderVH) { holder.indicator.show(); return }
            holder as VideoVH
            val video = shownVideos[position]

            holder.title.text = fixEnc(video.title)
            holder.meta.text  = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
            }
            holder.title.setTextColor(AppTheme.text)
            holder.meta.setTextColor(AppTheme.textSecondary)

            val ratio = RATIOS[position % RATIOS.size]
            val colW  = context.resources.displayMetrics.widthPixels / 2 - dp(18)
            val h     = (colW / ratio).toInt()
            (holder.thumb.layoutParams as LinearLayout.LayoutParams).height = h
            holder.thumb.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(AppTheme.thumbBg)
            }
            holder.thumb.clipToOutline = true

            if (video.thumb.isNotEmpty()) {
                Glide.with(context)
                    .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                        .addHeader("User-Agent", UA_EV)
                        .addHeader("Referer", refererEV(video.source))
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                        .build()))
                    .override(480).centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(crossFade))
                    .into(holder.thumb)
            } else holder.thumb.setImageDrawable(null)

            holder.root.setOnClickListener { ExibicaoPage.start(activity, video) }
            holder.root.setOnLongClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showLongPressSheet(video); true
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is VideoVH) {
                try { Glide.with(holder.thumb.context).clear(holder.thumb) } catch (_: Exception) {}
            }
            if (holder is LoaderVH) holder.indicator.hide()
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

    private fun showLongPressSheet(video: FeedVideo) {
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )

        val sheetRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val handlebar = View(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }
        sheetRoot.addView(handlebar, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity      = Gravity.CENTER_HORIZONTAL
            it.topMargin    = dp(10)
            it.bottomMargin = dp(10)
        })

        sheetRoot.addView(TextView(context).apply {
            text = fixEnc(video.title)
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 13.5f
            setTypeface(null, Typeface.BOLD); maxLines = 2
            setPadding(dp(20), dp(8), dp(20), dp(2))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheetRoot.addView(TextView(context).apply {
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty())    append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
            setTextColor(Color.parseColor("#888888")); textSize = 11.5f
            setPadding(dp(20), 0, dp(20), dp(14))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sheetRoot.addView(View(context).apply { setBackgroundColor(Color.parseColor("#EEEEEE")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        data class SI(val icon: String, val label: String, val action: () -> Unit)
        listOf(
            SI("icons/svg/bookmark.svg", "Guardar para ver mais tarde") {
                dialog.dismiss(); showSnackbar("Guardado") },
            SI("icons/svg/playlist_add.svg", "Adicionar à playlist") {
                dialog.dismiss(); showSnackbar("Adicionado à playlist") },
            SI("icons/svg/open_in_browser.svg", "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(
                    BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
        ).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                val ok = activity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true)
                if (ok) background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(activity.svgImageView(item.icon, 22, Color.parseColor("#555555")),
                LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(context).apply {
                text = item.label
                setTextColor(Color.parseColor("#1C1B1F")); textSize = 15f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            sheetRoot.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        sheetRoot.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(sheetRoot)
        dialog.show()
    }

    private fun showSnackbar(message: String) {
        (findViewWithTag<View>("snackbar_ev"))?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        val navH = activity.navBarHeight + activity.dp(activity.bottomNavHeightDp)
        val snack = FrameLayout(context).apply {
            tag = "snackbar_ev"
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1C1B1F"))
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        snack.addView(TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#F4EFF4"))
            textSize = 14f
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })

        addView(snack, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity      = Gravity.BOTTOM
            it.bottomMargin = navH + dp(8)
            it.leftMargin   = dp(12)
            it.rightMargin  = dp(12)
        })

        snack.alpha = 0f; snack.translationY = dp(16).toFloat()
        snack.animate().alpha(1f).translationY(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        handler.postDelayed({
            if (snack.isAttachedToWindow)
                snack.animate().alpha(0f).translationY(dp(16).toFloat()).setDuration(160)
                    .withEndAction { (snack.parent as? ViewGroup)?.removeView(snack) }.start()
        }, 3000)
    }

    init {
        val density = context.resources.displayMetrics.density
        fun dpI(v: Int) = (v * density).toInt()

        appBarH    = dpI(52)
        chipBarH   = dpI(44)
        contentTop = appBarH + chipBarH
        colGapPx   = dpI(8)
        sidePadPx  = dpI(10)

        setBackgroundColor(AppTheme.bg)

        recycler = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            setHasFixedSize(false)
            setPadding(sidePadPx, dpI(8), sidePadPx, dpI(72))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator   = null
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect, view: View,
                    parent: RecyclerView, state: RecyclerView.State
                ) {
                    val half = colGapPx / 2
                    outRect.left = half; outRect.right = half; outRect.bottom = dpI(10)
                }
            })
        }
        recycler.adapter = feedAdapter

        swipeRefresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(AppTheme.ytRed)
            setProgressBackgroundColorSchemeColor(AppTheme.bg)
            setOnRefreshListener { doRefresh() }
        }
        swipeRefresh.addView(recycler,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(swipeRefresh,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
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
            it.gravity      = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(82); it.rightMargin = dp(16)
        })

        skeletonView = buildSkeletonView()
        addView(skeletonView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
                it.topMargin = contentTop
            })

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        addView(errorView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
                it.gravity = Gravity.CENTER
            })

        buildAppBar()
        buildDrawer()
        fetch()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); activity.setStatusBarDark(false)
    }

    private fun dp(v: Int)   = (v * context.resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * context.resources.displayMetrics.density)

    fun isDrawerOpen()      = ::drawerView.isInitialized && drawerView.isDrawerOpen()
    fun closeDrawerIfOpen() { if (::drawerView.isInitialized) drawerView.close() }

    private fun buildAppBar() {
        val appBar = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.bg); elevation = dp(2).toFloat()
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        val menuBtn = FrameLayout(context).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true; isFocusable = true
            setOnClickListener { drawerView.toggle() }
        }
        try {
            val px = dp(22)
            val svg = SVG.getFromAsset(context.assets, "icons/svg/hamburger.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            menuBtn.addView(ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(AppTheme.text)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        row.addView(menuBtn, LinearLayout.LayoutParams(dp(40), appBarH))

        row.addView(TextView(context).apply {
            text = "Explorar"; setTextColor(AppTheme.text); textSize = 21f
            setTypeface(null, Typeface.BOLD); letterSpacing = -0.03f
            setPadding(dp(2), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val castBtn = FrameLayout(context).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true; isFocusable = true
            setOnClickListener { showSnackbar("Cast não disponível") }
        }
        try {
            val px = dp(24)
            val svg = SVG.getFromAsset(context.assets, "icons/svg/cast.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            castBtn.addView(ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(AppTheme.text)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, FrameLayout.LayoutParams(dp(24), dp(24)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
        row.addView(castBtn, LinearLayout.LayoutParams(dp(40), appBarH))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, appBarH))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, appBarH).also {
            it.gravity = Gravity.TOP
        })
    }

    // CORRIGIDO: drawer volta ao decorView como estava no original
    private fun buildDrawer() {
        val decorView = activity.window.decorView as ViewGroup
        drawerView = DrawerView(context)
        decorView.addView(drawerView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            gravity = Gravity.CENTER_VERTICAL
        }
        chipLabels.forEachIndexed { i, label ->
            val sel  = i == 0
            val chip = TextView(context).apply {
                text = label; textSize = 12.5f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(14), 0, dp(14), 0)
                gravity = Gravity.CENTER
                tag = "chip_$i"; includeFontPadding = false
                isClickable = true; isFocusable = true
                setOnClickListener {
                    animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
                    selectChip(i)
                }
            }
            row.addView(chip,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)).also {
                    if (i > 0) it.leftMargin = dp(7)
                })
        }
        scroll.addView(row)
        return scroll
    }

    private fun makeChipBg(selected: Boolean) = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(100).toFloat()
        setColor(if (selected) AppTheme.chipBgActive else AppTheme.chipBg)
        if (!selected) setStroke(dp(1), AppTheme.divider)
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

    private fun fetch() {
        isLoading = true; page = 1
        skeletonView.visibility = View.VISIBLE; skeletonView.alpha = 1f
        errorView.visibility = View.GONE; recycler.visibility = View.GONE

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
                                skeletonView.visibility = View.GONE
                                skeletonView.alpha = 1f
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

    private fun videoMatches(v: FeedVideo, keywords: List<String>): Boolean {
        val title = v.title.lowercase()
        val tags  = v.tags.joinToString(" ").lowercase()
        val cats  = v.categories.joinToString(" ").lowercase()
        val perf  = v.performer.lowercase()
        return keywords.any { kw ->
            title.contains(kw) || tags.contains(kw) || cats.contains(kw) || perf.contains(kw)
        }
    }

    private fun applyFilter() {
        val filtered: List<FeedVideo> = when (currentChip) {
            0    -> allVideos.toList()
            1    -> allVideos.sortedByDescending { it.publishedAt }
            2    -> allVideos.sortedByDescending { parseViews(it.views) }
            3    -> allVideos.sortedBy { it.publishedAt }
            else -> {
                val kws: List<String> = when (currentChip) {
                    4  -> listOf("amador","amateur","caseiro","homemade")
                    5  -> listOf("milf","mature","maduro","cougar","mom","mãe","mother")
                    6  -> listOf("asian","asiática","japonesa","japanese","korean","chinese",
                                 "thai","japan","korea","china")
                    7  -> listOf("latina","latin","brazilian","brasileiro","colombiana",
                                 "mexico","mexican","spanish")
                    8  -> listOf("blonde","loira","blond","blondie","louro")
                    9  -> listOf("gay","gays","homosexual","twink","bareback","men")
                    10 -> listOf("lesbian","lésbica","lesbians","lesbo","girl on girl","sapphic")
                    11 -> listOf("bdsm","bondage","fetish","dominat","submiss","slave",
                                 "femdom","discipline")
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
            filtered.size > prevSize ->
                feedAdapter.notifyItemRangeInserted(prevSize, filtered.size - prevSize)
            else -> feedAdapter.notifyDataSetChanged()
        }
    }

    private fun parseViews(raw: String) = try {
        raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }

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
                shape = GradientDrawable.RECTANGLE
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
        skeletonRunnable = run; handler.post(run)
    }

    private fun buildErrorView(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(32), 0, dp(32), 0)
        addView(TextView(context).apply {
            text = "Sem ligação à internet"
            setTextColor(AppTheme.textSecondary); textSize = 13f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
        addView(TextView(context).apply {
            text = "Tentar novamente"; setTextColor(Color.WHITE); textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(AppTheme.ytRed)
            }
            setPadding(dp(20), dp(10), dp(20), dp(10)); gravity = Gravity.CENTER
            isClickable = true; isFocusable = true
            setOnClickListener { fetch() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun buildScrollTopBtn(): FrameLayout = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(Color.WHITE)
        }
        elevation = dp(4).toFloat()
        setOnClickListener { recycler.smoothScrollToPosition(0) }
        try {
            val px  = dp(20)
            val svg = SVG.getFromAsset(context.assets, "icons/svg/back_arrow.svg")
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            addView(ImageView(context).apply {
                setImageBitmap(bmp); setColorFilter(AppTheme.ytRed)
                scaleType = ImageView.ScaleType.CENTER_INSIDE; rotation = 90f
            }, FrameLayout.LayoutParams(dp(20), dp(20)).also { it.gravity = Gravity.CENTER })
        } catch (_: Exception) {}
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