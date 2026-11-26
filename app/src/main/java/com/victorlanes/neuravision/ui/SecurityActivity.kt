package com.victorlanes.neuravision.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.victorlanes.neuravision.data.AuthStore
import com.victorlanes.neuravision.databinding.ActivitySecurityBinding

/** Segurança: biometria, PIN e troca de senha da conta. */
class SecurityActivity : AppCompatActivity() {

    private lateinit var b: ActivitySecurityBinding
    private lateinit var auth: AuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(b.root)
        auth = AuthStore(this)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        val canBio = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        b.bioSwitch.isEnabled = canBio
        b.bioSwitch.isChecked = canBio && auth.biometricEnabled()
        b.bioSub.text = if (canBio) "Desbloquear o app com a digital" else "Este celular não tem biometria configurada"
        b.bioSwitch.setOnCheckedChangeListener { _, on -> auth.setBiometricEnabled(on) }

        updatePinLabel()
        b.pinRow.setOnClickListener { pinDialog() }
        b.passRow.setOnClickListener { passDialog() }
    }

    private fun updatePinLabel() {
        b.pinSub.text = if (auth.hasPin()) "PIN ativado · toque para alterar ou remover" else "Criar um PIN para entrar mais rápido"
    }

    private fun pinField(): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = "PIN (4 a 8 dígitos)"
    }

    private fun pinDialog() {
        val input = pinField()
        val builder = AlertDialog.Builder(this)
            .setTitle(if (auth.hasPin()) "Alterar PIN" else "Criar PIN")
            .setView(pad(input))
            .setPositiveButton("Salvar") { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) { toast("O PIN precisa ter pelo menos 4 dígitos"); return@setPositiveButton }
                auth.setPin(pin); updatePinLabel(); toast("PIN salvo")
            }
            .setNegativeButton("Cancelar", null)
        if (auth.hasPin()) builder.setNeutralButton("Remover") { _, _ -> auth.clearPin(); updatePinLabel(); toast("PIN removido") }
        builder.show()
    }

    private fun passDialog() {
        val cur = secret("Senha atual")
        val n1 = secret("Nova senha")
        val n2 = secret("Repita a nova senha")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(cur); addView(n1); addView(n2)
        }
        AlertDialog.Builder(this)
            .setTitle("Alterar senha")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                if (!auth.verifyPassword(cur.text.toString())) { toast("Senha atual incorreta"); return@setPositiveButton }
                val a = n1.text.toString(); val c = n2.text.toString()
                if (a.length < 4) { toast("A nova senha precisa ter pelo menos 4 caracteres"); return@setPositiveButton }
                if (a != c) { toast("As senhas não conferem"); return@setPositiveButton }
                auth.changePassword(a); toast("Senha alterada")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun secret(hint: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setHint(hint)
    }

    private fun pad(v: android.view.View) = LinearLayout(this).apply {
        setPadding(48, 24, 48, 0); addView(v)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
