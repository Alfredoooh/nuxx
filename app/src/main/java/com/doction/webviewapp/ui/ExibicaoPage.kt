// ExibicaoPage.kt
package com.doction.webviewapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.NestedScrollView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.models.FeedFetcher
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.models.VideoSource
import com.doction.webviewapp.theme.AppTheme
import kotlin.concurrent.thread
import kotlin.random.Random

private val CONVERT_APIS = listOf(
    "https://nuxxconvert1.onrender.com",
    "https://nuxxconvert2.onrender.com",
    "https://nuxxconvert3.onrender.com",
    "https://nuxxconvert4.onrender.com",
    "https://nuxxconvert5.onrender.com",
)

private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

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
    else                       -> "https://www.google.com/"
}

@SuppressLint("ViewConstructor")
class ExibicaoPage(
    context: Context,
    private val video: FeedVideo,
    private val onVideoTap: (FeedVideo) -> Unit
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())

    private lateinit var webView:     WebView
    private lateinit var spinnerView: FrameLayout
    private lateinit var errorView:   FrameLayout
    private lateinit var recycler:    RecyclerView

    private lateinit var titleTv:  TextView
    private lateinit var metaTv:   TextView
    private lateinit var btnDownload: FrameLayout

    private val relatedList = mutableListOf<FeedVideo>()
    private lateinit var relatedAdapter: RelatedAdapter

    private var playerHtml:   String? = null
    private var directUrl:    String? = null
    private var extracting    = false
    private var isDestroyed   = false

    init {
        setBackgroundColor(AppTheme.bg)
        applyDarkStatusBar()
        buildUI()
        loadPlayerTemplate()
        extractAndPlay(video.videoUrl)
        loadRelated()
    }

    // ── Status bar preta com ícones brancos ───────────────────────────────────
    private fun applyDarkStatusBar() {
        activity.window.statusBarColor = Color.BLACK
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isDestroyed = true
    }

    private fun buildUI() {
        val screenW = context.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        val rootCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Status bar spacer preto
        rootCol.addView(View(context).apply { setBackgroundColor(Color.BLACK) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))

        // ── Player ────────────────────────────────────────────────────────────
        val playerContainer = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        webView = buildWebView()
        playerContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        spinnerView = buildSpinner()
        playerContainer.addView(spinnerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        errorView = buildErrorView()
        errorView.visibility = View.GONE
        playerContainer.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Botão back
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { activity.closeVideoPlayer() }
        }
        btnBack.addView(activity.svgImageView("icons/svg/settings/settings_back.svg", 22, Color.WHITE),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        playerContainer.addView(btnBack, FrameLayout.LayoutParams(dp(42), dp(42)).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = dp(6); it.leftMargin = dp(4)
        })

        rootCol.addView(playerContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, playerH))

        // ── Descrição fixa ────────────────────────────────────────────────────
        val infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(AppTheme.bg)
        }
        titleTv = TextView(context).apply {
            text = video.title; setTextColor(AppTheme.text)
            textSize = 14.5f; setTypeface(typeface, Typeface.BOLD); maxLines = 3
        }
        infoBox.addView(titleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        metaTv = TextView(context).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty())    append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        }
        infoBox.addView(metaTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))

        // ── Botão download estilo YouTube ─────────────────────────────────────
        btnDownload = FrameLayout(context).apply { visibility = View.GONE }

        val dlPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                setColor(AppTheme.chipBg ?: Color.parseColor("#1A1A1A").let {
                    if (AppTheme.bg == Color.WHITE || AppTheme.bg == Color.parseColor("#FFFFFF"))
                        Color.parseColor("#F2F2F2")
                    else
                        Color.parseColor("#272727")
                })
            }
            setPadding(dp(16), dp(10), dp(20), dp(10))
        }

        dlPill.addView(
            activity.svgImageView("icons/svg/download.svg", 18, AppTheme.text),
            LinearLayout.LayoutParams(dp(18), dp(18))
        )
        dlPill.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
        dlPill.addView(TextView(context).apply {
            text = "Descarregar"
            setTextColor(AppTheme.text)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        })

        btnDownload.addView(dlPill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT))

        infoBox.addView(btnDownload, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        infoBox.addView(View(context).apply { setBackgroundColor(AppTheme.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        rootCol.addView(infoBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── Scroll independente dos relacionados ──────────────────────────────
        val relatedScroll = NestedScrollView(context).apply {
            isFillViewport = true; setBackgroundColor(AppTheme.bg)
        }
        val relatedCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(AppTheme.bg)
        }
        relatedCol.addView(TextView(context).apply {
            text = "Relacionados"; setTextColor(AppTheme.text)
            textSize = 13.5f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Skeleton
        val skeletonBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; tag = "skeleton"
        }
        repeat(5) { skeletonBox.addView(buildRelatedSkeleton()) }
        relatedCol.addView(skeletonBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        relatedAdapter = RelatedAdapter(
            items     = relatedList,
            onTap     = { v -> onVideoTap(v) },
            onMenuTap = { v, anchor -> showPopupMenu(v, anchor) }
        )
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false); isNestedScrollingEnabled = false
            adapter = relatedAdapter; visibility = View.GONE
        }
        relatedCol.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        relatedCol.addView(View(context), LinearLayout.LayoutParams(1, dp(32)))

        relatedScroll.addView(relatedCol, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootCol.addView(relatedScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(rootCol, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── Popup menu nativo claro (igual ao SearchView) ─────────────────────────
    private fun showPopupMenu(video: FeedVideo, anchor: View) {
        val lightCtx = ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_Light)
        val popup    = PopupMenu(lightCtx, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Guardar para ver mais tarde")
        popup.menu.add(0, 2, 0, "Adicionar à playlist")
        popup.menu.add(0, 3, 0, "Ver no browser")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                2 -> Toast.makeText(context, "Adicionado à playlist", Toast.LENGTH_SHORT).show()
                3 -> activity.addContentOverlay(
                        BrowserPage(context, freeNavigation = true, externalUrl = video.videoUrl))
            }
            true
        }
        popup.show()
    }

    // ── Player template ───────────────────────────────────────────────────────
    private fun loadPlayerTemplate() {
        thread {
            try {
                val html = context.assets.open("player.html").bufferedReader().use { it.readText() }
                handler.post { playerHtml = html; directUrl?.let { loadIntoWebView(it) } }
            } catch (_: Exception) {
                handler.post { playerHtml = FALLBACK_HTML; directUrl?.let { loadIntoWebView(it) } }
            }
        }
    }

    private fun buildHtml(url: String): String {
        val escaped = url.replace("\"", "&quot;")
        return (playerHtml ?: FALLBACK_HTML).replace("{{VIDEO_URL}}", escaped)
    }

    private fun loadIntoWebView(url: String) {
        if (playerHtml == null) return
        webView.loadDataWithBaseURL("https://nuxxx.app", buildHtml(url), "text/html", "UTF-8", null)
    }

    // ── Extracção paralela ────────────────────────────────────────────────────
    private fun extractAndPlay(videoUrl: String) {
        if (extracting) return
        extracting = true
        spinnerView.visibility = View.VISIBLE
        errorView.visibility   = View.GONE
        btnDownload.visibility = View.GONE

        val done   = java.util.concurrent.atomic.AtomicBoolean(false)
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        val total  = CONVERT_APIS.size

        CONVERT_APIS.forEach { api ->
            thread {
                try {
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val conn = (java.net.URL("$api/extract?url=$encoded")
                        .openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 90_000; requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
                        val link = org.json.JSONObject(body).optString("link", "")
                        if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                            handler.post {
                                if (isDestroyed) return@post
                                extracting = false; directUrl = link
                                spinnerView.visibility = View.GONE
                                btnDownload.visibility = View.VISIBLE
                                loadIntoWebView(link)
                            }
                        }
                    } else {
                        conn.disconnect()
                        if (failed.incrementAndGet() == total && !done.get())
                            handler.post { if (!isDestroyed) showError() }
                    }
                } catch (_: Exception) {
                    if (failed.incrementAndGet() == total && !done.get())
                        handler.post { if (!isDestroyed) showError() }
                }
            }
        }
    }

    private fun showError() {
        extracting = false
        spinnerView.visibility = View.GONE
        errorView.visibility   = View.VISIBLE
    }

    // ── Relacionados ──────────────────────────────────────────────────────────
    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }.take(40)
                handler.post {
                    if (isDestroyed) return@post
                    findViewWithTag<LinearLayout>("skeleton")?.visibility = View.GONE
                    relatedList.clear(); relatedList.addAll(result)
                    relatedAdapter.notifyDataSetChanged()
                    recycler.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    // ── WebView ───────────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccessFromFileURLs = true; allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true; loadWithOverviewMode = true; setSupportZoom(false)
            userAgentString = UA
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webChromeClient = WebChromeClient()
        webViewClient   = object : WebViewClient() {}
    }

    private fun buildSpinner() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, FrameLayout.LayoutParams(dp(32), dp(32)).also { it.gravity = Gravity.CENTER })
    }

    private fun buildErrorView() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        col.addView(activity.svgImageView("icons/svg/error.svg", 36,
            Color.parseColor("#99FFFFFF")),
            LinearLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))
        col.addView(TextView(context).apply {
            text = "Não foi possível obter o vídeo."
            setTextColor(Color.parseColor("#99FFFFFF")); textSize = 12f; gravity = Gravity.CENTER
        })
        col.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
        col.addView(TextView(context).apply {
            text = "Tentar novamente"; setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 12f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#80FFFFFF"))
            }
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { extractAndPlay(video.videoUrl) }
        })
        addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
    }

    private fun buildRelatedSkeleton(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(8), dp(14))
        }
        row.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
        val infoCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(13)))
        infoCol.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))
        infoCol.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                setColor(AppTheme.thumbShimmer1)
            }
        }, LinearLayout.LayoutParams(dp(120), dp(11)))
        row.addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun destroy() { webView.stopLoading(); webView.destroy() }
    private fun dp(v: Int) = activity.dp(v)

    companion object {
        private const val FALLBACK_HTML = """<!DOCTYPE html>
<html><head><meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<style>*{margin:0;padding:0;box-sizing:border-box}
html,body{width:100%;height:100%;background:#000;overflow:hidden}
video{width:100%;height:100%;display:block;object-fit:contain}</style>
</head><body>
<video id="vid" src="{{VIDEO_URL}}" controls autoplay playsinline webkit-playsinline></video>
<script>window.setSystemVolume=function(p){var v=document.getElementById('vid');
if(v){v.volume=p/100;v.muted=(p===0);}};
</script></body></html>"""
    }
}

