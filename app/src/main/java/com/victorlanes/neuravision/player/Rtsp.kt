package com.victorlanes.neuravision.player

import com.victorlanes.neuravision.model.Device
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

/** Monta as URLs RTSP e escolhe o endereço (local x DDNS). */
object Rtsp {

    private const val DEFAULT_PATH = "/cam/realmonitor?channel={ch}&subtype={st}"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Monta rtsp://usuario:senha@host:porta/caminho para um canal e qualidade. */
    fun url(host: String, port: Int, user: String, pass: String, channel: Int, sub: Boolean, path: String): String {
        val cred = if (user.isNotEmpty()) "${enc(user)}:${enc(pass)}@" else ""
        val tmpl = path.ifBlank { DEFAULT_PATH }
        val tail = tmpl.replace("{ch}", channel.toString()).replace("{st}", if (sub) "1" else "0")
        val sep = if (tail.startsWith("/")) "" else "/"
        return "rtsp://$cred$host:$port$sep$tail"
    }

    /** Testa se dá para abrir um socket TCP no host:porta (usado para saber se está na rede local). */
    fun reachable(host: String, port: Int, timeoutMs: Int = 700): Boolean {
        if (host.isBlank()) return false
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Escolhe o melhor endereço: tenta o LOCAL primeiro (quando ela está no Wi-Fi do local);
     * se não responder, usa o REMOTO (DDNS). Rode isto fora da thread principal.
     *
     * @return Triple(host, porta, ehRemoto) ou null se não há endereço cadastrado.
     */
    fun resolve(d: Device): Triple<String, Int, Boolean>? {
        if (d.hasLocal() && reachable(d.hostLocal, d.portFor(false))) {
            return Triple(d.hostLocal, d.portFor(false), false)
        }
        if (d.hasRemote()) {
            return Triple(d.hostRemote, d.portFor(true), true)
        }
        // sem remoto cadastrado: usa o local mesmo que o teste tenha falhado
        if (d.hasLocal()) return Triple(d.hostLocal, d.portFor(false), false)
        return null
    }
}
