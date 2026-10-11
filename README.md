# ALX VPN

App Android de VPN (estilo injetor) com configuração manual de servidor e vários protocolos modernos.
Núcleo: **sing-box** (compilado como `libbox.aar` direto no GitHub Actions). UI: Kotlin + Jetpack Compose.

## Protocolos
VLESS (TLS / Reality), VMess, Trojan, Shadowsocks (incl. 2022), ShadowTLS, Hysteria2, TUIC, WireGuard,
SSH (com payload via proxy HTTP/SOCKS, opcionalmente com SSL/TLS), HTTP Proxy / HTTPS Proxy, SOCKS5.
Transportes: TCP, WebSocket, HTTPUpgrade, HTTP/2, gRPC, QUIC.

## Ajustes
DNS (Cloudflare, Google, Quad9, AdGuard, personalizado) + DNS de bootstrap, IPv6, ignorar rede local,
MTU, nível de log e split tunneling por app (somente selecionados / todos exceto selecionados).

## Recursos de injetor
- Servidor (host/IP) separado de **SNI** e **Host** (domínio de fachada)
- Path e **headers extras** customizados (WS / HTTPUpgrade)
- Fingerprint uTLS, ALPN, ignorar certificado inválido
- Importar links `vless://` `vmess://` `trojan://` `ss://` `hy2://` `tuic://` (botão “Colar link”, aceita vários, um por linha)

## Como compilar na nuvem
1. Crie um repositório no GitHub e envie **todo o conteúdo deste zip** (inclusive a pasta oculta `.github`).
2. Vá em **Actions → Build ALX VPN → Run workflow** (ou faça um push na branch `main`).
3. Ao terminar, baixe o APK em **Artifacts → ALX-VPN-apk**.

O workflow instala Go + NDK, compila o `libbox.aar` do sing-box (versão em `SING_BOX_VERSION`, em `.github/workflows/build.yml`) e roda `gradle assembleRelease`.

## Observações
- O APK sai assinado com a chave *debug* (instala direto). Para publicar na Play Store, use sua própria keystore.
- O código fala com o libbox por reflexão em alguns pontos para tolerar mudanças de versão, mas se mudar `SING_BOX_VERSION` e der erro de compilação, envie o log.
- Limitações: não há payload HTTP “cru” sobre TCP nem SSH com payload (o sing-box não suporta); use WS/HTTPUpgrade com Host/SNI customizados.
- Use somente com servidores que você controla ou tem autorização para usar.
