package com.victorlanes.neuravision.licenca

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Gera códigos de ativação do NeuraVision, assinados com a chave privada ECDSA P-256.
 *
 * ATENÇÃO: este aplicativo contém a CHAVE PRIVADA das licenças. Mantenha-o SOMENTE no seu
 * aparelho e NUNCA distribua este APK. Qualquer pessoa com ele conseguiria gerar licenças.
 */
object Signer {

    // Chave privada ECDSA P-256 (PKCS#8, Base64). Par da chave pública embutida no app do cliente.
    private const val PRIVATE_KEY_B64 =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg/I2yTRTWBS+XXUxE8fIsDNHB6aYYxyWvQgeg3enfwYShRANCAARNvpE9HCrgMT38QhtWjXUkfttZPxgFCIMLn6Vz6B1Db/eHWBPnEhJ1OtBP7BAnVNr9hRhjSihqP0OKoaIqlEv0"

    /**
     * @param client   nome do cliente
     * @param deviceId ID do aparelho (NV-XXXX-XXXX-XXXX) ou vazio para código universal
     * @param expiryMs vencimento em epoch ms (0 = sem vencimento)
     * @param number   número sequencial da licença
     */
    fun generate(client: String, deviceId: String, expiryMs: Long, number: Int): String {
        val payload = JSONObject().apply {
            put("v", 1)
            put("c", client)
            put("d", deviceId)
            put("e", expiryMs)
            put("i", System.currentTimeMillis())
            put("n", number)
        }.toString().toByteArray()

        val priv = KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(Base64.decode(PRIVATE_KEY_B64, Base64.NO_WRAP)))
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(priv)
        s.update(payload)
        val sig = s.sign()

        return "NVL-${b64url(payload)}.${b64url(sig)}"
    }

    private fun b64url(b: ByteArray): String =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
