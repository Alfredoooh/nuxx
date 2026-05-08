package com.nuxx.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.caverock.androidsvg.SVG
import com.nuxx.app.MainActivity
import com.nuxx.app.models.SiteModel
import com.nuxx.app.models.kSites
import com.nuxx.app.theme.AppTheme
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch

private const val PREF_KEY         = "search_history_v3"
private const val PREF_HIDDEN_APPS = "hidden_apps_v1"
private const val PREF_CATEGORIES  = "selected_categories_v1"

@SuppressLint("ViewConstructor")
class SearchView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val history = mutableListOf<String>()

    private lateinit var appBarBg:         View
    private lateinit var appBarTitle:      TextView
    private lateinit var contentScroll:    NestedScrollView
    private lateinit var contentCol:       LinearLayout
    private lateinit var historyContainer: LinearLayout

    private val themeListener: () -> Unit = { applyTheme() }

    private var activeModalOverlay: FrameLayout? = null
    private var activePopupMenu:    PopupMenu?   = null

    private val allCategories = listOf(
        "Heterossexual" to "imagens/search_page/hetero.jpg",
        "Homossexual"   to "imagens/search_page/homo.jpg",
        "Lésbicas"      to "imagens/search_page/lesbicas.jpg",
        "Anal"          to "imagens/search_page/anal.jpg",
        "Amador"        to "imagens/search_page/amador.jpg",
        "MILF"          to "imagens/search_page/milf.jpg",
        "Teen"          to "imagens/search_page/teen.jpg",
        "Hentai"        to "imagens/search_page/hentai.jpg",
    )

    private val selectedCategories get(): MutableSet<String> {
        val saved = prefs.getStringSet(PREF_CATEGORIES, null)
        return saved?.toMutableSet() ?: allCategories.map { it.first }.toMutableSet()
    }

    init {
        setBackgroundColor(AppTheme.bg)
        // Statusbar gerida exclusivamente pelo MainActivity.switchTab — não interferir aqui
        loadHistory()
        buildUI()
        AppTheme.addThemeListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppTheme.removeThemeListener(themeListener)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            val popup = activePopupMenu
            val modal = activeModalOverlay
            when {
                popup != null -> { popup.dismiss(); return true }
                modal != null -> { dismissModal(modal); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadHistory() {
        val saved = prefs.getStringList(PREF_KEY)
        history.clear()
        history.addAll(saved)
    }

    private fun saveHistory(q: String) {
        history.remove(q); history.add(0, q)
        if (history.size > 30) history.removeAt(history.lastIndex)
        prefs.setStringList(PREF_KEY, history)
    }

    private fun removeFromHistory(q: String) {
        history.remove(q)
        prefs.setStringList(PREF_KEY, history)
        rebuildHistorySection()
    }

    private fun clearHistory() {
        history.clear()
        prefs.setStringList(PREF_KEY, history)
        rebuildHistorySection()
    }

    private fun SharedPreferences.getStringList(key: String): List<String> {
        val size = getInt("${key}_size", 0)
        return (0 until size).map { getString("${key}_$it", "") ?: "" }
    }

    private fun SharedPreferences.setStringList(key: String, list: List<String>) {
        edit().apply {
            putInt("${key}_size", list.size)
            list.forEachIndexed { i, s -> putString("${key}_$i", s) }
        }.apply()
    }

    private fun goSearch(q: String) {
        saveHistory(q); loadHistory(); rebuildHistorySection()
        activity.addContentOverlay(SearchResultsPage(context, q))
    }

    private fun goSearchPage() {
        activity.addContentOverlay(SearchResultsPage(context, ""))
    }

    private fun buildUI() {
        val statusH = activity.statusBarHeight
        buildAppBar(statusH)

        contentScroll = NestedScrollView(context).apply {
            isFillViewport  = true
            overScrollMode  = View.OVER_SCROLL_ALWAYS
        }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        contentScroll.addView(contentCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(contentScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = statusH + dp(100)
        })
        buildBody()
    }

    private fun buildAppBar(statusH: Int) {
        val totalH = statusH + dp(100)
        val bar    = FrameLayout(context)
        appBarBg   = View(context).apply { setBackgroundColor(AppTheme.bg) }
        bar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        appBarTitle = TextView(context).apply {
            text         = "Pesquisar"
            setTextColor(AppTheme.text)
            textSize     = 22f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(appBarTitle)
        val moreBtn = buildMoreVertIcon()
        titleRow.addView(moreBtn, LinearLayout.LayoutParams(dp(32), dp(32)))
        col.addView(titleRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(8)
        })

        val searchBar = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#EBEBEB"))
            }
            setOnClickListener { goSearchPage() }
        }
        val searchInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        searchInner.addView(
            svgView("icons/svg/search.svg", 16, Color.argb(100, 0, 0, 0)),
            LinearLayout.LayoutParams(dp(16), dp(16)))
        searchInner.addView(View(context), LinearLayout.LayoutParams(dp(8), 0))
        searchInner.addView(TextView(context).apply {
            text = "Pesquisar..."
            setTextColor(Color.argb(100, 0, 0, 0))
            textSize = 14f
        })
        searchBar.addView(searchInner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(44)))
        col.addView(searchBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        bar.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, totalH).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildMoreVertIcon(): View {
        val btn = object : View(context) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AppTheme.text; style = android.graphics.Paint.Style.FILL
            }
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f; val cy = height / 2f
                val r  = dp(2).toFloat(); val gap = dp(5).toFloat()
                canvas.drawCircle(cx, cy - gap, r, paint)
                canvas.drawCircle(cx, cy,       r, paint)
                canvas.drawCircle(cx, cy + gap, r, paint)
            }
        }
        btn.setOnClickListener { showPopupMenu(btn) }
        return btn
    }

    private fun showPopupMenu(anchor: View) {
        val lightCtx = ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_Light)
        val popup    = PopupMenu(lightCtx, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Ocultar apps")
        popup.menu.add(0, 2, 0, "Limpar histórico")
        popup.menu.add(0, 3, 0, "Selecionar categorias")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showHiddenAppsModal()
                2 -> clearHistory()
                3 -> showCategoriesModal()
            }
            true
        }
        popup.setOnDismissListener { activePopupMenu = null }
        activePopupMenu = popup
        popup.show()
    }

    private fun showModal(buildContent: (dismiss: () -> Unit) -> View) {
        if (activeModalOverlay != null) return
        val decorView = activity.window.decorView as ViewGroup
        val overlay = FrameLayout(context)
        overlay.setBackgroundColor(Color.TRANSPARENT)
        overlay.setOnClickListener { dismissModal(overlay) }
        val sheet = buildContent { dismissModal(overlay) }
        sheet.isClickable = true
        sheet.setOnClickListener { }
        overlay.addView(sheet, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.BOTTOM
        })
        decorView.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        activeModalOverlay = overlay
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
        overlay.post { overlay.setBackgroundColor(Color.parseColor("#66000000")) }
        sheet.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val h = sheet.height.toFloat()
                    sheet.translationY = h
                    sheet.animate()
                        .translationY(0f)
                        .setDuration(380)
                        .setInterpolator(DecelerateInterpolator(2.2f))
                        .start()
                }
            })
    }

    private fun dismissModal(overlay: FrameLayout) {
        if (activeModalOverlay != overlay) return
        activeModalOverlay = null
        val decorView = activity.window.decorView as ViewGroup
        val sheet     = overlay.getChildAt(0) ?: run { decorView.removeView(overlay); return }
        val h         = sheet.height.toFloat()
        overlay.animate().alpha(0f).setDuration(220)
            .setInterpolator(AccelerateInterpolator()).start()
        sheet.animate()
            .translationY(h)
            .setDuration(280)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction { decorView.removeView(overlay) }
            .start()
    }

    private fun showHiddenAppsModal() {
        showModal { dismiss ->
            val screenH = resources.displayMetrics.heightPixels
            val maxH    = (screenH * 0.75f).toInt()
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background  = GradientDrawable().apply {
                    shape      = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        dp(20).toFloat(), dp(20).toFloat(),
                        dp(20).toFloat(), dp(20).toFloat(),
                        0f, 0f, 0f, 0f)
                    setColor(Color.WHITE)
                }
                setPadding(dp(20), dp(12), dp(20), dp(32))
                addView(buildSheetHandle())
                addView(TextView(context).apply {
                    text = "Ocultar apps"
                    setTextColor(Color.parseColor("#1A1A1A"))
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) })
                val scroll = NestedScrollView(context).apply { overScrollMode = View.OVER_SCROLL_NEVER }
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                kSites.forEach { site ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity     = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(8), 0, dp(8))
                    }
                    row.addView(TextView(context).apply {
                        text = site.name
                        setTextColor(Color.parseColor("#1A1A1A"))
                        textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val hiddenApps = prefs.getStringSet(PREF_HIDDEN_APPS, emptySet())!!.toMutableSet()
                    val sw = MaterialSwitch(context).apply {
                        isChecked = !hiddenApps.contains(site.name)
                        setOnCheckedChangeListener { _, checked ->
                            val set = prefs.getStringSet(PREF_HIDDEN_APPS, emptySet())!!.toMutableSet()
                            if (checked) set.remove(site.name) else set.add(site.name)
                            prefs.edit().putStringSet(PREF_HIDDEN_APPS, set).apply()
                        }
                    }
                    row.addView(sw)
                    col.addView(row)
                    col.addView(View(context).apply {
                        setBackgroundColor(Color.parseColor("#1AF0F0F0"))
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                }
                scroll.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT))
                addView(scroll, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    maxH - dp(12) - dp(40) - dp(56)))
                addView(TextView(context).apply {
                    text = "Fechar"
                    setTextColor(Color.parseColor("#E53935"))
                    textSize = 15f
                    gravity  = Gravity.CENTER
                    setPadding(0, dp(16), 0, 0)
                    setOnClickListener { dismiss(); buildBody() }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun showCategoriesModal() {
        val selected = selectedCategories
        showModal { dismiss ->
            val screenH = resources.displayMetrics.heightPixels
            val maxH    = (screenH * 0.75f).toInt()
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background  = GradientDrawable().apply {
                    shape       = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        dp(20).toFloat(), dp(20).toFloat(),
                        dp(20).toFloat(), dp(20).toFloat(),
                        0f, 0f, 0f, 0f)
                    setColor(Color.WHITE)
                }
                setPadding(dp(20), dp(12), dp(20), dp(32))
                addView(buildSheetHandle())
                addView(TextView(context).apply {
                    text = "Categorias do feed"
                    setTextColor(Color.parseColor("#1A1A1A"))
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(4) })
                addView(TextView(context).apply {
                    text = "Seleciona as categorias que aparecem no feed"
                    setTextColor(Color.argb(140, 0, 0, 0))
                    textSize = 13f
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) })
                val scroll = NestedScrollView(context).apply { overScrollMode = View.OVER_SCROLL_NEVER }
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                allCategories.forEach { (label, _) ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity     = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(10), 0, dp(10))
                    }
                    row.addView(TextView(context).apply {
                        text = label
                        setTextColor(Color.parseColor("#1A1A1A"))
                        textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val cb = MaterialCheckBox(context).apply {
                        isChecked = selected.contains(label)
                        setOnCheckedChangeListener { _, checked ->
                            val set = selectedCategories
                            if (checked) set.add(label) else set.remove(label)
                            prefs.edit().putStringSet(PREF_CATEGORIES, set).apply()
                        }
                    }
                    row.addView(cb)
                    col.addView(row)
                    col.addView(View(context).apply {
                        setBackgroundColor(Color.parseColor("#1AF0F0F0"))
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                }
                scroll.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT))
                addView(scroll, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    maxH - dp(186)))
                val saveBtn = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape        = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#E53935"))
                    }
                    setOnClickListener { dismiss(); buildBody() }
                }
                saveBtn.addView(TextView(context).apply {
                    text = "Guardar"
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, dp(14))
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT))
                addView(saveBtn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) })
            }
        }
    }

    private fun buildSheetHandle(): View {
        val container = FrameLayout(context).apply { setPadding(0, 0, 0, dp(12)) }
        container.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#DDDDDD"))
            }
        }, FrameLayout.LayoutParams(dp(36), dp(4)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
        })
        return container
    }

    private fun buildBody() {
        contentCol.removeAllViews()
        contentCol.addView(spacer(16))
        contentCol.addView(buildSitesRow())
        contentCol.addView(sectionTitle("Categorias"))
        contentCol.addView(buildCategoriesGrid())
        historyContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        contentCol.addView(historyContainer)
        rebuildHistorySection()
    }

    private fun rebuildHistorySection() {
        historyContainer.removeAllViews()
        if (history.isEmpty()) return
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(8))
        }
        hRow.addView(TextView(context).apply {
            text = "Histórico"
            setTextColor(AppTheme.text)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hRow.addView(TextView(context).apply {
            text = "Limpar"
            setTextColor(AppTheme.ytRed)
            textSize = 14f
            setOnClickListener { clearHistory() }
        })
        historyContainer.addView(hRow)
        val total = history.size
        history.forEachIndexed { i, label ->
            val isOnly  = total == 1
            val isFirst = i == 0
            val isLast  = i == total - 1
            val bigR    = dp(12).toFloat()
            val smallR  = dp(6).toFloat()
            val radii   = when {
                isOnly  -> floatArrayOf(bigR, bigR, bigR, bigR, bigR, bigR, bigR, bigR)
                isFirst -> floatArrayOf(bigR, bigR, bigR, bigR, smallR, smallR, smallR, smallR)
                isLast  -> floatArrayOf(smallR, smallR, smallR, smallR, bigR, bigR, bigR, bigR)
                else    -> floatArrayOf(smallR, smallR, smallR, smallR, smallR, smallR, smallR, smallR)
            }
            val item = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape       = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                    setColor(Color.parseColor("#F0F0F0"))
                }
                setOnClickListener { goSearch(label) }
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
            }
            val mutedColor = Color.argb(70, 0, 0, 0)
            row.addView(svgView("icons/svg/history.svg", 16, mutedColor),
                LinearLayout.LayoutParams(dp(16), dp(16)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))
            row.addView(TextView(context).apply {
                text = label
                setTextColor(AppTheme.text)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            item.addView(row, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(58)))
            setupSwipeToDismiss(item) { removeFromHistory(label) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin  = dp(16); lp.rightMargin = dp(16)
            if (!isLast) lp.bottomMargin = dp(2)
            historyContainer.addView(item, lp)
        }
    }

    private fun buildSitesRow(): View {
        val hiddenApps   = prefs.getStringSet(PREF_HIDDEN_APPS, emptySet())!!
        val visibleSites = kSites.filter { !hiddenApps.contains(it.name) }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        visibleSites.forEach { site ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { activity.addContentOverlay(BrowserPage(context, site)) }
            }
            val favicon = android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
            loadFavicon(favicon, site)
            cell.addView(favicon, LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(spacer(5))
            cell.addView(TextView(context).apply {
                text = site.name
                setTextColor(AppTheme.iconSub)
                textSize  = 10f
                gravity   = Gravity.CENTER
                maxLines  = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(cell)
        }
        scroll.addView(row)
        return scroll
    }

    private fun loadFavicon(iv: android.widget.ImageView, site: SiteModel) {
        val asset = site.localIconAsset
        if (asset != null) {
            try {
                val bmp = BitmapFactory.decodeStream(context.assets.open(asset))
                iv.setImageBitmap(cropCircle(bmp))
            } catch (_: Exception) { loadFaviconFallback(iv) }
        } else {
            Glide.with(context)
                .load(site.faviconUrl)
                .override(dp(48), dp(48))
                .transform(CircleCrop())
                .error(svgFallbackDrawable())
                .into(iv)
        }
    }

    private fun cropCircle(src: Bitmap): Bitmap {
        val size   = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val r      = size / 2f
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, ((size - src.width) / 2f), ((size - src.height) / 2f), paint)
        return output
    }

    private fun loadFaviconFallback(iv: android.widget.ImageView) {
        iv.setImageDrawable(svgView("icons/svg/globe.svg", 24, AppTheme.iconSub).drawable)
    }

    private fun svgFallbackDrawable() =
        svgView("icons/svg/globe.svg", 24, AppTheme.iconSub).drawable

    private fun buildCategoriesGrid(): View {
        val selected   = selectedCategories
        val categories = allCategories.filter { selected.contains(it.first) }
        val screenW = resources.displayMetrics.widthPixels
        val pad     = dp(16); val gap = dp(8)
        val colW    = (screenW - pad * 2 - gap) / 2
        val rowH    = (colW / 1.55f).toInt()
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        for (r in categories.indices step 2) {
            val rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(r, r + 1).forEach { i ->
                if (i >= categories.size) {
                    rowView.addView(View(context), LinearLayout.LayoutParams(colW, rowH)); return@forEach
                }
                val (label, asset) = categories[i]
                val card = buildCategoryCard(label, asset, colW, rowH)
                val lp   = LinearLayout.LayoutParams(colW, rowH)
                if (i % 2 != 0) lp.leftMargin = gap
                rowView.addView(card, lp)
            }
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (r > 0) rowLp.topMargin = gap
            grid.addView(rowView, rowLp)
        }
        return grid
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCategoryCard(label: String, asset: String, w: Int, h: Int): View {
        val card = FrameLayout(context).apply {
            clipToOutline = true
            background    = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E1E1E"))
            }
        }
        val img = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            try { setImageBitmap(BitmapFactory.decodeStream(context.assets.open(asset))) }
            catch (_: Exception) { setBackgroundColor(Color.parseColor("#1E1E1E")) }
        }
        card.addView(img, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#A6000000")))
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(TextView(context).apply {
            text = label; setTextColor(Color.WHITE)
            textSize = 13f; setTypeface(null, Typeface.BOLD)
            setShadowLayer(6f, 0f, 0f, Color.parseColor("#88000000"))
            setPadding(dp(10), 0, dp(10), dp(8))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.BOTTOM })
        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN   ->
                    v.animate().scaleX(0.96f).scaleY(0.96f)
                        .setDuration(100).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP     -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(200).setInterpolator(DecelerateInterpolator(2f)).start()
                    goSearch(label)
                }
                MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
            true
        }
        return card
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToDismiss(view: View, onDismiss: () -> Unit) {
        var startX = 0f; var isDragging = false
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN   -> { startX = event.x; isDragging = false; false }
                MotionEvent.ACTION_MOVE   -> {
                    val dx = event.x - startX
                    if (!isDragging && dx < -dp(10)) isDragging = true
                    if (isDragging && dx < 0) { v.translationX = dx; v.alpha = 1f + dx / v.width; true }
                    else false
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && v.translationX < -v.width * 0.4f) {
                        v.animate().translationX(-v.width.toFloat()).alpha(0f)
                            .setDuration(200).withEndAction { onDismiss() }.start()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(150).start()
                    }
                    isDragging = false; false
                }
                else -> false
            }
        }
    }

    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        appBarBg.setBackgroundColor(AppTheme.bg)
        appBarTitle.setTextColor(AppTheme.text)
        buildBody()
    }

    private fun sectionTitle(text: String) = TextView(context).apply {
        this.text = text; setTextColor(AppTheme.text)
        textSize  = 17f; setTypeface(null, Typeface.BOLD)
        setPadding(dp(16), dp(20), dp(16), dp(8))
    }

    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): android.widget.ImageView {
        val iv = android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE }
        try {
            val px  = dp(sizeDp)
            val svg = SVG.getFromAsset(context.assets, path)
            svg.documentWidth  = px.toFloat()
            svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            iv.setImageBitmap(bmp)
            iv.setColorFilter(tint)
        } catch (_: Exception) {}
        return iv
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}