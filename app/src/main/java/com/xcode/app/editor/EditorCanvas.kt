package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.*

class EditorCanvas(private val activity: EditorActivity) : LinearLayout(activity) {

    var onContentChanged: ((String, String) -> Unit)? = null

    private lateinit var toolbar: LinearLayout
    private lateinit var filePathLabel: TextView
    private lateinit var lineNumCol: LinearLayout
    private lateinit var codeEdit: EditText
    private lateinit var codeScroll: ScrollView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchCount: TextView
    private lateinit var emptyView: LinearLayout
    private lateinit var codeArea: LinearLayout

    private var currentPath: String? = null
    private var isDark = true
    private var isApplyingHighlight = false
    private var highlightJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val colBg       get() = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
    private val colText     get() = if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333")
    private val colLineNum  get() = Color.parseColor("#555558")
    private val colToolbar  get() = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
    private val colBorder   get() = if (isDark) Color.parseColor("#3e3e42") else Color.parseColor("#e0e0e0")
    private val colKw       get() = if (isDark) Color.parseColor("#569cd6") else Color.parseColor("#0000ff")
    private val colStr      get() = if (isDark) Color.parseColor("#ce9178") else Color.parseColor("#a31515")
    private val colCm       get() = if (isDark) Color.parseColor("#6a9955") else Color.parseColor("#008000")
    private val colNum      get() = if (isDark) Color.parseColor("#b5cea8") else Color.parseColor("#098658")
    private val colFn       get() = if (isDark) Color.parseColor("#dcdcaa") else Color.parseColor("#795e26")
    private val colTp       get() = if (isDark) Color.parseColor("#4ec9b0") else Color.parseColor("#267f99")

    init {
        orientation = VERTICAL
        buildLayout()
    }

