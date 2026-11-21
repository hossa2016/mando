package com.hossa.mando

import android.content.Context
import android.hardware.ConsumerIrManager

/** Control de la TV por infrarrojo (protocolo NEC) usando el emisor del celular. */
object Ir {
    data class Profile(val name: String, val b0: Int, val b1: Int, val power: Int, val volUp: Int, val volDown: Int, val mute: Int, val extended: Boolean)

    /** Códigos de Hisense (base de datos Flipper-IRDB). */
    val PROFILES = listOf(
        Profile("Hisense A", 0x00, 0xBF, 0x0D, 0x44, 0x43, 0x0E, extended = true),
        Profile("Hisense B", 0x04, 0xFB, 0x08, 0x02, 0x03, 0x09, extended = false)
    )

    fun available(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true
    } catch (e: Exception) { false }

    private fun necPattern(b0: Int, b1: Int, cmd: Int): IntArray {
        val out = ArrayList<Int>()
        out += 9000; out += 4500
        for (b in intArrayOf(b0 and 0xFF, b1 and 0xFF, cmd and 0xFF, cmd.inv() and 0xFF)) {
            for (i in 0 until 8) {
                out += 562
                out += if ((b shr i) and 1 == 1) 1687 else 562
            }
        }
        out += 562
        return out.toIntArray()
    }

    private fun send(ctx: Context, p: Profile, cmd: Int): Boolean = try {
        val ir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        // En NEC normal el segundo byte es el inverso de la dirección; en NEC extendido va tal cual.
        val b1 = if (p.extended) p.b1 else p.b0.inv() and 0xFF
        ir.transmit(38000, necPattern(p.b0, b1, cmd))
        true
    } catch (e: Exception) { false }

    fun profile(ctx: Context): Profile? = PROFILES.getOrNull(Prefs.irProfile(ctx) - 1)

    fun power(ctx: Context, p: Profile? = profile(ctx)) = p?.let { send(ctx, it, it.power) } ?: false
    fun volUp(ctx: Context) = profile(ctx)?.let { send(ctx, it, it.volUp) } ?: false
    fun volDown(ctx: Context) = profile(ctx)?.let { send(ctx, it, it.volDown) } ?: false
    fun mute(ctx: Context) = profile(ctx)?.let { send(ctx, it, it.mute) } ?: false
}
