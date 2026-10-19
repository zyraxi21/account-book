package io.github.zyraxi21.accountbook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM channels ORDER BY position, id")
    suspend fun channels(): List<ChannelEntity>

    @Transaction
    @Query("SELECT * FROM monthly_assets ORDER BY month DESC")
    suspend fun snapshots(): List<AssetWithBalances>

    @Transaction
    @Query("SELECT * FROM monthly_assets WHERE month = :month")
    suspend fun snapshot(month: String): AssetWithBalances?

    @Query("SELECT * FROM incomes ORDER BY receivedAtMillis DESC, id")
    suspend fun incomes(): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun income(id: String): IncomeEntity?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun settings(): AppSettingsEntity?

    @Query("SELECT * FROM remembered_channels ORDER BY position")
    suspend fun rememberedChannels(): List<RememberedChannelEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sms_import_receipts WHERE fingerprint = :fingerprint)")
    suspend fun hasReceipt(fingerprint: String): Boolean

    @Query("SELECT * FROM sms_income_records ORDER BY receivedAtMillis DESC, fingerprint")
    fun observeSmsIncomeRecords(): Flow<List<SmsIncomeRecordEntity>>

    @Query("SELECT * FROM sms_income_records WHERE status = 'PENDING' ORDER BY receivedAtMillis, fingerprint")
    suspend fun pendingSmsIncomes(): List<SmsIncomeRecordEntity>

    @Query("SELECT * FROM sms_income_records WHERE fingerprint = :fingerprint")
    suspend fun smsIncomeRecord(fingerprint: String): SmsIncomeRecordEntity?

    @Upsert suspend fun saveSmsIncomeRecord(record: SmsIncomeRecordEntity)

    /** 关闭某张卡的导入时，只改动它尚未入账的记录。 */
    @Query("UPDATE sms_income_records SET status = 'IGNORED' WHERE cardTail = :tail AND status = 'PENDING'")
    suspend fun ignorePendingSmsForCard(tail: String)

    @Query("SELECT * FROM sms_cards ORDER BY tail")
    suspend fun smsCards(): List<SmsCardEntity>

    @Upsert suspend fun saveSmsCards(cards: List<SmsCardEntity>)

    /** 同一天、同金额的收入；项目名由仓库层按全角半角归一化后再比较。 */
    @Query("SELECT * FROM incomes WHERE amountFen = :amountFen " +
        "AND receivedAtMillis >= :dayStart AND receivedAtMillis < :dayEnd " +
        "AND (:exceptId IS NULL OR id != :exceptId) ORDER BY receivedAtMillis, id")
    suspend fun incomesOnDay(amountFen: Long, dayStart: Long, dayEnd: Long, exceptId: String?): List<IncomeEntity>

    @Insert suspend fun insertChannels(channels: List<ChannelEntity>)
    @Upsert suspend fun saveChannel(channel: ChannelEntity)
    /** 拖动排序一次性写回 position。 */
    @Upsert suspend fun saveChannels(channels: List<ChannelEntity>)
    @Upsert suspend fun saveSnapshot(snapshot: MonthlyAssetEntity)
    @Insert suspend fun insertBalances(balances: List<ChannelBalanceEntity>)
    @Upsert suspend fun saveIncome(income: IncomeEntity)
    @Insert suspend fun insertReceipt(receipt: SmsImportReceiptEntity)
    @Upsert suspend fun saveSettings(settings: AppSettingsEntity)
    @Insert suspend fun insertRememberedChannels(channels: List<RememberedChannelEntity>)

    @Query("DELETE FROM channel_balances WHERE month = :month") suspend fun deleteBalances(month: String)
    @Query("DELETE FROM monthly_assets WHERE month = :month") suspend fun deleteSnapshot(month: String)
    @Query("DELETE FROM incomes WHERE id = :id") suspend fun deleteIncome(id: String)
    @Query("DELETE FROM remembered_channels") suspend fun clearRememberedChannels()
    @Query("DELETE FROM remembered_channels WHERE channelId = :id") suspend fun forgetChannel(id: String)

    // 以下用于导入时整表重组：只在导入的单个事务内调用。
    @Query("DELETE FROM channel_balances") suspend fun deleteAllBalances()
    @Query("DELETE FROM monthly_assets") suspend fun deleteAllSnapshots()
    @Query("DELETE FROM channels") suspend fun deleteAllChannels()
    @Query("DELETE FROM incomes") suspend fun deleteAllIncomes()
}