// ── RelatedAdapter ────────────────────────────────────────────────────────────
private class RelatedAdapter(
    private val items:     List<FeedVideo>,
    private val onTap:     (FeedVideo) -> Unit,
    private val onMenuTap: (FeedVideo, View) -> Unit,
) : RecyclerView.Adapter<RelatedAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        lateinit var thumb:    ImageView
        lateinit var title:    TextView
        lateinit var meta:     TextView
        lateinit var duration: TextView
        lateinit var menuBtn:  View
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(4), dp(14))
            isClickable = true; isFocusable = true
        }

        // Thumb + badge duração
        val thumbFrame = FrameLayout(ctx).apply {
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                setColor(AppTheme.thumbBg)
            }
        }
        val thumb = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(dp(160), dp(90)))
        val durationBadge = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#CC000000"))
            }
            setPadding(dp(4), dp(1), dp(4), dp(1)); visibility = View.GONE
        }
        thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.END
            it.bottomMargin = dp(4); it.rightMargin = dp(4)
        })
        row.addView(thumbFrame, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

        val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(ctx).apply {
            setTextColor(AppTheme.text); textSize = 13f; maxLines = 2
            setTypeface(null, Typeface.NORMAL)
        }
        infoCol.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(4)))
        val meta = TextView(ctx).apply {
            setTextColor(AppTheme.textSecondary); textSize = 11.5f; maxLines = 1
        }
        infoCol.addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(infoCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Botão menu (3 pontos)
        val menuBtn = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(4), dp(8), dp(4))
            try {
                val px  = dp(20)
                val svg = com.caverock.androidsvg.SVG.getFromAsset(ctx.assets, "icons/svg/more_vert.svg")
                svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(android.graphics.Canvas(bmp))
                setImageBitmap(bmp)
                setColorFilter(AppTheme.iconSub)
            } catch (_: Exception) {}
        }
        row.addView(menuBtn, LinearLayout.LayoutParams(dp(36), dp(90)))

        val vh = VH(row)
        vh.thumb    = thumb
        vh.title    = title
        vh.meta     = meta
        vh.duration = durationBadge
        vh.menuBtn  = menuBtn
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v   = items[position]
        val ctx = holder.root.context
        fun dp(i: Int) = (i * ctx.resources.displayMetrics.density).toInt()

        holder.title.text = v.title
        holder.title.setTextColor(AppTheme.text)
        holder.meta.text  = buildString {
            append(v.source.label)
            if (v.views.isNotEmpty()) append("  ·  ${v.views} vis.")
        }
        holder.meta.setTextColor(AppTheme.textSecondary)

        if (v.duration.isNotEmpty()) {
            holder.duration.text = v.duration; holder.duration.visibility = View.VISIBLE
        } else {
            holder.duration.visibility = View.GONE
        }

        (holder.thumb.parent as? FrameLayout)?.background?.let {
            (it as? GradientDrawable)?.setColor(AppTheme.thumbBg)
        }

        if (v.thumb.isNotEmpty()) {
            Glide.with(ctx)
                .load(GlideUrl(v.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", referer(v.source))
                    .build()))
                .override(320, 180).centerCrop().into(holder.thumb)
        }

        holder.root.setOnClickListener { onTap(v) }
        holder.menuBtn.setOnClickListener { onMenuTap(v, holder.menuBtn) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.thumb.context).clear(holder.thumb)
    }

    override fun getItemCount() = items.size
}