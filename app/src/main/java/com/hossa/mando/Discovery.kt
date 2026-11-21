package com.hossa.mando

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

/** Busca Chromecast / Google TV en la red WiFi (mDNS "_androidtvremote2._tcp"). */
class Discovery(ctx: Context, private val onFound: (name: String, host: String) -> Unit) {

    private val appCtx = ctx.applicationContext
    private val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        try {
            val wifi = appCtx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            lock = wifi.createMulticastLock("mando").apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) { }

        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                main.post { queue.addLast(service); next() }
            }
        }
        listener = l
        try { nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun next() {
        if (resolving) return
        val s = queue.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    main.post { resolving = false; next() }
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    main.post {
                        resolving = false
                        if (host != null) onFound(info.serviceName ?: "Chromecast", host)
                        next()
                    }
                }
            })
        } catch (e: Exception) {
            resolving = false
        }
    }

    fun stop() {
        try { listener?.let { nsd.stopServiceDiscovery(it) } } catch (e: Exception) { }
        listener = null
        try { lock?.release() } catch (e: Exception) { }
    }
}
