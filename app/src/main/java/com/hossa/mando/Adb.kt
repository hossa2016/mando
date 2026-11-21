package com.hossa.mando

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Depuración inalámbrica (Android 11+): vinculación con código y conexión TLS,
 * para abrir cualquier app instalada en el Chromecast.
 */
object Adb {
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var configured = false

    class NeedsPairing(message: String) : Exception(message)
    class AdbError(message: String) : Exception(message)

    private fun setup(ctx: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val path = (ctx.filesDir.absolutePath + "/kadb_key.pem").toPath()
            KadbCert.configure(store = OkioFilePrivateKeyStore(path))
            configured = true
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("mando", Context.MODE_PRIVATE)
    fun connectPort(ctx: Context) = prefs(ctx).getInt("adbPort", 0)
    private fun setConnectPort(ctx: Context, p: Int) = prefs(ctx).edit().putInt("adbPort", p).apply()

    /** Busca por mDNS un servicio de ADB del Chromecast (pairing o connect). Bloquea hasta timeoutMs. */
    fun discoverPort(ctx: Context, type: String, timeoutMs: Long = 6000): Int? {
        val host = Prefs.host(ctx) ?: return null
        var found: Int? = null
        val latch = CountDownLatch(1)
        val d = Discovery(ctx, type) { _, h, port -> if (h == host && found == null) { found = port; latch.countDown() } }
        d.start()
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        d.stop()
        return found
    }

    /** Vincula con el código de 6 dígitos. Bloquea. */
    fun pair(ctx: Context, pairPort: Int, code: String) {
        setup(ctx)
        val host = Prefs.host(ctx) ?: throw AdbError("No hay Chromecast vinculado.")
        val task = pool.submit(Callable { runBlocking { Kadb.pair(host, pairPort, code.trim(), "Mando") } })
        try {
            task.get(30, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true); throw AdbError("El Chromecast no respondió a la vinculación.")
        } catch (e: Exception) {
            val c = e.cause ?: e
            throw AdbError("No se pudo vincular: ${c.message ?: c.javaClass.simpleName}. Revisa el código y el puerto.")
        }
    }

    fun setPort(ctx: Context, port: Int) = setConnectPort(ctx, port)

    private fun run(ctx: Context, host: String, port: Int, command: String): String {
        val task = pool.submit(Callable {
            Kadb.create(host, port, connectTimeout = 6000).use { it.shell(command).allOutput }
        })
        try {
            return task.get(20, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true); throw AdbError("timeout")
        } catch (e: Exception) {
            val c = e.cause ?: e
            throw AdbError(c.message ?: c.javaClass.simpleName)
        }
    }

    /** Ejecuta un comando; si el puerto cambió lo vuelve a buscar. Bloquea: fuera del hilo principal. */
    fun shell(ctx: Context, command: String): String {
        setup(ctx)
        val host = Prefs.host(ctx) ?: throw AdbError("No hay Chromecast vinculado.")
        var port = connectPort(ctx)
        if (port > 0) {
            try { return run(ctx, host, port, command) } catch (e: AdbError) { /* reintenta abajo */ }
        }
        port = discoverPort(ctx, "_adb-tls-connect._tcp") ?: 0
        if (port <= 0) throw NeedsPairing("Activa la Depuración inalámbrica en el Chromecast y vincula Mando con el código.")
        setConnectPort(ctx, port)
        try {
            return run(ctx, host, port, command)
        } catch (e: AdbError) {
            throw NeedsPairing("El Chromecast no aceptó la conexión. Vincula Mando con el código de Depuración inalámbrica.")
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
        }.filter { it.contains(".") && !it.startsWith("com.google.android.tv") }.distinct().sorted()
    }

    fun prettyName(pkg: String): String {
        val skip = setOf("com", "android", "tv", "app", "apps", "net", "org", "google", "co", "atv", "leanback")
        val part = pkg.split(".").lastOrNull { it.lowercase() !in skip } ?: pkg.substringAfterLast(".")
        return part.replaceFirstChar { it.uppercase() }
    }
}
