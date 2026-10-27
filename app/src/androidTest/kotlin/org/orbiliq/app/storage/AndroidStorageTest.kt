package org.orbiliq.app.storage

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.orbiliq.core.model.*

/** Requires a physical Android device; no real accounts or credentials. */
@RunWith(AndroidJUnit4::class)
class AndroidStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun keystoreProjectionAndVaultSurviveReopen() = runBlocking {
        val account = Account(AccountId("storage-test"), ProviderId("synthetic"), "Synthetic storage test")
        AndroidLocalStorage(context).use { storage ->
            storage.repository.saveAccount(account)
            storage.vault.put("synthetic-session", byteArrayOf(4, 5, 6))
        }
        AndroidLocalStorage(context).use { storage ->
            assertTrue(storage.repository.snapshot().accounts.contains(account))
            val secret = storage.vault.get("synthetic-session")!!
            try { assertArrayEquals(byteArrayOf(4, 5, 6), secret) } finally { secret.fill(0) }
            storage.vault.remove("synthetic-session")
            assertNull(storage.vault.get("synthetic-session"))
        }
    }
    @Test fun sqliteVersionOneMigrationPreservesCiphertext() {
        val directory = File(context.noBackupFilesDir, "migration-test").apply { mkdirs() }
        val file = File(directory, "v1.db")
        file.delete()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE projection (id INTEGER PRIMARY KEY CHECK(id = 1), payload BLOB NOT NULL)")
                db.execSQL("INSERT INTO projection VALUES (1, ?)", arrayOf(ByteArray(29) { 1 }))
                db.version = 1
            }
            ProjectionDatabase(context, file).use { db ->
                assertArrayEquals(ByteArray(29) { 1 }, db.read())
                assertEquals(2, db.readableDatabase.version)
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun largeCiphertextDoesNotExceedCursorWindow() {
        val file = File(context.noBackupFilesDir, "large-projection-test.db")
        val bytes = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        try {
            ProjectionDatabase(context, file).use { it.write(bytes) }
            ProjectionDatabase(context, file).use { assertArrayEquals(bytes, it.read()) }
        } finally { SQLiteDatabase.deleteDatabase(file) }
    }
}
