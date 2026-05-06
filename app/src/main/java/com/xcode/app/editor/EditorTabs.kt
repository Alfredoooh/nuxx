package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class EditorTabs(context: Context) : HorizontalScrollView(context) {

    var onTabSelected: ((String) -> Unit)? = null
    var onTabClosed: ((String) -> Unit)? = null

    private val inner = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val tabs = linkedMapOf<String, LinearLayout>()
    private var activeTab: String? = null
    private var isDark = true

    companion object {
        private val DI = "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/"
        private val EXT_MAP = mapOf(
            "html"   to "html5/html5-original.svg",
            "htm"    to "html5/html5-original.svg",
            "css"    to "css3/css3-original.svg",
            "scss"   to "sass/sass-original.svg",
            "js"     to "javascript/javascript-original.svg",
            "mjs"    to "javascript/javascript-original.svg",
            "ts"     to "typescript/typescript-original.svg",
            "tsx"    to "react/react-original.svg",
            "jsx"    to "react/react-original.svg",
            "dart"   to "dart/dart-original.svg",
            "kt"     to "kotlin/kotlin-original.svg",
            "kts"    to "kotlin/kotlin-original.svg",
            "java"   to "java/java-original.svg",
            "py"     to "python/python-original.svg",
            "go"     to "go/go-original-wordmark.svg",
            "rs"     to "rust/rust-original.svg",
            "swift"  to "swift/swift-original.svg",
            "json"   to "json/json-original.svg",
            "xml"    to "xml/xml-original.svg",
            "yaml"   to "yaml/yaml-original.svg",
            "yml"    to "yaml/yaml-original.svg",
            "md"     to "markdown/markdown-original.svg",
            "sh"     to "bash/bash-original.svg",
            "bash"   to "bash/bash-original.svg",
            "gradle" to "gradle/gradle-original.svg",
            "cpp"    to "cplusplus/cplusplus-original.svg",
            "c"      to "c/c-original.svg",
            "cs"     to "csharp/csharp-original.svg",
            "rb"     to "ruby/ruby-original.svg",
            "php"    to "php/php-original.svg",
            "vue"    to "vuejs/vuejs-original.svg",
            "svelte" to "svelte/svelte-original.svg"
        )
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(Color.parseColor("#2d2d30"))
        addView(inner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
        minimumHeight = dp(35)
    }

    fun addTab(path: String) {
        if (tabs.containsKey(path)) return
        val tab = buildTab(path)
        tabs[path] = tab
        inner.addView(tab)
        visibility = VISIBLE
    }

    private fun buildTab(path: String): LinearLayout {
        val name = path.substringAfterLast('/')
        val ext  = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""

        val tab = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onTabSelected?.invoke(path) }
        }

        val iconUrl = EXT_MAP[ext]?.let { DI + it }
        if (iconUrl != null) {
            val imgLp = LinearLayout.LayoutParams(dp(14), dp(14))
            imgLp.setMargins(0, 0, dp(6), 0)
            val imgView = ImageView(context).apply {
                layoutParams = imgLp
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            Glide.with(context)
                .load(iconUrl)
                .apply(RequestOptions().override(dp(14), dp(14)))
                .into(imgView)
            tab.addView(imgView)
        } else {
            val genericIconLp = LinearLayout.LayoutParams(dp(14), dp(14))
            genericIconLp.setMargins(0, 0, dp(6), 0)
            val genericIcon = XCodeIcon(context, IconPaths.FILE, Color.parseColor("#858585"), dp(14))
            tab.addView(genericIcon, genericIconLp)
        }

        val nameView = TextView(context).apply {
            text = name
            textSize = 12f
            setTextColor(Color.parseColor("#858585"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        tab.addView(nameView)

        val dirtyDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#e2c08d"))
            }
            val dotLp = LinearLayout.LayoutParams(dp(6), dp(6))
            dotLp.setMargins(dp(5), 0, 0, 0)
            layoutParams = dotLp
            visibility = GONE
            tag = "dirty_$path"
        }
        tab.addView(dirtyDot)

        val closeFrame = FrameLayout(context).apply {
            val sz = dp(16)
            val closeLp = LinearLayout.LayoutParams(sz, sz)
            closeLp.setMargins(dp(5), 0, 0, 0)
            layoutParams = closeLp
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onTabClosed?.invoke(path) }
        }
        val closeIcon = XCodeIcon(context, IconPaths.CLOSE, Color.parseColor("#858585"), dp(10))
        closeFrame.addView(closeIcon, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER))
        tab.addView(closeFrame)

        tab.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            val sepLp = LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT)
            sepLp.setMargins(dp(8), 0, 0, 0)
            layoutParams = sepLp
        })

        return tab
    }

    fun setActive(path: String) {
        activeTab = path
        tabs.forEach { (p, tab) ->
            val active = p == path
            val bg = when {
                active -> if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
                else   -> if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#ececec")
            }
            tab.setBackgroundColor(bg)
            val nameView = tab.getChildAt(if (tab.getChildAt(0) is ImageView ||
                    tab.getChildAt(0) is XCodeIcon) 1 else 0) as? TextView
            nameView?.setTextColor(
                if (active) (if (isDark) Color.WHITE else Color.BLACK)
                else Color.parseColor("#858585")
            )
        }
        tabs[path]?.let { tab -> post { smoothScrollTo(tab.left, 0) } }
    }

    fun markDirty(path: String, dirty: Boolean) {
        tabs[path]?.findViewWithTag<View>("dirty_$path")?.visibility =
            if (dirty) View.VISIBLE else View.GONE
    }

    fun removeTab(path: String) {
        val tab = tabs.remove(path) ?: return
        inner.removeView(tab)
        if (tabs.isEmpty()) visibility = GONE
    }

    fun renameTab(oldPath: String, newPath: String) {
        val tab = tabs.remove(oldPath) ?: return
        val nameView = tab.getChildAt(
            if (tab.getChildAt(0) is ImageView || tab.getChildAt(0) is XCodeIcon) 1 else 0
        ) as? TextView
        nameView?.text = newPath.substringAfterLast('/')
        tabs[newPath] = tab
        if (activeTab == oldPath) activeTab = newPath
    }

    fun clearAll() {
        inner.removeAllViews()
        tabs.clear()
        visibility = GONE
    }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        setBackgroundColor(
            if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#ececec")
        )
        activeTab?.let { setActive(it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}