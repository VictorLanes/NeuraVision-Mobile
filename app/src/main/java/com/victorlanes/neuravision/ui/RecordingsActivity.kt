package com.victorlanes.neuravision.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.victorlanes.neuravision.R
import com.victorlanes.neuravision.databinding.ActivityRecordingsBinding
import com.victorlanes.neuravision.player.MediaSaver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista as gravações feitas no app, com abrir, compartilhar e apagar. */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityRecordingsBinding
    private val items = mutableListOf<File>()
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = Adapter(items, ::open, ::share, ::confirmDelete)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        items.clear()
        items.addAll(MediaSaver.listMedia(this))
        adapter.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun uriFor(f: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)

    private fun open(f: File) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(f), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Nenhum app para abrir o vídeo", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(f: File) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uriFor(f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar gravação"))
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("Apagar")
            .setMessage("Apagar esta gravação do celular?")
            .setPositiveButton("Apagar") { _, _ -> f.delete(); reload() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class Adapter(
        val data: List<File>,
        val onOpen: (File) -> Unit,
        val onShare: (File) -> Unit,
        val onDelete: (File) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val detail: TextView = v.findViewById(R.id.detail)
            val share: View = v.findViewById(R.id.share)
            val del: View = v.findViewById(R.id.del)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val f = data[position]
            h.name.text = f.name
            val size = Formatter.formatShortFileSize(h.itemView.context, f.length())
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
            h.detail.text = "$date · $size"
            h.itemView.setOnClickListener { onOpen(f) }
            h.share.setOnClickListener { onShare(f) }
            h.del.setOnClickListener { onDelete(f) }
        }
    }
}
