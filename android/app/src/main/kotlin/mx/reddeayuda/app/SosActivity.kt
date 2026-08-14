package mx.reddeayuda.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.OriginStatus

class SosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        RdaApp.instance.startMesh(sos = true)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImHere).setOnClickListener {
            val app = RdaApp.instance
            app.engine.createResponse(
                OriginStatus.IM_HERE,
                "ESTOY AQUI".toByteArray(Charsets.UTF_8),
                app.gnss.latest(),
                app.battery()
            )
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImOk).setOnClickListener {
            ContactAlerter.stopSosAlerts()
            SosLocationTracker.stop()
            RdaApp.instance.watchSosActive = false
            RdaApp.instance.engine.imOk()
            RdaApp.instance.startMesh(sos = false)
            finish()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        RdaApp.instance.uiListeners += { runOnUiThread { refresh() } }
        refresh()
    }

    private fun refresh() {
        val app = RdaApp.instance
        val ack = when (app.engine.lastAckKind) {
            AckKind.RESCUE_CONTACT -> "Los rescatistas están cerca"
            AckKind.MESSAGE_DELIVERED -> "Tu mensaje llegó a un rescatista"
            AckKind.MESSAGE_RECEIVED -> "Tu mensaje viaja por la red"
            AckKind.RESCUE_CONFIRMED -> "Marcado como localizado"
            null -> "Buscando nodos cercanos por Bluetooth…"
        }
        findViewById<TextView>(R.id.txtAck).text = ack
        val fix = app.gnss.latest()
        findViewById<TextView>(R.id.txtMeta).text = if (fix.isUnknown) {
            "Batería ${app.battery()}%  ·  ubicación no disponible"
        } else {
            "Batería ${app.battery()}%  ·  ${"%.5f".format(fix.latitudeMicrodegrees / 1_000_000.0)}, ${"%.5f".format(fix.longitudeMicrodegrees / 1_000_000.0)}"
        }
    }
}
