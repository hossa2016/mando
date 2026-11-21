package com.hossa.mando

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Certificado del cliente y conexiones TLS hacia el Chromecast. */
object Tls {
    private const val PASS = "mando"
    private const val ALIAS = "cliente"

    private fun file(ctx: Context) = File(ctx.filesDir, "cliente.p12")

    @Synchronized
    fun keyStore(ctx: Context): KeyStore {
        val f = file(ctx)
        val ks = KeyStore.getInstance("PKCS12")
        if (f.exists()) {
            try {
                f.inputStream().use { ks.load(it, PASS.toCharArray()) }
                if (ks.containsAlias(ALIAS)) return ks
            } catch (e: Exception) { }
        }
        // Crear un certificado autofirmado nuevo
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val name = X500Name("CN=atvremote/Mando, O=Hossa, C=CO")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 3650L * 86_400_000L), name, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, kp.private, PASS.toCharArray(), arrayOf(cert))
        f.outputStream().use { ks.store(it, PASS.toCharArray()) }
        return ks
    }

    fun clientCert(ctx: Context): X509Certificate =
        keyStore(ctx).getCertificate(ALIAS) as X509Certificate

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun connect(ctx: Context, host: String, port: Int, timeoutMs: Int = 6000): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(ctx), PASS.toCharArray())
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), SecureRandom())
        val s = ssl.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = 0
        s.tcpNoDelay = true
        s.startHandshake()
        return s
    }

    private fun unsigned(b: BigInteger): ByteArray {
        val a = b.toByteArray()
        return if (a.size > 1 && a[0] == 0.toByte()) a.copyOfRange(1, a.size) else a
    }

    /**
     * Secreto de emparejamiento: SHA-256 de (módulo y exponente del cliente,
     * módulo y exponente del servidor, y los últimos 4 caracteres del código).
     * Devuelve null si el código no coincide (el primer byte del hash debe ser igual a los 2 primeros caracteres).
     */
    fun pairingSecret(client: X509Certificate, server: X509Certificate, code: String): ByteArray? {
        val c = client.publicKey as RSAPublicKey
        val s = server.publicKey as RSAPublicKey
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(c.modulus))
        md.update(unsigned(c.publicExponent))
        md.update(unsigned(s.modulus))
        md.update(unsigned(s.publicExponent))
        md.update(hex(code.substring(2)))
        val hash = md.digest()
        val check = code.substring(0, 2).toInt(16).toByte()
        return if (hash[0] == check) hash else null
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
