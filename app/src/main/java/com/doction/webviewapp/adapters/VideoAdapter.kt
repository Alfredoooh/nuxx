package com.doction.webviewapp.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.doction.webviewapp.models.FeedVideo

class VideoAdapter(
    private val videos: List<FeedVideo>,
    private val onTap: (FeedVideo) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    private val ratios = listOf(16f/9, 4f/3, 16f/9, 16f/9, 4f/3,
        16f/9, 16f/9, 4f/3, 16f/9, 16f/9)

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val thumb: ImageView = root.getChildAt(0) as ImageView
        val title: TextView  = root.getChildAt(1) as TextView
        val meta: TextView   = root.getChildAt(2) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val thumb = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        col.addView(thumb, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(120)))

        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            setPadding(0, dp(5), 0, 0)
        }
        col.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        val meta = TextView(ctx).apply {
            setTextColor(Color.parseColor("#888888"))
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
        holder.title.text = video.title
        holder.meta.text = video.source
        // TODO: Glide.with(holder.thumb).load(video.thumb).into(holder.thumb)
        holder.root.setOnClickListener { onTap(video) }

        // Ajusta altura do thumb pela ratio
        val ratio = ratios[position % ratios.size]
        val density = holder.root.context.resources.displayMetrics.density
        val width = holder.root.context.resources.displayMetrics.widthPixels / 2 -
                (18 * density).toInt()
        val height = (width / ratio).toInt()
        val lp = holder.thumb.layoutParams
        lp.height = height
        holder.thumb.layoutParams = lp
    }

    override fun getItemCount() = videos.size
}