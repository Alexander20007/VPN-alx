package com.alx.vpn.data

data class FieldSpec(
    val key: String,
    val label: String,
    val options: List<String>? = null,
    val multiline: Boolean = false,
    val number: Boolean = false,
)

object Protocols {
    val all = listOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "wireguard", "ssh")

    private val ssMethods = listOf(
        "chacha20-ietf-poly1305", "aes-128-gcm", "aes-256-gcm",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )

    fun defaults(p: String): Map<String, String> = when (p) {
        "vless", "vmess" -> mapOf("port" to "443", "network" to "tcp", "security" to "tls")
        "trojan" -> mapOf("port" to "443", "network" to "tcp", "security" to "tls")
        "shadowsocks" -> mapOf("port" to "8388", "method" to "chacha20-ietf-poly1305")
        "hysteria2" -> mapOf("port" to "443")
        "tuic" -> mapOf("port" to "443", "congestion" to "bbr")
        "wireguard" -> mapOf("port" to "51820", "wgMtu" to "1280")
        "ssh" -> mapOf("port" to "22")
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
            }
        }
        return out
    }

    private fun transport(v: Map<String, String>, reality: Boolean): List<FieldSpec> {
        val l = mutableListOf<FieldSpec>()
        l += FieldSpec("network", "Transporte", listOf("tcp", "ws", "httpupgrade", "grpc"))
        when (v["network"]) {
            "ws", "httpupgrade" -> {
                l += FieldSpec("path", "Path (ex: /)")
                l += FieldSpec("host", "Host (header Host / payload)")
                l += FieldSpec("headers", "Headers extras (Chave: Valor, um por linha)", multiline = true)
            }
            "grpc" -> l += FieldSpec("service", "Service name")
        }
        l += FieldSpec("security", "Segurança", if (reality) listOf("none", "tls", "reality") else listOf("none", "tls"))
        when (v["security"]) {
            "tls" -> {
                l += FieldSpec("sni", "SNI (domínio de fachada)")
                l += FieldSpec("fp", "Fingerprint uTLS", listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "random"))
                l += FieldSpec("alpn", "ALPN (ex: h2,http/1.1)")
                l += FieldSpec("insecure", "Ignorar certificado inválido", listOf("false", "true"))
            }
            "reality" -> {
                l += FieldSpec("sni", "SNI (server name)")
                l += FieldSpec("fp", "Fingerprint uTLS", listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random"))
                l += FieldSpec("pbk", "Reality public key")
                l += FieldSpec("sid", "Reality short id")
            }
        }
        return l
    }
}
