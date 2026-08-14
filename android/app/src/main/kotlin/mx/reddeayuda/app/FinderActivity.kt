package mx.reddeayuda.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import mx.reddeayuda.platform.GeoMath
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.RescueAction

/**
 * Buscador solo para rescatistas: distancia en vivo + mapa (Google Maps si hay API key,
 * si no OpenStreetMap en WebView).
 */
class FinderActivity : AppCompatActivity() {

    private var originHex: String = ""
    private var googleMap: GoogleMap? = null
    private var meMarker: Marker? = null
    private var victimMarker: Marker? = null
    private var line: Polyline? = null
    private var mapReady = false
    private var lastOsmKey: String = ""

    private val onFix: (GeoFix) -> Unit = { runOnUiThread { refreshUi() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finder)
        SosBanner.bind(this, findViewById(R.id.sosBanner))
        BackBar.bind(this, getString(R.string.finder_title))

        originHex = intent.getStringExtra(EXTRA_ORIGIN) ?: ""
        if (originHex.isBlank()) {
            Toast.makeText(this, R.string.finder_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<MaterialButton>(R.id.btnOpenMaps).setOnClickListener { openExternalMaps() }
        findViewById<MaterialButton>(R.id.btnContact).setOnClickListener {
            val packet = RdaApp.instance.engine.findVisibleByOrigin(originHex)
            if (packet == null) {
                Toast.makeText(this, R.string.finder_missing, Toast.LENGTH_SHORT).show()
            } else {
                RdaApp.instance.engine.createRescuePing(
                    packet.originDeviceId,
                    RescueAction.CONTACT,
                    RdaApp.instance.battery()
                )
                Toast.makeText(this, R.string.contact, Toast.LENGTH_SHORT).show()
            }
        }

        setupMap()
        RdaApp.instance.uiListeners += { runOnUiThread { refreshUi() } }
        refreshUi()
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

    private fun hasMapsKey(): Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()

    private fun setupMap() {
        val web = findViewById<WebView>(R.id.webMap)
        val mapContainer = findViewById<View>(R.id.mapContainer)
        if (hasMapsKey()) {
            web.visibility = View.GONE
            mapContainer.visibility = View.VISIBLE
            val frag = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
            frag?.getMapAsync { map ->
                googleMap = map
                mapReady = true
                map.uiSettings.isZoomControlsEnabled = true
                @SuppressLint("MissingPermission")
                try {
                    map.isMyLocationEnabled = true
                } catch (_: SecurityException) {
                }
                refreshUi()
            }
        } else {
            mapContainer.visibility = View.GONE
            web.visibility = View.VISIBLE
            web.settings.javaScriptEnabled = true
            web.webViewClient = WebViewClient()
            findViewById<TextView>(R.id.txtMapHint).text = getString(R.string.finder_osm_hint)
        }
    }

    private fun refreshUi() {
        val packet = RdaApp.instance.engine.findVisibleByOrigin(originHex) ?: return
        val victim = GeoFix(
            packet.latitudeMicrodegrees,
            packet.longitudeMicrodegrees,
            packet.accuracyMeters
        )
        val me = RdaApp.instance.gnss.latest()
        val dist = GeoMath.distanceMeters(me, victim)

        findViewById<TextView>(R.id.txtVictimId).text =
            getString(R.string.finder_victim, packet.shortId().uppercase())
        findViewById<TextView>(R.id.txtDistance).text = GeoMath.formatDistance(dist)
        findViewById<TextView>(R.id.txtDetail).text = buildString {
            append("Batería víctima ${packet.battery}%")
            if (!victim.isUnknown) {
                append("  ·  ±${packet.accuracyMeters} m")
                append("\n${"%.5f".format(GeoMath.lat(victim))}, ${"%.5f".format(GeoMath.lon(victim))}")
            } else {
                append("\nUbicación de la víctima aún no disponible")
            }
            if (!me.isUnknown) {
                append("\nTu posición: ${"%.5f".format(GeoMath.lat(me))}, ${"%.5f".format(GeoMath.lon(me))}")
            }
        }

        if (hasMapsKey() && mapReady && !victim.isUnknown) {
            updateGoogleMap(me, victim)
        } else if (!hasMapsKey() && !victim.isUnknown) {
            updateOsm(me, victim)
        }
    }

    private fun updateGoogleMap(me: GeoFix, victim: GeoFix) {
        val map = googleMap ?: return
        val v = LatLng(GeoMath.lat(victim), GeoMath.lon(victim))
        if (victimMarker == null) {
            victimMarker = map.addMarker(
                MarkerOptions()
                    .position(v)
                    .title(getString(R.string.finder_marker_victim))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            victimMarker?.position = v
        }
        if (!me.isUnknown) {
            val m = LatLng(GeoMath.lat(me), GeoMath.lon(me))
            if (meMarker == null) {
                meMarker = map.addMarker(
                    MarkerOptions()
                        .position(m)
                        .title(getString(R.string.finder_marker_me))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            } else {
                meMarker?.position = m
            }
            line?.remove()
            line = map.addPolyline(
                PolylineOptions()
                    .add(m, v)
                    .color(0xFF0D7377.toInt())
                    .width(8f)
            )
            val bounds = LatLngBounds.builder().include(m).include(v).build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(v, 16f))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun updateOsm(me: GeoFix, victim: GeoFix) {
        val key = "${victim.latitudeMicrodegrees},${victim.longitudeMicrodegrees}," +
            "${me.latitudeMicrodegrees},${me.longitudeMicrodegrees}"
        if (key == lastOsmKey) return
        lastOsmKey = key
        val web = findViewById<WebView>(R.id.webMap)
        val vLat = GeoMath.lat(victim)
        val vLon = GeoMath.lon(victim)
        val meJs = if (me.isUnknown) {
            "null"
        } else {
            "{lat:${GeoMath.lat(me)},lon:${GeoMath.lon(me)}}"
        }
        val html = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html,body,#m{margin:0;height:100%;}</style></head>
            <body><div id="m"></div><script>
            var map=L.map('m');
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
            var v=L.marker([$vLat,$vLon]).addTo(map).bindPopup('Víctima');
            var me=$meJs;
            if(me){var mm=L.marker([me.lat,me.lon]).addTo(map).bindPopup('Tú');
            L.polyline([[me.lat,me.lon],[$vLat,$vLon]],{color:'#0D7377'}).addTo(map);
            map.fitBounds(L.latLngBounds([me.lat,me.lon],[$vLat,$vLon]).pad(0.2));}
            else{map.setView([$vLat,$vLon],16);}
            </script></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://local/", html, "text/html", "UTF-8", null)
    }

    private fun openExternalMaps() {
        val packet = RdaApp.instance.engine.findVisibleByOrigin(originHex) ?: return
        val victim = GeoFix(packet.latitudeMicrodegrees, packet.longitudeMicrodegrees, packet.accuracyMeters)
        if (victim.isUnknown) {
            Toast.makeText(this, R.string.finder_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        val lat = GeoMath.lat(victim)
        val lon = GeoMath.lon(victim)
        val uri = Uri.parse("google.navigation:q=$lat,$lon")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
        }
    }

    companion object {
        const val EXTRA_ORIGIN = "origin_hex"
    }
}