    private fun buildLayout() {
        // ── Toolbar ───────────────────────────────────────────────────────
        toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colToolbar)
            setPadding(dp(8), 0, dp(8), 0)
            visibility = GONE
        }
        filePathLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(colLineNum)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        toolbar.addView(filePathLabel)
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))

        // ── Search bar ────────────────────────────────────────────────────
        searchBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colToolbar)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = GONE
        }
        searchInput = EditText(context).apply {
            hint = "Procurar no ficheiro..."
            textSize = 12f
            setTextColor(colText)
            setHintTextColor(colLineNum)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(4), dp(8), dp(4))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#eeeeee"))
                cornerRadius = dp(3).toFloat()
                setStroke(dp(1), colBorder)
            }
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        searchCount = TextView(context).apply {
            textSize = 11f
            setTextColor(colLineNum)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, dp(4), 0)
            minWidth = dp(60)
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { doSearch(s.toString()) }
        })
        val closeBtnSearch = XCodeIcon(context, IconPaths.CLOSE, colLineNum, dp(14)).apply {
            val sz = dp(28)
            layoutParams = LayoutParams(sz, sz).apply { marginStart = dp(2) }
            isClickable = true; isFocusable = true
            setOnClickListener { hideSearch() }
        }
        searchBar.addView(searchInput)
        searchBar.addView(searchCount)
        searchBar.addView(closeBtnSearch)
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        // ── Code area ─────────────────────────────────────────────────────
        codeArea = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            visibility = GONE
        }

        lineNumCol = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(colBg)
            setPadding(0, dp(14), 0, dp(14))
        }
        val lineNumBorder = View(context).apply {
            setBackgroundColor(colBorder)
        }
        codeArea.addView(lineNumCol, LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.MATCH_PARENT))
        codeArea.addView(lineNumBorder, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))

        codeScroll = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        codeEdit = EditText(context).apply {
            setBackgroundColor(colBg)
            setTextColor(colText)
            typeface = Typeface.MONOSPACE
            textSize = EditorState.fontSize.toFloat()
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(14), dp(16), dp(14))
            isSingleLine = false
            isVerticalScrollBarEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextIsSelectable(true)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        codeScroll.addView(codeEdit)
        codeArea.addView(codeScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        addView(codeArea)

        codeEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isApplyingHighlight) return
                val path = currentPath ?: return
                val text = s.toString()
                onContentChanged?.invoke(path, text)
                updateLineNumbers(text)
                scheduleHighlight(path, text)
            }
        })

        // ── Empty state ───────────────────────────────────────────────────
        emptyView = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colBg)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val emptyIcon = XCodeIcon(context, IconPaths.FILE, Color.parseColor("#2a2a30"), dp(52))
        val emptyText = TextView(context).apply {
            text = "Abre o Explorer ou cria um novo projeto"
            textSize = 12f
            setTextColor(Color.parseColor("#444448"))
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(14), dp(32), 0)
        }
        emptyView.addView(emptyIcon, LayoutParams(dp(52), dp(52)).apply { bottomMargin = dp(12) })
        emptyView.addView(emptyText)
        addView(emptyView)

        showEmpty()
    }

    // ── Load / show ───────────────────────────────────────────────────────

    fun loadFile(path: String) {
        val file = EditorState.openFiles[path] ?: return
        currentPath = path
        emptyView.visibility = GONE
        toolbar.visibility = VISIBLE
        codeArea.visibility = VISIBLE
        filePathLabel.text = path

        if (file.isBinary) {
            isApplyingHighlight = true
            codeEdit.setText("[Ficheiro binario - visualizacao nao disponivel]")
            codeEdit.isEnabled = false
            isApplyingHighlight = false
            lineNumCol.removeAllViews()
            return
        }

        codeEdit.isEnabled = true
        isApplyingHighlight = true
        codeEdit.setText(file.content)
        isApplyingHighlight = false
        updateLineNumbers(file.content)
        scheduleHighlight(path, file.content)
    }

    fun showEmpty() {
        currentPath = null
        toolbar.visibility = GONE
        searchBar.visibility = GONE
        codeArea.visibility = GONE
        emptyView.visibility = VISIBLE
    }

    // ── Line numbers ──────────────────────────────────────────────────────

    private fun updateLineNumbers(text: String) {
        val count = text.count { it == '\n' } + 1
        if (lineNumCol.childCount == count) return
        lineNumCol.removeAllViews()
        val lineH = (EditorState.fontSize * 1.6f * resources.displayMetrics.scaledDensity).toInt()
        for (i in 1..count) {
            lineNumCol.addView(TextView(context).apply {
                this.text = i.toString()
                textSize = EditorState.fontSize.toFloat()
                setTextColor(colLineNum)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, lineH
                )
            })
        }
    }

    // ── Syntax highlight ──────────────────────────────────────────────────

    private fun scheduleHighlight(path: String, text: String) {
        highlightJob?.cancel()
        highlightJob = scope.launch {
            delay(280)
            val ext = path.substringAfterLast('.', "").lowercase()
            val spannable = withContext(Dispatchers.Default) { buildHighlight(text, ext) }
            if (currentPath == path && !isApplyingHighlight) {
                isApplyingHighlight = true
                val sel = codeEdit.selectionStart.coerceIn(0, spannable.length)
                codeEdit.text = spannable
                try { codeEdit.setSelection(sel) } catch (_: Exception) {}
                isApplyingHighlight = false
            }
        }
    }

    private fun buildHighlight(text: String, ext: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)

        fun applyRegex(regex: Regex, color: Int) {
            regex.findAll(text).forEach { m ->
                try {
                    if (m.range.first >= 0 && m.range.last + 1 <= sb.length)
                        sb.setSpan(ForegroundColorSpan(color), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } catch (_: Exception) {}
            }
        }

        val codeExts = setOf("js","mjs","ts","jsx","tsx","dart","kt","kts","java","swift","go","rs","py","c","cpp","cs","rb","php","lua","sh","bash")

        when {
            ext in codeExts -> {
                applyRegex(Regex("/\\*[\\s\\S]*?\\*/"), colCm)
                applyRegex(Regex("(?<!:)//[^\n]*"), colCm)
                applyRegex(Regex("#[^\n]*"), colCm)
                applyRegex(Regex("`[^`]*`|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'"), colStr)
                applyRegex(Regex("\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*[fFLlUu]?)\\b"), colNum)
                val kws = getKeywords(ext)
                if (kws.isNotEmpty())
                    applyRegex(Regex("(?<![\\w.])\\b(${kws.joinToString("|")})\\b"), colKw)
                applyRegex(Regex("\\b[A-Z][A-Za-z0-9_]+\\b"), colTp)
                applyRegex(Regex("\\b[a-z_][a-zA-Z0-9_]*(?=\\s*\\()"), colFn)
            }
            ext in setOf("html","htm","xml","svg") -> {
                applyRegex(Regex("<!--[\\s\\S]*?-->"), colCm)
                applyRegex(Regex("</?[\\w:-]+"), colKw)
                applyRegex(Regex("[\\w:-]+="), colFn)
                applyRegex(Regex("\"[^\"]*\"|'[^']*'"), colStr)
            }
            ext in setOf("css","scss","sass","less") -> {
                applyRegex(Regex("/\\*[\\s\\S]*?\\*/"), colCm)
                applyRegex(Regex("//[^\n]*"), colCm)
                applyRegex(Regex("@[\\w-]+"), colKw)
                applyRegex(Regex("#[0-9a-fA-F]{3,8}\\b"), colNum)
                applyRegex(Regex("\\b\\d+\\.?\\d*(px|em|rem|vh|vw|%|s|ms|deg|fr)?\\b"), colNum)
                applyRegex(Regex("[\\w-]+\\s*:"), colKw)
                applyRegex(Regex("\"[^\"]*\"|'[^']*'"), colStr)
            }
            ext == "json" -> {
                applyRegex(Regex("\"[^\"]+\"\\s*:"), colKw)
                applyRegex(Regex(":\\s*\"[^\"]*\""), colStr)
                applyRegex(Regex(":\\s*-?\\d+\\.?\\d*"), colNum)
                applyRegex(Regex("\\b(true|false|null)\\b"), colKw)
            }
            ext in setOf("yaml","yml") -> {
                applyRegex(Regex("#[^\n]*"), colCm)
                applyRegex(Regex("^[\\w-]+\\s*:", RegexOption.MULTILINE), colKw)
                applyRegex(Regex("\"[^\"]*\"|'[^']*'"), colStr)
                applyRegex(Regex(":\\s*\\d+\\.?\\d*"), colNum)
            }
            ext == "md" -> {
                applyRegex(Regex("^#{1,6}\\s.+", RegexOption.MULTILINE), colKw)
                applyRegex(Regex("`[^`\n]+`"), colStr)
                applyRegex(Regex("\\*\\*[^*\n]+\\*\\*|__[^_\n]+__"), colFn)
                applyRegex(Regex("\\*[^*\n]+\\*|_[^_\n]+_"), colTp)
                applyRegex(Regex("^>\\s.+", RegexOption.MULTILINE), colCm)
                applyRegex(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), colKw)
            }
        }
        return sb
    }

    private fun getKeywords(ext: String): List<String> = when (ext) {
        "kt","kts" -> listOf("abstract","actual","annotation","as","break","by","catch","class","companion","const","constructor","continue","crossinline","data","do","dynamic","else","enum","expect","external","false","final","finally","for","fun","get","if","import","in","infix","init","inline","inner","interface","internal","is","it","lateinit","noinline","null","object","open","operator","out","override","package","private","protected","public","reified","return","sealed","set","super","suspend","tailrec","this","throw","true","try","typealias","val","var","vararg","when","where","while")
        "dart" -> listOf("abstract","as","assert","async","await","break","case","catch","class","const","continue","covariant","default","deferred","do","dynamic","else","enum","export","extends","extension","external","factory","false","final","finally","for","Function","get","hide","if","implements","import","in","interface","is","late","library","mixin","new","null","on","operator","part","required","rethrow","return","set","show","static","super","switch","sync","this","throw","true","try","typedef","var","void","while","with","yield")
        "java" -> listOf("abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","false","final","finally","float","for","if","implements","import","instanceof","int","interface","long","native","new","null","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","true","try","void","volatile","while")
        "ts","tsx" -> listOf("abstract","as","async","await","break","case","catch","class","const","continue","declare","default","delete","do","else","enum","export","extends","false","finally","for","from","function","if","implements","import","in","instanceof","interface","let","namespace","new","null","of","override","private","protected","public","readonly","return","static","super","switch","this","throw","true","try","type","typeof","undefined","var","void","while","yield")
        "js","jsx","mjs" -> listOf("async","await","break","case","catch","class","const","continue","debugger","default","delete","do","else","export","extends","false","finally","for","function","if","import","in","instanceof","let","new","null","of","return","super","switch","this","throw","true","try","typeof","undefined","var","void","while","yield")
        "py" -> listOf("False","None","True","and","as","assert","async","await","break","class","continue","def","del","elif","else","except","finally","for","from","global","if","import","in","is","lambda","nonlocal","not","or","pass","raise","return","try","while","with","yield")
        "go" -> listOf("break","case","chan","const","continue","default","defer","else","fallthrough","for","func","go","goto","if","import","interface","map","package","range","return","select","struct","switch","type","var","nil","true","false","iota")
        "rs" -> listOf("as","async","await","break","const","continue","crate","dyn","else","enum","extern","false","fn","for","if","impl","in","let","loop","match","mod","move","mut","pub","ref","return","self","Self","static","struct","super","trait","true","type","unsafe","use","where","while")
        "swift" -> listOf("associatedtype","class","deinit","enum","extension","fileprivate","func","import","init","inout","internal","let","open","operator","private","protocol","public","rethrows","return","static","struct","subscript","typealias","var","break","case","continue","default","defer","do","else","fallthrough","for","guard","if","in","repeat","throw","switch","where","while","as","catch","false","is","nil","super","self","Self","true","try")
        "cs" -> listOf("abstract","as","base","bool","break","byte","case","catch","char","checked","class","const","continue","decimal","default","delegate","do","double","else","enum","event","explicit","extern","false","finally","float","for","foreach","goto","if","implicit","in","int","interface","internal","is","lock","long","namespace","new","null","object","operator","out","override","params","private","protected","public","readonly","ref","return","sbyte","sealed","short","sizeof","static","string","struct","switch","this","throw","true","try","typeof","uint","ulong","unchecked","unsafe","ushort","using","virtual","void","volatile","while","async","await","dynamic","var","yield")
        "c","cpp" -> listOf("auto","break","case","char","const","continue","default","do","double","else","enum","extern","float","for","goto","if","inline","int","long","return","short","signed","sizeof","static","struct","switch","typedef","union","unsigned","void","volatile","while","NULL","true","false","class","namespace","new","delete","public","private","protected","virtual","override","template","typename","nullptr","bool","constexpr")
        "sh","bash" -> listOf("if","then","else","elif","fi","for","while","do","done","case","esac","function","return","export","local","echo","cd","ls","pwd","mkdir","rm","cp","mv","cat","grep","sed","awk","curl","source","exit","in","read")
        else -> emptyList()
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────

    fun undo() {
        val path = currentPath ?: return
        val content = EditorState.undo(path) ?: return
        isApplyingHighlight = true
        codeEdit.setText(content)
        isApplyingHighlight = false
        updateLineNumbers(content)
        EditorState.openFiles[path]?.content = content
        activity.appBar.updateUndoRedo(EditorState.canUndo(path), EditorState.canRedo(path))
    }

    fun redo() {
        val path = currentPath ?: return
        val content = EditorState.redo(path) ?: return
        isApplyingHighlight = true
        codeEdit.setText(content)
        isApplyingHighlight = false
        updateLineNumbers(content)
        EditorState.openFiles[path]?.content = content
        activity.appBar.updateUndoRedo(EditorState.canUndo(path), EditorState.canRedo(path))
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun toggleSearch() {
        if (searchBar.visibility == VISIBLE) hideSearch()
        else { searchBar.visibility = VISIBLE; searchInput.requestFocus() }
    }

    private fun hideSearch() {
        searchBar.visibility = GONE
        searchCount.text = ""
    }

    private fun doSearch(query: String) {
        if (query.isEmpty()) { searchCount.text = ""; return }
        val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            .findAll(codeEdit.text.toString()).toList()
        searchCount.text = "${matches.size} resultado(s)"
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        setBackgroundColor(colBg)
        toolbar.setBackgroundColor(colToolbar)
        codeEdit.setBackgroundColor(colBg)
        codeEdit.setTextColor(colText)
        lineNumCol.setBackgroundColor(colBg)
        emptyView.setBackgroundColor(colBg)
        currentPath?.let { loadFile(it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}