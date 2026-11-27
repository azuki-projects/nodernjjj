package com.example.runtime

import android.os.Build
import android.util.Base64
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal fun formatThrowableLines(t: Throwable): List<String> {
    val lines = mutableListOf<String>()
    var current: Throwable? = t
    var depth = 0
    while (current != null && depth < 6) {
        val typeName = current::class.java.simpleName.ifBlank { "Error" }
        val msg = current.message?.takeIf { it.isNotBlank() } ?: "(no message)"
        val msgLines = msg.replace("\r\n", "\n").split('\n')
        val prefix = if (depth == 0) "$typeName: " else "Caused by: $typeName: "
        msgLines.forEachIndexed { idx, line ->
            lines.add(if (idx == 0) "$prefix$line" else line)
        }
        current = current.cause
        depth++
    }
    val frames = t.stackTrace
        .filterNot { it.className.startsWith("kotlinx.coroutines.") || it.className.startsWith("kotlin.coroutines.") }
        .take(15)
    if (frames.isNotEmpty()) {
        frames.forEach { frame ->
            lines.add("    at $frame")
        }
    }
    return lines
}

class NodeEngine {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeServerPort = MutableStateFlow<Int?>(null)
    val activeServerPort: StateFlow<Int?> = _activeServerPort.asStateFlow()

    private val _lanAddresses = MutableStateFlow<List<String>>(emptyList())
    val lanAddresses: StateFlow<List<String>> = _lanAddresses.asStateFlow()

    private val runSeq = AtomicLong(0L)
    private var activeJob: Job? = null
    private val activeServers = ConcurrentHashMap<String, ServerHolder>()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<HttpReply>>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private data class HttpReply(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: String
    )

    private class ServerHolder(
        val socket: ServerSocket,
        val queue: Channel<String>,
        val acceptJob: Job
    )

    fun runFile(
        scope: CoroutineScope,
        projectDir: File,
        entryFile: File,
        nodeVersion: String = NodeRuntimeInstaller.DEFAULT_NODE_VERSION,
        npmVersion: String = NodeRuntimeInstaller.DEFAULT_NPM_VERSION,
        lanHostingEnabled: Boolean = true,
        args: List<String> = emptyList(),
        extraEnv: Map<String, String> = emptyMap(),
        onOutput: (TerminalLine) -> Unit,
        onClear: () -> Unit = {},
        onFinished: (Boolean) -> Unit = {}
    ) {
        val myRunId = runSeq.incrementAndGet()
        val previousJob = activeJob
        cleanupNetworkResources()
        _isRunning.value = true

        activeJob = scope.launch(Dispatchers.Default) {
            var success = true
            var qjs: QuickJs? = null
            try {
                previousJob?.cancelAndJoin()
                if (runSeq.get() != myRunId) {
                    success = false
                    return@launch
                }

                if (!entryFile.exists()) {
                    onOutput(TerminalLine(text = "Error: Cannot find module '${entryFile.name}'", type = TerminalLineType.STDERR))
                    success = false
                    return@launch
                }

                qjs = QuickJs.create(Dispatchers.Default)

                val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val argvJson = JSONArray(listOf("node", entryFile.absolutePath) + args).toString()
                val envObj = JSONObject().apply {
                    put("NODE_ENV", "development")
                    put("PORT", "3000")
                    put("HOST", if (lanHostingEnabled) "0.0.0.0" else "127.0.0.1")
                    put("HOME", projectDir.absolutePath)
                    extraEnv.forEach { (k, v) -> put(k, v) }
                }.toString()

                val projectCanonical = projectDir.canonicalPath

                fun resolveSandboxed(rawPath: String): File {
                    val f = File(rawPath)
                    val canonical = f.canonicalPath
                    if (canonical != projectCanonical && !canonical.startsWith(projectCanonical + File.separator)) {
                        error("EACCES: path outside workspace sandbox: $rawPath")
                    }
                    return f
                }

                qjs.define("__native") {
                    function("log") { fnArgs ->
                        val level = fnArgs.getOrNull(0)?.toString() ?: "STDOUT"
                        val msg = fnArgs.getOrNull(1)?.toString() ?: ""
                        if (level == "SYSTEM" && msg == "__CLEAR__") {
                            onClear()
                        } else {
                            val lineType = when (level) {
                                "STDERR" -> TerminalLineType.STDERR
                                "INFO" -> TerminalLineType.INFO
                                else -> TerminalLineType.STDOUT
                            }
                            msg.split('\n').forEach { line ->
                                onOutput(TerminalLine(text = line, type = lineType))
                            }
                        }
                        Unit
                    }

                    asyncFunction("sleep") { fnArgs ->
                        val ms = (fnArgs.getOrNull(0) as? Number)?.toLong() ?: 0L
                        delay(ms.coerceAtLeast(0L))
                        ""
                    }

                    function("fsExists") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: return@function false
                        runCatching { resolveSandboxed(path).exists() }.getOrDefault(false)
                    }

                    function("fsIsDir") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: return@function false
                        runCatching { resolveSandboxed(path).isDirectory }.getOrDefault(false)
                    }

                    function("fsRead") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        val file = resolveSandboxed(path)
                        if (!file.exists()) error("ENOENT: no such file or directory, open '$path'")
                        file.readText()
                    }

                    function("fsWrite") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        val data = fnArgs.getOrNull(1)?.toString() ?: ""
                        val append = (fnArgs.getOrNull(2) as? Boolean) ?: false
                        val file = resolveSandboxed(path)
                        file.parentFile?.mkdirs()
                        if (append) file.appendText(data) else file.writeText(data)
                        Unit
                    }

