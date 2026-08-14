package mx.reddeayuda.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class ContactsActivity : AppCompatActivity() {

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        importContact(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        SosBanner.bind(this, findViewById(R.id.sosBanner))
        BackBar.bind(this, getString(R.string.contacts_title))

        findViewById<MaterialButton>(R.id.btnPickContact).setOnClickListener {
            ensureContactsPermissionThen { launchPicker() }
        }
        refreshList()
    }

    private fun ensureContactsPermissionThen(block: () -> Unit) {
        val need = mutableListOf(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.SEND_SMS
        }
        val missing = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            block()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            launchPicker()
        } else if (requestCode == REQ) {
            Toast.makeText(this, R.string.contacts_permission, Toast.LENGTH_LONG).show()
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
                val name = cursor.getString(0).orEmpty()
                val phone = cursor.getString(1).orEmpty()
                val added = ContactStore.add(this, name, phone)
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
        refreshList()
    }

    private fun refreshList() {
        val contacts = ContactStore.list(this)
        findViewById<TextView>(R.id.txtCount).text =
            getString(R.string.contacts_count, contacts.size, ContactStore.MAX)
        findViewById<MaterialButton>(R.id.btnPickContact).isEnabled =
            contacts.size < ContactStore.MAX

        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.contacts_empty)
                setTextColor(getColor(R.color.ink_soft))
                setPadding(24, 24, 24, 24)
            }
            list.addView(empty)
            return
        }
        contacts.forEach { c ->
            val row = inflater.inflate(R.layout.item_contact, list, false)
            row.findViewById<TextView>(R.id.name).text = c.name
            row.findViewById<TextView>(R.id.phone).text = c.phone
            row.findViewById<View>(R.id.btnRemove).setOnClickListener {
                ContactStore.remove(this, c.id)
                refreshList()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * resources.displayMetrics.density).toInt()
            list.addView(row, lp)
        }
    }

    companion object {
        private const val REQ = 91
    }
}
