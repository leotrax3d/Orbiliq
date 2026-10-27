package org.orbiliq.app.storage

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.orbiliq.core.data.storage.*

/** Non-exportable key; Android enforces use after device unlock. No silent key replacement. */
internal class AndroidStorageKey(private val directory: File) {
    @Synchronized fun get(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(directory.listFiles().orEmpty().isEmpty()) { "Storage key unavailable; explicit recovery required" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }
    private companion object { const val ALIAS = "org.orbiliq.local-storage.v1" }
}

/** SQLite contains only authenticated ciphertext; WAL/journal never see private plaintext.
 * v1 -> v2 is additive. Unsupported upgrades/downgrades fail without destructive fallback.
 */
internal class ProjectionDatabase(context: Context, file: File) :
    SQLiteOpenHelper(context, file.absolutePath, null, 2), CiphertextStorage {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projection (id INTEGER PRIMARY KEY CHECK(id = 1), payload BLOB NOT NULL)")
        db.execSQL("CREATE TABLE storage_metadata (id INTEGER PRIMARY KEY CHECK(id = 1), format INTEGER NOT NULL)")
        db.execSQL("INSERT INTO storage_metadata VALUES (1, 1)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "Unsupported database migration" }
        db.execSQL("CREATE TABLE storage_metadata (id INTEGER PRIMARY KEY CHECK(id = 1), format INTEGER NOT NULL)")
        db.execSQL("INSERT INTO storage_metadata VALUES (1, 1)")
    }
    override fun read(): ByteArray? {
        val db = readableDatabase
        val length = db.rawQuery("SELECT length(payload) FROM projection WHERE id = 1", null).use {
            if(it.moveToFirst()) it.getInt(0) else return null
        }
        require(length in 29..MAX_BYTES) { "Invalid storage size" }
        // Stay below Android CursorWindow limits even for larger encrypted projections.
        return ByteArray(length).also { result ->
            var offset = 0
            while(offset < length) {
                val size = minOf(512 * 1024, length - offset)
                db.rawQuery("SELECT substr(payload, ?, ?) FROM projection WHERE id = 1", arrayOf("${offset + 1}", "$size")).use {
                    check(it.moveToFirst()); val chunk = it.getBlob(0); check(chunk.size == size)
                    chunk.copyInto(result, offset)
                }
                offset += size
            }
        }
    }
    override fun write(bytes: ByteArray) {
        require(bytes.size <= MAX_BYTES) { "Projection exceeds storage limit" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("projection", null, ContentValues().apply { put("id", 1); put("payload", bytes) }, SQLiteDatabase.CONFLICT_REPLACE)
                .also { check(it != -1L) { "Storage commit failed" } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private companion object { const val MAX_BYTES = 64 * 1024 * 1024 }
}

internal class AndroidSecretVault(private val directory: File, private val cipher: BlobCipher) : SecretVault {
    private fun file(reference: String): AtomicFile {
        require(reference.isNotBlank())
        val id = MessageDigest.getInstance("SHA-256").digest(reference.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, id))
    }
    @Synchronized override fun put(reference: String, secret: ByteArray) {
        val encrypted = cipher.encrypt(secret, "orbiliq/vault/$reference")
        val target = file(reference)
        val stream = target.startWrite()
        try { stream.write(encrypted); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    }
    @Synchronized override fun get(reference: String): ByteArray? {
        val target = file(reference)
        return try { cipher.decrypt(target.readFully(), "orbiliq/vault/$reference") }
        catch (e: java.io.FileNotFoundException) { null }
    }
    @Synchronized override fun remove(reference: String) = file(reference).delete()
}

/** Construct on Dispatchers.IO. Sole app-wide repository owner. */
internal class AndroidLocalStorage(context: Context) : AutoCloseable {
    private val directory = File(context.noBackupFilesDir, "local-store").apply { check(isDirectory || mkdirs()) }
    private val key = AndroidStorageKey(directory)
    // Generate before SQLite/vault create files, so missing keys never replace an existing key.
    private val cipher = BlobCipher { key.get() }.also { key.get() }
    private val database = ProjectionDatabase(context, File(directory, "projection.db"))
    private val encrypted = EncryptedSnapshotStorage(database, cipher)
    val repository = PersistentLocalRepository(encrypted)
    val vault: SecretVault = AndroidSecretVault(File(directory, "vault").apply { check(isDirectory || mkdirs()) }, cipher)
    override fun close() = encrypted.close()
}
