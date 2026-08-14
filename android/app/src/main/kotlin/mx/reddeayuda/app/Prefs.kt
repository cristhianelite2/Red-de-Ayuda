package mx.reddeayuda.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("rda_prefs", Context.MODE_PRIVATE)

    fun raw(ctx: Context): SharedPreferences = p(ctx)

    fun onboardingDone(ctx: Context): Boolean = p(ctx).getBoolean("onboarding_done", false)

    fun setOnboardingDone(ctx: Context) {
        p(ctx).edit().putBoolean("onboarding_done", true).apply()
    }

    fun wifiDirectEnabled(ctx: Context): Boolean = p(ctx).getBoolean("wifi_direct", true)

    fun setWifiDirectEnabled(ctx: Context, enabled: Boolean) {
        p(ctx).edit().putBoolean("wifi_direct", enabled).apply()
    }
}

data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String
)

object ContactStore {
    const val MAX = 5
    private const val KEY = "emergency_contacts"

    fun list(ctx: Context): List<EmergencyContact> {
        val raw = Prefs.raw(ctx).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                EmergencyContact(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    phone = o.optString("phone")
                ).takeIf { it.id.isNotBlank() && it.phone.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, contacts: List<EmergencyContact>) {
        val arr = JSONArray()
        contacts.take(MAX).forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("phone", c.phone)
            )
        }
        Prefs.raw(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, name: String, phone: String): EmergencyContact? {
        val current = list(ctx)
        if (current.size >= MAX) return null
        val cleaned = phone.filter { it.isDigit() || it == '+' }
        if (cleaned.length < 7) return null
        if (current.any { it.phone.filter(Char::isDigit) == cleaned.filter(Char::isDigit) }) {
            return null
        }
        val contact = EmergencyContact(
            id = System.currentTimeMillis().toString(),
            name = name.trim().ifBlank { cleaned },
            phone = cleaned
        )
        save(ctx, current + contact)
        return contact
    }

    fun remove(ctx: Context, id: String) {
        save(ctx, list(ctx).filterNot { it.id == id })
    }
}
