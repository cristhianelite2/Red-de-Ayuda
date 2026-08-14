package mx.reddeayuda.wear

/** Mismos paths que el teléfono (WearProtocol / WearPaths). */
object WearPaths {
    const val SOS = "/rda/sos"
    const val IM_OK = "/rda/im_ok"
    const val VITALS = "/rda/vitals"
    const val LOCATION = "/rda/location"
}

object WearCodec {
    const val SIZE = 15

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

    private fun writeInt(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeShort(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
