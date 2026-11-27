package com.victorlanes.neuravision.licenca

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Guarda o PIN do app gerador e o histórico de licenças emitidas (localmente). */
class Store(context: Context) {

    private val p = context.getSharedPreferences("neuralic", Context.MODE_PRIVATE)

    // ---------------- PIN ----------------
    fun hasPin(): Boolean = p.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = Base64.decode(p.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        return p.getString("pin_hash", "") == hash(pin, salt)
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        var d = md.digest(pin.toByteArray())
        repeat(5000) { d = MessageDigest.getInstance("SHA-256").digest(d) }
        return Base64.encodeToString(d, Base64.NO_WRAP)
    }

    // ---------------- numeração ----------------
    fun nextNumber(): Int {
        val n = p.getInt("counter", 0) + 1
        p.edit().putInt("counter", n).apply()
        return n
    }

    // ---------------- histórico ----------------
    fun addHistory(client: String, deviceId: String, expiry: Long, number: Int, code: String) {
        val arr = history()
        arr.put(JSONObject().apply {
            put("client", client); put("deviceId", deviceId)
            put("expiry", expiry); put("issued", System.currentTimeMillis())
            put("number", number); put("code", code)
        })
        // mantém no máximo 200 registros
        while (arr.length() > 200) arr.remove(0)
        p.edit().putString("history", arr.toString()).apply()
    }

    fun history(): JSONArray =
        try { JSONArray(p.getString("history", "[]")) } catch (e: Exception) { JSONArray() }
}
