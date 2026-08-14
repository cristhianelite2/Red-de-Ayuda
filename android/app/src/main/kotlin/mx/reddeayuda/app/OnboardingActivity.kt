package mx.reddeayuda.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import mx.reddeayuda.platform.PermissionCatalog

class OnboardingActivity : AppCompatActivity() {
    private var step = 0
    private val totalSteps = 5

    private val images = intArrayOf(
        R.drawable.onboard_1,
        R.drawable.onboard_2,
        R.drawable.onboard_3,
        R.drawable.onboard_2,
        R.drawable.onboard_1
    )
    private val titles = intArrayOf(
        R.string.onboard_1_title,
        R.string.onboard_2_title,
        R.string.onboard_3_title,
        R.string.onboard_4_title,
        R.string.onboard_5_title
    )
    private val bodies = intArrayOf(
        R.string.onboard_1_body,
        R.string.onboard_2_body,
        R.string.onboard_3_body,
        R.string.onboard_4_body,
        R.string.onboard_5_body
    )

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        importContact(uri)
        bind()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener { onNext() }
        findViewById<MaterialButton>(R.id.btnAction).setOnClickListener { onAction() }
        bind()
    }

    private fun onNext() {
        when (step) {
            3 -> {
                // permisos: se puede avanzar aunque no todos estén concedidos
                step++
                bind()
            }
            4 -> finishOnboarding()
            else -> {
                step++
                bind()
            }
        }
    }

    private fun onAction() {
        when (step) {
            3 -> requestRuntimePermissions()
            4 -> ensureContactsThenPick()
        }
    }

    private fun finishOnboarding() {
        Prefs.setOnboardingDone(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun requestRuntimePermissions() {
        val missing = PermissionCatalog.missing(this)
        if (missing.isEmpty()) {
            Toast.makeText(this, "Permisos listos", Toast.LENGTH_SHORT).show()
            step++
            bind()
        } else {
            ActivityCompat.requestPermissions(this, missing, REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            bind()
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Gracias — puedes seguir", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQ_CONTACTS &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            launchPicker()
        }
    }

    private fun ensureContactsThenPick() {
        val need = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS)
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_CONTACTS)
        } else {
            launchPicker()
        }
    }

    private fun launchPicker() {
        if (ContactStore.list(this).size >= ContactStore.MAX) {
            Toast.makeText(this, R.string.contacts_max, Toast.LENGTH_LONG).show()
            return
        }
        pickContact.launch(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        )
    }

    private fun importContact(uri: Uri) {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val added = ContactStore.add(this, cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty())
                if (added == null) {
                    Toast.makeText(this, R.string.contacts_add_fail, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.contacts_added, added.name), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.contacts_permission, Toast.LENGTH_LONG).show()
        } finally {
            cursor?.close()
        }
    }

    private fun bind() {
        findViewById<ImageView>(R.id.image).setImageResource(images[step])
        findViewById<TextView>(R.id.title).setText(titles[step])
        findViewById<TextView>(R.id.body).setText(bodies[step])
        findViewById<TextView>(R.id.stepLabel).text =
            getString(R.string.onboard_step, step + 1, totalSteps)

        val action = findViewById<MaterialButton>(R.id.btnAction)
        val next = findViewById<MaterialButton>(R.id.btnNext)
        val extra = findViewById<TextView>(R.id.extraLabel)

        when (step) {
            3 -> {
                action.visibility = View.VISIBLE
                action.setText(R.string.onboard_4_allow)
                next.setText(R.string.onboard_next)
                val missing = PermissionCatalog.missing(this).size
                extra.visibility = View.VISIBLE
                extra.text = if (missing == 0) {
                    "Todos los permisos concedidos"
                } else {
                    "Faltan $missing permiso(s) — puedes concederlos ahora"
                }
            }
            4 -> {
                action.visibility = View.VISIBLE
                action.setText(R.string.onboard_5_add)
                next.setText(R.string.onboard_5_skip)
                val n = ContactStore.list(this).size
                extra.visibility = View.VISIBLE
                extra.text = getString(R.string.onboard_5_count, n)
            }
            else -> {
                action.visibility = View.GONE
                extra.visibility = View.GONE
                next.setText(if (step >= 2 && step < 3) R.string.onboard_next else R.string.onboard_next)
            }
        }

        val dots = findViewById<LinearLayout>(R.id.dots)
        dots.removeAllViews()
        repeat(totalSteps) { i ->
            val v = View(this)
            val size = (8 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = size / 2
            lp.marginEnd = size / 2
            v.layoutParams = lp
            v.setBackgroundResource(if (i == step) R.drawable.dot_on else R.drawable.dot)
            dots.addView(v)
        }
    }

    companion object {
        private const val REQ_PERMS = 81
        private const val REQ_CONTACTS = 82
    }
}
