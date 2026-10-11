package com.alx.vpn.core

import com.alx.vpn.data.Profile
import org.json.JSONArray
import org.json.JSONObject

/** Converte um [Profile] numa configuração JSON do sing-box (1.10.x). */
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

    fun build(p: Profile): String {
        val cfg = o(
            "log" to o("level" to "info", "timestamp" to true),
            "dns" to o(
                "servers" to a(
                    o("tag" to "remote", "address" to "https://1.1.1.1/dns-query", "detour" to "proxy"),
                    o("tag" to "local", "address" to "local"),
                ),
                "rules" to a(o("outbound" to "any", "server" to "local")),
                "final" to "remote",
                "strategy" to "prefer_ipv4",
            ),
            "inbounds" to a(
                o(
                    "type" to "tun", "tag" to "tun-in",
                    "inet4_address" to "172.19.0.1/30",
                    "inet6_address" to "fdfe:dcba:9876::1/126",
                    "mtu" to 1500, "auto_route" to true, "strict_route" to false,
                    "stack" to "mixed", "sniff" to true,
                ),
            ),
            "outbounds" to a(
                proxy(p),
                o("type" to "direct", "tag" to "direct"),
                o("type" to "block", "tag" to "block"),
                o("type" to "dns", "tag" to "dns-out"),
            ),
            "route" to o(
                "rules" to a(
                    o("protocol" to "dns", "outbound" to "dns-out"),
                    o("ip_is_private" to true, "outbound" to "direct"),
                ),
                "final" to "proxy",
                "auto_detect_interface" to true,
            ),
        )
        return cfg.toString(2)
    }

    private fun proxy(p: Profile): JSONObject {
        val port = p.v("port", "443").toIntOrNull() ?: 443
        val j = o("type" to p.protocol, "tag" to "proxy", "server" to p.v("server"), "server_port" to port)
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
            }
        }
        return j
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

    private fun headers(p: Profile): JSONObject {
        val h = JSONObject()
        p.v("host").takeIf { it.isNotEmpty() }?.let { h.put("Host", it) }
        p.f["headers"].orEmpty().lines().forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) {
                val k = line.substring(0, i).trim()
                val v = line.substring(i + 1).trim()
                if (k.isNotEmpty()) h.put(k, v)
            }
        }
        return h
    }

    private fun transport(p: Profile): JSONObject? = when (p.v("network", "tcp")) {
        "ws" -> o("type" to "ws", "path" to p.v("path", "/"), "headers" to headers(p))
        "httpupgrade" -> o(
            "type" to "httpupgrade", "host" to p.v("host").ifEmpty { null },
            "path" to p.v("path", "/"), "headers" to headers(p),
        )
        "grpc" -> o("type" to "grpc", "service_name" to p.v("service"))
        else -> null
    }
}
