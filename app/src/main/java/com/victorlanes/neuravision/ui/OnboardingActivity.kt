package com.victorlanes.neuravision.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.victorlanes.neuravision.data.AuthStore
import com.victorlanes.neuravision.data.Session
import com.victorlanes.neuravision.databinding.ActivityOnboardingBinding

/** Primeiro uso: criar a conta que protege o aplicativo. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private lateinit var auth: AuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        auth = AuthStore(this)

        val canBio = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        b.useBio.visibility = if (canBio) android.view.View.VISIBLE else android.view.View.GONE
        b.useBio.isChecked = canBio

        b.create.setOnClickListener { create() }
    }

    private fun create() {
        val name = b.name.text.toString().trim()
        val email = b.email.text.toString().trim()
        val pass = b.pass.text.toString()
        val pass2 = b.pass2.text.toString()

        if (name.isEmpty()) { b.name.error = "Informe o seu nome"; return }
        if (pass.length < 4) { b.pass.error = "A senha precisa ter pelo menos 4 caracteres"; return }
        if (pass != pass2) { b.pass2.error = "As senhas não conferem"; return }

        auth.createAccount(name, email, pass)
        auth.setBiometricEnabled(b.useBio.visibility == android.view.View.VISIBLE && b.useBio.isChecked)

        Session.unlocked = true
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }
}
