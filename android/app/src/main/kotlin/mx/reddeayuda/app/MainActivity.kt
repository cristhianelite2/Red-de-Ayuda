package mx.reddeayuda.app

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import mx.reddeayuda.platform.BluetoothHelper
import mx.reddeayuda.platform.OemBattery
import mx.reddeayuda.platform.PermissionCatalog
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.DeviceState

class MainActivity : AppCompatActivity() {

    private var pendingSos = false
    private var askedBluetoothOnLaunch = false

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (BluetoothHelper.isEnabled(this)) {
            startMeshIfNeeded()
            if (pendingSos) {
                activateSos()
            } else {
                Toast.makeText(this, R.string.bt_on, Toast.LENGTH_SHORT).show()
            }
        } else if (pendingSos) {
            pendingSos = false
            Toast.makeText(this, R.string.bt_denied, Toast.LENGTH_LONG).show()
        } else if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.bt_denied, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshBluetoothCard()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_EXIT, false) == true) {
            finishAndRemoveTask()
            return
        }
        if (!Prefs.onboardingDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        bindGuide(
            findViewById(R.id.cardSismo),
            R.drawable.guide_sismo,
            getString(R.string.guide_sismo_title),
            getString(R.string.guide_sismo_sub)
        ) { openGuide("sismo") }

        bindGuide(
            findViewById(R.id.cardInundacion),
            R.drawable.guide_inundacion,
            getString(R.string.guide_flood_title),
            getString(R.string.guide_flood_sub)
        ) { openGuide("inundacion") }

        bindGuide(
            findViewById(R.id.cardRcp),
            R.drawable.guide_rcp,
            getString(R.string.guide_rcp_title),
            getString(R.string.guide_rcp_sub)
        ) {
            startActivity(Intent(this, FirstAidMenuActivity::class.java))
        }

        findViewById<View>(R.id.btnSos).setOnClickListener { confirmSos() }
        findViewById<View>(R.id.cardRepeater).setOnClickListener { startRepeater() }
        findViewById<View>(R.id.cardRescue).setOnClickListener { startRescue() }
        findViewById<MaterialButton>(R.id.btnEnableBluetooth).setOnClickListener {
            pendingSos = false
            requestBluetoothOn()
        }
        findViewById<View>(R.id.footerCredit).setOnClickListener { openAuthorSite() }

        SosBanner.bind(this, findViewById(R.id.sosBanner))
        RdaApp.instance.uiListeners += { runOnUiThread { refresh() } }
        ensurePermissions()
        startMeshIfNeeded()
        promptBluetoothOnLaunch()
        refresh()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindGuide(card: View, image: Int, title: String, sub: String, click: () -> Unit) {
        card.findViewById<ImageView>(R.id.guideImage).setImageResource(image)
        card.findViewById<TextView>(R.id.guideTitle).text = title
        card.findViewById<TextView>(R.id.guideSub).text = sub
        card.setOnClickListener { click() }
    }

    private fun openAuthorSite() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.footer_url))))
    }

    private fun openGuide(id: String) {
        startActivity(Intent(this, GuideActivity::class.java).putExtra(GuideActivity.EXTRA_ID, id))
    }

    private fun confirmSos() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sos_confirm_title)
            .setMessage(R.string.sos_confirm_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.activate) { _, _ ->
                pendingSos = true
                ensureBluetoothThen { activateSos() }
            }
            .show()
    }

    private fun activateSos() {
        pendingSos = false
        val app = RdaApp.instance
        app.startMesh(sos = true)
        app.engine.createEmergency(app.gnss.lastKnown(), app.battery())
        refresh()
    }

    private fun promptBluetoothOnLaunch() {
        if (BluetoothHelper.isEnabled(this) || askedBluetoothOnLaunch) return
        askedBluetoothOnLaunch = true
        AlertDialog.Builder(this)
            .setTitle(R.string.bt_launch_title)
            .setMessage(R.string.bt_launch_body)
            .setPositiveButton(R.string.bt_enable) { _, _ ->
                pendingSos = false
                requestBluetoothOn()
            }
            .setNegativeButton(R.string.bt_later, null)
            .show()
    }

    private fun ensureBluetoothThen(onReady: () -> Unit) {
        if (BluetoothHelper.isEnabled(this)) {
            onReady()
        } else {
            Toast.makeText(this, R.string.bt_needed_sos, Toast.LENGTH_SHORT).show()
            requestBluetoothOn()
        }
    }

    private fun requestBluetoothOn() {
        if (BluetoothHelper.isEnabled(this)) {
            refreshBluetoothCard()
            return
        }
        if (BluetoothHelper.adapter(this) == null) {
            Toast.makeText(this, R.string.bt_denied, Toast.LENGTH_LONG).show()
            pendingSos = false
            return
        }
        val missing = PermissionCatalog.missing(this)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing, REQ_BT_PERMS)
            return
        }
        try {
            enableBluetooth.launch(BluetoothHelper.requestEnableIntent())
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.bt_denied, Toast.LENGTH_LONG).show()
            pendingSos = false
        }
    }

    private fun startRepeater() {
        startMeshIfNeeded()
        RdaApp.instance.engine.enterRepeaterRole()
        if (!OemBattery.isIgnoringOptimizations(this)) {
            AlertDialog.Builder(this)
                .setTitle("Ayudar en silencio")
                .setMessage("Tu teléfono retransmitirá SOS sin mostrar datos de nadie. Para que funcione con la pantalla apagada, excluye la app del ahorro de batería.")
                .setPositiveButton("Configurar") { _, _ ->
                    startActivity(OemBattery.requestIgnoreIntent(this))
                }
                .setNegativeButton("Ahora no", null)
                .show()
        } else {
            Toast.makeText(this, "Repetidor silencioso activo", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun startRescue() {
        startMeshIfNeeded()
        RdaApp.instance.engine.enterRescueRole()
        startActivity(Intent(this, RescueActivity::class.java))
    }

    private fun startMeshIfNeeded() {
        RdaApp.instance.startMesh()
    }

    private fun ensurePermissions() {
        val missing = PermissionCatalog.missing(this)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing, 77)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            Toast.makeText(this, "Sin Bluetooth o ubicación la red no puede arrancar", Toast.LENGTH_LONG).show()
            if (requestCode == REQ_BT_PERMS) pendingSos = false
        } else if (requestCode == REQ_BT_PERMS) {
            requestBluetoothOn()
        }
        refresh()
    }

    private fun refresh() {
        val app = RdaApp.instance
        val snap = app.engine.snapshot()
        val chip = findViewById<TextView>(R.id.chipStatus)
        val meshOn = snap.state == DeviceState.SOS ||
            snap.state == DeviceState.RESCUE_CONTACT ||
            snap.role == DeviceRole.RESCUER ||
            app.engine.pendingCount() > 0
        chip.text = when {
            snap.state == DeviceState.SOS -> "SOS activo"
            snap.role == DeviceRole.RESCUER -> "Rescatista"
            meshOn -> getString(R.string.mesh_on)
            else -> getString(R.string.mesh_off)
        }
        chip.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                getColor(
                    when {
                        snap.state == DeviceState.SOS -> R.color.sos
                        snap.role == DeviceRole.RESCUER -> R.color.teal
                        else -> R.color.paper_dark
                    }
                )
            )
        )
        chip.setTextColor(
            getColor(
                if (snap.state == DeviceState.SOS || snap.role == DeviceRole.RESCUER) {
                    R.color.white
                } else {
                    R.color.chip_ok
                }
            )
        )
        findViewById<TextView>(R.id.txtMeta).text =
            "Batería ${app.battery()}%  ·  cola ${app.engine.pendingCount()}"
        refreshBluetoothCard()
    }

    private fun refreshBluetoothCard() {
        val on = BluetoothHelper.isEnabled(this)
        val status = findViewById<TextView>(R.id.txtBtStatus)
        val button = findViewById<MaterialButton>(R.id.btnEnableBluetooth)
        if (on) {
            status.text = getString(R.string.bt_on)
            status.setTextColor(getColor(R.color.chip_ok))
            status.backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.paper_dark))
            button.text = getString(R.string.bt_on)
            button.isEnabled = false
        } else {
            status.text = getString(R.string.bt_off_chip)
            status.setTextColor(getColor(R.color.sos))
            status.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFFBE9E7.toInt())
            button.text = getString(R.string.bt_enable)
            button.isEnabled = true
        }
    }

    companion object {
        const val EXTRA_EXIT = "exit_app"
        private const val REQ_BT_PERMS = 78
    }
}
