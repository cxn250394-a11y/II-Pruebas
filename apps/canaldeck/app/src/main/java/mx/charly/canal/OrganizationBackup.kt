package mx.charly.canal

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** User-controlled encrypted settings export. Never contains login tokens, cloud session or messages. */
object OrganizationBackup {
    private val header = byteArrayOf(0x43,0x44,0x4F,0x31) // CDO1
    private const val ITERATIONS = 180_000
    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        require(passphrase.size >= 12)
        val spec=PBEKeySpec(passphrase,salt,ITERATIONS,256)
        return try {
            val bits=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bits,"AES") } finally { bits.fill(0) }
        } finally { spec.clearPassword() }
    }
    fun encrypt(cleartext: ByteArray, passphrase: CharArray): ByteArray {
        require(cleartext.size in 1..300_000)
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val iv=ByteArray(12).also(SecureRandom()::nextBytes)
        val aes=key(passphrase,salt)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,aes,GCMParameterSpec(128,iv))
        cipher.updateAAD(header)
        return header + salt + iv + cipher.doFinal(cleartext)
    }
    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        require(payload.size in 49..400_000)
        require(payload.copyOfRange(0,4).contentEquals(header))
        val salt=payload.copyOfRange(4,20);val iv=payload.copyOfRange(20,32)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(passphrase,salt),GCMParameterSpec(128,iv))
        cipher.updateAAD(header)
        return cipher.doFinal(payload.copyOfRange(32,payload.size)).also { require(it.size <= 300_000) }
    }
}
