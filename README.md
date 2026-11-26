# NeuraVision Mobile

App Android para ver as câmeras de um DVR/NVR Intelbras (ou qualquer equipamento com RTSP) no celular —
na rede local **e pela internet**, quando a cliente não estiver no estabelecimento.

- Toca o vídeo RTSP direto do equipamento (motor **VLC**: lida com H.264/H.265, usuário e senha).
- Mosaico de câmeras (1 / 4 / 9), toque para abrir em **tela cheia**, botão **HD/SD**.
- Guarda vários equipamentos, cada um com **endereço local + DDNS**. O app usa o local no Wi-Fi do
  estabelecimento e troca para o **DDNS** automaticamente quando está fora.
- Sem servidor e sem mensalidade. APK gerado pelo **GitHub Actions**.

---

## 1. Gerar o APK (GitHub Actions)

1. Crie um repositório no seu GitHub (ex.: `VictorLanes/neuravision-mobile`).
2. Suba estes arquivos (pode arrastar a pasta no site do GitHub, ou `git push`).
3. O build roda sozinho. Acompanhe na aba **Actions**.
4. Baixe o APK em um destes lugares:
   - **Actions → último build → Artifacts → NeuraVision-APK**, ou
   - **Releases** (ele publica uma release `v1.0-N` com o `NeuraVision.apk`).

> O APK é *debug* (não precisa de chave de assinatura). Instala normalmente; o Android pode pedir para
> permitir “instalar apps de fontes desconhecidas”.

## 2. Instalar no celular

1. Baixe o `NeuraVision.apk` no celular.
2. Abra o arquivo → permita a instalação de fontes desconhecidas se for pedido → **Instalar**.

## 3. Cadastrar o equipamento no app

Toque em **+** e preencha:

| Campo | O que colocar |
|---|---|
| Nome | Como quiser (ex.: “Loja da Ana”) |
| IP na rede do local | IP do DVR na rede dela (ex.: `192.168.1.108`) — opcional, para quando ela está no Wi-Fi do local |
| Acesso de fora (DDNS) | O endereço DDNS ou IP público (ver seção 4) |
| Porta RTSP | Normalmente **554** |
| Porta externa | Só se o roteador redireciona uma porta diferente (ex.: 5554 → 554) |
| Usuário / Senha | Login do DVR |
| Nº de câmeras | Quantas câmeras mostrar |

Pronto: toque no equipamento para ver as câmeras.

---

## 4. Guia: liberar o acesso de fora (DDNS + porta)

Para o celular ver o DVR pela internet, o roteador do **estabelecimento** precisa de duas coisas:
**um endereço que não muda (DDNS)** e **uma porta aberta até o DVR (redirecionamento)**.

### 4.1. DDNS (endereço fixo)
O IP público da internet da loja costuma mudar. O DDNS dá um nome fixo que sempre aponta para ela.

- **Intelbras:** no próprio DVR, em **Rede → DDNS**, ative o **DDNS Intelbras** e anote o endereço
  (algo como `xxxxx.ddns-intelbras.com.br`). Também dá para usar **No-IP** ou **DuckDNS** (grátis) no DVR
  ou no roteador.
- Esse endereço vai no campo **Acesso de fora (DDNS)** do app.

### 4.2. Redirecionamento de porta (NAT / “Virtual Server”)
No **roteador da loja**, redirecione a porta RTSP para o IP do DVR:

1. Entre no roteador (geralmente `192.168.0.1` ou `192.168.1.1`).
2. Procure **Redirecionamento de portas** / **Port Forwarding** / **Virtual Server** / **NAT**.
3. Crie uma regra:
   - **Porta externa:** 554 (ou outra, ex.: 5554 — mais seguro)
   - **Porta interna:** 554
   - **IP interno:** o IP do DVR (ex.: `192.168.1.108`)
   - **Protocolo:** TCP (pode deixar TCP/UDP)
4. Salve. Se usou uma porta externa diferente (ex.: 5554), coloque-a no campo **Porta externa** do app.

> Dica Intelbras: alguns roteadores Intelbras chamam isso de **“Encaminhamento de porta”** em
> *Avançado → NAT*. Nos TP-Link é *Encaminhamento → Servidores Virtuais*. Nos Mikrotik é *IP → Firewall → NAT*.

### 4.3. Fixe o IP do DVR
Para o redirecionamento não quebrar, deixe o DVR com **IP fixo** (no próprio DVR em *Rede*, ou reserva de
DHCP no roteador).

### 4.4. Segurança (importante)
Abrir a porta expõe o DVR na internet. Reduza o risco:
- Troque a **senha padrão** do DVR por uma **forte**.
- Prefira uma **porta externa diferente** de 554 (ex.: 5554).
- Se o provedor usa **CGNAT** (IP compartilhado), o redirecionamento não funciona — peça **IP público**
  ao provedor, ou use uma VPN.

---

## 5. Como o app escolhe o endereço
Ao abrir as câmeras, o app testa primeiro o **IP local** (rápido, quando ela está no Wi-Fi do local);
se não responder, usa o **DDNS**. A barra de cima mostra qual está em uso (“Rede local” ou “Acesso externo”).

## 6. Dicas de desempenho
- No mosaico o app usa o **stream secundário** (mais leve) — ideal para 4G/5G. Em tela cheia dá para ligar o **HD**.
- Em internet fraca, veja menos câmeras por vez (layout 1 ou 4).

## 7. Problemas comuns
| Sintoma | Provável causa |
|---|---|
| “Sem sinal” só fora do Wi-Fi | DDNS ou redirecionamento de porta incompletos (seção 4) |
| Não conecta nem no local | IP, usuário ou senha errados, ou RTSP desabilitado no DVR |
| Trava/corta no 4G | Rede fraca — use o stream secundário (padrão) e menos câmeras |
| Pede senha e não entra | Confira usuário/senha; alguns DVRs têm senha própria de stream |

---

## Estrutura
- `app/src/main/java/.../model` · `data` — equipamento e armazenamento local (JSON).
- `.../player` — `Rtsp` (monta URL, escolhe local/DDNS), `VlcEngine`, `CameraTile` (vídeo via libVLC).
- `.../ui` — lista, cadastro e ao vivo (mosaico + tela cheia).
- `.github/workflows/build.yml` — gera o APK.

Feito para Intelbras/Dahua (caminho RTSP `/cam/realmonitor?channel={ch}&subtype={st}`). Para outras marcas,
ajuste o **Caminho RTSP** em *Opções avançadas* no cadastro.
