package io.github.zyraxi21.accountbook.data.local

import android.content.Context
import androidx.room.Room
import io.github.zyraxi21.accountbook.data.crypto.DatabaseKeyStore
import io.github.zyraxi21.accountbook.domain.BookError
import io.github.zyraxi21.accountbook.domain.BookException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import java.io.File

object EncryptedDatabaseFactory {
    fun open(context: Context, directory: File = File(context.noBackupFilesDir, "ledger"),
             keyAlias: String = "accountbook.database.wrapping.v1"): BookDatabase {
        val database = File(directory, "accountbook.db")
        val sidecarExists = listOf("-wal", "-shm", "-journal").any { File(database.path + it).exists() }
        if (!database.exists() && sidecarExists) throw BookException(BookError.STORAGE_DATABASE_MISSING)
        val password = DatabaseKeyStore(directory, keyAlias).loadOrCreate(database.exists())
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        return Room.databaseBuilder(context.applicationContext, BookDatabase::class.java, database.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(password))
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
