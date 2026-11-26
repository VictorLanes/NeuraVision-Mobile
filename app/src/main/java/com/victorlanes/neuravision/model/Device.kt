package com.victorlanes.neuravision.model

import java.util.UUID

/**
 * Um equipamento (DVR/NVR/câmera) que o app vai ver.
 *
 * - [hostLocal]  IP na rede do estabelecimento (ex.: 192.168.1.108) — usado quando o celular está no Wi-Fi do local.
 * - [hostRemote] endereço de fora: DDNS (ex.: loja.ddns-intelbras.com.br) ou IP público — usado quando ela está longe.
 * - [port]       porta RTSP (padrão 554; se o roteador redireciona outra porta externa, use [portRemote]).
 * - [path]       modelo do caminho RTSP. Vazio = padrão Intelbras/Dahua. Use {ch} e {st} para canal e substream.
 */
data class Device(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var hostLocal: String = "",
    var hostRemote: String = "",
    var port: Int = 554,
    var portRemote: Int = 0,          // 0 = mesma porta local (roteador redireciona igual)
    var user: String = "admin",
    var pass: String = "",
    var channels: Int = 4,
    var path: String = "",            // vazio = /cam/realmonitor?channel={ch}&subtype={st}
    var startChannel: Int = 1
) {
    fun portFor(remote: Boolean): Int = if (remote && portRemote > 0) portRemote else port

    fun hasRemote(): Boolean = hostRemote.isNotBlank()
    fun hasLocal(): Boolean = hostLocal.isNotBlank()
}
