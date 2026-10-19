package io.github.zyraxi21.accountbook.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val active: Boolean = true,
    val position: Int,
)

@Entity(tableName = "monthly_assets")
data class MonthlyAssetEntity(
    @PrimaryKey val month: String,
    val registeredAtMillis: Long,
    val liabilityFen: Long,
    /** 债权金额；v5 新增，旧账本迁移后为 0。 */
    @ColumnInfo(defaultValue = "0") val receivableFen: Long = 0,
)

@Entity(
    tableName = "channel_balances",
    primaryKeys = ["month", "channelId"],
    foreignKeys = [
        ForeignKey(entity = MonthlyAssetEntity::class, parentColumns = ["month"], childColumns = ["month"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("channelId")],
)
data class ChannelBalanceEntity(
    val month: String,
    val channelId: String,
    val channelName: String,
    val amountFen: Long,
    val position: Int,
)

data class AssetWithBalances(
    @Embedded val asset: MonthlyAssetEntity,
    @Relation(parentColumn = "month", entityColumn = "month") val balances: List<ChannelBalanceEntity>,
)

@Entity(tableName = "incomes", indices = [Index("receivedAtMillis")])
data class IncomeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountFen: Long,
    val receivedAtMillis: Long,
    val source: String,
)

/** 凭据不关联删除收入，防止已删除的短信因重投而重新入账。 */
@Entity(tableName = "sms_import_receipts")
data class SmsImportReceiptEntity(@PrimaryKey val fingerprint: String)

/** 保留识别结果与处理状态，不保存短信原文；忽略后仍可浏览和再次入账。 */
@Entity(tableName = "sms_income_records")
data class SmsIncomeRecordEntity(
    @PrimaryKey val fingerprint: String,
    val title: String,
    val amountFen: Long,
    val receivedAtMillis: Long,
    @ColumnInfo(defaultValue = "'PENDING'") val status: String = "PENDING",
    val incomeId: String? = null,
    /** 银行卡尾号，用于按卡筛选与排除；v8 之前的记录为空。 */
    val cardTail: String? = null,
)

/** 短信里出现过的银行卡尾号与用户是否导入该卡的记录；新发现的卡默认导入。 */
@Entity(tableName = "sms_cards")
data class SmsCardEntity(@PrimaryKey val tail: String, @ColumnInfo(defaultValue = "1") val included: Boolean = true)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val smsAutoImportEnabled: Boolean = false,
    /** 最近一次成功导出的时间戳，仅用于界面提示。 */
    val lastExportAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "1") val hideOnStartup: Boolean = true,
    @ColumnInfo(defaultValue = "0") val allowScreenshots: Boolean = false,
    @ColumnInfo(defaultValue = "0") val useWanGrouping: Boolean = false,
    /** 与上月差额的口径：1 表示按净资产，0 表示按总资产。 */
    @ColumnInfo(defaultValue = "1") val differenceUsesNet: Boolean = true,
    /** 与待确认清单在同一事务中推进，查询失败时保持原值。 */
    val lastSmsScanAtMillis: Long? = null,
    /** 用户上次选择的卡尾号筛选；记录的卡不在列表里时回到全部。 */
    val smsCardFilter: String? = null,
)

@Entity(
    tableName = "remembered_channels",
    foreignKeys = [ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)],
)
data class RememberedChannelEntity(@PrimaryKey val channelId: String, val position: Int)
