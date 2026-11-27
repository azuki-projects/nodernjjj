package com.example.data.fs

import android.content.Context
import com.example.data.model.FileNode
import com.example.data.model.ProjectInfo
import com.example.data.model.ProjectTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    val runtimeRootDir: File
        get() = File(context.filesDir, "nodejs").apply { mkdirs() }

    val npmCacheDir: File
        get() = File(context.filesDir, "npm-cache").apply { mkdirs() }

    val workspacesDir: File
        get() = File(context.filesDir, "workspaces").apply { mkdirs() }

    private fun metaFile(projectDir: File): File = File(projectDir, ".nodestudio.json")

    suspend fun seedStarterProjectsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = workspacesDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        if (existing.isEmpty()) {
            createProject(
                name = "hello-http-api",
                description = "Live loopback HTTP server with JSON routes and OS telemetry",
                template = ProjectTemplate.HTTP_API
            )
            createProject(
                name = "crypto-fs-lab",
                description = "Modular CommonJS utilities, SHA-256 hashing & real file I/O",
                template = ProjectTemplate.CRYPTO_FS
            )
        }
    }

    suspend fun listProjects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        val dirs = workspacesDir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()
        dirs.mapNotNull { dir ->
            readProjectInfo(dir)
        }.sortedByDescending { it.lastModified }
    }

    suspend fun getProject(projectId: String): ProjectInfo? = withContext(Dispatchers.IO) {
        val dir = File(workspacesDir, projectId)
        if (!dir.exists() || !dir.isDirectory) return@withContext null
        readProjectInfo(dir)
    }

    private fun readProjectInfo(dir: File): ProjectInfo? {
        val meta = runCatching {
            val file = metaFile(dir)
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrDefault(JSONObject())

        val pkgJson = runCatching {
            val file = File(dir, "package.json")
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrDefault(JSONObject())

        val name = meta.optString("name").ifBlank {
            pkgJson.optString("name").ifBlank { dir.name }
        }
        val description = meta.optString("description").ifBlank {
            pkgJson.optString("description").ifBlank { "Node.js workspace" }
        }
        val templateId = meta.optString("template", ProjectTemplate.BLANK.id)
        val entryFile = meta.optString("entryFile").ifBlank {
            pkgJson.optString("main").ifBlank { "index.js" }
        }

        val deps = pkgJson.optJSONObject("dependencies")?.length() ?: 0
        val fileCount = countProjectFiles(dir)
        val lastMod = latestTimestamp(dir)
        val gitRemote = meta.optString("gitRemoteUrl").ifBlank { null }

        return ProjectInfo(
            id = dir.name,
            name = name,
            description = description,
            entryFile = entryFile,
            template = ProjectTemplate.fromId(templateId),
            lastModified = lastMod,
            fileCount = fileCount,
            dependencyCount = deps,
            directoryPath = dir.absolutePath,
            gitRemoteUrl = gitRemote
        )
    }

    private fun countProjectFiles(dir: File): Int {
        var count = 0
        dir.walkTopDown()
            .onEnter { it.name != "node_modules" && it.name != ".git" }
            .forEach { if (it.isFile && it.name != ".nodestudio.json") count++ }
        return count
    }

    private fun latestTimestamp(dir: File): Long {
        var max = dir.lastModified()
        dir.listFiles()?.forEach { child ->
            if (child.name != "node_modules" && child.name != ".git" && child.lastModified() > max) {
                max = child.lastModified()
            }
        }
        return max
    }

    suspend fun createProject(
        name: String,
        description: String,
        template: ProjectTemplate
    ): ProjectInfo = withContext(Dispatchers.IO) {
        val slug = name.lowercase()
            .replace(Regex("[^a-z0-9-_]"), "-")
            .trim('-')
            .ifBlank { "node-app" }
        val id = "${slug}-${UUID.randomUUID().toString().take(6)}"
        val projectDir = File(workspacesDir, id).apply { mkdirs() }

        val entry = template.defaultEntry
        val meta = JSONObject().apply {
            put("name", name.trim().ifBlank { slug })
            put("description", description.trim().ifBlank { template.subtitle })
            put("template", template.id)
            put("entryFile", entry)
            put("createdAt", System.currentTimeMillis())
        }
        metaFile(projectDir).writeText(meta.toString(2))

        writeTemplateFiles(projectDir, slug, description, template)
        readProjectInfo(projectDir)!!
    }

    suspend fun createProjectFromGit(
        repoUrl: String,
        customName: String?,
        branch: String?,
        gitClient: com.example.data.git.GitClient,
        onProgress: (String, Float) -> Unit
    ): Result<ProjectInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val inferredName = repoUrl.trim()
                .trimEnd('/')
                .substringAfterLast('/')
                .removeSuffix(".git")
                .ifBlank { "git-repo" }
            val displayName = customName?.trim()?.ifBlank { null } ?: inferredName
            val slug = displayName.lowercase()
                .replace(Regex("[^a-z0-9-_]"), "-")
                .trim('-')
                .ifBlank { "git-repo" }
            val id = "${slug}-${UUID.randomUUID().toString().take(6)}"
            val projectDir = File(workspacesDir, id)

            gitClient.cloneRepository(
                repoUrl = repoUrl,
                branch = branch,
                targetDir = projectDir,
                onProgress = onProgress
            ).getOrThrow()

            val pkgJson = runCatching {
                val f = File(projectDir, "package.json")
                if (f.exists()) JSONObject(f.readText()) else JSONObject()
            }.getOrDefault(JSONObject())

            val detectedEntry = pkgJson.optString("main").ifBlank {
                listOf("server.js", "app.js", "index.js", "main.js", "src/index.js")
                    .firstOrNull { File(projectDir, it).exists() } ?: "index.js"
            }
            val detectedDesc = pkgJson.optString("description").ifBlank {
                "Cloned from ${repoUrl.trim()}"
            }

            val meta = JSONObject().apply {
                put("name", displayName)
                put("description", detectedDesc)
                put("template", ProjectTemplate.GIT_CLONE.id)
                put("entryFile", detectedEntry)
                put("gitRemoteUrl", repoUrl.trim())
                put("createdAt", System.currentTimeMillis())
            }
            metaFile(projectDir).writeText(meta.toString(2))
            readProjectInfo(projectDir)!!
        }
    }

    private fun writeTemplateFiles(
        dir: File,
        pkgName: String,
        description: String,
        template: ProjectTemplate
    ) {
        val pkgJson = JSONObject().apply {
            put("name", pkgName)
            put("version", "1.0.0")
            put("description", description.ifBlank { template.subtitle })
            put("main", template.defaultEntry)
            put("scripts", JSONObject().apply {
                put("start", "node ${template.defaultEntry}")
                put("dev", "node ${template.defaultEntry}")
            })
            put("dependencies", JSONObject())
        }
        File(dir, "package.json").writeText(pkgJson.toString(2) + "\n")

        when (template) {
            ProjectTemplate.HTTP_API -> {
                File(dir, "routes.js").writeText(
                    """
                    const os = require('os');

                    function buildStatusPayload(requestCount, url) {
                      return {
                        ok: true,
                        service: '$pkgName',
                        route: url,
                        requestsServed: requestCount,
                        nodeVersion: process.version,
                        platform: process.platform,
                        arch: process.arch,
                        uptimeSec: Math.round(process.uptime() * 100) / 100,
                        timestamp: new Date().toISOString()
                      };
                    }

                    module.exports = { buildStatusPayload };
                    """.trimIndent() + "\n"
                )
                File(dir, "server.js").writeText(
                    """
                    const http = require('http');
                    const { buildStatusPayload } = require('./routes');

                    const PORT = Number(process.env.PORT) || 3000;
                    let requestCount = 0;

                    const server = http.createServer((req, res) => {
                      requestCount += 1;
                      console.log(`[HTTP] ${'$'}{req.method} ${'$'}{req.url} (#${'$'}{requestCount})`);

                      if (req.url === '/health') {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ status: 'healthy', pid: process.pid }));
                        return;
                      }

                      const payload = buildStatusPayload(requestCount, req.url);
                      res.writeHead(200, { 'Content-Type': 'application/json' });
                      res.end(JSON.stringify(payload, null, 2));
                    });

                    server.listen(PORT, () => {
                      console.log(`Server listening on http://127.0.0.1:${'$'}{PORT}`);
                      console.log('Tap "Test GET /" below or run: curl http://127.0.0.1:' + PORT);
                    });
                    """.trimIndent() + "\n"
                )
            }

            ProjectTemplate.CRYPTO_FS -> {
                val utilsDir = File(dir, "utils").apply { mkdirs() }
                File(utilsDir, "formatter.js").writeText(
                    """
                    function banner(title) {
                      const line = '='.repeat(title.length + 4);
                      return `${'$'}{line}\n  ${'$'}{title}\n${'$'}{line}`;
                    }

                    module.exports = { banner };
                    """.trimIndent() + "\n"
                )
                File(dir, "index.js").writeText(
                    """
                    const fs = require('fs');
                    const path = require('path');
                    const crypto = require('crypto');
                    const { banner } = require('./utils/formatter');

                    async function main() {
                      console.log(banner('NodeStudio Crypto & FS Lab'));

                      const reportPath = path.join(process.cwd(), 'audit.json');
                      const token = crypto.randomUUID();
                      const digest = crypto
                        .createHash('sha256')
                        .update(`nodestudio:${'$'}{token}`)
                        .digest('hex');

                      const payload = {
                        generatedAt: new Date().toISOString(),
                        token,
                        sha256: digest,
                        node: process.version
                      };

                      fs.writeFileSync(reportPath, JSON.stringify(payload, null, 2));
                      console.log('Wrote file:', reportPath);

                      const saved = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
                      console.log('Verified SHA-256:', saved.sha256.slice(0, 24) + '...');
                    }

                    main().catch(err => console.error('Fatal:', err));
                    """.trimIndent() + "\n"
                )
            }

            ProjectTemplate.CLI_TOOL -> {
                File(dir, "cli.js").writeText(
                    """
                    const os = require('os');

                    const args = process.argv.slice(2);
                    console.log('NodeStudio CLI Runner', process.version);
                    console.log('Host:', os.hostname(), '| CPUs:', os.cpus().length);
                    console.log('Args:', args.length ? args.join(', ') : '(none)');

                    let tick = 0;
                    const timer = setInterval(() => {
                      tick += 1;
                      console.log(`[task] step ${'$'}{tick}/3 complete`);
                      if (tick >= 3) {
                        clearInterval(timer);
                        console.log('All CLI tasks finished cleanly.');
                      }
                    }, 350);
                    """.trimIndent() + "\n"
                )
            }

            ProjectTemplate.BLANK, ProjectTemplate.GIT_CLONE -> {
                File(dir, "index.js").writeText(
                    """
                    console.log('Hello from NodeStudio!', process.version);

                    const nums = [1, 2, 3, 4, 5];
                    const squared = nums.map(n => n * n);
                    console.log('Squared:', JSON.stringify(squared));
                    """.trimIndent() + "\n"
                )
            }
        }
    }

    suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return@withContext
        val dir = File(workspacesDir, projectId)
        if (!dir.exists()) return@withContext
        val meta = runCatching { JSONObject(metaFile(dir).readText()) }.getOrDefault(JSONObject())
        meta.put("name", cleanName)
        meta.put("updatedAt", System.currentTimeMillis())
        metaFile(dir).writeText(meta.toString(2))
    }

    suspend fun updateProjectEntryFile(projectId: String, entryRelativePath: String) = withContext(Dispatchers.IO) {
        val dir = File(workspacesDir, projectId)
        if (!dir.exists()) return@withContext
        val meta = runCatching { JSONObject(metaFile(dir).readText()) }.getOrDefault(JSONObject())
        meta.put("entryFile", entryRelativePath)
        metaFile(dir).writeText(meta.toString(2))

        val pkgFile = File(dir, "package.json")
        if (pkgFile.exists()) {
            val pkg = runCatching { JSONObject(pkgFile.readText()) }.getOrDefault(JSONObject())
            pkg.put("main", entryRelativePath)
            val scripts = pkg.optJSONObject("scripts") ?: JSONObject()
            scripts.put("start", "node $entryRelativePath")
            pkg.put("scripts", scripts)
            pkgFile.writeText(pkg.toString(2) + "\n")
        }
    }

    suspend fun duplicateProject(projectId: String): ProjectInfo? = withContext(Dispatchers.IO) {
        val source = File(workspacesDir, projectId)
        if (!source.exists()) return@withContext null
        val sourceInfo = readProjectInfo(source) ?: return@withContext null
        val newId = "${projectId.substringBeforeLast('-')}-${UUID.randomUUID().toString().take(6)}"
        val target = File(workspacesDir, newId)
        source.copyRecursively(target, overwrite = true)
        val meta = runCatching { JSONObject(metaFile(target).readText()) }.getOrDefault(JSONObject())
        meta.put("name", "${sourceInfo.name} Copy")
        metaFile(target).writeText(meta.toString(2))
        readProjectInfo(target)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val dir = File(workspacesDir, projectId)
        if (dir.exists()) dir.deleteRecursively()
    }

    suspend fun buildFileTree(
        projectDir: File,
        expandedPaths: Set<String>
    ): List<FileNode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileNode>()
        fun walk(currentDir: File, depth: Int) {
            val children = currentDir.listFiles()
                ?.filter { it.name != ".nodestudio.json" && it.name != ".git" }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: return
            for (child in children) {
                val rel = child.relativeTo(projectDir).path.replace(File.separatorChar, '/')
                val isExpanded = child.isDirectory && expandedPaths.contains(rel)
                result.add(
                    FileNode(
                        name = child.name,
                        relativePath = rel,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isDirectory) 0L else child.length(),
                        lastModified = child.lastModified(),
                        depth = depth,
                        isExpanded = isExpanded
                    )
                )
                if (isExpanded) {
                    walk(child, depth + 1)
                }
            }
        }
        if (projectDir.exists()) {
            walk(projectDir, 0)
        }
        result
    }

    suspend fun readFileContent(projectDir: File, relativePath: String): String = withContext(Dispatchers.IO) {
        val target = resolveSafe(projectDir, relativePath) ?: return@withContext ""
        if (!target.exists() || target.isDirectory) return@withContext ""
        if (target.length() > 512_000L) {
            return@withContext "// File exceeds 500 KB preview limit (${target.length()} bytes)\n"
        }
        target.readText()
    }

    suspend fun saveFileContent(projectDir: File, relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val target = resolveSafe(projectDir, relativePath) ?: return@withContext
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    suspend fun createFile(projectDir: File, relativePath: String, initialContent: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolveSafe(projectDir, relativePath) ?: return@withContext false
            if (target.exists()) return@withContext false
            target.parentFile?.mkdirs()
            target.writeText(initialContent)
            true
        }

    suspend fun createFolder(projectDir: File, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolveSafe(projectDir, relativePath) ?: return@withContext false
            if (target.exists()) return@withContext false
            target.mkdirs()
        }

    suspend fun renameNode(projectDir: File, oldRelativePath: String, newName: String): String? =
        withContext(Dispatchers.IO) {
            val cleanName = newName.trim().replace("/", "").replace("\\", "")
            if (cleanName.isBlank() || cleanName == "." || cleanName == "..") return@withContext null
            val source = resolveSafe(projectDir, oldRelativePath) ?: return@withContext null
            val parent = source.parentFile ?: projectDir
            val dest = File(parent, cleanName)
            if (!source.exists()) return@withContext null
            if (source.canonicalPath == dest.canonicalPath && source.name == cleanName) {
                return@withContext oldRelativePath
            }
            if (!dest.canonicalPath.startsWith(projectDir.canonicalPath)) return@withContext null
            if (dest.exists()) {
                if (source.name.equals(cleanName, ignoreCase = true) &&
                    source.canonicalPath.equals(dest.canonicalPath, ignoreCase = true)
                ) {
                    val temp = File(parent, ".__tmp_rename_${UUID.randomUUID()}")
                    if (source.renameTo(temp) && temp.renameTo(dest)) {
                        return@withContext dest.relativeTo(projectDir).path.replace(File.separatorChar, '/')
                    }
                }
                return@withContext null
            }
            if (source.renameTo(dest)) {
                dest.relativeTo(projectDir).path.replace(File.separatorChar, '/')
            } else null
        }

    suspend fun deleteNode(projectDir: File, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolveSafe(projectDir, relativePath) ?: return@withContext false
            if (!target.exists()) return@withContext false
            target.deleteRecursively()
        }

    fun resolveSafe(rootDir: File, relativePath: String): File? {
        val clean = relativePath.trimStart('/')
        val candidate = File(rootDir, clean)
        val rootCanonical = rootDir.canonicalPath
        val targetCanonical = candidate.canonicalPath
        return if (targetCanonical == rootCanonical || targetCanonical.startsWith(rootCanonical + File.separator)) {
            candidate
        } else {
            null
        }
    }
}
