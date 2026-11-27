package com.victorlanes.neuravision.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.victorlanes.neuravision.R
import com.victorlanes.neuravision.databinding.ViewCameraTileBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Quadro de câmera (libVLC) com controles: foto, gravar, expandir e menu.
 * O vídeo é desenhado num TextureView (modo do attachViews) para permitir o snapshot.
 */
@SuppressLint("SetTextI18n")
class CameraTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val b = ViewCameraTileBinding.inflate(LayoutInflater.from(context), this, true)
    private var player: MediaPlayer? = null
    private var recording = false

    var channel: Int = 0
        private set

    // callbacks ligados pela tela
    var onExpand: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onInfo: ((String) -> Unit)? = null

    init {
        b.btnSnap.setOnClickListener { takeSnapshot() }
        b.btnRec.setOnClickListener { toggleRecord() }
        b.btnExpand.setOnClickListener { onExpand?.invoke() }
        b.btnMenu.setOnClickListener { onMenu?.invoke() }
    }

    fun setTitle(text: String) { b.title.text = text }

    fun highlight(sel: Boolean) {
        b.root.setBackgroundResource(if (sel) R.drawable.tile_selected else R.drawable.tile_border)
    }

    fun controlsVisible(): Boolean = b.controls.visibility == View.VISIBLE
    fun showControls(show: Boolean) { b.controls.visibility = if (show) View.VISIBLE else View.GONE }
    fun toggleControls() = showControls(!controlsVisible())

    /** Em tela cheia o botão de expandir vira "voltar". */
    fun setExpanded(expanded: Boolean) {
        b.btnExpand.setImageResource(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand)
    }

    fun play(engine: LibVLC, url: String, channel: Int, title: String, sub: Boolean) {
        this.channel = channel
        setTitle(title)
        stop()
        showStatus("Conectando…", false)

        val mp = MediaPlayer(engine)
        player = mp
        mp.attachViews(b.videoLayout, null, false, true)   // true = TextureView (necessário p/ snapshot)
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

    // ------------------------------------------------------------ snapshot
    private fun findTexture(v: View): TextureView? {
        if (v is TextureView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findTexture(v.getChildAt(i))?.let { return it }
        return null
    }

    fun takeSnapshot() {
        val tv = findTexture(b.videoLayout)
        val bmp = tv?.bitmap
        if (bmp == null) { onInfo?.invoke("Não consegui capturar a imagem agora"); return }
        val name = MediaSaver.saveSnapshot(context, bmp, b.title.text.toString().ifBlank { "cam$channel" })
        onInfo?.invoke(if (name != null) "Foto salva na galeria" else "Falha ao salvar a foto")
    }

    // ------------------------------------------------------------ gravação
    fun isRecording(): Boolean = recording

    fun toggleRecord() {
        val mp = player ?: return
        if (!recording) {
            val dir = MediaSaver.recordingsDir(context)
            val ok = try { mp.record(dir.absolutePath) } catch (e: Throwable) { false }
            if (ok) {
                recording = true
                b.recDot.visibility = View.VISIBLE
                onInfo?.invoke("Gravando…")
            } else {
                onInfo?.invoke("Não foi possível iniciar a gravação")
            }
        } else {
            try { mp.record(null) } catch (e: Throwable) { /* ignore */ }
            recording = false
            b.recDot.visibility = View.GONE
            onInfo?.invoke("Salvando gravação…")
            // o libVLC fecha o arquivo logo após parar; aí publicamos na galeria
            postDelayed({
                val name = MediaSaver.publishNewestRecording(context)
                onInfo?.invoke(if (name != null) "Gravação salva na galeria (álbum NeuraVision)"
                               else "Gravação salva (veja em Minhas gravações)")
            }, 1500)
        }
    }

    fun stop() {
        player?.let { mp ->
            try {
                if (recording) { try { mp.record(null) } catch (e: Throwable) {} ; recording = false; b.recDot.visibility = View.GONE }
                mp.setEventListener(null)
                if (mp.isPlaying) mp.stop()
                mp.detachViews()
                mp.release()
            } catch (e: Exception) {
                // ignora erros ao liberar
            }
        }
        player = null
    }

    private fun showStatus(text: String, error: Boolean) {
        b.status.text = text
        b.status.visibility = View.VISIBLE
        b.status.setTextColor(if (error) 0xFFFF6B6B.toInt() else 0xFFBFD4E6.toInt())
    }

    private fun hideStatus() { b.status.visibility = View.GONE }
}
