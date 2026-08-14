package mx.reddeayuda.app

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.OriginStatus

object SosBanner {
    fun bind(activity: Activity, banner: View) {
        fun refresh() {
            val state = RdaApp.instance.engine.snapshot().state
            val active = state == DeviceState.SOS || state == DeviceState.RESCUE_CONTACT
            banner.visibility = if (active) View.VISIBLE else View.GONE
            if (active) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val ack = when (RdaApp.instance.engine.lastAckKind) {
                AckKind.RESCUE_CONTACT -> "Los rescatistas están cerca"
                AckKind.MESSAGE_DELIVERED -> "Tu mensaje llegó a un rescatista"
                AckKind.MESSAGE_RECEIVED -> "Tu mensaje viaja por la red"
                else -> activity.getString(R.string.sos_notif_text)
            }
            banner.findViewById<TextView>(R.id.bannerAck).text = ack
        }
        banner.findViewById<View>(R.id.bannerImHere).setOnClickListener {
            val app = RdaApp.instance
            app.engine.createResponse(
                OriginStatus.IM_HERE,
                "ESTOY AQUI".toByteArray(Charsets.UTF_8),
                app.gnss.latest(),
                app.battery()
            )
            Toast.makeText(activity, "Enviado: estoy aquí", Toast.LENGTH_SHORT).show()
        }
        banner.findViewById<View>(R.id.bannerImOk).setOnClickListener {
            ContactAlerter.stopSosAlerts()
            SosLocationTracker.stop()
            RdaApp.instance.engine.imOk()
            RdaApp.instance.startMesh(sos = false)
            banner.visibility = View.GONE
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(activity, R.string.im_ok, Toast.LENGTH_SHORT).show()
            refresh()
        }
        RdaApp.instance.uiListeners += { activity.runOnUiThread { refresh() } }
        refresh()
    }
}
