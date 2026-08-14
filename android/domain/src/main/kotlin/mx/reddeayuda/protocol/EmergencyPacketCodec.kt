package mx.reddeayuda.protocol

import java.nio.ByteBuffer

object EmergencyPacketCodec {
    fun encode(packet: EmergencyPacket): ByteArray {
        val payload = packet.payload
        val total = ProtocolConstants.HEADER_SIZE + payload.size + ProtocolConstants.AUTH_SIZE
        val buf = LittleEndian.buffer(total)
        putU8(buf, packet.version)
        putU8(buf, packet.type.code)
        putU8(buf, packet.flags)
        putU8(buf, packet.ttl)
        putU8(buf, packet.hopCount)
        putU8(buf, packet.eventType.code)
        putU8(buf, packet.status.code)
        putU8(buf, packet.battery)
        buf.put(packet.messageId)
        buf.put(packet.originDeviceId)
        buf.putInt(packet.timestamp.toInt())
        buf.putInt(packet.latitudeMicrodegrees)
        buf.putInt(packet.longitudeMicrodegrees)
        putU16(buf, packet.accuracyMeters)
        putU16(buf, payload.size)
        buf.put(payload)
        buf.put(packet.auth)
        return buf.array()
    }

    fun decode(bytes: ByteArray): EmergencyPacket {
        val min = ProtocolConstants.HEADER_SIZE + ProtocolConstants.AUTH_SIZE
        require(bytes.size >= min) { "packet too short: ${bytes.size}" }
        val buf = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val version = getU8(buf)
        require(version == ProtocolConstants.VERSION) { "unsupported version $version" }
        val type = PacketType.from(getU8(buf)) ?: error("unknown packet type")
        val flags = getU8(buf)
        val ttl = getU8(buf)
        val hopCount = getU8(buf)
        val eventType = EventType.from(getU8(buf)) ?: EventType.UNKNOWN
        val status = OriginStatus.from(getU8(buf)) ?: OriginStatus.UNKNOWN
        val battery = getU8(buf)
        val messageId = ByteArray(ProtocolConstants.MESSAGE_ID_SIZE).also { buf.get(it) }
        val origin = ByteArray(ProtocolConstants.DEVICE_ID_SIZE).also { buf.get(it) }
        val timestamp = buf.int.toLong() and 0xFFFF_FFFFL
        val lat = buf.int
        val lon = buf.int
        val accuracy = getU16(buf)
        val payloadLen = getU16(buf)
        require(payloadLen <= ProtocolConstants.MAX_PAYLOAD) { "payloadLen $payloadLen" }
        require(bytes.size >= ProtocolConstants.HEADER_SIZE + payloadLen + ProtocolConstants.AUTH_SIZE) {
            "truncated payload"
        }
        val payload = ByteArray(payloadLen).also { buf.get(it) }
        val auth = ByteArray(ProtocolConstants.AUTH_SIZE).also { buf.get(it) }
        return EmergencyPacket(
            version = version,
            type = type,
            flags = flags,
            ttl = ttl,
            hopCount = hopCount,
            eventType = eventType,
            status = status,
            battery = battery,
            messageId = messageId,
            originDeviceId = origin,
            timestamp = timestamp,
            latitudeMicrodegrees = lat,
            longitudeMicrodegrees = lon,
            accuracyMeters = accuracy,
            payload = payload,
            auth = auth
        )
    }

    fun encodeBeacon(role: DeviceRole, state: DeviceState, pendingCount: Int, wantsExchange: Boolean, hasSos: Boolean): ByteArray {
        val flags = (if (hasSos) 1 else 0) or (if (wantsExchange) 2 else 0)
        return byteArrayOf(
            ProtocolConstants.BEACON_MAGIC_0,
            ProtocolConstants.BEACON_MAGIC_1,
            ProtocolConstants.VERSION.toByte(),
            ((state.code and 0x0F shl 4) or (role.code and 0x0F)).toByte(),
            pendingCount.coerceIn(0, 255).toByte(),
            flags.toByte()
        )
    }

    fun decodeBeacon(data: ByteArray): Beacon? {
        if (data.size < 6) return null
        if (data[0] != ProtocolConstants.BEACON_MAGIC_0 || data[1] != ProtocolConstants.BEACON_MAGIC_1) return null
        val packed = data[3].toInt() and 0xFF
        return Beacon(
            version = data[2].toInt() and 0xFF,
            role = DeviceRole.from(packed and 0x0F),
            state = DeviceState.from(packed shr 4),
            pendingCount = data[4].toInt() and 0xFF,
            hasSos = (data[5].toInt() and 1) != 0,
            wantsExchange = (data[5].toInt() and 2) != 0
        )
    }

    private fun putU8(buf: ByteBuffer, v: Int) {
        buf.put((v and 0xFF).toByte())
    }

    private fun putU16(buf: ByteBuffer, v: Int) {
        buf.putShort((v and 0xFFFF).toShort())
    }

    private fun getU8(buf: ByteBuffer): Int = buf.get().toInt() and 0xFF

    private fun getU16(buf: ByteBuffer): Int = buf.short.toInt() and 0xFFFF
}

data class Beacon(
    val version: Int,
    val role: DeviceRole,
    val state: DeviceState,
    val pendingCount: Int,
    val hasSos: Boolean,
    val wantsExchange: Boolean
)
