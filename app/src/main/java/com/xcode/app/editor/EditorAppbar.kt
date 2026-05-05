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

    private lateinit var repoNameView: TextView
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var dirtyIndicator: View
    private lateinit var scrollableActions: HorizontalScrollView
    private lateinit var pushBtn: TextView
    private var isDark = true

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        elevation = 4f
        buildBar()
    }

    private fun buildBar() {
        // ── Drawer button (fixed left) ────────────────────────────────────
        val drawerBtn = makeIconTextBtn(
            icon = drawerIcon(),
            label = null,
            widthDp = 48
        ) { onDrawerToggle?.invoke() }
        addView(drawerBtn, LayoutParams(dp(48), dp(40)))

        // ── Scrollable middle section ─────────────────────────────────────
        scrollableActions = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        val scrollInner = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Pull
        scrollInner.addView(makeIconTextBtn(pullIcon(), "Pull") { onPull?.invoke() })
        // Novo
        scrollInner.addView(makeIconTextBtn(newFileIcon(), "Novo") { onNewFile?.invoke() })
        // Projeto
        scrollInner.addView(makeIconTextBtn(projectIcon(), "Projeto") { onNewProject?.invoke() })
        // Undo
        scrollInner.addView(makeIconTextBtn(undoIcon(), "Undo") { onUndo?.invoke() })
        // Redo
        scrollInner.addView(makeIconTextBtn(redoIcon(), "Redo") { onRedo?.invoke() })
        // Procurar
        scrollInner.addView(makeIconTextBtn(searchIcon(), "Procurar") { onSearch?.invoke() })
        // Preview
        scrollInner.addView(makeIconTextBtn(previewIcon(), "Preview") { onPreview?.invoke() })

        // Repo name (center, in scroll)
        repoNameView = TextView(context).apply {
            text = "XCode Editor"
            textSize = 11f
            setTextColor(Color.parseColor("#858585"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        scrollInner.addView(repoNameView, LayoutParams(dp(120), WRAP_CONTENT))

        scrollableActions.addView(scrollInner)
        addView(scrollableActions, LayoutParams(0, dp(40), 1f))

        // ── Push button (fixed right) ─────────────────────────────────────
        pushBtn = TextView(context).apply {
            text = "↑  Push"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.parseColor("#0e7af0"))
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#330e7af0"))
            foreground = RippleDrawable(ripple, null, null)
            setOnClickListener { onPush?.invoke() }
        }
        addView(pushBtn, LayoutParams(WRAP_CONTENT, dp(40)))

        // Status bar at bottom — embed in a wrapper
        buildStatusBar()
    }

    private fun buildStatusBar() {
        // The status bar is a separate row below the toolbar.
        // We add it as part of the parent EditorActivity layout,
        // but expose update methods here.
        // Actual status bar is inside EditorActivity's root column.
    }

    private fun makeIconTextBtn(
        icon: String,
        label: String?,
        widthDp: Int? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val btn = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val h = dp(40)
            val w = if (widthDp != null) dp(widthDp) else WRAP_CONTENT
            layoutParams = LayoutParams(w, h)
            if (widthDp == null) setPadding(dp(10), 0, dp(10), 0)
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff"))
            foreground = RippleDrawable(ripple, null, null)
            setOnClickListener { onClick() }
        }
        val iconView = TextView(context).apply {
            text = icon
            textSize = 14f
            setTextColor(Color.parseColor("#cccccc"))
            gravity = Gravity.CENTER
        }
        btn.addView(iconView)
        if (label != null) {
            val labelView = TextView(context).apply {
                text = label
                textSize = 11.5f
                setTextColor(Color.parseColor("#cccccc"))
                setPadding(dp(4), 0, 0, 0)
                gravity = Gravity.CENTER
            }
            btn.addView(labelView)
        }
        return btn
    }

    fun setStatus(status: Status, msg: String? = null) {
        // Notify EditorActivity status bar
    }

    fun updateDirtyIndicator(dirty: Boolean) { }

    fun updateForFile(path: String?) {
        // Update preview button visibility etc.
    }

    fun setRepoName(name: String) {
        repoNameView.text = name
    }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val bg = if (isDark) Color.parseColor("#252526") else Color.WHITE
        val border = if (isDark) Color.parseColor("#333336") else Color.parseColor("#e0e0e0")
        setBackgroundColor(bg)
    }

    // ── Unicode icons ─────────────────────────────────────────────────────
    private fun drawerIcon() = "≡"
    private fun pullIcon() = "↓"
    private fun newFileIcon() = "+"
    private fun projectIcon() = "⊞"
    private fun undoIcon() = "↩"
    private fun redoIcon() = "↪"
    private fun searchIcon() = "⌕"
    private fun previewIcon() = "◉"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}