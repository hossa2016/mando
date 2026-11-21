package com.hossa.mando

import android.content.Context
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Emparejamiento con Google TV (puerto 6467, protocolo "polo").
 * Flujo: solicitud -> opciones -> configuración (la TV muestra un código) -> secreto.
 */
class Pairing(private val ctx: Context, private val host: String) {

    sealed class Result {
        object Ok : Result()
        object WrongCode : Result()
        object Cancelled : Result()
        data class Error(val message: String) : Result()
    }

    private val codes = LinkedBlockingQueue<String>()
    @Volatile private var cancelled = false

    fun submitCode(code: String) = codes.offer(code.trim().uppercase())
    fun cancel() { cancelled = true; codes.offer("") }

    private fun envelope() = Pb().int(1, 2).int(2, 200)

    private fun hexEncoding() = Pb().int(1, 3).int(2, 6) // HEXADECIMAL, 6 símbolos

    /** Bloquea hasta terminar. onCodeShown se llama cuando la TV ya muestra el código. */
    fun run(onCodeShown: () -> Unit): Result {
        val sock = try {
            Tls.connect(ctx, host, 6467)
        } catch (e: Exception) {
            return Result.Error("No pudimos conectar con el Chromecast. Revisa que esté encendido y en el mismo WiFi.")
        }
        try {
            val out = sock.outputStream
            val ins = sock.inputStream
            fun send(p: Pb) = Pb.writeFramed(out, p.toByteArray())
            fun receive(): Pb.Companion.Msg {
                val m = Pb.parse(Pb.readFramed(ins))
                val status = m.int(2, 200)
                if (status != 200L) throw PairingStatus(status)
                return m
            }

            send(envelope().msg(10, Pb().str(1, "atvremote").str(2, "Mando")))
            receive()
            send(envelope().msg(20, Pb().msg(1, hexEncoding()).int(3, 1)))
            receive()
            send(envelope().msg(30, Pb().msg(1, hexEncoding()).int(2, 1)))
            receive()

            onCodeShown()
            val code = codes.poll(5, TimeUnit.MINUTES) ?: return Result.Error("Se acabó el tiempo para escribir el código.")
            if (cancelled || code.isEmpty()) return Result.Cancelled
            if (!Regex("^[0-9A-F]{6}$").matches(code)) return Result.WrongCode

            val server = sock.session.peerCertificates[0] as X509Certificate
            val secret = Tls.pairingSecret(Tls.clientCert(ctx), server, code) ?: return Result.WrongCode
            send(envelope().msg(40, Pb().bytes(1, secret)))
            receive()
            return Result.Ok
        } catch (e: PairingStatus) {
            return if (e.status == 402L) Result.WrongCode
            else Result.Error("El Chromecast rechazó el emparejamiento (código ${e.status}).")
        } catch (e: Exception) {
            return if (cancelled) Result.Cancelled else Result.Error("Se perdió la conexión durante el emparejamiento.")
        } finally {
            try { sock.close() } catch (e: Exception) { }
        }
    }

    private class PairingStatus(val status: Long) : Exception()
}
