package com.victorlanes.neuravision.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.victorlanes.neuravision.R
import com.victorlanes.neuravision.data.BackupError
import com.victorlanes.neuravision.data.BackupManager
import com.victorlanes.neuravision.data.DeviceStore
import com.victorlanes.neuravision.data.Session
import com.victorlanes.neuravision.databinding.ActivityMainBinding
import com.victorlanes.neuravision.model.Device

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var store: DeviceStore
    private val items = mutableListOf<Device>()
    private lateinit var adapter: DeviceAdapter

    private var pendingExportPassword = ""
    private var pendingImportBytes: ByteArray? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeBackup(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = DeviceStore(this)
        setSupportActionBar(b.toolbar)

        adapter = DeviceAdapter(items,
            onOpen = { openLive(it) },
            onEdit = { edit(it.id) },
            onDelete = { confirmDelete(it) })
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.fab.setOnClickListener { edit(null) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    // ---- re-bloqueio ao voltar de segundo plano ----
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Session.pausedAt = System.currentTimeMillis()
    }

    override fun onRestart() {
        super.onRestart()
        if (Session.shouldRelock()) {
            Session.lock()
            startActivity(Intent(this, LockActivity::class.java))
            finish()
        }
    }

    // ---- menu ----
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_backup -> startExport()
            R.id.action_import -> importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            R.id.action_security -> startActivity(Intent(this, SecurityActivity::class.java))
            R.id.action_lock -> { Session.lock(); startActivity(Intent(this, LockActivity::class.java)); finish() }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---- backup ----
    private fun startExport() {
        if (store.all().isEmpty()) { toast("Não há equipamentos para salvar"); return }
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Senha do backup (recomendado)"
        }
        AlertDialog.Builder(this)
            .setTitle("Fazer backup")
            .setMessage("O backup guarda os seus equipamentos (inclui usuário e senha das câmeras). "
                + "Proteja com uma senha — ela será pedida ao importar.")
            .setView(LinearLayout(this).apply { setPadding(48, 16, 48, 0); addView(field) })
            .setPositiveButton("Salvar arquivo") { _, _ ->
                pendingExportPassword = field.text.toString()
                exportLauncher.launch("neuravision_backup.nvbackup.json")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun writeBackup(uri: Uri) {
        try {
            val bytes = BackupManager.build(store.all(), pendingExportPassword)
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            toast("Backup salvo com ${store.all().size} equipamento(s)")
        } catch (e: Exception) {
            toast("Não consegui salvar o backup: ${e.message}")
        } finally {
            pendingExportPassword = ""
        }
    }

    private fun readBackup(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        } catch (e: Exception) {
            toast("Não consegui abrir o arquivo"); return
        }
        try {
            applyImport(BackupManager.read(bytes, ""))
        } catch (e: BackupError) {
            if (e.message?.contains("senha", ignoreCase = true) == true) {
                pendingImportBytes = bytes
                askImportPassword()
            } else {
                toast(e.message ?: "Arquivo inválido")
            }
        }
    }

    private fun askImportPassword() {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Senha do backup"
        }
        AlertDialog.Builder(this)
            .setTitle("Importar backup")
            .setMessage("Este backup tem senha. Informe para restaurar.")
            .setView(LinearLayout(this).apply { setPadding(48, 16, 48, 0); addView(field) })
            .setPositiveButton("Importar") { _, _ ->
                val bytes = pendingImportBytes ?: return@setPositiveButton
                try {
                    applyImport(BackupManager.read(bytes, field.text.toString()))
                } catch (e: BackupError) {
                    toast(e.message ?: "Senha incorreta")
                } finally {
                    pendingImportBytes = null
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyImport(devices: List<Device>) {
        if (devices.isEmpty()) { toast("O backup não tinha equipamentos"); return }
        for (d in devices) store.save(d)
        reload()
        toast("${devices.size} equipamento(s) importado(s)")
    }

    // ---- lista ----
    private fun reload() {
        items.clear()
        items.addAll(store.all())
        adapter.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun edit(id: String?) {
        startActivity(Intent(this, DeviceEditActivity::class.java).apply { if (id != null) putExtra("id", id) })
    }

    private fun openLive(d: Device) {
        startActivity(Intent(this, LiveActivity::class.java).putExtra("id", d.id))
    }

    private fun confirmDelete(d: Device) {
        AlertDialog.Builder(this)
            .setTitle(d.name)
            .setMessage("Remover este equipamento do app?")
            .setPositiveButton("Remover") { _, _ -> store.delete(d.id); reload() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- adapter ----
    private class DeviceAdapter(
        val data: List<Device>,
        val onOpen: (Device) -> Unit,
        val onEdit: (Device) -> Unit,
        val onDelete: (Device) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val detail: TextView = v.findViewById(R.id.detail)
            val menu: View = v.findViewById(R.id.menu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
            return VH(v)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val d = data[position]
            h.name.text = d.name
            val addr = when {
                d.hasRemote() -> d.hostRemote
                d.hasLocal() -> d.hostLocal
                else -> "sem endereço"
            }
            h.detail.text = "$addr · ${d.channels} câmera(s)"
            h.itemView.setOnClickListener { onOpen(d) }
            h.menu.setOnClickListener { v ->
                androidx.appcompat.widget.PopupMenu(v.context, v).apply {
                    menu.add("Ver câmeras").setOnMenuItemClickListener { onOpen(d); true }
                    menu.add("Editar").setOnMenuItemClickListener { onEdit(d); true }
                    menu.add("Remover").setOnMenuItemClickListener { onDelete(d); true }
                    show()
                }
            }
        }
    }
}
