package mx.reddeayuda.app

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.ProtocolConstants
import mx.reddeayuda.protocol.VitalsPayload

/**
 * Recibe SOS / vitales / ubicación desde el smartwatch Wear OS.
 * El teléfono activa la mesh y reenvía a la red + SMS de contactos.
 */
class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val app = try {
            RdaApp.instance
        } catch (_: Exception) {
            return
        }
        when (event.path) {
            WearPaths.SOS -> handleSos(app, event.data)
            WearPaths.IM_OK -> handleImOk(app)
            WearPaths.VITALS, WearPaths.LOCATION -> handleVitalsOrLocation(app, event.data)
            else -> app.push("Wear: ruta desconocida ${event.path}")
        }
    }

    private fun handleSos(app: RdaApp, data: ByteArray) {
        val decoded = WearPayload.decode(data)
        val fix = if (decoded != null) {
            GeoFix(
                latitudeMicrodegrees = decoded.latMicro,
                longitudeMicrodegrees = decoded.lonMicro,
                accuracyMeters = decoded.accuracyM.coerceIn(0, ProtocolConstants.UNKNOWN_ACCURACY)
            )
        } else {
            app.gnss.latest()
        }
        val battery = decoded?.battery?.takeIf { it in 0..100 } ?: app.battery()
        val vitals = VitalsPayload(
            heartRateBpm = decoded?.heartRate ?: 0,
            spo2Percent = decoded?.spo2 ?: 0,
            skinTempCenti = decoded?.tempCenti ?: 0
        )
        val payload = if (vitals.hasAny) vitals.toBytes() else ByteArray(0)

        app.watchSosActive = true
        app.startMesh(sos = true)
        val state = app.engine.snapshot().state
        if (state != DeviceState.SOS && state != DeviceState.RESCUE_CONTACT) {
            app.engine.createEmergency(fix, battery, payload = payload, fromWatch = true)
        } else {
            app.engine.createLocationUpdate(fix, battery, payload, fromWatch = true)
        }
        SosLocationTracker.start()
        ContactAlerter.startSosAlerts(app)
        if (vitals.hasAny) {
            app.engine.publishVitals(vitals, fix, battery, fromWatch = true)
        }
        app.push(
            "SOS desde reloj" +
                if (vitals.hasAny) " · ${vitals.toSummary()}" else ""
        )
        val meshIntent = Intent(app, MeshService::class.java).putExtra(MeshService.EXTRA_SOS, true)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(meshIntent)
        } else {
            app.startService(meshIntent)
        }
    }

    private fun handleImOk(app: RdaApp) {
        ContactAlerter.stopSosAlerts()
        SosLocationTracker.stop()
        app.watchSosActive = false
        app.engine.imOk()
        app.startMesh(sos = false)
        app.push("Estoy bien — desde el reloj")
        val meshIntent = Intent(app, MeshService::class.java).putExtra(MeshService.EXTRA_SOS, false)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(meshIntent)
        } else {
            app.startService(meshIntent)
        }
    }

    private fun handleVitalsOrLocation(app: RdaApp, data: ByteArray) {
        val decoded = WearPayload.decode(data) ?: return
        val state = app.engine.snapshot().state
        if (state != DeviceState.SOS && state != DeviceState.RESCUE_CONTACT) {
            app.push("Vitales del reloj ignorados (SOS inactivo)")
            return
        }
        val fix = GeoFix(
            latitudeMicrodegrees = decoded.latMicro,
            longitudeMicrodegrees = decoded.lonMicro,
            accuracyMeters = decoded.accuracyM.coerceIn(0, ProtocolConstants.UNKNOWN_ACCURACY)
        )
        val vitals = VitalsPayload(decoded.heartRate, decoded.spo2, decoded.tempCenti)
        val battery = decoded.battery.coerceIn(0, 100)
        if (vitals.hasAny) {
            app.engine.publishVitals(vitals, fix, battery, fromWatch = true)
            app.push("Vitales reloj: ${vitals.toSummary()}")
        } else if (!fix.isUnknown) {
            app.engine.createLocationUpdate(fix, battery, fromWatch = true)
            app.push("Ubicación reloj actualizada")
        }
    }
}
