package mx.reddeayuda.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class MeshService : Service() {
    private var sosMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()

        when (intent?.action) {
            ACTION_IM_OK -> {
                ContactAlerter.stopSosAlerts()
                SosLocationTracker.stop()
                RdaApp.instance.watchSosActive = false
                RdaApp.instance.engine.imOk()
                sosMode = false
                showForeground(sos = false)
                RdaApp.instance.push("Estoy bien — SOS desactivado desde la notificación")
                return START_STICKY
            }
            ACTION_CLOSE_APP -> {
                closeApplication()
                return START_NOT_STICKY
            }
        }

        if (intent?.hasExtra(EXTRA_SOS) == true) {
            sosMode = intent.getBooleanExtra(EXTRA_SOS, false)
        }
        showForeground(sosMode)
        return START_STICKY
    }

    private fun closeApplication() {
        ContactAlerter.stopSosAlerts()
        SosLocationTracker.stop()
        try {
            RdaApp.instance.stopMesh()
        } catch (_: Exception) {
        }
        RdaApp.instance.push("Aplicación cerrada desde la notificación")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(ID)
        stopSelf()
        val exit = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(MainActivity.EXTRA_EXIT, true)
        }
        startActivity(exit)
    }

    private fun showForeground(sos: Boolean) {
        val notification = if (sos) sosNotification() else meshNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, notification)
        }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MESH, "Red de Ayuda", NotificationManager.IMPORTANCE_LOW)
        )
        val sosCh = NotificationChannel(
            CH_SOS,
            "Pidiendo ayuda",
            NotificationManager.IMPORTANCE_HIGH
        )
        sosCh.description = "Aviso persistente mientras tu SOS está activo"
        nm.createNotificationChannel(sosCh)
    }

    private fun meshNotification(): Notification {
        val open = activityPending(Intent(this, MainActivity::class.java), REQ_OPEN)
        val close = servicePending(
            Intent(this, MeshService::class.java).setAction(ACTION_CLOSE_APP),
            REQ_CLOSE
        )
        val large = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        return NotificationCompat.Builder(this, CH_MESH)
            .setContentTitle(getString(R.string.mesh_notif_title))
            .setContentText(getString(R.string.mesh_notif_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(getString(R.string.mesh_notif_text))
            )
            .setSmallIcon(R.drawable.ic_stat_rda)
            .setLargeIcon(large)
            .setColor(ContextCompat.getColor(this, R.color.navy))
            .setContentIntent(open)
            .addAction(0, getString(R.string.close_app), close)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun sosNotification(): Notification {
        val open = activityPending(Intent(this, MainActivity::class.java), REQ_OPEN)
        val imOk = servicePending(
            Intent(this, MeshService::class.java).setAction(ACTION_IM_OK),
            REQ_IM_OK
        )
        val large = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        return NotificationCompat.Builder(this, CH_SOS)
            .setContentTitle(getString(R.string.sos_notif_title))
            .setContentText(getString(R.string.sos_notif_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(getString(R.string.sos_notif_text))
            )
            .setSmallIcon(R.drawable.ic_stat_rda)
            .setLargeIcon(large)
            .setColor(ContextCompat.getColor(this, R.color.sos))
            .setContentIntent(open)
            .addAction(0, getString(R.string.im_ok), imOk)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun activityPending(intent: Intent, requestCode: Int): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePending(intent: Intent, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_SOS = "sos"
        const val ACTION_IM_OK = "mx.reddeayuda.app.action.IM_OK"
        const val ACTION_CLOSE_APP = "mx.reddeayuda.app.action.CLOSE_APP"
        const val ID = 42
        const val CH_MESH = "rda_mesh"
        const val CH_SOS = "rda_sos"
        private const val REQ_OPEN = 1
        private const val REQ_IM_OK = 2
        private const val REQ_CLOSE = 3
    }
}
