package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.fs.ProjectRepository
import com.example.data.model.FileNode
import com.example.data.model.ProjectInfo
import com.example.data.model.ProjectTemplate
import com.example.data.model.RuntimeInstallState
import com.example.data.model.GitCloneState
import com.example.data.prefs.StudioSettings
import com.example.runtime.NodeCoreModules
import com.example.runtime.formatThrowableLines
import com.example.ui.components.FileExplorerPane
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.components.TerminalPane
import com.example.ui.components.applyAltChord
import com.example.ui.components.applyCtrlChord
import com.example.ui.components.autocompleteTerminalInput
import com.example.ui.components.computeLineStartOffsets
import com.example.ui.screens.HomeProjectsScreen
import com.example.ui.theme.DarkSyntaxColors
import com.example.ui.theme.NodeStudioTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app_name matches NodeStudio`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NodeStudio", appName)
    }

    @Test
    fun `syntax highlighter preserves exact text for VisualTransformation`() {
        val jsCode = """
            // comment
            const http = require('http');
            const port = 3000;
            async function start() { return `listening:${'$'}{port}`; }
        """.trimIndent()
        val highlightedJs = SyntaxHighlighter.highlight(jsCode, "js", DarkSyntaxColors)
        assertEquals(jsCode, highlightedJs.text)
        assertTrue(highlightedJs.spanStyles.isNotEmpty())

        val jsonCode = """{"name": "demo", "count": 42, "ok": true}"""
        val highlightedJson = SyntaxHighlighter.highlight(jsonCode, "json", DarkSyntaxColors)
        assertEquals(jsonCode, highlightedJson.text)
    }

    @Test
    fun `project repository creates, renames projects and nested files cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ProjectRepository(context)
        repo.workspacesDir.deleteRecursively()
        repo.seedStarterProjectsIfEmpty()

        val initial = repo.listProjects()
        assertEquals(2, initial.size)

        val custom = repo.createProject("test-cli", "CLI test", ProjectTemplate.CLI_TOOL)
        repo.renameProject(custom.id, "renamed-cli")
        assertEquals("renamed-cli", repo.getProject(custom.id)?.name)

        val dir = File(custom.directoryPath)
        repo.createFolder(dir, "utils")
        repo.createFile(dir, "utils/helper.js", "module.exports = 1;")
        val renamedFile = repo.renameNode(dir, "utils/helper.js", "helper2.js")
        assertEquals("utils/helper2.js", renamedFile)

        val renamedFolder = repo.renameNode(dir, "utils", "lib")
        assertEquals("lib", renamedFolder)
        assertTrue(File(dir, "lib/helper2.js").exists())

        repo.deleteProject(custom.id)
        assertEquals(2, repo.listProjects().size)
    }

    @Test
    fun `computeLineStartOffsets accurately maps every logical line including trailing newline`() {
        val offsets = computeLineStartOffsets("const a = 1;\nconsole.log(a);\n")
        assertEquals(3, offsets.size)
        assertEquals(0, offsets[0])
        assertEquals(13, offsets[1])
        assertEquals(29, offsets[2])

        val emptyOffsets = computeLineStartOffsets("")
        assertEquals(1, emptyOffsets.size)
        assertEquals(0, emptyOffsets[0])
    }

    @Test
    fun `node core modules and throwable formatter preserve full error messages and causes`() {
        val script = NodeCoreModules.buildBootstrapScript(
            projectPath = "/tmp/demo",
            entryFilePath = "/tmp/demo/server.js",
            nodeVersion = "v22.14.0",
            npmVersion = "10.9.2",
            arch = "arm64-v8a",
            argvJson = "[\"node\",\"/tmp/demo/server.js\"]",
            envJson = "{\"PORT\":\"3000\"}"
        )
        assertTrue(script.contains("__formatError"))
        assertTrue(script.contains("global.__runEntry"))
        assertTrue(script.contains("pkg.exports"))

        val rootCause = IllegalStateException("Port 3000 EADDRINUSE")
        val topError = RuntimeException("ReferenceError: foo is not defined\n    at index.js:4", rootCause)
        val formatted = formatThrowableLines(topError)
        assertTrue(formatted.any { it.contains("ReferenceError: foo is not defined") })
        assertTrue(formatted.any { it.contains("at index.js:4") })
        assertTrue(formatted.any { it.contains("Caused by: IllegalStateException: Port 3000 EADDRINUSE") })
    }

    @Test
    fun `terminal Ctrl and Alt chords and Tab autocomplete work accurately`() {
        val ctrlW = applyCtrlChord('w', TextFieldValue("npm install express", TextRange(19)))
        assertEquals("npm install ", ctrlW.field.text)

        val ctrlA = applyCtrlChord('a', TextFieldValue("node app.js", TextRange(11)))
        assertEquals(0, ctrlA.field.selection.start)

        val ctrlC = applyCtrlChord('c', TextFieldValue("node app.js", TextRange(5)))
        assertTrue(ctrlC.stopProcess)
        assertEquals("", ctrlC.field.text)

        val ctrlL = applyCtrlChord('l', TextFieldValue("ls", TextRange(2)))
        assertTrue(ctrlL.clearTerminal)

        val altDot = applyAltChord('.', TextFieldValue("cat ", TextRange(4)), listOf("node server.js"))
        assertEquals("cat server.js", altDot.field.text)

        val altB = applyAltChord('b', TextFieldValue("node server.js", TextRange(14)), emptyList())
        assertEquals(5, altB.field.selection.start)

        val completed = autocompleteTerminalInput(
            TextFieldValue("node ser", TextRange(8)),
            listOf("package.json", "server.js", "utils/math.js")
        )
        assertEquals("node server.js", completed.text)
    }

    @Test
    fun `clicking Rename in FileExplorerPane opens dialog and confirms rename`() {
        var renamedArgs: Pair<String, String>? = null
        val nodes = listOf(
            FileNode(
                name = "index.js",
                relativePath = "index.js",
                isDirectory = false,
                sizeBytes = 128,
                lastModified = 1000L,
                depth = 0
            )
        )

        composeRule.setContent {
            NodeStudioTheme(themeMode = "dark") {
                FileExplorerPane(
                    projectName = "demo",
                    entryFile = "index.js",
                    fileTree = nodes,
                    activeFilePath = "index.js",
                    onToggleFolder = {},
                    onOpenFile = {},
                    onCreateFile = { _, _ -> },
                    onCreateFolder = {},
                    onRenameNode = { oldPath, newName -> renamedArgs = oldPath to newName },
                    onDeleteNode = {},
                    onSetEntryPoint = {},
                    onImportFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("file_more_index.js").performClick()
        composeRule.onNodeWithTag("file_menu_rename_index.js").performClick()
        composeRule.onNodeWithTag("dialog_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_name_input").performTextReplacement("app.js")
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()

        assertNotNull(renamedArgs)
        assertEquals("index.js" to "app.js", renamedArgs)
    }

    @Test
    fun `clicking Rename on HomeProjectsScreen project card opens dialog and confirms rename`() {
        var renamedProject: Pair<String, String>? = null
        val project = ProjectInfo(
            id = "demo-1234",
            name = "hello-api",
            description = "Demo project",
            entryFile = "server.js",
            template = ProjectTemplate.HTTP_API,
            lastModified = System.currentTimeMillis(),
            fileCount = 3,
            dependencyCount = 0,
            directoryPath = "/tmp/demo-1234"
        )

        composeRule.setContent {
            NodeStudioTheme(themeMode = "dark") {
                HomeProjectsScreen(
                    runtimeReady = RuntimeInstallState.Ready("v22.14.0", "10.9.2", "arm64-v8a", "/tmp/node"),
                    projects = listOf(project),
                    totalProjectCount = 1,
                    searchQuery = "",
                    selectedFilter = null,
                    settings = StudioSettings(),
                    gitCloneState = GitCloneState.Idle,
                    runtimeUpdateInfo = null,
                    isCheckingUpdates = false,
                    updateError = null,
                    onSearchQueryChange = {},
                    onFilterSelected = {},
                    onOpenProject = {},
                    onCreateProject = { _, _, _ -> },
                    onCloneGitProject = { _, _, _ -> },
                    onResetGitCloneState = {},
                    onRenameProject = { id, newName -> renamedProject = id to newName },
                    onDuplicateProject = {},
                    onDeleteProject = {},
                    onThemeModeChange = {},
                    onFontSizeChange = {},
                    onWordWrapChange = {},
                    onLocalHostingChange = {},
                    onWakeLockChange = {},
                    onRunningNotificationChange = {},
                    onSelectRuntimeProfile = {},
                    onCheckRuntimeUpdates = {},
                    onApplyRuntimeVersion = { _, _ -> },
                    onReinstallRuntime = {}
                )
            }
        }

        composeRule.onNodeWithTag("project_more_demo-1234").performClick()
        composeRule.onNodeWithTag("project_menu_rename_demo-1234").performClick()
        composeRule.onNodeWithTag("dialog_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_name_input").performTextReplacement("my-renamed-api")
        composeRule.onNodeWithTag("dialog_confirm_button").performClick()

        assertNotNull(renamedProject)
        assertEquals("demo-1234" to "my-renamed-api", renamedProject)
    }

    @Test
    fun `TerminalPane shows Ctrl and Alt key caps and arms modifier badge`() {
        composeRule.setContent {
            NodeStudioTheme(themeMode = "dark") {
                TerminalPane(
                    entryFile = "server.js",
                    currentDirLabel = "demo",
                    lines = emptyList(),
                    commandHistory = emptyList(),
                    isRunning = false,
                    activeServerPort = null,
                    lanAddresses = emptyList(),
                    localHostingEnabled = true,
                    wakeLockEnabled = true,
                    runningNotificationEnabled = true,
                    onToggleLocalHosting = {},
                    onToggleWakeLock = {},
                    onToggleRunningNotification = {},
                    onExecuteCommand = {},
                    onStopProcess = {},
                    onTestServer = {},
                    onClear = {},
                    workspacePaths = listOf("server.js")
                )
            }
        }

        composeRule.onNodeWithTag("terminal_ctrl_key").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("terminal_modifier_badge").assertIsDisplayed()
        composeRule.onNodeWithText("CTRL+").assertIsDisplayed()

        composeRule.onNodeWithTag("terminal_alt_key").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("ALT+").assertIsDisplayed()
    }

    @Test
    fun `MainActivity launches and renders NodeStudioApp cleanly`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
        val activity = controller.get()
        assertTrue(activity != null && !activity.isFinishing)
    }
}
