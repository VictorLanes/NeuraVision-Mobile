package com.victorlanes.neuravision.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.DeviceStore
import com.victorlanes.neuravision.databinding.ActivityLiveBinding
import com.victorlanes.neuravision.model.Device
import com.victorlanes.neuravision.player.CameraTile
import com.victorlanes.neuravision.player.Rtsp
import com.victorlanes.neuravision.player.VlcEngine
import org.videolan.libvlc.LibVLC

@SuppressLint("SetTextI18n")
class LiveActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveBinding
    private lateinit var device: Device
    private var libVlc: LibVLC? = null

    private var host = ""
    private var port = 554
    private var remote = false
    private var resolved = false

    private var layout = 4          // 1, 4 ou 9 câmeras por tela
    private var page = 0
    private var single: Int? = null // canal em tela cheia (null = mosaico)
    private var hd = false          // tela cheia em alta (stream principal)

    private val tiles = mutableListOf<CameraTile>()
    private lateinit var channels: List<Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val d = intent.getStringExtra("id")?.let { DeviceStore(this).get(it) }
        if (d == null) {
            Toast.makeText(this, "Equipamento não encontrado", Toast.LENGTH_LONG).show()
            finish(); return
        }
        device = d
        channels = (0 until device.channels).map { device.startChannel + it }
        layout = if (device.channels <= 1) 1 else 4

        b.title.text = device.name
        b.back.setOnClickListener { handleBack() }
        b.btnLayout.setOnClickListener { cycleLayout() }
        b.btnPrev.setOnClickListener { if (page > 0) { page--; render() } }
        b.btnNext.setOnClickListener { if ((page + 1) * layout < channels.size) { page++; render() } }
        b.btnHd.setOnClickListener { hd = !hd; updateHdLabel(); render() }

        libVlc = VlcEngine.create(this)
        resolveThenRender()
    }

    private fun resolveThenRender() {
        b.status.text = "Procurando o equipamento…"
        b.status.visibility = View.VISIBLE
        Thread {
            val r = Rtsp.resolve(device)
            runOnUiThread {
                if (r == null) {
                    b.status.text = "Nenhum endereço cadastrado."
                    return@runOnUiThread
                }
                host = r.first; port = r.second; remote = r.third; resolved = true
                b.addr.text = (if (remote) "Acesso externo · " else "Rede local · ") + host
                b.status.visibility = View.GONE
                render()
            }
        }.start()
    }

    private fun cycleLayout() {
        layout = when (layout) { 1 -> 4; 4 -> 9; else -> 1 }
        single = null
        page = 0
        render()
    }

    private fun updateHdLabel() {
        b.btnHd.text = if (hd) "HD" else "SD"
    }

    private fun render() {
        if (!resolved) return
        stopTiles()
        b.grid.removeAllViews()
        tiles.clear()

        val engine = libVlc ?: return
        val single = single
        if (single != null) {
            b.grid.rowCount = 1
            b.grid.columnCount = 1
            addTile(engine, single, sub = !hd)
            b.pager.visibility = View.GONE
            b.btnHd.visibility = View.VISIBLE
            return
        }

        val cols = when (layout) { 1 -> 1; 4 -> 2; else -> 3 }
        b.grid.rowCount = cols
        b.grid.columnCount = cols
        b.btnHd.visibility = View.GONE

        val start = page * layout
        val slice = channels.drop(start).take(layout)
        for (ch in slice) addTile(engine, ch, sub = true)

        val pages = (channels.size + layout - 1) / layout
        b.pager.visibility = if (pages > 1) View.VISIBLE else View.GONE
        b.pageLabel.text = "${page + 1}/$pages"
        b.btnPrev.isEnabled = page > 0
        b.btnNext.isEnabled = (page + 1) < pages
    }

    private fun addTile(engine: LibVLC, channel: Int, sub: Boolean) {
        val tile = CameraTile(this)
        val lp = GridLayout.LayoutParams().apply {
            width = 0; height = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(2, 2, 2, 2)
        }
        tile.layoutParams = lp
        tile.setOnClickListener { toggleSingle(channel) }
        b.grid.addView(tile)
        tiles.add(tile)

        val url = Rtsp.url(host, port, device.user, device.pass, channel, sub, device.path)
        tile.play(engine, url, channel, "Câmera $channel", sub)
    }

    private fun toggleSingle(channel: Int) {
        single = if (single == null) channel else null
        render()
    }

    private fun stopTiles() {
        for (t in tiles) t.stop()
    }

    override fun onStop() {
        super.onStop()
        stopTiles()
    }

    override fun onStart() {
        super.onStart()
        if (resolved && tiles.isEmpty()) render()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        if (single != null) {
            single = null
            render()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTiles()
        libVlc?.release()
        libVlc = null
    }
}
