package mx.reddeayuda.core

interface Clock {
    fun nowEpochSeconds(): Long
}

object SystemClock : Clock {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}

class FixedClock(var epochSeconds: Long) : Clock {
    override fun nowEpochSeconds(): Long = epochSeconds
}

data class StateSnapshot(
    val role: mx.reddeayuda.protocol.DeviceRole,
    val state: mx.reddeayuda.protocol.DeviceState
)

data class QueuedPacket(
    val packet: mx.reddeayuda.protocol.EmergencyPacket,
    val firstSeenAt: Long,
    var lastSentAt: Long = 0L,
    var sendCount: Int = 0
)

data class GeoFixSnapshot(
    val latitudeMicrodegrees: Int,
    val longitudeMicrodegrees: Int,
    val accuracyMeters: Int
)

interface PacketRepository {
    fun insert(packet: mx.reddeayuda.protocol.EmergencyPacket, firstSeenAt: Long)
    fun getAll(): List<QueuedPacket>
    fun delete(messageId: ByteArray)
    fun size(): Int
    fun findByMessageId(messageId: ByteArray): QueuedPacket?
}

class InMemoryPacketRepository : PacketRepository {
    private val items = LinkedHashMap<String, QueuedPacket>()

    override fun insert(packet: mx.reddeayuda.protocol.EmergencyPacket, firstSeenAt: Long) {
        val key = packet.messageIdHex()
        if (!items.containsKey(key)) {
            items[key] = QueuedPacket(packet, firstSeenAt)
        }
    }

    override fun getAll(): List<QueuedPacket> = items.values.toList()

    override fun delete(messageId: ByteArray) {
        items.remove(mx.reddeayuda.protocol.Hex.encode(messageId))
    }

    override fun size(): Int = items.size

    override fun findByMessageId(messageId: ByteArray): QueuedPacket? =
        items[mx.reddeayuda.protocol.Hex.encode(messageId)]
}

interface Transport {
    fun discover()
    fun start()
    fun stop()
    fun send(packet: mx.reddeayuda.protocol.EmergencyPacket)
    fun isAvailable(): Boolean
    fun getCapabilities(): Int
}

interface PositioningProvider {
    fun getLastKnown(): mx.reddeayuda.protocol.GeoFix
    fun requestUpdate()
    fun isAvailable(): Boolean
}

interface EarthquakeProvider {
    fun getRecentEvents(): List<EarthquakeEvent>
    fun getEvent(id: String): EarthquakeEvent?
}

data class EarthquakeEvent(
    val id: String,
    val magnitude: Double,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class StubEarthquakeProvider : EarthquakeProvider {
    override fun getRecentEvents(): List<EarthquakeEvent> = emptyList()
    override fun getEvent(id: String): EarthquakeEvent? = null
}

interface EngineListener {
    fun onLog(message: String) {}
    fun onStateChanged(snapshot: StateSnapshot) {}
    fun onSosForRescuer(packet: mx.reddeayuda.protocol.EmergencyPacket) {}
    fun onAckForMe(kind: mx.reddeayuda.protocol.AckKind, packet: mx.reddeayuda.protocol.EmergencyPacket) {}
    fun onRescuePingForMe(action: mx.reddeayuda.protocol.RescueAction, packet: mx.reddeayuda.protocol.EmergencyPacket) {}
    fun onQueueChanged(size: Int) {}
}
