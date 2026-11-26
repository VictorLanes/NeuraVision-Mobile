package com.victorlanes.neuravision.data

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Conta local do app (sem servidor): guarda nome/e-mail e os hashes da senha e do PIN.
 * As senhas NUNCA são guardadas em texto — só o hash PBKDF2 (com sal aleatório).
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("neuravision_auth", Context.MODE_PRIVATE)

    fun hasAccount(): Boolean = prefs.contains("pwd")

    fun name(): String = prefs.getString("name", "") ?: ""
    fun email(): String = prefs.getString("email", "") ?: ""

    fun createAccount(name: String, email: String, password: String) {
        prefs.edit()
            .putString("name", name.trim())
            .putString("email", email.trim())
            .putString("pwd", hash(password))
            .apply()
    }

    fun verifyPassword(password: String): Boolean =
        verify(password, prefs.getString("pwd", "") ?: "")

    fun changePassword(newPassword: String) {
        prefs.edit().putString("pwd", hash(newPassword)).apply()
    }

    fun updateProfile(name: String, email: String) {
        prefs.edit().putString("name", name.trim()).putString("email", email.trim()).apply()
    }

    // ---------------- PIN ----------------
    fun hasPin(): Boolean = prefs.contains("pin")
    fun setPin(pin: String) = prefs.edit().putString("pin", hash(pin)).apply()
    fun clearPin() = prefs.edit().remove("pin").apply()
    fun verifyPin(pin: String): Boolean = verify(pin, prefs.getString("pin", "") ?: "")

    // ---------------- biometria ----------------
    fun biometricEnabled(): Boolean = prefs.getBoolean("bio", false)
    fun setBiometricEnabled(on: Boolean) = prefs.edit().putBoolean("bio", on).apply()

    // ---------------- hashing ----------------
    private fun hash(text: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val dk = pbkdf2(text, salt, ITER)
        return "$ITER:${b64(salt)}:${b64(dk)}"
    }

    private fun verify(text: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        return try {
            val iter = parts[0].toInt()
            val salt = unb64(parts[1])
            val expected = unb64(parts[2])
            val calc = pbkdf2(text, salt, iter)
            constantTimeEquals(calc, expected)
        } catch (e: Exception) {
            false
        }
    }

    private fun pbkdf2(text: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(text.toCharArray(), salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val ITER = 120_000
    }
}
