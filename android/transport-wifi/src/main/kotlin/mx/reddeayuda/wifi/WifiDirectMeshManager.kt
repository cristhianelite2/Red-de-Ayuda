package mx.reddeayuda.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transporte Wi‑Fi Direct oportunista: descubre peers, forma grupo y
 * intercambia paquetes binarios (mismo payload que BLE GATT).
 */
class WifiDirectMeshManager(
    private val context: Context,
    private val hooks: Hooks
) {
    interface Hooks {
        fun outgoingPackets(): List<ByteArray>
        fun onIncoming(bytes: ByteArray)
        fun onLog(message: String)
        fun onAvailabilityChanged(available: Boolean)
    }

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private val running = AtomicBoolean(false)
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var discoverTask: Runnable? = null

    val isSupported: Boolean
        get() = manager != null &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)

    @Volatile
    var lastActive: Boolean = false
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val on = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    lastActive = on && running.get()
                    hooks.onAvailabilityChanged(lastActive)
                    hooks.onLog(if (on) "Wi‑Fi Direct listo" else "Wi‑Fi Direct apagado")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onConnectionChanged()
            }
        }
    }

    fun start() {
        if (!isSupported) {
            hooks.onLog("Wi‑Fi Direct no soportado en este teléfono")
            hooks.onAvailabilityChanged(false)
            return
        }
        if (!running.compareAndSet(false, true)) return
        channel = manager!!.initialize(context, context.mainLooper, null)
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        lastActive = true
        hooks.onAvailabilityChanged(true)
        hooks.onLog("Wi‑Fi Direct activo (SOS y retransmisión)")
        scheduleDiscover()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        discoverTask?.let { main.removeCallbacks(it) }
        discoverTask = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        try {
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {
        }
        lastActive = false
        hooks.onAvailabilityChanged(false)
        hooks.onLog("Wi‑Fi Direct detenido")
    }

    private fun scheduleDiscover() {
        val task = object : Runnable {
            override fun run() {
                if (!running.get()) return
                try {
                    manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = hooks.onLog("Wi‑Fi Direct buscando peers…")
                        override fun onFailure(reason: Int) =
                            hooks.onLog("Wi‑Fi Direct discover falló ($reason)")
                    })
                } catch (e: SecurityException) {
                    hooks.onLog("Wi‑Fi Direct sin permiso: ${e.message}")
                }
                main.postDelayed(this, 20_000L)
            }
        }
        discoverTask = task
        main.post(task)
    }

    private fun requestPeers() {
        try {
            manager?.requestPeers(channel) { list ->
                val peers = list?.deviceList.orEmpty()
                val target = peers.firstOrNull {
                    it.status != WifiP2pDevice.CONNECTED &&
                        (it.deviceName.contains("Android", true) ||
                            it.deviceName.contains("Red", true) ||
                            it.deviceName.isNotBlank())
                } ?: return@requestPeers
                connect(target)
            }
        } catch (e: SecurityException) {
            hooks.onLog("Wi‑Fi Direct peers: ${e.message}")
        }
    }

    private fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = hooks.onLog("Wi‑Fi Direct conectando a ${device.deviceName}")
                override fun onFailure(reason: Int) =
                    hooks.onLog("Wi‑Fi Direct connect falló ($reason)")
            })
        } catch (e: SecurityException) {
            hooks.onLog("Wi‑Fi Direct connect: ${e.message}")
        }
    }

    private fun onConnectionChanged() {
        try {
            manager?.requestConnectionInfo(channel) { info ->
                if (info == null || !info.groupFormed) return@requestConnectionInfo
                io.execute {
                    try {
                        if (info.isGroupOwner) {
                            serveAsOwner()
                        } else {
                            val host = info.groupOwnerAddress?.hostAddress ?: return@execute
                            connectAsClient(host)
                        }
                    } catch (e: Exception) {
                        hooks.onLog("Wi‑Fi Direct I/O: ${e.message}")
                    }
                }
            }
        } catch (e: SecurityException) {
            hooks.onLog("Wi‑Fi Direct info: ${e.message}")
        }
    }

    private fun serveAsOwner() {
        ServerSocket(PORT).use { server ->
            server.soTimeout = 12_000
            val socket = server.accept()
            exchange(socket)
        }
    }

    private fun connectAsClient(host: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, PORT), 8_000)
            exchange(socket)
        }
    }

    private fun exchange(socket: Socket) {
        socket.soTimeout = 10_000
        val out = DataOutputStream(socket.getOutputStream())
        val input = DataInputStream(socket.getInputStream())
        val packets = hooks.outgoingPackets()
        out.writeInt(packets.size.coerceAtMost(8))
        packets.take(8).forEach { bytes ->
            out.writeInt(bytes.size)
            out.write(bytes)
        }
        out.flush()
        val count = input.readInt().coerceIn(0, 8)
        repeat(count) {
            val size = input.readInt().coerceIn(0, 4096)
            val buf = ByteArray(size)
            input.readFully(buf)
            hooks.onIncoming(buf)
        }
        hooks.onLog("Wi‑Fi Direct: enviados ${packets.size.coerceAtMost(8)}, recibidos $count")
        try {
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PORT = 8988
    }
}
