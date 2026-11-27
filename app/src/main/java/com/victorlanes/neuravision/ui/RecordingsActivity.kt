package com.victorlanes.neuravision.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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

/** Lista as gravações do álbum NeuraVision (galeria), com abrir, compartilhar e apagar. */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityRecordingsBinding
    private val items = mutableListOf<MediaSaver.Rec>()
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
        items.addAll(MediaSaver.listRecordings(this))
        adapter.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun shareableUri(rec: MediaSaver.Rec): Uri =
        if (rec.uri.scheme == "file") FileProvider.getUriForFile(this, "$packageName.fileprovider", File(rec.uri.path!!))
        else rec.uri

    private fun open(rec: MediaSaver.Rec) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareableUri(rec), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum app para abrir o vídeo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(rec: MediaSaver.Rec) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, shareableUri(rec))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar gravação"))
    }

    private fun confirmDelete(rec: MediaSaver.Rec) {
        AlertDialog.Builder(this)
            .setTitle("Apagar")
            .setMessage("Apagar esta gravação do celular?")
            .setPositiveButton("Apagar") { _, _ ->
                if (MediaSaver.delete(this, rec)) reload()
                else Toast.makeText(this, "Não consegui apagar", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class Adapter(
        val data: List<MediaSaver.Rec>,
        val onOpen: (MediaSaver.Rec) -> Unit,
        val onShare: (MediaSaver.Rec) -> Unit,
        val onDelete: (MediaSaver.Rec) -> Unit
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
            val rec = data[position]
            h.name.text = rec.name
            val size = Formatter.formatShortFileSize(h.itemView.context, rec.size)
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(rec.date))
            h.detail.text = "$date · $size"
            h.itemView.setOnClickListener { onOpen(rec) }
            h.share.setOnClickListener { onShare(rec) }
            h.del.setOnClickListener { onDelete(rec) }
        }
    }
}
