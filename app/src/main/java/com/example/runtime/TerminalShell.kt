package com.example.runtime

import com.example.data.git.GitClient
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.data.npm.NpmRegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class TerminalShell(
    private val nodeEngine: NodeEngine,
    private val npmClient: NpmRegistryClient,
    private val gitClient: GitClient = GitClient()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun executeCommand(
        rawCommand: String,
        projectDir: File,
        currentSubDir: File,
        nodeVersion: String,
        npmVersion: String,
        lanHostingEnabled: Boolean,
        scope: CoroutineScope,
        onChangeDir: (File) -> Unit,
        onOutput: (TerminalLine) -> Unit,
        onClear: () -> Unit,
        onFilesChanged: suspend () -> Unit,
        onExecutionFinished: (Boolean) -> Unit = {}
    ) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return

        onOutput(TerminalLine(text = "$ $trimmed", type = TerminalLineType.COMMAND))

        val tokens = tokenizeCommand(trimmed)
        if (tokens.isEmpty()) return

        val cmd = tokens[0]
        val args = tokens.drop(1)

        when (cmd) {
            "clear", "cls" -> onClear()

            "help" -> {
                listOf(
                    "NodeStudio Shell Commands:",
                    "  node <file.js> [args]   Run a JavaScript file",
                    "  node -e \"<code>\"        Evaluate inline JavaScript",
                    "  node -v                 Show Node.js runtime version ($nodeVersion)",
                    "  npm start / npm run <s> Run script from package.json",
                    "  npm install [pkg]       Install package(s) from npmjs.org",
                    "  npm update [pkg]        Update package(s) to latest",
                    "  npm uninstall <pkg>     Remove package from node_modules",
                    "  npm list                List installed dependencies",
                    "  npm search <query>      Search npm registry",
                    "  git status|log|pull     Inspect or pull git repo",
                    "  git clone <url> [dir]   Clone a Git repo into workspace",
                    "  curl <url>              Send HTTP GET request",
                    "  ls | pwd | cd | cat | touch | mkdir | rm | echo | clear"
                ).forEach { line ->
                    onOutput(TerminalLine(text = line, type = TerminalLineType.INFO))
                }
            }

            "pwd" -> {
                val rel = currentSubDir.relativeTo(projectDir).path
                val display = if (rel.isEmpty()) "/${projectDir.name}" else "/${projectDir.name}/$rel"
                onOutput(TerminalLine(text = display, type = TerminalLineType.STDOUT))
            }

            "ls", "ll", "dir" -> {
                val target = if (args.isNotEmpty()) resolveInside(projectDir, currentSubDir, args[0]) else currentSubDir
                if (target == null || !target.exists()) {
                    onOutput(TerminalLine(text = "ls: cannot access '${args.firstOrNull()}': No such file or directory", type = TerminalLineType.STDERR))
                } else {
                    val items = target.listFiles()
                        ?.filter { it.name != ".nodestudio.json" }
                        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                        ?: emptyList()
                    if (items.isEmpty()) {
                        onOutput(TerminalLine(text = "(empty directory)", type = TerminalLineType.INFO))
                    } else {
                        items.forEach { f ->
                            val suffix = if (f.isDirectory) "/" else "  (${f.length()} B)"
                            onOutput(TerminalLine(text = "${f.name}$suffix", type = TerminalLineType.STDOUT))
                        }
                    }
                }
            }

            "cd" -> {
                val destArg = args.firstOrNull() ?: "."
                val target = if (destArg == "~" || destArg == "/") projectDir else resolveInside(projectDir, currentSubDir, destArg)
                if (target == null || !target.exists() || !target.isDirectory) {
                    onOutput(TerminalLine(text = "cd: no such directory: $destArg", type = TerminalLineType.STDERR))
                } else {
                    onChangeDir(target)
                }
            }

            "cat" -> {
                val fileArg = args.firstOrNull()
                val file = fileArg?.let { resolveInside(projectDir, currentSubDir, it) }
                if (file == null || !file.exists() || file.isDirectory) {
                    onOutput(TerminalLine(text = "cat: ${fileArg ?: "missing operand"}: No such file", type = TerminalLineType.STDERR))
                } else {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (file.length() > 512_000L) {
                                listOf("[cat] File exceeds 500 KB limit (${file.length()} bytes)")
                            } else {
                                file.readText().split('\n')
                            }
                        }
                    }.onSuccess { lines ->
                        lines.forEach { onOutput(TerminalLine(text = it, type = TerminalLineType.STDOUT)) }
                    }.onFailure { err ->
                        emitFailure("cat", err, onOutput)
                    }
                }
            }

            "touch" -> {
                val fileArg = args.firstOrNull()
                val file = fileArg?.let { resolveInside(projectDir, currentSubDir, it) }
                if (file == null) {
                    onOutput(TerminalLine(text = "touch: invalid path", type = TerminalLineType.STDERR))
                } else {
                    file.parentFile?.mkdirs()
                    if (!file.exists()) file.writeText("")
                    onFilesChanged()
                    onOutput(TerminalLine(text = "created ${file.name}", type = TerminalLineType.SUCCESS))
                }
            }

            "mkdir" -> {
                val dirArg = args.firstOrNull()
                val dir = dirArg?.let { resolveInside(projectDir, currentSubDir, it) }
                if (dir == null) {
                    onOutput(TerminalLine(text = "mkdir: invalid path", type = TerminalLineType.STDERR))
                } else {
                    dir.mkdirs()
                    onFilesChanged()
                    onOutput(TerminalLine(text = "created directory ${dir.name}/", type = TerminalLineType.SUCCESS))
                }
            }

            "rm" -> {
                val targetArg = args.lastOrNull()
                val target = targetArg?.let { resolveInside(projectDir, currentSubDir, it) }
                if (target == null || !target.exists() || target == projectDir) {
                    onOutput(TerminalLine(text = "rm: cannot remove '${targetArg ?: ""}'", type = TerminalLineType.STDERR))
                } else {
                    target.deleteRecursively()
                    onFilesChanged()
                    onOutput(TerminalLine(text = "removed ${target.name}", type = TerminalLineType.INFO))
                }
            }

            "echo" -> {
                onOutput(TerminalLine(text = args.joinToString(" "), type = TerminalLineType.STDOUT))
            }

            "curl" -> {
                val url = args.firstOrNull()
                if (url.isNullOrBlank()) {
                    onOutput(TerminalLine(text = "curl: try 'curl http://127.0.0.1:3000'", type = TerminalLineType.STDERR))
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val req = Request.Builder().url(url).build()
                            httpClient.newCall(req).execute().use { resp ->
                                onOutput(TerminalLine(text = "HTTP/${resp.protocol} ${resp.code} ${resp.message}", type = TerminalLineType.INFO))
                                val body = resp.body?.string().orEmpty()
                                body.split('\n').forEach { line ->
                                    onOutput(TerminalLine(text = line, type = TerminalLineType.STDOUT))
                                }
                            }
                        }.onFailure { err ->
                            emitFailure("curl", err, onOutput)
                        }
                    }
                }
            }

            "git" -> handleGitCommand(args, projectDir, currentSubDir, onOutput, onFilesChanged)

            "node" -> handleNodeCommand(
                args, projectDir, currentSubDir, nodeVersion, npmVersion, lanHostingEnabled,
                scope, onOutput, onClear, onExecutionFinished
            )

            "npm" -> handleNpmCommand(
                args, projectDir, currentSubDir, nodeVersion, npmVersion, lanHostingEnabled,
                scope, onOutput, onClear, onFilesChanged, onExecutionFinished
            )

            else -> {
                onOutput(
                    TerminalLine(
                        text = "$cmd: command not found. Type 'help' for available commands.",
                        type = TerminalLineType.STDERR
                    )
                )
            }
        }
    }

    private suspend fun handleGitCommand(
        args: List<String>,
        projectDir: File,
        currentSubDir: File,
        onOutput: (TerminalLine) -> Unit,
        onFilesChanged: suspend () -> Unit
    ) {
        when (val sub = args.firstOrNull() ?: "status") {
            "status" -> {
                gitClient.gitStatus(projectDir)
                    .onSuccess { msg -> msg.split('\n').forEach { onOutput(TerminalLine(text = it, type = TerminalLineType.STDOUT)) } }
                    .onFailure { err -> emitFailure("fatal", err, onOutput) }
            }
            "log" -> {
                gitClient.gitLog(projectDir)
                    .onSuccess { msg -> msg.split('\n').forEach { onOutput(TerminalLine(text = it, type = TerminalLineType.STDOUT)) } }
                    .onFailure { err -> emitFailure("fatal", err, onOutput) }
            }
            "pull" -> {
                onOutput(TerminalLine(text = "Pulling from origin...", type = TerminalLineType.INFO))
                gitClient.gitPull(projectDir)
                    .onSuccess { msg ->
                        onFilesChanged()
                        onOutput(TerminalLine(text = msg, type = TerminalLineType.SUCCESS))
                    }
                    .onFailure { err -> emitFailure("fatal", err, onOutput) }
            }
            "clone" -> {
                val url = args.getOrNull(1)
                if (url.isNullOrBlank()) {
                    onOutput(TerminalLine(text = "Usage: git clone <https-url> [folder]", type = TerminalLineType.STDERR))
                    return
                }
                val folderName = args.getOrNull(2) ?: url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
                val dest = resolveInside(projectDir, currentSubDir, folderName)
                if (dest == null || dest.exists()) {
                    onOutput(TerminalLine(text = "fatal: destination '$folderName' invalid or already exists", type = TerminalLineType.STDERR))
                    return
                }
                onOutput(TerminalLine(text = "Cloning into '$folderName'...", type = TerminalLineType.INFO))
                gitClient.cloneRepository(url, null, dest) { title, _ ->
                    onOutput(TerminalLine(text = title, type = TerminalLineType.INFO))
                }.onSuccess {
                    onFilesChanged()
                    onOutput(TerminalLine(text = "Cloned $url into $folderName/", type = TerminalLineType.SUCCESS))
                }.onFailure { err ->
                    emitFailure("fatal", err, onOutput)
                }
            }
            else -> onOutput(TerminalLine(text = "git: supported subcommands: status, log, pull, clone", type = TerminalLineType.INFO))
        }
    }

    private fun handleNodeCommand(
        args: List<String>,
        projectDir: File,
        currentSubDir: File,
        nodeVersion: String,
        npmVersion: String,
        lanHostingEnabled: Boolean,
        scope: CoroutineScope,
        onOutput: (TerminalLine) -> Unit,
        onClear: () -> Unit,
        onExecutionFinished: (Boolean) -> Unit
    ) {
        if (args.isEmpty() || args[0] == "-v" || args[0] == "--version") {
            onOutput(TerminalLine(text = nodeVersion, type = TerminalLineType.STDOUT))
            return
        }
        if (args[0] == "-e" || args[0] == "--eval") {
            val code = args.drop(1).joinToString(" ")
            nodeEngine.evaluateSnippet(scope, projectDir, code, nodeVersion, npmVersion, lanHostingEnabled, onOutput, onExecutionFinished)
            return
        }
        val scriptName = args[0]
        val entryFile = resolveInside(projectDir, currentSubDir, scriptName)
        if (entryFile == null || !entryFile.exists()) {
            onOutput(TerminalLine(text = "Error: Cannot find module '$scriptName'", type = TerminalLineType.STDERR))
            return
        }
        nodeEngine.runFile(
            scope = scope,
            projectDir = projectDir,
            entryFile = entryFile,
            nodeVersion = nodeVersion,
            npmVersion = npmVersion,
            lanHostingEnabled = lanHostingEnabled,
            args = args.drop(1),
            onOutput = onOutput,
            onClear = onClear,
            onFinished = onExecutionFinished
        )
    }

    private suspend fun handleNpmCommand(
        args: List<String>,
        projectDir: File,
        currentSubDir: File,
        nodeVersion: String,
        npmVersion: String,
        lanHostingEnabled: Boolean,
        scope: CoroutineScope,
        onOutput: (TerminalLine) -> Unit,
        onClear: () -> Unit,
        onFilesChanged: suspend () -> Unit,
        onExecutionFinished: (Boolean) -> Unit
    ) {
        val sub = args.firstOrNull() ?: "help"
        when (sub) {
            "-v", "--version" -> {
                onOutput(TerminalLine(text = npmVersion, type = TerminalLineType.STDOUT))
            }

            "init" -> {
                val pkgFile = File(projectDir, "package.json")
                if (pkgFile.exists()) {
                    onOutput(TerminalLine(text = "package.json already exists", type = TerminalLineType.INFO))
                } else {
                    val json = JSONObject().apply {
                        put("name", projectDir.name)
                        put("version", "1.0.0")
                        put("main", "index.js")
                        put("scripts", JSONObject().put("start", "node index.js"))
                        put("dependencies", JSONObject())
                    }
                    pkgFile.writeText(json.toString(2) + "\n")
                    onFilesChanged()
                    onOutput(TerminalLine(text = "Wrote ${pkgFile.name}", type = TerminalLineType.SUCCESS))
                }
            }

            "i", "install", "add" -> {
                val pkgSpecs = args.drop(1).filter { !it.startsWith("-") }
                if (pkgSpecs.isEmpty()) {
                    onOutput(TerminalLine(text = "Installing dependencies from package.json...", type = TerminalLineType.INFO))
                    npmClient.installAllFromPackageJson(projectDir) { msg ->
                        onOutput(TerminalLine(text = msg, type = TerminalLineType.INFO))
                    }.onSuccess {
                        onFilesChanged()
                        onOutput(TerminalLine(text = "npm install complete.", type = TerminalLineType.SUCCESS))
                    }.onFailure { err ->
                        emitFailure("npm ERR!", err, onOutput)
                    }
                } else {
                    for (spec in pkgSpecs) {
                        onOutput(TerminalLine(text = "Fetching $spec from registry.npmjs.org...", type = TerminalLineType.INFO))
                        npmClient.installPackage(projectDir, spec) { msg ->
                            onOutput(TerminalLine(text = msg, type = TerminalLineType.INFO))
                        }.onSuccess { installed ->
                            onFilesChanged()
                            onOutput(
                                TerminalLine(
                                    text = "+ ${installed.name}@${installed.installedVersion}",
                                    type = TerminalLineType.SUCCESS
                                )
                            )
                        }.onFailure { err ->
                            emitFailure("npm ERR!", err, onOutput)
                        }
                    }
                }
            }

            "update", "up", "upgrade" -> {
                val target = args.getOrNull(1)
                if (target.isNullOrBlank()) {
                    npmClient.updateAllPackages(projectDir) { msg ->
                        onOutput(TerminalLine(text = msg, type = TerminalLineType.INFO))
                    }.onSuccess { count ->
                        onFilesChanged()
                        onOutput(TerminalLine(text = "Updated $count package(s).", type = TerminalLineType.SUCCESS))
                    }.onFailure { err ->
                        emitFailure("npm ERR!", err, onOutput)
                    }
                } else {
                    npmClient.updatePackage(projectDir, target) { msg ->
                        onOutput(TerminalLine(text = msg, type = TerminalLineType.INFO))
                    }.onSuccess { installed ->
                        onFilesChanged()
                        onOutput(TerminalLine(text = "+ updated ${installed.name}@${installed.installedVersion}", type = TerminalLineType.SUCCESS))
                    }.onFailure { err ->
                        emitFailure("npm ERR!", err, onOutput)
                    }
                }
            }

            "uninstall", "remove", "rm", "un" -> {
                val pkg = args.getOrNull(1)
                if (pkg.isNullOrBlank()) {
                    onOutput(TerminalLine(text = "Usage: npm uninstall <package>", type = TerminalLineType.STDERR))
                } else {
                    npmClient.uninstallPackage(projectDir, pkg)
                    onFilesChanged()
                    onOutput(TerminalLine(text = "- removed $pkg", type = TerminalLineType.SUCCESS))
                }
            }

            "list", "ls" -> {
                val installed = npmClient.listInstalledPackages(projectDir)
                if (installed.isEmpty()) {
                    onOutput(TerminalLine(text = "${projectDir.name}@1.0.0 (no dependencies)", type = TerminalLineType.INFO))
                } else {
                    onOutput(TerminalLine(text = "${projectDir.name}@1.0.0", type = TerminalLineType.STDOUT))
                    installed.forEach { pkg ->
                        val ver = pkg.installedVersion ?: pkg.versionConstraint
                        val status = if (pkg.hasFilesOnDisk) "" else " [missing on disk]"
                        onOutput(TerminalLine(text = "├── ${pkg.name}@$ver$status", type = TerminalLineType.STDOUT))
                    }
                }
            }

            "search" -> {
                val q = args.drop(1).joinToString(" ")
                if (q.isBlank()) {
                    onOutput(TerminalLine(text = "Usage: npm search <query>", type = TerminalLineType.STDERR))
                } else {
                    npmClient.searchPackages(q).onSuccess { list ->
                        list.take(8).forEach { item ->
                            onOutput(
                                TerminalLine(
                                    text = "${item.name}@${item.version} — ${item.description.take(64)}",
                                    type = TerminalLineType.STDOUT
                                )
                            )
                        }
                    }.onFailure { err ->
                        emitFailure("npm ERR!", err, onOutput)
                    }
                }
            }

            "start", "test", "run" -> {
                val scriptKey = if (sub == "run") args.getOrNull(1) ?: "start" else sub
                val pkgFile = File(projectDir, "package.json")
                val scriptCmd = if (pkgFile.exists()) {
                    runCatching {
                        JSONObject(pkgFile.readText()).optJSONObject("scripts")?.optString(scriptKey)
                    }.getOrNull()
                } else null

                if (scriptCmd.isNullOrBlank()) {
                    if (scriptKey == "start" && File(projectDir, "index.js").exists()) {
                        handleNodeCommand(
                            listOf("index.js"), projectDir, currentSubDir, nodeVersion, npmVersion,
                            lanHostingEnabled, scope, onOutput, onClear, onExecutionFinished
                        )
                    } else {
                        onOutput(TerminalLine(text = "npm ERR! Missing script: \"$scriptKey\"", type = TerminalLineType.STDERR))
                    }
                } else {
                    onOutput(TerminalLine(text = "> $scriptCmd", type = TerminalLineType.INFO))
                    val subTokens = tokenizeCommand(scriptCmd)
                    if (subTokens.firstOrNull() == "node") {
                        handleNodeCommand(
                            subTokens.drop(1), projectDir, currentSubDir, nodeVersion, npmVersion,
                            lanHostingEnabled, scope, onOutput, onClear, onExecutionFinished
                        )
                    } else {
                        onOutput(TerminalLine(text = "Unsupported script runner: $scriptCmd", type = TerminalLineType.STDERR))
                    }
                }
            }

            else -> {
                onOutput(TerminalLine(text = "Unknown npm command '$sub'. Try: install, update, uninstall, list, search, start, run", type = TerminalLineType.STDERR))
            }
        }
    }

    private fun emitFailure(
        prefix: String,
        err: Throwable,
        onOutput: (TerminalLine) -> Unit
    ) {
        val typeName = err::class.java.simpleName.ifBlank { "Error" }
        val primary = err.message?.takeIf { it.isNotBlank() } ?: typeName
        primary.replace("\r\n", "\n").split('\n').forEachIndexed { idx, line ->
            val text = if (idx == 0) "$prefix: $line" else "  $line"
            onOutput(TerminalLine(text = text, type = TerminalLineType.STDERR))
        }
        var cause = err.cause
        var depth = 0
        while (cause != null && depth < 5) {
            val causeType = cause::class.java.simpleName.ifBlank { "Error" }
            val causeMsg = cause.message?.takeIf { it.isNotBlank() } ?: "(no details)"
            onOutput(TerminalLine(text = "  caused by $causeType: $causeMsg", type = TerminalLineType.STDERR))
            cause = cause.cause
            depth++
        }
    }

    private fun resolveInside(root: File, current: File, raw: String): File? {
        val candidate = if (raw.startsWith("/")) File(root, raw.trimStart('/')) else File(current, raw)
        val rootCanon = root.canonicalPath
        val candCanon = candidate.canonicalPath
        return if (candCanon == rootCanon || candCanon.startsWith(rootCanon + File.separator)) candidate else null
    }

    private fun tokenizeCommand(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar: Char? = null
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                quoteChar != null -> {
                    if (ch == quoteChar) quoteChar = null else current.append(ch)
                }
                ch == '"' || ch == '\'' -> quoteChar = ch
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
            i++
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
