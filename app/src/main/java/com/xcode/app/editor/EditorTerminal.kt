// EditorTerminal.kt
package com.xcode.app.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class EditorTerminal(context: Context) : LinearLayout(context) {

    private lateinit var tabBar: LinearLayout
    private lateinit var outputContainer: LinearLayout
    private lateinit var outputScroll: ScrollView
    private lateinit var promptLabel: TextView
    private lateinit var inputField: EditText
    private lateinit var panelGit: LinearLayout
    private lateinit var panelTerm: LinearLayout
    private lateinit var panelOut: LinearLayout

    private var isDark = true
    private var isCollapsed = false
    private var activePanel = "git"
    private var cwd = "~/project"
    private val history = ArrayDeque<String>()
    private var historyIdx = -1

    companion object {
        private val COLLAPSED_H_DP = 28
        private val EXPANDED_H_DP = 200
    }

    init {
        orientation = VERTICAL
        buildLayout()
    }

    private fun buildLayout() {
        tabBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#2d2d30"))
        }

        val tabGit = makeTab("⎇  Git", "git")
        val tabTerm = makeTab("▶  Terminal", "terminal")
        val tabOut = makeTab("≡  Output", "output")

        tabBar.addView(tabGit)
        tabBar.addView(tabTerm)
        tabBar.addView(tabOut)

        val rightRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        rightRow.addView(makeHeaderBtn("🗑") { clearActivePanel() })
        rightRow.addView(makeHeaderBtn("⌃") { toggleCollapse() })
        tabBar.addView(rightRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(COLLAPSED_H_DP)))

        // ── Git panel ─────────────────────────────────────────────────────
        panelGit = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
        }
        val gitScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        val gitContent = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            id = View.generateViewId()
            tag = "git_content"
        }
        gitScroll.addView(gitContent)
        panelGit.addView(gitScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val gitActions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#252526"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val commitInput = EditText(context).apply {
            hint = "Mensagem do commit..."
            textSize = 11.5f
            setTextColor(Color.parseColor("#cccccc"))
            setHintTextColor(Color.parseColor("#555558"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.TRANSPARENT)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#3c3c3c"))
                cornerRadius = dp(3).toFloat()
            }
            background = bg
            setPadding(dp(8), dp(4), dp(8), dp(4))
            inputType = InputType.TYPE_CLASS_TEXT
        }
        gitActions.addView(commitInput, LayoutParams(0, WRAP_CONTENT, 1f))
        gitActions.addView(makeActionBtn("Guardar", "#0e7af0") { /* stage */ })
        panelGit.addView(gitActions, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        addView(panelGit, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Terminal panel ────────────────────────────────────────────────
        panelTerm = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#0d0d0d"))
            visibility = GONE
        }
        outputScroll = ScrollView(context).apply { overScrollMode = OVER_SCROLL_NEVER }
        outputContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        outputScroll.addView(outputContainer)
        panelTerm.addView(outputScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(Color.parseColor("#111111"))
        }
        promptLabel = TextView(context).apply {
            text = "$cwd $ "
            textSize = 12.5f
            setTextColor(Color.parseColor("#4ec9b0"))
            typeface = Typeface.MONOSPACE
        }
        inputField = EditText(context).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#d4d4d4"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = "comando..."
            setHintTextColor(Color.parseColor("#333336"))
        }
        inputField.setOnEditorActionListener { _, _, _ ->
            val cmd = inputField.text.toString()
            inputField.text.clear()
            execCommand(cmd)
            true
        }
        inputRow.addView(promptLabel)
        inputRow.addView(inputField, LayoutParams(0, WRAP_CONTENT, 1f))
        panelTerm.addView(inputRow, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(panelTerm, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Output panel ──────────────────────────────────────────────────
        panelOut = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#1e1e1e"))
            visibility = GONE
        }
        addView(panelOut, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        expand()
        termPrint("XCode Terminal — escreve \"help\" para ver os comandos", "dim")
    }

    private fun makeTab(label: String, id: String): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (id == activePanel) Color.parseColor("#cccccc") else Color.parseColor("#858585"))
            setPadding(dp(14), 0, dp(14), 0)
            gravity = Gravity.CENTER_VERTICAL
            height = dp(28)
            tag = "ptab_$id"
            isClickable = true
            isFocusable = true
            setOnClickListener { switchPanel(id) }
        }
    }

    fun switchPanel(id: String) {
        activePanel = id
        panelGit.visibility = if (id == "git") VISIBLE else GONE
        panelTerm.visibility = if (id == "terminal") VISIBLE else GONE
        panelOut.visibility = if (id == "output") VISIBLE else GONE
        if (isCollapsed) expand()
        if (id == "terminal") inputField.requestFocus()
    }

    fun toggleCollapse() {
        if (isCollapsed) expand() else collapse()
    }

    private fun collapse() {
        isCollapsed = true
        panelGit.visibility = GONE
        panelTerm.visibility = GONE
        panelOut.visibility = GONE
        val lp = layoutParams as? android.widget.LinearLayout.LayoutParams
        lp?.weight = 0f
        lp?.height = dp(COLLAPSED_H_DP)
        layoutParams = lp
    }

    private fun expand() {
        isCollapsed = false
        switchPanel(activePanel)
        val lp = layoutParams as? android.widget.LinearLayout.LayoutParams
        lp?.weight = 0f
        lp?.height = dp(EXPANDED_H_DP)
        layoutParams = lp
    }

    fun clearActivePanel() {
        when (activePanel) {
            "git" -> (outputContainer.parent as? LinearLayout)?.removeAllViews()
            "terminal" -> outputContainer.removeAllViews()
            "output" -> panelOut.removeAllViews()
        }
    }

    private fun termPrint(text: String, type: String = "info") {
        val color = when (type) {
            "ok" -> "#4ec9b0"
            "err" -> "#f44747"
            "warn" -> "#e2c08d"
            "cmd" -> "#7ec8e3"
            "dim" -> "#555558"
            else -> "#d4d4d4"
        }
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        val timeV = TextView(context).apply {
            this.text = "$ts  "
            textSize = 11.5f
            setTextColor(Color.parseColor("#333336"))
            typeface = Typeface.MONOSPACE
        }
        val textV = TextView(context).apply {
            this.text = text
            textSize = 12.5f
            setTextColor(Color.parseColor(color))
            typeface = Typeface.MONOSPACE
        }
        row.addView(timeV)
        row.addView(textV, LayoutParams(0, WRAP_CONTENT, 1f))
        outputContainer.addView(row)
        outputScroll.post { outputScroll.fullScroll(FOCUS_DOWN) }
    }

    private fun execCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return
        history.addFirst(cmd)
        historyIdx = -1
        termPrint("$cwd \$ $cmd", "cmd")

        val parts = cmd.split(Regex("\\s+"))
        val c = parts[0]
        val args = parts.drop(1)

        when (c) {
            "clear" -> outputContainer.removeAllViews()
            "echo" -> termPrint(args.joinToString(" "))
            "pwd" -> termPrint(cwd)
            "ls" -> {
                val files = EditorState.localProject?.files?.keys?.map { it.split('/')[0] }?.toSet()
                    ?: EditorState.openFiles.keys.map { it.split('/')[0] }.toSet()
                termPrint(if (files.isEmpty()) "(vazio)" else files.sorted().joinToString("  "))
            }
            "cat" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: cat <ficheiro>", "err"); return }
                val files = EditorState.localProject?.files ?: EditorState.openFiles.mapValues { it.value.content }
                val content = files[fname] ?: files.entries.firstOrNull { it.key.endsWith("/$fname") }?.value
                if (content != null) termPrint(content) else termPrint("cat: $fname: não encontrado", "err")
            }
            "cd" -> {
                cwd = when {
                    args.isEmpty() || args[0] == "~" -> "~/project"
                    args[0] == ".." -> cwd.substringBeforeLast('/').ifEmpty { "~" }
                    else -> "$cwd/${args[0]}"
                }
                promptLabel.text = "$cwd \$ "
            }
            "wc" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: wc <ficheiro>", "err"); return }
                val files = EditorState.localProject?.files ?: EditorState.openFiles.mapValues { it.value.content }
                val content = files[fname]
                if (content != null) {
                    termPrint("${content.lines().size} ${content.trim().split(Regex("\\s+")).size} ${content.length} $fname")
                } else termPrint("wc: $fname: não encontrado", "err")
            }
            "grep" -> {
                if (args.size < 2) { termPrint("Uso: grep <padrão> <ficheiro>", "err"); return }
                val files = EditorState.localProject?.files ?: EditorState.openFiles.mapValues { it.value.content }
                val content = files[args[1]] ?: run { termPrint("grep: ${args[1]}: não encontrado", "err"); return }
                val matches = content.lines().filter { it.contains(args[0], ignoreCase = true) }
                if (matches.isEmpty()) termPrint("(sem resultados)", "dim")
                else matches.forEach { termPrint(it) }
            }
            "head" -> {
                val n = if (args.contains("-n")) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
                val fname = args.firstOrNull { !it.startsWith("-") && it != n.toString() }
                    ?: run { termPrint("Uso: head [-n N] <ficheiro>", "err"); return }
                val files = EditorState.localProject?.files ?: EditorState.openFiles.mapValues { it.value.content }
                val content = files[fname] ?: run { termPrint("head: $fname: não encontrado", "err"); return }
                content.lines().take(n).forEach { termPrint(it) }
            }
            "tail" -> {
                val n = if (args.contains("-n")) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
                val fname = args.firstOrNull { !it.startsWith("-") && it != n.toString() }
                    ?: run { termPrint("Uso: tail [-n N] <ficheiro>", "err"); return }
                val files = EditorState.localProject?.files ?: EditorState.openFiles.mapValues { it.value.content }
                val content = files[fname] ?: run { termPrint("tail: $fname: não encontrado", "err"); return }
                content.lines().takeLast(n).forEach { termPrint(it) }
            }
            "find" -> {
                val pattern = args.firstOrNull() ?: ""
                val files = EditorState.localProject?.files?.keys ?: EditorState.openFiles.keys
                val found = files.filter { it.contains(pattern) }
                if (found.isEmpty()) termPrint("(sem resultados)", "dim")
                else found.forEach { termPrint("./$it") }
            }
            "touch" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: touch <ficheiro>", "err"); return }
                EditorState.localProject?.files?.set(fname, "") ?: run { termPrint("touch: apenas em modo local", "warn"); return }
                termPrint("Criado: $fname", "ok")
            }
            "rm" -> {
                val fname = args.firstOrNull() ?: run { termPrint("Uso: rm <ficheiro>", "err"); return }
                val p = EditorState.localProject ?: run { termPrint("rm: apenas em modo local", "warn"); return }
                if (p.files.remove(fname) != null) termPrint("Removido: $fname", "ok")
                else termPrint("rm: $fname: não encontrado", "err")
            }
            "mv" -> {
                if (args.size < 2) { termPrint("Uso: mv <origem> <destino>", "err"); return }
                val p = EditorState.localProject ?: run { termPrint("mv: apenas em modo local", "warn"); return }
                if (p.files.containsKey(args[0])) { p.files[args[1]] = p.files.remove(args[0])!!; termPrint("Movido: ${args[0]} → ${args[1]}", "ok") }
                else termPrint("mv: ${args[0]}: não encontrado", "err")
            }
            "cp" -> {
                if (args.size < 2) { termPrint("Uso: cp <origem> <destino>", "err"); return }
                val p = EditorState.localProject ?: run { termPrint("cp: apenas em modo local", "warn"); return }
                if (p.files.containsKey(args[0])) { p.files[args[1]] = p.files[args[0]]!!; termPrint("Copiado: ${args[0]} → ${args[1]}", "ok") }
                else termPrint("cp: ${args[0]}: não encontrado", "err")
            }
            "help" -> {
                termPrint("Comandos disponíveis:", "ok")
                termPrint("  ls, cat, cd, pwd, echo, clear, wc, grep, head, tail, find, touch, rm, mv, cp, help")
                termPrint("  (ambiente simulado — sem acesso ao sistema real)", "dim")
            }
            else -> termPrint("$c: comando não encontrado. Escreve \"help\".", "err")
        }
    }

    fun log(msg: String, type: String = "info") {
        termPrint(msg, type)
    }

    fun applyTheme(isDark: Boolean) {
        this.isDark = isDark
    }

    private fun makeHeaderBtn(icon: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = icon
            textSize = 13f
            setTextColor(Color.parseColor("#858585"))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }.also { btn ->
            val sz = dp(28)
            val lp = LayoutParams(sz, sz)
            lp.marginStart = dp(2)
            lp.marginEnd = dp(2)
            btn.layoutParams = lp
        }

    private fun makeActionBtn(label: String, color: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = dp(3).toFloat()
            }
            background = bg
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }.also { btn ->
            val lp = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            lp.marginStart = dp(6)
            btn.layoutParams = lp
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}