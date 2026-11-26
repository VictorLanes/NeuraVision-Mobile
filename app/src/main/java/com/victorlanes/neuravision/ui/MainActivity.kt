package com.victorlanes.neuravision.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.victorlanes.neuravision.R
import com.victorlanes.neuravision.data.DeviceStore
import com.victorlanes.neuravision.databinding.ActivityMainBinding
import com.victorlanes.neuravision.model.Device

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var store: DeviceStore
    private val items = mutableListOf<Device>()
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = DeviceStore(this)

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

    private fun reload() {
        items.clear()
        items.addAll(store.all())
        adapter.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun edit(id: String?) {
        startActivity(Intent(this, DeviceEditActivity::class.java).apply {
            if (id != null) putExtra("id", id)
        })
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

    // ---------------------------------------------------------------- adapter
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
