package com.victorlanes.neuravision.player

import android.content.Context
import org.videolan.libvlc.LibVLC

/** Uma instância do LibVLC por tela, compartilhada pelos vários players do mosaico. */
object VlcEngine {

    fun create(context: Context): LibVLC {
        val options = arrayListOf(
            "--rtsp-tcp",            // RTSP por TCP: mais confiável pela internet
            "--network-caching=1500",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--no-sub-autodetect-file",
            "--avcodec-hw=any",      // usa decodificação por hardware quando possível (H.264/H.265)
            "-vvv"
        )
        return LibVLC(context.applicationContext, options)
    }
}
