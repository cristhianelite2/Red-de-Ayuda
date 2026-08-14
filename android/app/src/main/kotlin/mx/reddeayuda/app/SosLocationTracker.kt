package mx.reddeayuda.app

import android.os.Handler
import android.os.Looper
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.GeoFix
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mientras el SOS está activo: GPS en vivo + LOCATION_UPDATE en la mesh (~cada 30 s).
 */
object SosLocationTracker {
    private const val MESH_INTERVAL_MS = 30_000L
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var tick: Runnable? = null
    private var lastPublished: GeoFix = GeoFix()

    private val onFix: (GeoFix) -> Unit = { fix ->
        if (running.get() && !fix.isUnknown) {
            val moved = distanceMeters(lastPublished, fix) >= 15.0
            if (moved || lastPublished.isUnknown) {
                publish(fix)
            }
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val app = RdaApp.instance
        app.gnss.acquire()
        app.gnss.addListener(onFix)
        publish(app.gnss.latest())
        val task = object : Runnable {
            override fun run() {
                if (!running.get()) return
                val state = app.engine.snapshot().state
                if (state != DeviceState.SOS && state != DeviceState.RESCUE_CONTACT) {
                    stop()
                    return
                }
                publish(app.gnss.latest())
                main.postDelayed(this, MESH_INTERVAL_MS)
            }
        }
        tick = task
        main.postDelayed(task, MESH_INTERVAL_MS)
        app.push("GPS en vivo activo (LOCATION_UPDATE cada 30 s)")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        tick?.let { main.removeCallbacks(it) }
        tick = null
        try {
            RdaApp.instance.gnss.removeListener(onFix)
            RdaApp.instance.gnss.release()
        } catch (_: Exception) {
        }
        lastPublished = GeoFix()
    }

    private fun publish(fix: GeoFix) {
        if (fix.isUnknown) return
        val app = RdaApp.instance
        val packet = app.engine.createLocationUpdate(fix, app.battery())
        if (packet != null) {
            lastPublished = fix
            app.push("Ubicación mesh actualizada ±${fix.accuracyMeters} m")
        }
    }

    private fun distanceMeters(a: GeoFix, b: GeoFix): Double {
        if (a.isUnknown || b.isUnknown) return Double.MAX_VALUE
        val out = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitudeMicrodegrees / 1_000_000.0,
            a.longitudeMicrodegrees / 1_000_000.0,
            b.latitudeMicrodegrees / 1_000_000.0,
            b.longitudeMicrodegrees / 1_000_000.0,
            out
        )
        return out[0].toDouble()
    }
}
