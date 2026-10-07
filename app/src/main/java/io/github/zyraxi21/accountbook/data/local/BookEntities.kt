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

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val smsAutoImportEnabled: Boolean = false,
    /** 最近一次成功导出的时间戳，仅用于界面提示。 */
    val lastExportAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "1") val hideOnStartup: Boolean = true,
    @ColumnInfo(defaultValue = "0") val allowScreenshots: Boolean = false,
)

@Entity(
    tableName = "remembered_channels",
    foreignKeys = [ForeignKey(entity = ChannelEntity::class, parentColumns = ["id"], childColumns = ["channelId"], onDelete = ForeignKey.CASCADE)],
)
data class RememberedChannelEntity(@PrimaryKey val channelId: String, val position: Int)
