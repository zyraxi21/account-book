package io.github.zyraxi21.accountbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, MonthlyAssetEntity::class, ChannelBalanceEntity::class,
        IncomeEntity::class, SmsImportReceiptEntity::class, AppSettingsEntity::class, RememberedChannelEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
