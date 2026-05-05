package com.xcode.app.editor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.xcode.app.home.HomeActivity
import com.xcode.app.preview.PreviewActivity
import com.xcode.app.services.XCodeKeepAliveService
import com.xcode.app.ui.XCodeDialog
import kotlinx.coroutines.launch

class EditorActivity : AppCompatActivity() {

    lateinit var state: EditorState
    private lateinit var insetsController: WindowInsetsControllerCompat
    private lateinit var appBar: EditorAppBar
    private lateinit var drawer: EditorDrawer
    private lateinit var canvas: EditorCanvas
    private lateinit var tabs: EditorTabs
    private lateinit var terminal: EditorTerminal
    lateinit var gitManager: GitManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        XCodeKeepAliveService.start(this)
        EditorState.load(this)

        gitManager = GitManager(
            RepoConfig(
                name = EditorState.activeRepo.name,
                ownerRepo = EditorState.activeRepo.ownerRepo,
                token = EditorState.activeRepo.token
            )
        )

        buildLayout()
        applyTheme(EditorState.isDark)

        // Load tree
        if (!EditorState.isLocalMode) {
            loadBranches()
            loadTree()
        } else {
            drawer.renderLocalTree(EditorState.localProject!!)
        }
    }

    private fun buildLayout() {
        // Root: DrawerLayout wrapping everything
        val root = FrameLayout(this)

        // Main content column
        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // AppBar
        appBar = EditorAppBar(this)
        mainCol.addView(appBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Tabs
        tabs = EditorTabs(this)
        mainCol.addView(tabs, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Canvas (code editor)
        canvas = EditorCanvas(this)
        mainCol.addView(canvas, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        // Terminal panel
        terminal = EditorTerminal(this)
        mainCol.addView(terminal, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(mainCol)

        // Drawer (on top)
        drawer = EditorDrawer(this)
        root.addView(drawer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Wire callbacks
        appBar.onDrawerToggle = { drawer.toggle() }
        appBar.onPull = { gitPull() }
        appBar.onPush = { openPushDialog() }
        appBar.onNewFile = { showNewFileDialog() }
        appBar.onNewProject = { showNewProjectDialog() }
        appBar.onUndo = { canvas.undo() }
        appBar.onRedo = { canvas.redo() }
        appBar.onSearch = { canvas.toggleSearch() }
        appBar.onPreview = { openPreview() }

        drawer.onFileSelected = { path, sha -> openFile(path, sha) }
        drawer.onGoHome = { goHome() }
        drawer.onNewFile = { folder -> showNewFileDialog(folder) }
        drawer.onNewFolder = { folder -> showNewFolderDialog(folder) }
        drawer.onRenameFile = { path -> showRenameDialog(path, false) }
        drawer.onRenameFolder = { path -> showRenameDialog(path, true) }
        drawer.onDeleteFile = { path, sha -> deleteFile(path, sha) }
        drawer.onDeleteFolder = { path -> deleteFolder(path) }
        drawer.onBranchChange = { branch ->
            EditorState.currentBranch = branch
            EditorState.reset()
            tabs.clearAll()
            canvas.showEmpty()
            loadTree()
        }

        tabs.onTabSelected = { path -> activateFile(path) }
        tabs.onTabClosed = { path -> closeFile(path) }

        canvas.onContentChanged = { path, content ->
            EditorState.openFiles[path]?.let { f ->
                EditorState.pushUndo(path, f.content)
                f.content = content
                f.dirty = true
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.files?.set(path, content)
                }
            }
            tabs.markDirty(path, true)
            appBar.updateDirtyIndicator(true)
        }
    }

    // ── File operations ───────────────────────────────────────────────────

    fun openFile(path: String, sha: String) {
        if (EditorState.openFiles.containsKey(path)) {
            activateFile(path)
            drawer.close()
            return
        }
        if (EditorState.isLocalMode) {
            val content = EditorState.localProject?.files?.get(path) ?: ""
            val f = EditorState.OpenFile(
                path = path, content = content, sha = null,
                isLocal = true
            )
            EditorState.openFiles[path] = f
            EditorState.pushUndo(path, content)
            tabs.addTab(path)
            activateFile(path)
            drawer.close()
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "A abrir...")
                val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                val f = EditorState.OpenFile(
                    path = path,
                    content = fc.content,
                    sha = fc.sha,
                    isBinary = fc.isBinary,
                    rawBase64 = fc.rawBase64
                )
                EditorState.openFiles[path] = f
                EditorState.pushUndo(path, fc.content)
                tabs.addTab(path)
                activateFile(path)
                appBar.setStatus(EditorAppBar.Status.OK)
                drawer.close()
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro ao abrir")
                showError("Erro ao abrir ficheiro: ${e.message}")
            }
        }
    }

    fun activateFile(path: String) {
        EditorState.activeFilePath = path
        tabs.setActive(path)
        canvas.loadFile(path)
        appBar.updateForFile(path)
    }

    fun closeFile(path: String) {
        val file = EditorState.openFiles[path]
        if (file?.dirty == true) {
            XCodeDialog.confirm(
                ctx = this,
                message = "${path.substringAfterLast('/')} tem alterações não guardadas. Fechar mesmo assim?",
                confirmText = "Fechar",
                destructive = true,
                onConfirm = {
                    doCloseFile(path)
                }
            )
        } else {
            doCloseFile(path)
        }
    }

    private fun doCloseFile(path: String) {
        EditorState.openFiles.remove(path)
        EditorState.undoStacks.remove(path)
        EditorState.redoStacks.remove(path)
        tabs.removeTab(path)
        val remaining = EditorState.openFiles.keys.toList()
        if (remaining.isEmpty()) {
            EditorState.activeFilePath = null
            canvas.showEmpty()
            appBar.updateForFile(null)
        } else {
            activateFile(remaining.last())
        }
    }

    // ── Git operations ────────────────────────────────────────────────────

    fun loadTree() {
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "A carregar...")
                val items = gitManager.getTree(EditorState.currentBranch)
                EditorState.treeItems = items
                drawer.renderTree(items)
                appBar.setStatus(EditorAppBar.Status.OK, "Pronto")
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro")
                showError("Erro ao carregar árvore: ${e.message}")
            }
        }
    }

    fun loadBranches() {
        lifecycleScope.launch {
            try {
                val branches = gitManager.getBranches()
                drawer.setBranches(branches, EditorState.currentBranch)
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun gitPull() {
        if (EditorState.isLocalMode) {
            showError("Modo local — sem pull disponível.")
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "Pull...")
                loadTree()
                // Refresh open files
                EditorState.openFiles.keys.toList().forEach { path ->
                    val f = EditorState.openFiles[path] ?: return@forEach
                    if (f.isBinary) return@forEach
                    val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                    f.content = fc.content
                    f.sha = fc.sha
                    f.dirty = false
                    tabs.markDirty(path, false)
                    if (EditorState.activeFilePath == path) canvas.loadFile(path)
                }
                EditorState.stagedFiles.clear()
                appBar.setStatus(EditorAppBar.Status.OK, "Pull concluído")
                appBar.updateDirtyIndicator(false)
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro no pull")
                showError("Pull falhou: ${e.message}")
            }
        }
    }

    fun openPushDialog() {
        if (EditorState.isLocalMode) {
            showError("Modo local — configura um repositório GitHub primeiro.")
            return
        }
        val staged = EditorState.stagedFiles.keys.toList()
        val dirty = EditorState.openFiles.filter { it.value.dirty || it.value.isNew }.keys.toList()
        if (staged.isEmpty() && dirty.isEmpty()) {
            showError("Nada para fazer push.")
            return
        }
        XCodeDialog.input(
            ctx = this,
            title = "Push → ${EditorState.currentBranch}",
            hint = "feat: descrição das alterações...",
            onConfirm = { msg ->
                if (msg.isBlank()) {
                    showError("Escreve uma mensagem de commit.")
                } else {
                    doPush(msg)
                }
            }
        )
    }

    private fun doPush(message: String) {
        lifecycleScope.launch {
            // Stage dirty files first
            EditorState.openFiles.filter { it.value.dirty || it.value.isNew }
                .keys.forEach { EditorState.stageFile(it) }

            val paths = EditorState.stagedFiles.keys.toList()
            appBar.setStatus(EditorAppBar.Status.BUSY, "Push...")
            var ok = 0; var fail = 0
            for (path in paths) {
                val sf = EditorState.stagedFiles[path] ?: continue
                try {
                    val newSha = gitManager.putFile(
                        path = path,
                        content = sf.content,
                        sha = sf.sha,
                        message = message,
                        branch = EditorState.currentBranch,
                        isBinary = sf.isBinary,
                        rawBase64 = sf.rawBase64
                    )
                    EditorState.openFiles[path]?.sha = newSha
                    EditorState.stagedFiles.remove(path)
                    tabs.markDirty(path, false)
                    ok++
                } catch (e: Exception) {
                    fail++
                }
            }
            appBar.setStatus(
                if (fail > 0) EditorAppBar.Status.ERROR else EditorAppBar.Status.OK,
                "Push: $ok ok${if (fail > 0) ", $fail erro(s)" else ""}"
            )
            appBar.updateDirtyIndicator(EditorState.openFiles.any { it.value.dirty })
        }
    }

    // ── File dialogs ──────────────────────────────────────────────────────

    fun showNewFileDialog(folder: String = "") {
        XCodeDialog.input(
            ctx = this,
            title = "Novo Ficheiro",
            hint = "ex: main.dart, index.html",
            onConfirm = { name ->
                if (name.isBlank()) return@input
                val path = if (folder.isNotEmpty()) "$folder/$name" else name
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.files?.set(path, "")
                    ProjectManager.save(this, EditorState.localProject!!)
                    val f = EditorState.OpenFile(
                        path = path, content = "", sha = null,
                        isLocal = true, isNew = true
                    )
                    EditorState.openFiles[path] = f
                    drawer.renderLocalTree(EditorState.localProject!!)
                    tabs.addTab(path)
                    activateFile(path)
                } else {
                    val f = EditorState.OpenFile(
                        path = path, content = "", sha = null, isNew = true
                    )
                    EditorState.openFiles[path] = f
                    EditorState.stagedFiles[path] = EditorState.StagedFile(
                        path = path, content = "", sha = null, isNew = true,
                        isBinary = false, rawBase64 = ""
                    )
                    tabs.addTab(path)
                    activateFile(path)
                    appBar.updateDirtyIndicator(true)
                }
            }
        )
    }

    fun showNewFolderDialog(parentFolder: String = "") {
        XCodeDialog.input(
            ctx = this,
            title = "Nova Pasta",
            hint = "ex: components",
            onConfirm = { name ->
                if (name.isBlank()) return@input
                val keepPath = if (parentFolder.isNotEmpty()) "$parentFolder/$name/.gitkeep"
                else "$name/.gitkeep"
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.files?.set(keepPath, "")
                    ProjectManager.save(this, EditorState.localProject!!)
                    drawer.renderLocalTree(EditorState.localProject!!)
                } else {
                    EditorState.stagedFiles[keepPath] = EditorState.StagedFile(
                        path = keepPath, content = "", sha = null,
                        isNew = true, isBinary = false, rawBase64 = ""
                    )
                    lifecycleScope.launch { loadTree() }
                }
            }
        )
    }

    fun showRenameDialog(path: String, isFolder: Boolean) {
        val current = path.substringAfterLast('/')
        XCodeDialog.input(
            ctx = this,
            title = if (isFolder) "Renomear pasta" else "Renomear ficheiro",
            hint = "novo nome",
            initial = current,
            onConfirm = { newName ->
                if (newName.isBlank() || newName == current) return@input
                if (isFolder) renameFolder(path, newName)
                else renameFile(path, newName)
            }
        )
    }

    private fun renameFile(oldPath: String, newName: String) {
        val dir = oldPath.substringBeforeLast('/', "")
        val newPath = if (dir.isEmpty()) newName else "$dir/$newName"
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { p ->
                p.files[newPath] = p.files[oldPath] ?: ""
                p.files.remove(oldPath)
                ProjectManager.save(this, p)
                EditorState.openFiles[oldPath]?.let { f ->
                    EditorState.openFiles[newPath] = f.copy(path = newPath)
                    EditorState.openFiles.remove(oldPath)
                    if (EditorState.activeFilePath == oldPath) EditorState.activeFilePath = newPath
                }
                tabs.renameTab(oldPath, newPath)
                drawer.renderLocalTree(p)
            }
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "A renomear...")
                val fc = gitManager.getFileContent(oldPath, EditorState.currentBranch)
                gitManager.putFile(newPath, fc.content, null, "rename: $oldPath → $newPath", EditorState.currentBranch)
                gitManager.deleteFile(oldPath, fc.sha, "rename: remove $oldPath", EditorState.currentBranch)
                EditorState.openFiles[oldPath]?.let { f ->
                    EditorState.openFiles[newPath] = f.copy(path = newPath)
                    EditorState.openFiles.remove(oldPath)
                    if (EditorState.activeFilePath == oldPath) EditorState.activeFilePath = newPath
                }
                tabs.renameTab(oldPath, newPath)
                appBar.setStatus(EditorAppBar.Status.OK, "Renomeado")
                loadTree()
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR)
                showError("Erro ao renomear: ${e.message}")
            }
        }
    }

    private fun renameFolder(folderPath: String, newName: String) {
        val parentDir = folderPath.substringBeforeLast('/', "")
        val newFolder = if (parentDir.isEmpty()) newName else "$parentDir/$newName"
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { p ->
                val toRename = p.files.keys.filter { it.startsWith("$folderPath/") }
                toRename.forEach { k ->
                    val rel = k.removePrefix("$folderPath/")
                    p.files["$newFolder/$rel"] = p.files[k] ?: ""
                    p.files.remove(k)
                }
                ProjectManager.save(this, p)
                drawer.renderLocalTree(p)
            }
            return
        }
        lifecycleScope.launch {
            appBar.setStatus(EditorAppBar.Status.BUSY, "A renomear pasta...")
            val files = EditorState.treeItems.filter {
                it.type == "blob" && it.path.startsWith("$folderPath/")
            }
            files.forEach { f ->
                val rel = f.path.removePrefix("$folderPath/")
                val newPath = "$newFolder/$rel"
                try {
                    val fc = gitManager.getFileContent(f.path, EditorState.currentBranch)
                    gitManager.putFile(newPath, fc.content, null, "rename: ${f.path} → $newPath", EditorState.currentBranch)
                    gitManager.deleteFile(f.path, fc.sha, "rename: remove ${f.path}", EditorState.currentBranch)
                } catch (e: Exception) { /* continue */ }
            }
            appBar.setStatus(EditorAppBar.Status.OK, "Pasta renomeada")
            loadTree()
        }
    }

    private fun deleteFile(path: String, sha: String) {
        XCodeDialog.confirm(
            ctx = this,
            message = "Eliminar \"${path.substringAfterLast('/')}\"? Esta acção não pode ser desfeita.",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = {
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.files?.remove(path)
                    ProjectManager.save(this, EditorState.localProject!!)
                    EditorState.openFiles.remove(path)
                    tabs.removeTab(path)
                    drawer.renderLocalTree(EditorState.localProject!!)
                    if (EditorState.activeFilePath == path) {
                        val rem = EditorState.openFiles.keys.toList()
                        if (rem.isEmpty()) canvas.showEmpty() else activateFile(rem.last())
                    }
                    return@confirm
                }
                lifecycleScope.launch {
                    try {
                        appBar.setStatus(EditorAppBar.Status.BUSY, "A eliminar...")
                        val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                        gitManager.deleteFile(path, fc.sha, "delete: $path", EditorState.currentBranch)
                        EditorState.openFiles.remove(path)
                        tabs.removeTab(path)
                        if (EditorState.activeFilePath == path) {
                            val rem = EditorState.openFiles.keys.toList()
                            if (rem.isEmpty()) canvas.showEmpty() else activateFile(rem.last())
                        }
                        appBar.setStatus(EditorAppBar.Status.OK, "Eliminado")
                        loadTree()
                    } catch (e: Exception) {
                        appBar.setStatus(EditorAppBar.Status.ERROR)
                        showError("Erro ao eliminar: ${e.message}")
                    }
                }
            }
        )
    }

    private fun deleteFolder(folderPath: String) {
        val files = if (EditorState.isLocalMode) {
            EditorState.localProject?.files?.keys?.filter { it.startsWith("$folderPath/") } ?: emptyList()
        } else {
            EditorState.treeItems.filter { it.type == "blob" && it.path.startsWith("$folderPath/") }.map { it.path }
        }
        XCodeDialog.confirm(
            ctx = this,
            message = "Eliminar pasta \"${folderPath.substringAfterLast('/')}\" com ${files.size} ficheiro(s)?",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = {
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.let { p ->
                        files.forEach { p.files.remove(it); EditorState.openFiles.remove(it); tabs.removeTab(it) }
                        ProjectManager.save(this, p)
                        drawer.renderLocalTree(p)
                        val rem = EditorState.openFiles.keys.toList()
                        if (rem.isEmpty()) canvas.showEmpty() else if (EditorState.activeFilePath !in EditorState.openFiles) activateFile(rem.last())
                    }
                    return@confirm
                }
                lifecycleScope.launch {
                    appBar.setStatus(EditorAppBar.Status.BUSY, "A eliminar pasta...")
                    files.forEach { path ->
                        try {
                            val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                            gitManager.deleteFile(path, fc.sha, "delete: $path", EditorState.currentBranch)
                            EditorState.openFiles.remove(path)
                            tabs.removeTab(path)
                        } catch (e: Exception) { /* continue */ }
                    }
                    val rem = EditorState.openFiles.keys.toList()
                    if (rem.isEmpty()) canvas.showEmpty() else if (EditorState.activeFilePath !in EditorState.openFiles) activateFile(rem.last())
                    appBar.setStatus(EditorAppBar.Status.OK, "Pasta eliminada")
                    loadTree()
                }
            }
        )
    }

    // ── New project ───────────────────────────────────────────────────────

    fun showNewProjectDialog() {
        XCodeDialog.input(
            ctx = this,
            title = "Novo Projeto Local",
            hint = "Nome do projeto",
            onConfirm = { name ->
                if (name.isBlank()) return@input
                showProjectTypeDialog(name)
            }
        )
    }

    private fun showProjectTypeDialog(name: String) {
        val types = ProjectManager.templates.entries.toList()
        val labels = types.map { it.value.first }.toTypedArray()
        // Simple choice dialog
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Tipo de Projeto")
            .setItems(labels) { _, i ->
                val type = types[i].key
                val project = ProjectManager.createFromTemplate(name, type)
                EditorState.localProject = project
                EditorState.isLocalMode = true
                ProjectManager.save(this, project)
                EditorState.saveRepos(this)
                EditorState.reset()
                tabs.clearAll()
                drawer.renderLocalTree(project)
                canvas.showEmpty()
                appBar.setRepoName(name)
                // Open first file
                project.files.keys.firstOrNull()?.let { openFile(it, "") }
            }
            .create()
        dialog.show()
    }

    // ── Preview ───────────────────────────────────────────────────────────

    fun openPreview() {
        val path = EditorState.activeFilePath ?: return
        val file = EditorState.openFiles[path] ?: return
        val ext = path.substringAfterLast('.', "").lowercase()
        val previewable = setOf("html", "htm", "svg", "md", "css")
        if (ext !in previewable) {
            showError("Preview não disponível para .$ext")
            return
        }
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("title", path.substringAfterLast('/'))
            putExtra("html", buildPreviewHtml(ext, file.content))
            putExtra("isDark", EditorState.isDark)
        }
        startActivity(intent)
    }

    private fun buildPreviewHtml(ext: String, content: String): String {
        return when (ext) {
            "html", "htm" -> content
            "svg" -> """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:${if (EditorState.isDark) "#1e1e1e" else "#fff"};}</style></head><body>$content</body></html>"""
            "md" -> {
                val body = mdToHtml(content)
                """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{font-family:sans-serif;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.6;color:${if (EditorState.isDark) "#cccccc" else "#333"};background:${if (EditorState.isDark) "#1e1e1e" else "#fff"};}h1,h2,h3{border-bottom:1px solid #3e3e42;padding-bottom:8px;}code{background:${if (EditorState.isDark) "#2d2d30" else "#f4f4f4"};padding:2px 6px;border-radius:3px;font-size:.9em;font-family:monospace;}pre code{display:block;padding:12px;overflow-x:auto;}blockquote{border-left:4px solid #0e7af0;margin:0;padding-left:16px;color:#858585;}a{color:#0e7af0;}</style></head><body>$body</body></html>"""
            }
            "css" -> """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>$content</style></head><body style="padding:24px;font-family:sans-serif;background:${if (EditorState.isDark) "#1e1e1e" else "#fff"};color:${if (EditorState.isDark) "#ccc" else "#333"}"><h1>CSS Preview</h1><p>Parágrafo de exemplo.</p><a href="#">Link</a> &nbsp; <button>Botão</button></body></html>"""
            else -> "<h2>Sem preview disponível</h2>"
        }
    }

    private fun mdToHtml(md: String): String = md
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("^######\\s(.+)", RegexOption.MULTILINE), "<h6>$1</h6>")
        .replace(Regex("^#####\\s(.+)", RegexOption.MULTILINE), "<h5>$1</h5>")
        .replace(Regex("^####\\s(.+)", RegexOption.MULTILINE), "<h4>$1</h4>")
        .replace(Regex("^###\\s(.+)", RegexOption.MULTILINE), "<h3>$1</h3>")
        .replace(Regex("^##\\s(.+)", RegexOption.MULTILINE), "<h2>$1</h2>")
        .replace(Regex("^#\\s(.+)", RegexOption.MULTILINE), "<h1>$1</h1>")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
        .replace(Regex("^>\\s(.+)", RegexOption.MULTILINE), "<blockquote>$1</blockquote>")
        .replace(Regex("^[-*+]\\s(.+)", RegexOption.MULTILINE), "<li>$1</li>")
        .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
        .replace("\n\n", "</p><p>")

    // ── Theme ─────────────────────────────────────────────────────────────

    fun applyTheme(isDark: Boolean) {
        EditorState.isDark = isDark
        val bg = if (isDark) Color.parseColor("#1e1e1e") else Color.WHITE
        window.decorView.setBackgroundColor(bg)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        appBar.applyTheme(isDark)
        canvas.applyTheme(isDark)
        drawer.applyTheme(isDark)
        terminal.applyTheme(isDark)
        tabs.applyTheme(isDark)
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun goHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onBackPressed() {
        if (drawer.isOpen()) {
            drawer.close()
        }
        // No back to home — intentional
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    fun showError(msg: String) {
        XCodeDialog.alert(this, msg)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}