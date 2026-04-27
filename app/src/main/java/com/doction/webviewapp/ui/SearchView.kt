// SearchView.kt
package com.doction.webviewapp.ui

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
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG
import com.doction.webviewapp.MainActivity
import com.doction.webviewapp.models.SiteModel
import com.doction.webviewapp.models.kSites
import com.doction.webviewapp.theme.AppTheme

private const val PREF_KEY = "search_history_v3"

@SuppressLint("ViewConstructor")
class SearchView(context: Context) : FrameLayout(context) {

    private val activity = context as MainActivity
    private val handler  = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_prefs", Context.MODE_PRIVATE)

    private val history = mutableListOf<String>()

    private lateinit var appBarBg:      View
    private lateinit var appBarTitle:   TextView
    private lateinit var contentScroll: NestedScrollView
    private lateinit var contentCol:    LinearLayout

    private val themeListener: () -> Unit = { applyTheme() }

    private val categories = listOf(
        "Heterossexual" to "imagens/search_page/hetero.jpg",
        "Homossexual"   to "imagens/search_page/homo.jpg",
        "Lésbicas"      to "imagens/search_page/lesbicas.jpg",
        "Anal"          to "imagens/search_page/anal.jpg",
        "Amador"        to "imagens/search_page/amador.jpg",
        "MILF"          to "imagens/search_page/milf.jpg",
        "Teen"          to "imagens/search_page/teen.jpg",
        "Hentai"        to "imagens/search_page/hentai.jpg",
    )

    init {
        setBackgroundColor(AppTheme.bg)
        forceStatusBarLight()
        loadHistory()
        buildUI()
        AppTheme.addThemeListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppTheme.removeThemeListener(themeListener)
    }

    // ── Status bar clara com ícones escuros ───────────────────────────────────

