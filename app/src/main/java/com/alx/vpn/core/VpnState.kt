package com.alx.vpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object VpnState {
    enum class Status { Idle, Connecting, Connected, Error }

    val status = MutableStateFlow(Status.Idle)
    val message = MutableStateFlow("")
    val connectedAt = MutableStateFlow(0L)
    val logs = MutableStateFlow<List<String>>(emptyList())

    private val tsPrefix = Regex("^[+\\-]\\d{4}\\s+\\d{4}-\\d{2}-\\d{2}\\s+[\\d:]+\\s+")
    private var lastKey = ""
    private var repeat = 0

    /** Agrupa linhas repetidas seguidas ("(x37)") para não inundar a tela de logs. */
    @Synchronized
    fun log(line: String) {
        val key = line.replace(tsPrefix, "").trim()
        if (key == lastKey) {
            repeat++
            logs.update { l ->
                if (l.isEmpty()) l else l.dropLast(1) + "$key  (x${repeat + 1})"
            }
            return
        }
        lastKey = key
        repeat = 0
        logs.update { (it + key).takeLast(300) }
    }

    fun clear() {
        lastKey = ""; repeat = 0
        logs.value = emptyList()
    }
}
