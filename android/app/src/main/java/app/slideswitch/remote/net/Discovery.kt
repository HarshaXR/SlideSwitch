package app.slideswitch.remote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** Finds SlideSwitch companions on the local network via NSD (mDNS / DNS-SD). */
class Discovery(context: Context) {
    companion object {
        const val SERVICE_TYPE = "_slideswitch._tcp."
        private const val TAG = "SlideSwitchNsd"
    }

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _computers = MutableStateFlow<List<Computer>>(emptyList())
    val computers: StateFlow<List<Computer>> = _computers.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        val nsd = nsd ?: return
        if (listener != null) return
        multicastLock = wifi?.createMulticastLock("slideswitch")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { _searching.value = true }
            override fun onDiscoveryStopped(serviceType: String) { _searching.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start failed $errorCode")
                _searching.value = false
                listener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop failed $errorCode")
            }
            override fun onServiceFound(info: NsdServiceInfo) = enqueueResolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _computers.value = _computers.value.filterNot { it.name == info.serviceName }
            }
        }
        listener = l
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        listener = null
        _searching.value = false
        multicastLock?.release()
        multicastLock = null
    }

    fun restart() {
        stop()
        _computers.value = emptyList()
        start()
    }

    // Older NsdManager versions allow only one resolve at a time, so resolve sequentially.
    @Synchronized
    private fun enqueueResolve(info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        if (!resolving) resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        val next = resolveQueue.removeFirstOrNull()
        if (next == null) {
            resolving = false
            return
        }
        resolving = true
        val nsd = nsd ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = resolveNext()
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = resolveNext()
            }
            nsd.registerServiceInfoCallback(next, executor, callback)
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = resolveNext()
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo)
                    resolveNext()
                }
            })
        }
    }

    private fun onResolved(info: NsdServiceInfo) {
        val host = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: info.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION") info.host
        }?.hostAddress ?: return
        val attrs = info.attributes
        fun attr(key: String) = attrs[key]?.toString(Charsets.UTF_8)
        val computer = Computer(
            name = info.serviceName.replace("\\032", " "),
            host = host,
            port = info.port,
            serverId = attr("id"),
            platform = attr("platform"),
        )
        _computers.value = (_computers.value.filterNot { it.name == computer.name } + computer)
            .sortedBy { it.name.lowercase() }
    }
}
