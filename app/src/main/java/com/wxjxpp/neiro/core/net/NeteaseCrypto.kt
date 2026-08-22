package com.wxjxpp.neiro.core.net

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云接口加密（weapi / eapi / linuxapi）。
 *
 * 移植自 lx-music-mobile 的 `wy/utils/crypto.js`（参考其 Python 移植版逐行对照）：
 * - weapi：两层 AES-128-CBC-PKCS7。内层用固定 presetKey，外层用随机 16 位数字 key；
 *   encSecKey = RSA-NoPadding(反转后的外层 key 补零到 128 字节) 的 hex
 * - eapi：`url-36cd479b6b5-text-36cd479b6b5-md5(nobody url use text md5forencrypt)`
 *   整体 AES-128-ECB-PKCS7 后转大写 hex
 *
 * 注意："AES" 在 Android 上默认即 AES/ECB/PKCS5Padding（PKCS5 与 PKCS7 对 16 字节块等价），
 * 与原版 JS 的 AES_MODE.ECB_128_NoPadding 实际行为一致。
 */
object NeteaseCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val LINUXAPI_KEY = "rFgB&h#%2?^eDg:Q"
    private const val EAPI_KEY = "e82ckenh8dichen8"

    /** 网易云 RSA 公钥（NoPadding 加密外层 secretKey 用）。 */
    private const val PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ3" +
            "7BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvakl" +
            "V8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44o" +
            "ncaTWz7OBGLbCiK45wIDAQAB"

    private val random = SecureRandom()

    // ---- weapi ----

    fun weapi(obj: Any): Map<String, String> {
        val text = jsonDumps(obj)
        val secretKey = ByteArray(16) { ('0' + random.nextInt(10)).code.toByte() }

        val inner = aesCbcBase64(text.toByteArray(Charsets.UTF_8), PRESET_KEY.toByteArray(), IV.toByteArray())
        val params = Base64.encodeToString(
            aesCbc(inner, secretKey, IV.toByteArray()),
            Base64.NO_WRAP,
        )
        val reversed = secretKey.reversedArray()
        val padded = ByteArray(128)
        System.arraycopy(reversed, 0, padded, 128 - reversed.size, reversed.size)
        val encSecKey = rsaNoPadding(padded).joinToString("") { "%02x".format(it) }
        return mapOf("params" to params, "encSecKey" to encSecKey)
    }

    // ---- eapi ----

    fun eapi(url: String, obj: Any): Map<String, String> {
        val text = if (obj is String) obj else jsonDumps(obj)
        val message = "nobody${url}use${text}md5forencrypt"
        val digest = MessageDigest.getInstance("MD5").digest(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val data = "$url-36cd479b6b5-$text-36cd479b6b5-$digest"
        val encrypted = aesEcb(data.toByteArray(Charsets.UTF_8), EAPI_KEY.toByteArray())
        return mapOf("params" to encrypted.joinToString("") { "%02X".format(it) })
    }

    // ---- linuxapi ----

    fun linuxapi(obj: Any): Map<String, String> {
        val text = jsonDumps(obj)
        val encrypted = aesEcb(text.toByteArray(Charsets.UTF_8), LINUXAPI_KEY.toByteArray())
        return mapOf("eparams" to encrypted.joinToString("") { "%02X".format(it) })
    }

    // ---- 加密原语 ----

    private fun aesCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun aesCbcBase64(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        aesCbc(data, key, iv)

    private fun aesEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    /** RSA/ECB/NoPadding：输入必须恰好 128 字节，输出 128 字节密文。 */
    private fun rsaNoPadding(input: ByteArray): ByteArray {
        require(input.size == 128) { "RSA NoPadding requires 128-byte input" }
        val modulus = java.math.BigInteger(1, Base64.decode(PUBLIC_KEY, Base64.DEFAULT).let { derModulus(it) })
        val exponent = java.math.BigInteger("10001", 16)
        val m = java.math.BigInteger(1, input)
        return padTo128(m.modPow(exponent, modulus).toByteArray())
    }

    /** BigInteger.toByteArray() 可能带符号位或长度不足，统一补齐到 128 字节。 */
    private fun padTo128(bytes: ByteArray): ByteArray = when {
        bytes.size == 128 -> bytes
        bytes.size > 128 -> bytes.copyOfRange(bytes.size - 128, bytes.size)
        else -> ByteArray(128 - bytes.size) + bytes
    }

    /**
     * 从 SubjectPublicKeyInfo DER 里提取 RSA modulus。
     * 结构：SEQUENCE { SEQUENCE(alg), BIT STRING { SEQUENCE { INTEGER n, INTEGER e } } }
     */
    private fun derModulus(spki: ByteArray): ByteArray {
        var pos = 0
        // 外层 SEQUENCE 头
        pos += derHeaderSize(spki, pos)
        // alg SEQUENCE
        pos += derTotalSize(spki, pos)
        // BIT STRING
        val bitStringHeader = derHeaderSize(spki, pos)
        val bitString = spki.copyOfRange(pos + bitStringHeader + 1, pos + bitStringHeader + derContentSize(spki, pos))
        // RSA SEQUENCE
        var p = 0
        p += derHeaderSize(bitString, p)
        // n INTEGER
        val nHeader = derHeaderSize(bitString, p)
        val nLen = derContentSize(bitString, p)
        return bitString.copyOfRange(p + nHeader, p + nHeader + nLen)
    }

    private fun derHeaderSize(data: ByteArray, pos: Int): Int {
        val lenByte = data[pos + 1].toInt() and 0xFF
        return if (lenByte and 0x80 == 0) 2 else 2 + (lenByte and 0x7F)
    }

    private fun derContentSize(data: ByteArray, pos: Int): Int {
        val lenByte = data[pos + 1].toInt() and 0xFF
        return if (lenByte and 0x80 == 0) {
            lenByte
        } else {
            val n = lenByte and 0x7F
            var len = 0
            for (i in 0 until n) len = (len shl 8) or (data[pos + 2 + i].toInt() and 0xFF)
            len
        }
    }

    private fun derTotalSize(data: ByteArray, pos: Int): Int = derHeaderSize(data, pos) + derContentSize(data, pos)

    private fun jsonDumps(obj: Any): String = when (obj) {
        is String -> obj
        else -> compactJson(obj)
    }

    /** 与 JS JSON.stringify 一致的无空格序列化（org.json 输出本身无空格）。 */
    private fun compactJson(obj: Any): String = when (obj) {
        is org.json.JSONObject -> obj.toString()
        is org.json.JSONArray -> obj.toString()
        is Map<*, *> -> {
            val jo = org.json.JSONObject()
            obj.forEach { (k, v) -> jo.put(k.toString(), v ?: org.json.JSONObject.NULL) }
            jo.toString()
        }

        else -> obj.toString()
    }

    private fun ByteArray.toByteArray(length: Int): ByteArray {
        val stripped = dropWhile { it == 0.toByte() }.toByteArray()
        val result = ByteArray(length)
        System.arraycopy(stripped, 0, result, length - stripped.size, stripped.size)
        return result
    }
}