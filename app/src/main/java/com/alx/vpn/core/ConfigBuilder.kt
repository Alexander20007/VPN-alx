package com.alx.vpn.core

import com.alx.vpn.data.Profile
import com.alx.vpn.data.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Converte um [Profile] + [Settings] numa configuração JSON do sing-box (1.10.x). */
object ConfigBuilder {

    private fun o(vararg kv: Pair<String, Any?>): JSONObject {
        val j = JSONObject()
        for ((k, v) in kv) if (v != null) j.put(k, v)
        return j
    }

    private fun a(vararg v: Any): JSONArray {
        val j = JSONArray()
        v.forEach { j.put(it) }
        return j
    }

    fun build(p: Profile, s: Settings): String {
        val outbounds = JSONArray()
        proxyChain(p).forEach { outbounds.put(it) }
        outbounds.put(o("type" to "direct", "tag" to "direct"))
        outbounds.put(o("type" to "block", "tag" to "block"))
        outbounds.put(o("type" to "dns", "tag" to "dns-out"))

        val tun = o(
            "type" to "tun", "tag" to "tun-in",
            "inet4_address" to "172.19.0.1/30",
            "mtu" to s.mtu, "auto_route" to true, "strict_route" to false,
            "stack" to "mixed", "sniff" to true,
        )
        if (s.ipv6) tun.put("inet6_address", "fdfe:dcba:9876::1/126")

        val rules = JSONArray()
            .put(o("protocol" to "dns", "outbound" to "dns-out"))
            .put(o("port" to 53, "outbound" to "dns-out"))
        if (s.bypassLan) rules.put(o("ip_is_private" to true, "outbound" to "direct"))

        val cfg = o(
            "log" to o("level" to s.logLevel, "timestamp" to true),
            "dns" to o(
                "servers" to a(
                    o("tag" to "remote", "address" to s.dnsAddress(), "address_resolver" to "bootstrap", "detour" to "proxy"),
                    // Bootstrap DNS por UDP, direto (fora do túnel): resolve o domínio do servidor.
                    o("tag" to "bootstrap", "address" to s.bootstrapDns, "detour" to "direct"),
                ),
                "rules" to a(o("outbound" to "any", "server" to "bootstrap")),
                "final" to "remote",
                "strategy" to if (s.ipv6) "prefer_ipv4" else "ipv4_only",
            ),
            "inbounds" to a(tun),
            "outbounds" to outbounds,
            "route" to o("rules" to rules, "final" to "proxy", "auto_detect_interface" to true),
        )
        return cfg.toString(2)
    }

