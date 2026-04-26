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
import android.webkit.*
import android.widget.*
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
    VideoSource.BEEG           -> "https://beeg.com/"
    VideoSource.TUBE8          -> "https://www.tube8.com/"
    VideoSource.TNAFLIX        -> "https://www.tnaflix.com/"
    VideoSource.EMPFLIX        -> "https://www.empflix.com/"
    VideoSource.PORNTREX       -> "https://www.porntrex.com/"
    VideoSource.HCLIPS         -> "https://hclips.com/"
    VideoSource.TUBEDUPE       -> "https://www.tubedupe.com/"
    VideoSource.NUVID          -> "https://www.nuvid.com/"
    VideoSource.SUNPORNO       -> "https://www.sunporno.com/"
    VideoSource.PORNONE        -> "https://pornone.com/"
    VideoSource.SLUTLOAD       -> "https://www.slutload.com/"
    VideoSource.ICEPORN        -> "https://www.iceporn.com/"
    VideoSource.VJAV           -> "https://vjav.com/"
    VideoSource.JIZZBUNKER     -> "https://jizzbunker.com/"
    VideoSource.CLIPHUNTER     -> "https://www.cliphunter.com/"
    VideoSource.SEXVID         -> "https://sexvid.xxx/"
    VideoSource.YEPTUBE        -> "https://www.yeptube.com/"
    VideoSource.XNXX           -> "https://www.xnxx.com/"
    VideoSource.PORNOXO        -> "https://www.pornoxo.com/"
    VideoSource.ANYSEX         -> "https://anysex.com/"
    VideoSource.FUQER          -> "https://fuqer.com/"
    VideoSource.FAPSTER        -> "https://fapster.xxx/"
    VideoSource.PROPORN        -> "https://proporn.com/"
    VideoSource.H2PORN         -> "https://www.h2porn.com/"
    VideoSource.ALPHAPORNO     -> "https://www.alphaporno.com/"
    VideoSource.WATCHMYGF      -> "https://watchmygf.me/"
    VideoSource.XCAFE          -> "https://xcafe.com/"
    VideoSource.TUBECUP        -> "https://tubecup.com/"
    VideoSource.VIDLOX         -> "https://vidlox.me/"
    VideoSource.NAUGHTYAMERICA -> "https://www.naughtyamerica.com/"
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

    // Referências para redesenho de tema
    private lateinit var rootScrollBg:  View
    private lateinit var infoBox:       LinearLayout
    private lateinit var titleTv:       TextView
    private lateinit var metaTv:        TextView
    private lateinit var relatedLabel:  TextView
    private lateinit var dividerLine:   View

    private val relatedList      = mutableListOf<FeedVideo>()
    private lateinit var relatedAdapter: RelatedAdapter

    private var playerHtml:   String? = null
    private var directUrl:    String? = null
    private var extracting    = false
    private var extractFailed = false

    private val themeListener: () -> Unit = { applyTheme() }

    init {
        setBackgroundColor(AppTheme.bg)
        buildUI()
        loadPlayerTemplate()
        extractAndPlay(video.videoUrl)
        loadRelated()
        AppTheme.addThemeListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppTheme.removeThemeListener(themeListener)
    }

    // ── Aplicar tema em runtime ────────────────────────────────────────────────

    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        infoBox.setBackgroundColor(AppTheme.bg)
        titleTv.setTextColor(AppTheme.text)
        metaTv.setTextColor(AppTheme.textSecondary)
        relatedLabel.setTextColor(AppTheme.text)
        dividerLine.setBackgroundColor(AppTheme.divider)
        relatedAdapter.notifyDataSetChanged()
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        val screenW = context.resources.displayMetrics.widthPixels
        val playerH = (screenW * 9f / 16f).toInt()

        val rootScroll = ScrollView(context).apply { isFillViewport = true }
        val rootCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // ── Player ──────────────────────────────────────────────────────────────
        val playerContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        webView = buildWebView()
        playerContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        spinnerView = buildSpinner()
        playerContainer.addView(spinnerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        errorView = buildErrorView()
        errorView.visibility = View.GONE
        playerContainer.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        // ── Botão voltar ─────────────────────────────────────────────────────
        val btnBack = FrameLayout(context).apply {
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.OVAL
            gd.setColor(Color.parseColor("#B3000000"))
            background = gd
            setOnClickListener { activity.closeVideoPlayer() }
        }
        val backIcon = activity.svgImageView(
            "icons/svg/settings/settings_back.svg", 22, Color.WHITE)
        btnBack.addView(backIcon, FrameLayout.LayoutParams(dp(22), dp(22)).also {
            it.gravity = Gravity.CENTER
        })
        playerContainer.addView(btnBack, FrameLayout.LayoutParams(dp(40), dp(40)).also {
            it.gravity    = Gravity.TOP or Gravity.START
            it.topMargin  = dp(8)
            it.leftMargin = dp(8)
        })

        // ── Botão download ───────────────────────────────────────────────────
        val btnDownload = FrameLayout(context).apply {
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.OVAL
            gd.setColor(Color.parseColor("#B3000000"))
            background = gd
            visibility = View.GONE
            setOnClickListener { /* DownloadService */ }
        }
        val downloadIcon = activity.svgImageView(
            "icons/svg/download.svg", 17, Color.WHITE)
        btnDownload.addView(downloadIcon, FrameLayout.LayoutParams(dp(17), dp(17)).also {
            it.gravity = Gravity.CENTER
        })
        btnDownload.tag = "btn_download"
        playerContainer.addView(btnDownload, FrameLayout.LayoutParams(dp(36), dp(36)).also {
            it.gravity     = Gravity.TOP or Gravity.END
            it.topMargin   = dp(8)
            it.rightMargin = dp(8)
        })

        rootCol.addView(playerContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, playerH))

        // ── Descrição ─────────────────────────────────────────────────────────
        infoBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(4))
            setBackgroundColor(AppTheme.bg)
        }

        titleTv = TextView(context).apply {
            setTextColor(AppTheme.text)
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 3
            text = video.title
        }
        infoBox.addView(titleTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(6)))

        metaTv = TextView(context).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 11.5f
            text = buildString {
                append(video.source.label)
                if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
                if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
            }
        }
        infoBox.addView(metaTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        infoBox.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))

        dividerLine = View(context).apply { setBackgroundColor(AppTheme.divider) }
        infoBox.addView(dividerLine, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1))

        rootCol.addView(infoBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── Label Relacionados ────────────────────────────────────────────────
        relatedLabel = TextView(context).apply {
            text = "Relacionados"
            setTextColor(AppTheme.text)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        rootCol.addView(relatedLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── Skeleton ──────────────────────────────────────────────────────────
        val skeletonBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "skeleton"
        }
        repeat(5) { skeletonBox.addView(buildRelatedSkeleton()) }
        rootCol.addView(skeletonBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── RecyclerView relacionados ─────────────────────────────────────────
        relatedAdapter = RelatedAdapter(relatedList) { v -> onVideoTap(v) }
        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
            adapter = relatedAdapter
            visibility = View.GONE
        }
        rootCol.addView(recycler, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        rootCol.addView(View(context), LinearLayout.LayoutParams(1, dp(32)))

        rootScroll.addView(rootCol, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(rootScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // ── Template player.html ──────────────────────────────────────────────────

    private fun loadPlayerTemplate() {
        thread {
            try {
                val html = context.assets.open("player.html")
                    .bufferedReader().use { it.readText() }
                handler.post {
                    playerHtml = html
                    directUrl?.let { loadIntoWebView(it) }
                }
            } catch (_: Exception) {
                handler.post {
                    playerHtml = FALLBACK_HTML
                    directUrl?.let { loadIntoWebView(it) }
                }
            }
        }
    }

    private fun buildHtml(url: String): String {
        val escaped = url.replace("\"", "&quot;")
        return (playerHtml ?: FALLBACK_HTML).replace("{{VIDEO_URL}}", escaped)
    }

    private fun loadIntoWebView(url: String) {
        if (playerHtml == null) return
        webView.loadDataWithBaseURL(
            "https://nuxxx.app",
            buildHtml(url),
            "text/html", "UTF-8", null)
    }

    // ── Extracção ─────────────────────────────────────────────────────────────

    private fun extractAndPlay(videoUrl: String) {
        if (extracting) return
        extracting    = true
        extractFailed = false
        spinnerView.visibility = View.VISIBLE
        errorView.visibility   = View.GONE
        findViewWithTag<View>("btn_download")?.visibility = View.GONE

        val done   = java.util.concurrent.atomic.AtomicBoolean(false)
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        val total  = CONVERT_APIS.size

        CONVERT_APIS.forEach { api ->
            thread {
                try {
                    val encoded = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val conn = (java.net.URL("$api/extract?url=$encoded")
                        .openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout    = 90_000
                        requestMethod  = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        val link = org.json.JSONObject(body).optString("link", "")
                        if (link.isNotEmpty() && done.compareAndSet(false, true)) {
                            handler.post {
                                extracting = false
                                directUrl  = link
                                spinnerView.visibility = View.GONE
                                findViewWithTag<View>("btn_download")?.visibility = View.VISIBLE
                                loadIntoWebView(link)
                            }
                        }
                    } else {
                        conn.disconnect()
                        if (failed.incrementAndGet() == total && !done.get()) {
                            handler.post { showError() }
                        }
                    }
                } catch (_: Exception) {
                    if (failed.incrementAndGet() == total && !done.get()) {
                        handler.post { showError() }
                    }
                }
            }
        }
    }

    private fun showError() {
        extracting    = false
        extractFailed = true
        spinnerView.visibility = View.GONE
        errorView.visibility   = View.VISIBLE
    }

    // ── Related ───────────────────────────────────────────────────────────────

    private fun loadRelated() {
        thread {
            try {
                val result = FeedFetcher.fetchAll(Random.nextInt(1, 30))
                    .filter { it.videoUrl != video.videoUrl }
                    .take(20)
                handler.post {
                    val skeleton = (parent as? ViewGroup)
                        ?.findViewWithTag<LinearLayout>("skeleton")
                    skeleton?.visibility = View.GONE
                    relatedList.clear()
                    relatedList.addAll(result)
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
            javaScriptEnabled                = true
            domStorageEnabled                = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccessFromFileURLs      = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort                  = true
            loadWithOverviewMode             = true
            setSupportZoom(false)
            userAgentString = UA
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webChromeClient = WebChromeClient()
        webViewClient   = object : WebViewClient() {}
    }

    // ── Spinner ───────────────────────────────────────────────────────────────

    private fun buildSpinner() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList =
                android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, FrameLayout.LayoutParams(dp(32), dp(32)).also {
            it.gravity = Gravity.CENTER
        })
    }

    // ── Erro ──────────────────────────────────────────────────────────────────

    private fun buildErrorView() = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
        }

        // error.svg — único ícone de erro disponível nos assets
        val errIcon = activity.svgImageView(
            "icons/svg/error.svg", 36, Color.parseColor("#99FFFFFF"))
        col.addView(errIcon, LinearLayout.LayoutParams(dp(36), dp(36)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
        })

        col.addView(View(context), LinearLayout.LayoutParams(1, dp(10)))

        col.addView(TextView(context).apply {
            text = "Não foi possível obter o vídeo."
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 12f
            gravity  = Gravity.CENTER
        })

        col.addView(View(context), LinearLayout.LayoutParams(1, dp(12)))

        col.addView(TextView(context).apply {
            text = "Tentar novamente"
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 12f
            gravity  = Gravity.CENTER
            val gd = GradientDrawable()
            gd.shape        = GradientDrawable.RECTANGLE
            gd.cornerRadius = dp(8).toFloat()
            gd.setStroke(dp(1), Color.parseColor("#80FFFFFF"))
            background = gd
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { extractAndPlay(video.videoUrl) }
        })

        addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private fun buildRelatedSkeleton(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(8), dp(14))
        }
        val thumb = View(context).apply {
            background = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(6).toFloat()
                it.setColor(AppTheme.thumbShimmer1)
            }
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))

        val infoCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val line1 = View(context).apply {
            background = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(4).toFloat()
                it.setColor(AppTheme.thumbShimmer1)
            }
        }
        infoCol.addView(line1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(13)))
        infoCol.addView(View(context), LinearLayout.LayoutParams(1, dp(5)))

        val line2 = View(context).apply {
            background = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(4).toFloat()
                it.setColor(AppTheme.thumbShimmer1)
            }
        }
        infoCol.addView(line2, LinearLayout.LayoutParams(dp(120), dp(11)))

        row.addView(infoCol, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

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
<script>
window.setSystemVolume=function(p){
  var v=document.getElementById('vid');
  if(v){v.volume=p/100;v.muted=(p===0);}
};
</script>
</body></html>"""
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RelatedAdapter
// ─────────────────────────────────────────────────────────────────────────────
private class RelatedAdapter(
    private val items: List<FeedVideo>,
    private val onTap: (FeedVideo) -> Unit
) : RecyclerView.Adapter<RelatedAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        lateinit var thumb: android.widget.ImageView
        lateinit var title: TextView
        lateinit var meta:  TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(8), dp(14))
            isClickable = true
            isFocusable = true
        }

        val thumb = android.widget.ImageView(ctx).apply {
            scaleType     = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background    = GradientDrawable().also {
                it.shape        = GradientDrawable.RECTANGLE
                it.cornerRadius = dp(6).toFloat()
                it.setColor(AppTheme.thumbBg)
            }
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(160), dp(90)))
        row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 0))

        val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val title = TextView(ctx).apply {
            setTextColor(AppTheme.text)
            textSize = 13f
            maxLines = 2
        }
        infoCol.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        infoCol.addView(View(ctx), LinearLayout.LayoutParams(1, dp(4)))

        val meta = TextView(ctx).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 11.5f
            maxLines = 1
        }
        infoCol.addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        row.addView(infoCol, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val vh = VH(row)
        vh.thumb = thumb
        vh.title = title
        vh.meta  = meta
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v   = items[position]
        val ctx = holder.root.context
        holder.title.text = v.title
        holder.meta.text  = buildString {
            append(v.source.label)
            if (v.views.isNotEmpty()) append("  ·  ${v.views} vis.")
        }
        // Redesenha cores ao fazer bind (respeita tema actual)
        holder.title.setTextColor(AppTheme.text)
        holder.meta.setTextColor(AppTheme.textSecondary)
        (holder.thumb.background as? GradientDrawable)?.setColor(AppTheme.thumbBg)

        if (v.thumb.isNotEmpty()) {
            Glide.with(ctx)
                .load(GlideUrl(v.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", referer(v.source))
                    .build()))
                .override(320, 180)
                .centerCrop()
                .into(holder.thumb)
        }
        holder.root.setOnClickListener { onTap(v) }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.thumb.context).clear(holder.thumb)
    }

    override fun getItemCount() = items.size
}