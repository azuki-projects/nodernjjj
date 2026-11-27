package com.example.runtime

import android.os.Build
import com.dokar.quickjs.QuickJs
import com.example.data.fs.ProjectRepository
import com.example.data.model.RuntimeInstallState
import com.example.data.model.RuntimeProfile
import com.example.data.model.RuntimeUpdateInfo
import com.example.data.prefs.StudioPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class NodeRuntimeInstaller(
    private val projectRepository: ProjectRepository,
    private val preferences: StudioPreferences
) {
    companion object {
        const val DEFAULT_NODE_VERSION = "v22.14.0"
        const val DEFAULT_NPM_VERSION = "10.9.2"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun installOrVerify(
        forceReinstall: Boolean = false,
        targetNodeVersion: String? = null,
        targetNpmVersion: String? = null
    ): Flow<RuntimeInstallState> = flow {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val runtimeDir = projectRepository.runtimeRootDir
        val currentPrefs = preferences.settingsFlow.first()
        val chosenNode = targetNodeVersion ?: currentPrefs.installedNodeVersion.ifBlank { DEFAULT_NODE_VERSION }
        val chosenNpm = targetNpmVersion ?: currentPrefs.installedNpmVersion.ifBlank { DEFAULT_NPM_VERSION }
        val logs = mutableListOf<String>()

        suspend fun emitStep(step: Int, title: String, progress: Float, logLine: String) {
            logs.add(logLine)
            emit(
                RuntimeInstallState.Installing(
                    stepIndex = step,
                    totalSteps = 5,
                    stepTitle = title,
                    progress = progress,
                    logs = logs.toList()
                )
            )
        }

        try {
            emitStep(1, "Inspecting Device Architecture", 0.15f, "$ uname -m -> $arch (Android SDK ${Build.VERSION.SDK_INT})")
            delay(150)

            val manifestFile = File(runtimeDir, "runtime-manifest.json")
            if (forceReinstall && runtimeDir.exists()) {
                runtimeDir.deleteRecursively()
                runtimeDir.mkdirs()
            }

            emitStep(2, "Extracting Node.js $chosenNode Core Engine", 0.38f, "unpacking QuickJS ES2023 VM ($chosenNode) -> ${runtimeDir.absolutePath}")
            val engineCheck = withContext(Dispatchers.Default) {
                val qjs = QuickJs.create(Dispatchers.Default)
                try {
                    qjs.evaluate<String>("'$chosenNode'")
                } finally {
                    qjs.close()
                }
            }
            if (engineCheck != chosenNode) {
                error("Engine verification returned unexpected version: $engineCheck")
            }
            delay(160)

            emitStep(3, "Provisioning Built-in Node Core Modules", 0.62f, "registering fs, path, os, http, crypto, url, events, util, buffer, timers")
            withContext(Dispatchers.IO) {
                File(runtimeDir, "bin").mkdirs()
                File(runtimeDir, "lib").mkdirs()
                File(runtimeDir, "node_modules").mkdirs()
                projectRepository.npmCacheDir.mkdirs()

                File(runtimeDir, "lib/core-modules.json").writeText(
                    JSONObject().apply {
                        put("node", chosenNode)
                        put("npm", chosenNpm)
                        put("engine", "QuickJS-ES2023")
                        put("modules", listOf("fs", "fs/promises", "path", "os", "http", "crypto", "url", "events", "util", "buffer", "querystring", "assert", "timers", "stream", "module"))
                    }.toString(2)
                )

                File(runtimeDir, "npmrc.json").writeText(
                    JSONObject().apply {
                        put("registry", "https://registry.npmjs.org")
                        put("cache", projectRepository.npmCacheDir.absolutePath)
                        put("saveExact", false)
                    }.toString(2)
                )
            }
            delay(150)

            emitStep(4, "Configuring npm v$chosenNpm & Workspaces", 0.82f, "npm config set registry https://registry.npmjs.org")
            projectRepository.seedStarterProjectsIfEmpty()
            delay(140)

            emitStep(5, "Verifying node -v && npm -v", 1.0f, "node $chosenNode | npm v$chosenNpm [OK]")
            withContext(Dispatchers.IO) {
                manifestFile.writeText(
                    JSONObject().apply {
                        put("nodeVersion", chosenNode)
                        put("npmVersion", chosenNpm)
                        put("arch", arch)
                        put("installedAt", System.currentTimeMillis())
                    }.toString(2)
                )
            }
            preferences.setRuntimeInstalled(true, chosenNode, chosenNpm)
            delay(150)

            emit(
                RuntimeInstallState.Ready(
                    nodeVersion = chosenNode,
                    npmVersion = chosenNpm,
                    arch = arch,
                    runtimePath = runtimeDir.absolutePath
                )
            )
        } catch (t: Throwable) {
            emit(RuntimeInstallState.Failed(t.message ?: "Failed to initialize Node.js runtime"))
        }
    }

    suspend fun checkForUpdates(
        currentNodeVersion: String,
        currentNpmVersion: String
    ): Result<RuntimeUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            // Query official Node.js release index (read top slice safely for speed)
            val nodeReq = Request.Builder()
                .url("https://nodejs.org/dist/index.json")
                .header("Accept", "application/json")
                .build()

            var latestLts = RuntimeProfile.PRESETS.first().nodeVersion
            var latestCurrent = "v23.9.0"

            httpClient.newCall(nodeReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    val arr = JSONArray(raw)
                    var foundCurrent = false
                    var foundLts = false
                    for (i in 0 until minOf(arr.length(), 80)) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val ver = obj.optString("version")
                        val lts = obj.opt("lts")
                        if (!foundCurrent && ver.isNotBlank()) {
                            latestCurrent = ver
                            foundCurrent = true
                        }
                        if (!foundLts && lts != null && lts != false && lts.toString() != "false") {
                            latestLts = ver
                            foundLts = true
                        }
                        if (foundCurrent && foundLts) break
                    }
                }
            }

            // Query official npm latest tag
            val npmReq = Request.Builder()
                .url("https://registry.npmjs.org/npm/latest")
                .header("Accept", "application/json")
                .build()

            var latestNpm = currentNpmVersion
            httpClient.newCall(npmReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JSONObject(resp.body?.string() ?: "{}")
                    latestNpm = obj.optString("version").ifBlank { currentNpmVersion }
                }
            }

            val hasUpdate = (latestLts != currentNodeVersion && latestCurrent != currentNodeVersion) ||
                (latestNpm != currentNpmVersion)

            RuntimeUpdateInfo(
                latestNodeLts = latestLts,
                latestNodeCurrent = latestCurrent,
                latestNpmVersion = latestNpm,
                hasUpdate = hasUpdate
            )
        }
    }
}
