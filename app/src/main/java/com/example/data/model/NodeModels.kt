package com.example.data.model

import java.util.UUID

enum class ProjectTemplate(
    val id: String,
    val label: String,
    val subtitle: String,
    val defaultEntry: String
) {
    HTTP_API(
        id = "http_api",
        label = "HTTP Server API",
        subtitle = "Live local HTTP server with JSON routes & logging",
        defaultEntry = "server.js"
    ),
    CRYPTO_FS(
        id = "crypto_fs",
        label = "Async FS & Crypto",
        subtitle = "Local file I/O, SHA-256 hashing & modular require()",
        defaultEntry = "index.js"
    ),
    CLI_TOOL(
        id = "cli_tool",
        label = "CLI Utility",
        subtitle = "Interactive Node.js script with process.argv & timers",
        defaultEntry = "cli.js"
    ),
    BLANK(
        id = "blank",
        label = "Blank Workspace",
        subtitle = "Minimal index.js & package.json starter",
        defaultEntry = "index.js"
    ),
    GIT_CLONE(
        id = "git_clone",
        label = "Git Repository",
        subtitle = "Cloned from remote Git repository",
        defaultEntry = "index.js"
    );

    companion object {
        fun fromId(id: String): ProjectTemplate =
            entries.find { it.id == id } ?: BLANK
    }
}

data class RuntimeProfile(
    val id: String,
    val nodeVersion: String,
    val npmVersion: String,
    val channelLabel: String,
    val description: String
) {
    companion object {
        val PRESETS = listOf(
            RuntimeProfile(
                id = "node22_lts",
                nodeVersion = "v22.14.0",
                npmVersion = "10.9.2",
                channelLabel = "22 LTS (Jod)",
                description = "Recommended Active LTS runtime"
            ),
            RuntimeProfile(
                id = "node20_lts",
                nodeVersion = "v20.18.3",
                npmVersion = "10.8.2",
                channelLabel = "20 LTS (Iron)",
                description = "Enterprise Maintenance LTS"
            ),
            RuntimeProfile(
                id = "node23_current",
                nodeVersion = "v23.9.0",
                npmVersion = "10.9.2",
                channelLabel = "23 Current",
                description = "Latest features & ES module interop"
            ),
            RuntimeProfile(
                id = "node18_lts",
                nodeVersion = "v18.20.7",
                npmVersion = "10.8.2",
                channelLabel = "18 Legacy LTS",
                description = "Legacy CommonJS compatibility"
            )
        )
    }
}

data class RuntimeUpdateInfo(
    val latestNodeLts: String,
    val latestNodeCurrent: String,
    val latestNpmVersion: String,
    val hasUpdate: Boolean,
    val checkedAt: Long = System.currentTimeMillis()
)

data class ProjectInfo(
    val id: String,
    val name: String,
    val description: String,
    val entryFile: String,
    val template: ProjectTemplate,
    val lastModified: Long,
    val fileCount: Int,
    val dependencyCount: Int,
    val directoryPath: String,
    val gitRemoteUrl: String? = null
)

data class FileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val depth: Int,
    val isExpanded: Boolean = false
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

data class EditorTab(
    val relativePath: String,
    val name: String,
    val content: String,
    val savedContent: String
) {
    val isDirty: Boolean get() = content != savedContent
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class NpmPackageSearchResult(
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val keywords: List<String> = emptyList()
)

data class InstalledPackage(
    val name: String,
    val versionConstraint: String,
    val installedVersion: String?,
    val description: String,
    val hasFilesOnDisk: Boolean
)

enum class TerminalLineType {
    COMMAND,
    STDOUT,
    STDERR,
    INFO,
    SUCCESS,
    SYSTEM
}

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: TerminalLineType,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface RuntimeInstallState {
    data object Checking : RuntimeInstallState
    data class Installing(
        val stepIndex: Int,
        val totalSteps: Int,
        val stepTitle: String,
        val progress: Float,
        val logs: List<String>
    ) : RuntimeInstallState
    data class Ready(
        val nodeVersion: String,
        val npmVersion: String,
        val arch: String,
        val runtimePath: String
    ) : RuntimeInstallState
    data class Failed(val errorMessage: String) : RuntimeInstallState
}

sealed interface GitCloneState {
    data object Idle : GitCloneState
    data class Cloning(val progressText: String, val progressFraction: Float) : GitCloneState
    data class Error(val message: String) : GitCloneState
}

enum class StudioTab {
    FILES,
    EDITOR,
    NPM,
    TERMINAL
}
