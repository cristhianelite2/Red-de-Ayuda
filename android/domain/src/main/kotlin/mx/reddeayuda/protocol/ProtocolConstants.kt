package mx.reddeayuda.protocol

object ProtocolConstants {
    const val VERSION: Int = 1
    const val HEADER_SIZE: Int = 48
    const val AUTH_SIZE: Int = 16
    const val MESSAGE_ID_SIZE: Int = 16
    const val DEVICE_ID_SIZE: Int = 8
    const val MAX_PAYLOAD: Int = 180
    const val DEFAULT_TTL: Int = 20
    const val ACK_TTL: Int = 10
    const val PING_TTL: Int = 10
    const val MAX_AGE_SECONDS: Long = 86_400L
    const val CLOCK_SKEW_SECONDS: Long = 300L
    const val UNKNOWN_ACCURACY: Int = 65_535
    const val UNKNOWN_BATTERY: Int = 255
    const val QUEUE_CAPACITY: Int = 64
    const val SEEN_CACHE_CAPACITY: Int = 1024
    const val FLAG_BLE: Int = 1 shl 0
    const val FLAG_WIFI: Int = 1 shl 1
    const val FLAG_UWB: Int = 1 shl 2
    const val FLAG_WATCH: Int = 1 shl 3
    const val FLAG_GATEWAY: Int = 1 shl 4
    const val FLAG_RESCUE_NODE: Int = 1 shl 5
    const val FLAG_ENCRYPTED: Int = 1 shl 6

    const val SERVICE_UUID: String = "a1e00001-5244-4159-0001-726564617975"
    const val META_UUID: String = "a1e00002-5244-4159-0001-726564617975"
    const val INBOX_UUID: String = "a1e00003-5244-4159-0001-726564617975"
    const val OUTBOX_UUID: String = "a1e00004-5244-4159-0001-726564617975"
    const val SEEN_UUID: String = "a1e00005-5244-4159-0001-726564617975"
    const val MANUFACTURER_ID: Int = 0xFFFF
    const val BEACON_MAGIC_0: Byte = 0x52 // R
    const val BEACON_MAGIC_1: Byte = 0x41 // A
}

enum class PacketType(val code: Int) {
    SOS(1),
    ACK(2),
    RESCUE_PING(3),
    RESCUE_PING_ALL(4),
    SAFETY_CHECK(5),
    RESPONSE(6),
    LOCATION_UPDATE(7);

    companion object {
        fun from(code: Int): PacketType? = entries.find { it.code == code }
    }
}

enum class EventType(val code: Int) {
    UNKNOWN(0),
    USER_INITIATED(1),
    EARTHQUAKE(2),
    COLLAPSE(3),
    FLOOD(4),
    OTHER_DISASTER(5);

    companion object {
        fun from(code: Int): EventType? = entries.find { it.code == code }
    }
}

enum class OriginStatus(val code: Int) {
    UNKNOWN(0),
    NEED_HELP(1),
    IM_HERE(2),
    CANNOT_RESPOND(3),
    RESOLVED(4);

    companion object {
        fun from(code: Int): OriginStatus? = entries.find { it.code == code }
    }
}

enum class AckKind(val code: Int) {
    MESSAGE_RECEIVED(1),
    MESSAGE_DELIVERED(2),
    RESCUE_CONTACT(3),
    RESCUE_CONFIRMED(4);

    companion object {
        fun from(code: Int): AckKind? = entries.find { it.code == code }
    }
}

enum class RescueAction(val code: Int) {
    CONTACT(1),
    SOUND(2),
    VIBRATE(3),
    LOCATION_REQUEST(4),
    SCREEN(5);

    companion object {
        fun from(code: Int): RescueAction? = entries.find { it.code == code }
    }
}

enum class DeviceRole(val code: Int) {
    CIVILIAN(0),
    RESCUER(1);

    companion object {
        fun from(code: Int): DeviceRole = entries.find { it.code == code } ?: CIVILIAN
    }
}

enum class DeviceState(val code: Int) {
    NORMAL(0),
    DISASTER(1),
    SAFETY_CHECK(2),
    SOS(3),
    RESCUE_CONTACT(4),
    RESOLVED(5);

    companion object {
        fun from(code: Int): DeviceState = entries.find { it.code == code } ?: NORMAL
    }
}

enum class StateEvent {
    USER_NEED_HELP,
    USER_IM_OK,
    USER_RESOLVE,
    ENTER_RESCUE_ROLE,
    LEAVE_RESCUE_ROLE,
    PACKET_RESCUE_PING,
    DISASTER_ALERT,
    DISASTER_CLEAR,
    SAFETY_TIMEOUT,
    SEVERE_NO_RESPONSE,
    START_SAFETY_CHECK
}
