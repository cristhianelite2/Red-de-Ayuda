package mx.reddeayuda.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import mx.reddeayuda.platform.GeoMath
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.ProtocolConstants
import mx.reddeayuda.protocol.RescueAction
import mx.reddeayuda.protocol.VitalsPayload

class RescueActivity : AppCompatActivity() {
    private var selected: EmergencyPacket? = null
    private val onFix: (GeoFix) -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rescue)
        SosBanner.bind(this, findViewById(R.id.sosBanner))
        BackBar.bind(this, getString(R.string.rescue_title))

        findViewById<View>(R.id.btnContact).setOnClickListener { ping(RescueAction.CONTACT) }
        findViewById<View>(R.id.btnSound).setOnClickListener { ping(RescueAction.SOUND) }
        findViewById<View>(R.id.btnFinder).setOnClickListener { openFinder() }

        RdaApp.instance.uiListeners += { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        RdaApp.instance.gnss.acquire()
        RdaApp.instance.gnss.addListener(onFix)
    }

    override fun onStop() {
        RdaApp.instance.gnss.removeListener(onFix)
        RdaApp.instance.gnss.release()
        super.onStop()
    }

    private fun openFinder() {
        val packet = selected ?: RdaApp.instance.engine.visibleSosPackets().lastOrNull()
        if (packet == null) {
            Toast.makeText(this, R.string.finder_missing, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, FinderActivity::class.java)
                .putExtra(FinderActivity.EXTRA_ORIGIN, packet.originHex())
        )
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
        val me = RdaApp.instance.gnss.latest()
        packets.forEach { packet ->
            val row = inflater.inflate(R.layout.item_sos, list, false)
            row.findViewById<TextView>(R.id.title).text = "SOS ${packet.shortId().uppercase()}"
            val victim = GeoFix(
                packet.latitudeMicrodegrees,
                packet.longitudeMicrodegrees,
                packet.accuracyMeters
            )
            val dist = GeoMath.formatDistance(GeoMath.distanceMeters(me, victim))
            val lat = packet.latitudeMicrodegrees / 1_000_000.0
            val lon = packet.longitudeMicrodegrees / 1_000_000.0
            val vitals = VitalsPayload.parseLoose(packet.payload)
            val watch = (packet.flags and ProtocolConstants.FLAG_WATCH) != 0
            row.findViewById<TextView>(R.id.meta).text = buildString {
                append("$dist  ·  hop ${packet.hopCount}  ·  bat ${packet.battery}%")
                if (watch) append("  ·  reloj")
                append("\n")
                append(
                    if (victim.isUnknown) "Ubicación pendiente"
                    else "${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
                )
                if (vitals != null && vitals.hasAny) {
                    append("\n${vitals.toSummary()}")
                }
            }
            row.setOnClickListener {
                selected = packet
                Toast.makeText(this, "Seleccionado ${packet.shortId()}", Toast.LENGTH_SHORT).show()
            }
            row.setOnLongClickListener {
                selected = packet
                openFinder()
                true
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            list.addView(row, params)
        }
        if (selected == null || packets.none { it.originHex() == selected?.originHex() }) {
            selected = packets.lastOrNull()
        }
    }
}
