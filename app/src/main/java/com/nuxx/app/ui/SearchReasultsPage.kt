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
    private val appBarH get() = statusH + dp(56)

    private lateinit var searchField:       EditText
    private lateinit var clearBtn:          ImageView
    private lateinit var bodyFrame:         FrameLayout
    private lateinit var suggestionsScroll: NestedScrollView
    private lateinit var suggestionsCol:    LinearLayout
    private lateinit var resultsScroll:     NestedScrollView
    private lateinit var resultsGrid:       LinearLayout
    private lateinit var emptyState:        LinearLayout
    private lateinit var loadingView:       LinearLayout

    private val ICO_BACK    = "icons/svg/phosphor-icons/regular/arrow-left.svg"
    private val ICO_SEARCH  = "icons/svg/phosphor-icons/regular/magnifying-glass.svg"
    private val ICO_CLOSE   = "icons/svg/phosphor-icons/regular/x.svg"
    private val ICO_HISTORY = "icons/svg/phosphor-icons/regular/clock-counter-clockwise.svg"
    private val ICO_ARROW   = "icons/svg/phosphor-icons/regular/arrow-up-left.svg"

    init {
        setBackgroundColor(AppTheme.bg)
        loadHistory()
        buildUI()
        activity.setStatusBarDark(false)
        activity.window.statusBarColor = AppTheme.bg
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
        bodyFrame = FrameLayout(context).apply { setBackgroundColor(AppTheme.bg) }
        addView(bodyFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = appBarH
        })
        buildResultsPane()
        buildSuggestionsView()
        buildLoadingView()
        buildEmptyState()
    }

    private fun buildAppBar() {
        val appBar = FrameLayout(context).apply {
            setBackgroundColor(AppTheme.bg)
            elevation = dp(2).toFloat()
        }

        appBar.addView(View(context).apply { setBackgroundColor(AppTheme.bg) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, statusH))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(12), 0)
        }

        val backBtn = FrameLayout(context).apply {
            isClickable = true; isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { dismiss() }
        }
        backBtn.addView(svgView(ICO_BACK, 22, AppTheme.text),
            FrameLayout.LayoutParams(dp(22), dp(22)).also { it.gravity = Gravity.CENTER })
        row.addView(backBtn, LinearLayout.LayoutParams(dp(46), dp(56)))

        val searchBar = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(AppTheme.bgSecondary)
            }
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        inner.addView(svgView(ICO_SEARCH, 18, AppTheme.textSecondary),
            LinearLayout.LayoutParams(dp(18), dp(18)))
        inner.addView(View(context), LinearLayout.LayoutParams(dp(8), 0))

        searchField = EditText(context).apply {
            setText(initialQuery)
            setTextColor(AppTheme.text)
            setHintTextColor(AppTheme.textSecondary)
            hint = "Pesquisar vídeos..."
            textSize = 15f
            background = null
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(text.toString().trim()); true
                } else false
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
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !isEditing) { isEditing = true; showEditing() }
            }
        }
        inner.addView(searchField,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        clearBtn = svgView(ICO_CLOSE, 16, AppTheme.textSecondary).apply {
            visibility = if (initialQuery.isNotEmpty()) View.VISIBLE else View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isClickable = true; isFocusable = true
            setOnClickListener {
                searchField.setText("")
                suggestions.clear()
                rebuildSuggestions()
            }
        }
        inner.addView(clearBtn, LinearLayout.LayoutParams(dp(30), dp(30)))

        searchBar.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(46)))
        row.addView(searchBar, LinearLayout.LayoutParams(0, dp(46), 1f))

        appBar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56)).also {
            it.topMargin = statusH; it.gravity = Gravity.TOP
        })

        appBar.addView(View(context).apply { setBackgroundColor(AppTheme.divider) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, appBarH).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildResultsPane() {
        resultsScroll = NestedScrollView(context).apply {
            isFillViewport = true
            visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        resultsGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(80))
        }
        resultsScroll.addView(resultsGrid, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(resultsScroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildSuggestionsView() {
        suggestionsScroll = NestedScrollView(context).apply {
            isFillViewport = true
            visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        suggestionsCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(32))
        }
        suggestionsScroll.addView(suggestionsCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        bodyFrame.addView(suggestionsScroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildLoadingView() {
        loadingView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        val indicator = com.google.android.material.progressindicator.CircularProgressIndicator(context).apply {
            isIndeterminate = true
            indicatorSize = dp(36)
            trackThickness = dp(3)
            @Suppress("RestrictedApi")
            trackCornerRadius = dp(50)
            setIndicatorColor(AppTheme.primary)
            trackColor = Color.parseColor("#22000000")
        }
        loadingView.addView(indicator,
            LinearLayout.LayoutParams(dp(36), dp(36)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        loadingView.addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
        loadingView.addView(TextView(context).apply {
            text = "A pesquisar..."
            setTextColor(AppTheme.textSecondary)
            textSize = 14f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        bodyFrame.addView(loadingView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun buildEmptyState() {
        emptyState = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(AppTheme.bg)
        }
        emptyState.addView(svgView(ICO_SEARCH, 52, AppTheme.textSecondary),
            LinearLayout.LayoutParams(dp(52), dp(52)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        emptyState.addView(View(context), LinearLayout.LayoutParams(0, dp(16)))
        emptyState.addView(TextView(context).apply {
            text = "Sem resultados"
            setTextColor(AppTheme.text)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        emptyState.addView(View(context), LinearLayout.LayoutParams(0, dp(8)))
        emptyState.addView(TextView(context).apply {
            text = "Tenta pesquisar com outras palavras"
            setTextColor(AppTheme.textSecondary)
            textSize = 13f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        bodyFrame.addView(emptyState, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun showEditing() {
        isEditing = true
        resultsScroll.visibility = View.GONE
        loadingView.visibility = View.GONE
        emptyState.visibility = View.GONE
        suggestionsScroll.visibility = View.VISIBLE
        rebuildSuggestions()
    }

    private fun showLoading() {
        suggestionsScroll.visibility = View.GONE
        emptyState.visibility = View.GONE
        resultsScroll.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    private fun showResults() {
        suggestionsScroll.visibility = View.GONE
        emptyState.visibility = View.GONE
        loadingView.visibility = View.GONE
        resultsScroll.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        suggestionsScroll.visibility = View.GONE
        loadingView.visibility = View.GONE
        resultsScroll.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }

    private fun doSearch(q: String) {
        if (q.isEmpty()) return
        searchField.setText(q)
        searchField.clearFocus()
        hideKeyboard()
        isEditing = false
        saveHistory(q)
        showLoading()
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
        val pad = dp(12); val gap = dp(8)
        val colW = (screenW - pad * 2 - gap) / 2
        val thumbH = (colW / 1.77f).toInt()
        for (index in results.indices step 2) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(index, index + 1).forEach { i ->
                if (i >= results.size) {
                    row.addView(View(context),
                        LinearLayout.LayoutParams(colW, thumbH + dp(60)))
                    return@forEach
                }
                val card = buildVideoCard(results[i], colW, thumbH)
                val lp   = LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT)
                if (i % 2 != 0) lp.leftMargin = gap
                row.addView(card, lp)
            }
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (index > 0) rowLp.topMargin = gap
            resultsGrid.addView(row, rowLp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildVideoCard(video: FeedVideo, colW: Int, thumbH: Int): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                foreground = context.getDrawable(tv.resourceId)
        }
        val thumbFrame = FrameLayout(context).apply {
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(AppTheme.thumbBg)
            }
        }
        val thumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        if (video.thumb.isNotEmpty()) {
            Glide.with(context).load(video.thumb).override(colW, thumbH).centerCrop().into(thumb)
        }
        thumbFrame.addView(thumb, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, thumbH))
        if (video.duration.isNotEmpty()) {
            thumbFrame.addView(TextView(context).apply {
                text = video.duration
                setTextColor(Color.WHITE)
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#CC000000"))
                }
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.BOTTOM or Gravity.END
                it.bottomMargin = dp(4); it.rightMargin = dp(4)
            })
        }
        card.addView(thumbFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, thumbH))
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        info.addView(TextView(context).apply {
            text = video.title
            setTextColor(AppTheme.text)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
        })
        val metaStr = buildString {
            if (video.source.label.isNotEmpty()) append(video.source.label)
            if (video.views.isNotEmpty()) append("  ·  ${video.views}")
            if (video.duration.isNotEmpty()) append("  ·  ${video.duration}")
        }
        if (metaStr.isNotEmpty()) {
            info.addView(TextView(context).apply {
                text = metaStr
                setTextColor(AppTheme.textSecondary)
                textSize = 10.5f
                maxLines = 1
                setPadding(0, dp(2), 0, 0)
            })
        }
        card.addView(info, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.setOnClickListener { VideoPreviewModal.show(activity, video) }
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
                for (m in regex.findAll(body)) {
                    if (count++ == 0) continue
                    list.add(m.groupValues[1])
                    if (list.size >= 8) break
                }
                handler.post {
                    if (!isDestroyed) {
                        suggestions.clear(); suggestions.addAll(list)
                        if (isEditing) rebuildSuggestions()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun rebuildSuggestions() {
        suggestionsCol.removeAllViews()
        val q        = searchField.text.toString().trim()
        val showSugg = q.length >= 2 && suggestions.isNotEmpty()
        val items    = if (showSugg) suggestions else history

        if (!showSugg && history.isNotEmpty()) {
            val hRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(8))
            }
            hRow.addView(TextView(context).apply {
                text = "Pesquisas recentes"
                setTextColor(AppTheme.textSecondary)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.04f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            hRow.addView(TextView(context).apply {
                text = "Limpar"
                setTextColor(AppTheme.primary)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                isClickable = true; isFocusable = true
                setOnClickListener { showClearHistoryDialog() }
            })
            suggestionsCol.addView(hRow)
        }

        if (items.isEmpty()) return

        items.forEachIndexed { i, label ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
                    background = context.getDrawable(tv.resourceId)
                setOnClickListener { doSearch(label) }
            }
            row.addView(svgView(
                if (showSugg) ICO_SEARCH else ICO_HISTORY,
                18, AppTheme.textSecondary
            ), LinearLayout.LayoutParams(dp(18), dp(18)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(14), 0))
            row.addView(TextView(context).apply {
                text = label
                setTextColor(AppTheme.text)
                textSize = 15f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val rightIco = svgView(
                if (showSugg) ICO_ARROW else ICO_CLOSE,
                16, AppTheme.textSecondary
            ).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (showSugg) {
                        searchField.setText(label)
                        searchField.setSelection(label.length)
                    } else {
                        removeHistory(label)
                    }
                }
            }
            row.addView(rightIco, LinearLayout.LayoutParams(dp(34), dp(34)))

            suggestionsCol.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

            suggestionsCol.addView(View(context).apply {
                setBackgroundColor(AppTheme.dividerSoft)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1).also {
                it.leftMargin = dp(48)
            })
        }
    }

    private fun showClearHistoryDialog() {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#88000000"))
            isClickable = true
        }
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(AppTheme.bg)
            }
            elevation = dp(8).toFloat()
            setPadding(dp(24), dp(24), dp(24), dp(16))
        }
        dialogView.addView(TextView(context).apply {
            text = "Limpar histórico"
            setTextColor(AppTheme.text)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        })
        dialogView.addView(View(context), LinearLayout.LayoutParams(0, dp(10)))
        dialogView.addView(TextView(context).apply {
            text = "Tens a certeza que queres apagar todo o histórico de pesquisa? Esta ação não pode ser desfeita."
            setTextColor(AppTheme.textSecondary)
            textSize = 14f
            setLineSpacing(0f, 1.4f)
        })
        dialogView.addView(View(context), LinearLayout.LayoutParams(0, dp(20)))
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = TextView(context).apply {
            text = "Cancelar"
            setTextColor(AppTheme.textSecondary)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true))
                background = context.getDrawable(tv.resourceId)
        }
        val confirmBtn = TextView(context).apply {
            text = "Limpar"
            setTextColor(AppTheme.primary)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true; isFocusable = true
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true))
                background = context.getDrawable(tv.resourceId)
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(View(context), LinearLayout.LayoutParams(dp(4), 0))
        btnRow.addView(confirmBtn)
        dialogView.addView(btnRow)

        overlay.addView(dialogView, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.85f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.CENTER })

        val decorView = activity.window.decorView as android.view.ViewGroup
        decorView.addView(overlay, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT))

        fun dismiss() {
            overlay.animate().alpha(0f).setDuration(180).withEndAction {
                decorView.removeView(overlay)
            }.start()
        }

        overlay.setOnClickListener { dismiss() }
        cancelBtn.setOnClickListener { dismiss() }
        confirmBtn.setOnClickListener { clearHistory(); dismiss() }

        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(200).start()
        dialogView.scaleX = 0.92f; dialogView.scaleY = 0.92f
        dialogView.animate().scaleX(1f).scaleY(1f)
            .setDuration(250).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun dismiss() { hideKeyboard(); activity.removeContentOverlay(this) }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val px = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp); iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val SEARCH_PREF = "search_history_v3"
    }
}