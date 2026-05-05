package com.xcode.app.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EditorState {

    // ── Repo config ───────────────────────────────────────────────────────
    data class RepoEntry(val name: String, val ownerRepo: String, val token: String)

    private val defaultRepos = mutableListOf(
        RepoEntry("nuxx", "Alfredoooh/nuxx", "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")
    )

    var repos: MutableList<RepoEntry> = mutableListOf()
    var activeRepoIdx: Int = 0
    val activeRepo get() = repos.getOrElse(activeRepoIdx) { repos.first() }

    // ── Git state ─────────────────────────────────────────────────────────
    var currentBranch: String = "main"

    // ── Open files ────────────────────────────────────────────────────────
    data class OpenFile(
        val path: String,
        var content: String,
        var sha: String?,
        var dirty: Boolean = false,
        var isNew: Boolean = false,
        var isLocal: Boolean = false,
        var isBinary: Boolean = false,
        var rawBase64: String = "",
        var mimeType: String = ""
    )

    val openFiles: MutableMap<String, OpenFile> = mutableMapOf()
    var activeFilePath: String? = null

    // ── Staged files ──────────────────────────────────────────────────────
    data class StagedFile(
        val path: String,
        val content: String,
        val sha: String?,
        val isNew: Boolean,
        val isBinary: Boolean,
        val rawBase64: String
    )

    val stagedFiles: MutableMap<String, StagedFile> = mutableMapOf()

    // ── Local project ─────────────────────────────────────────────────────
    var localProject: LocalProject? = null
    var isLocalMode: Boolean = false

    // ── Tree ──────────────────────────────────────────────────────────────
    var treeItems: List<GitFile> = emptyList()

    // ── Theme ─────────────────────────────────────────────────────────────
    var isDark: Boolean = true

    // ── Editor settings ───────────────────────────────────────────────────
    var fontSize: Int = 13
    var tabSize: Int = 2
    var wordWrap: Boolean = false
    var autoSave: Boolean = false
    var formatOnSave: Boolean = false

    // ── Undo / Redo ───────────────────────────────────────────────────────
    private val undoStacks: MutableMap<String, ArrayDeque<String>> = mutableMapOf()
    private val redoStacks: MutableMap<String, ArrayDeque<String>> = mutableMapOf()

    fun pushUndo(path: String, content: String) {
        val stack = undoStacks.getOrPut(path) { ArrayDeque() }
        if (stack.lastOrNull() == content) return
        stack.addLast(content)
        if (stack.size > 300) stack.removeFirst()
        redoStacks[path]?.clear()
    }

    fun undo(path: String): String? {
        val stack = undoStacks[path] ?: return null
        if (stack.size < 2) return null
        val current = stack.removeLast()
        redoStacks.getOrPut(path) { ArrayDeque() }.addLast(current)
        return stack.lastOrNull()
    }

    fun redo(path: String): String? {
        val stack = redoStacks[path] ?: return null
        val content = stack.removeLastOrNull() ?: return null
        undoStacks.getOrPut(path) { ArrayDeque() }.addLast(content)
        return content
    }

    fun canUndo(path: String) = (undoStacks[path]?.size ?: 0) >= 2
    fun canRedo(path: String) = (redoStacks[path]?.size ?: 0) >= 1

    // ── Clipboard ─────────────────────────────────────────────────────────
    data class Clipboard(val mode: String, val path: String, val isFolder: Boolean)
    var clipboard: Clipboard? = null

    // ── Persist ───────────────────────────────────────────────────────────
    fun load(ctx: Context) {
        val prefs = ctx.getSharedPreferences("xcode", Context.MODE_PRIVATE)
        val reposRaw = prefs.getString("nx_repos", null)
        repos = if (reposRaw != null) {
            try {
                val arr = JSONArray(reposRaw)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    RepoEntry(o.getString("name"), o.getString("ownerRepo"), o.getString("token"))
                }.toMutableList()
            } catch (e: Exception) { defaultRepos.toMutableList() }
        } else defaultRepos.toMutableList()

        activeRepoIdx = prefs.getInt("nx_active_repo", 0).coerceIn(0, (repos.size - 1).coerceAtLeast(0))
        currentBranch = prefs.getString("nx_branch", "main") ?: "main"
        isDark = prefs.getBoolean("nx_dark", true)
        isLocalMode = prefs.getBoolean("nx_local_mode", false)
        fontSize = prefs.getInt("nx_font_size", 13)
        tabSize = prefs.getInt("nx_tab_size", 2)
        wordWrap = prefs.getBoolean("nx_word_wrap", false)
        autoSave = prefs.getBoolean("nx_auto_save", false)
        localProject = ProjectManager.load(ctx)
        if (isLocalMode && localProject == null) isLocalMode = false
    }

    fun save(ctx: Context) {
        val arr = JSONArray()
        repos.forEach { r ->
            arr.put(JSONObject().apply {
                put("name", r.name)
                put("ownerRepo", r.ownerRepo)
                put("token", r.token)
            })
        }
        ctx.getSharedPreferences("xcode", Context.MODE_PRIVATE).edit()
            .putString("nx_repos", arr.toString())
            .putInt("nx_active_repo", activeRepoIdx)
            .putString("nx_branch", currentBranch)
            .putBoolean("nx_dark", isDark)
            .putBoolean("nx_local_mode", isLocalMode)
            .putInt("nx_font_size", fontSize)
            .putInt("nx_tab_size", tabSize)
            .putBoolean("nx_word_wrap", wordWrap)
            .putBoolean("nx_auto_save", autoSave)
            .apply()
    }

    fun stageFile(path: String) {
        val file = openFiles[path] ?: return
        stagedFiles[path] = StagedFile(
            path = path,
            content = file.content,
            sha = file.sha,
            isNew = file.isNew,
            isBinary = file.isBinary,
            rawBase64 = file.rawBase64
        )
        file.dirty = false
        file.isNew = false
    }

    fun reset() {
        openFiles.clear()
        stagedFiles.clear()
        activeFilePath = null
        treeItems = emptyList()
        undoStacks.clear()
        redoStacks.clear()
        clipboard = null
    }
}