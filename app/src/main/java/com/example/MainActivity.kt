package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.RuntimeInstallState
import com.example.ui.screens.HomeProjectsScreen
import com.example.ui.screens.StartupSetupScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.NodeStudioTheme
import com.example.ui.viewmodel.MainViewModel

const val ROUTE_HOME = "home"
const val ROUTE_STUDIO = "studio/{projectId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NodeStudioApp()
        }
    }
}

@Composable
fun NodeStudioApp(
    mainViewModel: MainViewModel = viewModel()
) {
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val installState by mainViewModel.installState.collectAsStateWithLifecycle()
    val filteredProjects by mainViewModel.filteredProjects.collectAsStateWithLifecycle()
    val allProjectsCount by mainViewModel.allProjectsCount.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by mainViewModel.selectedFilter.collectAsStateWithLifecycle()
    val gitCloneState by mainViewModel.gitCloneState.collectAsStateWithLifecycle()
    val runtimeUpdateInfo by mainViewModel.runtimeUpdateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdates by mainViewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val updateError by mainViewModel.updateError.collectAsStateWithLifecycle()

    var lastReadyState by remember { mutableStateOf<RuntimeInstallState.Ready?>(null) }
    val currentReady = installState as? RuntimeInstallState.Ready
    if (currentReady != null && lastReadyState != currentReady) {
        lastReadyState = currentReady
    }

    NodeStudioTheme(themeMode = settings.themeMode) {
        Crossfade(
            targetState = installState is RuntimeInstallState.Ready,
            modifier = Modifier.fillMaxSize(),
            label = "runtime_gate_crossfade"
        ) { isReady ->
            val readyState = currentReady ?: lastReadyState
            if (!isReady || readyState == null) {
                StartupSetupScreen(
                    installState = installState,
                    onRetry = { mainViewModel.startAutoInstall(forceReinstall = true) }
                )
            } else {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ROUTE_HOME) {
                        HomeProjectsScreen(
                            runtimeReady = readyState,
                            projects = filteredProjects,
                            totalProjectCount = allProjectsCount,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            settings = settings,
                            gitCloneState = gitCloneState,
                            runtimeUpdateInfo = runtimeUpdateInfo,
                            isCheckingUpdates = isCheckingUpdates,
                            updateError = updateError,
                            onSearchQueryChange = mainViewModel::setSearchQuery,
                            onFilterSelected = mainViewModel::setTemplateFilter,
                            onOpenProject = { project ->
                                navController.navigate("studio/${project.id}")
                            },
                            onCreateProject = { name, desc, template ->
                                mainViewModel.createProject(name, desc, template) { created ->
                                    navController.navigate("studio/${created.id}")
                                }
                            },
                            onCloneGitProject = { url, name, branch ->
                                mainViewModel.cloneGitProject(url, name, branch) { created ->
                                    navController.navigate("studio/${created.id}")
                                }
                            },
                            onResetGitCloneState = mainViewModel::resetGitCloneState,
                            onRenameProject = mainViewModel::renameProject,
                            onDuplicateProject = mainViewModel::duplicateProject,
                            onDeleteProject = mainViewModel::deleteProject,
                            onThemeModeChange = mainViewModel::setThemeMode,
                            onFontSizeChange = mainViewModel::setEditorFontSize,
                            onWordWrapChange = mainViewModel::setWordWrap,
                            onLocalHostingChange = mainViewModel::setLocalHostingEnabled,
                            onWakeLockChange = mainViewModel::setWakeLockEnabled,
                            onRunningNotificationChange = mainViewModel::setRunningNotificationEnabled,
                            onSelectRuntimeProfile = mainViewModel::switchRuntimeProfile,
                            onCheckRuntimeUpdates = mainViewModel::checkForRuntimeUpdates,
                            onApplyRuntimeVersion = mainViewModel::applyCustomRuntimeVersion,
                            onReinstallRuntime = { mainViewModel.startAutoInstall(forceReinstall = true) }
                        )
                    }

                    composable(
                        route = ROUTE_STUDIO,
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                        StudioScreen(
                            projectId = projectId,
                            settings = settings,
                            onBackHome = {
                                mainViewModel.refreshProjects()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
