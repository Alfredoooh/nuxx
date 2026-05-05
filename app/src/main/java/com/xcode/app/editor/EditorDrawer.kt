// EditorDrawer.kt
package com.xcode.app.editor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.bumptech.glide.Glide

class EditorDrawer(private val activity: EditorActivity) : FrameLayout(activity) {

    var onFileSelected: ((String, String) -> Unit)? = null
    var onGoHome: (() -> Unit)? = null
    var onNewFile: ((String) -> Unit)? = null
    var onNewFolder: ((String) -> Unit)? = null
    var onRenameFile: ((String) -> Unit)? = null
    var onRenameFolder: ((String) -> Unit)? = null
    var onDeleteFile: ((String, String) -> Unit)? = null
    var onDeleteFolder: ((String) -> Unit)? = null
    var onBranchChange: ((String) -> Unit)? = null

    private lateinit var overlay: View
    private lateinit var panel: LinearLayout
    private lateinit var treeContainer: LinearLayout
    private lateinit var branchSpinner: Spinner
    private lateinit var repoSpinner: Spinner
    private var open = false
    private val folderState = mutableMapOf<String, Boolean>()
    private var isDark = true

    companion object {
        private const val PANEL_WIDTH_DP = 290
        private val DI = "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/"
        private val EXT_ICONS = mapOf(
            "html" to "html5/html5-original.svg", "htm" to "html5/html5-original.svg",
            "css" to "css3/css3-original.svg", "scss" to "sass/sass-original.svg",
            "js" to "javascript/javascript-original.svg",
            "ts" to "typescript/typescript-original.svg",
            "tsx" to "react/react-original.svg", "jsx" to "react/react-original.svg",
            "dart" to "dart/dart-original.svg",
            "kt" to "kotlin/kotlin-original.svg", "kts" to "kotlin/kotlin-original.svg",
            "java" to "java/java-original.svg", "py" to "python/python-original.svg",
            "go" to "go/go-original-wordmark.svg", "rs" to "rust/rust-original.svg",
            "swift" to "swift/swift-original.svg", "json" to "json/json-original.svg",
            "xml" to "xml/xml-original.svg",
            "yaml" to "yaml/yaml-original.svg", "yml" to "yaml/yaml-original.svg",
            "md" to "markdown/markdown-original.svg", "sh" to "bash/bash-original.svg",
            "gradle" to "gradle/gradle-original.svg"
        )
        private val NAME_ICONS = mapOf(
            "dockerfile" to "docker/docker-original.svg",
            ".gitignore" to "git/git-original.svg",
            "pubspec.yaml" to "dart/dart-original.svg",
            "build.gradle" to "gradle/gradle-original.svg",
            "package.json" to "nodejs/nodejs-original.svg",
            "tsconfig.json" to "typescript/typescript-original.svg"
        )
    }

    init {
        buildDrawer()
    }

