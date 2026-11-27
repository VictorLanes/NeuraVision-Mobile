package com.victorlanes.neuravision.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.License
import com.victorlanes.neuravision.databinding.ActivityActivationBinding
import java.net.URLEncoder

/** Tela de ativação: mostra o ID do aparelho e recebe o código enviado pelo administrador. */
class ActivationActivity : AppCompatActivity() {

    private lateinit var b: ActivityActivationBinding
    private lateinit var deviceId: String

    // Suporte / administrador (você)
    private val adminWhats = "5521987754974"

    // quando chamada por bloqueio (teste vencido), o "voltar" fecha o app
    private val blocking get() = intent.getBooleanExtra("blocking", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { goBack() }

        deviceId = License.deviceId(this)
        b.deviceId.text = deviceId
        showStatus()

        b.btnCopy.setOnClickListener { copy(deviceId); toast("ID copiado") }
        b.btnWhats.setOnClickListener { openWhats() }
        b.btnActivate.setOnClickListener { activate() }
    }

    private fun showStatus() {
        val s = License.state(this)
        b.statusText.text = when (s.status) {
            License.Status.TRIAL ->
                "Versão gratuita · ${s.daysLeft} dia(s) restantes do teste.\n" +
                "Ative para liberar gravação, download, backup e vários equipamentos."
            License.Status.TRIAL_EXPIRED ->
                "O teste gratuito de ${License.TRIAL_DAYS} dias terminou.\n" +
                "Ative o aplicativo com o código do administrador para continuar usando."
            License.Status.LICENSE_EXPIRED ->
                "Sua licença venceu em ${License.dateStr(s.expiry)}.\n" +
                "Fale com o administrador para renovar e ativar novamente."
            License.Status.LICENSED ->
                "Aplicativo ativado" + (if (s.client.isNotBlank()) " · ${s.client}" else "") + ".\n" +
                (if (s.expiry == 0L) "Licença sem vencimento." else "Válida até ${License.dateStr(s.expiry)}.")
        }
    }

    private fun activate() {
        when (val r = License.activate(this, b.code.text.toString())) {
            is License.Result.Ok -> {
                AlertDialog.Builder(this)
                    .setTitle("Tudo certo!")
                    .setMessage("NeuraVision ativado com sucesso.")
                    .setPositiveButton("Continuar") { _, _ ->
                        startActivity(Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
            is License.Result.Error ->
                AlertDialog.Builder(this).setTitle("Não deu para ativar")
                    .setMessage(r.msg).setPositiveButton("OK", null).show()
        }
    }

    private fun openWhats() {
        val msg = "Olá! Quero ativar o NeuraVision.\nID do meu aparelho: $deviceId"
        val url = "https://wa.me/$adminWhats?text=" + URLEncoder.encode(msg, "UTF-8")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            copy(deviceId); toast("WhatsApp não encontrado. ID copiado — envie ao administrador.")
        }
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("NeuraVision", text))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun goBack() {
        if (blocking) finishAffinity() else finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() = goBack()
}
