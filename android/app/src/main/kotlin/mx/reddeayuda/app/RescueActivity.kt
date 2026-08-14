package mx.reddeayuda.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.RescueAction

class RescueActivity : AppCompatActivity() {
    private var selected: EmergencyPacket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rescue)
        SosBanner.bind(this, findViewById(R.id.sosBanner))
        BackBar.bind(this, getString(R.string.rescue_title))

        findViewById<View>(R.id.btnContact).setOnClickListener { ping(RescueAction.CONTACT) }
        findViewById<View>(R.id.btnSound).setOnClickListener { ping(RescueAction.SOUND) }

        RdaApp.instance.uiListeners += { runOnUiThread { refresh() } }
        refresh()
    }

    private fun ping(action: RescueAction) {
        val packet = selected ?: RdaApp.instance.engine.visibleSosPackets().lastOrNull()
        if (packet == null) {
            Toast.makeText(this, "No hay SOS para contactar", Toast.LENGTH_SHORT).show()
            return
        }
        RdaApp.instance.engine.createRescuePing(packet.originDeviceId, action, RdaApp.instance.battery())
        Toast.makeText(this, "${action.name} → ${packet.shortId()}", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        val list = findViewById<LinearLayout>(R.id.list)
        val packets = RdaApp.instance.engine.visibleSosPackets()
        findViewById<View>(R.id.empty).visibility = if (packets.isEmpty()) View.VISIBLE else View.GONE
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        packets.forEach { packet ->
            val row = inflater.inflate(R.layout.item_sos, list, false)
            row.findViewById<TextView>(R.id.title).text = "SOS ${packet.shortId().uppercase()}"
            val lat = packet.latitudeMicrodegrees / 1_000_000.0
            val lon = packet.longitudeMicrodegrees / 1_000_000.0
            row.findViewById<TextView>(R.id.meta).text =
                "Hop ${packet.hopCount}  ·  batería ${packet.battery}%  ·  ${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
            row.setOnClickListener {
                selected = packet
                Toast.makeText(this, "Seleccionado ${packet.shortId()}", Toast.LENGTH_SHORT).show()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            list.addView(row, params)
        }
        if (selected == null) selected = packets.lastOrNull()
    }
}
