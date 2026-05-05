package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.bumptech.glide.Glide

class EditorTabs(context: Context) : HorizontalScrollView(context) {

    var onTabSelected: ((String) -> Unit)? = null
    var onTabClosed: ((String) -> Unit)? = null

    private val inner = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    private val tabs = mutableMapOf<String, View>()
    private var activeTab: String? = null
    private var isDark = true

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(inner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        minimumHeight = dp(35)
        setBackgroundColor(Color.parseColor("#2d2d30"))
        visibility = if (tabs.isEmpty()) GONE else VISIBLE
    }

    fun addTab(path: String) {
        if (tabs.containsKey(path)) return
        val name = path.substringAfterLast('/')
        val tab = buildTab(path, name)
        tabs[path] = tab
        inner.addView(tab)
        visibility = VISIBLE
    }

    private fun buildTab(path: String, name: String): LinearLayout {
        val tab = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            minimumWidth = dp(110)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            isClickable = true
            isFocusable = true
            tag = "tab_$path"
        }

        // File icon
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(5) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconUrl = getIconUrl(name)
        if (iconUrl != null) Glide.with(context).load(iconUrl).into(iconView)
        tab.addView(iconView)

        // Name
        val nameView = TextView(context).apply {
            text = name
            textSize = 12f
            setTextColor(Color.parseColor("#858585"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        tab.addView(nameView)

        // Dirty dot (hidden by default)
        val dirtyDot = View(context).apply {
            setBackgroundColor(Color.parseColor("#e2c08d"))
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginStart = dp(4) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#e2c08d"))
            }
            visibility = GONE
            tag = "dirty_$path"
        }
        tab.addView(dirtyDot)

        // Close button
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 10f
            setTextColor(Color.parseColor("#858585"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(4) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onTabClosed?.invoke(path) }
        }
        tab.addView(closeBtn)

        // Separator
        val sep = View(context).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT)
        }

        tab.setOnClickListener { onTabSelected?.invoke(path) }

        // Wrap in container with right border
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        wrapper.addView(tab, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        wrapper.addView(sep, LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT))

        return tab
    }

    fun setActive(path: String) {
        activeTab = path
        tabs.forEach { (p, tab) ->
            val isActive = p == path
            val bg = when {
                isActive -> if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
                else -> if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#ececec")
            }
            tab.setBackgroundColor(bg)
            val nameView = (tab as? LinearLayout)?.getChildAt(1) as? TextView
            nameView?.setTextColor(
                if (isActive) (if (isDark) Color.WHITE else Color.BLACK)
                else Color.parseColor("#858585")
            )
            // Blue top border for active tab
            tab.foreground = if (isActive) {
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    // Top border hack via layer-list would need XML; use setTag for now
                }
                null // Simplified; real accent top border needs XML drawable
            } else null
        }
    }

    fun markDirty(path: String, dirty: Boolean) {
        val tab = tabs[path] as? LinearLayout ?: return
        val dot = tab.findViewWithTag<View>("dirty_$path")
        dot?.visibility = if (dirty) View.VISIBLE else View.GONE
    }

    fun removeTab(path: String) {
        val tab = tabs[path] ?: return
        inner.removeView(tab)
        tabs.remove(path)
        if (tabs.isEmpty()) visibility = GONE
    }

    fun renameTab(oldPath: String, newPath: String) {
        val tab = tabs[oldPath] as? LinearLayout ?: return
        val nameView = tab.getChildAt(1) as? TextView
        nameView?.text = newPath.substringAfterLast('/')
        tabs.remove(oldPath)
        tabs[newPath] = tab
    }

    fun clearAll() {
        inner.removeAllViews()
        tabs.clear()
        visibility = GONE
    }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val bg = if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#ececec")
        setBackgroundColor(bg)
        activeTab?.let { setActive(it) }
    }

    private fun getIconUrl(name: String): String? {
        val DI = "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/"
        val EXT = mapOf(
            "html" to "html5/html5-original.svg","css" to "css3/css3-original.svg",
            "js" to "javascript/javascript-original.svg","ts" to "typescript/typescript-original.svg",
            "tsx" to "react/react-original.svg","jsx" to "react/react-original.svg",
            "dart" to "dart/dart-original.svg","kt" to "kotlin/kotlin-original.svg",
            "java" to "java/java-original.svg","py" to "python/python-original.svg",
            "go" to "go/go-original-wordmark.svg","rs" to "rust/rust-original.svg",
            "json" to "json/json-original.svg","yaml" to "yaml/yaml-original.svg",
            "yml" to "yaml/yaml-original.svg","md" to "markdown/markdown-original.svg"
        )
        val ext = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        return EXT[ext]?.let { DI + it }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}