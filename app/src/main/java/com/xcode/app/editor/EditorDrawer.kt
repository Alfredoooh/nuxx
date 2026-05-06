package com.xcode.app.editor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class EditorDrawer(private val activity: EditorActivity) : FrameLayout(activity) {

    var onFileSelected: ((String, String) -> Unit)? = null
    var onGoHome: (() -> Unit)? = null
    var onNewFile: ((String) -> Unit)? = null
    var onNewFolder: ((String) -> Unit)? = null
    var onRenameFile: ((String) -> Unit)? = null
    var onRenameFolder: ((String) -> Unit)? = null
    var onDeleteFile: ((String, String) -> Unit)? = null
    var onDeleteFolder: ((String) -> Unit)? = null
    var onDuplicateFile: ((String) -> Unit)? = null
    var onBranchChange: ((String) -> Unit)? = null
    var onRepoChange: ((Int) -> Unit)? = null
    var onSwitchToLocal: (() -> Unit)? = null

    private lateinit var overlay: View
    private lateinit var panel: LinearLayout
    private lateinit var treeScroll: ScrollView
    private lateinit var treeContainer: LinearLayout
    private lateinit var branchSpinner: Spinner
    private lateinit var repoSpinner: Spinner
    private var isOpen = false
    private val folderState = mutableMapOf<String, Boolean>()
    private var isDark = true

    companion object {
        private const val PANEL_W_DP = 290
        private val DI = "https://cdn.jsdelivr.net/gh/devicons/devicon@latest/icons/"
        private val EXT_MAP = mapOf(
            "html" to "html5/html5-original.svg",
            "htm"  to "html5/html5-original.svg",
            "css"  to "css3/css3-original.svg",
            "scss" to "sass/sass-original.svg",
            "sass" to "sass/sass-original.svg",
            "js"   to "javascript/javascript-original.svg",
            "mjs"  to "javascript/javascript-original.svg",
            "ts"   to "typescript/typescript-original.svg",
            "tsx"  to "react/react-original.svg",
            "jsx"  to "react/react-original.svg",
            "dart" to "dart/dart-original.svg",
            "kt"   to "kotlin/kotlin-original.svg",
            "kts"  to "kotlin/kotlin-original.svg",
            "java" to "java/java-original.svg",
            "py"   to "python/python-original.svg",
            "go"   to "go/go-original-wordmark.svg",
            "rs"   to "rust/rust-original.svg",
            "swift" to "swift/swift-original.svg",
            "json" to "json/json-original.svg",
            "xml"  to "xml/xml-original.svg",
            "yaml" to "yaml/yaml-original.svg",
            "yml"  to "yaml/yaml-original.svg",
            "md"   to "markdown/markdown-original.svg",
            "sh"   to "bash/bash-original.svg",
            "bash" to "bash/bash-original.svg",
            "gradle" to "gradle/gradle-original.svg",
            "cpp"  to "cplusplus/cplusplus-original.svg",
            "c"    to "c/c-original.svg",
            "cs"   to "csharp/csharp-original.svg",
            "rb"   to "ruby/ruby-original.svg",
            "php"  to "php/php-original.svg",
            "lua"  to "lua/lua-original.svg",
            "vue"  to "vuejs/vuejs-original.svg",
            "svelte" to "svelte/svelte-original.svg"
        )
        private val NAME_MAP = mapOf(
            "dockerfile"    to "docker/docker-original.svg",
            ".gitignore"    to "git/git-original.svg",
            ".gitattributes" to "git/git-original.svg",
            "pubspec.yaml"  to "dart/dart-original.svg",
            "build.gradle"  to "gradle/gradle-original.svg",
            "settings.gradle" to "gradle/gradle-original.svg",
            "package.json"  to "nodejs/nodejs-original.svg",
            "tsconfig.json" to "typescript/typescript-original.svg",
            "vite.config.js" to "vitejs/vitejs-original.svg",
            "vite.config.ts" to "vitejs/vitejs-original.svg"
        )
    }

    init { buildDrawer() }

    private fun buildDrawer() {
        overlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            visibility = GONE
            setOnClickListener { close() }
        }
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val panelW = dp(PANEL_W_DP)
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            elevation = 20f
            translationX = -panelW.toFloat()
        }
        addView(panel, LayoutParams(panelW, LayoutParams.MATCH_PARENT))

        // ── Header ────────────────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            setPadding(dp(14), dp(6), dp(6), dp(6))
        }
        val headerTitle = TextView(context).apply {
            text = "EXPLORER"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.parseColor("#858585"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(headerTitle)
        header.addView(makeHeaderIconBtn(IconPaths.MOON)    { activity.applyTheme(!EditorState.isDark) })
        header.addView(makeHeaderIconBtn(IconPaths.RELOAD)  { activity.loadTreeOrProject() })
        header.addView(makeHeaderIconBtn(IconPaths.UPLOAD)  { activity.triggerUpload() })
        header.addView(makeHeaderIconBtn(IconPaths.SETTINGS){ activity.openSettingsDialog() })
        header.addView(makeHeaderIconBtn(IconPaths.CLOSE)   { close() })
        panel.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
        ))
        panel.addView(makeDivider())

        // ── Repo spinner ──────────────────────────────────────────────────
        val repoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        repoSpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        updateRepoSpinner()
        repoSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var initialized = false
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!initialized) { initialized = true; return }
                val localCount = if (EditorState.localProject != null) 1 else 0
                if (pos < localCount) onSwitchToLocal?.invoke()
                else onRepoChange?.invoke(pos - localCount)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        repoRow.addView(repoSpinner)
        panel.addView(repoRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ── Branch row ────────────────────────────────────────────────────
        val branchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(6))
        }
        val branchIconLp = LinearLayout.LayoutParams(dp(13), dp(13))
        branchIconLp.setMargins(0, 0, dp(6), 0)
        val branchIcon = XCodeIcon(context, IconPaths.BRANCH, Color.parseColor("#858585"), dp(13))
        branchRow.addView(branchIcon, branchIconLp)
        branchSpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        setBranches(listOf(EditorState.currentBranch), EditorState.currentBranch)
        branchRow.addView(branchSpinner)
        panel.addView(branchRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(makeDivider())

        // ── Tree ──────────────────────────────────────────────────────────
        treeScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        treeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        treeScroll.addView(treeContainer)
        panel.addView(treeScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        panel.addView(makeDivider())

        // ── Footer ────────────────────────────────────────────────────────
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        footer.addView(makeFooterItem(IconPaths.HOME, "Inicio") { onGoHome?.invoke() })
        panel.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    // ── Tree rendering ────────────────────────────────────────────────────

    fun renderTree(items: List<GitFile>) {
        treeContainer.removeAllViews()
        val paths = items.filter { it.type == "blob" }.map { it.path }
        val shaMap = items.associate { it.path to it.sha }
        val root = buildFileTree(paths)
        renderNode(root, treeContainer, 0, "", shaMap)
    }

    fun renderLocalTree(project: LocalProject) {
        treeContainer.removeAllViews()
        val root = buildFileTree(project.files.keys.toList())
        renderNode(root, treeContainer, 0, "", emptyMap())
    }

    private fun buildFileTree(paths: List<String>): Map<String, Any> {
        val root = mutableMapOf<String, Any>()
        paths.sorted().forEach { path ->
            val parts = path.split('/')
            var node = root
            parts.forEachIndexed { i, part ->
                if (i == parts.size - 1) {
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
            { it.key.lowercase() }
        ))
        sorted.forEach { (name, value) ->
            val fullPath = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
            if (value == "__file__") {
                container.addView(buildFileRow(name, fullPath, shaMap[fullPath] ?: "", depth))
            } else {
                val fid = fullPath.hashCode().toString()
                val childOpen = folderState.getOrDefault(fid, true)
                container.addView(buildFolderRow(name, fullPath, fid, childOpen, depth))
                val childContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (childOpen) VISIBLE else GONE
                    tag = "child_$fid"
                }
                if (childOpen) {
                    renderNode(value as Map<String, Any>, childContainer, depth + 1, fullPath, shaMap)
                } else {
                    childContainer.setTag(android.R.id.text1, Pair(value as Map<String, Any>, shaMap))
                }
                container.addView(childContainer)
            }
        }
    }

    private fun buildFileRow(name: String, path: String, sha: String, depth: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)
            )
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
        }
        if (EditorState.activeFilePath == path) {
            row.setBackgroundColor(Color.parseColor("#094771"))
        }

        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), 1)
        })

        val iconUrl = resolveIconUrl(name)
        if (iconUrl != null) {
            val imgLp = LinearLayout.LayoutParams(dp(16), dp(16))
            imgLp.setMargins(0, 0, dp(6), 0)
            val imgView = ImageView(context).apply {
                layoutParams = imgLp
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            Glide.with(context)
                .load(iconUrl)
                .apply(RequestOptions().override(dp(16), dp(16)))
                .into(imgView)
            row.addView(imgView)
        } else {
            val genericIconLp = LinearLayout.LayoutParams(dp(16), dp(16))
            genericIconLp.setMargins(0, 0, dp(6), 0)
            val genericIcon = XCodeIcon(context, IconPaths.FILE, Color.parseColor("#858585"), dp(16))
            row.addView(genericIcon, genericIconLp)
        }

        row.addView(TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(if (EditorState.activeFilePath == path) Color.WHITE else Color.parseColor("#cccccc"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.setOnClickListener { onFileSelected?.invoke(path, sha) }
        row.setOnLongClickListener { showFileCtxMenu(row, path, sha); true }
        return row
    }

    private fun buildFolderRow(name: String, path: String, fid: String, isOpen: Boolean, depth: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)
            )
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            tag = "folderrow_$fid"
        }

        val arrowLp = LinearLayout.LayoutParams(dp(14), dp(10))
        arrowLp.setMargins(0, 0, dp(2), 0)
        val arrowIcon = XCodeIcon(
            context,
            if (isOpen) IconPaths.CHEVRON_DOWN else IconPaths.CHEVRON_RIGHT,
            Color.parseColor("#858585"),
            dp(10)
        ).apply { tag = "arrow_$fid" }
        row.addView(arrowIcon, arrowLp)

        val folderIconLp = LinearLayout.LayoutParams(dp(15), dp(15))
        folderIconLp.setMargins(0, 0, dp(5), 0)
        val folderIcon = XCodeIcon(
            context,
            if (isOpen) IconPaths.FOLDER_OPEN else IconPaths.FOLDER_CLOSED,
            if (isOpen) Color.parseColor("#dcb67a") else Color.parseColor("#e8c27d"),
            dp(15)
        ).apply { tag = "ficon_$fid" }
        row.addView(folderIcon, folderIconLp)

        row.addView(TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(Color.parseColor("#cccccc"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.setOnClickListener { toggleFolder(fid, row.parent as? LinearLayout) }
        row.setOnLongClickListener { showFolderCtxMenu(row, path); true }
        return row
    }

    private fun toggleFolder(fid: String, parent: LinearLayout?) {
        val childContainer = parent?.findViewWithTag<LinearLayout>("child_$fid") ?: return
        val nowOpen = childContainer.visibility == GONE
        folderState[fid] = nowOpen
        childContainer.visibility = if (nowOpen) VISIBLE else GONE

        if (nowOpen && childContainer.childCount == 0) {
            @Suppress("UNCHECKED_CAST")
            val pending = childContainer.getTag(android.R.id.text1)
                    as? Pair<Map<String, Any>, Map<String, String>>
            if (pending != null) {
                val rowAbove = parent.getChildAt(parent.indexOfChild(childContainer) - 1) as? LinearLayout
                val depthPx = rowAbove?.paddingStart ?: dp(8)
                val depth = ((depthPx - dp(8)) / dp(14)) + 1
                renderNode(pending.first, childContainer, depth, "", pending.second)
                childContainer.setTag(android.R.id.text1, null)
            }
        }

        val rowIdx = parent.indexOfChild(childContainer) - 1
        if (rowIdx >= 0) {
            val row = parent.getChildAt(rowIdx) as? LinearLayout ?: return
            row.findViewWithTag<XCodeIcon>("arrow_$fid")?.updatePath(
                if (nowOpen) IconPaths.CHEVRON_DOWN else IconPaths.CHEVRON_RIGHT
            )
            row.findViewWithTag<XCodeIcon>("ficon_$fid")?.updatePathAndColor(
                if (nowOpen) IconPaths.FOLDER_OPEN else IconPaths.FOLDER_CLOSED,
                if (nowOpen) Color.parseColor("#dcb67a") else Color.parseColor("#e8c27d")
            )
        }
    }

    // ── Context menus ─────────────────────────────────────────────────────

    private fun showFileCtxMenu(anchor: View, path: String, sha: String) {
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
                2 -> onNewFile?.invoke(path.substringBeforeLast('/', ""))
                3 -> onRenameFile?.invoke(path)
                4 -> onDuplicateFile?.invoke(path)
                5 -> onDeleteFile?.invoke(path, sha)
            }
            true
        }
        popup.show()
    }

    private fun showFolderCtxMenu(anchor: View, path: String) {
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

    // ── Branches & repos ──────────────────────────────────────────────────

    fun setBranches(branches: List<String>, current: String) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, branches)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        branchSpinner.adapter = adapter
        val idx = branches.indexOf(current).coerceAtLeast(0)
        branchSpinner.setSelection(idx)
        branchSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var init = false
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!init) { init = true; return }
                if (branches[pos] != EditorState.currentBranch) onBranchChange?.invoke(branches[pos])
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    fun updateRepoSpinner() {
        val names = mutableListOf<String>()
        EditorState.localProject?.let { names.add("${it.name} (local)") }
        EditorState.repos.forEach { names.add(it.name) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repoSpinner.adapter = adapter
        val localCount = if (EditorState.localProject != null) 1 else 0
        val sel = if (EditorState.isLocalMode) 0 else EditorState.activeRepoIdx + localCount
        repoSpinner.setSelection(sel.coerceIn(0, (names.size - 1).coerceAtLeast(0)))
    }

    // ── Open / close animation ────────────────────────────────────────────

    fun toggle() { if (isOpen) close() else open() }

    fun open() {
        isOpen = true
        overlay.visibility = VISIBLE
        val pw = dp(PANEL_W_DP).toFloat()
        ValueAnimator.ofFloat(-pw, 0f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                panel.translationX = v
                overlay.alpha = (1f - (-v / pw)).coerceIn(0f, 1f)
            }
            start()
        }
    }

    fun close() {
        val pw = dp(PANEL_W_DP).toFloat()
        ValueAnimator.ofFloat(0f, -pw).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                panel.translationX = v
                overlay.alpha = (1f - (-v / pw)).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isOpen = false
                    overlay.visibility = GONE
                }
            })
            start()
        }
    }

    fun isOpen() = isOpen

    // ── Theme ─────────────────────────────────────────────────────────────

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        panel.setBackgroundColor(
            if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun resolveIconUrl(name: String): String? {
        val lower = name.lowercase()
        val ext = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        return NAME_MAP[lower]?.let { DI + it } ?: EXT_MAP[ext]?.let { DI + it }
    }

    private fun makeHeaderIconBtn(svgPath: String, onClick: () -> Unit): XCodeIcon =
        XCodeIcon(context, svgPath, Color.parseColor("#858585"), dp(14)).apply {
            val sz = dp(28)
            val lp = LinearLayout.LayoutParams(sz, sz)
            lp.setMargins(dp(2), 0, 0, 0)
            layoutParams = lp
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
        }

    private fun makeFooterItem(svgPath: String, label: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
        }
        val iconLp = LinearLayout.LayoutParams(dp(14), dp(14))
        iconLp.setMargins(0, 0, dp(10), 0)
        val icon = XCodeIcon(context, svgPath, Color.parseColor("#858585"), dp(14))
        row.addView(icon, iconLp)
        row.addView(TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#858585"))
        })
        return row
    }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(Color.parseColor("#3e3e42"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// ── Extensions on EditorActivity ─────────────────────────────────────────

fun EditorActivity.loadTreeOrProject() {
    if (EditorState.isLocalMode) EditorState.localProject?.let { drawer.renderLocalTree(it) }
    else loadTree()
}

fun EditorActivity.triggerUpload() {
    // TODO: open file picker
}

fun EditorActivity.openSettingsDialog() {
    com.xcode.app.ui.XCodeDialog.alert(this, "Configuracoes em desenvolvimento.")
}