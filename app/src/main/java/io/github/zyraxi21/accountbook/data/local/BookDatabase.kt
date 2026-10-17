package io.github.zyraxi21.accountbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, MonthlyAssetEntity::class, ChannelBalanceEntity::class,
        IncomeEntity::class, SmsImportReceiptEntity::class, AppSettingsEntity::class, RememberedChannelEntity::class],
    version = 5,
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

/** 保留原账本，旧版默认继续在启动时隐藏并禁止前台截图。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN hideOnStartup INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN allowScreenshots INTEGER NOT NULL DEFAULT 0")
    }
}

/** 仅新增金额显示偏好，旧账本默认使用千位分隔。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN useWanGrouping INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v5 新增「债权」金额与差额口径。
 * 两列都带默认值，旧账本的每月资产表债权为 0、差额默认按净资产，历史数据无需重写。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE monthly_assets ADD COLUMN receivableFen INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN differenceUsesNet INTEGER NOT NULL DEFAULT 1")
    }
}
