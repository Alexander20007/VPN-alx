package com.alx.vpn.core

import android.content.Context
import io.nekohasekai.libbox.Libbox

object Core {
    private var ready = false

    /** Chama Libbox.setup(...) adaptando-se à assinatura da versão do sing-box usada. */
    @Synchronized
    fun ensureSetup(ctx: Context) {
        if (ready) return
        val base = ctx.filesDir.absolutePath
        val work = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).absolutePath
        val tmp = ctx.cacheDir.absolutePath
        val m = Libbox::class.java.methods.first { it.name == "setup" }
        val types = m.parameterTypes
        val args: Array<Any?> = if (types.size == 1) {
            val opts = types[0].getDeclaredConstructor().newInstance()
            PlatformProxy.setBean(opts, "basePath", base)
            PlatformProxy.setBean(opts, "workingPath", work)
            PlatformProxy.setBean(opts, "tempPath", tmp)
            arrayOf(opts)
        } else {
            val paths = listOf(base, work, tmp).iterator()
            types.map { t ->
                when (t) {
                    String::class.java -> if (paths.hasNext()) paths.next() else ""
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }.toTypedArray()
        }
        m.invoke(null, *args)
        ready = true
    }
}
