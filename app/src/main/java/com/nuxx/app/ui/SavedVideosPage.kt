// SavedVideosPage.kt
package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.nuxx.app.MainActivity
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("ViewConstructor")
class SavedVideosPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val density  get() = context.resources.displayMetrics.density

    private val prefs get() = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)

    init {
        setBackgroundColor(AppTheme.bg)
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        requestFocus()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = AppTheme.bg
    }

    fun handleBack() = activity.closeCurrentOverlay()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            handleBack(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun buildUI() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        root.addView(View(context).apply { setBackgroundColor(AppTheme.bg) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.statusBarHeight))

        val bar = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val btnBack = FrameLayout(context).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { handleBack() }
            isClickable = true; isFocusable = true
        }
        btnBack.addView(activity.svgImageView("icons/svg/phosphor-icons/regular/arrow-left.svg", 22, AppTheme.text),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        row.addView(btnBack, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(TextView(context).apply {
            text = "Vídeos guardados"; setTextColor(AppTheme.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(bar)
        root.addView(View(context).apply {
            setBackgroundColor(AppTheme.divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val videos = loadSavedVideos()

        if (videos.isEmpty()) {
            val empty = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(32), 0, dp(32), 0)
            }
            empty.addView(activity.svgImageView("icons/svg/phosphor-icons/regular/bookmark.svg", 48, AppTheme.textSecondary),
                LinearLayout.LayoutParams(dp(48), dp(48)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
            empty.addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
            empty.addView(TextView(context).apply {
                text = "Nenhum vídeo guardado"; setTextColor(AppTheme.textSecondary)
                textSize = 15f; gravity = Gravity.CENTER
            })
            root.addView(empty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val recycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                itemAnimator = null
                setPadding(0, dp(8), 0, dp(32))
                clipToPadding = false
            }
            recycler.adapter = SavedAdapter(videos)
            root.addView(recycler, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun loadSavedVideos(): List<FeedVideo> {
        val json = prefs.getString("saved_videos", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FeedVideo(
                    title      = obj.optString("title"),
                    thumb      = obj.optString("thumb"),
                    videoUrl   = obj.optString("videoUrl"),
                    views      = obj.optString("views"),
                    duration   = obj.optString("duration"),
                    source     = com.nuxx.app.models.VideoSource.values()
                        .firstOrNull { it.name == obj.optString("source") }
                        ?: com.nuxx.app.models.VideoSource.EPORNER,
                    tags       = emptyList(),
                    categories = emptyList(),
                    performer  = obj.optString("performer"),
                    publishedAt = obj.optString("publishedAt")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private inner class SavedAdapter(private val items: List<FeedVideo>) :
        RecyclerView.Adapter<SavedAdapter.VH>() {

        inner class VH(val root: View) : RecyclerView.ViewHolder(root)

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(buildItem())
        override fun onBindViewHolder(holder: VH, position: Int) = bind(holder.root, items[position])

        private fun buildItem(): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(10), dp(12), dp(10)); gravity = Gravity.TOP
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                    background = context.getDrawable(tv.resourceId)
            }
            val thumbFr = FrameLayout(context).apply {
                clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                    setColor(AppTheme.thumbBg)
                }
            }
            val thumbIv = android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; tag = "thumb"
            }
            val durBadge = TextView(context).apply {
                setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(4), dp(2), dp(4), dp(2)); visibility = View.GONE; tag = "dur"
            }
            thumbFr.addView(thumbIv, FrameLayout.LayoutParams(dp(130), dp(73)))
            thumbFr.addView(durBadge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(4); it.rightMargin = dp(4)
            })
            row.addView(thumbFr, LinearLayout.LayoutParams(dp(130), dp(73)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(10), 0))
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }
            val titleTv = TextView(context).apply {
                setTextColor(AppTheme.text); textSize = 13f
                setTypeface(null, Typeface.BOLD); maxLines = 2; tag = "title"
            }
            val srcTv = TextView(context).apply {
                setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1; tag = "source"
            }
            val viewsTv = TextView(context).apply {
                setTextColor(AppTheme.textSecondary); textSize = 11f; maxLines = 1; tag = "views"
            }
            col.addView(titleTv)
            col.addView(View(context), LinearLayout.LayoutParams(1, dp(4)))
            col.addView(srcTv)
            col.addView(View(context), LinearLayout.LayoutParams(1, dp(2)))
            col.addView(viewsTv)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }

        private fun bind(root: View, v: FeedVideo) {
            val row     = root as LinearLayout
            val thumbFr = row.getChildAt(0) as FrameLayout
            val thumbIv = thumbFr.findViewWithTag<android.widget.ImageView>("thumb")
            val durBadge = thumbFr.findViewWithTag<TextView>("dur")
            val col     = row.getChildAt(2) as LinearLayout
            val titleTv = col.findViewWithTag<TextView>("title")
            val srcTv   = col.findViewWithTag<TextView>("source")
            val viewsTv = col.findViewWithTag<TextView>("views")

            titleTv?.text = v.title
            srcTv?.text   = v.source.label
            if (v.views.isNotEmpty()) { viewsTv?.text = "${v.views} visualizações"; viewsTv?.visibility = View.VISIBLE }
            else viewsTv?.visibility = View.GONE
            if (v.duration.isNotEmpty()) { durBadge?.text = v.duration; durBadge?.visibility = View.VISIBLE }
            else durBadge?.visibility = View.GONE

            if (v.thumb.isNotEmpty()) {
                Glide.with(context).load(GlideUrl(v.thumb, LazyHeaders.Builder()
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://www.google.com/").build()))
                    .override(260, 146).centerCrop().into(thumbIv!!)
            }
            root.setOnClickListener { VideoPreviewModal.show(activity, v) }
        }
    }

    private fun dp(v: Int) = (v * density).toInt()
}

// Extensão para guardar/remover vídeos
object SavedVideosManager {

    fun saveVideo(context: Context, video: FeedVideo) {
        val prefs = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)
        val json  = prefs.getString("saved_videos", "[]") ?: "[]"
        val arr   = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        // Verifica se já existe
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("videoUrl") == video.videoUrl) return
        }
        val obj = JSONObject().apply {
            put("title",       video.title)
            put("thumb",       video.thumb)
            put("videoUrl",    video.videoUrl)
            put("views",       video.views)
            put("duration",    video.duration)
            put("source",      video.source.name)
            put("performer",   video.performer)
            put("publishedAt", video.publishedAt)
        }
        arr.put(obj)
        prefs.edit().putString("saved_videos", arr.toString()).apply()
    }

    fun removeVideo(context: Context, videoUrl: String) {
        val prefs   = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)
        val json    = prefs.getString("saved_videos", "[]") ?: "[]"
        val arr     = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val newArr  = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("videoUrl") != videoUrl) newArr.put(obj)
        }
        prefs.edit().putString("saved_videos", newArr.toString()).apply()
    }

    fun isVideoSaved(context: Context, videoUrl: String): Boolean {
        val prefs = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)
        val json  = prefs.getString("saved_videos", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).any { arr.getJSONObject(it).optString("videoUrl") == videoUrl }
        } catch (_: Exception) { false }
    }
}