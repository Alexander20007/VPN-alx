package com.alx.vpn.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.alx.vpn.MainActivity
import com.alx.vpn.R
import com.alx.vpn.data.ProfileStore
import com.alx.vpn.data.SettingsStore
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import java.net.NetworkInterface
import kotlin.concurrent.thread

class AlxVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.alx.vpn.STOP"
        const val EXTRA_PROFILE = "profile_id"
        private const val CHANNEL = "alx_vpn"
        private const val NOTIF_ID = 1
    }

    private val cm by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    @Volatile private var box: BoxService? = null
    @Volatile private var listener: Any? = null
    private var monitorCb: ConnectivityManager.NetworkCallback? = null
    @Volatile private var stopping = false
    @Volatile private var tunPfd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            startFg("Desconectando…")
            stopVpn()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_PROFILE)
        startFg("Conectando…")
        VpnState.message.value = ""
        VpnState.status.value = VpnState.Status.Connecting
        thread(name = "alx-start") { startVpn(id) }
        return START_NOT_STICKY
    }

    private fun startVpn(profileId: String?) {
        try {
            val p = ProfileStore(this).load().firstOrNull { it.id == profileId }
                ?: error("Perfil não encontrado")
            VpnState.log("Iniciando ${p.name} (${p.protocol})")
            Core.ensureSetup(this)
            val config = ConfigBuilder.build(p, SettingsStore(this).load())
            val svc = Libbox.newService(config, PlatformProxy.create(this) as PlatformInterface)
            svc.start()
            box = svc
            if (stopping) {
                try { svc.close() } catch (_: Throwable) {}
                return
            }
            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = VpnState.Status.Connected
            startFg("Conectado a ${p.name}")
            VpnState.log("Conectado")
        } catch (t: Throwable) {
            val msg = (t.cause?.message ?: t.message ?: t.toString())
            VpnState.log("ERRO: $msg")
            VpnState.message.value = msg
            VpnState.status.value = VpnState.Status.Error
            stopVpn()
        }
    }

    private fun stopVpn() {
        if (stopping) return
        stopping = true
        thread(name = "alx-stop") {
            val b = box
            box = null
            if (b != null) {
                val t = thread(name = "alx-close") { try { b.close() } catch (_: Throwable) {} }
                t.join(4000)
            }
            stopMonitor()
            closeTun()
            if (VpnState.status.value != VpnState.Status.Error) {
                VpnState.status.value = VpnState.Status.Idle
            }
            VpnState.connectedAt.value = 0L
            VpnState.log("Desconectado")
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun closeTun() {
        try { tunPfd?.close() } catch (_: Throwable) {}
        tunPfd = null
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        try { box?.close() } catch (_: Throwable) {}
        box = null
        stopMonitor()
        closeTun()
        if (VpnState.status.value == VpnState.Status.Connected ||
            VpnState.status.value == VpnState.Status.Connecting
        ) VpnState.status.value = VpnState.Status.Idle
        super.onDestroy()
    }

    // ---------- chamados pela PlatformProxy ----------

    fun openTunFd(): Int {
        val cfg = SettingsStore(this).load()
        closeTun()
        val b = Builder()
            .setSession("ALX VPN")
            .setMtu(cfg.mtu)
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("172.19.0.2")
        if (cfg.ipv6) {
            b.addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0)
        }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)

        // Split tunneling por app. O próprio ALX VPN fica fora do túnel para evitar loops.
        val apps = cfg.apps.filter { it != packageName }
        try {
            if (cfg.appMode == "only" && apps.isNotEmpty()) {
                apps.forEach { try { b.addAllowedApplication(it) } catch (_: Exception) {} }
            } else {
                if (cfg.appMode == "except") apps.forEach { try { b.addDisallowedApplication(it) } catch (_: Exception) {} }
                b.addDisallowedApplication(packageName)
            }
        } catch (_: Exception) {}

        pickUnderlying()?.let { b.setUnderlyingNetworks(arrayOf(it)) }
        val pfd = b.establish() ?: throw IllegalStateException("Permissão de VPN negada ou revogada")
        tunPfd = pfd
        // O sing-box recebe uma cópia do fd; o original fica aqui para fechar a "chave" ao desconectar.
        return pfd.dup().detachFd()
    }

    fun startMonitor(l: Any) {
        listener = l
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notifyDefault()
            override fun onLost(network: Network) = notifyDefault()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = notifyDefault()
        }
        monitorCb = cb
        cm.registerNetworkCallback(req, cb)
        notifyDefault()
    }

    fun stopMonitor() {
        monitorCb?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Throwable) {} }
        monitorCb = null
        listener = null
    }

    private fun pickUnderlying(): Network? {
        @Suppress("DEPRECATION")
        val nets = cm.allNetworks.mapNotNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) n to c else null
        }
        return nets.minByOrNull { (_, c) ->
            when {
                c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
                else -> 3
            }
        }?.first
    }

    private fun notifyDefault() {
        val l = listener ?: return
        val net = pickUnderlying()
        val name = net?.let { cm.getLinkProperties(it)?.interfaceName } ?: ""
        val idx = if (name.isNotEmpty()) {
            try { NetworkInterface.getByName(name)?.index ?: -1 } catch (_: Exception) { -1 }
        } else -1
        try { if (net != null) setUnderlyingNetworks(arrayOf(net)) } catch (_: Throwable) {}
        try {
            val ic = Class.forName("io.nekohasekai.libbox.InterfaceUpdateListener")
            val m = ic.methods.first { it.name.equals("updateDefaultInterface", true) }
            val a = m.parameterTypes.map { t ->
                when (t) {
                    String::class.java -> name
                    java.lang.Integer.TYPE -> idx
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }
            m.invoke(l, *a.toTypedArray())
        } catch (t: Throwable) {
            VpnState.log("monitor: ${t.cause?.message ?: t.message}")
        }
    }

    fun interfaces(): Any {
        val ifCls = Class.forName("io.nekohasekai.libbox.NetworkInterface")
        val iterCls = Class.forName("io.nekohasekai.libbox.NetworkInterfaceIterator")
        val sys = try { NetworkInterface.getNetworkInterfaces().toList() } catch (_: Exception) { emptyList() }
        val list = mutableListOf<Any>()
        @Suppress("DEPRECATION")
        for (n in cm.allNetworks) {
            val lp = cm.getLinkProperties(n) ?: continue
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val name = lp.interfaceName ?: continue
            val ni = sys.find { it.name == name } ?: continue
            val o = ifCls.getDeclaredConstructor().newInstance()
            PlatformProxy.setBean(o, "name", name)
            PlatformProxy.setBean(o, "index", ni.index)
            PlatformProxy.setBean(o, "mtu", try { ni.mtu } catch (_: Exception) { 1500 })
            PlatformProxy.setBean(
                o, "addresses",
                ni.interfaceAddresses.map { (it.address.hostAddress ?: "").substringBefore('%') + "/" + it.networkPrefixLength },
            )
            PlatformProxy.setBean(o, "dnsServer", lp.dnsServers.mapNotNull { it.hostAddress })
            var flags = 0
            try {
                if (ni.isUp) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                if (ni.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                if (ni.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                if (ni.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            } catch (_: Exception) {}
            PlatformProxy.setBean(o, "flags", flags)
            PlatformProxy.setBean(
                o, "type",
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                },
            )
            PlatformProxy.setBean(o, "metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            list.add(o)
        }
        return PlatformProxy.iterProxy(iterCls, list)
    }

    // ---------- notificação ----------

    private fun startFg(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "ALX VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, AlxVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ALX VPN")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Desconectar", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
