package com.victorlanes.neuravision.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.victorlanes.neuravision.R
import com.victorlanes.neuravision.databinding.ViewCameraTileBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Um quadro de câmera: mostra o vídeo (libVLC), o título e o estado (conectando / sem sinal).
 * Chame [play] para abrir uma URL e [stop] para liberar. É reutilizável.
 */
@SuppressLint("SetTextI18n")
class CameraTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val b = ViewCameraTileBinding.inflate(LayoutInflater.from(context), this, true)
    private var player: MediaPlayer? = null
    private var libVlc: LibVLC? = null
    private var attached = false
    var channel: Int = 0
        private set

    val videoLayout: VLCVideoLayout get() = b.videoLayout

    fun setTitle(text: String) {
        b.title.text = text
    }

    fun setSelected(sel: Boolean) {
        b.root.setBackgroundResource(if (sel) R.drawable.tile_selected else R.drawable.tile_border)
    }

    fun play(engine: LibVLC, url: String, channel: Int, title: String, sub: Boolean) {
        this.channel = channel
        this.libVlc = engine
        setTitle(title)
        stop()                       // garante que não há player anterior
        showStatus("Conectando…", false)

        val mp = MediaPlayer(engine)
        player = mp
        if (!attached) {
            mp.attachViews(b.videoLayout, null, false, false)
            attached = true
        } else {
            mp.attachViews(b.videoLayout, null, false, false)
        }
        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT

        val media = Media(engine, Uri.parse(url))
        media.addOption(":network-caching=${if (sub) 1200 else 2000}")
        media.addOption(":rtsp-tcp")
        mp.media = media
        media.release()

        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> hideStatus()
                MediaPlayer.Event.Buffering -> if (ev.buffering < 100f && player?.isPlaying != true) showStatus("Carregando…", false)
                MediaPlayer.Event.EncounteredError -> showStatus("Sem sinal", true)
                MediaPlayer.Event.EndReached -> showStatus("Sem sinal", true)
            }
        }
        mp.play()
    }

    fun stop() {
        player?.let { mp ->
            try {
                mp.setEventListener(null)
                if (mp.isPlaying) mp.stop()
                mp.detachViews()
                mp.release()
            } catch (e: Exception) {
                // ignora erros ao liberar
            }
        }
        player = null
        attached = false
    }

    private fun showStatus(text: String, error: Boolean) {
        b.status.text = text
        b.status.visibility = VISIBLE
        b.status.setTextColor(if (error) 0xFFFF6B6B.toInt() else 0xFFBFD4E6.toInt())
    }

    private fun hideStatus() {
        b.status.visibility = GONE
    }
}
