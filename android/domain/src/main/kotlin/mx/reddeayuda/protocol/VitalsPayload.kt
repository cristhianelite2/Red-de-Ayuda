package mx.reddeayuda.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Signos vitales compactos (reloj / sensores).
 * Magic "VT" + version 1.
 */
data class VitalsPayload(
    val heartRateBpm: Int = 0,
    val spo2Percent: Int = 0,
    /** Temperatura cutánea × 100 (ej. 3650 = 36.50 °C). 0 = desconocida. */
    val skinTempCenti: Int = 0
) {
    val hasAny: Boolean
        get() = heartRateBpm > 0 || spo2Percent > 0 || skinTempCenti > 0

    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC0)
        buf.put(MAGIC1)
        buf.put(VERSION)
        buf.put(heartRateBpm.coerceIn(0, 250).toByte())
        buf.put(spo2Percent.coerceIn(0, 100).toByte())
        buf.putShort(skinTempCenti.coerceIn(0, 5000).toShort())
        return buf.array()
    }

    fun summaryUtf8(): ByteArray = toSummary().toByteArray(Charsets.UTF_8)

    fun toSummary(): String = buildString {
        append("VITALES")
        if (heartRateBpm > 0) append(" FC=${heartRateBpm}lpm")
        if (spo2Percent > 0) append(" SpO2=${spo2Percent}%")
        if (skinTempCenti > 0) {
            append(" T=${"%.1f".format(skinTempCenti / 100.0)}C")
        }
        if (!hasAny) append(" (sin sensor)")
    }

    companion object {
        private const val MAGIC0: Byte = 0x56 // V
        private const val MAGIC1: Byte = 0x54 // T
        private const val VERSION: Byte = 1
        const val SIZE: Int = 7

        fun parse(bytes: ByteArray): VitalsPayload? {
            if (bytes.size < SIZE) return null
            if (bytes[0] != MAGIC0 || bytes[1] != MAGIC1) return null
            if (bytes[2] != VERSION) return null
            val buf = ByteBuffer.wrap(bytes, 0, SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(3)
            val hr = buf.get().toInt() and 0xFF
            val spo2 = buf.get().toInt() and 0xFF
            val temp = buf.short.toInt() and 0xFFFF
            return VitalsPayload(hr, spo2, temp)
        }

        fun parseLoose(bytes: ByteArray): VitalsPayload? {
            parse(bytes)?.let { return it }
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
            if (!text.startsWith("VITALES")) return null
            val hr = Regex("""FC=(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val spo2 = Regex("""SpO2=(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return VitalsPayload(hr, spo2, 0)
        }
    }
}
