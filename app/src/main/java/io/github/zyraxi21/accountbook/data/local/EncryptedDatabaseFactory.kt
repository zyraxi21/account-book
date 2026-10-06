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
        val keyStore = DatabaseKeyStore(directory, keyAlias)
        val password = keyStore.loadOrCreate(database.exists())
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        val bookDatabase = Room.databaseBuilder(context.applicationContext, BookDatabase::class.java, database.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(password))
            .addMigrations(MIGRATION_1_2)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        try {
            // Room 默认延迟打开，必须确认数据库创建成功后才能完成首次密钥提交。
            bookDatabase.openHelper.writableDatabase
            keyStore.completeInitialization(password)
            return bookDatabase
        } catch (error: Exception) {
            runCatching { bookDatabase.close() }
            throw error
        }
    }
}
