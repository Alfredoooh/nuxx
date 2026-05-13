// HistoryPage.kt
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
import com.nuxx.app.models.VideoSource
import com.nuxx.app.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("ViewConstructor")
class HistoryPage(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val density  get() = context.resources.displayMetrics.density
    private val prefs get() = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)

    init {
        setBackgroundColor(AppTheme.bg)
        buildUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true; requestFocus()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = AppTheme.bg
    }

    fun handleBack() = activity.removeContentOverlay(this)

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
            text = "Histórico"; setTextColor(AppTheme.text)
            textSize = 20f; setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(bar)
        root.addView(View(context).apply {
            setBackgroundColor(AppTheme.divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val grouped = loadGroupedHistory()

        if (grouped.isEmpty()) {
            val empty = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(32), 0, dp(32), 0)
            }
            empty.addView(activity.svgImageView("icons/svg/phosphor-icons/regular/clock-counter-clockwise.svg", 48, AppTheme.textSecondary),
                LinearLayout.LayoutParams(dp(48), dp(48)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
            empty.addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
            empty.addView(TextView(context).apply {
                text = "Nenhum vídeo assistido"; setTextColor(AppTheme.textSecondary)
                textSize = 15f; gravity = Gravity.CENTER
            })
            root.addView(empty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val scroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            }
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(32))
            }
            grouped.forEach { (date, videos) ->
                col.addView(TextView(context).apply {
                    text = date; setTextColor(AppTheme.textSecondary)
                    textSize = 11f; setTypeface(null, Typeface.BOLD); letterSpacing = 0.06f
                    setPadding(dp(16), dp(16), dp(16), dp(8))
                })
                val hscroll = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setPadding(dp(12), 0, dp(12), 0)
                }
                val hrow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
                }
                videos.forEach { v ->
                    val card = buildHistoryCard(v)
                    hrow.addView(card, LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).also {
                        it.rightMargin = dp(10)
                    })
                }
                hscroll.addView(hrow)
                col.addView(hscroll)
                col.addView(View(context).apply {
                    setBackgroundColor(AppTheme.dividerSoft)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
                        it.topMargin = dp(12)
                    }
                })
            }
            scroll.addView(col, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun buildHistoryCard(v: FeedVideo): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
            setOnClickListener { VideoPreviewModal.show(activity, v) }
        }
        val thumbFr = FrameLayout(context).apply {
            clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(AppTheme.thumbBg)
            }
        }
        val thumbIv = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        val durBadge = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 9f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#CC000000"))
            }
            setPadding(dp(3), dp(1), dp(3), dp(1))
            visibility = if (v.duration.isNotEmpty()) View.VISIBLE else View.GONE
            text = v.duration
        }
        thumbFr.addView(thumbIv, FrameLayout.LayoutParams(dp(150), dp(84)))
        thumbFr.addView(durBadge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(3); it.rightMargin = dp(3)
        })
        card.addView(thumbFr)
        card.addView(TextView(context).apply {
            text = v.title; setTextColor(AppTheme.text)
            textSize = 11.5f; setTypeface(null, Typeface.BOLD); maxLines = 2
            setPadding(0, dp(5), 0, 0)
        })
        card.addView(TextView(context).apply {
            text = v.source.label; setTextColor(AppTheme.textSecondary)
            textSize = 10f; setPadding(0, dp(2), 0, 0)
        })
        if (v.thumb.isNotEmpty()) {
            Glide.with(context).load(GlideUrl(v.thumb, LazyHeaders.Builder()
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.google.com/").build()))
                .override(300, 168).centerCrop().into(thumbIv)
        }
        return card
    }

    private fun loadGroupedHistory(): Map<String, List<FeedVideo>> {
        val json = prefs.getString("watch_history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.optString("watchedAt", ""), FeedVideo(
                    title       = obj.optString("title"),
                    thumb       = obj.optString("thumb"),
                    videoUrl    = obj.optString("videoUrl"),
                    views       = obj.optString("views"),
                    duration    = obj.optString("duration"),
                    source      = VideoSource.values().firstOrNull { it.name == obj.optString("source") }
                        ?: VideoSource.EPORNER,
                    tags        = emptyList(),
                    categories  = emptyList(),
                    performer   = obj.optString("performer"),
                    publishedAt = obj.optLong("publishedAt", 0L)
                ))
            }
            val sdf    = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val today  = sdf.format(Date())
            val ystday = sdf.format(Date(System.currentTimeMillis() - 86400000))
            list.groupBy { (date, _) ->
                when (date.take(10)) {
                    today  -> "Hoje"
                    ystday -> "Ontem"
                    else   -> date.take(10)
                }
            }.mapValues { entry -> entry.value.map { it.second } }
        } catch (_: Exception) { emptyMap() }
    }

    private fun dp(v: Int) = (v * density).toInt()
}

object HistoryManager {
    fun addToHistory(context: Context, video: FeedVideo) {
        val prefs  = context.getSharedPreferences("nuxx_prefs", Context.MODE_PRIVATE)
        val json   = prefs.getString("watch_history", "[]") ?: "[]"
        val arr    = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val newArr = JSONArray()
        val sdf    = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val obj    = JSONObject().apply {
            put("title",       video.title)
            put("thumb",       video.thumb)
            put("videoUrl",    video.videoUrl)
            put("views",       video.views)
            put("duration",    video.duration)
            put("source",      video.source.name)
            put("performer",   video.performer)
            put("publishedAt", video.publishedAt)
            put("watchedAt",   sdf.format(Date()))
        }
        newArr.put(obj)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("videoUrl") != video.videoUrl) newArr.put(o)
        }
        val limited = JSONArray()
        for (i in 0 until minOf(newArr.length(), 200)) limited.put(newArr.get(i))
        prefs.edit().putString("watch_history", limited.toString()).apply()
    }
}