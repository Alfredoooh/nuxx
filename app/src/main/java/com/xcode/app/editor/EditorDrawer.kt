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
            "htm" to "html5/html5-original.svg",
            "css" to "css3/css3-original.svg",
            "scss" to "sass/sass-original.svg",
            "sass" to "sass/sass-original.svg",
            "js" to "javascript/javascript-original.svg",
            "mjs" to "javascript/javascript-original.svg",
            "ts" to "typescript/typescript-original.svg",
            "tsx" to "react/react-original.svg",
            "jsx" to "react/react-original.svg",
            "dart" to "dart/dart-original.svg",
            "kt" to "kotlin/kotlin-original.svg",
            "kts" to "kotlin/kotlin-original.svg",
            "java" to "java/java-original.svg",
            "py" to "python/python-original.svg",
            "go" to "go/go-original-wordmark.svg",
            "rs" to "rust/rust-original.svg",
            "swift" to "swift/swift-original.svg",
            "json" to "json/json-original.svg",
            "xml" to "xml/xml-original.svg",
            "yaml" to "yaml/yaml-original.svg",
            "yml" to "yaml/yaml-original.svg",
            "md" to "markdown/markdown-original.svg",
            "sh" to "bash/bash-original.svg",
            "bash" to "bash/bash-original.svg",
            "gradle" to "gradle/gradle-original.svg",
            "cpp" to "cplusplus/cplusplus-original.svg",
            "c" to "c/c-original.svg",
            "cs" to "csharp/csharp-original.svg",
            "rb" to "ruby/ruby-original.svg",
            "php" to "php/php-original.svg",
            "lua" to "lua/lua-original.svg",
            "vue" to "vuejs/vuejs-original.svg",
            "svelte" to "svelte/svelte-original.svg"
        )
        private val NAME_MAP = mapOf(
            "dockerfile" to "docker/docker-original.svg",
            ".gitignore" to "git/git-original.svg",
            ".gitattributes" to "git/git-original.svg",
            "pubspec.yaml" to "dart/dart-original.svg",
            "build.gradle" to "gradle/gradle-original.svg",
            "settings.gradle" to "gradle/gradle-original.svg",
            "package.json" to "nodejs/nodejs-original.svg",
            "tsconfig.json" to "typescript/typescript-original.svg",
            "vite.config.js" to "vitejs/vitejs-original.svg",
            "vite.config.ts" to "vitejs/vitejs-original.svg"
        )
    }

    init { buildDrawer() }

    private fun buildDrawer() {
        // Overlay
        overlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            visibility = GONE
            setOnClickListener { close() }
        }
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Panel
        val panelW = dp(PANEL_W_DP)
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            elevation = 20f
            translationX = -panelW.toFloat()
            layoutParams = LayoutParams(panelW, LayoutParams.MATCH_PARENT)
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
        header.addView(makeHeaderIconBtn(
            svgPath = "M6 .278a.768.768 0 0 1 .08.858 7.208 7.208 0 0 0-.878 3.46c0 4.021 3.278 7.277 7.318 7.277.527 0 1.04-.055 1.533-.16a.787.787 0 0 1 .81.316.733.733 0 0 1-.031.893A8.349 8.349 0 0 1 8.344 16C3.734 16 0 12.286 0 7.71 0 4.266 2.114 1.312 5.124.06A.752.752 0 0 1 6 .278z"
        ) { activity.applyTheme(!EditorState.isDark) })
        header.addView(makeHeaderIconBtn(
            svgPath = "M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9z M8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z"
        ) { activity.loadTreeOrProject() })
        header.addView(makeHeaderIconBtn(
            svgPath = "M.5 9.9a.5.5 0 0 1 .5.5v2.5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2.5a.5.5 0 0 1 1 0v2.5a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2v-2.5a.5.5 0 0 1 .5-.5zM7.646 1.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1-.708.708L8.5 2.707V11.5a.5.5 0 0 1-1 0V2.707L5.354 4.854a.5.5 0 1 1-.708-.708l3-3z"
        ) { activity.triggerUpload() })
        header.addView(makeHeaderIconBtn(
            svgPath = "M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.858z"
        ) { activity.openSettingsDialog() })
        header.addView(makeHeaderIconBtn(
            svgPath = "M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z"
        ) { close() })
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
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!initialized) { initialized = true; return }
                val localCount = if (EditorState.localProject != null) 1 else 0
                if (pos < localCount) onSwitchToLocal?.invoke()
                else onRepoChange?.invoke(pos - localCount)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        repoRow.addView(repoSpinner)
        panel.addView(repoRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ── Branch spinner ────────────────────────────────────────────────
        val branchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(6))
        }
        // Branch SVG icon
        val branchIcon = SvgIconView(
            context,
            "M11.75 2.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zm-2.25.75a2.25 2.25 0 1 1 3 2.122V6A2.5 2.5 0 0 1 10 8.5H6a1 1 0 0 0-1 1v1.128a2.251 2.251 0 1 1-1.5 0V5.372a2.25 2.25 0 1 1 1.5 0v1.836A2.492 2.492 0 0 1 6 7h4a1 1 0 0 0 1-1v-.628A2.25 2.25 0 0 1 9.5 3.25zM4.25 12a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5zM3.5 3.25a.75.75 0 1 1 1.5 0 .75.75 0 0 1-1.5 0z",
            Color.parseColor("#858585"),
            dp(13)
        )
        branchRow.addView(branchIcon, LinearLayout.LayoutParams(dp(13), dp(13)).apply { marginEnd = dp(6) })
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
        treeScroll = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
        }
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
        footer.addView(makeFooterItem(
            svgPath = "M8.354 1.146a.5.5 0 0 0-.708 0l-6 6A.5.5 0 0 0 1.5 7.5v7a.5.5 0 0 0 .5.5h4.5a.5.5 0 0 0 .5-.5v-4h2v4a.5.5 0 0 0 .5.5H14a.5.5 0 0 0 .5-.5v-7a.5.5 0 0 0-.146-.354L13 5.793V2.5a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5v1.293L8.354 1.146zM2.5 14V7.707l5.5-5.5 5.5 5.5V14H10v-4a.5.5 0 0 0-.5-.5h-3a.5.5 0 0 0-.5.5v4H2.5z",
            label = "Inicio"
        ) { onGoHome?.invoke() })
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
                    childContainer.setTag(android.R.id.text1, value to shaMap)
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
            tag = "row_$path"
        }

        // Indent spacer
        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), 1)
        })

        // Devicon via Glide
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconUrl = resolveIconUrl(name)
        if (iconUrl != null) {
            Glide.with(context)
                .load(iconUrl)
                .apply(RequestOptions().override(dp(16), dp(16)))
                .into(iconView)
        } else {
            // Generic file icon via SvgIconView
            val generic = SvgIconView(
                context,
                "M4 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4.5L9.5 0H4zm0 1h5v4h4v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1z",
                Color.parseColor("#858585"),
                dp(16)
            )
            row.addView(generic, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) })
        }
        if (iconUrl != null) row.addView(iconView)

        val label = TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(
                if (EditorState.activeFilePath == path) Color.WHITE
                else Color.parseColor("#cccccc")
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        if (EditorState.activeFilePath == path) {
            row.setBackgroundColor(Color.parseColor("#094771"))
        }

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

        // Arrow (chevron right / down)
        val arrowPath = if (isOpen)
            "M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708z"
        else
            "M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708z"
        val arrowView = SvgIconView(context, arrowPath, Color.parseColor("#858585"), dp(10))
        row.addView(arrowView, LinearLayout.LayoutParams(dp(14), dp(10)).apply { marginEnd = dp(2) })

        // Folder icon — use devicon folder or a custom SVG
        val folderPath = if (isOpen)
            "M1 3.5A1.5 1.5 0 0 1 2.5 2h2.764c.958 0 1.76.56 2.311 1.184C7.985 3.648 8.48 4 9 4h4.5A1.5 1.5 0 0 1 15 5.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 12.5v-9z"
        else
            "M.54 3.87.5 3a2 2 0 0 1 2-2h3.672a2 2 0 0 1 1.414.586l.828.828A2 2 0 0 0 9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a1.99 1.99 0 0 1 .342-1.31z"
        val folderColor = if (isOpen) Color.parseColor("#dcb67a") else Color.parseColor("#e8c27d")
        val folderIcon = SvgIconView(context, folderPath, folderColor, dp(15))
        row.addView(folderIcon, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(5) })

        val label = TextView(context).apply {
            text = name
            textSize = 12.5f
            setTextColor(Color.parseColor("#cccccc"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        row.setOnClickListener { toggleFolder(fid, row.parent as? LinearLayout) }
        row.setOnLongClickListener { showFolderCtxMenu(row, path); true }
        return row
    }

    private fun toggleFolder(fid: String, parent: LinearLayout?) {
        val childContainer = parent?.findViewWithTag<LinearLayout>("child_$fid") ?: return
        val nowOpen = childContainer.visibility == GONE
        folderState[fid] = nowOpen
        childContainer.visibility = if (nowOpen) VISIBLE else GONE

        // Rebuild child if was lazy
        if (nowOpen && childContainer.childCount == 0) {
            @Suppress("UNCHECKED_CAST")
            val pending = childContainer.getTag(android.R.id.text1)
                    as? Pair<Map<String, Any>, Map<String, String>>
            if (pending != null) {
                val (node, shaMap) = pending
                val depth = (((parent.getChildAt(0) as? LinearLayout)
                    ?.paddingStart ?: dp(8)) - dp(8)) / dp(14) + 1
                renderNode(node, childContainer, depth, "", shaMap)
                childContainer.setTag(android.R.id.text1, null)
            }
        }

        // Update arrow and folder icon in the row above
        val rowIdx = parent.indexOfChild(childContainer) - 1
        if (rowIdx >= 0) {
            val row = parent.getChildAt(rowIdx) as? LinearLayout ?: return
            val newArrow = if (nowOpen)
                "M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708z"
            else
                "M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708z"
            (row.getChildAt(0) as? SvgIconView)?.updatePath(newArrow)
            val newFolderPath = if (nowOpen)
                "M1 3.5A1.5 1.5 0 0 1 2.5 2h2.764c.958 0 1.76.56 2.311 1.184C7.985 3.648 8.48 4 9 4h4.5A1.5 1.5 0 0 1 15 5.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 12.5v-9z"
            else
                "M.54 3.87.5 3a2 2 0 0 1 2-2h3.672a2 2 0 0 1 1.414.586l.828.828A2 2 0 0 0 9.828 3h3.982a2 2 0 0 1 1.992 2.181l-.637 7A2 2 0 0 1 13.174 14H2.826a2 2 0 0 1-1.991-1.819l-.637-7a1.99 1.99 0 0 1 .342-1.31z"
            val newFolderColor = if (nowOpen) Color.parseColor("#dcb67a") else Color.parseColor("#e8c27d")
            (row.getChildAt(1) as? SvgIconView)?.updatePathAndColor(newFolderPath, newFolderColor)
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
        repoSpinner.setSelection(sel.coerceIn(0, names.size - 1))
    }

    // ── Open / close ──────────────────────────────────────────────────────

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
        val bg = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        panel.setBackgroundColor(bg)
    }

    // ── Icon resolution ───────────────────────────────────────────────────

    private fun resolveIconUrl(name: String): String? {
        val lower = name.lowercase()
        val ext = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        return NAME_MAP[lower]?.let { DI + it } ?: EXT_MAP[ext]?.let { DI + it }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun makeHeaderIconBtn(svgPath: String, onClick: () -> Unit): SvgIconView {
        return SvgIconView(context, svgPath, Color.parseColor("#858585"), dp(14)).apply {
            val sz = dp(28)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginStart = dp(2) }
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
        }
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
        val icon = SvgIconView(context, svgPath, Color.parseColor("#858585"), dp(14))
        row.addView(icon, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(10) })
        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#858585"))
        }
        row.addView(labelView)
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

// ── Extensions on EditorActivity needed by drawer ─────────────────────────
fun EditorActivity.loadTreeOrProject() {
    if (EditorState.isLocalMode) {
        EditorState.localProject?.let { drawer.renderLocalTree(it) }
    } else {
        loadTree()
    }
}

fun EditorActivity.triggerUpload() {
    // Hook: open file picker for upload
}

fun EditorActivity.openSettingsDialog() {
    // TODO: implement settings screen
    com.xcode.app.ui.XCodeDialog.alert(this, "Configuracoes em desenvolvimento.")
}