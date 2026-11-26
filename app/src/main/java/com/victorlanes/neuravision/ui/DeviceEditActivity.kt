package com.victorlanes.neuravision.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.DeviceStore
import com.victorlanes.neuravision.databinding.ActivityDeviceEditBinding
import com.victorlanes.neuravision.model.Device

class DeviceEditActivity : AppCompatActivity() {

    private lateinit var b: ActivityDeviceEditBinding
    private lateinit var store: DeviceStore
    private var device = Device()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDeviceEditBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = DeviceStore(this)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getStringExtra("id")
        if (id != null) {
            store.get(id)?.let { device = it }
            b.toolbar.title = "Editar equipamento"
        } else {
            b.toolbar.title = "Novo equipamento"
        }
        fill()

        b.advancedToggle.setOnClickListener {
            val show = b.advanced.visibility != android.view.View.VISIBLE
            b.advanced.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            b.advancedToggle.text = if (show) "Opções avançadas ▲" else "Opções avançadas ▼"
        }

        b.save.setOnClickListener { save() }
    }

    private fun fill() {
        b.name.setText(device.name)
        b.hostLocal.setText(device.hostLocal)
        b.hostRemote.setText(device.hostRemote)
        b.port.setText(device.port.toString())
        b.portRemote.setText(if (device.portRemote > 0) device.portRemote.toString() else "")
        b.user.setText(device.user)
        b.pass.setText(device.pass)
        b.channels.setText(device.channels.toString())
        b.startChannel.setText(device.startChannel.toString())
        b.path.setText(device.path)
    }

    private fun save() {
        val name = b.name.text.toString().trim()
        val local = b.hostLocal.text.toString().trim()
        val remote = b.hostRemote.text.toString().trim()
        if (name.isEmpty()) {
            b.name.error = "Dê um nome (ex.: Loja da Ana)"
            return
        }
        if (local.isEmpty() && remote.isEmpty()) {
            b.hostRemote.error = "Informe o endereço local e/ou o DDNS"
            return
        }
        device.apply {
            this.name = name
            hostLocal = local
            hostRemote = remote
            port = b.port.text.toString().toIntOrNull() ?: 554
            portRemote = b.portRemote.text.toString().toIntOrNull() ?: 0
            user = b.user.text.toString().trim().ifEmpty { "admin" }
            pass = b.pass.text.toString()
            channels = b.channels.text.toString().toIntOrNull()?.coerceIn(1, 64) ?: 4
            startChannel = b.startChannel.text.toString().toIntOrNull()?.coerceIn(0, 64) ?: 1
            path = b.path.text.toString().trim()
        }
        store.save(device)
        Toast.makeText(this, "Equipamento salvo", Toast.LENGTH_SHORT).show()
        finish()
    }
}
