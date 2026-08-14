package mx.reddeayuda.app

import android.app.Application
import android.content.Intent
import android.os.Build
import mx.reddeayuda.ble.BleMeshManager
import mx.reddeayuda.core.EmergencyEngine
import mx.reddeayuda.core.EmergencyStateMachine
import mx.reddeayuda.core.EngineListener
import mx.reddeayuda.core.InMemoryPacketRepository
import mx.reddeayuda.core.MessageCache
import mx.reddeayuda.core.SystemClock
import mx.reddeayuda.data.SqlitePacketRepository
import mx.reddeayuda.platform.BatteryReader
import mx.reddeayuda.platform.ConnectivityHelper
import mx.reddeayuda.platform.DeviceIdentity
import mx.reddeayuda.platform.GnssProvider
import mx.reddeayuda.platform.RescueSound
import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.EmergencyPacketCodec
import mx.reddeayuda.protocol.ProtocolConstants
import mx.reddeayuda.protocol.RescueAction
import mx.reddeayuda.wifi.WifiDirectMeshManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

class RdaApp : Application() {
    lateinit var engine: EmergencyEngine
        private set
    lateinit var ble: BleMeshManager
        private set
    lateinit var wifi: WifiDirectMeshManager
        private set
    lateinit var gnss: GnssProvider
        private set
    lateinit var sound: RescueSound
        private set

    @Volatile
    var wifiDirectActive: Boolean = false
        private set

    @Volatile
    var watchSosActive: Boolean = false

    val logs = CopyOnWriteArrayList<String>()
    val uiListeners = CopyOnWriteArrayList<(String) -> Unit>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        val id = DeviceIdentity.getOrCreate(this)
        gnss = GnssProvider(this)
        sound = RescueSound(this)
        val repo = try {
            SqlitePacketRepository(this)
        } catch (_: Exception) {
            InMemoryPacketRepository()
        }
        engine = EmergencyEngine(
            localDeviceId = id,
            clock = SystemClock,
            repository = repo,
            cache = MessageCache(),
            stateMachine = EmergencyStateMachine(),
            listener = object : EngineListener {
                override fun onLog(message: String) {
                    push("CORE $message")
                }

                override fun onStateChanged(snapshot: mx.reddeayuda.core.StateSnapshot) {
                    push("ESTADO ${snapshot.role}/${snapshot.state}")
                }

                override fun onSosForRescuer(packet: EmergencyPacket) {
                    push("SOS ${packet.shortId()} hop=${packet.hopCount} bat=${packet.battery}")
                }

                override fun onAckForMe(kind: AckKind, packet: EmergencyPacket) {
                    push("ACK ${kind.name} — no significa que te hayan encontrado")
                }

                override fun onRescuePingForMe(action: RescueAction, packet: EmergencyPacket) {
                    push("RESCATISTAS CERCA (${action.name})")
                    sound.handle(action)
                }
            }
        )
        ble = BleMeshManager(this, id, object : BleMeshManager.Hooks {
            override fun outgoingPackets(): List<ByteArray> =
                engine.packetsToSend().map { EmergencyPacketCodec.encode(it) }

            override fun onIncoming(bytes: ByteArray) {
                try {
                    engine.receive(EmergencyPacketCodec.decode(bytes))
                } catch (e: Exception) {
                    push("paquete inválido ${e.message}")
                }
            }

            override fun metaBytes(): ByteArray {
                val snap = engine.snapshot()
                val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                buf.put(1)
                buf.put(snap.role.code.toByte())
                buf.put(snap.state.code.toByte())
                buf.put(engine.pendingCount().coerceIn(0, 255).toByte())
                buf.putShort(meshFlags().toShort())
                buf.putShort(8)
                return buf.array()
            }

            override fun beaconBytes(): ByteArray {
                val snap = engine.snapshot()
                return EmergencyPacketCodec.encodeBeacon(
                    snap.role,
                    snap.state,
                    engine.pendingCount(),
                    wantsExchange = true,
                    hasSos = engine.hasSosQueued() || snap.role == DeviceRole.RESCUER
                )
            }

            override fun onLog(message: String) {
                push("BLE $message")
            }
        })
        wifi = WifiDirectMeshManager(this, object : WifiDirectMeshManager.Hooks {
            override fun outgoingPackets(): List<ByteArray> =
                engine.packetsToSend().map { EmergencyPacketCodec.encode(it) }

            override fun onIncoming(bytes: ByteArray) {
                try {
                    engine.receive(EmergencyPacketCodec.decode(bytes))
                } catch (e: Exception) {
                    push("Wi‑Fi paquete inválido ${e.message}")
                }
            }

            override fun onLog(message: String) {
                push(message)
            }

            override fun onAvailabilityChanged(available: Boolean) {
                wifiDirectActive = available
                uiListeners.forEach { it("wifi") }
            }
        })
    }

    fun battery(): Int = BatteryReader.percent(this)

    fun meshFlags(): Int {
        var f = engine.flags()
        if (wifiDirectActive) f = f or ProtocolConstants.FLAG_WIFI
        if (ConnectivityHelper.isOnline(this)) f = f or ProtocolConstants.FLAG_GATEWAY
        if (watchSosActive) f = f or ProtocolConstants.FLAG_WATCH
        return f
    }

    fun startMesh(sos: Boolean = false) {
        val intent = Intent(this, MeshService::class.java).putExtra(MeshService.EXTRA_SOS, sos)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        ble.start()
        if (Prefs.wifiDirectEnabled(this) && ConnectivityHelper.wifiDirectSupported(this)) {
            wifi.start()
        }
    }

    fun stopMesh() {
        wifi.stop()
        ble.stop()
        stopService(Intent(this, MeshService::class.java))
    }

    fun push(message: String) {
        val line = "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $message"
        logs.add(0, line)
        while (logs.size > 200) logs.removeAt(logs.lastIndex)
        uiListeners.forEach { it(line) }
    }

    companion object {
        lateinit var instance: RdaApp
            private set
    }
}
