package mx.reddeayuda.wear

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class WearMainActivity : AppCompatActivity() {
    private var sosActive = false
    private lateinit var sensors: WearSensors
    private var adapting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)
        sensors = WearSensors(this)

        findViewById<View>(R.id.btnSos).setOnClickListener { activateSos() }
        findViewById<MaterialButton>(R.id.btnImOk).setOnClickListener { cancelSos() }
        findViewById<View>(R.id.scroll).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!adapting) adaptToWatchShape()
        }
        ensurePermissions()
        refreshUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<View>(R.id.scroll).post { adaptToWatchShape() }
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

    /**
     * Escala el SOS al área útil (redondo / cuadrado / chin).
     * Si está activo, prioriza que quepan vitales + "Estoy bien".
     */
    private fun adaptToWatchShape() {
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val content = findViewById<ViewGroup>(R.id.content)
        val btn = findViewById<View>(R.id.btnSos)
        val label = findViewById<TextView>(R.id.txtSosLabel)
        val title = findViewById<View>(R.id.txtTitle)
        val status = findViewById<View>(R.id.txtStatus)
        val vitals = findViewById<View>(R.id.txtVitals)
        val ok = findViewById<View>(R.id.btnImOk)

        val viewportH = scroll.height - scroll.paddingTop - scroll.paddingBottom
        val viewportW = scroll.width - scroll.paddingStart - scroll.paddingEnd
        if (viewportH <= 0 || viewportW <= 0) return

        // Con SOS activo el título se oculta para ganar altura.
        title.visibility = if (sosActive) View.GONE else View.VISIBLE

        val gap = resources.getDimensionPixelSize(R.dimen.wear_gap)
        val fixedChrome =
            content.paddingTop + content.paddingBottom +
                (if (title.visibility == View.VISIBLE) estimateH(title, viewportW) + gap else 0) +
                estimateH(status, viewportW) +
                estimateH(vitals, viewportW) + 3.dp +
                (if (ok.visibility == View.VISIBLE) estimateH(ok, viewportW) + gap else 0) +
                gap

        val roomForSos = (viewportH - fixedChrome).coerceAtLeast(dimenPx(R.dimen.wear_sos_min))
        val ratio = if (sosActive) 0.36f else 0.48f
        val preferred = (minOf(viewportW, viewportH) * ratio).toInt()
        val minSos = dimenPx(R.dimen.wear_sos_min)
        val maxSos = dimenPx(R.dimen.wear_sos_max)
        val size = preferred.coerceIn(minSos, minOf(maxSos, roomForSos))

        adapting = true
        content.minimumHeight = viewportH

        val lp = btn.layoutParams
        if (lp.width != size || lp.height != size) {
            lp.width = size
            lp.height = size
            btn.layoutParams = lp
            val sosSp = (size / resources.displayMetrics.density / 4.4f).coerceIn(14f, 26f)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sosSp)
        }

        // Si aún no cabe en idle, oculta el título.
        if (!sosActive) {
            val total = fixedChrome + size
            title.visibility = if (total <= viewportH) View.VISIBLE else View.GONE
            content.minimumHeight = viewportH
        }

        content.post {
            adapting = false
            scroll.scrollTo(0, 0)
        }
    }

    private fun estimateH(view: View, width: Int): Int {
        if (view.visibility == View.GONE) return 0
        if (view.measuredHeight > 0) return view.measuredHeight
        val wSpec = View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), View.MeasureSpec.AT_MOST)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        return view.measuredHeight
    }

    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun dimenPx(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun activateSos() {
        if (!hasRequiredPerms()) {
            Toast.makeText(this, R.string.need_perms, Toast.LENGTH_LONG).show()
            ensurePermissions()
            return
        }
        sosActive = true
        WearSosService.start(this)
        refreshUi()
        sensors.requestFreshLocation { loc ->
            val payload = sensors.buildPayload(loc)
            WearPhoneClient.send(this, WearPaths.SOS, payload) { ok ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (ok) R.string.status_sent else R.string.status_no_phone,
                        Toast.LENGTH_SHORT
                    ).show()
                    findViewById<TextView>(R.id.txtStatus).setText(
                        if (ok) R.string.status_sent else R.string.status_no_phone
                    )
                }
            }
        }
        refreshVitals()
    }

    private fun cancelSos() {
        sosActive = false
        WearSosService.stop(this)
        refreshUi()
        Toast.makeText(this, R.string.im_ok, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        findViewById<MaterialButton>(R.id.btnImOk).visibility =
            if (sosActive) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtStatus).setText(
            if (sosActive) R.string.status_active else R.string.status_idle
        )
        refreshVitals()
        findViewById<View>(R.id.scroll).post { adaptToWatchShape() }
    }

    private fun refreshVitals() {
        val hr = sensors.heartRate
        val spo2 = sensors.spo2
        val tv = findViewById<TextView>(R.id.txtVitals)
        tv.text = when {
            hr > 0 && spo2 > 0 -> getString(R.string.vitals_line, hr, spo2)
            hr > 0 -> getString(R.string.vitals_hr_only, hr)
            else -> getString(R.string.vitals_na)
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
