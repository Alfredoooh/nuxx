package com.xcode.app.editor

import android.content.Context
import android.graphics.*
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
    private lateinit var undoBtn: LinearLayout
    private lateinit var redoBtn: LinearLayout
    private lateinit var previewBtn: LinearLayout
    private var isDark = true

    init {
        orientation = VERTICAL
        buildBar()
    }

    private fun buildBar() {
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
        }

        // Hamburger (fixed left)
        topRow.addView(buildHamburgerBtn(), LayoutParams(dp(48), dp(40)))

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

        scrollInner.addView(buildActionBtn(IconPaths.ARROW_DOWN, "Pull") { onPull?.invoke() })
        scrollInner.addView(buildActionBtn(IconPaths.PLUS, "Novo") { onNewFile?.invoke() })
        scrollInner.addView(buildActionBtn(IconPaths.GRID, "Projeto") { onNewProject?.invoke() })

        undoBtn = buildActionBtn(IconPaths.UNDO, "Undo") { onUndo?.invoke() }
        scrollInner.addView(undoBtn)

        redoBtn = buildActionBtn(IconPaths.REDO, "Redo") { onRedo?.invoke() }
        scrollInner.addView(redoBtn)

        scrollInner.addView(buildActionBtn(IconPaths.SEARCH, "Procurar") { onSearch?.invoke() })

        previewBtn = buildActionBtn(IconPaths.EYE, "Preview", accentColor = true) { onPreview?.invoke() }
        scrollInner.addView(previewBtn)

        repoLabel = TextView(context).apply {
            text = if (EditorState.repos.isNotEmpty()) EditorState.activeRepo.ownerRepo else "XCode"
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

        // Push (fixed right)
        topRow.addView(buildPushBtn(), LayoutParams(WRAP_CONTENT, dp(40)))

        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        // Status bar
        val statusRow = LinearLayout(context).apply {
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
        statusRow.addView(statusDot)
        statusRow.addView(statusLabel)
        statusRow.addView(View(context), LayoutParams(0, 1, 1f))
        addView(statusRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(20)))
    }

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
        repeat(3) { i ->
            btn.addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#cccccc"))
                val lp = LayoutParams(dp(18), dp(2))
                if (i > 0) lp.topMargin = dp(3)
                layoutParams = lp
            })
        }
        return btn
    }

    private fun buildActionBtn(
        iconPath: String,
        label: String,
        accentColor: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val col = if (accentColor) "#0e7af0" else "#cccccc"
        val btn = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(9), 0, dp(9), 0)
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            layoutParams = LayoutParams(WRAP_CONTENT, dp(40))
            setOnClickListener { onClick() }
        }
        val icon = XCodeIcon(context, iconPath, Color.parseColor(col), dp(14))
        btn.addView(icon, LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(4) })
        btn.addView(TextView(context).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.parseColor(col))
        })
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
        val icon = XCodeIcon(context, IconPaths.ARROW_UP, Color.WHITE, dp(14))
        btn.addView(icon, LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(6) })
        btn.addView(TextView(context).apply {
            text = "Push"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        return btn
    }

    fun setStatus(status: Status, msg: String? = null) {
        val dotColor: String
        val text: String
        when (status) {
            Status.OK    -> { dotColor = "#4ec9b0"; text = msg ?: "Pronto" }
            Status.BUSY  -> { dotColor = "#e2c08d"; text = msg ?: "A trabalhar..." }
            Status.ERROR -> { dotColor = "#f44747"; text = msg ?: "Erro" }
        }
        (statusDot.background as? GradientDrawable)?.setColor(Color.parseColor(dotColor))
        statusLabel.text = text
    }

    fun updateForFile(path: String?) {
        val ext = path?.substringAfterLast('.', "")?.lowercase() ?: ""
        previewBtn.visibility = if (ext in setOf("html","htm","svg","md","css")) VISIBLE else GONE
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
        (getChildAt(0) as? LinearLayout)?.setBackgroundColor(bg)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ═══════════════════════════════════════════════════════════════════════════
// Icon path constants — Bootstrap Icons viewBox 0 0 16 16
// ═══════════════════════════════════════════════════════════════════════════
object IconPaths {
    const val ARROW_DOWN  = "M8 1a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L7.5 13.293V1.5A.5.5 0 0 1 8 1z"
    const val ARROW_UP    = "M8 15a.5.5 0 0 0 .5-.5V2.707l3.146 3.147a.5.5 0 0 0 .708-.708l-4-4a.5.5 0 0 0-.708 0l-4 4a.5.5 0 0 0 .708.708L7.5 2.707V14.5A.5.5 0 0 0 8 15z"
    const val PLUS        = "M8 4a.5.5 0 0 1 .5.5v3h3a.5.5 0 0 1 0 1h-3v3a.5.5 0 0 1-1 0v-3h-3a.5.5 0 0 1 0-1h3v-3A.5.5 0 0 1 8 4z"
    const val GRID        = "M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5v-3zm8 0A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5v-3zm-8 8A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5v-3zm8 0A1.5 1.5 0 0 1 10.5 9h3A1.5 1.5 0 0 1 15 10.5v3A1.5 1.5 0 0 1 13.5 15h-3A1.5 1.5 0 0 1 9 13.5v-3z"
    const val UNDO        = "M8 3a5 5 0 1 0 4.546 2.914.5.5 0 0 1 .908-.417A6 6 0 1 1 8 2v1z M8 4.466V.534a.25.25 0 0 1 .41-.192l2.36 1.966c.12.1.12.284 0 .384L8.41 4.658A.25.25 0 0 1 8 4.466z"
    const val REDO        = "M8 3a5 5 0 1 1-4.546 2.914.5.5 0 0 0-.908-.417A6 6 0 1 0 8 2v1z M8 4.466V.534a.25.25 0 0 0-.41-.192L5.23 2.308c-.12.1-.12.284 0 .384l2.36 1.966A.25.25 0 0 0 8 4.466z"
    const val SEARCH      = "M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398l3.85 3.85a1 1 0 0 0 1.415-1.415l-3.85-3.85zm-5.242 1.156a5.5 5.5 0 1 1 0-11 5.5 5.5 0 0 1 0 11z"
    const val EYE         = "M10.5 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z M0 8s3-5.5 8-5.5S16 8 16 8s-3 5.5-8 5.5S0 8 0 8zm8 3.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z"
    const val BRANCH      = "M11.75 2.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zm-2.25.75a2.25 2.25 0 1 1 3 2.122V6A2.5 2.5 0 0 1 10 8.5H6a1 1 0 0 0-1 1v1.128a2.251 2.251 0 1 1-1.5 0V5.372a2.25 2.25 0 1 1 1.5 0v1.836A2.492 2.492 0 0 1 6 7h4a1 1 0 0 0 1-1v-.628A2.25 2.25 0 0 1 9.5 3.25zM4.25 12a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zM3.5 3.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0z"
    const val HOME        = "M8.354 1.146a.5.5 0 0 0-.708 0l-6 6A.5.5 0 0 0 1.5 7.5v7a.5.5 0 0 0 .5.5h4.5a.5.5 0 0 0 .5-.5v-4h2v4a.5.5 0 0 0 .5.5H14a.5.5 0 0 0 .5-.5v-7a.5.5 0 0 0-.146-.354L13 5.793V2.5a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5v1.293L8.354 1.146zM2.5 14V7.707l5.5-5.5 5.5 5.5V14H10v-4a.5.5 0 0 0-.5-.5h-3a.5.5 0 0 0-.5.5v4H2.5z"
    const val SETTINGS    = "M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.858z"
    const val FOLDER_OPEN = "M1 3.5A1.5 1.5 0 0 1 2.5 2h2.764c.958 0 1.76.56 2.311 1.184C7.985 3.648 8.48 4 9 4h4.5A1.5 1.5 0 0 1 15 5.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 12.5v-9z"
    const val FOLDER_CLOSED = "M.54 3.87.5 3a2 2 0 0 1 2-2h3.672a2 2 0 0 1 1.414.586l.828.828A2 2 0 0 0 9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a1.99 1.99 0 0 1 .342-1.31z"
    const val CHEVRON_RIGHT = "M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708z"
    const val CHEVRON_DOWN  = "M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708z"
    const val CHEVRON_LEFT  = "M11.354 1.646a.5.5 0 0 1 0 .708L5.707 8l5.647 5.646a.5.5 0 0 1-.708.708l-6-6a.5.5 0 0 1 0-.708l6-6a.5.5 0 0 1 .708 0z"
    const val FILE          = "M4 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4.5L9.5 0H4zm0 1h5v4h4v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1z"
    const val CLOSE         = "M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z"
    const val RELOAD        = "M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9z M8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z"
    const val UPLOAD        = "M.5 9.9a.5.5 0 0 1 .5.5v2.5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2.5a.5.5 0 0 1 1 0v2.5a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2v-2.5a.5.5 0 0 1 .5-.5z M7.646 1.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1-.708.708L8.5 2.707V11.5a.5.5 0 0 1-1 0V2.707L5.354 4.854a.5.5 0 1 1-.708-.708l3-3z"
    const val MOON          = "M6 .278a.768.768 0 0 1 .08.858 7.208 7.208 0 0 0-.878 3.46c0 4.021 3.278 7.277 7.318 7.277.527 0 1.04-.055 1.533-.16a.787.787 0 0 1 .81.316.733.733 0 0 1-.031.893A8.349 8.349 0 0 1 8.344 16C3.734 16 0 12.286 0 7.71 0 4.266 2.114 1.312 5.124.06A.752.752 0 0 1 6 .278z"
    const val TRASH         = "M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5zm3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0V6z M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1v1z"
    const val RENAME        = "M12.146.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1 0 .708l-10 10a.5.5 0 0 1-.168.11l-5 2a.5.5 0 0 1-.65-.65l2-5a.5.5 0 0 1 .11-.168l10-10zM11.207 2.5 13.5 4.793 14.793 3.5 12.5 1.207 11.207 2.5zm1.586 3L10.5 3.207 4 9.707V10h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.293l6.5-6.5zm-9.761 5.175-.106.106-1.528 3.821 3.821-1.528.106-.106A.5.5 0 0 1 5 12.5V12h-.5a.5.5 0 0 1-.5-.5V11h-.5a.5.5 0 0 1-.468-.325z"
    const val DUPLICATE     = "M4 2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V2zm2-1a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H6zM2 5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-1h1v1a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1v1H2z"
    const val NEW_FILE      = "M8 6.5a.5.5 0 0 1 .5.5v1.5H10a.5.5 0 0 1 0 1H8.5V11a.5.5 0 0 1-1 0V9.5H6a.5.5 0 0 1 0-1h1.5V7a.5.5 0 0 1 .5-.5z M14 4.5V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2a2 2 0 0 1 2-2h5.5L14 4.5zm-3 0A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V4.5h-2z"
    const val NEW_FOLDER    = "M9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a1.99 1.99 0 0 1 .342-1.31L.5 3a2 2 0 0 1 2-2h3.672a2 2 0 0 1 1.414.586l.828.828A2 2 0 0 0 9.828 3zm-8.322.12C1.72 3.042 1.95 3 2.19 3h5.396l-.707-.707A1 1 0 0 0 6.172 2H2.5a1 1 0 0 0-1 .981l.006.139z M8 10a.5.5 0 0 1 .5.5V12H10a.5.5 0 0 1 0 1H8.5v1.5a.5.5 0 0 1-1 0V13H6a.5.5 0 0 1 0-1h1.5v-1.5A.5.5 0 0 1 8 10z"
}

// ═══════════════════════════════════════════════════════════════════════════
// XCodeIcon — renders SVG path data on Canvas without PathParser
// ═══════════════════════════════════════════════════════════════════════════
class XCodeIcon(
    context: Context,
    private var pathData: String,
    private var iconColor: Int,
    private val sizePx: Int
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@XCodeIcon.iconColor
        style = Paint.Style.FILL
    }
    private var cachedPath = Path()
    private var cachedW = 0f
    private var cachedH = 0f

    fun updatePath(newPath: String) {
        pathData = newPath
        rebuildPath()
        invalidate()
    }

    fun updatePathAndColor(newPath: String, newColor: Int) {
        pathData = newPath
        iconColor = newColor
        paint.color = newColor
        rebuildPath()
        invalidate()
    }

    fun updateColor(newColor: Int) {
        iconColor = newColor
        paint.color = newColor
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedW = w.toFloat()
        cachedH = h.toFloat()
        rebuildPath()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(cachedPath, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    private fun rebuildPath() {
        if (cachedW <= 0f || cachedH <= 0f) return
        cachedPath = parseSvgPath(pathData, cachedW, cachedH)
    }

    companion object {

        fun parseSvgPath(d: String, viewW: Float, viewH: Float): Path {
            val path = Path()
            val sx = viewW / 16f
            val sy = viewH / 16f

            // Tokenise into commands and number-strings
            val commands = mutableListOf<Pair<Char, FloatArray>>()
            val cmdRegex = Regex("([MmLlHhVvCcSsQqTtAaZz])")
            val parts = cmdRegex.split(d.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            val cmdChars = cmdRegex.findAll(d.trim()).map { it.value[0] }.toList()

            cmdChars.forEachIndexed { i, ch ->
                val numStr = parts.getOrElse(i) { "" }
                val nums = parseNums(numStr)
                commands.add(Pair(ch, nums))
            }

            var cx = 0f; var cy = 0f
            var lastCtrlX = cx; var lastCtrlY = cy
            var lastCmd = ' '
            var startX = 0f; var startY = 0f

            for ((cmd, nums) in commands) {
                val rel = cmd.isLowerCase()
                val upper = cmd.uppercaseChar()

                fun ax(v: Float) = if (rel) cx + v * sx else v * sx
                fun ay(v: Float) = if (rel) cy + v * sy else v * sy

                when (upper) {
                    'M' -> {
                        var k = 0
                        while (k + 1 < nums.size) {
                            val nx = ax(nums[k]); val ny = ay(nums[k + 1])
                            if (k == 0) { path.moveTo(nx, ny); startX = nx; startY = ny }
                            else path.lineTo(nx, ny)
                            cx = nx; cy = ny; k += 2
                        }
                    }
                    'L' -> {
                        var k = 0
                        while (k + 1 < nums.size) {
                            val nx = ax(nums[k]); val ny = ay(nums[k + 1])
                            path.lineTo(nx, ny); cx = nx; cy = ny; k += 2
                        }
                    }
                    'H' -> {
                        nums.forEach { v ->
                            val nx = if (rel) cx + v * sx else v * sx
                            path.lineTo(nx, cy); cx = nx
                        }
                    }
                    'V' -> {
                        nums.forEach { v ->
                            val ny = if (rel) cy + v * sy else v * sy
                            path.lineTo(cx, ny); cy = ny
                        }
                    }
                    'C' -> {
                        var k = 0
                        while (k + 5 < nums.size) {
                            val x1 = ax(nums[k]);   val y1 = ay(nums[k + 1])
                            val x2 = ax(nums[k + 2]); val y2 = ay(nums[k + 3])
                            val nx = ax(nums[k + 4]); val ny = ay(nums[k + 5])
                            path.cubicTo(x1, y1, x2, y2, nx, ny)
                            lastCtrlX = x2; lastCtrlY = y2
                            cx = nx; cy = ny; k += 6
                        }
                    }
                    'S' -> {
                        var k = 0
                        while (k + 3 < nums.size) {
                            val x1 = if (lastCmd in listOf('C', 'c', 'S', 's'))
                                2f * cx - lastCtrlX else cx
                            val y1 = if (lastCmd in listOf('C', 'c', 'S', 's'))
                                2f * cy - lastCtrlY else cy
                            val x2 = ax(nums[k]);   val y2 = ay(nums[k + 1])
                            val nx = ax(nums[k + 2]); val ny = ay(nums[k + 3])
                            path.cubicTo(x1, y1, x2, y2, nx, ny)
                            lastCtrlX = x2; lastCtrlY = y2
                            cx = nx; cy = ny; k += 4
                        }
                    }
                    'Q' -> {
                        var k = 0
                        while (k + 3 < nums.size) {
                            val x1 = ax(nums[k]);   val y1 = ay(nums[k + 1])
                            val nx = ax(nums[k + 2]); val ny = ay(nums[k + 3])
                            path.quadTo(x1, y1, nx, ny)
                            lastCtrlX = x1; lastCtrlY = y1
                            cx = nx; cy = ny; k += 4
                        }
                    }
                    'T' -> {
                        var k = 0
                        while (k + 1 < nums.size) {
                            val x1 = if (lastCmd in listOf('Q','q','T','t'))
                                2f * cx - lastCtrlX else cx
                            val y1 = if (lastCmd in listOf('Q','q','T','t'))
                                2f * cy - lastCtrlY else cy
                            val nx = ax(nums[k]); val ny = ay(nums[k + 1])
                            path.quadTo(x1, y1, nx, ny)
                            lastCtrlX = x1; lastCtrlY = y1
                            cx = nx; cy = ny; k += 2
                        }
                    }
                    'A' -> {
                        // Simplified: treat as line to endpoint
                        var k = 0
                        while (k + 6 < nums.size) {
                            val nx = ax(nums[k + 5]); val ny = ay(nums[k + 6])
                            path.lineTo(nx, ny); cx = nx; cy = ny; k += 7
                        }
                    }
                    'Z' -> {
                        path.close()
                        cx = startX; cy = startY
                    }
                }
                lastCmd = upper
            }
            return path
        }

        private fun parseNums(s: String): FloatArray {
            if (s.isBlank()) return floatArrayOf()
            val result = mutableListOf<Float>()
            val regex = Regex("-?\\d*\\.?\\d+(?:[eE][+-]?\\d+)?")
            regex.findAll(s).forEach {
                it.value.toFloatOrNull()?.let { f -> result.add(f) }
            }
            return result.toFloatArray()
        }
    }
}