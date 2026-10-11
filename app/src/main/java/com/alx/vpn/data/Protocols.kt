package com.alx.vpn.data

data class FieldSpec(
    val key: String,
    val label: String,
    val options: List<String>? = null,
    val multiline: Boolean = false,
    val number: Boolean = false,
)

object Protocols {
    val all = listOf(
        "vless", "vmess", "trojan", "shadowsocks", "shadowtls",
        "hysteria2", "tuic", "wireguard", "ssh", "http", "socks",
    )

    val labels = mapOf(
        "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan (TLS)",
        "shadowsocks" to "Shadowsocks", "shadowtls" to "ShadowTLS (SS + TLS)",
        "hysteria2" to "Hysteria2", "tuic" to "TUIC", "wireguard" to "WireGuard",
        "ssh" to "SSH (com payload via proxy)", "http" to "HTTP Proxy / HTTPS", "socks" to "SOCKS5",
    )

    fun label(p: String) = labels[p] ?: p.uppercase()

    private val ssMethods = listOf(
        "chacha20-ietf-poly1305", "aes-128-gcm", "aes-256-gcm",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )
    private val fps = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "random")

    fun defaults(p: String): Map<String, String> = when (p) {
        "vless", "vmess", "trojan" -> mapOf("port" to "443", "network" to "tcp", "security" to "tls")
        "shadowsocks" -> mapOf("port" to "8388", "method" to "chacha20-ietf-poly1305")
        "shadowtls" -> mapOf("port" to "443", "method" to "chacha20-ietf-poly1305", "stlsVersion" to "3", "fp" to "chrome")
        "hysteria2" -> mapOf("port" to "443")
        "tuic" -> mapOf("port" to "443", "congestion" to "bbr")
        "wireguard" -> mapOf("port" to "51820", "wgMtu" to "1280")
        "ssh" -> mapOf("port" to "22", "via" to "none", "viaPort" to "80", "viaTls" to "false")
        "http" -> mapOf("port" to "8080", "security" to "none")
        "socks" -> mapOf("port" to "1080", "security" to "none")
        else -> emptyMap()
    }

    fun fields(p: String, v: Map<String, String>): List<FieldSpec> {
        val out = mutableListOf(
            FieldSpec("server", "Servidor (host ou IP)"),
            FieldSpec("port", "Porta", number = true),
        )
        when (p) {
            "vless" -> {
                out += FieldSpec("uuid", "UUID")
                out += FieldSpec("flow", "Flow", listOf("", "xtls-rprx-vision"))
                out += transport(v, reality = true)
            }
            "vmess" -> {
                out += FieldSpec("uuid", "UUID")
                out += transport(v, reality = false)
            }
            "trojan" -> {
                out += FieldSpec("password", "Senha")
                out += transport(v, reality = false)
            }
            "shadowsocks" -> {
                out += FieldSpec("method", "Método", ssMethods)
                out += FieldSpec("password", "Senha")
            }
            "shadowtls" -> {
                out += FieldSpec("method", "Método Shadowsocks", ssMethods)
                out += FieldSpec("password", "Senha Shadowsocks")
                out += FieldSpec("stlsPassword", "Senha ShadowTLS")
                out += FieldSpec("stlsVersion", "Versão ShadowTLS", listOf("3", "2", "1"))
                out += FieldSpec("sni", "SNI (domínio de handshake TLS)")
                out += FieldSpec("fp", "Fingerprint uTLS", fps.drop(1))
            }
            "hysteria2" -> {
                out += FieldSpec("password", "Senha")
                out += FieldSpec("sni", "SNI (domínio)")
                out += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
                out += FieldSpec("obfsPass", "Senha obfs (salamander, opcional)")
                out += FieldSpec("up", "Upload Mbps (opcional)", number = true)
                out += FieldSpec("down", "Download Mbps (opcional)", number = true)
            }
            "tuic" -> {
                out += FieldSpec("uuid", "UUID")
                out += FieldSpec("password", "Senha")
                out += FieldSpec("sni", "SNI (domínio)")
                out += FieldSpec("alpn", "ALPN (ex: h3)")
                out += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
                out += FieldSpec("congestion", "Controle de congestionamento", listOf("bbr", "cubic", "new_reno"))
            }
            "wireguard" -> {
                out += FieldSpec("wgPrivate", "Chave privada")
                out += FieldSpec("wgPublic", "Chave pública do servidor")
                out += FieldSpec("wgPsk", "Pre-shared key (opcional)")
                out += FieldSpec("wgAddress", "Endereço local (ex: 10.0.0.2/32)")
                out += FieldSpec("wgMtu", "MTU", number = true)
            }
            "ssh" -> {
                out += FieldSpec("sshUser", "Usuário")
                out += FieldSpec("sshPass", "Senha")
                out += FieldSpec("sshKey", "Chave privada (opcional)", multiline = true)
                out += FieldSpec("via", "Payload / túnel intermediário", listOf("none", "http", "socks"))
                if (v["via"] == "http" || v["via"] == "socks") {
                    out += FieldSpec("viaServer", "Proxy intermediário (host/IP)")
                    out += FieldSpec("viaPort", "Porta do proxy", number = true)
                    out += FieldSpec("viaUser", "Usuário do proxy (opcional)")
                    out += FieldSpec("viaPass", "Senha do proxy (opcional)")
                }
                if (v["via"] == "http") {
                    out += FieldSpec("viaHeaders", "Payload: headers HTTP (Chave: Valor, um por linha)\nex: Host: exemplo.com", multiline = true)
                    out += FieldSpec("viaTls", "SSL/TLS no proxy", listOf("false", "true"))
                    if (v["viaTls"] == "true") {
                        out += FieldSpec("viaSni", "SNI do proxy (SSL/TLS)")
                        out += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
                    }
                }
            }
            "http" -> {
                out += FieldSpec("user", "Usuário (opcional)")
                out += FieldSpec("password", "Senha (opcional)")
                out += FieldSpec("path", "Path (opcional)")
                out += FieldSpec("headers", "Payload: headers HTTP (Chave: Valor, um por linha)", multiline = true)
                out += FieldSpec("security", "SSL/TLS (HTTPS proxy)", listOf("none", "tls"))
                if (v["security"] == "tls") {
                    out += FieldSpec("sni", "SNI")
                    out += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
                }
            }
            "socks" -> {
                out += FieldSpec("user", "Usuário (opcional)")
                out += FieldSpec("password", "Senha (opcional)")
                out += FieldSpec("security", "SSL/TLS", listOf("none", "tls"))
                if (v["security"] == "tls") {
                    out += FieldSpec("sni", "SNI")
                    out += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
                }
            }
        }
        return out
    }

    private fun transport(v: Map<String, String>, reality: Boolean): List<FieldSpec> {
        val l = mutableListOf<FieldSpec>()
        l += FieldSpec("network", "Transporte", listOf("tcp", "ws", "httpupgrade", "http", "grpc", "quic"))
        when (v["network"]) {
            "ws", "httpupgrade", "http" -> {
                l += FieldSpec("path", "Path (ex: /)")
                l += FieldSpec("host", "Host (header Host / payload)")
                l += FieldSpec("headers", "Headers extras (Chave: Valor, um por linha)", multiline = true)
            }
            "grpc" -> l += FieldSpec("service", "Service name")
        }
        l += FieldSpec("security", "Segurança (SSL/TLS)", if (reality) listOf("none", "tls", "reality") else listOf("none", "tls"))
        when (v["security"]) {
            "tls" -> {
                l += FieldSpec("sni", "SNI (domínio de fachada)")
                l += FieldSpec("fp", "Fingerprint uTLS", fps)
                l += FieldSpec("alpn", "ALPN (ex: h2,http/1.1)")
                l += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
            }
            "reality" -> {
                l += FieldSpec("sni", "SNI (server name)")
                l += FieldSpec("fp", "Fingerprint uTLS", fps.drop(1))
                l += FieldSpec("pbk", "Reality public key")
                l += FieldSpec("sid", "Reality short id")
            }
        }
        return l
    }
}
