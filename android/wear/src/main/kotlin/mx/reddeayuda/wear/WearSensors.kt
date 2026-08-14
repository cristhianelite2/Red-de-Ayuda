package mx.reddeayuda.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Lee ubicación del reloj + frecuencia cardíaca (si el hardware lo permite).
 * SpO2 no es estándar en SensorManager; se deja 0 salvo que el OEM lo exponga.
 */
class WearSensors(private val context: Context) : SensorEventListener {
    @Volatile
    var heartRate: Int = 0
        private set

    @Volatile
    var spo2: Int = 0
        private set

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var hrSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    fun start() {
        hrSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun batteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 50
    }

    fun lastKnownLocation(): Triple<Int, Int, Int> {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
            try {
                val loc = lm.getLastKnownLocation(p) ?: return@forEach
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            } catch (_: SecurityException) {
            }
        }
        val loc = best ?: return Triple(0, 0, 65535)
        return Triple(
            (loc.latitude * 1_000_000.0).toInt(),
            (loc.longitude * 1_000_000.0).toInt(),
            loc.accuracy.toInt().coerceIn(0, 65535)
        )
    }

    fun requestFreshLocation(onResult: (Triple<Int, Int, Int>) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onResult(lastKnownLocation())
            return
        }
        val cts = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult(
                        Triple(
                            (loc.latitude * 1_000_000.0).toInt(),
                            (loc.longitude * 1_000_000.0).toInt(),
                            loc.accuracy.toInt().coerceIn(0, 65535)
                        )
                    )
                } else {
                    onResult(lastKnownLocation())
                }
            }
            .addOnFailureListener { onResult(lastKnownLocation()) }
    }

    fun buildPayload(loc: Triple<Int, Int, Int> = lastKnownLocation()): ByteArray =
        WearCodec.encode(
            latMicro = loc.first,
            lonMicro = loc.second,
            accuracyM = loc.third,
            battery = batteryPercent(),
            heartRate = heartRate,
            spo2 = spo2
        )

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val v = event.values.firstOrNull()?.toInt() ?: return
            if (v > 0) heartRate = v.coerceIn(0, 250)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
