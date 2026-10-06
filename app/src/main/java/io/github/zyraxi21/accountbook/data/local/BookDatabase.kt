package io.github.zyraxi21.accountbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, MonthlyAssetEntity::class, ChannelBalanceEntity::class,
        IncomeEntity::class, SmsImportReceiptEntity::class, AppSettingsEntity::class, RememberedChannelEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}

/** v2 只为设置表补一列导出时间，不触碰任何账务数据。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN lastExportAtMillis INTEGER")
    }
}
