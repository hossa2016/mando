package com.hossa.mando

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Codificador/decodificador mínimo de protobuf (solo lo que usa el protocolo de Google TV). */
class Pb {
    private val out = ByteArrayOutputStream()

    private fun rawVarint(v: Long) {
        var x = v
        while (true) {
            if (x and 0x7FL.inv() == 0L) { out.write(x.toInt()); return }
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
    }

    fun int(field: Int, v: Long): Pb { rawVarint((field shl 3).toLong()); rawVarint(v); return this }
    fun int(field: Int, v: Int): Pb = int(field, v.toLong())

    fun bytes(field: Int, b: ByteArray): Pb {
        rawVarint(((field shl 3) or 2).toLong()); rawVarint(b.size.toLong()); out.write(b); return this
    }

    fun str(field: Int, s: String): Pb = bytes(field, s.toByteArray(Charsets.UTF_8))
    fun msg(field: Int, m: Pb): Pb = bytes(field, m.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        /** Mensaje leído: campo -> lista de valores (Long para varint, ByteArray para bytes/mensajes). */
        class Msg(val fields: Map<Int, List<Any>>) {
            fun has(f: Int) = fields.containsKey(f)
            fun int(f: Int, def: Long = 0): Long = (fields[f]?.firstOrNull() as? Long) ?: def
            fun bytes(f: Int): ByteArray? = fields[f]?.firstOrNull() as? ByteArray
            fun msg(f: Int): Msg? = bytes(f)?.let { parse(it) }
            fun str(f: Int): String? = bytes(f)?.toString(Charsets.UTF_8)
        }

        fun parse(data: ByteArray): Msg {
            val map = HashMap<Int, MutableList<Any>>()
            var i = 0
            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (i >= data.size) throw EOFException()
                    val b = data[i++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (i < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> map.getOrPut(field) { ArrayList() }.add(varint())
                    2 -> {
                        val len = varint().toInt()
                        map.getOrPut(field) { ArrayList() }.add(data.copyOfRange(i, i + len))
                        i += len
                    }
                    1 -> i += 8
                    5 -> i += 4
                    else -> return Msg(map)
                }
            }
            return Msg(map)
        }

        /** Escribe un mensaje con su longitud (varint) delante. */
        fun writeFramed(os: OutputStream, data: ByteArray) {
            val head = ByteArrayOutputStream()
            var x = data.size.toLong()
            while (true) {
                if (x and 0x7FL.inv() == 0L) { head.write(x.toInt()); break }
                head.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
            os.write(head.toByteArray())
            os.write(data)
            os.flush()
        }

        fun readFramed(ins: InputStream): ByteArray {
            var shift = 0
            var len = 0L
            while (true) {
                val b = ins.read()
                if (b < 0) throw EOFException()
                len = len or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            val buf = ByteArray(len.toInt())
            var off = 0
            while (off < buf.size) {
                val r = ins.read(buf, off, buf.size - off)
                if (r < 0) throw EOFException()
                off += r
            }
            return buf
        }
    }
}
