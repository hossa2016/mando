package com.hossa.mando

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Depuración ADB por WiFi (puerto 5555) para abrir cualquier app instalada en el Chromecast. */
object Adb {
    private val pool = Executors.newCachedThreadPool()

    class AdbError(message: String) : Exception(message)

    private fun keys(ctx: Context): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /** Ejecuta un comando de shell en el Chromecast. Bloquea: llamar fuera del hilo principal. */
    fun shell(ctx: Context, command: String, timeoutSec: Long = 25): String {
        val host = Prefs.host(ctx) ?: throw AdbError("No hay Chromecast vinculado.")
        val task = pool.submit(Callable {
            Dadb.create(host, 5555, keys(ctx)).use { d -> d.shell(command).allOutput }
        })
        try {
            return task.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true)
            throw AdbError("El Chromecast no respondió. Si en la TV apareció \"¿Permitir la depuración?\", marca \"Permitir siempre\", acepta y vuelve a intentar.")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            if (cause is ConnectException || cause is SocketTimeoutException) {
                throw AdbError("No se pudo conectar. Activa la Depuración por USB en el Chromecast (Configuración → Sistema → Opciones para desarrolladores).")
            }
            throw AdbError("Error de ADB: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }

    /** Abre una app por su paquete (primero como app de TV, si no como app normal). */
    fun launch(ctx: Context, pkg: String) {
        val p = pkg.filter { it.isLetterOrDigit() || it == '.' || it == '_' }
        val cmd = "c=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN " +
            "-c android.intent.category.LEANBACK_LAUNCHER $p | tail -n 1); " +
            "case \"\$c\" in */*) am start -n \"\$c\" ;; *) monkey -p $p -c android.intent.category.LAUNCHER 1 ;; esac"
        shell(ctx, cmd)
    }

    /** Apps que aparecen en el inicio de Google TV: lista de paquetes. */
    fun apps(ctx: Context): List<String> {
        val out = shell(ctx, "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER; " +
            "pm list packages -3")
        return out.lines().mapNotNull { line ->
            val l = line.trim()
            when {
                l.startsWith("package:") -> l.removePrefix("package:")
                l.contains("/") -> l.substringBefore("/")
                else -> null
            }
        }.filter { it.contains(".") && !it.startsWith("com.google.android.tv") }
            .distinct().sorted()
    }

    /** Nombre legible a partir del paquete: com.android.mgstv -> Mgstv */
    fun prettyName(pkg: String): String {
        val skip = setOf("com", "android", "tv", "app", "apps", "net", "org", "google", "co", "atv", "leanback")
        val part = pkg.split(".").lastOrNull { it.lowercase() !in skip } ?: pkg.substringAfterLast(".")
        return part.replaceFirstChar { it.uppercase() }
    }
}
