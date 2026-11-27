package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.StudioTab
import com.example.data.prefs.StudioSettings
import com.example.ui.components.CodeEditorView
import com.example.ui.components.FileExplorerPane
import com.example.ui.components.NpmManagerPane
import com.example.ui.components.TerminalPane
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.NodeEmerald
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    projectId: String,
    settings: StudioSettings,
    onBackHome: () -> Unit,
    studioViewModel: StudioViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        studioViewModel.loadProject(projectId)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        studioViewModel.runProjectEntry(switchToTerminal = true)
    }

    fun launchWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            settings.runningNotificationEnabled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            studioViewModel.runProjectEntry(switchToTerminal = true)
        }
    }

    val project by studioViewModel.project.collectAsStateWithLifecycle()
    val selectedTab by studioViewModel.selectedStudioTab.collectAsStateWithLifecycle()
    val fileTree by studioViewModel.fileTree.collectAsStateWithLifecycle()
    val openTabs by studioViewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabPath by studioViewModel.activeTabPath.collectAsStateWithLifecycle()
    val installedPackages by studioViewModel.installedPackages.collectAsStateWithLifecycle()
    val npmQuery by studioViewModel.npmSearchQuery.collectAsStateWithLifecycle()
    val npmResults by studioViewModel.npmSearchResults.collectAsStateWithLifecycle()
    val isSearchingNpm by studioViewModel.isSearchingNpm.collectAsStateWithLifecycle()
    val isInstallingNpm by studioViewModel.isInstallingNpm.collectAsStateWithLifecycle()
    val npmInstallLog by studioViewModel.npmInstallLog.collectAsStateWithLifecycle()
    val npmError by studioViewModel.npmError.collectAsStateWithLifecycle()
    val terminalLines by studioViewModel.terminalLines.collectAsStateWithLifecycle()
    val commandHistory by studioViewModel.commandHistory.collectAsStateWithLifecycle()
    val currentDirLabel by studioViewModel.currentSubDirLabel.collectAsStateWithLifecycle()
    val isRunning by studioViewModel.isRunning.collectAsStateWithLifecycle()
    val activeServerPort by studioViewModel.activeServerPort.collectAsStateWithLifecycle()
    val lanAddresses by studioViewModel.lanAddresses.collectAsStateWithLifecycle()
    val fileActionMessage by studioViewModel.fileActionMessage.collectAsStateWithLifecycle()

    val hasDirtyTabs = openTabs.any { it.isDirty }

    BackHandler {
        onBackHome()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackHome,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to workspaces")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = project?.name ?: "Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeTabPath ?: (project?.entryFile ?: "index.js"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (hasDirtyTabs) {
                        IconButton(
                            onClick = { studioViewModel.saveActiveTab() },
                            modifier = Modifier.testTag("top_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = AmberGlow)
                        }
                    }

                    if (isRunning) {
                        Button(
                            onClick = { studioViewModel.stopExecution() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("studio_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(id = R.string.studio_stop_button), style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = { launchWithNotificationCheck() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("studio_run_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(id = R.string.studio_run_button), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            )
        },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 760.dp) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.FILES,
                            onClick = { studioViewModel.selectTab(StudioTab.FILES) },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text(stringResource(id = R.string.tab_files)) },
                            modifier = Modifier.testTag("nav_files")
                        )
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.EDITOR,
                            onClick = { studioViewModel.selectTab(StudioTab.EDITOR) },
                            icon = {
                                BadgedBox(badge = {
                                    if (hasDirtyTabs) Badge(containerColor = AmberGlow)
                                }) {
                                    Icon(Icons.Default.Code, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(id = R.string.tab_editor)) },
                            modifier = Modifier.testTag("nav_editor")
                        )
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.NPM,
                            onClick = { studioViewModel.selectTab(StudioTab.NPM) },
                            icon = {
                                BadgedBox(badge = {
                                    if (installedPackages.isNotEmpty()) {
                                        Badge { Text(installedPackages.size.toString()) }
                                    }
                                }) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(id = R.string.tab_npm)) },
                            modifier = Modifier.testTag("nav_npm")
                        )
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.TERMINAL,
                            onClick = { studioViewModel.selectTab(StudioTab.TERMINAL) },
                            icon = {
                                BadgedBox(badge = {
                                    if (isRunning) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(NodeEmerald)
                                        )
                                    }
                                }) {
                                    Icon(Icons.Default.Terminal, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(id = R.string.tab_terminal)) },
                            modifier = Modifier.testTag("nav_terminal")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 760.dp

            if (isWideScreen) {
                var leftRailTab by remember { mutableIntStateOf(0) }
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(270.dp)
                            .fillMaxHeight()
                    ) {
                        TabRow(selectedTabIndex = leftRailTab) {
                            Tab(
                                selected = leftRailTab == 0,
                                onClick = { leftRailTab = 0 },
                                text = { Text("Files") }
                            )
                            Tab(
                                selected = leftRailTab == 1,
                                onClick = { leftRailTab = 1 },
                                text = { Text("npm (${installedPackages.size})") }
                            )
                        }
                        if (leftRailTab == 0) {
                            FileExplorerPane(
                                projectName = project?.name ?: "",
                                entryFile = project?.entryFile ?: "index.js",
                                fileTree = fileTree,
                                activeFilePath = activeTabPath,
                                onToggleFolder = studioViewModel::toggleFolder,
                                onOpenFile = { studioViewModel.openFile(it, switchToEditor = false) },
                                onCreateFile = studioViewModel::createFile,
                                onCreateFolder = studioViewModel::createFolder,
                                onRenameNode = studioViewModel::renameNode,
                                onDeleteNode = studioViewModel::deleteNode,
                                onSetEntryPoint = studioViewModel::setEntryPoint,
                                onImportFile = studioViewModel::importFile,
                                actionMessage = fileActionMessage,
                                onDismissActionMessage = studioViewModel::clearFileActionMessage
                            )
                        } else {
                            NpmManagerPane(
                                searchQuery = npmQuery,
                                searchResults = npmResults,
                                installedPackages = installedPackages,
                                isSearching = isSearchingNpm,
                                isInstalling = isInstallingNpm,
                                installLogs = npmInstallLog,
                                errorMessage = npmError,
                                onQueryChange = studioViewModel::updateNpmQuery,
                                onSearch = { studioViewModel.searchNpm() },
                                onInstallPackage = studioViewModel::installNpmPackage,
                                onInstallAll = studioViewModel::installAllDependencies,
                                onUpdatePackage = studioViewModel::updateNpmPackage,
                                onUpdateAll = studioViewModel::updateAllNpmPackages,
                                onUninstallPackage = studioViewModel::uninstallNpmPackage
                            )
                        }
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    CodeEditorView(
                        tabs = openTabs,
                        activeTabPath = activeTabPath,
                        fontSizeSp = settings.editorFontSizeSp,
                        wordWrap = settings.wordWrap,
                        onSelectTab = studioViewModel::selectEditorTab,
                        onCloseTab = studioViewModel::closeEditorTab,
                        onContentChange = studioViewModel::updateActiveTabContent,
                        onSave = studioViewModel::saveActiveTab,
                        onOpenFilesPane = { leftRailTab = 0 },
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    )

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    TerminalPane(
                        entryFile = project?.entryFile ?: "index.js",
                        currentDirLabel = currentDirLabel,
                        lines = terminalLines,
                        commandHistory = commandHistory,
                        isRunning = isRunning,
                        activeServerPort = activeServerPort,
                        lanAddresses = lanAddresses,
                        localHostingEnabled = settings.localHostingEnabled,
                        wakeLockEnabled = settings.wakeLockEnabled,
                        runningNotificationEnabled = settings.runningNotificationEnabled,
                        onToggleLocalHosting = studioViewModel::setLocalHostingEnabled,
                        onToggleWakeLock = studioViewModel::setWakeLockEnabled,
                        onToggleRunningNotification = studioViewModel::setRunningNotificationEnabled,
                        onExecuteCommand = studioViewModel::executeTerminalCommand,
                        onStopProcess = studioViewModel::stopExecution,
                        onTestServer = { studioViewModel.testLocalServer("/") },
                        onClear = studioViewModel::clearTerminal,
                        workspacePaths = fileTree.map { it.relativePath },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }
            } else {
                when (selectedTab) {
                    StudioTab.FILES -> FileExplorerPane(
                        projectName = project?.name ?: "",
                        entryFile = project?.entryFile ?: "index.js",
                        fileTree = fileTree,
                        activeFilePath = activeTabPath,
                        onToggleFolder = studioViewModel::toggleFolder,
                        onOpenFile = { studioViewModel.openFile(it, switchToEditor = true) },
                        onCreateFile = studioViewModel::createFile,
                        onCreateFolder = studioViewModel::createFolder,
                        onRenameNode = studioViewModel::renameNode,
                        onDeleteNode = studioViewModel::deleteNode,
                        onSetEntryPoint = studioViewModel::setEntryPoint,
                        onImportFile = studioViewModel::importFile,
                        actionMessage = fileActionMessage,
                        onDismissActionMessage = studioViewModel::clearFileActionMessage
                    )

                    StudioTab.EDITOR -> CodeEditorView(
                        tabs = openTabs,
                        activeTabPath = activeTabPath,
                        fontSizeSp = settings.editorFontSizeSp,
                        wordWrap = settings.wordWrap,
                        onSelectTab = studioViewModel::selectEditorTab,
                        onCloseTab = studioViewModel::closeEditorTab,
                        onContentChange = studioViewModel::updateActiveTabContent,
                        onSave = studioViewModel::saveActiveTab,
                        onOpenFilesPane = { studioViewModel.selectTab(StudioTab.FILES) }
                    )

                    StudioTab.NPM -> NpmManagerPane(
                        searchQuery = npmQuery,
                        searchResults = npmResults,
                        installedPackages = installedPackages,
                        isSearching = isSearchingNpm,
                        isInstalling = isInstallingNpm,
                        installLogs = npmInstallLog,
                        errorMessage = npmError,
                        onQueryChange = studioViewModel::updateNpmQuery,
                        onSearch = { studioViewModel.searchNpm() },
                        onInstallPackage = studioViewModel::installNpmPackage,
                        onInstallAll = studioViewModel::installAllDependencies,
                        onUpdatePackage = studioViewModel::updateNpmPackage,
                        onUpdateAll = studioViewModel::updateAllNpmPackages,
                        onUninstallPackage = studioViewModel::uninstallNpmPackage
                    )

                    StudioTab.TERMINAL -> TerminalPane(
                        entryFile = project?.entryFile ?: "index.js",
                        currentDirLabel = currentDirLabel,
                        lines = terminalLines,
                        commandHistory = commandHistory,
                        isRunning = isRunning,
                        activeServerPort = activeServerPort,
                        lanAddresses = lanAddresses,
                        localHostingEnabled = settings.localHostingEnabled,
                        wakeLockEnabled = settings.wakeLockEnabled,
                        runningNotificationEnabled = settings.runningNotificationEnabled,
                        onToggleLocalHosting = studioViewModel::setLocalHostingEnabled,
                        onToggleWakeLock = studioViewModel::setWakeLockEnabled,
                        onToggleRunningNotification = studioViewModel::setRunningNotificationEnabled,
                        onExecuteCommand = studioViewModel::executeTerminalCommand,
                        onStopProcess = studioViewModel::stopExecution,
                        onTestServer = { studioViewModel.testLocalServer("/") },
                        onClear = studioViewModel::clearTerminal,
                        workspacePaths = fileTree.map { it.relativePath }
                    )
                }
            }
        }
    }
}
