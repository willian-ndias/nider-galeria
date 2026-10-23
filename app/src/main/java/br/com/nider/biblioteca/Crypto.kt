package br.com.nider.biblioteca

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object Crypto {
    private const val ITERATIONS = 120_000
    private val random = SecureRandom()

    fun salt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun key(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun check(key: SecretKey): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(key.encoded).copyOf(16)

    fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return iv + body
    }

    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, 12)
        val body = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun b64d(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
