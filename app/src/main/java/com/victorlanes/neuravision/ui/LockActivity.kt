package com.victorlanes.neuravision.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.victorlanes.neuravision.data.AuthStore
import com.victorlanes.neuravision.data.Session
import com.victorlanes.neuravision.databinding.ActivityLockBinding

/** Entrada no app: biometria (se ativada) e/ou senha/PIN. */
class LockActivity : AppCompatActivity() {

    private lateinit var b: ActivityLockBinding
    private lateinit var auth: AuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLockBinding.inflate(layoutInflater)
        setContentView(b.root)
        auth = AuthStore(this)

        b.hello.text = "Olá, ${auth.name().ifBlank { "bem-vindo" }}"
        b.field.hint = if (auth.hasPin()) "Senha ou PIN" else "Senha"
        b.enter.setOnClickListener { tryPassword() }
        b.useBio.setOnClickListener { promptBiometric() }

        val canBio = canUseBiometric()
        b.useBio.visibility = if (auth.biometricEnabled() && canBio) android.view.View.VISIBLE else android.view.View.GONE
        if (auth.biometricEnabled() && canBio) b.root.post { promptBiometric() }
    }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun tryPassword() {
        val v = b.field.editText?.text?.toString() ?: ""
        if (v.isEmpty()) { b.field.error = "Digite a senha"; return }
        val ok = auth.verifyPassword(v) || (auth.hasPin() && auth.verifyPin(v))
        if (ok) unlock() else b.field.error = "Senha incorreta"
    }

    private fun promptBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this@LockActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear o NeuraVision")
            .setSubtitle("Use a sua digital ou biometria")
            .setNegativeButtonText("Usar senha")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun unlock() {
        Session.unlocked = true
        Session.pausedAt = 0
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // sem "voltar" para fora da tela de bloqueio: fecha o app
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishAffinity()
    }
}
