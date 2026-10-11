package com.alx.vpn.data

import android.content.Context

data class Settings(
    val dnsPreset: String = "Cloudflare",
    val dnsCustom: String = "",
    val bootstrapDns: String = "8.8.8.8",
    val ipv6: Boolean = false,
    val bypassLan: Boolean = true,
    val mtu: Int = 1500,
    val logLevel: String = "warn",
    val appMode: String = "all", // all | only | except
    val apps: Set<String> = emptySet(),
) {
    fun dnsAddress(): String = when (dnsPreset) {
        "Google" -> "https://8.8.8.8/dns-query"
        "Quad9" -> "https://9.9.9.9/dns-query"
        "AdGuard" -> "https://94.140.14.14/dns-query"
        "Personalizado" -> dnsCustom.ifBlank { "https://1.1.1.1/dns-query" }
        else -> "https://1.1.1.1/dns-query"
    }

    companion object {
        val dnsPresets = listOf("Cloudflare", "Google", "Quad9", "AdGuard", "Personalizado")
        val mtus = listOf(1280, 1400, 1500)
        val logLevels = listOf("error", "warn", "info", "debug")
        val appModes = listOf("all", "only", "except")
        fun appModeLabel(m: String) = when (m) {
            "only" -> "Somente apps selecionados"
            "except" -> "Todos, exceto selecionados"
            else -> "Todos os apps"
        }
    }
}

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("alx_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            dnsPreset = sp.getString("dnsPreset", d.dnsPreset) ?: d.dnsPreset,
            dnsCustom = sp.getString("dnsCustom", d.dnsCustom) ?: "",
            bootstrapDns = sp.getString("bootstrapDns", d.bootstrapDns) ?: d.bootstrapDns,
            ipv6 = sp.getBoolean("ipv6", d.ipv6),
            bypassLan = sp.getBoolean("bypassLan", d.bypassLan),
            mtu = sp.getInt("mtu", d.mtu),
            logLevel = sp.getString("logLevel", d.logLevel) ?: d.logLevel,
            appMode = sp.getString("appMode", d.appMode) ?: d.appMode,
            apps = sp.getStringSet("apps", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("dnsPreset", s.dnsPreset)
            .putString("dnsCustom", s.dnsCustom)
            .putString("bootstrapDns", s.bootstrapDns)
            .putBoolean("ipv6", s.ipv6)
            .putBoolean("bypassLan", s.bypassLan)
            .putInt("mtu", s.mtu)
            .putString("logLevel", s.logLevel)
            .putString("appMode", s.appMode)
            .putStringSet("apps", s.apps)
            .apply()
    }
}
