package com.example.data.npm

import com.example.data.model.InstalledPackage
import com.example.data.model.NpmPackageSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class NpmRegistryClient(private val cacheDir: File) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun searchPackages(query: String): Result<List<NpmPackageSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim().ifBlank { "utility" }
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://registry.npmjs.org/-/v1/search?text=$encoded&size=20"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Registry returned HTTP ${response.code}")
                    }
                    val body = response.body?.string() ?: "{}"
                    val root = JSONObject(body)
                    val objects = root.optJSONArray("objects") ?: return@runCatching emptyList()
                    buildList {
                        for (i in 0 until objects.length()) {
                            val obj = objects.optJSONObject(i) ?: continue
                            val pkg = obj.optJSONObject("package") ?: continue
                            val publisher = pkg.optJSONObject("publisher")
                            val kwArray = pkg.optJSONArray("keywords")
                            val keywords = buildList {
                                if (kwArray != null) {
                                    for (k in 0 until minOf(kwArray.length(), 4)) {
                                        add(kwArray.optString(k))
                                    }
                                }
                            }
                            add(
                                NpmPackageSearchResult(
                                    name = pkg.optString("name"),
                                    version = pkg.optString("version", "0.0.0"),
                                    description = pkg.optString("description", "No description provided"),
                                    author = publisher?.optString("username")?.ifBlank { "npm" } ?: "npm",
                                    keywords = keywords
                                )
                            )
                        }
                    }
                }
            }
        }

    suspend fun installPackage(
        projectDir: File,
        packageSpec: String,
        onLog: (String) -> Unit
    ): Result<InstalledPackage> = withContext(Dispatchers.IO) {
        runCatching {
            val (pkgName, requestedRange) = parsePackageSpec(packageSpec)
            val visited = mutableSetOf<String>()
            val resolvedTop = installRecursive(
                nodeModulesDir = File(projectDir, "node_modules").apply { mkdirs() },
                pkgName = pkgName,
                requestedRange = requestedRange,
                depth = 0,
                maxDepth = 3,
                visited = visited,
                onLog = onLog
            )

            updatePackageJson(projectDir, pkgName, "^${resolvedTop.version}")
            updatePackageLock(projectDir, pkgName, resolvedTop.version, resolvedTop.tarballUrl)

            onLog("added ${visited.size} package(s) to node_modules")
            InstalledPackage(
                name = pkgName,
                versionConstraint = "^${resolvedTop.version}",
                installedVersion = resolvedTop.version,
                description = resolvedTop.description,
                hasFilesOnDisk = true
            )
        }
    }

    suspend fun installAllFromPackageJson(
        projectDir: File,
        onLog: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val pkgFile = File(projectDir, "package.json")
            if (!pkgFile.exists()) error("package.json not found in workspace")
            val json = JSONObject(pkgFile.readText())
            val deps = json.optJSONObject("dependencies") ?: JSONObject()
            val names = deps.keys().asSequence().toList()
            if (names.isEmpty()) {
                onLog("up to date — no dependencies declared in package.json")
                return@runCatching 0
            }
            val visited = mutableSetOf<String>()
            val nmDir = File(projectDir, "node_modules").apply { mkdirs() }
            for (name in names) {
                val range = deps.optString(name, "latest")
                installRecursive(nmDir, name, range, 0, 3, visited, onLog)
            }
            onLog("installed ${visited.size} package(s)")
            visited.size
        }
    }

    suspend fun uninstallPackage(projectDir: File, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pkgDir = File(projectDir, "node_modules/$packageName")
                if (pkgDir.exists()) pkgDir.deleteRecursively()

                val pkgFile = File(projectDir, "package.json")
                if (pkgFile.exists()) {
                    val json = JSONObject(pkgFile.readText())
                    val deps = json.optJSONObject("dependencies")
                    deps?.remove(packageName)
                    pkgFile.writeText(json.toString(2) + "\n")
                }

                val lockFile = File(projectDir, "package-lock.json")
                if (lockFile.exists()) {
                    val lock = JSONObject(lockFile.readText())
                    lock.optJSONObject("packages")?.remove("node_modules/$packageName")
                    lockFile.writeText(lock.toString(2) + "\n")
                }
            }
        }

    suspend fun updatePackage(
        projectDir: File,
        packageName: String,
        onLog: (String) -> Unit
    ): Result<InstalledPackage> = installPackage(projectDir, "$packageName@latest", onLog)

    suspend fun updateAllPackages(
        projectDir: File,
        onLog: (String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val pkgFile = File(projectDir, "package.json")
            if (!pkgFile.exists()) error("package.json not found")
            val json = JSONObject(pkgFile.readText())
            val deps = json.optJSONObject("dependencies") ?: JSONObject()
            val names = deps.keys().asSequence().toList()
            if (names.isEmpty()) {
                onLog("No dependencies to update")
                return@runCatching 0
            }
            var updated = 0
            for (name in names) {
                onLog("Checking latest for $name...")
                installPackage(projectDir, "$name@latest", onLog).getOrThrow()
                updated++
            }
            updated
        }
    }

    suspend fun listInstalledPackages(projectDir: File): List<InstalledPackage> =
        withContext(Dispatchers.IO) {
            val pkgFile = File(projectDir, "package.json")
            if (!pkgFile.exists()) return@withContext emptyList()
            val json = runCatching { JSONObject(pkgFile.readText()) }.getOrNull()
                ?: return@withContext emptyList()
            val deps = json.optJSONObject("dependencies") ?: return@withContext emptyList()

            deps.keys().asSequence().sorted().map { name ->
                val constraint = deps.optString(name, "*")
                val modPkgFile = File(projectDir, "node_modules/$name/package.json")
                val modJson = if (modPkgFile.exists()) {
                    runCatching { JSONObject(modPkgFile.readText()) }.getOrNull()
                } else null
                InstalledPackage(
                    name = name,
                    versionConstraint = constraint,
                    installedVersion = modJson?.optString("version"),
                    description = modJson?.optString("description")?.ifBlank { "Installed npm package" }
                        ?: "Declared in package.json",
                    hasFilesOnDisk = File(projectDir, "node_modules/$name").exists()
                )
            }.toList()
        }

    private data class ResolvedPackage(
        val name: String,
        val version: String,
        val description: String,
        val tarballUrl: String,
        val dependencies: Map<String, String>
    )

    private fun installRecursive(
        nodeModulesDir: File,
        pkgName: String,
        requestedRange: String?,
        depth: Int,
        maxDepth: Int,
        visited: MutableSet<String>,
        onLog: (String) -> Unit
    ): ResolvedPackage {
        val resolved = fetchManifest(pkgName, requestedRange)
        val key = "${resolved.name}@${resolved.version}"
        if (visited.contains(key)) return resolved
        visited.add(key)

        onLog("GET ${resolved.name}@${resolved.version} -> unpacking...")
        val tarballFile = downloadTarball(resolved.name, resolved.version, resolved.tarballUrl)
        val targetDir = File(nodeModulesDir, resolved.name)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        extractTarGz(tarballFile, targetDir)

        if (depth < maxDepth) {
            for ((depName, depRange) in resolved.dependencies) {
                if (!visited.any { it.startsWith("$depName@") }) {
                    runCatching {
                        installRecursive(
                            nodeModulesDir = nodeModulesDir,
                            pkgName = depName,
                            requestedRange = depRange,
                            depth = depth + 1,
                            maxDepth = maxDepth,
                            visited = visited,
                            onLog = onLog
                        )
                    }.onFailure { err ->
                        onLog("warn sub-dependency $depName skipped: ${err.message}")
                    }
                }
            }
        }
        return resolved
    }

    private fun fetchManifest(pkgName: String, requestedRange: String?): ResolvedPackage {
        val encodedName = pkgName.replace("/", "%2F")
        val url = "https://registry.npmjs.org/$encodedName"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("npm package '$pkgName' not found (HTTP ${response.code})")
            }
            val body = response.body?.string() ?: error("Empty registry response for $pkgName")
            val root = JSONObject(body)
            val versions = root.optJSONObject("versions")
                ?: error("No versions published for $pkgName")
            val distTags = root.optJSONObject("dist-tags")
            val latestTag = distTags?.optString("latest")

            val allVersions = versions.keys().asSequence().toList()
            val selectedVersion = selectMatchingVersion(allVersions, latestTag, requestedRange)
                ?: error("No matching version for $pkgName@$requestedRange")

            val verObj = versions.getJSONObject(selectedVersion)
            val dist = verObj.getJSONObject("dist")
            val tarballUrl = dist.getString("tarball")
            val description = verObj.optString("description", root.optString("description", ""))

            val depsObj = verObj.optJSONObject("dependencies")
            val deps = buildMap {
                if (depsObj != null) {
                    for (depKey in depsObj.keys()) {
                        put(depKey, depsObj.optString(depKey, "*"))
                    }
                }
            }

            return ResolvedPackage(
                name = pkgName,
                version = selectedVersion,
                description = description,
                tarballUrl = tarballUrl,
                dependencies = deps
            )
        }
    }

    private fun downloadTarball(name: String, version: String, url: String): File {
        cacheDir.mkdirs()
        val safeFileName = "${name.replace("/", "__")}__$version.tgz"
        val cached = File(cacheDir, safeFileName)
        if (cached.exists() && cached.length() > 0L) {
            return cached
        }

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to download tarball ($url): HTTP ${response.code}")
            }
            val body = response.body ?: error("Empty tarball stream")
            val tmp = File(cacheDir, "$safeFileName.part")
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            tmp.renameTo(cached)
        }
        return cached
    }

    private fun extractTarGz(tgzFile: File, targetDir: File) {
        GZIPInputStream(BufferedInputStream(FileInputStream(tgzFile))).use { gzis ->
            val header = ByteArray(512)
            var nextLongName: String? = null
            val targetCanonical = targetDir.canonicalPath

            while (true) {
                val read = readFully(gzis, header)
                if (read < 512 || header.all { it == 0.toByte() }) break

                val rawName = readCString(header, 0, 100)
                val prefix = readCString(header, 345, 155)
                val sizeStr = readCString(header, 124, 12).trim()
                val size = sizeStr.toLongOrNull(8) ?: 0L
                val typeFlag = header[156].toInt().toChar()

                val combinedName = nextLongName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
                nextLongName = null

                if (typeFlag == 'L') {
                    val longBytes = readExactBytes(gzis, size)
                    skipPadding(gzis, size)
                    nextLongName = String(longBytes).trimEnd('\u0000', '\n')
                    continue
                }

                if (typeFlag == 'x' || typeFlag == 'g') {
                    skipFully(gzis, size)
                    skipPadding(gzis, size)
                    continue
                }

                val stripped = stripFirstSegment(combinedName)
                if (stripped.isNotEmpty()) {
                    val outFile = File(targetDir, stripped)
                    val outCanonical = outFile.canonicalPath
                    if (outCanonical.startsWith(targetCanonical + File.separator)) {
                        if (typeFlag == '5') {
                            outFile.mkdirs()
                        } else if (typeFlag == '0' || typeFlag == '\u0000') {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                copyExact(gzis, fos, size)
                            }
                            skipPadding(gzis, size)
                            continue
                        }
                    }
                }

                skipFully(gzis, size)
                skipPadding(gzis, size)
            }
        }
    }

    private fun stripFirstSegment(path: String): String {
        val clean = path.trimStart('.').trimStart('/')
        val slash = clean.indexOf('/')
        return if (slash >= 0) clean.substring(slash + 1) else ""
    }

    private fun readCString(buf: ByteArray, offset: Int, len: Int): String {
        var end = offset
        val limit = offset + len
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun readExactBytes(input: InputStream, size: Long): ByteArray {
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        return bytes
    }

    private fun copyExact(input: InputStream, output: FileOutputStream, size: Long) {
        val buffer = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n < 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun skipFully(input: InputStream, size: Long) {
        val buffer = ByteArray(4096)
        var remaining = size
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n < 0) break
            remaining -= n
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val rem = (512 - (size % 512)) % 512
        if (rem > 0) skipFully(input, rem)
    }

    private fun parsePackageSpec(spec: String): Pair<String, String?> {
        val clean = spec.trim()
        if (clean.startsWith("@")) {
            val secondAt = clean.indexOf('@', startIndex = 1)
            return if (secondAt > 0) {
                clean.substring(0, secondAt) to clean.substring(secondAt + 1)
            } else {
                clean to null
            }
        }
        val at = clean.indexOf('@')
        return if (at > 0) {
            clean.substring(0, at) to clean.substring(at + 1)
        } else {
            clean to null
        }
    }

    private fun selectMatchingVersion(
        versions: List<String>,
        latestTag: String?,
        range: String?
    ): String? {
        if (range.isNullOrBlank() || range == "latest" || range == "*") {
            return latestTag ?: versions.lastOrNull()
        }
        val clean = range.trim()
        if (versions.contains(clean)) return clean

        val target = clean.trimStart('^', '~', '>', '=', '<', 'v', ' ')
        val targetParts = parseSemver(target) ?: return latestTag ?: versions.lastOrNull()

        val stableVersions = versions.filter { !it.contains('-') }
        val candidates = stableVersions.mapNotNull { v ->
            parseSemver(v)?.let { v to it }
        }.sortedWith(compareBy({ it.second.first }, { it.second.second }, { it.second.third }))

        val matched = when {
            clean.startsWith("^") -> candidates.filter { (_, p) ->
                if (targetParts.first > 0) {
                    p.first == targetParts.first && compareSemver(p, targetParts) >= 0
                } else {
                    p.first == 0 && p.second == targetParts.second && compareSemver(p, targetParts) >= 0
                }
            }
            clean.startsWith("~") -> candidates.filter { (_, p) ->
                p.first == targetParts.first && p.second == targetParts.second && p.third >= targetParts.third
            }
            else -> candidates.filter { (_, p) -> compareSemver(p, targetParts) >= 0 }
        }
        return matched.lastOrNull()?.first ?: latestTag ?: versions.lastOrNull()
    }

    private fun parseSemver(v: String): Triple<Int, Int, Int>? {
        val core = v.substringBefore('-').split('.')
        if (core.size < 2) return null
        val major = core[0].toIntOrNull() ?: return null
        val minor = core[1].toIntOrNull() ?: 0
        val patch = core.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    private fun compareSemver(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }

    private fun updatePackageJson(projectDir: File, pkgName: String, versionConstraint: String) {
        val pkgFile = File(projectDir, "package.json")
        val json = if (pkgFile.exists()) {
            runCatching { JSONObject(pkgFile.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject().apply {
                put("name", projectDir.name)
                put("version", "1.0.0")
                put("main", "index.js")
            }
        }
        val deps = json.optJSONObject("dependencies") ?: JSONObject()
        deps.put(pkgName, versionConstraint)
        json.put("dependencies", deps)
        pkgFile.writeText(json.toString(2) + "\n")
    }

    private fun updatePackageLock(
        projectDir: File,
        pkgName: String,
        version: String,
        tarballUrl: String
    ) {
        val lockFile = File(projectDir, "package-lock.json")
        val lock = if (lockFile.exists()) {
            runCatching { JSONObject(lockFile.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject().apply {
                put("name", projectDir.name)
                put("lockfileVersion", 3)
            }
        }
        val packages = lock.optJSONObject("packages") ?: JSONObject()
        packages.put(
            "node_modules/$pkgName",
            JSONObject().apply {
                put("version", version)
                put("resolved", tarballUrl)
            }
        )
        lock.put("packages", packages)
        lockFile.writeText(lock.toString(2) + "\n")
    }
}
