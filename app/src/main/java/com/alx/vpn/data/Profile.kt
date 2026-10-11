package com.alx.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Perfil de servidor. Os campos específicos de cada protocolo ficam no mapa [f]. */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val f: Map<String, String>,
) {
    fun v(key: String, default: String = ""): String =
        f[key]?.trim().takeUnless { it.isNullOrEmpty() } ?: default

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("protocol", protocol)
        .put("f", JSONObject(f))

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val fo = o.getJSONObject("f")
            val map = fo.keys().asSequence().associateWith { fo.getString(it) }
            return Profile(o.getString("id"), o.getString("name"), o.getString("protocol"), map)
        }
    }
}

class ProfileStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("alx_vpn", Context.MODE_PRIVATE)

    fun load(): List<Profile> = try {
        val arr = JSONArray(sp.getString("profiles", "[]"))
        (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("profiles", arr.toString()).apply()
    }

    var selectedId: String?
        get() = sp.getString("selected", null)
        set(value) { sp.edit().putString("selected", value).apply() }
}