    /** Lista de outbounds; o primeiro tem sempre a tag "proxy". */
    private fun proxyChain(p: Profile): List<JSONObject> {
        val port = p.v("port", "443").toIntOrNull() ?: 443
        val j = o("type" to p.protocol, "tag" to "proxy", "server" to p.v("server"), "server_port" to port)
        val extra = mutableListOf<JSONObject>()
        when (p.protocol) {
            "vless" -> {
                j.put("uuid", p.v("uuid"))
                p.v("flow").takeIf { it.isNotEmpty() }?.let { j.put("flow", it) }
                j.put("packet_encoding", "xudp")
                tls(p)?.let { j.put("tls", it) }
                transport(p)?.let { j.put("transport", it) }
            }
            "vmess" -> {
                j.put("uuid", p.v("uuid")); j.put("security", "auto"); j.put("alter_id", 0)
                j.put("packet_encoding", "xudp")
                tls(p)?.let { j.put("tls", it) }
                transport(p)?.let { j.put("transport", it) }
            }
            "trojan" -> {
                j.put("password", p.v("password"))
                tls(p)?.let { j.put("tls", it) }
                transport(p)?.let { j.put("transport", it) }
            }
            "shadowsocks" -> {
                j.put("method", p.v("method", "chacha20-ietf-poly1305"))
                j.put("password", p.v("password"))
            }
            "shadowtls" -> {
                // Shadowsocks por cima de ShadowTLS (camuflagem TLS).
                j.put("type", "shadowsocks")
                j.remove("server"); j.remove("server_port")
                j.put("method", p.v("method", "chacha20-ietf-poly1305"))
                j.put("password", p.v("password"))
                j.put("detour", "shadowtls-out")
                extra += o(
                    "type" to "shadowtls", "tag" to "shadowtls-out",
                    "server" to p.v("server"), "server_port" to port,
                    "version" to (p.v("stlsVersion", "3").toIntOrNull() ?: 3),
                    "password" to p.v("stlsPassword"),
                    "tls" to o(
                        "enabled" to true,
                        "server_name" to p.v("sni", p.v("server")),
                        "utls" to o("enabled" to true, "fingerprint" to p.v("fp", "chrome")),
                    ),
                )
            }
            "http" -> {
                p.v("user").takeIf { it.isNotEmpty() }?.let { j.put("username", it) }
                p.v("password").takeIf { it.isNotEmpty() }?.let { j.put("password", it) }
                p.v("path").takeIf { it.isNotEmpty() }?.let { j.put("path", it) }
                val h = parseHeaders(p.f["headers"].orEmpty())
                if (h.length() > 0) j.put("headers", h)
                tls(p)?.let { j.put("tls", it) }
            }
            "socks" -> {
                j.put("version", "5")
                p.v("user").takeIf { it.isNotEmpty() }?.let { j.put("username", it) }
                p.v("password").takeIf { it.isNotEmpty() }?.let { j.put("password", it) }
                tls(p)?.let { j.put("tls", it) }
            }
            "hysteria2" -> {
                j.put("password", p.v("password"))
                p.v("up").toIntOrNull()?.let { j.put("up_mbps", it) }
                p.v("down").toIntOrNull()?.let { j.put("down_mbps", it) }
                p.v("obfsPass").takeIf { it.isNotEmpty() }?.let {
                    j.put("obfs", o("type" to "salamander", "password" to it))
                }
                j.put("tls", tls(p, force = true, defaultAlpn = listOf("h3")))
            }
            "tuic" -> {
                j.put("uuid", p.v("uuid")); j.put("password", p.v("password"))
                j.put("congestion_control", p.v("congestion", "bbr"))
                j.put("udp_relay_mode", "native")
                j.put("tls", tls(p, force = true, defaultAlpn = listOf("h3")))
            }
            "wireguard" -> {
                val addrs = JSONArray()
                p.v("wgAddress", "10.0.0.2/32").split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { addrs.put(it) }
                j.put("local_address", addrs)
                j.put("private_key", p.v("wgPrivate"))
                j.put("peer_public_key", p.v("wgPublic"))
                p.v("wgPsk").takeIf { it.isNotEmpty() }?.let { j.put("pre_shared_key", it) }
                j.put("mtu", p.v("wgMtu", "1280").toIntOrNull() ?: 1280)
            }
            "ssh" -> {
                j.put("user", p.v("sshUser", "root"))
                p.v("sshPass").takeIf { it.isNotEmpty() }?.let { j.put("password", it) }
                p.f["sshKey"]?.takeIf { it.isNotBlank() }?.let { j.put("private_key", it.trim()) }
                // SSH com payload: o SSH sai por um proxy HTTP/SOCKS intermediário.
                val via = p.v("via", "none")
                val viaServer = p.v("viaServer")
                if (via != "none" && viaServer.isNotEmpty()) {
                    j.put("detour", "via-out")
                    val v = o(
                        "type" to via, "tag" to "via-out",
                        "server" to viaServer, "server_port" to (p.v("viaPort", "80").toIntOrNull() ?: 80),
                    )
                    p.v("viaUser").takeIf { it.isNotEmpty() }?.let { v.put("username", it) }
                    p.v("viaPass").takeIf { it.isNotEmpty() }?.let { v.put("password", it) }
                    if (via == "socks") v.put("version", "5")
                    if (via == "http") {
                        val h = parseHeaders(p.f["viaHeaders"].orEmpty())
                        if (h.length() > 0) v.put("headers", h)
                        if (p.v("viaTls") == "true") {
                            val t = o("enabled" to true)
                            p.v("viaSni").takeIf { it.isNotEmpty() }?.let { t.put("server_name", it) }
                            if (p.v("insecure") == "true") t.put("insecure", true)
                            v.put("tls", t)
                        }
                    }
                    extra += v
                }
            }
        }
        return listOf(j) + extra
    }

    private fun tls(p: Profile, force: Boolean = false, defaultAlpn: List<String>? = null): JSONObject? {
        val sec = p.v("security", "none")
        if (!force && sec == "none") return null
        val t = o("enabled" to true)
        // SNI de fachada: se vazio, o sing-box usa o endereço do servidor.
        p.v("sni").ifEmpty { p.v("host") }.takeIf { it.isNotEmpty() }?.let { t.put("server_name", it) }
        if (p.v("insecure") == "true") t.put("insecure", true)
        val alpn = p.v("alpn").split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { defaultAlpn ?: emptyList() }
        if (alpn.isNotEmpty()) t.put("alpn", JSONArray(alpn))
        val fp = p.v("fp", if (sec == "reality") "chrome" else "")
        if (fp.isNotEmpty()) t.put("utls", o("enabled" to true, "fingerprint" to fp))
        if (sec == "reality") {
            t.put("reality", o("enabled" to true, "public_key" to p.v("pbk"), "short_id" to p.v("sid")))
        }
        return t
    }

    private fun parseHeaders(text: String): JSONObject {
        val h = JSONObject()
        text.lines().forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) {
                val k = line.substring(0, i).trim()
                val v = line.substring(i + 1).trim()
                if (k.isNotEmpty()) h.put(k, v)
            }
        }
        return h
    }

    private fun headers(p: Profile): JSONObject {
        val h = JSONObject()
        p.v("host").takeIf { it.isNotEmpty() }?.let { h.put("Host", it) }
        val extra = parseHeaders(p.f["headers"].orEmpty())
        extra.keys().forEach { h.put(it, extra.getString(it)) }
        return h
    }

    private fun transport(p: Profile): JSONObject? = when (p.v("network", "tcp")) {
        "ws" -> o("type" to "ws", "path" to p.v("path", "/"), "headers" to headers(p))
        "httpupgrade" -> o(
            "type" to "httpupgrade", "host" to p.v("host").ifEmpty { null },
            "path" to p.v("path", "/"), "headers" to headers(p),
        )
        "http" -> o(
            "type" to "http", "host" to a(p.v("host", p.v("sni", p.v("server")))),
            "path" to p.v("path", "/"), "headers" to headers(p),
        )
        "grpc" -> o("type" to "grpc", "service_name" to p.v("service"))
        "quic" -> o("type" to "quic")
        else -> null
    }
}
