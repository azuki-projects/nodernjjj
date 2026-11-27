package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GitCloneState
import com.example.data.model.ProjectInfo
import com.example.data.model.ProjectTemplate
import com.example.data.model.RuntimeInstallState
import com.example.data.model.RuntimeProfile
import com.example.data.model.RuntimeUpdateInfo
import com.example.data.prefs.StudioSettings
import com.example.ui.components.InputNameDialog
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.NodeEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeProjectsScreen(
    runtimeReady: RuntimeInstallState.Ready,
    projects: List<ProjectInfo>,
    totalProjectCount: Int,
    searchQuery: String,
    selectedFilter: ProjectTemplate?,
    settings: StudioSettings,
    gitCloneState: GitCloneState,
    runtimeUpdateInfo: RuntimeUpdateInfo?,
    isCheckingUpdates: Boolean,
    updateError: String?,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (ProjectTemplate?) -> Unit,
    onOpenProject: (ProjectInfo) -> Unit,
    onCreateProject: (String, String, ProjectTemplate) -> Unit,
    onCloneGitProject: (String, String?, String?) -> Unit,
    onResetGitCloneState: () -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onLocalHostingChange: (Boolean) -> Unit,
    onWakeLockChange: (Boolean) -> Unit,
    onRunningNotificationChange: (Boolean) -> Unit,
    onSelectRuntimeProfile: (RuntimeProfile) -> Unit,
    onCheckRuntimeUpdates: () -> Unit,
    onApplyRuntimeVersion: (String, String) -> Unit,
    onReinstallRuntime: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearch by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectInfo?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_art),
                            contentDescription = "NodeStudio icon",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showSettingsDialog = true }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NodeEmerald)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Node ${runtimeReady.nodeVersion} • npm ${runtimeReady.npmVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NodeEmerald
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) onSearchQueryChange("")
                        },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search projects"
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "IDE & Runtime Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    onResetGitCloneState()
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(id = R.string.projects_new_button)) },
                modifier = Modifier.testTag("create_project_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search workspaces by name or description...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("projects_search_input")
                )
            }

            // Hero Workspace Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(124.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner),
                    contentDescription = "Workspaces banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xE60B0F14), Color(0x880B0F14))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.projects_header_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$totalProjectCount local project(s) • ${runtimeReady.arch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            onResetGitCloneState()
                            showCreateDialog = true
                        },
                        modifier = Modifier.testTag("hero_new_project_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New / Git")
                    }
                }
            }

            // Template Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { onFilterSelected(null) },
                        label = { Text("All") }
                    )
                }
                items(ProjectTemplate.entries) { tpl ->
                    FilterChip(
                        selected = selectedFilter == tpl,
                        onClick = { onFilterSelected(if (selectedFilter == tpl) null else tpl) },
                        label = { Text(tpl.label) }
                    )
                }
            }

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No matching workspaces",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Create a new Node.js project or clone from Git to start coding.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 680.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = { renameTarget = project },
                                onDuplicate = { onDuplicateProject(project.id) },
                                onDelete = { deleteTarget = project }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var wasCloning by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(gitCloneState) {
            if (gitCloneState is GitCloneState.Cloning) {
                wasCloning = true
            } else if (wasCloning && gitCloneState is GitCloneState.Idle) {
                wasCloning = false
                showCreateDialog = false
            }
        }
        CreateOrCloneProjectDialog(
            gitCloneState = gitCloneState,
            onDismiss = {
                if (gitCloneState !is GitCloneState.Cloning) {
                    onResetGitCloneState()
                    showCreateDialog = false
                }
            },
            onCreate = { name, desc, tpl ->
                showCreateDialog = false
                onCreateProject(name, desc, tpl)
            },
            onCloneGit = { url, name, branch ->
                onCloneGitProject(url, name, branch)
            }
        )
    }

    if (showSettingsDialog) {
        StudioSettingsDialog(
            runtimeReady = runtimeReady,
            settings = settings,
            runtimeUpdateInfo = runtimeUpdateInfo,
            isCheckingUpdates = isCheckingUpdates,
            updateError = updateError,
            onDismiss = { showSettingsDialog = false },
            onThemeModeChange = onThemeModeChange,
            onFontSizeChange = onFontSizeChange,
            onWordWrapChange = onWordWrapChange,
            onLocalHostingChange = onLocalHostingChange,
            onWakeLockChange = onWakeLockChange,
            onRunningNotificationChange = onRunningNotificationChange,
            onSelectRuntimeProfile = { profile ->
                showSettingsDialog = false
                onSelectRuntimeProfile(profile)
            },
            onCheckRuntimeUpdates = onCheckRuntimeUpdates,
            onApplyRuntimeVersion = { nodeVer, npmVer ->
                showSettingsDialog = false
                onApplyRuntimeVersion(nodeVer, npmVer)
            },
            onReinstallRuntime = {
                showSettingsDialog = false
                onReinstallRuntime()
            }
        )
    }

    renameTarget?.let { target ->
        InputNameDialog(
            title = "Rename Project",
            placeholder = "Project name",
            initialValue = target.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameProject(target.id, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete '${target.name}'?") },
            text = { Text("All files and node_modules inside this project folder will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(target.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectInfo,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (tplIcon, tplTint) = when (project.template) {
        ProjectTemplate.HTTP_API -> Icons.Default.Http to CyanPulse
        ProjectTemplate.CRYPTO_FS -> Icons.Default.Lock to NodeEmerald
        ProjectTemplate.CLI_TOOL -> Icons.Default.Terminal to AmberGlow
        ProjectTemplate.GIT_CLONE -> Icons.Default.CloudDownload to CyanPulse
        ProjectTemplate.BLANK -> Icons.Default.Code to MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}")
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tplTint.copy(alpha = 0.14f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tplIcon,
                            contentDescription = null,
                            tint = tplTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.template.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tplTint
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("project_more_${project.id}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Manage project ${project.name}")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = { menuExpanded = false; onRename() },
                            modifier = Modifier.testTag("project_menu_rename_${project.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { menuExpanded = false; onDuplicate() },
                            modifier = Modifier.testTag("project_menu_duplicate_${project.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                            modifier = Modifier.testTag("project_menu_delete_${project.id}")
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaBadge(text = project.entryFile)
                    MetaBadge(text = "${project.fileCount} files")
                    if (project.dependencyCount > 0) {
                        MetaBadge(text = "${project.dependencyCount} npm")
                    }
                    if (project.gitRemoteUrl != null) {
                        MetaBadge(text = "Git")
                    }
                }
                Text(
                    text = formatRelativeTime(project.lastModified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_studio_${project.id}"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Studio")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CreateOrCloneProjectDialog(
    gitCloneState: GitCloneState,
    onDismiss: () -> Unit,
    onCreate: (String, String, ProjectTemplate) -> Unit,
    onCloneGit: (String, String?, String?) -> Unit
) {
    var modeTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.HTTP_API) }

    var repoUrl by remember { mutableStateOf("") }
    var gitBranch by remember { mutableStateOf("") }
    var gitCustomName by remember { mutableStateOf("") }
    val isCloning = gitCloneState is GitCloneState.Cloning

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Node.js Project", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = modeTab) {
                    Tab(
                        selected = modeTab == 0,
                        onClick = { if (!isCloning) modeTab = 0 },
                        text = { Text("Templates") },
                        modifier = Modifier.testTag("tab_new_template")
                    )
                    Tab(
                        selected = modeTab == 1,
                        onClick = { if (!isCloning) modeTab = 1 },
                        text = { Text("Git Clone") },
                        modifier = Modifier.testTag("tab_new_git_clone")
                    )
                }

                if (modeTab == 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Project Name") },
                        placeholder = { Text("my-node-service") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_project_name_input")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        singleLine = true,
                        label = { Text("Description (optional)") },
                        placeholder = { Text("Express-style JSON API") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Starter Template", style = MaterialTheme.typography.labelLarge)
                    val creatableTemplates = listOf(
                        ProjectTemplate.HTTP_API,
                        ProjectTemplate.CRYPTO_FS,
                        ProjectTemplate.CLI_TOOL,
                        ProjectTemplate.BLANK
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        creatableTemplates.forEach { tpl ->
                            val isSelected = selectedTemplate == tpl
                            Surface(
                                onClick = { selectedTemplate = tpl },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = tpl.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tpl.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        singleLine = true,
                        enabled = !isCloning,
                        label = { Text("Git Repository URL (HTTPS)") },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("git_clone_url_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { if (!isCloning) repoUrl = "https://github.com/lukeed/kleur.git" },
                            label = { Text("kleur", style = MaterialTheme.typography.labelSmall) }
                        )
                        AssistChip(
                            onClick = { if (!isCloning) repoUrl = "https://github.com/sindresorhus/ms.git" },
                            label = { Text("ms", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    OutlinedTextField(
                        value = gitCustomName,
                        onValueChange = { gitCustomName = it },
                        singleLine = true,
                        enabled = !isCloning,
                        label = { Text("Project Name (optional)") },
                        placeholder = { Text("Auto-detected from repo URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gitBranch,
                        onValueChange = { gitBranch = it },
                        singleLine = true,
                        enabled = !isCloning,
                        label = { Text("Branch (optional)") },
                        placeholder = { Text("default branch") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (gitCloneState is GitCloneState.Cloning) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { gitCloneState.progressFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = NodeEmerald
                            )
                            Text(
                                text = gitCloneState.progressText,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPulse
                            )
                        }
                    } else if (gitCloneState is GitCloneState.Error) {
                        Text(
                            text = gitCloneState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (modeTab == 0) {
                Button(
                    onClick = {
                        onCreate(
                            name.ifBlank { "node-workspace" },
                            description,
                            selectedTemplate
                        )
                    },
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create & Open")
                }
            } else {
                Button(
                    onClick = { onCloneGit(repoUrl, gitCustomName.ifBlank { null }, gitBranch.ifBlank { null }) },
                    enabled = repoUrl.isNotBlank() && !isCloning,
                    modifier = Modifier.testTag("confirm_git_clone_button")
                ) {
                    if (isCloning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Cloning...")
                    } else {
                        Text("Clone & Open")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCloning) { Text("Cancel") }
        }
    )
}

@Composable
private fun StudioSettingsDialog(
    runtimeReady: RuntimeInstallState.Ready,
    settings: StudioSettings,
    runtimeUpdateInfo: RuntimeUpdateInfo?,
    isCheckingUpdates: Boolean,
    updateError: String?,
    onDismiss: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onLocalHostingChange: (Boolean) -> Unit,
    onWakeLockChange: (Boolean) -> Unit,
    onRunningNotificationChange: (Boolean) -> Unit,
    onSelectRuntimeProfile: (RuntimeProfile) -> Unit,
    onCheckRuntimeUpdates: () -> Unit,
    onApplyRuntimeVersion: (String, String) -> Unit,
    onReinstallRuntime: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runtime & Studio Settings", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Node.js & npm Version Selector + Updater
                Text("Node.js & npm Version", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuntimeProfile.PRESETS.forEach { profile ->
                        val isCurrent = settings.installedNodeVersion == profile.nodeVersion &&
                            settings.installedNpmVersion == profile.npmVersion
                        Surface(
                            onClick = { if (!isCurrent) onSelectRuntimeProfile(profile) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.testTag("runtime_profile_${profile.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${profile.channelLabel} — ${profile.nodeVersion}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "npm v${profile.npmVersion} • ${profile.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    MetaBadge("ACTIVE")
                                }
                            }
                        }
                    }
                }

                // Runtime Update Checker
                FilledTonalButton(
                    onClick = onCheckRuntimeUpdates,
                    enabled = !isCheckingUpdates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_runtime_updates_button")
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Checking nodejs.org & npmjs.org...")
                    } else {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check Node.js & npm Updates")
                    }
                }

                if (runtimeUpdateInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Latest LTS: ${runtimeUpdateInfo.latestNodeLts} • Current: ${runtimeUpdateInfo.latestNodeCurrent}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Latest npm: v${runtimeUpdateInfo.latestNpmVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        onApplyRuntimeVersion(
                                            runtimeUpdateInfo.latestNodeLts,
                                            runtimeUpdateInfo.latestNpmVersion
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Update LTS", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onApplyRuntimeVersion(
                                            runtimeUpdateInfo.latestNodeCurrent,
                                            runtimeUpdateInfo.latestNpmVersion
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Update Current", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (!updateError.isNullOrBlank()) {
                    Text(updateError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Local Hosting, WakeLock & Notification
                Text("Local Hosting & Execution", style = MaterialTheme.typography.labelLarge)
                SettingSwitchRow(
                    title = "LAN Local Hosting (0.0.0.0)",
                    subtitle = "Expose http.createServer on device Wi-Fi IP",
                    checked = settings.localHostingEnabled,
                    onCheckedChange = onLocalHostingChange
                )
                SettingSwitchRow(
                    title = "WakeLock (Keep CPU Awake)",
                    subtitle = "Prevent sleep while server or script is running",
                    checked = settings.wakeLockEnabled,
                    onCheckedChange = onWakeLockChange
                )
                SettingSwitchRow(
                    title = "Running Notification",
                    subtitle = "Show foreground status with Stop action",
                    checked = settings.runningNotificationEnabled,
                    onCheckedChange = onRunningNotificationChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Appearance & Editor", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "Auto").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }

                Column {
                    Text(
                        text = "Editor Font Size: ${settings.editorFontSizeSp}sp",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.editorFontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt()) },
                        valueRange = 11f..20f,
                        steps = 8
                    )
                }

                SettingSwitchRow(
                    title = "Editor Word Wrap",
                    subtitle = "Wrap long lines inside the code editor",
                    checked = settings.wordWrap,
                    onCheckedChange = onWordWrapChange
                )

                OutlinedButton(
                    onClick = onReinstallRuntime,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Re-verify / Reinstall (${runtimeReady.nodeVersion})")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
    }
}
