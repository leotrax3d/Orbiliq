package org.orbiliq.core.data.storage

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ciphertext format: version, random 96-bit IV, ciphertext with 128-bit authentication tag.
 * AAD binds each blob to its purpose; ciphertext cannot be moved from vault to database/media.
 */
class BlobCipher(private val key: () -> SecretKey) {
    fun encrypt(plain: ByteArray, purpose: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12)
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(encrypted: ByteArray, purpose: String): ByteArray {
        require(encrypted.size >= 29 && encrypted[0] == 1.toByte()) { "Invalid encrypted storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted, 13, encrypted.size - 13)
    }
}

/** Platform backend stores only ciphertext. No plaintext staging files. */
interface CiphertextStorage : AutoCloseable {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}
class EncryptedSnapshotStorage(private val backend: CiphertextStorage, private val cipher: BlobCipher) : SnapshotStorage {
    override fun read(): ByteArray? = backend.read()?.let { cipher.decrypt(it, "orbiliq/projection/v1") }
    override fun write(bytes: ByteArray) = backend.write(cipher.encrypt(bytes, "orbiliq/projection/v1"))
    override fun close() = backend.close()
}

/** References, never credential values, belong in account handles. */
interface SecretVault {
    fun put(reference: String, secret: ByteArray)
    fun get(reference: String): ByteArray?
    fun remove(reference: String)
}
