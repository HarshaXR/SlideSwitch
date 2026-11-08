package app.slideswitch.remote.net

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A computer running a SlideSwitch companion app. */
data class Computer(
    val name: String,
    val host: String,
    val port: Int,
    val serverId: String? = null,
    val platform: String? = null,
) {
    val key: String get() = serverId ?: "$host:$port"
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val target: Computer) : ConnectionState
    /** The server wants a pairing code (none supplied, or a stored token was rejected). */
    data class NeedsCode(val target: Computer, val wrongCode: Boolean) : ConnectionState
    data class Connected(
        val target: Computer,
        val serverName: String,
        val platform: String,
        val capabilities: Set<String>,
    ) : ConnectionState
    data class Failed(val target: Computer, val message: String) : ConnectionState
}

/** Protocol actions (shared/PROTOCOL.md). New ones can be added without breaking old servers. */
enum class Action(val wire: String) {
    Next("next"), Previous("previous"), Start("start"), End("end"), Black("black"),
}

/**
 * SlideSwitch protocol v1 client over WebSocket (OkHttp).
 * Stores per-server tokens so re-pairing needs no code.
 */
class RemoteClient(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("slideswitch", Context.MODE_PRIVATE)
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Last error message from a command (e.g. permission_denied on the computer). */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private val commandCounter = AtomicInteger(0)
    private var generation = 0

    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        prefs.edit()?.putString("deviceId", it)?.apply()
    }

    private val deviceName: String =
        listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct().joinToString(" ").ifBlank { "Android phone" }

    fun hasToken(target: Computer): Boolean = tokenFor(target) != null

    /** Connects using a stored token, or [code] if given. With neither, asks the UI for a code. */
    fun connect(target: Computer, code: String? = null) {
        disconnect()
        val token = tokenFor(target)
        if (code == null && token == null) {
            _state.value = ConnectionState.NeedsCode(target, wrongCode = false)
            return
        }
        val myGeneration = ++generation
        _state.value = ConnectionState.Connecting(target)
        _lastError.value = null

        val host = if (target.host.contains(':')) "[${target.host}]" else target.host
        val request = Request.Builder().url("ws://$host:${target.port}/").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject()
                    .put("type", "hello")
                    .put("v", 1)
                    .put("device", deviceName)
                    .put("deviceId", deviceId)
                if (code != null) hello.put("code", code) else hello.put("token", token)
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGeneration != generation) return
                handle(target, text, usedCode = code != null)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGeneration != generation) return
                onDropped(target, "Disconnected")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGeneration != generation) return
                onDropped(target, t.message ?: "Couldn't reach ${target.name}")
            }
        })
    }

    fun disconnect() {
        generation++
        pingJob?.cancel()
        socket?.close(1000, "bye")
        socket = null
        _state.value = ConnectionState.Idle
    }

    /** Sends a command. Returns false if not connected or the server lacks the capability. */
    fun send(action: Action): Boolean {
        val connected = _state.value as? ConnectionState.Connected ?: return false
        if (action.wire !in connected.capabilities) return false
        val message = JSONObject()
            .put("type", "command")
            .put("id", "c-${commandCounter.incrementAndGet()}")
            .put("action", action.wire)
        return socket?.send(message.toString()) == true
    }

    fun clearError() { _lastError.value = null }

    private fun handle(target: Computer, text: String, usedCode: Boolean) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "welcome" -> {
                val caps = json.optJSONArray("capabilities")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                } ?: Action.entries.map { it.wire }.toSet()
                val serverId = json.optString("serverId").ifBlank { null }
                val resolved = target.copy(serverId = serverId ?: target.serverId)
                json.optString("token").ifBlank { null }?.let { saveToken(resolved, target, it) }
                _state.value = ConnectionState.Connected(
                    target = resolved,
                    serverName = json.optString("server", target.name),
                    platform = json.optString("platform", "unknown"),
                    capabilities = caps,
                )
                startPings()
            }
            "error" -> when (json.optString("code")) {
                "bad_code" -> {
                    if (!usedCode) forgetToken(target)
                    generation++
                    _state.value = ConnectionState.NeedsCode(target, wrongCode = usedCode)
                }
                "version", "not_paired" -> {
                    generation++
                    _state.value = ConnectionState.Failed(target, json.optString("message", "Incompatible companion"))
                }
                else -> _lastError.value = json.optString("message", "The computer refused that command")
            }
            "ack", "pong", "status" -> Unit
            else -> Unit // Unknown types are ignored, per the protocol's extensibility rule.
        }
    }

    private fun onDropped(target: Computer, message: String) {
        pingJob?.cancel()
        socket = null
        val current = _state.value
        if (current is ConnectionState.NeedsCode) return
        _state.value = ConnectionState.Failed(target, message)
    }

    private fun startPings() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(10_000)
                socket?.send("""{"type":"ping"}""")
            }
        }
    }

    // Tokens are stored under both the server id and host:port, so a manual or QR connection
    // (which doesn't know the id up front) still finds them.
    private fun tokenFor(target: Computer): String? =
        target.serverId?.let { prefs.getString("token:$it", null) }
            ?: prefs.getString("token:${target.host}:${target.port}", null)

    private fun saveToken(resolved: Computer, original: Computer, token: String) {
        prefs.edit().apply {
            resolved.serverId?.let { putString("token:$it", token) }
            putString("token:${original.host}:${original.port}", token)
            putString("last", JSONObject()
                .put("name", resolved.name).put("host", resolved.host)
                .put("port", resolved.port).put("serverId", resolved.serverId ?: "")
                .toString())
        }.apply()
    }

    private fun forgetToken(target: Computer) {
        prefs.edit().apply {
            target.serverId?.let { remove("token:$it") }
            remove("token:${target.host}:${target.port}")
        }.apply()
    }

    /** The most recently connected computer, for one-tap reconnect. */
    fun lastComputer(): Computer? {
        val raw = prefs.getString("last", null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            Computer(j.getString("name"), j.getString("host"), j.getInt("port"), j.optString("serverId").ifBlank { null })
        }.getOrNull()
    }
}
