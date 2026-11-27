package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.fs.ProjectRepository
import com.example.data.model.EditorTab
import com.example.data.model.FileNode
import com.example.data.model.InstalledPackage
import com.example.data.model.NpmPackageSearchResult
import com.example.data.model.ProjectInfo
import com.example.data.model.StudioTab
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.data.npm.NpmRegistryClient
import com.example.data.prefs.StudioPreferences
import com.example.data.prefs.StudioSettings
import com.example.runtime.NodeEngineHolder
import com.example.runtime.NodeRunnerService
import com.example.runtime.TerminalShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val preferences = StudioPreferences(application)
    private val npmClient = NpmRegistryClient(repository.npmCacheDir)
    private val nodeEngine = NodeEngineHolder.engine
    private val terminalShell = TerminalShell(nodeEngine, npmClient)

    val settings: StateFlow<StudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioSettings())

    private val _project = MutableStateFlow<ProjectInfo?>(null)
    val project: StateFlow<ProjectInfo?> = _project.asStateFlow()

    private val _selectedStudioTab = MutableStateFlow(StudioTab.EDITOR)
    val selectedStudioTab: StateFlow<StudioTab> = _selectedStudioTab.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private val _expandedPaths = MutableStateFlow<Set<String>>(setOf("utils", "src"))
    val expandedPaths: StateFlow<Set<String>> = _expandedPaths.asStateFlow()

    private val _openTabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val openTabs: StateFlow<List<EditorTab>> = _openTabs.asStateFlow()

    private val _activeTabPath = MutableStateFlow<String?>(null)
    val activeTabPath: StateFlow<String?> = _activeTabPath.asStateFlow()

    private val _installedPackages = MutableStateFlow<List<InstalledPackage>>(emptyList())
    val installedPackages: StateFlow<List<InstalledPackage>> = _installedPackages.asStateFlow()

    private val _npmSearchQuery = MutableStateFlow("")
    val npmSearchQuery: StateFlow<String> = _npmSearchQuery.asStateFlow()

    private val _npmSearchResults = MutableStateFlow<List<NpmPackageSearchResult>>(emptyList())
    val npmSearchResults: StateFlow<List<NpmPackageSearchResult>> = _npmSearchResults.asStateFlow()

    private val _isSearchingNpm = MutableStateFlow(false)
    val isSearchingNpm: StateFlow<Boolean> = _isSearchingNpm.asStateFlow()

    private val _isInstallingNpm = MutableStateFlow(false)
    val isInstallingNpm: StateFlow<Boolean> = _isInstallingNpm.asStateFlow()

    private val _npmInstallLog = MutableStateFlow<List<String>>(emptyList())
    val npmInstallLog: StateFlow<List<String>> = _npmInstallLog.asStateFlow()

    private val _npmError = MutableStateFlow<String?>(null)
    val npmError: StateFlow<String?> = _npmError.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _currentSubDirLabel = MutableStateFlow("")
    val currentSubDirLabel: StateFlow<String> = _currentSubDirLabel.asStateFlow()

    private val _fileActionMessage = MutableStateFlow<String?>(null)
    val fileActionMessage: StateFlow<String?> = _fileActionMessage.asStateFlow()

    val isRunning: StateFlow<Boolean> = nodeEngine.isRunning
    val activeServerPort: StateFlow<Int?> = nodeEngine.activeServerPort
    val lanAddresses: StateFlow<List<String>> = nodeEngine.lanAddresses

    private var currentSubDir: File? = null
    private var lastLaunchedScript: String = "index.js"

    init {
        viewModelScope.launch {
            combine(isRunning, activeServerPort, settings) { running, port, prefs ->
                Triple(running, port, prefs)
            }.collect { (running, port, prefs) ->
                val proj = _project.value
                if (running && proj != null && (prefs.runningNotificationEnabled || prefs.wakeLockEnabled)) {
                    NodeRunnerService.startOrUpdate(
                        context = getApplication(),
                        projectName = proj.name,
                        scriptName = lastLaunchedScript,
                        serverPort = port,
                        lanHosting = prefs.localHostingEnabled,
                        wakeLock = prefs.wakeLockEnabled
                    )
                } else {
                    NodeRunnerService.stop(getApplication())
                }
            }
        }
    }

    fun loadProject(projectId: String) {
        if (_project.value?.id == projectId) return
        nodeEngine.stop()
        viewModelScope.launch {
            val info = repository.getProject(projectId) ?: return@launch
            _project.value = info
            val dir = File(info.directoryPath)
            currentSubDir = dir
            _currentSubDirLabel.value = info.name
            _openTabs.value = emptyList()
            _activeTabPath.value = null
            val currentSettings = settings.value
            _terminalLines.value = listOf(
                TerminalLine(
                    text = "NodeStudio Terminal (${currentSettings.installedNodeVersion} | npm ${currentSettings.installedNpmVersion})",
                    type = TerminalLineType.SYSTEM
                ),
                TerminalLine(
                    text = "Workspace: /${info.name}  •  Entry: ${info.entryFile}  •  Type 'help' for commands",
                    type = TerminalLineType.INFO
                )
            )
            refreshWorkspace()
            openFile(info.entryFile)
            searchNpm("lodash")
        }
    }

    fun selectTab(tab: StudioTab) {
        _selectedStudioTab.value = tab
    }

    suspend fun refreshWorkspace() {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        _project.value = repository.getProject(proj.id)
        _fileTree.value = repository.buildFileTree(dir, _expandedPaths.value)
        _installedPackages.value = npmClient.listInstalledPackages(dir)
    }

    fun toggleFolder(relativePath: String) {
        viewModelScope.launch {
            val current = _expandedPaths.value.toMutableSet()
            if (current.contains(relativePath)) current.remove(relativePath) else current.add(relativePath)
            _expandedPaths.value = current
            val proj = _project.value ?: return@launch
            _fileTree.value = repository.buildFileTree(File(proj.directoryPath), current)
        }
    }

    fun openFile(relativePath: String, switchToEditor: Boolean = false) {
        viewModelScope.launch {
            val proj = _project.value ?: return@launch
            val existing = _openTabs.value.find { it.relativePath == relativePath }
            if (existing != null) {
                _activeTabPath.value = existing.relativePath
                if (switchToEditor) _selectedStudioTab.value = StudioTab.EDITOR
                return@launch
            }
            val dir = File(proj.directoryPath)
            val content = repository.readFileContent(dir, relativePath)
            val tab = EditorTab(
                relativePath = relativePath,
                name = relativePath.substringAfterLast('/'),
                content = content,
                savedContent = content
            )
            _openTabs.value = _openTabs.value + tab
            _activeTabPath.value = tab.relativePath
            if (switchToEditor) _selectedStudioTab.value = StudioTab.EDITOR
        }
    }

    fun selectEditorTab(relativePath: String) {
        _activeTabPath.value = relativePath
    }

    fun closeEditorTab(relativePath: String) {
        val remaining = _openTabs.value.filterNot { it.relativePath == relativePath }
        _openTabs.value = remaining
        if (_activeTabPath.value == relativePath) {
            _activeTabPath.value = remaining.lastOrNull()?.relativePath
        }
    }

    fun updateActiveTabContent(newContent: String) {
        val activePath = _activeTabPath.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.relativePath == activePath) tab.copy(content = newContent) else tab
        }
    }

    fun saveActiveTab() {
        val activePath = _activeTabPath.value ?: return
        val tab = _openTabs.value.find { it.relativePath == activePath } ?: return
        val proj = _project.value ?: return
        viewModelScope.launch {
            val dir = File(proj.directoryPath)
            repository.saveFileContent(dir, tab.relativePath, tab.content)
            _openTabs.value = _openTabs.value.map {
                if (it.relativePath == activePath) it.copy(savedContent = it.content) else it
            }
            refreshWorkspace()
        }
    }

    suspend fun saveAllDirtyTabs() {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        val updated = _openTabs.value.map { tab ->
            if (tab.isDirty) {
                repository.saveFileContent(dir, tab.relativePath, tab.content)
                tab.copy(savedContent = tab.content)
            } else tab
        }
        _openTabs.value = updated
    }

    fun createFile(relativePath: String, initialContent: String = "") {
        val proj = _project.value ?: return
        viewModelScope.launch {
            val dir = File(proj.directoryPath)
            if (repository.createFile(dir, relativePath, initialContent)) {
                refreshWorkspace()
                openFile(relativePath, switchToEditor = true)
            }
        }
    }

    fun createFolder(relativePath: String) {
        val proj = _project.value ?: return
        viewModelScope.launch {
            val dir = File(proj.directoryPath)
            if (repository.createFolder(dir, relativePath)) {
                _expandedPaths.value = _expandedPaths.value + relativePath
                refreshWorkspace()
            }
        }
    }

    fun clearFileActionMessage() {
        _fileActionMessage.value = null
    }

    fun renameNode(oldPath: String, newName: String) {
        val proj = _project.value ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            _fileActionMessage.value = "Name cannot be empty."
            return
        }
        viewModelScope.launch {
            val dir = File(proj.directoryPath)
            val newPath = repository.renameNode(dir, oldPath, trimmedName)
            if (newPath == null) {
                _fileActionMessage.value = "Could not rename '${oldPath.substringAfterLast('/')}' to '$trimmedName' (name already exists or is invalid)."
                return@launch
            }
            _fileActionMessage.value = null
            if (newPath == oldPath) return@launch

            _openTabs.value = _openTabs.value.map { tab ->
                when {
                    tab.relativePath == oldPath -> tab.copy(
                        relativePath = newPath,
                        name = newPath.substringAfterLast('/')
                    )
                    tab.relativePath.startsWith("$oldPath/") -> tab.copy(
                        relativePath = newPath + tab.relativePath.removePrefix(oldPath)
                    )
                    else -> tab
                }
            }
            val currentActive = _activeTabPath.value
            if (currentActive == oldPath) {
                _activeTabPath.value = newPath
            } else if (currentActive != null && currentActive.startsWith("$oldPath/")) {
                _activeTabPath.value = newPath + currentActive.removePrefix(oldPath)
            }
            _expandedPaths.value = _expandedPaths.value.map { path ->
                when {
                    path == oldPath -> newPath
                    path.startsWith("$oldPath/") -> newPath + path.removePrefix(oldPath)
                    else -> path
                }
            }.toSet()

            if (proj.entryFile == oldPath) {
                repository.updateProjectEntryFile(proj.id, newPath)
            } else if (proj.entryFile.startsWith("$oldPath/")) {
                repository.updateProjectEntryFile(proj.id, newPath + proj.entryFile.removePrefix(oldPath))
            }
            refreshWorkspace()
        }
    }

    fun deleteNode(relativePath: String) {
        val proj = _project.value ?: return
        viewModelScope.launch {
            val dir = File(proj.directoryPath)
            if (repository.deleteNode(dir, relativePath)) {
                closeEditorTab(relativePath)
                refreshWorkspace()
            }
        }
    }

    fun setEntryPoint(relativePath: String) {
        val proj = _project.value ?: return
        viewModelScope.launch {
            repository.updateProjectEntryFile(proj.id, relativePath)
            refreshWorkspace()
        }
    }

    fun importFile(fileName: String, content: String) {
        createFile(fileName, content)
    }

    fun setLocalHostingEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLocalHostingEnabled(enabled) }
    }

    fun setWakeLockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setWakeLockEnabled(enabled) }
    }

    fun setRunningNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setRunningNotificationEnabled(enabled) }
    }

    fun runProjectEntry(switchToTerminal: Boolean = true) {
        val proj = _project.value ?: return
        val activeTab = _openTabs.value.find { it.relativePath == _activeTabPath.value }
        val targetScript = if (activeTab != null && activeTab.extension in setOf("js", "mjs", "cjs")) {
            activeTab.relativePath
        } else {
            proj.entryFile
        }
        lastLaunchedScript = targetScript
        val prefs = settings.value

        viewModelScope.launch {
            saveAllDirtyTabs()
            if (switchToTerminal) _selectedStudioTab.value = StudioTab.TERMINAL
            val dir = File(proj.directoryPath)
            val entryFile = File(dir, targetScript)
            appendTerminalLine(TerminalLine(text = "$ node $targetScript", type = TerminalLineType.COMMAND))
            nodeEngine.runFile(
                scope = viewModelScope,
                projectDir = dir,
                entryFile = entryFile,
                nodeVersion = prefs.installedNodeVersion,
                npmVersion = prefs.installedNpmVersion,
                lanHostingEnabled = prefs.localHostingEnabled,
                onOutput = { line -> appendTerminalLine(line) },
                onClear = { clearTerminal() },
                onFinished = { ok ->
                    viewModelScope.launch { refreshWorkspace() }
                    appendTerminalLine(
                        TerminalLine(
                            text = if (ok) "[done] process exited cleanly" else "[stopped]",
                            type = if (ok) TerminalLineType.SUCCESS else TerminalLineType.INFO
                        )
                    )
                }
            )
        }
    }

    fun stopExecution() {
        nodeEngine.stop()
        NodeRunnerService.stop(getApplication())
        appendTerminalLine(TerminalLine(text = "^C Process terminated", type = TerminalLineType.INFO))
    }

    fun testLocalServer(path: String = "/") {
        val port = activeServerPort.value ?: return
        executeTerminalCommand("curl http://127.0.0.1:$port$path")
    }

    fun executeTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        val subDir = currentSubDir ?: dir
        val prefs = settings.value

        _commandHistory.value = (_commandHistory.value.filterNot { it == trimmed } + trimmed).takeLast(30)
        if (trimmed.startsWith("node ") || trimmed.startsWith("npm start")) {
            lastLaunchedScript = trimmed.removePrefix("node ").trim().ifBlank { proj.entryFile }
        }

        viewModelScope.launch {
            saveAllDirtyTabs()
            terminalShell.executeCommand(
                rawCommand = trimmed,
                projectDir = dir,
                currentSubDir = subDir,
                nodeVersion = prefs.installedNodeVersion,
                npmVersion = prefs.installedNpmVersion,
                lanHostingEnabled = prefs.localHostingEnabled,
                scope = viewModelScope,
                onChangeDir = { newDir ->
                    currentSubDir = newDir
                    val rel = newDir.relativeTo(dir).path
                    _currentSubDirLabel.value = if (rel.isEmpty()) proj.name else "${proj.name}/$rel"
                },
                onOutput = { line -> appendTerminalLine(line) },
                onClear = { clearTerminal() },
                onFilesChanged = { refreshWorkspace() },
                onExecutionFinished = { ok ->
                    viewModelScope.launch { refreshWorkspace() }
                    appendTerminalLine(
                        TerminalLine(
                            text = if (ok) "[done]" else "[stopped]",
                            type = if (ok) TerminalLineType.SUCCESS else TerminalLineType.INFO
                        )
                    )
                }
            )
        }
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    private fun appendTerminalLine(line: TerminalLine) {
        val updated = (_terminalLines.value + line).takeLast(500)
        _terminalLines.value = updated
    }

    fun updateNpmQuery(query: String) {
        _npmSearchQuery.value = query
    }

    fun searchNpm(query: String = _npmSearchQuery.value) {
        viewModelScope.launch {
            _isSearchingNpm.value = true
            _npmError.value = null
            npmClient.searchPackages(query)
                .onSuccess { results ->
                    _npmSearchResults.value = results
                }
                .onFailure { err ->
                    _npmError.value = err.message ?: "Failed to query npmjs.org"
                }
            _isSearchingNpm.value = false
        }
    }

    fun installNpmPackage(packageSpec: String) {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        viewModelScope.launch {
            _isInstallingNpm.value = true
            _npmError.value = null
            _npmInstallLog.value = listOf("npm install $packageSpec")
            npmClient.installPackage(dir, packageSpec) { msg ->
                _npmInstallLog.value = _npmInstallLog.value + msg
            }.onSuccess { installed ->
                _npmInstallLog.value = _npmInstallLog.value + "+ ${installed.name}@${installed.installedVersion} installed"
                refreshWorkspace()
            }.onFailure { err ->
                _npmError.value = err.message ?: "Installation failed"
                _npmInstallLog.value = _npmInstallLog.value + "ERR! ${err.message}"
            }
            _isInstallingNpm.value = false
        }
    }

    fun updateNpmPackage(packageName: String) {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        viewModelScope.launch {
            _isInstallingNpm.value = true
            _npmError.value = null
            _npmInstallLog.value = listOf("npm update $packageName")
            npmClient.updatePackage(dir, packageName) { msg ->
                _npmInstallLog.value = _npmInstallLog.value + msg
            }.onSuccess { installed ->
                _npmInstallLog.value = _npmInstallLog.value + "+ updated ${installed.name}@${installed.installedVersion}"
                refreshWorkspace()
            }.onFailure { err ->
                _npmError.value = err.message ?: "Update failed"
            }
            _isInstallingNpm.value = false
        }
    }

    fun updateAllNpmPackages() {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        viewModelScope.launch {
            _isInstallingNpm.value = true
            _npmError.value = null
            _npmInstallLog.value = listOf("npm update (all)")
            npmClient.updateAllPackages(dir) { msg ->
                _npmInstallLog.value = _npmInstallLog.value + msg
            }.onSuccess { count ->
                _npmInstallLog.value = _npmInstallLog.value + "Updated $count package(s)"
                refreshWorkspace()
            }.onFailure { err ->
                _npmError.value = err.message ?: "Update failed"
            }
            _isInstallingNpm.value = false
        }
    }

    fun installAllDependencies() {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        viewModelScope.launch {
            _isInstallingNpm.value = true
            _npmError.value = null
            _npmInstallLog.value = listOf("npm install (from package.json)")
            npmClient.installAllFromPackageJson(dir) { msg ->
                _npmInstallLog.value = _npmInstallLog.value + msg
            }.onSuccess {
                refreshWorkspace()
            }.onFailure { err ->
                _npmError.value = err.message ?: "Install failed"
            }
            _isInstallingNpm.value = false
        }
    }

    fun uninstallNpmPackage(packageName: String) {
        val proj = _project.value ?: return
        val dir = File(proj.directoryPath)
        viewModelScope.launch {
            npmClient.uninstallPackage(dir, packageName)
            refreshWorkspace()
        }
    }
}
