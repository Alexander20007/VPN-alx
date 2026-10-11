package com.alx.vpn.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/** Importa links vless://, vmess://, trojan://, ss://, hysteria2://, hy2://, tuic://. */
object LinkParser {

    fun parse(text: String): Profile? {
        val s = text.trim()
        return try {
            when {
                s.startsWith("vmess://") -> vmess(s)
                s.startsWith("vless://") -> generic(s, "vless")
                s.startsWith("trojan://") -> generic(s, "trojan")
                s.startsWith("hysteria2://") || s.startsWith("hy2://") -> generic(s, "hysteria2")
                s.startsWith("tuic://") -> generic(s, "tuic")
                s.startsWith("ss://") -> ss(s)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeB64(raw: String): String {
        var t = raw.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun generic(link: String, proto: String): Profile? {
        val u = Uri.parse(link.replaceFirst("hy2://", "hysteria2://"))
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 443
        val q = { k: String -> u.getQueryParameter(k) ?: "" }
        val user = u.userInfo ?: ""
        val m = mutableMapOf("server" to host, "port" to port.toString())

        fun transportAndTls() {
            val type = q("type").ifBlank { "tcp" }
            m["network"] = if (type in listOf("ws", "grpc", "httpupgrade")) type else "tcp"
            m["path"] = q("path")
            m["host"] = q("host")
            m["service"] = q("serviceName")
            val sec = q("security").ifBlank { if (proto == "trojan") "tls" else "none" }
            m["security"] = if (sec in listOf("tls", "reality")) sec else "none"
            m["sni"] = q("sni").ifBlank { q("peer") }
            m["fp"] = q("fp")
            m["pbk"] = q("pbk")
            m["sid"] = q("sid")
            m["alpn"] = q("alpn")
            m["insecure"] = if (q("allowInsecure") == "1" || q("insecure") == "1") "true" else "false"
        }

        when (proto) {
            "vless" -> { m["uuid"] = user; m["flow"] = q("flow"); transportAndTls() }
            "trojan" -> { m["password"] = user; transportAndTls() }
            "hysteria2" -> {
                m["password"] = user
                m["sni"] = q("sni")
                m["insecure"] = if (q("insecure") == "1") "true" else "false"
                m["obfsPass"] = q("obfs-password")
            }
            "tuic" -> {
                m["uuid"] = user.substringBefore(':')
                m["password"] = user.substringAfter(':', "")
                m["sni"] = q("sni")
                m["alpn"] = q("alpn")
                m["congestion"] = q("congestion_control").ifBlank { "bbr" }
                m["insecure"] = if (q("allow_insecure") == "1" || q("insecure") == "1") "true" else "false"
            }
        }
        return Profile(name = u.fragment?.ifBlank { null } ?: host, protocol = proto, f = m)
    }

    private fun vmess(link: String): Profile? {
        val j = JSONObject(decodeB64(link.removePrefix("vmess://")))
        val net = j.optString("net", "tcp")
        val tls = j.optString("tls", "") == "tls"
        val m = mutableMapOf(
            "server" to j.optString("add"),
            "port" to j.optString("port", "443"),
            "uuid" to j.optString("id"),
            "network" to if (net in listOf("ws", "grpc", "httpupgrade")) net else "tcp",
            "path" to j.optString("path"),
            "host" to j.optString("host"),
            "service" to j.optString("path"),
            "security" to if (tls) "tls" else "none",
            "sni" to j.optString("sni"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
        )
        if (m["server"].isNullOrBlank()) return null
        return Profile(name = j.optString("ps").ifBlank { m["server"]!! }, protocol = "vmess", f = m)
    }

    private fun ss(link: String): Profile? {
        val body = link.removePrefix("ss://")
        val name = Uri.decode(body.substringAfter('#', ""))
        val main = body.substringBefore('#').substringBefore('?').trimEnd('/')
        val method: String
        val pass: String
        val hostPort: String
        if ('@' in main) {
            val ui = main.substringBefore('@')
            hostPort = main.substringAfter('@')
            val dec = if (':' in ui) Uri.decode(ui) else decodeB64(ui)
            method = dec.substringBefore(':')
            pass = dec.substringAfter(':')
        } else {
            val dec = decodeB64(main)
            hostPort = dec.substringAfterLast('@')
            val ui = dec.substringBeforeLast('@')
            method = ui.substringBefore(':')
            pass = ui.substringAfter(':')
        }
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').trim('/')
        if (host.isBlank()) return null
        return Profile(
            name = name.ifBlank { host }, protocol = "shadowsocks",
            f = mapOf("server" to host, "port" to port, "method" to method, "password" to pass),
        )
    }
}
