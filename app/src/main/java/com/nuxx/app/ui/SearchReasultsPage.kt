// SearchResultsPage.kt
package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity
import com.nuxx.app.models.FeedFetcher
import com.nuxx.app.models.FeedVideo
import com.nuxx.app.theme.AppTheme
import kotlin.concurrent.thread

@SuppressLint("ViewConstructor")
class SearchResultsPage(
    context: Context,
    private val initialQuery: String = ""
) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val history     = mutableListOf<String>()
    private val suggestions = mutableListOf<String>()
    private val results     = mutableListOf<FeedVideo>()
    private var isDestroyed = false
    private var isEditing   = false

    private val statusH get() = activity.statusBarHeight
    private val appBarH get() = statusH + dp(48)

    private lateinit var appBarBg:          View
    private lateinit var searchField:       EditText
    private lateinit var clearBtn:          ImageView
    private lateinit var appBarContainer:   FrameLayout
    private lateinit var bodyFrame:         FrameLayout
    private lateinit var suggestionsScroll: NestedScrollView
    private lateinit var suggestionsCol:    LinearLayout
    private lateinit var resultsScroll:     NestedScrollView
    private lateinit var resultsGrid:       LinearLayout
    private lateinit var emptyState:        LinearLayout
    private lateinit var loadingView:       LinearLayout

    init {
        setBackgroundColor(Color.WHITE)
        loadHistory()
        buildUI()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = Color.WHITE
        if (initialQuery.isNotEmpty()) handler.post { doSearch(initialQuery) }
        else handler.post { showEditing() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isDestroyed = true
    }

    fun onBackPressed(): Boolean { dismiss(); return true }

    private fun loadHistory() {
        val size = prefs.getInt("${SEARCH_PREF}_size", 0)
        history.clear()
        history.addAll((0 until size).map { prefs.getString("${SEARCH_PREF}_$it", "") ?: "" })
    }

    private fun saveHistory(q: String) {
        if (q.isEmpty()) return
        history.remove(q); history.add(0, q)
        if (history.size > 20) history.removeAt(history.lastIndex)
        prefs.edit().apply {
            putInt("${SEARCH_PREF}_size", history.size)
            history.forEachIndexed { i, s -> putString("${SEARCH_PREF}_$i", s) }
        }.apply()
    }

    private fun removeHistory(q: String) {
        history.remove(q)
        prefs.edit().apply {
            putInt("${SEARCH_PREF}_size", history.size)
            history.forEachIndexed { i, s -> putString("${SEARCH_PREF}_$i", s) }
        }.apply()
        if (isEditing) rebuildSuggestions()
    }

    private fun clearHistory() {
        history.clear()
        prefs.edit().remove("${SEARCH_PREF}_size").apply()
        if (isEditing) rebuildSuggestions()
    }

    private fun buildUI() {
        buildAppBar()
        bodyFrame = FrameLayout(context).apply { setBackgroundColor(Color.WHITE) }
        addView(bodyFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = appBarH })
        buildResultsPane()
        buildSuggestionsView()
        buildLoadingView()
        buildEmptyState()
    }

    private fun buildAppBar() {
        appBarContainer = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(Color.WHITE) }
        appBarContainer.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, statusH))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(8), dp(6))
        }
        val backBtn = svgView("icons/svg/back_arrow.svg", 20, AppTheme.icon).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { dismiss() }
        }
        row.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(36)))
        row.addView(View(context), LinearLayout.LayoutParams(dp(2), 0))
        val searchBar = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#E8E8E8"))
            }
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        inner.addView(svgView("icons/svg/search.svg", 16, Color.argb(100, 0, 0, 0)),
            LinearLayout.LayoutParams(dp(16), dp(16)))
        inner.addView(View(context), LinearLayout.LayoutParams(dp(6), 0))
        searchField = EditText(context).apply {
            setText(initialQuery); setTextColor(Color.parseColor("#1A1A1A"))
            setHintTextColor(Color.argb(100, 0, 0, 0)); hint = "Pesquisar..."
            textSize = 15f; background = null; maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType  = android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(text.toString().trim()); true } else false
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    val q = s?.toString()?.trim() ?: ""
                    clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                    if (!isEditing) { isEditing = true; showEditing() }
                    if (q.length >= 2) fetchSuggestions(q)
                    else { suggestions.clear(); rebuildSuggestions() }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus && !isEditing) { isEditing = true; showEditing() } }
        }
        inner.addView(searchField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        clearBtn = svgView("icons/svg/close.svg", 14, Color.argb(150, 0, 0, 0)).apply {
            visibility = if (initialQuery.isNotEmpty()) View.VISIBLE else View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { searchField.setText(""); suggestions.clear(); rebuildSuggestions() }
        }
        inner.addView(clearBtn, LinearLayout.LayoutParams(dp(28), dp(28)))
        searchBar.addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(36)))
        row.addView(searchBar, LinearLayout.LayoutParams(0, dp(36), 1f))
        col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        appBarContainer.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(appBarContainer, LayoutParams(LayoutParams.MATCH_PARENT, appBarH).also { it.gravity = Gravity.TOP })
    }

    private fun buildResultsPane() {
        resultsScroll = NestedScrollView(context).apply { isFillViewport = true; visibility = View.GONE; setBackgroundColor(Color.WHITE) }
        resultsGrid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(80)) }
        resultsScroll.addView(resultsGrid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(resultsScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildSuggestionsView() {
        suggestionsScroll = NestedScrollView(context).apply { isFillViewport = true; visibility = View.GONE; setBackgroundColor(Color.WHITE) }
        suggestionsCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        suggestionsScroll.addView(suggestionsCol, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(suggestionsScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildLoadingView() {
        loadingView = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE; setBackgroundColor(Color.WHITE) }
        val pb = ProgressBar(context, null, android.R.attr.progressBarStyle).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(AppTheme.ytRed)
        }
        loadingView.addView(pb, LinearLayout.LayoutParams(dp(40), dp(40)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        loadingView.addView(View(context), LinearLayout.LayoutParams(0, dp(12)))
        loadingView.addView(TextView(context).apply {
            text = "A pesquisar..."; setTextColor(Color.argb(100, 0, 0, 0)); textSize = 14f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        bodyFrame.addView(loadingView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildEmptyState() {
        emptyState = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE; setBackgroundColor(Color.WHITE) }
        emptyState.addView(svgView("icons/svg/search.svg", 48, Color.argb(64, 0, 0, 0)),
            LinearLayout.LayoutParams(dp(48), dp(48)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        emptyState.addView(View(context), LinearLayout.LayoutParams(0, dp(14)))
        emptyState.addView(TextView(context).apply {
            text = "Sem resultados"; setTextColor(Color.argb(100, 0, 0, 0)); textSize = 14f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        bodyFrame.addView(emptyState, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun showEditing() {
        isEditing = true
        resultsScroll.visibility = View.GONE; loadingView.visibility = View.GONE
        emptyState.visibility = View.GONE; suggestionsScroll.visibility = View.VISIBLE
        rebuildSuggestions()
    }

    private fun showLoading() {
        suggestionsScroll.visibility = View.GONE; emptyState.visibility = View.GONE
        resultsScroll.visibility = View.GONE; loadingView.visibility = View.VISIBLE
    }

    private fun showResults() {
        suggestionsScroll.visibility = View.GONE; emptyState.visibility = View.GONE
        loadingView.visibility = View.GONE; resultsScroll.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        suggestionsScroll.visibility = View.GONE; loadingView.visibility = View.GONE
        resultsScroll.visibility = View.GONE; emptyState.visibility = View.VISIBLE
    }

    // ── Pesquisa via FeedFetcher.fetchSearch ──────────────────────────────────
    private fun doSearch(q: String) {
        if (q.isEmpty()) return
        searchField.setText(q); searchField.clearFocus(); hideKeyboard()
        isEditing = false; saveHistory(q); showLoading()
        thread {
            try {
                val found = FeedFetcher.fetchSearch(q)
                handler.post {
                    if (isDestroyed) return@post
                    results.clear(); results.addAll(found)
                    if (results.isEmpty()) showEmpty()
                    else { buildResultsGrid(); showResults() }
                }
            } catch (_: Exception) {
                handler.post { if (!isDestroyed) showEmpty() }
            }
        }
    }

    private fun buildResultsGrid() {
        resultsGrid.removeAllViews()
        val screenW = resources.displayMetrics.widthPixels
        val pad = dp(8); val gap = dp(6)
        val colW = (screenW - pad * 2 - gap) / 2
        val thumbH = (colW / 1.77f).toInt()
        for (index in results.indices step 2) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(index, index + 1).forEach { i ->
                if (i >= results.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(colW, thumbH + dp(52))); return@forEach
                }
                val card = buildVideoCard(results[i], colW, thumbH)
                val lp   = LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT)
                if (i % 2 != 0) lp.leftMargin = gap
                row.addView(card, lp)
            }
            val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (index > 0) rowLp.topMargin = gap
            resultsGrid.addView(row, rowLp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildVideoCard(video: FeedVideo, colW: Int, thumbH: Int): View {
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val thumbFrame = FrameLayout(context).apply {
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#EBEBEB"))
            }
        }
        val thumb = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        if (video.thumb.isNotEmpty()) Glide.with(context).load(video.thumb).override(colW, thumbH).centerCrop().into(thumb)
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, thumbH))
        if (video.duration.isNotEmpty()) {
            thumbFrame.addView(TextView(context).apply {
                text = video.duration; setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(Color.parseColor("#CC000000")) }
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
                it.gravity = Gravity.BOTTOM or Gravity.END; it.bottomMargin = dp(4); it.rightMargin = dp(4)
            })
        }
        card.addView(thumbFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, thumbH))
        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(6), dp(2), dp(4)) }
        info.addView(TextView(context).apply {
            text = video.title; setTextColor(Color.parseColor("#1A1A1A")); textSize = 12f
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setTypeface(null, Typeface.BOLD)
        })
        val meta = buildString {
            if (video.source.label.isNotEmpty()) append(video.source.label)
            if (video.views.isNotEmpty()) append(" · ${video.views}")
        }
        if (meta.isNotEmpty()) {
            info.addView(TextView(context).apply {
                text = meta; setTextColor(Color.argb(130, 0, 0, 0)); textSize = 10f; maxLines = 1
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) })
        }
        card.addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // Abre o player correcto — não BrowserPage
        card.setOnClickListener { activity.openVideoPlayer(video) }
        card.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN   -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            false
        }
        return card
    }

    private fun fetchSuggestions(q: String) {
        thread {
            try {
                val url  = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${android.net.Uri.encode(q)}"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                val body  = conn.inputStream.bufferedReader().readText()
                val list  = mutableListOf<String>()
                val regex = Regex(""""([^"]+)"""")
                var count = 0
                for (m in regex.findAll(body)) { if (count++ == 0) continue; list.add(m.groupValues[1]); if (list.size >= 7) break }
                handler.post { if (!isDestroyed) { suggestions.clear(); suggestions.addAll(list); if (isEditing) rebuildSuggestions() } }
            } catch (_: Exception) {}
        }
    }

    private fun rebuildSuggestions() {
        suggestionsCol.removeAllViews()
        val q        = searchField.text.toString().trim()
        val showSugg = q.length >= 2 && suggestions.isNotEmpty()
        val items    = if (showSugg) suggestions else history
        if (!showSugg && history.isNotEmpty()) {
            val hRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
            hRow.addView(TextView(context).apply {
                text = "Pesquisas recentes"; setTextColor(Color.argb(115, 0, 0, 0)); textSize = 12f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            hRow.addView(TextView(context).apply {
                text = "Limpar tudo"; setTextColor(AppTheme.ytRed); textSize = 12f; setTypeface(null, Typeface.BOLD)
                setOnClickListener { clearHistory() }
            })
            suggestionsCol.addView(hRow)
        }
        if (items.isEmpty()) return
        val total = items.size
        items.forEachIndexed { i, label ->
            val isOnly = total == 1; val isFirst = i == 0; val isLast = i == total - 1
            val bigR = dp(12).toFloat(); val smallR = dp(6).toFloat()
            val radii = when {
                isOnly  -> floatArrayOf(bigR, bigR, bigR, bigR, bigR, bigR, bigR, bigR)
                isFirst -> floatArrayOf(bigR, bigR, bigR, bigR, smallR, smallR, smallR, smallR)
                isLast  -> floatArrayOf(smallR, smallR, smallR, smallR, bigR, bigR, bigR, bigR)
                else    -> floatArrayOf(smallR, smallR, smallR, smallR, smallR, smallR, smallR, smallR)
            }
            val item = FrameLayout(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadii = radii; setColor(Color.parseColor("#F0F0F0")) }
                setOnClickListener { doSearch(label) }
                alpha = 0f; translationY = dp(10).toFloat()
                animate().alpha(1f).translationY(0f).setDuration(280).setStartDelay((i * 40).toLong()).setInterpolator(DecelerateInterpolator(2f)).start()
            }
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), 0) }
            val mutedColor = Color.argb(70, 0, 0, 0)
            row.addView(svgView(if (showSugg) "icons/svg/search.svg" else "icons/svg/history.svg", 16, mutedColor), LinearLayout.LayoutParams(dp(16), dp(16)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))
            row.addView(TextView(context).apply {
                text = label; setTextColor(Color.parseColor("#1A1A1A")); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val actionIcon = svgView(if (showSugg) "icons/svg/back_arrow.svg" else "icons/svg/close.svg", 15, mutedColor).apply {
                if (showSugg) rotation = -45f; setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener {
                    if (showSugg) { searchField.setText(label); searchField.setSelection(label.length) }
                    else removeHistory(label)
                }
            }
            row.addView(actionIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
            item.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(58)))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (!isLast) lp.bottomMargin = dp(2)
            suggestionsCol.addView(item, lp)
        }
    }

    private fun dismiss() { hideKeyboard(); activity.removeContentOverlay(this) }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px = dp(sizeDp); val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp)); iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    companion object { private const val SEARCH_PREF = "search_history_v3" }
}