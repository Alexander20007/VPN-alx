package com.alx.vpn.core

import io.nekohasekai.libbox.PlatformInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Implementa a PlatformInterface do libbox via java.lang.reflect.Proxy.
 * Assim o app não quebra se a versão do sing-box adicionar/remover métodos da interface:
 * métodos desconhecidos recebem um valor padrão.
 */
object PlatformProxy {

    fun create(s: AlxVpnService): PlatformInterface {
        val handler = InvocationHandler { proxy, m, args ->
            if (m.declaringClass == Any::class.java) {
                return@InvocationHandler when (m.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> "AlxPlatform"
                }
            }
            when (m.name.lowercase()) {
                "useplatformautodetectinterfacecontrol" -> true
                "autodetectinterfacecontrol" -> {
                    s.protect((args!![0] as Number).toInt()); null
                }
                "opentun" -> s.openTunFd()
                "writelog" -> {
                    VpnState.log(args!![0].toString()); null
                }
                "useprocfs" -> false
                "findconnectionowner", "packagenamebyuid", "uidbypackagename" ->
                    throw UnsupportedOperationException("não suportado")
                "useplatformdefaultinterfacemonitor" -> true
                "startdefaultinterfacemonitor" -> {
                    s.startMonitor(args!![0]!!); null
                }
                "closedefaultinterfacemonitor" -> {
                    s.stopMonitor(); null
                }
                "useplatforminterfacegetter" -> true
                "getinterfaces" -> s.interfaces()
                "systemcertificates" -> iterProxy(m.returnType, emptyList())
                else -> defaultFor(m)
            }
        }
        return Proxy.newProxyInstance(
            PlatformInterface::class.java.classLoader,
            arrayOf(PlatformInterface::class.java),
            handler,
        ) as PlatformInterface
    }

    private fun defaultFor(m: Method): Any? = when (m.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }

    /** Cria um iterador (StringIterator, NetworkInterfaceIterator...) do libbox para a lista dada. */
    fun iterProxy(cls: Class<*>, items: List<Any?>): Any {
        val it = items.iterator()
        return Proxy.newProxyInstance(cls.classLoader, arrayOf(cls)) { proxy, m, args ->
            if (m.declaringClass == Any::class.java) {
                when (m.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> "Iter"
                }
            } else when (m.name.lowercase()) {
                "len" -> items.size
                "hasnext" -> it.hasNext()
                "next" -> it.next()
                else -> defaultFor(m)
            }
        }
    }

    /** Chama setX(valor) por reflexão, convertendo o tipo conforme o parâmetro. */
    fun setBean(o: Any, prop: String, v: Any?) {
        val m = o.javaClass.methods.firstOrNull {
            it.name.equals("set$prop", ignoreCase = true) && it.parameterCount == 1
        } ?: return
        val t = m.parameterTypes[0]
        val arg: Any? = when {
            t == java.lang.Integer.TYPE || t == java.lang.Integer::class.java -> when (v) {
                is Number -> v.toInt()
                is String -> listOf("wifi", "cellular", "ethernet", "other").indexOf(v).coerceAtLeast(0)
                else -> 0
            }
            t == java.lang.Long.TYPE -> (v as? Number)?.toLong() ?: 0L
            t == String::class.java -> v?.toString() ?: ""
            t == java.lang.Boolean.TYPE -> v as? Boolean ?: false
            t.isInterface -> iterProxy(t, (v as? List<*>) ?: emptyList<Any?>())
            else -> return
        }
        m.invoke(o, arg)
    }
}
