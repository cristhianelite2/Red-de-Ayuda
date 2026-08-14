package mx.reddeayuda.core

import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.EventType
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.RescueAction

/**
 * Mundo de radio en memoria: si dos nodos están en rango, intercambian paquetes
 * (equivalente a una sesión GATT corta).
 */
class RadioWorld(private val range: Double = 10.0) {
    private val nodes = mutableListOf<SimNode>()

    fun add(node: SimNode) {
        nodes += node
        node.world = this
    }

    fun tick() {
        val pairs = mutableListOf<Pair<SimNode, SimNode>>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                if (a.distanceTo(b) <= range && a.radioOn && b.radioOn) {
                    pairs += a to b
                }
            }
        }
        for ((a, b) in pairs) {
            exchange(a, b)
            exchange(b, a)
        }
    }

    private fun exchange(from: SimNode, to: SimNode) {
        val packets = from.engine.packetsToSend()
        for (p in packets) {
            to.engine.receive(p)
        }
    }
}

class SimNode(
    val name: String,
    deviceId: ByteArray,
    var x: Double,
    var y: Double,
    val clock: Clock,
    role: DeviceRole = DeviceRole.CIVILIAN
) {
    var world: RadioWorld? = null
    var radioOn: Boolean = true
    val civilianAlerts = mutableListOf<String>()
    val rescueVisible = mutableListOf<EmergencyPacket>()
    val pings = mutableListOf<RescueAction>()
    val acks = mutableListOf<AckKind>()
    val logs = mutableListOf<String>()

    val engine = EmergencyEngine(
        localDeviceId = deviceId,
        clock = clock,
        repository = InMemoryPacketRepository(),
        cache = MessageCache(),
        stateMachine = EmergencyStateMachine(role = role),
        listener = object : EngineListener {
            override fun onLog(message: String) {
                logs += "$name: $message"
            }

            override fun onSosForRescuer(packet: EmergencyPacket) {
                rescueVisible += packet
            }

            override fun onAckForMe(kind: AckKind, packet: EmergencyPacket) {
                acks += kind
            }

            override fun onRescuePingForMe(action: RescueAction, packet: EmergencyPacket) {
                pings += action
            }
        }
    )

    fun distanceTo(other: SimNode): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun sos(fix: GeoFix = GeoFix(19432608, -99133100, 15), battery: Int = 80) {
        engine.createEmergency(fix, battery, EventType.USER_INITIATED)
    }

    fun asRescue() {
        engine.enterRescueRole()
    }

    fun pingSound(target: ByteArray) {
        engine.createRescuePing(target, RescueAction.SOUND)
    }
}

fun deviceId(seed: Int): ByteArray = ByteArray(8) { i -> (seed * 17 + i).toByte() }
