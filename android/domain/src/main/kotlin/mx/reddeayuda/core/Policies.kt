package mx.reddeayuda.core

import mx.reddeayuda.protocol.Hex
import mx.reddeayuda.protocol.ProtocolConstants

class MessageCache(
    private val capacity: Int = ProtocolConstants.SEEN_CACHE_CAPACITY
) {
    private val order = LinkedHashMap<String, Long>(capacity, 0.75f, true)

    fun contains(messageId: ByteArray): Boolean = order.containsKey(Hex.encode(messageId))

    fun add(messageId: ByteArray, now: Long) {
        val key = Hex.encode(messageId)
        if (order.containsKey(key)) {
            order[key] = now
            return
        }
        order[key] = now
        while (order.size > capacity) {
            val oldest = order.entries.first().key
            order.remove(oldest)
        }
    }

    fun size(): Int = order.size

    fun ids(): Set<String> = order.keys.toSet()
}

data class TtlPolicy(
    val maxAgeSeconds: Long = ProtocolConstants.MAX_AGE_SECONDS,
    val clockSkewSeconds: Long = ProtocolConstants.CLOCK_SKEW_SECONDS
) {
    fun isExpired(timestamp: Long, now: Long): Boolean {
        if (timestamp > now + clockSkewSeconds) return true
        if (now - timestamp > maxAgeSeconds) return true
        return false
    }

    fun shouldForward(ttl: Int, timestamp: Long, now: Long): Boolean {
        if (ttl <= 0) return false
        if (isExpired(timestamp, now)) return false
        return true
    }
}

data class DutyCycle(
    val advertiseOnMs: Long,
    val advertiseOffMs: Long,
    val scanOnMs: Long,
    val scanOffMs: Long,
    val originRetryMs: Long
)

object BatteryPolicy {
    fun originRetryMs(batteryPercent: Int): Long = when {
        batteryPercent > 50 -> 5L * 60_000
        batteryPercent >= 20 -> 10L * 60_000
        batteryPercent >= 5 -> 25L * 60_000
        else -> 30L * 60_000
    }

    fun dutyCycle(
        role: mx.reddeayuda.protocol.DeviceRole,
        state: mx.reddeayuda.protocol.DeviceState,
        batteryPercent: Int
    ): DutyCycle {
        if (role == mx.reddeayuda.protocol.DeviceRole.RESCUER) {
            return DutyCycle(3_000, 1_000, 5_000, 1_000, originRetryMs(batteryPercent))
        }
        return when (state) {
            mx.reddeayuda.protocol.DeviceState.SOS,
            mx.reddeayuda.protocol.DeviceState.RESCUE_CONTACT -> when {
                batteryPercent > 50 -> DutyCycle(8_000, 2_000, 5_000, 10_000, originRetryMs(batteryPercent))
                batteryPercent >= 20 -> DutyCycle(3_000, 12_000, 3_000, 12_000, originRetryMs(batteryPercent))
                batteryPercent >= 5 -> DutyCycle(2_000, 28_000, 2_000, 28_000, originRetryMs(batteryPercent))
                else -> DutyCycle(2_000, 58_000, 1_000, 59_000, originRetryMs(batteryPercent))
            }
            mx.reddeayuda.protocol.DeviceState.DISASTER,
            mx.reddeayuda.protocol.DeviceState.SAFETY_CHECK ->
                DutyCycle(3_000, 17_000, 3_000, 17_000, originRetryMs(batteryPercent))
            else -> DutyCycle(3_000, 57_000, 3_000, 57_000, originRetryMs(batteryPercent))
        }
    }
}

object Priority {
    fun of(packet: mx.reddeayuda.protocol.EmergencyPacket, localDeviceId: ByteArray): Int {
        val type = packet.type
        if (type == mx.reddeayuda.protocol.PacketType.RESCUE_PING ||
            type == mx.reddeayuda.protocol.PacketType.RESCUE_PING_ALL
        ) {
            val ping = mx.reddeayuda.protocol.RescuePingPayload.parse(packet.payload)
            if (ping != null && ping.isFor(localDeviceId)) return 0
        }
        return when (type) {
            mx.reddeayuda.protocol.PacketType.SOS -> 1
            mx.reddeayuda.protocol.PacketType.ACK -> {
                val ack = mx.reddeayuda.protocol.AckPayload.parse(packet.payload)
                when (ack?.kind) {
                    mx.reddeayuda.protocol.AckKind.MESSAGE_DELIVERED,
                    mx.reddeayuda.protocol.AckKind.RESCUE_CONTACT,
                    mx.reddeayuda.protocol.AckKind.RESCUE_CONFIRMED -> 2
                    else -> 3
                }
            }
            mx.reddeayuda.protocol.PacketType.LOCATION_UPDATE,
            mx.reddeayuda.protocol.PacketType.RESPONSE -> 3
            else -> 4
        }
    }
}
