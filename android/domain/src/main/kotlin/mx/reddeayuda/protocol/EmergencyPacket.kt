package mx.reddeayuda.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class EmergencyPacket(
    val version: Int = ProtocolConstants.VERSION,
    val type: PacketType,
    val flags: Int,
    val ttl: Int,
    val hopCount: Int,
    val eventType: EventType,
    val status: OriginStatus,
    val battery: Int,
    val messageId: ByteArray,
    val originDeviceId: ByteArray,
    val timestamp: Long,
    val latitudeMicrodegrees: Int,
    val longitudeMicrodegrees: Int,
    val accuracyMeters: Int,
    val payload: ByteArray = ByteArray(0),
    val auth: ByteArray = ByteArray(ProtocolConstants.AUTH_SIZE)
) {
    init {
        require(messageId.size == ProtocolConstants.MESSAGE_ID_SIZE) { "messageId must be 16 bytes" }
        require(originDeviceId.size == ProtocolConstants.DEVICE_ID_SIZE) { "originDeviceId must be 8 bytes" }
        require(payload.size <= ProtocolConstants.MAX_PAYLOAD) { "payload too large" }
        require(auth.size == ProtocolConstants.AUTH_SIZE) { "auth must be 16 bytes" }
        require(ttl in 0..255 && hopCount in 0..255)
        require(battery in 0..255)
    }

    fun messageIdHex(): String = messageId.toHex()
    fun shortId(): String = messageId.toHex().take(8)
    fun originHex(): String = originDeviceId.toHex()

    fun isOrigin(localDeviceId: ByteArray): Boolean =
        originDeviceId.contentEquals(localDeviceId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmergencyPacket) return false
        return version == other.version &&
            type == other.type &&
            flags == other.flags &&
            ttl == other.ttl &&
            hopCount == other.hopCount &&
            eventType == other.eventType &&
            status == other.status &&
            battery == other.battery &&
            messageId.contentEquals(other.messageId) &&
            originDeviceId.contentEquals(other.originDeviceId) &&
            timestamp == other.timestamp &&
            latitudeMicrodegrees == other.latitudeMicrodegrees &&
            longitudeMicrodegrees == other.longitudeMicrodegrees &&
            accuracyMeters == other.accuracyMeters &&
            payload.contentEquals(other.payload) &&
            auth.contentEquals(other.auth)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + type.hashCode()
        result = 31 * result + flags
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + eventType.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + battery
        result = 31 * result + messageId.contentHashCode()
        result = 31 * result + originDeviceId.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + latitudeMicrodegrees
        result = 31 * result + longitudeMicrodegrees
        result = 31 * result + accuracyMeters
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + auth.contentHashCode()
        return result
    }

    companion object {
        fun randomMessageId(): ByteArray = uuidBytes(UUID.randomUUID())

        fun uuidBytes(uuid: UUID): ByteArray {
            val buf = ByteBuffer.allocate(16)
            buf.putLong(uuid.mostSignificantBits)
            buf.putLong(uuid.leastSignificantBits)
            return buf.array()
        }
    }
}

data class GeoFix(
    val latitudeMicrodegrees: Int = 0,
    val longitudeMicrodegrees: Int = 0,
    val accuracyMeters: Int = ProtocolConstants.UNKNOWN_ACCURACY
) {
    val isUnknown: Boolean
        get() = accuracyMeters == ProtocolConstants.UNKNOWN_ACCURACY &&
            latitudeMicrodegrees == 0 &&
            longitudeMicrodegrees == 0
}

data class AckPayload(
    val kind: AckKind,
    val refMessageId: ByteArray
) {
    fun toBytes(): ByteArray {
        val out = ByteArray(1 + ProtocolConstants.MESSAGE_ID_SIZE)
        out[0] = kind.code.toByte()
        System.arraycopy(refMessageId, 0, out, 1, ProtocolConstants.MESSAGE_ID_SIZE)
        return out
    }

    companion object {
        fun parse(payload: ByteArray): AckPayload? {
            if (payload.size < 1 + ProtocolConstants.MESSAGE_ID_SIZE) return null
            val kind = AckKind.from(payload[0].toInt() and 0xFF) ?: return null
            val ref = payload.copyOfRange(1, 1 + ProtocolConstants.MESSAGE_ID_SIZE)
            return AckPayload(kind, ref)
        }
    }
}

data class RescuePingPayload(
    val requestId: ByteArray,
    val targetDeviceId: ByteArray,
    val action: RescueAction
) {
    fun toBytes(): ByteArray {
        val out = ByteArray(16 + 8 + 1)
        System.arraycopy(requestId, 0, out, 0, 16)
        System.arraycopy(targetDeviceId, 0, out, 16, 8)
        out[24] = action.code.toByte()
        return out
    }

    fun isBroadcast(): Boolean = targetDeviceId.all { it == 0xFF.toByte() }

    fun isFor(localDeviceId: ByteArray): Boolean =
        isBroadcast() || targetDeviceId.contentEquals(localDeviceId)

    companion object {
        fun parse(payload: ByteArray): RescuePingPayload? {
            if (payload.size < 25) return null
            val action = RescueAction.from(payload[24].toInt() and 0xFF) ?: return null
            return RescuePingPayload(
                requestId = payload.copyOfRange(0, 16),
                targetDeviceId = payload.copyOfRange(16, 24),
                action = action
            )
        }

        fun allTarget(): ByteArray = ByteArray(8) { 0xFF.toByte() }
    }
}

object Hex {
    fun decode(hex: String): ByteArray {
        val clean = hex.replace(Regex("\\s"), "")
        require(clean.length % 2 == 0) { "odd hex length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun encode(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
}

fun ByteArray.toHex(): String = Hex.encode(this)

object LittleEndian {
    fun buffer(size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
}