    private fun buildDrawer() {
        overlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#88000000"))
            alpha = 0f
            visibility = GONE
            setOnClickListener { close() }
        }
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val panelWidth = dp(PANEL_WIDTH_DP)
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            translationX = -panelWidth.toFloat()
            elevation = 16f
        }
        addView(panel, LayoutParams(panelWidth, LayoutParams.MATCH_PARENT))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }
        val title = TextView(context).apply {
            text = "EXPLORER"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.parseColor("#858585"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        header.addView(makeHeaderBtn("◑") { activity.applyTheme(!EditorState.isDark) })
        header.addView(makeHeaderBtn("↺") { activity.loadTreeOrProject() })
        header.addView(makeHeaderBtn("↑") { activity.triggerUpload() })
        header.addView(makeHeaderBtn("⚙") { activity.openSettings() })
        header.addView(makeHeaderBtn("✕") { close() })
        panel.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))

        panel.addView(makeDivider())

        val repoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        repoSpinner = Spinner(context).apply {
            setPopupBackgroundResource(android.R.color.black)
        }
        repoRow.addView(repoSpinner, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        panel.addView(repoRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT))
        updateRepoSpinner()

        val branchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(6))
        }
        val branchIcon = TextView(context).apply {
            text = "⎇ "
            textSize = 11f
            setTextColor(Color.parseColor("#858585"))
        }
        branchSpinner = Spinner(context).apply {
            setPopupBackgroundResource(android.R.color.black)
        }
        branchRow.addView(branchIcon)
        branchRow.addView(branchSpinner, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        panel.addView(branchRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        panel.addView(makeDivider())

        val scroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        treeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        scroll.addView(treeContainer)
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        panel.addView(makeDivider())

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        footer.addView(makeFooterItem("⌂", "Ir para o Início") { onGoHome?.invoke() })
        panel.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT))
    }

    private fun makeHeaderBtn(icon: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = icon
            textSize = 14f
            setTextColor(Color.parseColor("#858585"))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff"))
            background = RippleDrawable(ripple, null, null)
            setOnClickListener { onClick() }
        }.also {
            val sz = dp(28)
            val lp = LinearLayout.LayoutParams(sz, sz)
            lp.marginStart = dp(2)
            it.layoutParams = lp
        }
    }

    private fun makeFooterItem(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff"))
            foreground = RippleDrawable(ripple, null, null)
            setOnClickListener { onClick() }
        }
        val iconV = TextView(context).apply {
            text = icon
            textSize = 14f
            setTextColor(Color.parseColor("#858585"))
            setPadding(0, 0, dp(10), 0)
        }
        val labelV = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#858585"))
        }
        row.addView(iconV)
        row.addView(labelV)
        return row
    }

    fun renderTree(items: List<GitFile>) {
        treeContainer.removeAllViews()
        val root = buildFileTree(items.filter { it.type == "blob" }.map { it.path } +
                items.filter { it.type == "tree" }.map { it.path + "/" })
        val shaMap = items.associate { it.path to it.sha }
        renderNode(root, treeContainer, 0, "", shaMap)
    }

    fun renderLocalTree(project: LocalProject) {
        treeContainer.removeAllViews()
        val root = buildFileTree(project.files.keys.toList())
        renderNode(root, treeContainer, 0, "", emptyMap())
    }

    private fun buildFileTree(paths: List<String>): Map<String, Any> {
        val root = mutableMapOf<String, Any>()
        paths.forEach { path ->
            val parts = path.trimEnd('/').split('/')
            var node = root
            parts.forEachIndexed { i, part ->
                if (i == parts.size - 1 && !path.endsWith('/')) {
                    @Suppress("UNCHECKED_CAST")
                    (node as MutableMap<String, Any>)[part] = "__file__"
                } else {
                    if (!node.containsKey(part)) {
                        @Suppress("UNCHECKED_CAST")
                        (node as MutableMap<String, Any>)[part] = mutableMapOf<String, Any>()
                    }
                    @Suppress("UNCHECKED_CAST")
                    node = node[part] as MutableMap<String, Any>
                }
            }
        }
        return root
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderNode(
        node: Map<String, Any>,
        container: LinearLayout,
        depth: Int,
        pathPrefix: String,
        shaMap: Map<String, String>
    ) {
        val sorted = node.entries.sortedWith(compareBy(
            { if (it.value == "__file__") 1 else 0 },
            { it.key }
        ))
        sorted.forEach { (name, value) ->
            val fullPath = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
            if (value == "__file__") {
                container.addView(buildFileRow(name, fullPath, shaMap[fullPath] ?: "", depth))
            } else {
                val folderId = fullPath.hashCode().toString()
                val isOpen = folderState[folderId] ?: true
                val folderRow = buildFolderRow(name, fullPath, folderId, isOpen, depth)
                container.addView(folderRow)
                val childContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (isOpen) VISIBLE else GONE
                    tag = "child_$folderId"
                }
                renderNode(value as Map<String, Any>, childContainer, depth + 1, fullPath, shaMap)
                container.addView(childContainer)
            }
        }
    }

    private fun buildFileRow(name: String, path: String, sha: String, depth: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 14), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff"))
            foreground = RippleDrawable(ripple, null, null)
        }
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))

        val spacer = View(context)
        row.addView(spacer, LinearLayout.LayoutParams(dp(14), dp(1)))

        val iconView = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val iconLp = LinearLayout.LayoutParams(dp(16), dp(16))
        iconLp.marginEnd = dp(5)
        iconView.layoutParams = iconLp
        val iconUrl = getIconUrl(name)
        if (iconUrl != null) {
            Glide.with(context).load(iconUrl).into(iconView)
        } else {
            iconView.setImageResource(android.R.drawable.ic_menu_agenda)
            iconView.setColorFilter(Color.parseColor("#858585"))
        }
        row.addView(iconView)

        val label = TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(Color.parseColor("#cccccc"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        row.setOnClickListener { onFileSelected?.invoke(path, sha) }
        row.setOnLongClickListener { showFileContextMenu(row, path, sha); true }

        if (EditorState.activeFilePath == path) {
            row.setBackgroundColor(Color.parseColor("#094771"))
        }

        return row
    }

    private fun buildFolderRow(name: String, path: String, folderId: String, isOpen: Boolean, depth: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 14), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            val ripple = android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff"))
            foreground = RippleDrawable(ripple, null, null)
            tag = "row_$folderId"
        }
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))

        val arrow = TextView(context).apply {
            text = if (isOpen) "▾" else "▸"
            textSize = 10f
            setTextColor(Color.parseColor("#858585"))
            gravity = Gravity.CENTER
            tag = "arrow_$folderId"
        }
        row.addView(arrow, LinearLayout.LayoutParams(dp(14), WRAP_CONTENT))

        val folderIcon = TextView(context).apply {
            text = if (isOpen) "📂" else "📁"
            textSize = 13f
            setPadding(0, 0, dp(5), 0)
            tag = "ficon_$folderId"
        }
        row.addView(folderIcon)

        val label = TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(Color.parseColor("#cccccc"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        row.setOnClickListener { toggleFolder(folderId, row.parent as? LinearLayout) }
        row.setOnLongClickListener { showFolderContextMenu(row, path); true }

        return row
    }

    private fun toggleFolder(folderId: String, parent: LinearLayout?) {
        val child = parent?.findViewWithTag<LinearLayout>("child_$folderId") ?: return
        val arrow = parent.findViewWithTag<TextView>("arrow_$folderId")
        val ficon = parent.findViewWithTag<TextView>("ficon_$folderId")
        val nowOpen = child.visibility == GONE
        folderState[folderId] = nowOpen
        child.visibility = if (nowOpen) VISIBLE else GONE
        arrow?.text = if (nowOpen) "▾" else "▸"
        ficon?.text = if (nowOpen) "📂" else "📁"
    }

    private fun showFileContextMenu(anchor: View, path: String, sha: String) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            add(0, 1, 0, "Abrir")
            add(0, 2, 0, "Novo ficheiro aqui")
            add(0, 3, 0, "Renomear")
            add(0, 4, 0, "Duplicar")
            add(0, 5, 0, "Eliminar")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onFileSelected?.invoke(path, sha)
                2 -> onNewFile?.invoke(path.substringBeforeLast('/'))
                3 -> onRenameFile?.invoke(path)
                4 -> activity.duplicateFile(path)
                5 -> onDeleteFile?.invoke(path, sha)
            }
            true
        }
        popup.show()
    }

    private fun showFolderContextMenu(anchor: View, path: String) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            add(0, 1, 0, "Novo ficheiro aqui")
            add(0, 2, 0, "Nova pasta aqui")
            add(0, 3, 0, "Renomear pasta")
            add(0, 4, 0, "Eliminar pasta")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> onNewFile?.invoke(path)
                2 -> onNewFolder?.invoke(path)
                3 -> onRenameFolder?.invoke(path)
                4 -> onDeleteFolder?.invoke(path)
            }
            true
        }
        popup.show()
    }

    fun setBranches(branches: List<String>, current: String) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, branches)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        branchSpinner.adapter = adapter
        val idx = branches.indexOf(current).coerceAtLeast(0)
        branchSpinner.setSelection(idx)
        branchSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val selected = branches[pos]
                if (selected != EditorState.currentBranch) onBranchChange?.invoke(selected)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateRepoSpinner() {
        val names = EditorState.repos.map { it.name }.toMutableList()
        EditorState.localProject?.let { names.add(0, "${it.name} (local)") }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repoSpinner.adapter = adapter
    }

    fun toggle() { if (open) close() else open() }

    fun open() {
        open = true
        overlay.visibility = VISIBLE
        val panelW = dp(PANEL_WIDTH_DP).toFloat()
        ValueAnimator.ofFloat(-panelW, 0f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                panel.translationX = it.animatedValue as Float
                overlay.alpha = 1f - (-(it.animatedValue as Float) / panelW)
            }
            start()
        }
    }

    fun close() {
        val panelW = dp(PANEL_WIDTH_DP).toFloat()
        ValueAnimator.ofFloat(0f, -panelW).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                panel.translationX = it.animatedValue as Float
                overlay.alpha = 1f - (-(it.animatedValue as Float) / panelW)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    open = false
                    overlay.visibility = GONE
                }
            })
            start()
        }
    }

    fun isOpen() = open

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val bg = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        panel.setBackgroundColor(bg)
    }

    private fun getIconUrl(name: String): String? {
        val lower = name.lowercase()
        val ext = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        return NAME_ICONS[lower]?.let { DI + it } ?: EXT_ICONS[ext]?.let { DI + it }
    }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(Color.parseColor("#3e3e42"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

fun EditorActivity.loadTreeOrProject() {
    if (EditorState.isLocalMode) drawer.renderLocalTree(EditorState.localProject!!)
    else loadTree()
}

fun EditorActivity.triggerUpload() {}
fun EditorActivity.openSettings() {}
fun EditorActivity.duplicateFile(path: String) {}