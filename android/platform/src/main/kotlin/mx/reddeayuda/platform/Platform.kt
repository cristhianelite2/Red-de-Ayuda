package mx.reddeayuda.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.ProtocolConstants
import java.security.SecureRandom

object DeviceIdentity {
    private const val PREF = "rda"
    private const val KEY = "device_id"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (existing != null && existing.length == 16) {
            return mx.reddeayuda.protocol.Hex.decode(existing)
        }
        val id = ByteArray(8)
        SecureRandom().nextBytes(id)
        prefs.edit().putString(KEY, mx.reddeayuda.protocol.Hex.encode(id)).apply()
        return id
    }
}

object PermissionCatalog {
    fun required(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun missing(context: Context): Array<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}

class GnssProvider(private val context: Context) {
    fun lastKnown(): GeoFix {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: android.location.Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            } catch (_: SecurityException) {
            }
        }
        val loc = best ?: return GeoFix()
        return GeoFix(
            latitudeMicrodegrees = (loc.latitude * 1_000_000.0).toInt(),
            longitudeMicrodegrees = (loc.longitude * 1_000_000.0).toInt(),
            accuracyMeters = loc.accuracy.toInt().coerceIn(0, ProtocolConstants.UNKNOWN_ACCURACY)
        )
    }
}

object BatteryReader {
    fun percent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val p = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (p in 0..100) p else 50
    }
}

object OemBattery {
    fun isIgnoringOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

object BluetoothHelper {
    fun adapter(context: Context): android.bluetooth.BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        return manager?.adapter
    }

    fun isEnabled(context: Context): Boolean {
        return try {
            adapter(context)?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    fun requestEnableIntent(): Intent =
        Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
}
