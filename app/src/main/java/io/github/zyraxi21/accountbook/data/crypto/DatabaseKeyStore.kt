package io.github.zyraxi21.accountbook.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.zyraxi21.accountbook.domain.BookError
import io.github.zyraxi21.accountbook.domain.BookException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore 只保存封装密钥，SQLCipher 随机口令只以密文文件持久化。 */
class DatabaseKeyStore(
    private val directory: File,
    private val alias: String = "accountbook.database.wrapping.v1",
) {
    val keyFile: File get() = File(directory, "database-key.v1")

    fun loadOrCreate(databaseExists: Boolean): ByteArray = synchronized(lock) {
        try {
            val atomicFile = AtomicFile(keyFile)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyFile.exists() || File(keyFile.path + ".bak").exists()) {
                if (!databaseExists) throw BookException(BookError.STORAGE_DATABASE_MISSING)
                val key = keyStore.getKey(alias, null) as? SecretKey
                    ?: throw BookException(BookError.STORAGE_KEY_MISSING)
                return@synchronized unwrap(readEnvelope(atomicFile), key)
            }
            if (databaseExists) throw BookException(BookError.STORAGE_KEY_MISSING)
            if (!directory.isDirectory && !directory.mkdirs()) throw BookException(BookError.STORAGE_UNAVAILABLE)
            val key = (keyStore.getKey(alias, null) as? SecretKey) ?: createWrappingKey()
            val password = ByteArray(32).also(SecureRandom()::nextBytes)
            val encrypted = wrap(password, key)
            val stream = atomicFile.startWrite()
            try {
                stream.write(encrypted)
                atomicFile.finishWrite(stream)
                // 确认封装文件真正落盘后，才允许使用随机口令创建数据库。
                val persistedPassword = unwrap(readEnvelope(atomicFile), key)
                try {
                    if (!password.contentEquals(persistedPassword)) throw BookException(BookError.STORAGE_CORRUPTED)
                } finally { persistedPassword.fill(0) }
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                password.fill(0)
                throw error
            }
            password
        } catch (error: BookException) {
            throw error
        } catch (error: Exception) {
            throw BookException(BookError.STORAGE_CORRUPTED, error)
        }
    }

    private fun readEnvelope(atomicFile: AtomicFile): ByteArray = atomicFile.openRead().use { input ->
        val bytes = input.readNBytes(129)
        if (bytes.size != 72) throw BookException(BookError.STORAGE_CORRUPTED)
        bytes
    }

    private fun createWrappingKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        generateKey()
    }

    private fun wrap(password: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(aad)
        }
        val ciphertext = cipher.doFinal(password)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(1)
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    private fun unwrap(envelope: ByteArray, key: SecretKey): ByteArray {
        if (envelope.size > 128) throw BookException(BookError.STORAGE_CORRUPTED)
        val input = DataInputStream(ByteArrayInputStream(envelope))
        if (input.readInt() != 1 || input.readInt() != 12) throw BookException(BookError.STORAGE_CORRUPTED)
        val iv = ByteArray(12).also(input::readFully)
        if (input.readInt() != 48) throw BookException(BookError.STORAGE_CORRUPTED)
        val ciphertext = ByteArray(48).also(input::readFully)
        if (input.available() != 0) throw BookException(BookError.STORAGE_CORRUPTED)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(aad)
            doFinal(ciphertext)
        }
    }

    companion object {
        private val lock = Any()
        private val aad = "io.github.zyraxi21.accountbook/database-key/v1".toByteArray(Charsets.UTF_8)
    }
}
