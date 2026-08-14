package mx.reddeayuda.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.GeoFix
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.app.Activity

/**
 * Avisa a la Red de contactos por SMS automático (saldo)
 * cada 5 minutos mientras el SOS siga activo.
 */
object ContactAlerter {
    private const val INTERVAL_MS = 5 * 60 * 1000L
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var tick: Runnable? = null
    private var activityRef: WeakReference<Activity>? = null

    fun startSosAlerts(context: Context) {
        val appCtx = context.applicationContext
        if (context is Activity) {
            activityRef = WeakReference(context)
        }
        if (!running.compareAndSet(false, true)) {
            dispatch(appCtx, first = false)
            return
        }
        dispatch(appCtx, first = true)
        val task = object : Runnable {
            override fun run() {
                if (!running.get()) return
                val state = RdaApp.instance.engine.snapshot().state
                val sos = state == DeviceState.SOS || state == DeviceState.RESCUE_CONTACT
                if (!sos) {
                    stopSosAlerts()
                    return
                }
                dispatch(appCtx, first = false)
                main.postDelayed(this, INTERVAL_MS)
            }
        }
        tick = task
        main.postDelayed(task, INTERVAL_MS)
    }

    fun stopSosAlerts() {
        running.set(false)
        tick?.let { main.removeCallbacks(it) }
        tick = null
        activityRef = null
    }

    private fun dispatch(context: Context, first: Boolean) {
        io.execute {
            val contacts = ContactStore.list(context)
            if (contacts.isEmpty()) {
                RdaApp.instance.push("SOS: configura tu Red de contactos (máx. 5)")
                return@execute
            }
            val fix = RdaApp.instance.gnss.latest()
            val body = buildMessage(fix, first)
            var smsOk = 0
            contacts.forEach { c ->
                if (sendSms(context, c.phone, body)) smsOk++
            }
            RdaApp.instance.push("SMS enviado: $smsOk/${contacts.size} contactos")
            main.post {
                val act = activityRef?.get()
                if (act != null && !act.isFinishing) {
                    Toast.makeText(
                        act,
                        act.getString(R.string.sms_sent_toast, smsOk, contacts.size),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun buildMessage(fix: GeoFix, first: Boolean): String {
        val prefix = if (first) {
            "SOS Red de Ayuda: NECESITO AYUDA."
        } else {
            "SOS Red de Ayuda (actualización cada 5 min): sigo necesitando ayuda."
        }
        val maps = if (fix.isUnknown) {
            "Ubicación aún no disponible."
        } else {
            val lat = fix.latitudeMicrodegrees / 1_000_000.0
            val lon = fix.longitudeMicrodegrees / 1_000_000.0
            "Mi ubicación: https://maps.google.com/?q=$lat,$lon"
        }
        return "$prefix $maps Enviado por la app Red de Ayuda."
    }

    private fun sendSms(context: Context, phone: String, body: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(phone, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
