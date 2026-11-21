package com.hossa.mando

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** Conexión de control remoto (puerto 6466). Una sola instancia para toda la app. */
object TvRemote {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, NEEDS_PAIRING }

    @Volatile var state = State.DISCONNECTED
        private set

    var listener: ((State) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()
    private var socket: SSLSocket? = null
    private var readerThread: Thread? = null
    @Volatile private var wanted = false
    @Volatile private var ready = false
    private var appCtx: Context? = null
    private var host: String? = null
    private var retry = 0
    private var sslFails = 0

    private fun set(s: State) {
        state = s
        main.post { listener?.invoke(s) }
    }

    fun start(ctx: Context, host: String) {
        appCtx = ctx.applicationContext
        if (this.host != host) { stopSocket(); this.host = host }
        wanted = true
        if (state == State.CONNECTED || state == State.CONNECTING) return
        connectAsync()
    }

    fun stop() {
        wanted = false
        stopSocket()
        set(State.DISCONNECTED)
    }

    private fun stopSocket() {
        ready = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
    }

    private fun connectAsync() {
        val ctx = appCtx ?: return
        val h = host ?: return
        set(State.CONNECTING)
        readerThread = Thread {
            try {
                val s = Tls.connect(ctx, h, 6466)
                socket = s
                retry = 0
                sslFails = 0
                readLoop(s)
            } catch (e: SSLException) {
                // Si el Chromecast rechaza el certificado dos veces seguidas, hay que emparejar otra vez.
                sslFails++
                if (sslFails >= 2) {
                    sslFails = 0
                    stopSocket()
                    wanted = false
                    set(State.NEEDS_PAIRING)
                    return@Thread
                }
            } catch (e: Exception) {
            }
            stopSocket()
            if (wanted) {
                set(State.CONNECTING)
                retry++
                val delay = minOf(1000L * retry, 8000L)
                main.postDelayed({ if (wanted && state != State.CONNECTED) connectAsync() }, delay)
            } else {
                set(State.DISCONNECTED)
            }
        }.apply { isDaemon = true; start() }
    }

    private fun readLoop(s: SSLSocket) {
        val ins = s.inputStream
        val out = s.outputStream
        while (true) {
            val m = Pb.parse(Pb.readFramed(ins))
            when {
                m.has(1) -> { // remote_configure
                    val info = Pb().str(1, "Mando").str(2, "Hossa").int(3, 1).str(4, "1")
                        .str(5, "atvremote").str(6, "1.0.0")
                    send(out, Pb().msg(1, Pb().int(1, 622).msg(2, info)))
                }
                m.has(2) -> { // remote_set_active
                    send(out, Pb().msg(2, Pb().int(1, 622)))
                    if (!ready) { ready = true; set(State.CONNECTED); flushPending() }
                }
                m.has(8) -> { // ping
                    val v = m.msg(8)?.int(1) ?: 0
                    send(out, Pb().msg(9, Pb().int(1, v)))
                    if (!ready) { ready = true; set(State.CONNECTED); flushPending() }
                }
            }
        }
    }

    @Synchronized
    private fun send(out: java.io.OutputStream, p: Pb) = Pb.writeFramed(out, p.toByteArray())

    /** Órdenes tocadas mientras se reconecta: se envían apenas haya conexión (si no pasan de 12 s). */
    private val pending = ArrayList<Pair<Long, Pb>>()

    private fun post(p: Pb) {
        val s = socket
        if (s == null || !ready) {
            synchronized(pending) { if (pending.size < 20) pending.add(System.currentTimeMillis() to p) }
            if (!wanted) appCtx?.let { c -> host?.let { h -> start(c, h) } }
            return
        }
        sender.execute {
            try { send(s.outputStream, p) } catch (e: Exception) { }
        }
    }

    private fun flushPending() {
        val now = System.currentTimeMillis()
        val list = synchronized(pending) { val l = pending.filter { now - it.first < 12_000 }; pending.clear(); l }
        for ((_, p) in list) post(p)
    }

    // ---------- Comandos ----------

    const val SHORT = 3
    const val START_LONG = 1
    const val END_LONG = 2

    fun key(code: Int, direction: Int = SHORT) = post(Pb().msg(10, Pb().int(1, code).int(2, direction)))

    fun openApp(link: String) = post(Pb().msg(90, Pb().str(1, link)))

    /** Escribe texto tecla por tecla (sirve en cualquier buscador de Google TV). */
    fun type(text: String) {
        val clean = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase()
        for (ch in clean) {
            val code = Keys.forChar(ch) ?: continue
            key(code)
        }
    }
}
