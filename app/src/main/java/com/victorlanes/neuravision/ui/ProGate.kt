package com.victorlanes.neuravision.ui

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.License

/** Bloqueia recursos da versão completa enquanto o app não estiver ativado. */
object ProGate {

    /** Retorna true se o recurso pode ser usado (app ativado). Caso contrário, mostra o aviso. */
    fun allowed(activity: AppCompatActivity, feature: String): Boolean {
        if (License.isPro(activity)) return true
        AlertDialog.Builder(activity)
            .setTitle("Recurso da versão completa")
            .setMessage("“$feature” faz parte da versão completa do NeuraVision.\n\n" +
                "Compre a licença com o administrador e ative o aplicativo com o código recebido.")
            .setPositiveButton("Ativar agora") { _, _ ->
                activity.startActivity(Intent(activity, ActivationActivity::class.java))
            }
            .setNegativeButton("Agora não", null)
            .show()
        return false
    }
}
