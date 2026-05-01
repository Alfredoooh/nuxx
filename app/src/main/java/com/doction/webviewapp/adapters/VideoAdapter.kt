package com.doction.webviewapp.adapters

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.doction.webviewapp.models.FeedVideo
import com.doction.webviewapp.models.VideoSource
import com.doction.webviewapp.theme.AppTheme

private val RATIOS = listOf(
    16f/9, 4f/3, 16f/9, 16f/9, 4f/3,
    16f/9, 16f/9, 4f/3, 16f/9, 16f/9
)

private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private val crossFade = DrawableCrossFadeFactory.Builder(180)
    .setCrossFadeEnabled(true).build()

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

class VideoAdapter(
    private val videos: MutableList<FeedVideo>,
    private val onTap: (FeedVideo, View) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val thumb: android.widget.ImageView = root.getChildAt(0) as android.widget.ImageView
        val title: TextView                 = root.getChildAt(1) as TextView
        val meta:  TextView                 = root.getChildAt(2) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
            isClickable = true
            isFocusable = true
        }

        val thumb = android.widget.ImageView(ctx).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(AppTheme.thumbBg)
            }
            clipToOutline = true
        }
        col.addView(thumb, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))

        val title = TextView(ctx).apply {
            setTextColor(AppTheme.text)
            textSize = 12f
            maxLines = 2
            setPadding(0, dp(6), 0, 0)
        }
        col.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        val meta = TextView(ctx).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 10.5f
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        return VH(col)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = videos[position]
        val ctx   = holder.root.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        holder.title.text = video.title
        holder.meta.text  = buildString {
            append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views} vis.")
        }

        val ratio = RATIOS[position % RATIOS.size]
        val colW  = ctx.resources.displayMetrics.widthPixels / 2 - dp(18)
        val h     = (colW / ratio).toInt()
        holder.thumb.layoutParams = (holder.thumb.layoutParams as LinearLayout.LayoutParams).also {
            it.height = h
        }

        holder.thumb.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(AppTheme.thumbBg)
        }
        holder.thumb.clipToOutline = true
        holder.title.setTextColor(AppTheme.text)
        holder.meta.setTextColor(AppTheme.textSecondary)

        if (video.thumb.isNotEmpty()) {
            Glide.with(ctx)
                .load(GlideUrl(video.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", referer(video.source))
                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()))
                .override(480)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(crossFade))
                .into(holder.thumb)
        } else {
            holder.thumb.setImageDrawable(null)
        }

        holder.root.setOnClickListener {
            holder.root.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(70)
                .withEndAction {
                    holder.root.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(110)
                        .withEndAction { onTap(video, holder.thumb) }
                        .start()
                }.start()
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        Glide.with(holder.thumb.context).clear(holder.thumb)
    }

    override fun getItemCount() = videos.size
}