    private fun forceStatusBarLight() {
        val window = activity.window
        window.statusBarColor = AppTheme.bg
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true  // ícones escuros
        }
    }

    // ── Histórico ─────────────────────────────────────────────────────────────

    private fun loadHistory() {
        val saved = prefs.getStringList(PREF_KEY)
        history.clear(); history.addAll(saved)
    }

    private fun saveHistory(q: String) {
        history.remove(q); history.add(0, q)
        if (history.size > 30) history.removeAt(history.lastIndex)
        prefs.setStringList(PREF_KEY, history)
    }

    private fun removeFromHistory(q: String) {
        history.remove(q); prefs.setStringList(PREF_KEY, history); rebuildHistory()
    }

    private fun clearHistory() {
        history.clear(); prefs.setStringList(PREF_KEY, history); rebuildHistory()
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

    // ── Navegação ─────────────────────────────────────────────────────────────

    private fun goSearch(q: String) {
        saveHistory(q)
        val page = SearchResultsPage(context, q)
        activity.addContentOverlay(page)
    }

    private fun goSearchPage() {
        val page = SearchResultsPage(context, "")
        activity.addContentOverlay(page)
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        val statusH = statusBarHeight()

        buildAppBar(statusH)

        contentScroll = NestedScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
        }
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        contentScroll.addView(contentCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // Conteúdo começa abaixo da status bar + app bar
        addView(contentScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.topMargin = statusH + dp(52)
        })

        buildBody()
    }

    private fun buildAppBar(statusH: Int) {
        val totalH = statusH + dp(52)

        val bar = FrameLayout(context)
        appBarBg = View(context).apply { setBackgroundColor(AppTheme.bg) }
        bar.addView(appBarBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }

        appBarTitle = TextView(context).apply {
            text = "Pesquisar"
            setTextColor(AppTheme.text)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = -0.03f
        }
        row.addView(appBarTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))

        // Search bar — mais curvo (cornerRadius 22dp)
        val searchBar = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#EBEBEB"))
            }
            setOnClickListener { goSearchPage() }
        }
        val searchInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
        }
        val searchIcon = svgView("icons/svg/search.svg", 16, Color.argb(100, 0, 0, 0))
        searchInner.addView(searchIcon, LinearLayout.LayoutParams(dp(16), dp(16)))
        searchInner.addView(View(context), LinearLayout.LayoutParams(dp(8), 0))
        searchInner.addView(TextView(context).apply {
            text = "Pesquisar..."
            setTextColor(Color.argb(100, 0, 0, 0))
            textSize = 14f
        })
        searchBar.addView(searchInner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(40)))
        row.addView(searchBar, LinearLayout.LayoutParams(0, dp(40), 1f))

        // O row fica na parte inferior do bar (abaixo da status bar)
        bar.addView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(52)).also {
            it.gravity = Gravity.BOTTOM
        })

        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, totalH).also {
            it.gravity = Gravity.TOP
        })
    }

    private fun buildBody() {
        contentCol.removeAllViews()
        contentCol.addView(spacer(16))
        contentCol.addView(buildSitesRow())
        contentCol.addView(sectionTitle("Categorias"))
        contentCol.addView(buildCategoriesGrid())
        rebuildHistory()
    }

    // ── Sites (favicons via Glide — asset local só se localIconAsset definido) ─

    private fun buildSitesRow(): View {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), 0)
        }

        kSites.forEach { site ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    val page = BrowserPage(context, site)
                    activity.addContentOverlay(page)
                }
            }

            val iconBg = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.parseColor("#F0F0F0"))
                }
            }

            val favicon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            loadFavicon(favicon, site)

            iconBg.addView(favicon, FrameLayout.LayoutParams(dp(32), dp(32)).also {
                it.gravity = Gravity.CENTER
            })
            cell.addView(iconBg, LinearLayout.LayoutParams(dp(52), dp(52)))
            cell.addView(spacer(5))
            cell.addView(TextView(context).apply {
                text = site.name
                setTextColor(AppTheme.iconSub)
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(cell)
        }

        scroll.addView(row)
        return scroll
    }

    /**
     * Carrega o favicon:
     * – Se o site tem localIconAsset → lê dos assets (mantém comportamento original)
     * – Caso contrário → carrega via Glide a partir de faviconUrl (Google S2)
     */
    private fun loadFavicon(iv: ImageView, site: SiteModel) {
        val asset = site.localIconAsset
        if (asset != null) {
            try {
                val bmp = BitmapFactory.decodeStream(context.assets.open(asset))
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {
                loadFaviconFallback(iv)
            }
        } else {
            Glide.with(context)
                .load(site.faviconUrl)
                .override(dp(32), dp(32))
                .centerInside()
                .error(svgFallbackDrawable())
                .into(iv)
        }
    }

    private fun loadFaviconFallback(iv: ImageView) {
        iv.setImageDrawable(svgView("icons/svg/globe.svg", 24, AppTheme.iconSub).drawable)
    }

    private fun svgFallbackDrawable() =
        svgView("icons/svg/globe.svg", 24, AppTheme.iconSub).drawable

    // ── Categorias ────────────────────────────────────────────────────────────

    private fun buildCategoriesGrid(): View {
        val screenW = resources.displayMetrics.widthPixels
        val pad     = dp(16)
        val gap     = dp(8)
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
                    rowView.addView(View(context), LinearLayout.LayoutParams(colW, rowH))
                    return@forEach
                }
                val (label, asset) = categories[i]
                val card = buildCategoryCard(label, asset, colW, rowH)
                val lp = LinearLayout.LayoutParams(colW, rowH)
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
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E1E1E"))
            }
        }
        val img = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            try {
                setImageBitmap(BitmapFactory.decodeStream(context.assets.open(asset)))
            } catch (_: Exception) {
                setBackgroundColor(Color.parseColor("#1E1E1E"))
            }
        }
        card.addView(img, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val gradient = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#A6000000")))
        }
        card.addView(gradient, FrameLayout.LayoutParams(
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
                MotionEvent.ACTION_DOWN  -> v.animate().scaleX(0.96f).scaleY(0.96f)
                    .setDuration(100).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP    -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(200).setInterpolator(DecelerateInterpolator(2f)).start()
                    goSearch(label)
                }
                MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(200).start()
            }
            true
        }
        return card
    }

    // ── Histórico ─────────────────────────────────────────────────────────────

    private var historyContainer: LinearLayout? = null

    private fun rebuildHistory() {
        historyContainer?.let { contentCol.removeView(it) }
        if (history.isEmpty()) { historyContainer = null; return }

        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        historyContainer = container

        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(8))
        }
        hRow.addView(TextView(context).apply {
            text = "Histórico"; setTextColor(AppTheme.text)
            textSize = 17f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hRow.addView(TextView(context).apply {
            text = "Limpar"; setTextColor(AppTheme.ytRed); textSize = 14f
            setOnClickListener { clearHistory() }
        })
        container.addView(hRow)

        val total = history.size
        history.forEachIndexed { i, label ->
            val isOnly  = total == 1
            val isFirst = i == 0
            val isLast  = i == total - 1
            val bigR    = dp(12).toFloat()
            val smallR  = dp(6).toFloat()
            val radii = when {
                isOnly  -> floatArrayOf(bigR, bigR, bigR, bigR, bigR, bigR, bigR, bigR)
                isFirst -> floatArrayOf(bigR, bigR, bigR, bigR, smallR, smallR, smallR, smallR)
                isLast  -> floatArrayOf(smallR, smallR, smallR, smallR, bigR, bigR, bigR, bigR)
                else    -> floatArrayOf(smallR, smallR, smallR, smallR, smallR, smallR, smallR, smallR)
            }
            val cardBg = Color.parseColor("#F0F0F0")
            val item = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                    setColor(cardBg)
                }
                setOnClickListener { goSearch(label) }
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
            }
            val mutedColor = Color.argb(70, 0, 0, 0)
            row.addView(svgView("icons/svg/history.svg", 16, mutedColor),
                LinearLayout.LayoutParams(dp(16), dp(16)))
            row.addView(View(context), LinearLayout.LayoutParams(dp(12), 0))
            row.addView(TextView(context).apply {
                text = label; setTextColor(AppTheme.text); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            item.addView(row, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(58)))

            setupSwipeToDismiss(item, container) { removeFromHistory(label) }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dp(16); lp.rightMargin = dp(16)
            if (!isLast) lp.bottomMargin = dp(2)
            container.addView(item, lp)
        }
        contentCol.addView(container)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToDismiss(view: View, parent: LinearLayout, onDismiss: () -> Unit) {
        var startX = 0f; var isDragging = false
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN   -> { startX = event.x; isDragging = false; false }
                MotionEvent.ACTION_MOVE   -> {
                    val dx = event.x - startX
                    if (!isDragging && dx < -dp(10)) isDragging = true
                    if (isDragging && dx < 0) { v.translationX = dx; v.alpha = 1f + dx / v.width; true } else false
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

    // ── Tema ──────────────────────────────────────────────────────────────────

    private fun applyTheme() {
        setBackgroundColor(AppTheme.bg)
        appBarBg.setBackgroundColor(AppTheme.bg)
        appBarTitle.setTextColor(AppTheme.text)
        buildBody()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sectionTitle(text: String) = TextView(context).apply {
        this.text = text; setTextColor(AppTheme.text)
        textSize = 17f; setTypeface(null, Typeface.BOLD)
        setPadding(dp(16), dp(20), dp(16), dp(8))
    }

    private fun spacer(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun svgView(path: String, sizeDp: Int, tint: Int): ImageView {
        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
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

    private fun statusBarHeight(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else dp(24)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}