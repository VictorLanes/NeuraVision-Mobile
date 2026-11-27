package com.victorlanes.neuravision.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Controle de licença do NeuraVision (versão gratuita de 15 dias + ativação por código).
 *
 * Como funciona:
 *  - Ao abrir pela primeira vez, começa um teste gratuito de 15 dias.
 *  - Durante o teste, os recursos avançados (gravar, baixar gravação, backup, mais de 1
 *    equipamento) ficam bloqueados; ver câmeras ao vivo continua liberado.
 *  - Passados os 15 dias, o app fica travado até ser ativado.
 *  - Para ativar, o cliente envia ao administrador o ID deste aparelho; o administrador gera
 *    um código (assinado com a chave privada que só ele tem) com a validade da licença; o
 *    cliente cola o código aqui e o app confere a assinatura com a chave pública embutida.
 *
 * A chave pública abaixo é o par da chave privada que fica SOMENTE no aplicativo gerador
 * (NeuraVision Licenças). Sem a chave privada ninguém consegue criar códigos válidos.
 */
object License {

    private const val PREF = "nv_license"
    private const val KEY_FIRST = "first_launch"
    private const val KEY_CODE = "license_code"
    const val TRIAL_DAYS = 15

    // Chave pública ECDSA P-256 (X.509/SubjectPublicKeyInfo, Base64).
    private const val PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETb6RPRwq4DE9/EIbVo11JH7bWT8YBQiDC5+lc+gdQ2/3h1gT5xISdTrQT+wQJ1Ta/YUYY0ooaj9DiqGiKpRL9A=="

    enum class Status { TRIAL, TRIAL_EXPIRED, LICENSED, LICENSE_EXPIRED }

    data class State(
        val status: Status,
        val daysLeft: Int,   // teste: dias restantes | licenciado: dias até vencer (-1 = sem vencimento)
        val client: String,  // nome do cliente gravado na licença
        val expiry: Long     // vencimento em epoch ms (0 = sem vencimento)
    ) {
        val isPro get() = status == Status.LICENSED
        val isLocked get() = status == Status.TRIAL_EXPIRED || status == Status.LICENSE_EXPIRED
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Marca o início do teste gratuito na primeira abertura. Chamar no Splash. */
    fun ensureFirstLaunch(c: Context) {
        val p = prefs(c)
        if (!p.contains(KEY_FIRST)) p.edit().putLong(KEY_FIRST, System.currentTimeMillis()).apply()
    }

    // ------------------------------------------------------------ ID do aparelho
    @SuppressLint("HardwareIds")
    fun deviceId(c: Context): String {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "neuravision"
        val digest = MessageDigest.getInstance("SHA-256").digest("NV|$raw".toByteArray())
        val b32 = base32(digest).take(12)
        return "NV-${b32.substring(0, 4)}-${b32.substring(4, 8)}-${b32.substring(8, 12)}"
    }

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0; var bits = 0
        for (byt in bytes) {
            buffer = (buffer shl 8) or (byt.toInt() and 0xFF); bits += 8
            while (bits >= 5) { bits -= 5; sb.append(B32[(buffer shr bits) and 0x1F]) }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------ estado atual
    fun state(c: Context): State {
        ensureFirstLaunch(c)
        prefs(c).getString(KEY_CODE, null)?.let { code ->
            val lic = verify(c, code)
            if (lic != null && (lic.deviceId.isEmpty() || lic.deviceId == deviceId(c))) {
                val now = System.currentTimeMillis()
                return if (lic.expiry == 0L || lic.expiry > now) {
                    val days = if (lic.expiry == 0L) -1 else daysBetween(now, lic.expiry)
                    State(Status.LICENSED, days, lic.client, lic.expiry)
                } else {
                    State(Status.LICENSE_EXPIRED, 0, lic.client, lic.expiry)
                }
            }
        }
        val first = prefs(c).getLong(KEY_FIRST, System.currentTimeMillis())
        val left = TRIAL_DAYS - daysBetween(first, System.currentTimeMillis())
        return if (left > 0) State(Status.TRIAL, left, "", 0)
        else State(Status.TRIAL_EXPIRED, 0, "", 0)
    }

    fun isPro(c: Context) = state(c).isPro
    fun isLocked(c: Context) = state(c).isLocked

    // ------------------------------------------------------------ ativação
    sealed class Result {
        object Ok : Result()
        data class Error(val msg: String) : Result()
    }

    fun activate(c: Context, rawCode: String): Result {
        val code = rawCode.trim().replace("\\s".toRegex(), "")
        if (code.isEmpty()) return Result.Error("Digite o código de ativação.")
        val lic = verify(c, code)
            ?: return Result.Error("Código inválido ou alterado. Confira com o administrador.")
        if (lic.deviceId.isNotEmpty() && lic.deviceId != deviceId(c))
            return Result.Error("Este código foi gerado para outro aparelho.\n" +
                "Envie ao administrador o ID deste aparelho: ${deviceId(c)}")
        if (lic.expiry != 0L && lic.expiry <= System.currentTimeMillis())
            return Result.Error("Esta licença já venceu (${dateStr(lic.expiry)}). Fale com o administrador.")
        prefs(c).edit().putString(KEY_CODE, code).apply()
        return Result.Ok
    }

    fun clear(c: Context) { prefs(c).edit().remove(KEY_CODE).apply() }

    // ------------------------------------------------------------ verificação do código
    private data class Lic(val client: String, val deviceId: String, val expiry: Long)

    private fun verify(c: Context, code: String): Lic? {
        return try {
            if (!code.startsWith("NVL-")) return null
            val body = code.substring(4)
            val dot = body.indexOf('.')
            if (dot <= 0) return null
            val payload = b64urlDecode(body.substring(0, dot))
            val sig = b64urlDecode(body.substring(dot + 1))
            val pub = KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_B64, Base64.NO_WRAP)))
            val ver = Signature.getInstance("SHA256withECDSA")
            ver.initVerify(pub)
            ver.update(payload)
            if (!ver.verify(sig)) return null
            val j = JSONObject(String(payload))
            Lic(j.optString("c", ""), j.optString("d", ""), j.optLong("e", 0L))
        } catch (e: Exception) { null }
    }

    private fun b64urlDecode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun daysBetween(a: Long, b: Long): Int =
        TimeUnit.MILLISECONDS.toDays((b - a).coerceAtLeast(0)).toInt()

    fun dateStr(epoch: Long): String =
        if (epoch == 0L) "sem vencimento"
        else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epoch))
}
