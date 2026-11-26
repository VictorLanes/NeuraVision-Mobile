package com.victorlanes.neuravision.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.victorlanes.neuravision.model.Device
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupError(message: String) : Exception(message)

/**
 * Backup dos equipamentos num arquivo .nvbackup (JSON). Se informar senha, o arquivo é
 * CRIPTOGRAFADO (AES-GCM) — importante porque ele contém usuário e senha das câmeras.
 */
object BackupManager {

    private const val APP = "neuravision-mobile"
    private const val ITER = 100_000
    private val gson = Gson()

    fun build(devices: List<Device>, password: String): ByteArray {
        val devicesJson = gson.toJson(devices)
        val root = JsonObject()
        root.addProperty("app", APP)
        root.addProperty("v", 1)
        if (password.isBlank()) {
            root.addProperty("enc", false)
            root.add("devices", JsonParser.parseString(devicesJson))
        } else {
            val salt = random(16)
            val iv = random(12)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ct = cipher.doFinal(devicesJson.toByteArray(Charsets.UTF_8))
            root.addProperty("enc", true)
            root.addProperty("salt", b64(salt))
            root.addProperty("iv", b64(iv))
            root.addProperty("data", b64(ct))
        }
        return gson.toJson(root).toByteArray(Charsets.UTF_8)
    }

    /** Lê o backup. Pede [password] só se o arquivo estiver criptografado. */
    fun read(bytes: ByteArray, password: String): List<Device> {
        val root = try {
            JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            throw BackupError("Arquivo inválido ou não é um backup do NeuraVision.")
        }
        if (root.get("app")?.asString != APP) throw BackupError("Este arquivo não é um backup do NeuraVision.")
        val enc = root.get("enc")?.asBoolean ?: false
        val devicesJson: String = if (!enc) {
            root.getAsJsonArray("devices")?.toString() ?: "[]"
        } else {
            if (password.isBlank()) throw BackupError("Este backup tem senha. Informe a senha para importar.")
            try {
                val salt = unb64(root.get("salt").asString)
                val iv = unb64(root.get("iv").asString)
                val ct = unb64(root.get("data").asString)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8)
            } catch (e: Exception) {
                throw BackupError("Senha incorreta ou arquivo danificado.")
            }
        }
        return try {
            val type = object : TypeToken<List<Device>>() {}.type
            gson.fromJson(devicesJson, type) ?: emptyList()
        } catch (e: Exception) {
            throw BackupError("Não foi possível ler os equipamentos do backup.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, 256)
        val dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(dk, "AES")
    }

    private fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
