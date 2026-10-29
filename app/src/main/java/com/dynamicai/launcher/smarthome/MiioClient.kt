package com.dynamicai.launcher.smarthome

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Мінімальний клієнт локального протоколу Xiaomi miIO (UDP 54321, AES-128-CBC).
 * Потрібні IP пристрою й токен із 32 hex-символів.
 */
class MiioClient(private val context: Context, private val ip: String, tokenHex: String) {

    private val token: ByteArray = tokenHex.trim().lowercase().let { h ->
        require(h.length == 32 && h.all { it in "0123456789abcdef" }) { "Токен має містити 32 символи 0-9, a-f" }
        ByteArray(16) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
    private val key = md5(token)
    private val iv = md5(key + token)
    private var nextId = (1000..9000).random()

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b)

    private fun crypt(mode: Int, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }.doFinal(data)

    private fun newSocket(): DatagramSocket {
        val s = DatagramSocket()
        s.soTimeout = 3000
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }?.bindSocket(s)
        } catch (_: Throwable) {}
        return s
    }

    private fun exchange(s: DatagramSocket, addr: InetAddress, out: ByteArray): ByteArray {
        var last: Throwable? = null
        repeat(3) {
            try {
                s.send(DatagramPacket(out, out.size, addr, 54321))
                val buf = ByteArray(4096)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                return buf.copyOf(p.length)
            } catch (t: Throwable) { last = t }
        }
        throw java.io.IOException("Пристрій не відповідає (${last?.message ?: "таймаут"}). Перевірте IP і що телефон у тій самій Wi-Fi мережі")
    }

    /** Виконує метод і повертає JSON-відповідь пристрою як текст. */
    suspend fun call(method: String, paramsJson: String): String = withContext(Dispatchers.IO) {
        val s = newSocket()
        try {
            val addr = InetAddress.getByName(ip.trim())
            val hello = ByteArray(32) { 0xFF.toByte() }
            hello[0] = 0x21.toByte(); hello[1] = 0x31.toByte(); hello[2] = 0x00.toByte(); hello[3] = 0x20.toByte()
            val h = exchange(s, addr, hello)
            if (h.size < 32) throw java.io.IOException("Некоректна відповідь пристрою")
            val devId = h.copyOfRange(8, 12)
            val stamp = ByteBuffer.wrap(h, 12, 4).int + 1

            val id = nextId++
            val payload = """{"id":$id,"method":"$method","params":$paramsJson}""".toByteArray(Charsets.UTF_8)
            val enc = crypt(Cipher.ENCRYPT_MODE, payload)
            val len = 32 + enc.size
            val header = ByteBuffer.allocate(32)
            header.put(0x21.toByte()).put(0x31.toByte()).putShort(len.toShort()).putInt(0).put(devId).putInt(stamp).put(token)
            val sum = md5(header.array() + enc)
            val pkt = ByteBuffer.allocate(len)
            pkt.put(header.array(), 0, 16).put(sum).put(enc)

            val r = exchange(s, addr, pkt.array())
            if (r.size <= 32) throw java.io.IOException("Порожня відповідь")
            val dec = crypt(Cipher.DECRYPT_MODE, r.copyOfRange(32, r.size))
            String(dec, Charsets.UTF_8).trimEnd('\u0000')
        } finally { s.close() }
    }
}
