package mx.reddeayuda.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mientras el SOS del reloj está activo: envía ubicación + vitales al teléfono cada 5 min.
 */
class WearSosService : Service() {
    private val sensors by lazy { WearSensors(this) }
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var tick: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (running.compareAndSet(false, true)) {
                    sensors.start()
                    startFg()
                    sendNow(WearPaths.SOS)
                    schedule()
                } else {
                    sendNow(WearPaths.VITALS)
                }
            }
        }
        return START_STICKY
    }

    private fun schedule() {
        val task = object : Runnable {
            override fun run() {
                if (!running.get()) return
                sendNow(WearPaths.VITALS)
                main.postDelayed(this, INTERVAL_MS)
            }
        }
        tick = task
        main.postDelayed(task, INTERVAL_MS)
    }

    private fun sendNow(path: String) {
        sensors.requestFreshLocation { loc ->
            val payload = sensors.buildPayload(loc)
            WearPhoneClient.send(this, path, payload) { /* log via UI optional */ }
        }
    }

    private fun stopInternal() {
        running.set(false)
        tick?.let { main.removeCallbacks(it) }
        tick = null
        sensors.stop()
        WearPhoneClient.send(this, WearPaths.IM_OK, ByteArray(0)) { }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startFg() {
        ensureChannel()
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_wear_sos)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(ID, n)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        if (running.get()) stopInternal()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "mx.reddeayuda.wear.STOP"
        private const val CHANNEL = "rda_wear_sos"
        private const val ID = 42
        private const val INTERVAL_MS = 5 * 60 * 1000L

        fun start(context: android.content.Context) {
            val i = Intent(context, WearSosService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, WearSosService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
