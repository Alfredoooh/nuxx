package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*

class EditorAppBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Status { OK, BUSY, ERROR }

    var onDrawerToggle: (() -> Unit)? = null
    var onPull: (() -> Unit)? = null
    var onPush: (() -> Unit)? = null
    var onNewFile: (() -> Unit)? = null
    var onNewProject: (() -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onRedo: (() -> Unit)? = null
    var onSearch: (() -> Unit)? = null
    var onPreview: (() -> Unit)? = null
    var onThemeToggle: (() -> Unit)? = null

    private lateinit var repoLabel: TextView
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var pushBtn: LinearLayout
    private lateinit var undoBtn: LinearLayout
    private lateinit var redoBtn: LinearLayout
    private lateinit var previewBtn: LinearLayout
    private lateinit var statusBar: LinearLayout

    private var isDark = true

    init {
        orientation = VERTICAL
        buildBar()
    }

    private fun buildBar() {
        // ── Top row ───────────────────────────────────────────────────────
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
        }

        // Drawer toggle — hamburger (3 lines)
        val drawerBtn = buildHamburgerBtn()
        topRow.addView(drawerBtn, LayoutParams(dp(48), dp(40)))

        // Scrollable middle
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        val scrollInner = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }

        // Pull — arrow down
        scrollInner.addView(buildActionBtn(
            svgPath = "M8 2a.75.75 0 0 1 .75.75v9.69l3.22-3.22a.75.75 0 0 1 1.06 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0l-4.5-4.5a.75.75 0 0 1 1.06-1.06L7.25 12.44V2.75A.75.75 0 0 1 8 2z",
            label = "Pull"
        ) { onPull?.invoke() })

        // New file — plus
        scrollInner.addView(buildActionBtn(
            svgPath = "M8 2a.5.5 0 0 1 .5.5v5h5a.5.5 0 0 1 0 1h-5v5a.5.5 0 0 1-1 0v-5h-5a.5.5 0 0 1 0-1h5v-5A.5.5 0 0 1 8 2z",
            label = "Novo"
        ) { onNewFile?.invoke() })

        // Project — grid
        scrollInner.addView(buildActionBtn(
            svgPath = "M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5v-3zm8 0A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5v-3zm-8 8A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5v-3zm8 0A1.5 1.5 0 0 1 10.5 9h3A1.5 1.5 0 0 1 15 10.5v3A1.5 1.5 0 0 1 13.5 15h-3A1.5 1.5 0 0 1 9 13.5v-3z",
            label = "Projeto"
        ) { onNewProject?.invoke() })

        // Undo
        undoBtn = buildActionBtn(
            svgPath = "M11 5.5a5.5 5.5 0 0 1-5.5 5.5H3.25a.75.75 0 0 1 0-1.5H5.5a4 4 0 0 0 0-8H3.75l1.72 1.72a.75.75 0 0 1-1.06 1.06L1.97 1.97a.75.75 0 0 1 0-1.06L4.41.47a.75.75 0 0 1 1.06 1.06L3.75 3.25H5.5A5.5 5.5 0 0 1 11 5.5z",
            label = "Undo"
        ) { onUndo?.invoke() }
        scrollInner.addView(undoBtn)

        // Redo
        redoBtn = buildActionBtn(
            svgPath = "M5 5.5A5.5 5.5 0 0 1 10.5 0H12.25l-1.72-1.72a.75.75 0 0 1 1.06-1.06l2.44 2.44a.75.75 0 0 1 0 1.06L11.59 3.16a.75.75 0 0 1-1.06-1.06L12.25 3.5H10.5a4 4 0 0 0 0 8H12.25a.75.75 0 0 1 0 1.5H10.5A5.5 5.5 0 0 1 5 5.5z",
            label = "Redo"
        ) { onRedo?.invoke() }
        scrollInner.addView(redoBtn)

        // Search — magnifier
        scrollInner.addView(buildActionBtn(
            svgPath = "M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398l3.85 3.85a1 1 0 0 0 1.415-1.415l-3.85-3.85zm-5.242 1.156a5.5 5.5 0 1 1 0-11 5.5 5.5 0 0 1 0 11z",
            label = "Procurar"
        ) { onSearch?.invoke() })

        // Preview — eye
        previewBtn = buildActionBtn(
            svgPath = "M10.5 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM0 8s3-5.5 8-5.5S16 8 16 8s-3 5.5-8 5.5S0 8 0 8zm8 3.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
            label = "Preview",
            accentColor = true
        ) { onPreview?.invoke() }
        scrollInner.addView(previewBtn)

        // Repo label
        repoLabel = TextView(context).apply {
            text = EditorState.activeRepo.ownerRepo
            textSize = 11f
            setTextColor(Color.parseColor("#555558"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(dp(12), 0, dp(12), 0)
        }
        scrollInner.addView(repoLabel, LayoutParams(dp(130), WRAP_CONTENT))

        scroll.addView(scrollInner)
        topRow.addView(scroll, LayoutParams(0, dp(40), 1f))

        // Push button — arrow up + bar (fixed right)
        pushBtn = buildPushBtn()
        topRow.addView(pushBtn, LayoutParams(WRAP_CONTENT, dp(40)))

        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        // ── Status bar ────────────────────────────────────────────────────
        statusBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#007acc"))
            setPadding(dp(10), 0, dp(10), 0)
        }

        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4ec9b0"))
            }
            layoutParams = LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(6) }
        }

        statusLabel = TextView(context).apply {
            text = "Pronto"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
        }

        statusBar.addView(statusDot)
        statusBar.addView(statusLabel)

        // Spacer
        statusBar.addView(View(context), LayoutParams(0, 1, 1f))

        addView(statusBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(20)))
    }

    // ── Builders ──────────────────────────────────────────────────────────

    private fun buildHamburgerBtn(): LinearLayout {
        val btn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onDrawerToggle?.invoke() }
        }
        repeat(3) {
            val line = View(context).apply {
                setBackgroundColor(Color.parseColor("#cccccc"))
                val lp = LayoutParams(dp(18), dp(2))
                lp.topMargin = if (it > 0) dp(3) else 0
                layoutParams = lp
            }
            btn.addView(line)
        }
        return btn
    }

    private fun buildActionBtn(
        svgPath: String,
        label: String,
        accentColor: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val btn = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(8), 0, dp(8), 0)
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(WRAP_CONTENT, dp(40))
        }

        val iconColor = if (accentColor) "#0e7af0" else "#cccccc"

        // SVG via android.graphics.drawable / Paint - use canvas-drawn ImageView
        val iconView = SvgIconView(context, svgPath, Color.parseColor(iconColor), dp(14))
        btn.addView(iconView, LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(4) })

        val labelView = TextView(context).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.parseColor(iconColor))
        }
        btn.addView(labelView)
        return btn
    }

    private fun buildPushBtn(): LinearLayout {
        val btn = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.parseColor("#0e7af0"))
            setPadding(dp(14), 0, dp(14), 0)
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#33000000")), null, null
            )
            setOnClickListener { onPush?.invoke() }
            layoutParams = LayoutParams(WRAP_CONTENT, dp(40))
        }
        // Push icon — arrow pointing UP with base line
        val iconView = SvgIconView(
            context,
            "M8 2.75a.75.75 0 0 1 .75.75v9.69l2.22-2.22a.75.75 0 0 1 1.06 1.06l-3.5 3.5a.75.75 0 0 1-1.06 0l-3.5-3.5a.75.75 0 0 1 1.06-1.06L7.25 13.19V3.5A.75.75 0 0 1 8 2.75zM2.75 14.5a.75.75 0 0 1 .75-.75h9a.75.75 0 0 1 0 1.5h-9a.75.75 0 0 1-.75-.75z",
            Color.WHITE, dp(14)
        )
        btn.addView(iconView, LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(6) })
        val label = TextView(context).apply {
            text = "Push"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        btn.addView(label)
        return btn
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun setStatus(status: Status, msg: String? = null) {
        val (dotColor, text) = when (status) {
            Status.OK -> Pair("#4ec9b0", msg ?: "Pronto")
            Status.BUSY -> Pair("#e2c08d", msg ?: "A trabalhar...")
            Status.ERROR -> Pair("#f44747", msg ?: "Erro")
        }
        (statusDot.background as? GradientDrawable)?.setColor(Color.parseColor(dotColor))
        statusLabel.text = text
        if (status == Status.BUSY) {
            statusDot.animate().alpha(0.2f).setDuration(500).withEndAction {
                statusDot.animate().alpha(1f).setDuration(500).withEndAction {
                    if (status == Status.BUSY) setStatus(status, msg)
                }.start()
            }.start()
        } else {
            statusDot.animate().cancel()
            statusDot.alpha = 1f
        }
    }

    fun updateForFile(path: String?) {
        val isPreviewable = path != null && path.substringAfterLast('.', "").lowercase() in
                setOf("html", "htm", "svg", "md", "css")
        previewBtn.visibility = if (isPreviewable) VISIBLE else GONE
        val dirty = path != null && (EditorState.openFiles[path]?.dirty == true ||
                EditorState.openFiles[path]?.isNew == true)
        // Could update staged count here
    }

    fun updateUndoRedo(canUndo: Boolean, canRedo: Boolean) {
        undoBtn.alpha = if (canUndo) 1f else 0.3f
        undoBtn.isEnabled = canUndo
        redoBtn.alpha = if (canRedo) 1f else 0.3f
        redoBtn.isEnabled = canRedo
    }

    fun setRepoName(name: String) { repoLabel.text = name }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val bg = if (isDark) Color.parseColor("#252526") else Color.WHITE
        val textColor = if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333")
        (getChildAt(0) as? LinearLayout)?.setBackgroundColor(bg)
        repoLabel.setTextColor(if (isDark) Color.parseColor("#555558") else Color.parseColor("#999999"))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ── Minimal SVG path renderer ─────────────────────────────────────────────
class SvgIconView(context: Context, private val pathData: String, private val color: Int, private val sizePx: Int) :
    View(context) {

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@SvgIconView.color
        style = android.graphics.Paint.Style.FILL
    }
    private var parsedPath: android.graphics.Path? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        parsedPath = parseSvgPath(pathData, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        parsedPath?.let { canvas.drawPath(it, paint) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    private fun parseSvgPath(d: String, w: Float, h: Float): android.graphics.Path {
        val path = android.graphics.Path()
        val scale = minOf(w, h) / 16f
        try {
            android.graphics.PathParser.createPathFromPathData(d)?.let { parsed ->
                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)
                parsed.transform(matrix)
                path.addPath(parsed)
            }
        } catch (e: Exception) { /* fallback empty path */ }
        return path
    }
}