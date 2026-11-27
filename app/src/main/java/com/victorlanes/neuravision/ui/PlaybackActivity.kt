package com.victorlanes.neuravision.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.DeviceStore
import com.victorlanes.neuravision.databinding.ActivityPlaybackBinding
import com.victorlanes.neuravision.model.Device
import com.victorlanes.neuravision.player.MediaSaver
import com.victorlanes.neuravision.player.Rtsp
import com.victorlanes.neuravision.player.VlcEngine
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Reproduz a gravação do DVR por data/hora (RTSP playback) e permite baixar. */
class PlaybackActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlaybackBinding
    private lateinit var device: Device
    private var channel = 1
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var recording = false

    private val cal = Calendar.getInstance()
    private val durations = listOf(15, 30, 60, 120)   // minutos

    private var host = ""
    private var port = 554

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        val d = intent.getStringExtra("id")?.let { DeviceStore(this).get(it) }
        if (d == null) { Toast.makeText(this, "Equipamento não encontrado", Toast.LENGTH_LONG).show(); finish(); return }
        device = d
        channel = intent.getIntExtra("channel", 1)
        b.toolbar.title = "Gravação · Câmera $channel"

        cal.add(Calendar.HOUR_OF_DAY, -1)       // começa 1h atrás por padrão

        b.spDuration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            durations.map { "$it min" })
        b.spDuration.setSelection(1)

        b.btnDate.setOnClickListener { pickDate() }
        b.btnTime.setOnClickListener { pickTime() }
        b.btnPlay.setOnClickListener { playback() }
        b.btnDownload.setOnClickListener { toggleDownload() }
        updateLabels()

        libVlc = VlcEngine.create(this)
        Thread {
            val r = Rtsp.resolve(device)
            runOnUiThread { if (r != null) { host = r.first; port = r.second } }
        }.start()
    }

    private fun updateLabels() {
        b.btnDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
        b.btnTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, day ->
            cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, day); updateLabels()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0); updateLabels()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun playback() {
        if (host.isBlank()) { Toast.makeText(this, "Ainda procurando o equipamento…", Toast.LENGTH_SHORT).show(); return }
        val engine = libVlc ?: return
        stopPlayer()
        b.status.text = "Carregando gravação…"
        b.status.visibility = View.VISIBLE

        val start = cal.time
        val mins = durations[b.spDuration.selectedItemPosition]
        val end = Date(start.time + mins * 60_000L)
        val url = Rtsp.playbackUrl(host, port, device.user, device.pass, channel, start, end)

        val mp = MediaPlayer(engine)
        player = mp
        mp.attachViews(b.videoLayout, null, false, true)
        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        val media = Media(engine, Uri.parse(url))
        media.addOption(":network-caching=2000")
        media.addOption(":rtsp-tcp")
        mp.media = media
        media.release()
        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> { b.status.visibility = View.GONE; b.btnDownload.visibility = View.VISIBLE }
                MediaPlayer.Event.EncounteredError -> b.status.apply { text = "Não encontrei gravação nesse horário (ou o DVR não libera playback por RTSP)."; visibility = View.VISIBLE }
                MediaPlayer.Event.EndReached -> b.status.apply { text = "Fim da gravação."; visibility = View.VISIBLE }
            }
        }
        mp.play()
    }

    private fun toggleDownload() {
        val mp = player ?: return
        if (!recording) {
            val ok = try { mp.record(MediaSaver.recordingsDir(this).absolutePath) } catch (e: Throwable) { false }
            if (ok) { recording = true; b.recDot.visibility = View.VISIBLE; b.btnDownload.text = "Parar o download"
                Toast.makeText(this, "Baixando a gravação…", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(this, "Este DVR não permitiu baixar por aqui", Toast.LENGTH_LONG).show()
        } else {
            try { mp.record(null) } catch (e: Throwable) {}
            recording = false; b.recDot.visibility = View.GONE; b.btnDownload.text = "Baixar esta gravação"
            Toast.makeText(this, "Salvando…", Toast.LENGTH_SHORT).show()
            b.recDot.postDelayed({
                val name = MediaSaver.publishNewestRecording(this)
                Toast.makeText(this, if (name != null) "Salvo na galeria (álbum NeuraVision)"
                                     else "Salvo (veja em Minhas gravações)", Toast.LENGTH_LONG).show()
            }, 1500)
        }
    }

    private fun stopPlayer() {
        player?.let { mp ->
            try {
                if (recording) { try { mp.record(null) } catch (e: Throwable) {}; recording = false; b.recDot.visibility = View.GONE }
                mp.setEventListener(null)
                if (mp.isPlaying) mp.stop()
                mp.detachViews()
                mp.release()
            } catch (e: Exception) {}
        }
        player = null
    }

    override fun onStop() { super.onStop(); stopPlayer() }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayer()
        libVlc?.release(); libVlc = null
    }
}
