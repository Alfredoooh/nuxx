package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class EditorTerminal(context: Context) : LinearLayout(context) {

    private lateinit var tabBar: LinearLayout
    private lateinit var tabGit: TextView
    private lateinit var tabTerm: TextView
    private lateinit var tabOut: TextView
    private lateinit var panelGit: LinearLayout
    private lateinit var panelTerm: LinearLayout
    private lateinit var panelOut: LinearLayout
    private lateinit var termOutput: LinearLayout
    private lateinit var termScroll: ScrollView
    private lateinit var termInput: EditText
    private lateinit var termPrompt: TextView
    private lateinit var gitOutput: LinearLayout
    private lateinit var outOutput: LinearLayout
    private lateinit var commitInput: EditText

    private var activePanel = "git"
    private var isCollapsed = false
    private var isDark = true
    private var cwd = "~/project"
    private val cmdHistory = ArrayDeque<String>()
    private var histIdx = -1

    companion object {
        private const val EXPANDED_H_DP  = 210
        private const val COLLAPSED_H_DP = 28
    }

    init {
        orientation = VERTICAL
        buildLayout()
    }

    private fun buildLayout() {
        // ── Tab bar ───────────────────────────────────────────────────────
        tabBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#2d2d30"))
        }
        tabGit  = buildTabLabel("Git",      "git")
        tabTerm = buildTabLabel("Terminal", "terminal")
        tabOut  = buildTabLabel("Output",   "output")
        tabBar.addView(tabGit)
        tabBar.addView(tabTerm)
        tabBar.addView(tabOut)

        val rightSection = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setPadding(0, 0, dp(4), 0)
        }
        rightSection.addView(makeIconBtn(IconPaths.TRASH)   { clearActivePanel() })
        rightSection.addView(makeIconBtn(IconPaths.CHEVRON_DOWN) { toggleCollapse() })
        tabBar.addView(rightSection)
        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(COLLAPSED_H_DP)))

        // ── Git panel ─────────────────────────────────────────────────────
        panelGit = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
        }
        val gitScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        gitOutput = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        gitScroll.addView(gitOutput)
        panelGit.addView(gitScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val gitBorderLine = View(context).apply {
            setBackgroundColor(Color.parseColor("#3e3e42"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }
        val gitActionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.parseColor("#252526"))
        }
        commitInput = EditText(context).apply {
            hint = "Mensagem do commit..."
            textSize = 11.5f
            setTextColor(Color.parseColor("#cccccc"))
            setHintTextColor(Color.parseColor("#444448"))
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(4), dp(8), dp(4))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#3c3c3c"))
                cornerRadius = dp(3).toFloat()
            }
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val stageBtn = makeActionBtn("Guardar", "#0e7af0") {
            val msg = commitInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                val dirty = EditorState.openFiles.filter { it.value.dirty || it.value.isNew }.keys.toList()
                dirty.forEach { EditorState.stageFile(it) }
                log("${dirty.size} ficheiro(s) staged: \"$msg\"", "ok")
                commitInput.text.clear()
            }
        }
        gitActionRow.addView(commitInput)
        gitActionRow.addView(stageBtn)
        panelGit.addView(gitBorderLine)
        panelGit.addView(gitActionRow, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))
        addView(panelGit, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Terminal panel ────────────────────────────────────────────────
        panelTerm = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#0d0d0d"))
            visibility = GONE
        }
        termScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        termOutput = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        termScroll.addView(termOutput)
        panelTerm.addView(termScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val termBorderLine = View(context).apply {
            setBackgroundColor(Color.parseColor("#1a1a1a"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }
        val inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0a0a0a"))
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        termPrompt = TextView(context).apply {
            text = "$cwd \$ "
            textSize = 12f
            setTextColor(Color.parseColor("#4ec9b0"))
            typeface = Typeface.MONOSPACE
        }
        termInput = EditText(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#d4d4d4"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = "comando..."
            setHintTextColor(Color.parseColor("#2a2a2e"))
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        }
        termInput.setOnEditorActionListener { _, _, _ ->
            val cmd = termInput.text.toString()
            termInput.text.clear()
            execCommand(cmd)
            true
        }
        inputRow.addView(termPrompt)
        inputRow.addView(termInput)
        panelTerm.addView(termBorderLine)
        panelTerm.addView(inputRow, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))
        addView(panelTerm, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Output panel ──────────────────────────────────────────────────
        panelOut = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#1e1e1e"))
            visibility = GONE
        }
        val outScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        outOutput = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        outScroll.addView(outOutput)
        panelOut.addView(outScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panelOut, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        setActiveTab("git")
        expand()
        termPrint("XCode Terminal — escreve \"help\" para ver os comandos", "dim")
    }

    // ── Tab switching ─────────────────────────────────────────────────────

    private fun buildTabLabel(text: String, id: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.parseColor(if (id == activePanel) "#cccccc" else "#555558"))
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER_VERTICAL
            height = dp(COLLAPSED_H_DP)
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { setActiveTab(id); if (isCollapsed) expand() }
        }

    private fun setActiveTab(id: String) {
        activePanel = id
        listOf(tabGit to "git", tabTerm to "terminal", tabOut to "output").forEach { (tab, tid) ->
            tab.setTextColor(Color.parseColor(if (tid == id) "#cccccc" else "#555558"))
        }
        panelGit.visibility  = if (id == "git")      VISIBLE else GONE
        panelTerm.visibility = if (id == "terminal") VISIBLE else GONE
        panelOut.visibility  = if (id == "output")   VISIBLE else GONE
        if (id == "terminal") termInput.requestFocus()
    }

    fun toggleCollapse() { if (isCollapsed) expand() else collapse() }

    private fun collapse() {
        isCollapsed = true
        panelGit.visibility  = GONE
        panelTerm.visibility = GONE
        panelOut.visibility  = GONE
        (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.height = dp(COLLAPSED_H_DP); lp.weight = 0f; layoutParams = lp
        }
    }

    private fun expand() {
        isCollapsed = false
        setActiveTab(activePanel)
        (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.height = dp(EXPANDED_H_DP); lp.weight = 0f; layoutParams = lp
        }
    }

    fun clearActivePanel() {
        when (activePanel) {
            "git"      -> gitOutput.removeAllViews()
            "terminal" -> termOutput.removeAllViews()
            "output"   -> outOutput.removeAllViews()
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────

    fun log(msg: String, type: String = "info") {
        val ts    = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val color = typeColor(type)
        gitOutput.addView(makeLogRow(ts, msg, Color.parseColor(color)))
        outOutput.addView(makeLogRow(ts, msg, Color.parseColor(color)))
    }

    private fun typeColor(type: String) = when (type) {
        "ok"   -> "#4ec9b0"
        "err"  -> "#f44747"
        "warn" -> "#e2c08d"
        "cmd"  -> "#7ec8e3"
        "dim"  -> "#555558"
        else   -> "#cccccc"
    }

    private fun makeLogRow(ts: String, msg: String, color: Int): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(TextView(context).apply {
            text = "$ts  "
            textSize = 11f
            setTextColor(Color.parseColor("#333336"))
            typeface = Typeface.MONOSPACE
        })
        row.addView(TextView(context).apply {
            text = msg
            textSize = 11.5f
            setTextColor(color)
            typeface = Typeface.MONOSPACE
            layoutParams = LayoutParams(0, WRAP_CONTENT, 1f)
        })
        return row
    }

    // ── Terminal ──────────────────────────────────────────────────────────

    private fun termPrint(text: String, type: String = "info") {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        termOutput.addView(makeLogRow(ts, text, Color.parseColor(typeColor(type))))
        termScroll.post { termScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun execCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return
        cmdHistory.addFirst(cmd)
        histIdx = -1
        termPrint("$cwd \$ $cmd", "cmd")
        val parts = cmd.split(Regex("\\s+"))
        val c = parts[0]; val args = parts.drop(1)

        when (c) {
            "clear" -> termOutput.removeAllViews()
            "echo"  -> termPrint(args.joinToString(" "))
            "pwd"   -> termPrint(cwd)
            "ls" -> {
                val keys = if (EditorState.isLocalMode)
                    EditorState.localProject?.files?.keys ?: emptySet()
                else EditorState.openFiles.keys
                val tops = keys.map { it.split('/')[0] }.toSortedSet()
                termPrint(if (tops.isEmpty()) "(vazio)" else tops.joinToString("  "))
            }
            "cat" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: cat <ficheiro>", "err"); return }
                val files = if (EditorState.isLocalMode) EditorState.localProject?.files
                else EditorState.openFiles.mapValues { it.value.content }
                val content = files?.get(fname)
                    ?: files?.entries?.firstOrNull { it.key.endsWith("/$fname") }?.value
                if (content != null) termPrint(content)
                else termPrint("cat: $fname: nao encontrado", "err")
            }
            "cd" -> {
                cwd = when {
                    args.isEmpty() || args[0] == "~" -> "~/project"
                    args[0] == ".." -> cwd.substringBeforeLast('/').ifEmpty { "~" }
                    else -> "$cwd/${args[0]}"
                }
                termPrompt.text = "$cwd \$ "
            }
            "wc" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: wc <ficheiro>", "err"); return }
                val files = if (EditorState.isLocalMode) EditorState.localProject?.files
                else EditorState.openFiles.mapValues { it.value.content }
                val content = files?.get(fname) ?: run { termPrint("wc: $fname: nao encontrado", "err"); return }
                termPrint("${content.lines().size} ${content.trim().split(Regex("\\s+")).size} ${content.length} $fname")
            }
            "grep" -> {
                if (args.size < 2) { termPrint("Uso: grep <padrao> <ficheiro>", "err"); return }
                val files = if (EditorState.isLocalMode) EditorState.localProject?.files
                else EditorState.openFiles.mapValues { it.value.content }
                val content = files?.get(args[1]) ?: run { termPrint("grep: ${args[1]}: nao encontrado", "err"); return }
                val matches = content.lines().filter { it.contains(args[0], ignoreCase = true) }
                if (matches.isEmpty()) termPrint("(sem resultados)", "dim") else matches.forEach { termPrint(it) }
            }
            "head" -> {
                val n = if (args.contains("-n")) args.getOrNull(args.indexOf("-n") + 1)?.toIntOrNull() ?: 10 else 10
                val fname = args.firstOrNull { !it.startsWith("-") && it.toIntOrNull() == null }
                    ?: run { termPrint("Uso: head [-n N] <ficheiro>", "err"); return }
                val files = if (EditorState.isLocalMode) EditorState.localProject?.files
                else EditorState.openFiles.mapValues { it.value.content }
                val content = files?.get(fname) ?: run { termPrint("head: $fname: nao encontrado", "err"); return }
                content.lines().take(n).forEach { termPrint(it) }
            }
            "tail" -> {
                val n = if (args.contains("-n")) args.getOrNull(args.indexOf("-n") + 1)?.toIntOrNull() ?: 10 else 10
                val fname = args.firstOrNull { !it.startsWith("-") && it.toIntOrNull() == null }
                    ?: run { termPrint("Uso: tail [-n N] <ficheiro>", "err"); return }
                val files = if (EditorState.isLocalMode) EditorState.localProject?.files
                else EditorState.openFiles.mapValues { it.value.content }
                val content = files?.get(fname) ?: run { termPrint("tail: $fname: nao encontrado", "err"); return }
                content.lines().takeLast(n).forEach { termPrint(it) }
            }
            "find" -> {
                val pattern = args.firstOrNull() ?: ""
                val keys = if (EditorState.isLocalMode) EditorState.localProject?.files?.keys ?: emptySet()
                else EditorState.openFiles.keys
                val found = keys.filter { it.contains(pattern) }
                if (found.isEmpty()) termPrint("(sem resultados)", "dim") else found.forEach { termPrint("./$it") }
            }
            "touch" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: touch <ficheiro>", "err"); return }
                if (!EditorState.isLocalMode) { termPrint("touch: apenas em modo local", "warn"); return }
                EditorState.localProject?.files?.set(fname, "")
                termPrint("Criado: $fname", "ok")
            }
            "rm" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: rm <ficheiro>", "err"); return }
                if (!EditorState.isLocalMode) { termPrint("rm: apenas em modo local", "warn"); return }
                val p = EditorState.localProject ?: return
                if (p.files.remove(fname) != null) termPrint("Removido: $fname", "ok")
                else termPrint("rm: $fname: nao encontrado", "err")
            }
            "mv" -> {
                if (args.size < 2) { termPrint("Uso: mv <origem> <destino>", "err"); return }
                if (!EditorState.isLocalMode) { termPrint("mv: apenas em modo local", "warn"); return }
                val p = EditorState.localProject ?: return
                if (p.files.containsKey(args[0])) {
                    p.files[args[1]] = p.files.remove(args[0])!!
                    termPrint("Movido: ${args[0]} -> ${args[1]}", "ok")
                } else termPrint("mv: ${args[0]}: nao encontrado", "err")
            }
            "cp" -> {
                if (args.size < 2) { termPrint("Uso: cp <origem> <destino>", "err"); return }
                if (!EditorState.isLocalMode) { termPrint("cp: apenas em modo local", "warn"); return }
                val p = EditorState.localProject ?: return
                if (p.files.containsKey(args[0])) {
                    p.files[args[1]] = p.files[args[0]]!!
                    termPrint("Copiado: ${args[0]} -> ${args[1]}", "ok")
                } else termPrint("cp: ${args[0]}: nao encontrado", "err")
            }
            "help" -> {
                termPrint("Comandos disponiveis:", "ok")
                termPrint("  ls  cat  cd  pwd  echo  clear")
                termPrint("  wc  grep  head  tail  find")
                termPrint("  touch  rm  mv  cp  help")
                termPrint("  (ambiente simulado - sem acesso ao sistema real)", "dim")
            }
            else -> termPrint("$c: comando nao encontrado. Escreve \"help\".", "err")
        }
    }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
        tabBar.setBackgroundColor(if (isDark) Color.parseColor("#2d2d30") else Color.parseColor("#ececec"))
        panelGit.setBackgroundColor(if (isDark) Color.parseColor("#252526") else Color.parseColor("#f3f3f3"))
    }

    private fun makeIconBtn(svgPath: String, onClick: () -> Unit): XCodeIcon =
        XCodeIcon(context, svgPath, Color.parseColor("#858585"), dp(13)).apply {
            val sz = dp(28)
            layoutParams = LayoutParams(sz, sz).apply { marginStart = dp(2) }
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#22ffffff")), null, null
            )
            setOnClickListener { onClick() }
        }

    private fun makeActionBtn(label: String, color: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = dp(3).toFloat()
            }
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(6) }
            isClickable = true
            isFocusable = true
            foreground = RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#33000000")), null, null
            )
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}