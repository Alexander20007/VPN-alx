package com.alx.vpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object VpnState {
    enum class Status { Idle, Connecting, Connected, Error }

    val status = MutableStateFlow(Status.Idle)
    val message = MutableStateFlow("")
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun log(line: String) {
        logs.update { (it + line).takeLast(400) }
    }
}
