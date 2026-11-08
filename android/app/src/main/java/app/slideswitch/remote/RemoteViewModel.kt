package app.slideswitch.remote

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.slideswitch.remote.net.Action
import app.slideswitch.remote.net.Computer
import app.slideswitch.remote.net.ConnectionState
import app.slideswitch.remote.net.Discovery
import app.slideswitch.remote.net.RemoteClient
import kotlinx.coroutines.flow.StateFlow

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val client = RemoteClient(app, viewModelScope)
    private val discovery = Discovery(app)
    private val settings = AppSettings(app)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val onboarded: StateFlow<Boolean> = settings.onboarded
    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun finishOnboarding() = settings.finishOnboarding()

    val state: StateFlow<ConnectionState> = client.state
    val lastError: StateFlow<String?> = client.lastError
    val computers: StateFlow<List<Computer>> = discovery.computers
    val searching: StateFlow<Boolean> = discovery.searching

    val recent: Computer? get() = client.lastComputer()

    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()
    fun refreshDiscovery() = discovery.restart()

    fun connect(computer: Computer, code: String? = null) = client.connect(computer, code)

    fun connectManually(host: String, port: Int) =
        client.connect(Computer(name = host, host = host.trim(), port = port))

    fun submitCode(code: String) {
        val target = (state.value as? ConnectionState.NeedsCode)?.target ?: return
        client.connect(target, code)
    }

    fun cancel() = client.disconnect()
    fun disconnect() = client.disconnect()
    fun send(action: Action) = client.send(action)
    fun clearError() = client.clearError()

    /** slideswitch://pair?host=…&port=…&code=…&name=… (from the companion's QR code). */
    fun handleDeepLink(uri: Uri?) {
        if (uri == null || uri.scheme != "slideswitch" || uri.host != "pair") return
        val host = uri.getQueryParameter("host") ?: return
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return
        val name = uri.getQueryParameter("name") ?: host
        val code = uri.getQueryParameter("code")
        client.connect(Computer(name = name, host = host, port = port), code)
    }

    override fun onCleared() {
        discovery.stop()
        client.disconnect()
    }
}
