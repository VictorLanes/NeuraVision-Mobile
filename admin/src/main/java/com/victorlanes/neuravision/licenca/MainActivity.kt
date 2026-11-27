package com.victorlanes.neuravision.licenca

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.licenca.databinding.ActivityMainBinding
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var store: Store
    private var customExpiry = 0L
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val options = listOf(
        "30 dias", "90 dias", "180 dias", "1 ano (365 dias)", "Sem vencimento", "Escolher data…"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        store = Store(this)

        b.validity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        b.validity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == 5) pickDate() else { customExpiry = 0L; updateValidityLabel(pos) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        b.btnGenerate.setOnClickListener { generate() }
        b.btnCopy.setOnClickListener { copy(b.code.text.toString()); toast("Código copiado") }
        b.btnShare.setOnClickListener { share() }
        b.btnHistory.setOnClickListener { showHistory() }

        gate()
    }

    // ---------------- PIN ----------------
    private fun gate() {
        if (store.hasPin()) askPin() else createPin()
    }

    private fun createPin() {
        val f = pinField("Crie um PIN (mín. 4 dígitos)")
        AlertDialog.Builder(this)
            .setTitle("Proteger o app")
            .setMessage("Defina um PIN para abrir o gerador de licenças.")
            .setView(wrap(f))
            .setCancelable(false)
            .setPositiveButton("Salvar") { _, _ ->
                val pin = f.text.toString()
                if (pin.length < 4) { toast("PIN muito curto"); createPin() }
                else { store.setPin(pin); unlock() }
            }
            .setNegativeButton("Sair") { _, _ -> finish() }
            .show()
    }

    private fun askPin() {
        val f = pinField("PIN")
        AlertDialog.Builder(this)
            .setTitle("Gerador de licenças")
            .setMessage("Digite o PIN para continuar.")
            .setView(wrap(f))
            .setCancelable(false)
            .setPositiveButton("Entrar") { _, _ ->
                if (store.checkPin(f.text.toString())) unlock()
                else { toast("PIN incorreto"); askPin() }
            }
            .setNegativeButton("Sair") { _, _ -> finish() }
            .show()
    }

    private fun unlock() {
        b.form.visibility = View.VISIBLE
        updateValidityLabel(b.validity.selectedItemPosition)
    }

    private fun pinField(hint: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        this.hint = hint
    }

    private fun wrap(v: View) = LinearLayout(this).apply { setPadding(48, 16, 48, 0); addView(v) }

    // ---------------- validade ----------------
    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, 23, 59, 59)
            customExpiry = cal.timeInMillis
            updateValidityLabel(5)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun expiryFor(pos: Int): Long = when (pos) {
        0 -> days(30); 1 -> days(90); 2 -> days(180); 3 -> days(365)
        4 -> 0L
        else -> customExpiry
    }

    private fun days(n: Int) = System.currentTimeMillis() + n * 86_400_000L

    private fun updateValidityLabel(pos: Int) {
        val e = expiryFor(pos)
        b.validityLabel.text = when {
            pos == 5 && e == 0L -> "Escolha uma data."
            e == 0L -> "Licença sem vencimento."
            else -> "Vence em ${fmt.format(Date(e))}."
        }
    }

    // ---------------- geração ----------------
    private fun generate() {
        val client = b.client.text.toString().trim()
        if (client.isEmpty()) { toast("Informe o nome do cliente"); return }
        val deviceId = b.deviceId.text.toString().trim().uppercase()
        val pos = b.validity.selectedItemPosition
        if (pos == 5 && customExpiry == 0L) { toast("Escolha a data de validade"); return }
        val expiry = expiryFor(pos)

        val number = store.nextNumber()
        val code = Signer.generate(client, deviceId, expiry, number)
        store.addHistory(client, deviceId, expiry, number, code)

        b.code.text = code
        b.result.visibility = View.VISIBLE
        toast("Licença #$number gerada")
    }

    private fun share() {
        val code = b.code.text.toString()
        if (code.isBlank()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, clientMessage(code))
        }, "Enviar código ao cliente"))
    }

    private fun clientMessage(code: String): String {
        val pos = b.validity.selectedItemPosition
        val e = expiryFor(pos)
        val venc = if (e == 0L) "Licença sem vencimento." else "Válido até ${fmt.format(Date(e))}."
        return "NeuraVision — código de ativação\n\n$venc\n\n" +
            "Como ativar:\n1) Abra o app NeuraVision\n2) Menu ⋮ → Licença / Ativar\n" +
            "3) Cole o código abaixo e toque em Ativar\n\n$code"
    }

    // ---------------- histórico ----------------
    private fun showHistory() {
        val arr: JSONArray = store.history()
        if (arr.length() == 0) { toast("Nenhuma licença emitida ainda"); return }
        val lines = ArrayList<String>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            val e = o.optLong("expiry", 0L)
            val venc = if (e == 0L) "sem vencimento" else "até ${fmt.format(Date(e))}"
            val dev = o.optString("deviceId", "").ifBlank { "qualquer aparelho" }
            lines.add("#${o.optInt("number")} · ${o.optString("client")}\n$dev · $venc")
        }
        AlertDialog.Builder(this)
            .setTitle("Licenças emitidas (${arr.length()})")
            .setItems(lines.toTypedArray()) { _, sel ->
                val o = arr.getJSONObject(arr.length() - 1 - sel)
                b.code.text = o.optString("code")
                b.result.visibility = View.VISIBLE
                toast("Código carregado — use Copiar ou Enviar")
            }
            .setPositiveButton("Fechar", null)
            .show()
    }

    // ---------------- util ----------------
    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("NeuraVision", text))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
