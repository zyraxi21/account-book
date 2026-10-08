package io.github.zyraxi21.accountbook.testing

import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * 只用于验证 UI 隐私与草稿；真实的持久化行为由加密数据库测试验证。
 *
 * 例外是渠道管理与设备偏好：它们由界面直接驱动，因此在内存里生效，
 * 使 UI 测试能验证拖动排序和新功能的结果，而不是只能看到抛出的断言。
 */
class ReadOnlyBookRepository(initial: BookData) : BookRepository {
    val data = MutableStateFlow(initial)
    override fun observeBook(): Flow<BookData> = data
    override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?): Unit = unsupported()
    override suspend fun deleteAsset(month: YearMonth): Unit = unsupported()
    override suspend fun saveIncome(income: Income, importFingerprint: String?): Unit = unsupported()
    override suspend fun deleteIncome(id: String): Unit = unsupported()
    override suspend fun renameChannel(id: String, name: String) {
        data.value = data.value.copy(channels = data.value.channels.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }
    override suspend fun deleteChannel(id: String) {
        data.value = data.value.copy(
            channels = data.value.channels.map { if (it.id == id) it.copy(active = false) else it },
            settings = data.value.settings.copy(defaultChannelIds = data.value.settings.defaultChannelIds.filterNot { it == id }),
        )
    }
    override suspend fun setSmsAutoImport(enabled: Boolean): Unit = unsupported()
    override suspend fun setHideOnStartup(enabled: Boolean) { data.value = data.value.copy(settings = data.value.settings.copy(hideOnStartup = enabled)) }
    override suspend fun setAllowScreenshots(enabled: Boolean) { data.value = data.value.copy(settings = data.value.settings.copy(allowScreenshots = enabled)) }
    override suspend fun setUseWanGrouping(enabled: Boolean) { data.value = data.value.copy(settings = data.value.settings.copy(useWanGrouping = enabled)) }
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean): Boolean = unsupported()
    override suspend fun recordExport(exportedAt: Instant): Unit = unsupported()
    override suspend fun importBook(data: BookData, mode: ImportMode): BookImportResult = unsupported()

    override suspend fun addChannel(name: String): Channel {
        val channel = Channel("channel-${UUID.randomUUID()}", name.trim(), true,
            (data.value.channels.maxOfOrNull { it.position } ?: -1) + 1)
        data.value = data.value.copy(channels = data.value.channels + channel)
        return channel
    }

    /** 与 EncryptedBookRepository 同一口径：只重排启用渠道，历史余额顺序不动。 */
    override suspend fun reorderChannels(orderedIds: List<String>) {
        val active = data.value.activeChannels
        val byId = active.associateBy { it.id }
        if (orderedIds.size != orderedIds.distinct().size || !byId.keys.containsAll(orderedIds)) throw BookException(BookError.CHANNEL_UNAVAILABLE)
        val requested = orderedIds.toSet()
        val ordered = orderedIds.mapNotNull(byId::get) + active.filterNot { it.id in requested }
        val positions = ordered.mapIndexed { index, channel -> channel.id to index }.toMap()
        // 与 EncryptedBookRepository 一致：默认渠道的成员集合不变，只跟随新的渠道顺序。
        val members = data.value.settings.defaultChannelIds.toSet()
        data.value = data.value.copy(
            // 真实仓储按 position 排序返回，这里同样重排列表，保持观察到的顺序一致。
            channels = data.value.channels.map { channel -> positions[channel.id]?.let { channel.copy(position = it) } ?: channel }
                .sortedBy { it.position },
            settings = data.value.settings.copy(defaultChannelIds = ordered.map { it.id }.filter { it in members }),
        )
    }

    private fun unsupported(): Nothing = throw AssertionError("隐私测试不应写入账务")
}
