// EditorActivity.kt
package com.xcode.app.editor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
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

    lateinit var appBar: EditorAppBar
    lateinit var drawer: EditorDrawer
    lateinit var canvas: EditorCanvas
    lateinit var tabs: EditorTabs
    lateinit var terminal: EditorTerminal
    lateinit var gitManager: GitManager

    private lateinit var insetsController: WindowInsetsControllerCompat

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
        if (EditorState.isLocalMode && EditorState.localProject != null) {
            drawer.renderLocalTree(EditorState.localProject!!)
        } else {
            loadBranches()
            loadTree()
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private fun buildLayout() {
        val root = FrameLayout(this)

        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        appBar = EditorAppBar(this)
        mainCol.addView(appBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        tabs = EditorTabs(this)
        mainCol.addView(tabs, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        canvas = EditorCanvas(this)
        mainCol.addView(canvas, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        terminal = EditorTerminal(this)
        mainCol.addView(terminal, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(mainCol)

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

        wireCallbacks()
    }

    private fun wireCallbacks() {
        appBar.onDrawerToggle = { drawer.toggle() }
        appBar.onPull = { gitPull() }
        appBar.onPush = { openPushDialog() }
        appBar.onNewFile = { showNewFileDialog() }
        appBar.onNewProject = { showNewProjectDialog() }
        appBar.onUndo = { canvas.undo() }
        appBar.onRedo = { canvas.redo() }
        appBar.onSearch = { canvas.toggleSearch() }
        appBar.onPreview = { openPreview() }
        appBar.onThemeToggle = { applyTheme(!EditorState.isDark) }

        drawer.onFileSelected = { path, sha -> openFile(path, sha) }
        drawer.onGoHome = { goHome() }
        drawer.onNewFile = { folder -> showNewFileDialog(folder) }
        drawer.onNewFolder = { folder -> showNewFolderDialog(folder) }
        drawer.onRenameFile = { path -> showRenameDialog(path, false) }
        drawer.onRenameFolder = { path -> showRenameDialog(path, true) }
        drawer.onDeleteFile = { path, sha -> confirmDeleteFile(path, sha) }
        drawer.onDeleteFolder = { path -> confirmDeleteFolder(path) }
        drawer.onDuplicateFile = { path -> duplicateFile(path) }
        drawer.onBranchChange = { branch ->
            EditorState.currentBranch = branch
            EditorState.reset()
            tabs.clearAll()
            canvas.showEmpty()
            loadTree()
        }
        drawer.onRepoChange = { idx ->
            EditorState.activeRepoIdx = idx
            EditorState.isLocalMode = false
            EditorState.reset()
            tabs.clearAll()
            canvas.showEmpty()
            gitManager.updateConfig(
                RepoConfig(
                    EditorState.activeRepo.name,
                    EditorState.activeRepo.ownerRepo,
                    EditorState.activeRepo.token
                )
            )
            appBar.setRepoName(EditorState.activeRepo.ownerRepo)
            EditorState.save(this)
            loadBranches()
            loadTree()
        }
        drawer.onSwitchToLocal = {
            EditorState.isLocalMode = true
            EditorState.reset()
            tabs.clearAll()
            canvas.showEmpty()
            EditorState.localProject?.let { drawer.renderLocalTree(it) }
            appBar.setRepoName(EditorState.localProject?.name ?: "Local")
            EditorState.save(this)
        }

        tabs.onTabSelected = { path -> activateFile(path) }
        tabs.onTabClosed = { path -> closeFile(path) }

        canvas.onContentChanged = { path, content ->
            val file = EditorState.openFiles[path] ?: return@onContentChanged
            EditorState.pushUndo(path, file.content)
            file.content = content
            file.dirty = true
            if (EditorState.isLocalMode) {
                EditorState.localProject?.files?.set(path, content)
            }
            tabs.markDirty(path, true)
            appBar.updateUndoRedo(EditorState.canUndo(path), EditorState.canRedo(path))
            if (EditorState.autoSave) autoSave(path)
        }
    }

    // ── File open / activate / close ──────────────────────────────────────

    fun openFile(path: String, sha: String) {
        if (EditorState.openFiles.containsKey(path)) {
            activateFile(path)
            drawer.close()
            return
        }
        if (EditorState.isLocalMode) {
            val content = EditorState.localProject?.files?.get(path) ?: ""
            EditorState.openFiles[path] = EditorState.OpenFile(
                path = path, content = content, sha = null, isLocal = true
            )
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
                EditorState.openFiles[path] = EditorState.OpenFile(
                    path = path,
                    content = fc.content,
                    sha = fc.sha,
                    isBinary = fc.isBinary,
                    rawBase64 = fc.rawBase64
                )
                EditorState.pushUndo(path, fc.content)
                tabs.addTab(path)
                activateFile(path)
                appBar.setStatus(EditorAppBar.Status.OK, "Pronto")
                drawer.close()
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro ao abrir")
                XCodeDialog.alert(this@EditorActivity, "Erro ao abrir ficheiro:\n${e.message}")
            }
        }
    }

    fun activateFile(path: String) {
        EditorState.activeFilePath = path
        tabs.setActive(path)
        canvas.loadFile(path)
        appBar.updateForFile(path)
        appBar.updateUndoRedo(EditorState.canUndo(path), EditorState.canRedo(path))
    }

    fun closeFile(path: String) {
        val file = EditorState.openFiles[path]
        if (file?.dirty == true) {
            XCodeDialog.confirm(
                ctx = this,
                message = "${path.substringAfterLast('/')} tem alteracoes nao guardadas. Fechar mesmo assim?",
                confirmText = "Fechar",
                destructive = true,
                onConfirm = { doCloseFile(path) }
            )
        } else doCloseFile(path)
    }

    private fun doCloseFile(path: String) {
        EditorState.openFiles.remove(path)
        tabs.removeTab(path)
        val rem = EditorState.openFiles.keys.toList()
        if (rem.isEmpty()) {
            EditorState.activeFilePath = null
            canvas.showEmpty()
            appBar.updateForFile(null)
        } else {
            activateFile(rem.last())
        }
    }

    private fun autoSave(path: String) {
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { ProjectManager.save(this, it) }
        } else {
            EditorState.stageFile(path)
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
                XCodeDialog.alert(this@EditorActivity, "Erro ao carregar arvore:\n${e.message}")
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
            XCodeDialog.alert(this, "Modo local — pull nao disponivel.")
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "Pull...")
                loadTree()
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
                appBar.setStatus(EditorAppBar.Status.OK, "Pull concluido")
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro no pull")
                XCodeDialog.alert(this@EditorActivity, "Pull falhou:\n${e.message}")
            }
        }
    }

    fun openPushDialog() {
        if (EditorState.isLocalMode) {
            XCodeDialog.alert(this, "Modo local — configura um repositorio GitHub.")
            return
        }
        val staged = EditorState.stagedFiles.size
        val dirty = EditorState.openFiles.count { it.value.dirty || it.value.isNew }
        if (staged == 0 && dirty == 0) {
            XCodeDialog.alert(this, "Nada para fazer push.")
            return
        }
        XCodeDialog.input(
            ctx = this,
            title = "Push para ${EditorState.currentBranch}",
            hint = "feat: descricao das alteracoes...",
            onConfirm = { msg ->
                if (msg.isBlank()) XCodeDialog.alert(this, "Escreve uma mensagem de commit.")
                else doPush(msg)
            }
        )
    }

    private fun doPush(message: String) {
        lifecycleScope.launch {
            EditorState.openFiles.filter { it.value.dirty || it.value.isNew }
                .keys.forEach { EditorState.stageFile(it) }
            val paths = EditorState.stagedFiles.keys.toList()
            appBar.setStatus(EditorAppBar.Status.BUSY, "Push...")
            terminal.log("Push: \"$message\" — ${paths.size} ficheiro(s)")
            var ok = 0; var fail = 0
            for (path in paths) {
                val sf = EditorState.stagedFiles[path] ?: continue
                try {
                    val newSha = gitManager.putFile(
                        path = path, content = sf.content, sha = sf.sha,
                        message = message, branch = EditorState.currentBranch,
                        isBinary = sf.isBinary, rawBase64 = sf.rawBase64
                    )
                    EditorState.openFiles[path]?.sha = newSha
                    EditorState.stagedFiles.remove(path)
                    tabs.markDirty(path, false)
                    terminal.log("  ok: $path", "ok")
                    ok++
                } catch (e: Exception) {
                    terminal.log("  erro: $path — ${e.message}", "err")
                    fail++
                }
            }
            val msg = "Push: $ok ok${if (fail > 0) ", $fail erro(s)" else ""}"
            appBar.setStatus(if (fail > 0) EditorAppBar.Status.ERROR else EditorAppBar.Status.OK, msg)
            terminal.log(msg, if (fail > 0) "err" else "ok")
        }
    }

    // ── File dialogs ──────────────────────────────────────────────────────

    fun showNewFileDialog(folder: String = "") {
        XCodeDialog.input(
            ctx = this,
            title = if (folder.isNotEmpty()) "Novo Ficheiro em $folder" else "Novo Ficheiro",
            hint = "ex: main.dart, index.html",
            onConfirm = { name ->
                if (name.isBlank()) return@input
                val path = if (folder.isNotEmpty()) "$folder/$name" else name
                val f = EditorState.OpenFile(
                    path = path, content = "", sha = null,
                    isLocal = EditorState.isLocalMode, isNew = !EditorState.isLocalMode
                )
                EditorState.openFiles[path] = f
                if (EditorState.isLocalMode) {
                    EditorState.localProject?.files?.set(path, "")
                    ProjectManager.save(this, EditorState.localProject!!)
                    drawer.renderLocalTree(EditorState.localProject!!)
                } else {
                    EditorState.stageFile(path)
                }
                tabs.addTab(path)
                activateFile(path)
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
                }
            }
        )
    }

    fun showRenameDialog(path: String, isFolder: Boolean) {
        val current = path.substringAfterLast('/')
        XCodeDialog.input(
            ctx = this,
            title = if (isFolder) "Renomear Pasta" else "Renomear Ficheiro",
            hint = "novo nome",
            initial = current,
            onConfirm = { newName ->
                if (newName.isBlank() || newName == current) return@input
                if (isFolder) renameFolder(path, newName) else renameFile(path, newName)
            }
        )
    }

    private fun renameFile(oldPath: String, newName: String) {
        val dir = oldPath.substringBeforeLast('/', "")
        val newPath = if (dir.isEmpty()) newName else "$dir/$newName"
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { p ->
                p.files[newPath] = p.files.remove(oldPath) ?: ""
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
                gitManager.putFile(newPath, fc.content, null, "rename: $oldPath -> $newPath", EditorState.currentBranch)
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
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro")
                XCodeDialog.alert(this@EditorActivity, "Erro ao renomear:\n${e.message}")
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
                    p.files["$newFolder/$rel"] = p.files.remove(k) ?: ""
                }
                ProjectManager.save(this, p)
                drawer.renderLocalTree(p)
            }
            return
        }
        lifecycleScope.launch {
            appBar.setStatus(EditorAppBar.Status.BUSY, "A renomear pasta...")
            EditorState.treeItems.filter {
                it.type == "blob" && it.path.startsWith("$folderPath/")
            }.forEach { f ->
                try {
                    val rel = f.path.removePrefix("$folderPath/")
                    val newPath = "$newFolder/$rel"
                    val fc = gitManager.getFileContent(f.path, EditorState.currentBranch)
                    gitManager.putFile(newPath, fc.content, null, "rename: ${f.path} -> $newPath", EditorState.currentBranch)
                    gitManager.deleteFile(f.path, fc.sha, "rename: remove ${f.path}", EditorState.currentBranch)
                } catch (e: Exception) { /* continue */ }
            }
            appBar.setStatus(EditorAppBar.Status.OK, "Pasta renomeada")
            loadTree()
        }
    }

    fun confirmDeleteFile(path: String, sha: String) {
        XCodeDialog.confirm(
            ctx = this,
            message = "Eliminar \"${path.substringAfterLast('/')}\"?\nEsta accao nao pode ser desfeita.",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = { deleteFile(path, sha) }
        )
    }

    private fun deleteFile(path: String, sha: String) {
        if (EditorState.isLocalMode) {
            EditorState.localProject?.files?.remove(path)
            ProjectManager.save(this, EditorState.localProject!!)
            EditorState.openFiles.remove(path)
            tabs.removeTab(path)
            drawer.renderLocalTree(EditorState.localProject!!)
            val rem = EditorState.openFiles.keys.toList()
            if (rem.isEmpty()) { EditorState.activeFilePath = null; canvas.showEmpty() }
            else if (EditorState.activeFilePath == path) activateFile(rem.last())
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "A eliminar...")
                val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                gitManager.deleteFile(path, fc.sha, "delete: $path", EditorState.currentBranch)
                EditorState.openFiles.remove(path)
                tabs.removeTab(path)
                val rem = EditorState.openFiles.keys.toList()
                if (rem.isEmpty()) { EditorState.activeFilePath = null; canvas.showEmpty() }
                else if (EditorState.activeFilePath == path) activateFile(rem.last())
                appBar.setStatus(EditorAppBar.Status.OK, "Eliminado")
                loadTree()
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro")
                XCodeDialog.alert(this@EditorActivity, "Erro ao eliminar:\n${e.message}")
            }
        }
    }

    fun confirmDeleteFolder(path: String) {
        val count = if (EditorState.isLocalMode)
            EditorState.localProject?.files?.keys?.count { it.startsWith("$path/") } ?: 0
        else
            EditorState.treeItems.count { it.type == "blob" && it.path.startsWith("$path/") }
        XCodeDialog.confirm(
            ctx = this,
            message = "Eliminar pasta \"${path.substringAfterLast('/')}\" com $count ficheiro(s)?",
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = { deleteFolder(path) }
        )
    }

    private fun deleteFolder(folderPath: String) {
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { p ->
                val toDelete = p.files.keys.filter { it.startsWith("$folderPath/") }
                toDelete.forEach { k ->
                    p.files.remove(k)
                    EditorState.openFiles.remove(k)
                    tabs.removeTab(k)
                }
                ProjectManager.save(this, p)
                drawer.renderLocalTree(p)
                val rem = EditorState.openFiles.keys.toList()
                if (rem.isEmpty()) { EditorState.activeFilePath = null; canvas.showEmpty() }
                else if (EditorState.activeFilePath !in EditorState.openFiles) activateFile(rem.last())
            }
            return
        }
        lifecycleScope.launch {
            appBar.setStatus(EditorAppBar.Status.BUSY, "A eliminar pasta...")
            EditorState.treeItems.filter {
                it.type == "blob" && it.path.startsWith("$folderPath/")
            }.forEach { f ->
                try {
                    val fc = gitManager.getFileContent(f.path, EditorState.currentBranch)
                    gitManager.deleteFile(f.path, fc.sha, "delete: ${f.path}", EditorState.currentBranch)
                    EditorState.openFiles.remove(f.path)
                    tabs.removeTab(f.path)
                } catch (e: Exception) { /* continue */ }
            }
            val rem = EditorState.openFiles.keys.toList()
            if (rem.isEmpty()) { EditorState.activeFilePath = null; canvas.showEmpty() }
            else if (EditorState.activeFilePath !in EditorState.openFiles) activateFile(rem.last())
            appBar.setStatus(EditorAppBar.Status.OK, "Pasta eliminada")
            loadTree()
        }
    }

    fun duplicateFile(path: String) {
        val ext = path.substringAfterLast('.', "")
        val base = if (ext.isNotEmpty()) path.substringBeforeLast('.') else path
        val newPath = if (ext.isNotEmpty()) "${base}_copy.$ext" else "${base}_copy"
        if (EditorState.isLocalMode) {
            EditorState.localProject?.let { p ->
                p.files[newPath] = p.files[path] ?: ""
                ProjectManager.save(this, p)
                drawer.renderLocalTree(p)
                terminal.log("Duplicado: $newPath", "ok")
            }
            return
        }
        lifecycleScope.launch {
            try {
                appBar.setStatus(EditorAppBar.Status.BUSY, "A duplicar...")
                val fc = gitManager.getFileContent(path, EditorState.currentBranch)
                gitManager.putFile(newPath, fc.content, null, "duplicate: $path", EditorState.currentBranch)
                appBar.setStatus(EditorAppBar.Status.OK, "Duplicado")
                terminal.log("Duplicado: $newPath", "ok")
                loadTree()
            } catch (e: Exception) {
                appBar.setStatus(EditorAppBar.Status.ERROR, "Erro")
                XCodeDialog.alert(this@EditorActivity, "Erro ao duplicar:\n${e.message}")
            }
        }
    }

    // ── New project ───────────────────────────────────────────────────────

    fun showNewProjectDialog() {
        XCodeDialog.input(
            ctx = this,
            title = "Novo Projeto Local",
            hint = "Nome do projeto",
            onConfirm = { name ->
                if (name.isBlank()) return@input
                val typeLabels = ProjectManager.templates.values.map { it.first }
                val typeKeys = ProjectManager.templates.keys.toList()
                XCodeDialog.choice(
                    ctx = this,
                    title = "Tipo de Projeto",
                    options = typeLabels,
                    onChoice = { idx, _ ->
                        val type = typeKeys[idx]
                        val project = ProjectManager.createFromTemplate(name, type)
                        EditorState.localProject = project
                        EditorState.isLocalMode = true
                        ProjectManager.save(this, project)
                        EditorState.save(this)
                        EditorState.reset()
                        tabs.clearAll()
                        drawer.renderLocalTree(project)
                        canvas.showEmpty()
                        appBar.setRepoName(name)
                        terminal.log("Projeto \"$name\" criado (${project.files.size} ficheiros)", "ok")
                        project.files.keys.firstOrNull()?.let { openFile(it, "") }
                    }
                )
            }
        )
    }

    // ── Preview ───────────────────────────────────────────────────────────

    fun openPreview() {
        val path = EditorState.activeFilePath ?: return
        val file = EditorState.openFiles[path] ?: return
        val ext = path.substringAfterLast('.', "").lowercase()
        val previewable = setOf("html", "htm", "svg", "md", "css")
        if (ext !in previewable) {
            XCodeDialog.alert(this, "Preview nao disponivel para .$ext")
            return
        }
        val html = buildPreviewHtml(ext, file.content)
        val intent = Intent(this, PreviewActivity::class.java).apply {
            putExtra("title", path.substringAfterLast('/'))
            putExtra("html", html)
            putExtra("isDark", EditorState.isDark)
        }
        startActivity(intent)
    }

    private fun buildPreviewHtml(ext: String, content: String): String {
        val dark = EditorState.isDark
        val bg = if (dark) "#1e1e1e" else "#ffffff"
        val fg = if (dark) "#cccccc" else "#333333"
        return when (ext) {
            "html", "htm" -> content
            "svg" -> """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:$bg;}</style></head><body>$content</body></html>"""
            "md" -> {
                val body = mdToHtml(content)
                """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{font-family:sans-serif;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.7;color:$fg;background:$bg;}h1,h2,h3,h4{margin-top:1.5em;margin-bottom:.5em;}h1,h2{border-bottom:1px solid ${if (dark) "#3e3e42" else "#e0e0e0"};padding-bottom:8px;}code{background:${if (dark) "#2d2d30" else "#f4f4f4"};padding:2px 6px;border-radius:3px;font-size:.9em;font-family:monospace;}pre{background:${if (dark) "#2d2d30" else "#f4f4f4"};padding:16px;border-radius:5px;overflow-x:auto;}pre code{background:none;padding:0;}blockquote{border-left:4px solid #0e7af0;margin:0;padding-left:16px;color:${if (dark) "#858585" else "#666"};}a{color:#0e7af0;}ul,ol{padding-left:1.5em;}</style></head><body>$body</body></html>"""
            }
            "css" -> """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>$content</style></head><body style="padding:32px;font-family:sans-serif;background:$bg;color:$fg"><h1>CSS Preview</h1><p>Paragrafo de exemplo.</p><a href="#">Link de exemplo</a><br><br><button>Botao</button></body></html>"""
            else -> "<body style='font-family:sans-serif;padding:32px;background:$bg;color:$fg'><p>Sem preview disponivel.</p></body>"
        }
    }

    private fun mdToHtml(md: String): String = md
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("^#{6}\\s(.+)", RegexOption.MULTILINE), "<h6>$1</h6>")
        .replace(Regex("^#{5}\\s(.+)", RegexOption.MULTILINE), "<h5>$1</h5>")
        .replace(Regex("^#{4}\\s(.+)", RegexOption.MULTILINE), "<h4>$1</h4>")
        .replace(Regex("^#{3}\\s(.+)", RegexOption.MULTILINE), "<h3>$1</h3>")
        .replace(Regex("^#{2}\\s(.+)", RegexOption.MULTILINE), "<h2>$1</h2>")
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
        EditorState.save(this)
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
        drawer.close()
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onBackPressed() {
        if (drawer.isOpen()) drawer.close()
    }

    override fun onPause() {
        super.onPause()
        EditorState.save(this)
        EditorState.localProject?.let { ProjectManager.save(this, it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}