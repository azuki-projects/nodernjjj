package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.fs.ProjectRepository
import com.example.data.git.GitClient
import com.example.data.model.GitCloneState
import com.example.data.model.ProjectInfo
import com.example.data.model.ProjectTemplate
import com.example.data.model.RuntimeInstallState
import com.example.data.model.RuntimeProfile
import com.example.data.model.RuntimeUpdateInfo
import com.example.data.prefs.StudioPreferences
import com.example.data.prefs.StudioSettings
import com.example.runtime.NodeRuntimeInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ProjectRepository(application)
    val preferences = StudioPreferences(application)
    private val gitClient = GitClient()
    private val installer = NodeRuntimeInstaller(repository, preferences)

    val settings: StateFlow<StudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioSettings())

    private val _installState = MutableStateFlow<RuntimeInstallState>(RuntimeInstallState.Checking)
    val installState: StateFlow<RuntimeInstallState> = _installState.asStateFlow()

    private val _runtimeUpdateInfo = MutableStateFlow<RuntimeUpdateInfo?>(null)
    val runtimeUpdateInfo: StateFlow<RuntimeUpdateInfo?> = _runtimeUpdateInfo.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    private val _gitCloneState = MutableStateFlow<GitCloneState>(GitCloneState.Idle)
    val gitCloneState: StateFlow<GitCloneState> = _gitCloneState.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<ProjectTemplate?>(null)
    val selectedFilter: StateFlow<ProjectTemplate?> = _selectedFilter.asStateFlow()

    val filteredProjects: StateFlow<List<ProjectInfo>> =
        combine(_projects, _searchQuery, _selectedFilter) { list, query, filter ->
            list.filter { project ->
                val matchesQuery = query.isBlank() ||
                    project.name.contains(query, ignoreCase = true) ||
                    project.description.contains(query, ignoreCase = true)
                val matchesFilter = filter == null || project.template == filter
                matchesQuery && matchesFilter
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allProjectsCount: StateFlow<Int> =
        combine(_projects) { it[0].size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        startAutoInstall(forceReinstall = false)
    }

    fun startAutoInstall(
        forceReinstall: Boolean = false,
        targetNodeVersion: String? = null,
        targetNpmVersion: String? = null
    ) {
        viewModelScope.launch {
            installer.installOrVerify(
                forceReinstall = forceReinstall,
                targetNodeVersion = targetNodeVersion,
                targetNpmVersion = targetNpmVersion
            ).collect { state ->
                _installState.value = state
                if (state is RuntimeInstallState.Ready) {
                    refreshProjects()
                }
            }
        }
    }

    fun switchRuntimeProfile(profile: RuntimeProfile) {
        startAutoInstall(
            forceReinstall = false,
            targetNodeVersion = profile.nodeVersion,
            targetNpmVersion = profile.npmVersion
        )
    }

    fun applyCustomRuntimeVersion(nodeVersion: String, npmVersion: String) {
        val normalizedNode = if (nodeVersion.startsWith("v")) nodeVersion.trim() else "v${nodeVersion.trim()}"
        startAutoInstall(
            forceReinstall = false,
            targetNodeVersion = normalizedNode,
            targetNpmVersion = npmVersion.trim().ifBlank { NodeRuntimeInstaller.DEFAULT_NPM_VERSION }
        )
    }

    fun checkForRuntimeUpdates() {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _updateError.value = null
            val current = settings.value
            installer.checkForUpdates(
                currentNodeVersion = current.installedNodeVersion,
                currentNpmVersion = current.installedNpmVersion
            ).onSuccess { info ->
                _runtimeUpdateInfo.value = info
            }.onFailure { err ->
                _updateError.value = err.message ?: "Unable to check nodejs.org release feed"
            }
            _isCheckingUpdates.value = false
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _projects.value = repository.listProjects()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTemplateFilter(template: ProjectTemplate?) {
        _selectedFilter.value = template
    }

    fun createProject(
        name: String,
        description: String,
        template: ProjectTemplate,
        onCreated: (ProjectInfo) -> Unit = {}
    ) {
        viewModelScope.launch {
            val created = repository.createProject(name, description, template)
            refreshProjects()
            onCreated(created)
        }
    }

    fun cloneGitProject(
        repoUrl: String,
        customName: String?,
        branch: String?,
        onCreated: (ProjectInfo) -> Unit
    ) {
        viewModelScope.launch {
            _gitCloneState.value = GitCloneState.Cloning("Connecting to Git remote...", 0.08f)
            repository.createProjectFromGit(
                repoUrl = repoUrl,
                customName = customName,
                branch = branch,
                gitClient = gitClient,
                onProgress = { label, fraction ->
                    _gitCloneState.value = GitCloneState.Cloning(label, fraction)
                }
            ).onSuccess { project ->
                _gitCloneState.value = GitCloneState.Idle
                refreshProjects()
                onCreated(project)
            }.onFailure { err ->
                _gitCloneState.value = GitCloneState.Error(err.message ?: "Git clone failed")
            }
        }
    }

    fun resetGitCloneState() {
        _gitCloneState.value = GitCloneState.Idle
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newName)
            refreshProjects()
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
            refreshProjects()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            refreshProjects()
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setEditorFontSize(sizeSp: Int) {
        viewModelScope.launch { preferences.setEditorFontSize(sizeSp) }
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch { preferences.setWordWrap(enabled) }
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
}
