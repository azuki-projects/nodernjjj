package com.example.data.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File

class GitClient {

    suspend fun cloneRepository(
        repoUrl: String,
        branch: String? = null,
        targetDir: File,
        onProgress: (title: String, fraction: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedInput = repoUrl.trim()
            val cleanUrl = if (!trimmedInput.contains("://") && trimmedInput.contains(".")) {
                "https://$trimmedInput"
            } else {
                trimmedInput
            }
            require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
                "Enter a valid HTTPS Git repository URL"
            }

            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            val monitor = object : ProgressMonitor {
                private var currentTask = "Connecting to remote..."
                private var totalWork = 0
                private var completedWork = 0

                override fun start(totalTasks: Int) {
                    onProgress("Initializing Git clone...", 0.05f)
                }

                override fun beginTask(title: String?, totalWork: Int) {
                    currentTask = title ?: "Cloning..."
                    this.totalWork = totalWork
                    this.completedWork = 0
                    onProgress(currentTask, if (totalWork > 0) 0.1f else 0.2f)
                }

                override fun update(completed: Int) {
                    completedWork += completed
                    val fraction = if (totalWork > 0) {
                        (completedWork.toFloat() / totalWork.toFloat()).coerceIn(0.05f, 0.98f)
                    } else 0.5f
                    val pct = if (totalWork > 0) " (${(fraction * 100).toInt()}%)" else ""
                    onProgress("$currentTask$pct", fraction)
                }

                override fun endTask() {
                    onProgress("$currentTask done", 0.98f)
                }

                override fun isCancelled(): Boolean = false
                override fun showDuration(enabled: Boolean) {}
            }

            val cloneCmd = Git.cloneRepository()
                .setURI(cleanUrl)
                .setDirectory(targetDir)
                .setDepth(1)
                .setTimeout(30)
                .setProgressMonitor(monitor)

            if (!branch.isNullOrBlank()) {
                cloneCmd.setBranch(branch.trim())
            }

            cloneCmd.call().use {
                onProgress("Clone complete", 1f)
            }
            targetDir
        }.onFailure {
            if (targetDir.exists()) targetDir.deleteRecursively()
        }
    }

    suspend fun gitStatus(projectDir: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(projectDir).use { git ->
                val status = git.status().call()
                val branch = git.repository.branch
                buildString {
                    appendLine("On branch $branch")
                    if (status.isClean) {
                        append("nothing to commit, working tree clean")
                    } else {
                        status.modified.forEach { appendLine("  modified:   $it") }
                        status.untracked.forEach { appendLine("  untracked:  $it") }
                        status.added.forEach { appendLine("  added:      $it") }
                        status.removed.forEach { appendLine("  deleted:    $it") }
                    }
                }.trimEnd()
            }
        }
    }

    suspend fun gitLog(projectDir: File, maxCount: Int = 8): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(projectDir).use { git ->
                val commits = git.log().setMaxCount(maxCount).call()
                commits.joinToString("\n") { commit ->
                    val shortId = commit.id.abbreviate(7).name()
                    val msg = commit.shortMessage
                    "$shortId - $msg (${commit.authorIdent.name})"
                }.ifBlank { "No commits found" }
            }
        }
    }

    suspend fun gitPull(projectDir: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(projectDir).use { git ->
                val res = git.pull().setTimeout(25).call()
                if (res.isSuccessful) {
                    "Pull successful (${res.mergeResult?.mergeStatus ?: "up-to-date"})"
                } else {
                    "Pull finished with status: ${res.mergeResult?.mergeStatus}"
                }
            }
        }
    }
}
