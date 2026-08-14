package mx.reddeayuda.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WearMainActivity : AppCompatActivity() {
    private var sosActive = false
    private lateinit var sensors: WearSensors

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)
        sensors = WearSensors(this)

        findViewById<View>(R.id.btnSos).setOnClickListener { activateSos() }
        findViewById<Button>(R.id.btnImOk).setOnClickListener { cancelSos() }
        ensurePermissions()
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        sensors.start()
        refreshVitals()
    }

    override fun onStop() {
        if (!sosActive) sensors.stop()
        super.onStop()
    }

    private fun activateSos() {
        if (!hasRequiredPerms()) {
            Toast.makeText(this, R.string.need_perms, Toast.LENGTH_LONG).show()
            ensurePermissions()
            return
        }
        sosActive = true
        WearSosService.start(this)
        findViewById<TextView>(R.id.txtStatus).setText(R.string.status_active)
        findViewById<Button>(R.id.btnImOk).visibility = View.VISIBLE
        sensors.requestFreshLocation { loc ->
            val payload = sensors.buildPayload(loc)
            WearPhoneClient.send(this, WearPaths.SOS, payload) { ok ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (ok) R.string.status_sent else R.string.status_no_phone,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        refreshVitals()
    }

    private fun cancelSos() {
        sosActive = false
        WearSosService.stop(this)
        findViewById<TextView>(R.id.txtStatus).setText(R.string.status_idle)
        findViewById<Button>(R.id.btnImOk).visibility = View.GONE
        Toast.makeText(this, R.string.im_ok, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        findViewById<Button>(R.id.btnImOk).visibility =
            if (sosActive) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtStatus).setText(
            if (sosActive) R.string.status_active else R.string.status_idle
        )
        refreshVitals()
    }

    private fun refreshVitals() {
        val hr = sensors.heartRate
        val spo2 = sensors.spo2
        val tv = findViewById<TextView>(R.id.txtVitals)
        tv.text = if (hr > 0 || spo2 > 0) {
            getString(R.string.vitals_line, hr, spo2)
        } else {
            getString(R.string.vitals_na)
        }
    }

    private fun hasRequiredPerms(): Boolean {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        return need.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensurePermissions() {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BODY_SENSORS
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 77)
        }
    }
}
