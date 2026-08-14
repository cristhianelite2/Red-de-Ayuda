package mx.reddeayuda.app

/**
 * Protocolo teléfono ↔ Wear OS (Data Layer / MessageClient).
 * El reloj dispara SOS; el teléfono es el radio BLE/Wi‑Fi de la mesh.
 */
object WearPaths {
    const val SOS = "/rda/sos"
    const val IM_OK = "/rda/im_ok"
    const val VITALS = "/rda/vitals"
    const val LOCATION = "/rda/location"
    const val STATUS = "/rda/status"
}

/**
 * Binario little-endian compartido reloj ↔ teléfono.
 * lat i32, lon i32, accuracy u16, battery u8, hr u8, spo2 u8, tempCenti u16
 */
object WearPayload {
    const val SIZE = 4 + 4 + 2 + 1 + 1 + 1 + 2 // 15

    fun encode(
        latMicro: Int,
        lonMicro: Int,
        accuracyM: Int,
        battery: Int,
        heartRate: Int,
        spo2: Int,
        tempCenti: Int = 0
    ): ByteArray {
        val out = ByteArray(SIZE)
        writeInt(out, 0, latMicro)
        writeInt(out, 4, lonMicro)
        writeShort(out, 8, accuracyM.coerceIn(0, 65535))
        out[10] = battery.coerceIn(0, 100).toByte()
        out[11] = heartRate.coerceIn(0, 250).toByte()
        out[12] = spo2.coerceIn(0, 100).toByte()
        writeShort(out, 13, tempCenti.coerceIn(0, 5000))
        return out
    }

    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < SIZE) return null
        return Decoded(
            latMicro = readInt(bytes, 0),
            lonMicro = readInt(bytes, 4),
            accuracyM = readShort(bytes, 8),
            battery = bytes[10].toInt() and 0xFF,
            heartRate = bytes[11].toInt() and 0xFF,
            spo2 = bytes[12].toInt() and 0xFF,
            tempCenti = readShort(bytes, 13)
        )
    }

    data class Decoded(
        val latMicro: Int,
        val lonMicro: Int,
        val accuracyM: Int,
        val battery: Int,
        val heartRate: Int,
        val spo2: Int,
        val tempCenti: Int
    )

    private fun writeInt(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun readInt(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeShort(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun readShort(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
}
