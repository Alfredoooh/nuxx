package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
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

private val crossFadeEV = DrawableCrossFadeFactory.Builder(180).setCrossFadeEnabled(true).build()

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

private fun fixEncEV(raw: String): String {
    return try {
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        val decoded = String(bytes, Charsets.UTF_8)
        if (decoded.any { it.code > 127 } || raw.none { it.code > 127 }) decoded else raw
    } catch (_: Exception) { raw }
}

private const val STYLE_GRID_ADAPTIVE = "grid_adaptive"
private const val STYLE_YOUTUBE       = "youtube"
private const val STYLE_GRID_FIXED    = "grid_fixed"
private const val STYLE_CARD_M3       = "card_m3"
private const val STYLE_CARD_M3_COLOR = "card_m3_color"

private const val ICO_EV_BOOKMARK = "icons/svg/phosphor-icons/regular/bookmark.svg"
private const val ICO_EV_PLAYLIST = "icons/svg/phosphor-icons/regular/playlist.svg"
private const val ICO_EV_GLOBE    = "icons/svg/phosphor-icons/regular/globe.svg"
private const val ICO_EV_CAST     = "icons/svg/phosphor-icons/regular/broadcast.svg"

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ExploreView(context: android.content.Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val prefs    get() = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)

    private val recycler: RecyclerView
    private val chipBar: HorizontalScrollView
    private val skeletonView: FrameLayout
    private val errorView: LinearLayout
    private val swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerView: DrawerView

    private val allVideos   = mutableListOf<FeedVideo>()
    private val shownVideos = mutableListOf<FeedVideo>()

    private var currentChip = 0
    private var isLoading   = true
    private val isFetching  = AtomicBoolean(false)
    private var page        = 1

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

    private val chipKeywords = mapOf(
        4  to listOf("amateur","amador","homemade","caseiro","real","amatuer"),
        5  to listOf("milf","mature","cougar","mom","mother","mãe","older","mommy","stepmom"),
        6  to listOf("asian","japanese","korean","chinese","thai","vietnamese","filipina","japan","korea","china","asia","oriental"),
        7  to listOf("latina","latin","brazilian","colombian","mexican","spanish","hispanic","brazil","colombia","mexico","argentina","venezuela"),
        8  to listOf("blonde","blond","loira","blondie","fair","yellow hair"),
        9  to listOf("gay","homosexual","twink","bareback","men","male","guys","boys","muscle men","daddy"),
        10 to listOf("lesbian","lesbians","lésbica","lesbo","girl on girl","sapphic","women only","two girls","girlfriends"),
        11 to listOf("bdsm","bondage","fetish","domination","submission","slave","femdom","discipline","tied","whip","spanking"),
        12 to listOf("anal","ass fuck","butt","booty","anale","anally","backdoor","buttfuck","sodomie"),
        13 to listOf("teen","18","19","young","college","novinha","teenager","petite","young adult","barely legal")
    )

    private fun currentCardStyle() = prefs.getString("card_style", STYLE_GRID_ADAPTIVE) ?: STYLE_GRID_ADAPTIVE

    // ── Helper SVG ────────────────────────────────────────────────────────────
    private fun svgView(path: String, sizeDp: Int, tint: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class VideoVH(val root: View) : RecyclerView.ViewHolder(root)

        override fun getItemCount() = shownVideos.size
        override fun getItemViewType(pos: Int) = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VideoVH(buildCardView())

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder as VideoVH
            bindCard(holder.root, shownVideos[position], position)
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is VideoVH) {
                val iv = holder.root.findViewWithTag<android.widget.ImageView>("thumb")
                if (iv != null) try { Glide.with(iv.context).clear(iv) } catch (_: Exception) {}
            }
        }
    }

    private val feedAdapter = FeedAdapter()

    // ── Card builders ─────────────────────────────────────────────────────────
    private fun buildCardView() = when (currentCardStyle()) {
        STYLE_YOUTUBE                          -> buildYoutubeCard()
        STYLE_GRID_FIXED                       -> buildGridFixedCard()
        STYLE_CARD_M3, STYLE_CARD_M3_COLOR     -> buildM3Card()
        else                                   -> buildGridAdaptiveCard()
    }

    private fun bindCard(root: View, video: FeedVideo, position: Int) = when (currentCardStyle()) {
        STYLE_YOUTUBE                          -> bindYoutubeCard(root, video)
        STYLE_GRID_FIXED                       -> bindGridFixedCard(root, video)
        STYLE_CARD_M3, STYLE_CARD_M3_COLOR     -> bindM3Card(root, video, currentCardStyle() == STYLE_CARD_M3_COLOR)
        else                                   -> bindGridAdaptiveCard(root, video, position)
    }

    // Grid adaptável
    private fun buildGridAdaptiveCard(): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
        }
        val thumb = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(AppTheme.thumbBg)
            }
            tag = "thumb"
        }
        col.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))
        col.addView(TextView(context).apply {
            setTextColor(AppTheme.text); textSize = 12f; maxLines = 2
            setPadding(0, dp(6), 0, 0); tag = "title"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 10.5f; maxLines = 1
            setPadding(0, dp(2), 0, 0); tag = "meta"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return col
    }

    private fun bindGridAdaptiveCard(root: View, video: FeedVideo, position: Int) {
        val col   = root as LinearLayout
        val thumb = col.findViewWithTag<android.widget.ImageView>("thumb")!!
        val title = col.findViewWithTag<TextView>("title")!!
        val meta  = col.findViewWithTag<TextView>("meta")!!
        title.text = fixEncEV(video.title)
        meta.text  = buildString {
            append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
        }
        val ratio = RATIOS[position % RATIOS.size]
        val colW  = context.resources.displayMetrics.widthPixels / 2 - dp(18)
        (thumb.layoutParams as LinearLayout.LayoutParams).height = (colW / ratio).toInt()
        if (video.thumb.isNotEmpty()) loadThumbEV(thumb, video) else thumb.setImageDrawable(null)
        col.setOnClickListener { VideoPreviewModal.show(activity, video) }
        col.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showLongPressSheet(video); true
        }
    }

    // YouTube
    private fun buildYoutubeCard(): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
        }
        val thumb = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; tag = "thumb"
        }
        col.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0)))
        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        val favicon = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#F0F0F0")) }
            tag = "favicon"
        }
        infoRow.addView(favicon, LinearLayout.LayoutParams(dp(36), dp(36)))
        infoRow.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(context).apply {
            setTextColor(AppTheme.text); textSize = 13f; maxLines = 2
            setTypeface(null, Typeface.BOLD); tag = "title"
        })
        textCol.addView(TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1
            setPadding(0, dp(2), 0, 0); tag = "meta"
        })
        infoRow.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(infoRow)
        return col
    }

    private fun bindYoutubeCard(root: View, video: FeedVideo) {
        val col     = root as LinearLayout
        val thumb   = col.findViewWithTag<android.widget.ImageView>("thumb")!!
        val favicon = col.findViewWithTag<android.widget.ImageView>("favicon")!!
        val title   = col.findViewWithTag<TextView>("title")!!
        val meta    = col.findViewWithTag<TextView>("meta")!!
        val w = context.resources.displayMetrics.widthPixels
        (thumb.layoutParams as LinearLayout.LayoutParams).height = (w * 9f / 16f).toInt()
        title.text = fixEncEV(video.title)
        meta.text  = buildString {
            append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
            if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
        }
        if (video.thumb.isNotEmpty()) loadThumbEV(thumb, video)
        Glide.with(context)
            .load("https://www.google.com/s2/favicons?sz=32&domain=${domainOfEV(video.source)}")
            .override(dp(24), dp(24)).circleCrop().into(favicon)
        col.setOnClickListener { VideoPreviewModal.show(activity, video) }
        col.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showLongPressSheet(video); true
        }
    }

    // Grid fixo
    private fun buildGridFixedCard(): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(14))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
        }
        val thumb = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                setColor(AppTheme.thumbBg)
            }
            tag = "thumb"
        }
        val colW = context.resources.displayMetrics.widthPixels / 2 - dp(18)
        col.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (colW * 9f / 16f).toInt()))
        col.addView(TextView(context).apply {
            setTextColor(AppTheme.text); textSize = 12f; maxLines = 2
            setPadding(0, dp(6), 0, 0); tag = "title"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 10.5f; maxLines = 1
            setPadding(0, dp(2), 0, 0); tag = "meta"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return col
    }

    private fun bindGridFixedCard(root: View, video: FeedVideo) {
        val col   = root as LinearLayout
        val thumb = col.findViewWithTag<android.widget.ImageView>("thumb")!!
        val title = col.findViewWithTag<TextView>("title")!!
        val meta  = col.findViewWithTag<TextView>("meta")!!
        title.text = fixEncEV(video.title)
        meta.text  = buildString {
            append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
        }
        if (video.thumb.isNotEmpty()) loadThumbEV(thumb, video)
        col.setOnClickListener { VideoPreviewModal.show(activity, video) }
        col.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showLongPressSheet(video); true
        }
    }

    // M3
    private fun buildM3Card(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F8F8F8"))
                setStroke(dp(1), Color.parseColor("#E8E8E8"))
            }
            elevation = dp(2).toFloat()
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
        }
        val thumb = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            tag = "thumb"
        }
        val colW = context.resources.displayMetrics.widthPixels / 2 - dp(18)
        card.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (colW * 9f / 16f).toInt()))
        val infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        infoBox.addView(TextView(context).apply {
            setTextColor(Color.parseColor("#1C1B1F")); textSize = 12f; maxLines = 2
            setTypeface(null, Typeface.BOLD); tag = "title"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(TextView(context).apply {
            setTextColor(Color.parseColor("#49454F")); textSize = 10.5f; maxLines = 1
            setPadding(0, dp(3), 0, 0); tag = "meta"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(infoBox)
        return card
    }

    private fun bindM3Card(root: View, video: FeedVideo, dynamic: Boolean) {
        val card  = root as LinearLayout
        val thumb = card.findViewWithTag<android.widget.ImageView>("thumb")!!
        val title = card.findViewWithTag<TextView>("title")!!
        val meta  = card.findViewWithTag<TextView>("meta")!!
        title.text = fixEncEV(video.title)
        meta.text  = buildString {
            append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
        }
        if (video.thumb.isNotEmpty()) {
            if (dynamic) {
                Glide.with(context).asBitmap()
                    .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                        .addHeader("User-Agent", UA_EV)
                        .addHeader("Referer", refererEV(video.source)).build()))
                    .override(240)
                    .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                        override fun onResourceReady(bmp: android.graphics.Bitmap, t: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?) {
                            thumb.setImageBitmap(bmp)
                            try {
                                val scaled   = android.graphics.Bitmap.createScaledBitmap(bmp, 1, 1, true)
                                val dominant = scaled.getPixel(0, 0)
                                val bg       = Color.argb(30, Color.red(dominant), Color.green(dominant), Color.blue(dominant))
                                (card.background as? GradientDrawable)?.setColor(bg)
                            } catch (_: Exception) {}
                        }
                        override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {}
                    })
            } else loadThumbEV(thumb, video)
        }
        card.setOnClickListener { VideoPreviewModal.show(activity, video) }
        card.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showLongPressSheet(video); true
        }
    }

    private fun loadThumbEV(iv: android.widget.ImageView, video: FeedVideo) {
        Glide.with(context)
            .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                .addHeader("User-Agent", UA_EV)
                .addHeader("Referer", refererEV(video.source))
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8").build()))
            .override(480).centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(crossFadeEV))
            .into(iv)
    }

    private fun domainOfEV(src: VideoSource) = when (src) {
        VideoSource.EPORNER   -> "eporner.com"
        VideoSource.PORNHUB   -> "pornhub.com"
        VideoSource.REDTUBE   -> "redtube.com"
        VideoSource.YOUPORN   -> "youporn.com"
        VideoSource.XVIDEOS   -> "xvideos.com"
        VideoSource.XHAMSTER  -> "xhamster.com"
        VideoSource.SPANKBANG -> "spankbang.com"
        VideoSource.BRAVOTUBE -> "bravotube.net"
        VideoSource.DRTUBER   -> "drtuber.com"
        VideoSource.TXXX      -> "txxx.com"
        VideoSource.GOTPORN   -> "gotporn.com"
        VideoSource.PORNDIG   -> "porndig.com"
        else                  -> "google.com"
    }

    private fun buildLayoutManager(): RecyclerView.LayoutManager = when (currentCardStyle()) {
        STYLE_YOUTUBE -> LinearLayoutManager(context)
        else -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }
    }

    // ── Long press sheet ──────────────────────────────────────────────────────
    private fun showLongPressSheet(video: FeedVideo) {
        val dialog = BottomSheetDialog(
            activity,
            com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        )
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(), dp(16).toFloat(),
                    dp(16).toFloat(), dp(16).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(Color.WHITE)
            }
        }
        // Handlebar
        root.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }, LinearLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(10); it.bottomMargin = dp(10)
        })
        root.addView(TextView(context).apply {
            text = fixEncEV(video.title); setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 13.5f; setTypeface(null, Typeface.BOLD); maxLines = 2
            setPadding(dp(20), dp(8), dp(20), dp(2))
        })
        root.addView(TextView(context).apply {
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
            setTextColor(Color.parseColor("#888888")); textSize = 11.5f
            setPadding(dp(20), 0, dp(20), dp(14))
        })
        root.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        data class SI(val ico: String, val label: String, val action: () -> Unit)
        listOf(
            SI(ICO_EV_BOOKMARK, "Guardar para ver mais tarde") {
                dialog.dismiss()
                SavedVideosManager.saveVideo(context, video)
                activity.showSnackbarGlobal("Guardado nos vídeos guardados")
            },
            SI(ICO_EV_PLAYLIST, "Adicionar à playlist") {
                dialog.dismiss()
                activity.showSnackbarGlobal("Funcionalidade ainda em desenvolvimento")
            },
            SI(ICO_EV_GLOBE, "Ver no browser") {
                dialog.dismiss()
                activity.addContentOverlay(BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
        ).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                    background = activity.getDrawable(tv.resourceId)
                setOnClickListener { item.action() }
            }
            row.addView(svgView(item.ico, 22, Color.parseColor("#555555")),
                LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(16), 1))
            row.addView(TextView(context).apply {
                text = item.label; setTextColor(Color.parseColor("#1C1B1F")); textSize = 15f
            })
            root.addView(row)
        }
        root.addView(View(context), LinearLayout.LayoutParams(1, dp(24)))
        dialog.setContentView(root)
        dialog.show()
    }

    // ── init ──────────────────────────────────────────────────────────────────
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
            layoutManager = buildLayoutManager()
            setHasFixedSize(false)
            setPadding(sidePadPx, dpI(8), sidePadPx, dpI(56))
            clipToPadding = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator   = null
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val half = colGapPx / 2
                    outRect.left = half; outRect.right = half; outRect.bottom = dpI(10)
                }
            })
        }
        recycler.adapter = feedAdapter

        // Infinite scroll sem footer loader
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm   = rv.layoutManager
                val last = when (lm) {
                    is StaggeredGridLayoutManager -> lm.findLastVisibleItemPositions(null).maxOrNull() ?: 0
                    is LinearLayoutManager        -> lm.findLastVisibleItemPosition()
                    else                          -> 0
                }
                if (last >= shownVideos.size - 6) fetchMore()
            }
        })

        swipeRefresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(AppTheme.primary)
            setProgressBackgroundColorSchemeColor(AppTheme.bg)
            setOnRefreshListener { doRefresh() }
        }
        swipeRefresh.addView(recycler, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(swipeRefresh, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = contentTop
        })

        chipBar = buildChipBar()
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, chipBarH).also {
            it.gravity = Gravity.TOP; it.topMargin = appBarH
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
            setBackgroundColor(AppTheme.bg); elevation = dp(2).toFloat()
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), 0); gravity = Gravity.CENTER_VERTICAL
        }

        // Hamburger (mantém original)
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
        row.addView(menuBtn, LinearLayout.LayoutParams(dp(44), appBarH))

        // Título
        row.addView(TextView(context).apply {
            text = "Explorar"; setTextColor(AppTheme.text); textSize = 21f
            setTypeface(null, Typeface.BOLD); letterSpacing = -0.03f
            setPadding(dp(2), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Botão vídeos guardados — phosphor bookmark
        val savedBtn = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { activity.addContentOverlay(SavedVideosPage(context)) }
        }
        savedBtn.addView(
            svgView(ICO_EV_BOOKMARK, 22, AppTheme.iconSub),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER }
        )
        row.addView(savedBtn, LinearLayout.LayoutParams(dp(44), appBarH))

        // Botão cast — phosphor broadcast
        val castBtn = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { activity.showSnackbarGlobal("Cast não disponível") }
        }
        castBtn.addView(
            svgView(ICO_EV_CAST, 22, AppTheme.iconSub),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER }
        )
        row.addView(castBtn, LinearLayout.LayoutParams(dp(44), appBarH))

        appBar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, appBarH))
        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, appBarH).also { it.gravity = Gravity.TOP })
    }

    private fun buildDrawer() {
        val decorView = activity.window.decorView as ViewGroup
        drawerView = DrawerView(context)
        decorView.addView(drawerView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ── ChipBar ───────────────────────────────────────────────────────────────
    private fun buildChipBar(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AppTheme.bg); overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(7), dp(12), dp(7)); gravity = Gravity.CENTER_VERTICAL
        }
        chipLabels.forEachIndexed { i, label ->
            val sel  = i == 0
            val chip = TextView(context).apply {
                text = label; textSize = 12.5f
                setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (sel) AppTheme.chipTextActive else AppTheme.textSecondary)
                background = makeChipBg(sel)
                setPadding(dp(14), 0, dp(14), 0); gravity = Gravity.CENTER
                tag = "chip_$i"; includeFontPadding = false
                isClickable = true; isFocusable = true
                setOnClickListener {
                    animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
                    selectChip(i)
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)).also {
                if (i > 0) it.leftMargin = dp(7)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun makeChipBg(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
        setColor(if (selected) AppTheme.primary else AppTheme.chipBg)
        if (!selected) setStroke(dp(1), AppTheme.divider)
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
        val total = fetchers.size
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
        thread {
            try {
                val result = FeedFetcher.fetchAll(page)
                handler.post {
                    if (result.isNotEmpty()) { allVideos.addAll(result); page++; applyFilter() }
                    isFetching.set(false)
                }
            } catch (_: Exception) {
                handler.post { isFetching.set(false) }
            }
        }
    }

    private fun videoMatches(v: FeedVideo, keywords: List<String>): Boolean {
        val title = v.title.lowercase()
        val tags  = v.tags.joinToString(" ").lowercase()
        val cats  = v.categories.joinToString(" ").lowercase()
        val perf  = v.performer.lowercase()
        val src   = v.source.label.lowercase()
        return keywords.any { kw ->
            title.contains(kw) || tags.contains(kw) || cats.contains(kw) ||
            perf.contains(kw)  || src.contains(kw)
        }
    }

    private fun applyFilter() {
        val feedPrefs = prefs.getStringSet("feed_prefs", emptySet()) ?: emptySet()
        val filtered: List<FeedVideo> = when (currentChip) {
            0 -> {
                if (feedPrefs.isEmpty()) allVideos.toList()
                else {
                    val prefKws = feedPrefs.flatMap { pref ->
                        when (pref) {
                            "Amador"   -> listOf("amateur","amador","homemade")
                            "MILF"     -> listOf("milf","mature","mom","mother")
                            "Asiática" -> listOf("asian","japanese","korean","chinese")
                            "Latina"   -> listOf("latina","latin","brazilian","colombian")
                            "Loira"    -> listOf("blonde","blond","loira")
                            "Gay"      -> listOf("gay","homosexual","twink","men")
                            "Lésbicas" -> listOf("lesbian","lesbians","girl on girl")
                            "BDSM"     -> listOf("bdsm","bondage","fetish","domination")
                            "Anal"     -> listOf("anal","ass fuck","booty")
                            "Teen"     -> listOf("teen","18","young","college","novinha")
                            "Hardcore" -> listOf("hardcore","rough","intense")
                            "Softcore" -> listOf("softcore","solo","tease","gentle")
                            else       -> emptyList()
                        }
                    }
                    val preferred = allVideos.filter { videoMatches(it, prefKws) }
                    val rest      = allVideos.filter { !videoMatches(it, prefKws) }
                    preferred + rest
                }
            }
            1    -> allVideos.sortedByDescending { it.publishedAt }
            2    -> allVideos.sortedByDescending { parseViewsEV(it.views) }
            3    -> allVideos.sortedBy { it.publishedAt }
            else -> {
                val kws = chipKeywords[currentChip] ?: emptyList()
                if (kws.isEmpty()) allVideos.toList()
                else allVideos.filter { videoMatches(it, kws) }
            }
        }
        val prevSize = shownVideos.size
        shownVideos.clear(); shownVideos.addAll(filtered)
        when {
            prevSize == 0 || filtered.size < prevSize -> feedAdapter.notifyDataSetChanged()
            filtered.size > prevSize                  -> feedAdapter.notifyItemRangeInserted(prevSize, filtered.size - prevSize)
            else                                      -> feedAdapter.notifyDataSetChanged()
        }
    }

    private fun parseViewsEV(raw: String) = try {
        raw.replace(Regex("[^\\d]"), "").toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }

    // ── Skeleton ──────────────────────────────────────────────────────────────
    private fun buildSkeletonView(): FrameLayout {
        val root = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val sv   = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row  = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(sidePadPx + colGapPx / 2, dp(8), sidePadPx + colGapPx / 2, dp(32))
        }
        val screenW = resources.displayMetrics.widthPixels
        val colW    = (screenW - sidePadPx * 2 - colGapPx) / 2
        val col1    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val col2    = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until 10) {
            val ratio = kRatios[i % kRatios.size]; val h = (colW / ratio).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
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
                handler.postDelayed(this, 40); skeletonRunnable = this
            }
        }
        skeletonRunnable = run; handler.post(run)
    }

    private fun buildErrorView(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(32), 0, dp(32), 0)
        addView(TextView(context).apply {
            text = "Sem ligação à internet"; setTextColor(AppTheme.textSecondary)
            textSize = 13f; gravity = Gravity.CENTER
        })
        addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
        addView(TextView(context).apply {
            text = "Tentar novamente"; setTextColor(Color.WHITE); textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(100).toFloat()
                setColor(AppTheme.primary)
            }
            setPadding(dp(20), dp(10), dp(20), dp(10)); gravity = Gravity.CENTER
            isClickable = true; isFocusable = true; setOnClickListener { fetch() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        skeletonRunnable?.let { handler.removeCallbacks(it) }; skeletonRunnable = null
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (::drawerView.isInitialized && drawerView.parent === decorView)
                decorView.removeView(drawerView)
        } catch (_: Exception) {}
    }
}