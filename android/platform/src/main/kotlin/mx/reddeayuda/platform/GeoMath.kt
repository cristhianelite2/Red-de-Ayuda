package mx.reddeayuda.platform

import android.location.Location
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.ProtocolConstants

object GeoMath {
    fun distanceMeters(a: GeoFix, b: GeoFix): Float? {
        if (a.isUnknown || b.isUnknown) return null
        if (a.accuracyMeters == ProtocolConstants.UNKNOWN_ACCURACY &&
            a.latitudeMicrodegrees == 0 && a.longitudeMicrodegrees == 0
        ) return null
        val out = FloatArray(1)
        Location.distanceBetween(
            a.latitudeMicrodegrees / 1_000_000.0,
            a.longitudeMicrodegrees / 1_000_000.0,
            b.latitudeMicrodegrees / 1_000_000.0,
            b.longitudeMicrodegrees / 1_000_000.0,
            out
        )
        return out[0]
    }

    fun formatDistance(meters: Float?): String {
        if (meters == null) return "distancia desconocida"
        return if (meters < 1000f) {
            "${meters.toInt()} m"
        } else {
            String.format("%.1f km", meters / 1000f)
        }
    }

    fun lat(fix: GeoFix): Double = fix.latitudeMicrodegrees / 1_000_000.0
    fun lon(fix: GeoFix): Double = fix.longitudeMicrodegrees / 1_000_000.0

    fun toFix(lat: Double, lon: Double, accuracy: Int = 10): GeoFix =
        GeoFix(
            latitudeMicrodegrees = (lat * 1_000_000.0).toInt(),
            longitudeMicrodegrees = (lon * 1_000_000.0).toInt(),
            accuracyMeters = accuracy
        )
}
