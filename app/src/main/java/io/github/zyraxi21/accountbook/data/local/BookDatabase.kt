package io.github.zyraxi21.accountbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, MonthlyAssetEntity::class, ChannelBalanceEntity::class,
        IncomeEntity::class, SmsImportReceiptEntity::class, SmsIncomeRecordEntity::class,
        SmsCardEntity::class, AppSettingsEntity::class, RememberedChannelEntity::class],
    version = 8,
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

/** 补查进度与待确认清单都留在加密库中，不改变已登记的收入。 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN lastSmsScanAtMillis INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_sms_incomes (fingerprint TEXT NOT NULL, title TEXT NOT NULL, " +
            "amountFen INTEGER NOT NULL, receivedAtMillis INTEGER NOT NULL, PRIMARY KEY(fingerprint))")
    }
}

/** 保留待确认明细与旧指纹；升级后完整补查一次，恢复仍在收件箱中的历史识别结果。 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE sms_income_records (fingerprint TEXT NOT NULL, title TEXT NOT NULL, " +
            "amountFen INTEGER NOT NULL, receivedAtMillis INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', " +
            "incomeId TEXT, PRIMARY KEY(fingerprint))")
        db.execSQL("INSERT INTO sms_income_records (fingerprint, title, amountFen, receivedAtMillis) " +
            "SELECT fingerprint, title, amountFen, receivedAtMillis FROM pending_sms_incomes")
        db.execSQL("DROP TABLE pending_sms_incomes")
        db.execSQL("UPDATE app_settings SET lastSmsScanAtMillis = NULL")
    }
}

/**
 * v8 按银行卡尾号区分短信。
 *
 * 旧记录的尾号列留空（不可从已保存的指纹反推），由下一次补查从收件箱恢复；
 * 卡列表同样从补查中逐步建立，因此不需要写死任何卡号。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_income_records ADD COLUMN cardTail TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS sms_cards (tail TEXT NOT NULL, " +
            "included INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(tail))")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN smsCardFilter TEXT")
    }
}
