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

    private lateinit var toolbarRow: LinearLayout
    private lateinit var filePathLabel: TextView
    private lateinit var lineNumContainer: LinearLayout
    private lateinit var codeEdit: EditText
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchCountLabel: TextView
    private lateinit var emptyView: LinearLayout

    private var currentPath: String? = null
    private var isDark = true
    private var isUpdating = false
    private var highlightJob: Job? = null

    private val highlightScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Colors — dark theme
    private val colText get() = if (isDark) Color.parseColor("#cccccc") else Color.parseColor("#333333")
    private val colBg get() = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
    private val colKw get() = if (isDark) Color.parseColor("#569cd6") else Color.parseColor("#0000ff")
    private val colStr get() = if (isDark) Color.parseColor("#ce9178") else Color.parseColor("#a31515")
    private val colCm get() = if (isDark) Color.parseColor("#6a9955") else Color.parseColor("#008000")
    private val colNum get() = if (isDark) Color.parseColor("#b5cea8") else Color.parseColor("#098658")
    private val colFn get() = if (isDark) Color.parseColor("#dcdcaa") else Color.parseColor("#795e26")
    private val colTp get() = if (isDark) Color.parseColor("#4ec9b0") else Color.parseColor("#267f99")

    init {
        orientation = VERTICAL
        buildLayout()
    }

    private fun buildLayout() {
        // ── Toolbar ───────────────────────────────────────────────────────
        toolbarRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            setPadding(dp(8), 0, dp(8), 0)
            minimumHeight = dp(28)
        }

        filePathLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#858585"))
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        toolbarRow.addView(filePathLabel)
        addView(toolbarRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))

        // Search bar (hidden by default)
        searchBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = GONE
        }
        searchInput = EditText(context).apply {
            hint = "Procurar..."
            textSize = 12f
            setTextColor(Color.parseColor("#cccccc"))
            setHintTextColor(Color.parseColor("#555558"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#3c3c3c"))
                cornerRadius = dp(3).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        searchCountLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#858585"))
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, dp(4), 0)
        }
        val closeSearch = makeSmallBtn("✕") { hideSearch() }
        searchBar.addView(searchInput)
        searchBar.addView(searchCountLabel)
        searchBar.addView(closeSearch)
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))

        // ── Editor area (line nums + code) ────────────────────────────────
        val editorArea = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        lineNumContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(colBg)
            setPadding(0, dp(14), 0, dp(14))
            minimumWidth = dp(46)
        }
        editorArea.addView(lineNumContainer, LinearLayout.LayoutParams(dp(46), LayoutParams.MATCH_PARENT))

        // Separator
        val sep = View(context).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
        }
        editorArea.addView(sep, LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT))

        // Code EditText
        codeEdit = EditText(context).apply {
            setBackgroundColor(colBg)
            setTextColor(colText)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setHorizontallyScrolling(false)
            isSingleLine = false
            inputType = (InputType.TYPE_CLASS_TEXT
                    or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            setTextIsSelectable(true)
        }

        val codeScroll = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = true
        }
        codeScroll.addView(codeEdit, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        editorArea.addView(codeScroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(editorArea)

        // ── Empty state ───────────────────────────────────────────────────
        emptyView = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colBg)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val emptyIcon = TextView(context).apply {
            text = "{ }"
            textSize = 48f
            setTextColor(Color.parseColor("#252526"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val emptyText = TextView(context).apply {
            text = "Abre o Explorer ou cria um novo projeto"
            textSize = 12f
            setTextColor(Color.parseColor("#555558"))
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(12), dp(32), 0)
        }
        emptyView.addView(emptyIcon)
        emptyView.addView(emptyText)
        addView(emptyView)

        // ── Wire text watcher ─────────────────────────────────────────────
        codeEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val path = currentPath ?: return
                val text = s.toString()
                onContentChanged?.invoke(path, text)
                updateLineNumbers(text)
                scheduleHighlight(path, text)
            }
        })

        // Search watcher
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { doSearch(s.toString()) }
        })

        showEmpty()
    }

    // ── Load file ─────────────────────────────────────────────────────────

    fun loadFile(path: String) {
        val file = EditorState.openFiles[path] ?: return
        currentPath = path

        emptyView.visibility = GONE
        toolbarRow.visibility = VISIBLE
        codeEdit.visibility = VISIBLE
        lineNumContainer.visibility = VISIBLE

        filePathLabel.text = path

        if (file.isBinary) {
            isUpdating = true
            codeEdit.setText("[Ficheiro binário — visualização não disponível]")
            codeEdit.isEnabled = false
            isUpdating = false
            return
        }

        codeEdit.isEnabled = true
        isUpdating = true
        codeEdit.setText(file.content)
        isUpdating = false
        updateLineNumbers(file.content)
        scheduleHighlight(path, file.content)

        // Initialise undo stack
        if (EditorState.undoStacks[path].isNullOrEmpty()) {
            EditorState.pushUndo(path, file.content)
        }
    }

    fun showEmpty() {
        currentPath = null
        toolbarRow.visibility = GONE
        codeEdit.visibility = GONE
        lineNumContainer.visibility = GONE
        searchBar.visibility = GONE
        emptyView.visibility = VISIBLE
    }

    // ── Line numbers ──────────────────────────────────────────────────────

    private fun updateLineNumbers(text: String) {
        val count = text.count { it == '\n' } + 1
        lineNumContainer.removeAllViews()
        for (i in 1..count) {
            val num = TextView(context).apply {
                this.text = i.toString()
                textSize = 13f
                setTextColor(Color.parseColor("#555558"))
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, 0, dp(8), 0)
                height = dp(21) // matches line height approx
            }
            lineNumContainer.addView(num)
        }
    }

    // ── Syntax highlight ──────────────────────────────────────────────────

    private fun scheduleHighlight(path: String, text: String) {
        highlightJob?.cancel()
        highlightJob = highlightScope.launch {
            delay(300)
            val ext = path.substringAfterLast('.', "").lowercase()
            val spannable = withContext(Dispatchers.Default) {
                buildHighlight(text, ext)
            }
            if (currentPath == path && !isUpdating) {
                isUpdating = true
                val sel = codeEdit.selectionStart
                codeEdit.text = spannable
                try { codeEdit.setSelection(sel.coerceIn(0, spannable.length)) } catch (_: Exception) {}
                isUpdating = false
            }
        }
    }

    private fun buildHighlight(text: String, ext: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)

        fun color(start: Int, end: Int, col: Int) {
            sb.setSpan(ForegroundColorSpan(col), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val codeExts = setOf("js","mjs","ts","jsx","tsx","dart","kt","kts","java","swift","go","rs","py","c","cpp","cs","sh","bash")

        when {
            ext in codeExts -> {
                // Comments
                applyRegex(sb, Regex("/\\*[\\s\\S]*?\\*/"), colCm)
                applyRegex(sb, Regex("//[^\n]*"), colCm)
                applyRegex(sb, Regex("#[^\n]*"), colCm)
                // Strings
                applyRegex(sb, Regex("`[^`]*`|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'"), colStr)
                // Numbers
                applyRegex(sb, Regex("\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*)\\b"), colNum)
                // Keywords
                val kw = getKeywords(ext)
                if (kw.isNotEmpty()) {
                    applyRegex(sb, Regex("\\b(${kw.joinToString("|")})\\b"), colKw)
                }
                // Types (PascalCase)
                applyRegex(sb, Regex("\\b[A-Z][A-Za-z0-9_]+\\b"), colTp)
                // Functions
                applyRegex(sb, Regex("\\b([a-z_][a-zA-Z0-9_]*)\\s*(?=\\()"), colFn)
            }
            ext in setOf("html","htm","xml","svg") -> {
                applyRegex(sb, Regex("<!--[\\s\\S]*?-->"), colCm)
                applyRegex(sb, Regex("</?[\\w:-]+"), colKw)
                applyRegex(sb, Regex("\"[^\"]*\"|'[^']*'"), colStr)
                applyRegex(sb, Regex("\\b[\\w:-]+="), colFn)
            }
            ext in setOf("css","scss","sass","less") -> {
                applyRegex(sb, Regex("/\\*[\\s\\S]*?\\*/"), colCm)
                applyRegex(sb, Regex("@[\\w-]+"), colKw)
                applyRegex(sb, Regex("#[0-9a-fA-F]{3,8}\\b"), colNum)
                applyRegex(sb, Regex("\\b\\d+\\.?\\d*(px|em|rem|vh|vw|%|s|ms|deg)?\\b"), colNum)
                applyRegex(sb, Regex("[\\w-]+\\s*:"), colKw)
                applyRegex(sb, Regex("\"[^\"]*\"|'[^']*'"), colStr)
            }
            ext == "json" -> {
                applyRegex(sb, Regex("\"[^\"]+\"\\s*:"), colKw)
                applyRegex(sb, Regex(":\\s*\"[^\"]*\""), colStr)
                applyRegex(sb, Regex(":\\s*\\d+\\.?\\d*"), colNum)
                applyRegex(sb, Regex("\\b(true|false|null)\\b"), colKw)
            }
            ext in setOf("yaml","yml") -> {
                applyRegex(sb, Regex("#[^\n]*"), colCm)
                applyRegex(sb, Regex("^[\\w-]+\\s*:", RegexOption.MULTILINE), colKw)
                applyRegex(sb, Regex("\"[^\"]*\"|'[^']*'"), colStr)
            }
            ext == "md" -> {
                applyRegex(sb, Regex("^#{1,6}\\s.+", RegexOption.MULTILINE), colKw)
                applyRegex(sb, Regex("`[^`\n]+`"), colStr)
                applyRegex(sb, Regex("\\*\\*[^*\n]+\\*\\*|__[^_\n]+__"), colFn)
                applyRegex(sb, Regex("\\*[^*\n]+\\*|_[^_\n]+_"), colTp)
                applyRegex(sb, Regex("^>\\s.+", RegexOption.MULTILINE), colCm)
            }
        }
        return sb
    }

    private fun applyRegex(sb: SpannableStringBuilder, regex: Regex, color: Int) {
        regex.findAll(sb).forEach { match ->
            try {
                sb.setSpan(
                    ForegroundColorSpan(color),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } catch (_: Exception) {}
        }
    }

    private fun getKeywords(ext: String): List<String> = when (ext) {
        "kt","kts" -> listOf("abstract","as","break","by","catch","class","companion","const",
            "constructor","continue","data","do","else","enum","false","final","finally","for",
            "fun","get","if","import","in","init","inline","inner","interface","internal","is",
            "it","lateinit","null","object","open","operator","override","package","private",
            "protected","public","return","sealed","set","super","suspend","this","throw","true",
            "try","typealias","val","var","vararg","when","where","while")
        "dart" -> listOf("abstract","as","assert","async","await","break","case","catch","class",
            "const","continue","default","do","dynamic","else","enum","extends","external",
            "factory","false","final","finally","for","Function","get","if","implements","import",
            "in","interface","is","late","library","mixin","new","null","on","operator","part",
            "required","rethrow","return","set","show","static","super","switch","sync","this",
            "throw","true","try","typedef","var","void","while","with","yield")
        "java" -> listOf("abstract","assert","boolean","break","byte","case","catch","char",
            "class","const","continue","default","do","double","else","enum","extends","false",
            "final","finally","float","for","if","implements","import","instanceof","int",
            "interface","long","native","new","null","package","private","protected","public",
            "return","short","static","super","switch","synchronized","this","throw","throws",
            "true","try","void","volatile","while")
        "ts","tsx" -> listOf("abstract","as","async","await","break","case","catch","class",
            "const","continue","declare","default","delete","do","else","enum","export","extends",
            "false","finally","for","from","function","if","implements","import","in","instanceof",
            "interface","let","namespace","new","null","of","private","protected","public",
            "readonly","return","static","super","switch","this","throw","true","try","type",
            "typeof","undefined","var","void","while","yield","override")
        "js","jsx","mjs" -> listOf("async","await","break","case","catch","class","const",
            "continue","debugger","default","delete","do","else","export","extends","false",
            "finally","for","function","if","import","in","instanceof","let","new","null","of",
            "return","super","switch","this","throw","true","try","typeof","undefined","var",
            "void","while","yield")
        "py" -> listOf("False","None","True","and","as","assert","async","await","break","class",
            "continue","def","del","elif","else","except","finally","for","from","global","if",
            "import","in","is","lambda","nonlocal","not","or","pass","raise","return","try",
            "while","with","yield")
        "go" -> listOf("break","case","chan","const","continue","default","defer","else",
            "fallthrough","for","func","go","goto","if","import","interface","map","package",
            "range","return","select","struct","switch","type","var","nil","true","false","iota")
        "rs" -> listOf("as","async","await","break","const","continue","crate","dyn","else",
            "enum","extern","false","fn","for","if","impl","in","let","loop","match","mod",
            "move","mut","pub","ref","return","self","Self","static","struct","super","trait",
            "true","type","unsafe","use","where","while")
        "swift" -> listOf("associatedtype","class","deinit","enum","extension","fileprivate",
            "func","import","init","inout","internal","let","open","operator","private","protocol",
            "public","rethrows","return","static","struct","subscript","typealias","var","break",
            "case","continue","default","defer","do","else","fallthrough","for","guard","if","in",
            "repeat","throw","switch","where","while","as","catch","false","is","nil","super",
            "self","Self","true","try")
        "cs" -> listOf("abstract","as","base","bool","break","byte","case","catch","char",
            "checked","class","const","continue","decimal","default","delegate","do","double",
            "else","enum","event","explicit","extern","false","finally","float","for","foreach",
            "goto","if","implicit","in","int","interface","internal","is","lock","long",
            "namespace","new","null","object","operator","out","override","params","private",
            "protected","public","readonly","ref","return","sbyte","sealed","short","sizeof",
            "static","string","struct","switch","this","throw","true","try","typeof","uint",
            "ulong","unchecked","unsafe","ushort","using","virtual","void","volatile","while",
            "async","await","dynamic","var","yield")
        "sh","bash" -> listOf("if","then","else","elif","fi","for","while","do","done","case",
            "esac","function","return","export","local","echo","cd","ls","pwd","mkdir","rm","cp",
            "mv","cat","grep","sed","awk","curl","wget","source","exit","in","read")
        "c","cpp" -> listOf("auto","break","case","char","const","continue","default","do",
            "double","else","enum","extern","float","for","goto","if","inline","int","long",
            "return","short","signed","sizeof","static","struct","switch","typedef","union",
            "unsigned","void","volatile","while","NULL","true","false","class","namespace",
            "new","delete","public","private","protected","virtual","override","template",
            "typename","nullptr","bool","constexpr","auto")
        else -> emptyList()
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────

    fun undo() {
        val path = currentPath ?: return
        val content = EditorState.undo(path) ?: return
        isUpdating = true
        codeEdit.setText(content)
        isUpdating = false
        updateLineNumbers(content)
        EditorState.openFiles[path]?.content = content
    }

    fun redo() {
        val path = currentPath ?: return
        val content = EditorState.redo(path) ?: return
        isUpdating = true
        codeEdit.setText(content)
        isUpdating = false
        updateLineNumbers(content)
        EditorState.openFiles[path]?.content = content
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun toggleSearch() {
        if (searchBar.visibility == VISIBLE) hideSearch()
        else {
            searchBar.visibility = VISIBLE
            searchInput.requestFocus()
        }
    }

    private fun hideSearch() {
        searchBar.visibility = GONE
        searchCountLabel.text = ""
    }

    private fun doSearch(query: String) {
        if (query.isEmpty()) { searchCountLabel.text = ""; return }
        val text = codeEdit.text.toString()
        val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).toList()
        searchCountLabel.text = "${matches.size} resultado(s)"
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        val bg = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
        val toolbar = if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3")
        codeEdit.setBackgroundColor(bg)
        codeEdit.setTextColor(colText)
        lineNumContainer.setBackgroundColor(bg)
        toolbarRow.setBackgroundColor(toolbar)
        emptyView.setBackgroundColor(bg)
        currentPath?.let { loadFile(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun makeSmallBtn(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#858585"))
            gravity = Gravity.CENTER
            val sz = dp(28)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}