                    function("fsMkdir") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        resolveSandboxed(path).mkdirs()
                        Unit
                    }

                    function("fsReaddir") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        val dir = resolveSandboxed(path)
                        if (!dir.exists() || !dir.isDirectory) error("ENOENT: not a directory '$path'")
                        JSONArray(dir.list()?.toList() ?: emptyList<String>()).toString()
                    }

                    function("fsStat") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        val f = resolveSandboxed(path)
                        if (!f.exists()) error("ENOENT: '$path'")
                        JSONObject().apply {
                            put("size", f.length())
                            put("mtimeMs", f.lastModified())
                            put("isDirectory", f.isDirectory)
                            put("isFile", f.isFile)
                        }.toString()
                    }

                    function("fsDelete") { fnArgs ->
                        val path = fnArgs.getOrNull(0)?.toString() ?: error("Path required")
                        resolveSandboxed(path).deleteRecursively()
                        Unit
                    }

                    function("fsRename") { fnArgs ->
                        val src = resolveSandboxed(fnArgs.getOrNull(0)?.toString() ?: error("Source required"))
                        val dst = resolveSandboxed(fnArgs.getOrNull(1)?.toString() ?: error("Dest required"))
                        src.renameTo(dst)
                        Unit
                    }

                    function("base64Encode") { fnArgs ->
                        val arr = JSONArray(fnArgs.getOrNull(0)?.toString() ?: "[]")
                        val bytes = ByteArray(arr.length()) { i -> arr.getInt(i).toByte() }
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }

                    function("base64Decode") { fnArgs ->
                        val b64 = fnArgs.getOrNull(0)?.toString() ?: ""
                        val decoded = Base64.decode(b64, Base64.DEFAULT)
                        JSONArray(decoded.map { it.toInt() and 0xFF }).toString()
                    }

                    function("cryptoUuid") { UUID.randomUUID().toString() }

                    function("cryptoRandomBytes") { fnArgs ->
                        val size = (fnArgs.getOrNull(0) as? Number)?.toInt()?.coerceIn(1, 4096) ?: 16
                        val bytes = ByteArray(size)
                        SecureRandom().nextBytes(bytes)
                        JSONArray(bytes.map { it.toInt() and 0xFF }).toString()
                    }

                    function("cryptoHash") { fnArgs ->
                        val alg = mapHashAlgorithm(fnArgs.getOrNull(0)?.toString() ?: "sha256")
                        val data = fnArgs.getOrNull(1)?.toString() ?: ""
                        val enc = fnArgs.getOrNull(2)?.toString() ?: "hex"
                        val digest = MessageDigest.getInstance(alg).digest(data.toByteArray(Charsets.UTF_8))
                        encodeBytes(digest, enc)
                    }

                    function("cryptoHmac") { fnArgs ->
                        val alg = mapHmacAlgorithm(fnArgs.getOrNull(0)?.toString() ?: "sha256")
                        val key = fnArgs.getOrNull(1)?.toString() ?: ""
                        val data = fnArgs.getOrNull(2)?.toString() ?: ""
                        val enc = fnArgs.getOrNull(3)?.toString() ?: "hex"
                        val mac = Mac.getInstance(alg)
                        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), alg))
                        encodeBytes(mac.doFinal(data.toByteArray(Charsets.UTF_8)), enc)
                    }

                    asyncFunction("httpFetch") { fnArgs ->
                        val url = fnArgs.getOrNull(0)?.toString() ?: error("URL required")
                        val method = fnArgs.getOrNull(1)?.toString() ?: "GET"
                        val headersJson = fnArgs.getOrNull(2)?.toString() ?: "{}"
                        val bodyStr = fnArgs.getOrNull(3)?.toString() ?: ""
                        withContext(Dispatchers.IO) {
                            performFetch(url, method, headersJson, bodyStr)
                        }
                    }

                    asyncFunction("httpStartServer") { fnArgs ->
                        val requestedPort = (fnArgs.getOrNull(0) as? Number)?.toInt() ?: 3000
                        startLocalServer(scope, requestedPort, lanHostingEnabled, onOutput)
                    }

                    asyncFunction("httpNextRequest") { fnArgs ->
                        val serverId = fnArgs.getOrNull(0)?.toString() ?: return@asyncFunction ""
                        val holder = activeServers[serverId] ?: return@asyncFunction ""
                        holder.queue.receiveCatching().getOrNull() ?: ""
                    }

                    function("httpSendResponse") { fnArgs ->
                        val reqId = fnArgs.getOrNull(0)?.toString() ?: return@function Unit
                        val statusCode = (fnArgs.getOrNull(1) as? Number)?.toInt() ?: 200
                        val headersJson = fnArgs.getOrNull(2)?.toString() ?: "{}"
                        val body = fnArgs.getOrNull(3)?.toString() ?: ""
                        val headersObj = runCatching { JSONObject(headersJson) }.getOrDefault(JSONObject())
                        val headersMap = buildMap {
                            for (k in headersObj.keys()) put(k, headersObj.optString(k))
                        }
                        pendingResponses.remove(reqId)?.complete(HttpReply(statusCode, headersMap, body))
                        Unit
                    }

                    function("httpStopServer") { fnArgs ->
                        val serverId = fnArgs.getOrNull(0)?.toString() ?: return@function Unit
                        stopServer(serverId)
                        Unit
                    }
                }

                val bootstrap = NodeCoreModules.buildBootstrapScript(
                    projectPath = projectCanonical,
                    entryFilePath = entryFile.canonicalPath,
                    nodeVersion = nodeVersion,
                    npmVersion = npmVersion,
                    arch = arch,
                    argvJson = argvJson,
                    envJson = envObj
                )
                qjs.evaluate<Any?>(bootstrap)
                qjs.evaluate<Any?>("globalThis.__runEntry()")
            } catch (_: CancellationException) {
                success = false
            } catch (t: Throwable) {
                success = false
                formatThrowableLines(t).forEach { errLine ->
                    onOutput(
                        TerminalLine(
                            text = errLine,
                            type = TerminalLineType.STDERR
                        )
                    )
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { qjs?.close() }
                    if (runSeq.get() == myRunId) {
                        cleanupNetworkResources()
                        _isRunning.value = false
                    }
                }
                onFinished(success)
            }
        }
    }

    fun evaluateSnippet(
        scope: CoroutineScope,
        projectDir: File,
        code: String,
        nodeVersion: String = NodeRuntimeInstaller.DEFAULT_NODE_VERSION,
        npmVersion: String = NodeRuntimeInstaller.DEFAULT_NPM_VERSION,
        lanHostingEnabled: Boolean = true,
        onOutput: (TerminalLine) -> Unit,
        onFinished: (Boolean) -> Unit = {}
    ) {
        val tempFile = File(projectDir, ".eval_tmp.js")
        tempFile.writeText(code)
        runFile(
            scope = scope,
            projectDir = projectDir,
            entryFile = tempFile,
            nodeVersion = nodeVersion,
            npmVersion = npmVersion,
            lanHostingEnabled = lanHostingEnabled,
            onOutput = onOutput,
            onFinished = { ok ->
                tempFile.delete()
                onFinished(ok)
            }
        )
    }

    fun stop() {
        runSeq.incrementAndGet()
        cleanupNetworkResources()
        activeJob?.cancel()
        activeJob = null
        _isRunning.value = false
    }

    private fun cleanupNetworkResources() {
        activeServers.keys.toList().forEach { stopServer(it) }
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
        _activeServerPort.value = null
        _lanAddresses.value = emptyList()
    }

    private fun stopServer(serverId: String) {
        val holder = activeServers.remove(serverId) ?: return
        holder.acceptJob.cancel()
        holder.queue.close()
        runCatching { holder.socket.close() }
        if (activeServers.isEmpty()) {
            _activeServerPort.value = null
            _lanAddresses.value = emptyList()
        }
    }

    private suspend fun startLocalServer(
        scope: CoroutineScope,
        requestedPort: Int,
        lanHostingEnabled: Boolean,
        onOutput: (TerminalLine) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val bindAddress = if (lanHostingEnabled) {
            InetAddress.getByName("0.0.0.0")
        } else {
            InetAddress.getByName("127.0.0.1")
        }

        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, requestedPort), 32)
            }
        }.getOrElse {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, 0), 32)
            }
        }

        val actualPort = socket.localPort
        val lanIps = if (lanHostingEnabled) NetworkUtils.getLocalLanIpAddresses() else emptyList()
        _lanAddresses.value = lanIps

        if (actualPort != requestedPort) {
            onOutput(
                TerminalLine(
                    text = "[host] port $requestedPort busy; bound to port $actualPort",
                    type = TerminalLineType.INFO
                )
            )
        }
        if (lanHostingEnabled && lanIps.isNotEmpty()) {
            onOutput(
                TerminalLine(
                    text = "[host] Local network hosting active: http://${lanIps.first()}:$actualPort",
                    type = TerminalLineType.SUCCESS
                )
            )
        }

        val serverId = UUID.randomUUID().toString()
        val queue = Channel<String>(Channel.UNLIMITED)

        val acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleHttpClient(client, queue)
                }
            }
        }

        activeServers[serverId] = ServerHolder(socket, queue, acceptJob)
        _activeServerPort.value = actualPort
        JSONObject().apply {
            put("serverId", serverId)
            put("port", actualPort)
        }.toString()
    }

    private suspend fun handleHttpClient(socket: Socket, queue: Channel<String>) {
        socket.use { client ->
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream())
            val headerBytes = ByteArrayOutputStream()
            var prev4 = 0
            while (true) {
                val b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                prev4 = (prev4 shl 8) or (b and 0xFF)
                if (prev4 == 0x0D0A0D0A) break
                if (headerBytes.size() > 65536) break
            }

            val rawHeader = headerBytes.toString("UTF-8")
            val lines = rawHeader.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().split(" ")
            val method = requestLine.getOrNull(0) ?: "GET"
            val url = requestLine.getOrNull(1) ?: "/"

            val headersObj = JSONObject()
            var contentLength = 0
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headersObj.put(key, value)
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            val body = if (contentLength > 0 && contentLength < 1_000_000) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(buf, read, contentLength - read)
                    if (n <= 0) break
                    read += n
                }
                String(buf, 0, read, Charsets.UTF_8)
            } else ""

            val reqId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<HttpReply>()
            pendingResponses[reqId] = deferred

            val payload = JSONObject().apply {
                put("reqId", reqId)
                put("method", method)
                put("url", url)
                put("headers", headersObj)
                put("body", body)
            }.toString()

            queue.trySend(payload)

            val reply = withTimeoutOrNull(15_000L) { deferred.await() }
                ?: HttpReply(504, mapOf("Content-Type" to "text/plain"), "Gateway Timeout")

            runCatching {
                val out = client.getOutputStream()
                val bodyBytes = reply.body.toByteArray(Charsets.UTF_8)
                val statusText = when (reply.statusCode) {
                    200 -> "OK"
                    201 -> "Created"
                    204 -> "No Content"
                    400 -> "Bad Request"
                    404 -> "Not Found"
                    500 -> "Internal Server Error"
                    else -> "OK"
                }
                val respHeader = buildString {
                    append("HTTP/1.1 ${reply.statusCode} $statusText\r\n")
                    reply.headers.forEach { (k, v) ->
                        if (!k.equals("content-length", ignoreCase = true)) {
                            append("$k: $v\r\n")
                        }
                    }
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(respHeader.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
            }
        }
    }

    private fun performFetch(
        url: String,
        method: String,
        headersJson: String,
        bodyStr: String
    ): String {
        val builder = Request.Builder().url(url)
        val hdrObj = runCatching { JSONObject(headersJson) }.getOrDefault(JSONObject())
        var contentType = "application/json"
        for (k in hdrObj.keys()) {
            val v = hdrObj.optString(k)
            builder.header(k, v)
            if (k.equals("content-type", ignoreCase = true)) contentType = v
        }
        val reqBody = if (method == "GET" || method == "HEAD") null
        else bodyStr.toRequestBody(contentType.toMediaTypeOrNull())
        builder.method(method, reqBody)

        httpClient.newCall(builder.build()).execute().use { response ->
            val respHeaders = JSONObject()
            response.headers.names().forEach { name ->
                respHeaders.put(name.lowercase(), response.header(name) ?: "")
            }
            return JSONObject().apply {
                put("status", response.code)
                put("statusText", response.message)
                put("headers", respHeaders)
                put("body", response.body?.string() ?: "")
            }.toString()
        }
    }

    private fun mapHashAlgorithm(alg: String): String = when (alg.lowercase()) {
        "md5" -> "MD5"
        "sha1", "sha-1" -> "SHA-1"
        "sha512", "sha-512" -> "SHA-512"
        else -> "SHA-256"
    }

    private fun mapHmacAlgorithm(alg: String): String = when (alg.lowercase()) {
        "sha1", "sha-1" -> "HmacSHA1"
        "sha512", "sha-512" -> "HmacSHA512"
        else -> "HmacSHA256"
    }

    private fun encodeBytes(bytes: ByteArray, enc: String): String = when (enc.lowercase()) {
        "base64" -> Base64.encodeToString(bytes, Base64.NO_WRAP)
        else -> bytes.joinToString("") { "%02x".format(it) }
    }
}
