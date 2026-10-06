package io.github.zyraxi21.accountbook.testing

import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

/** 只用于验证 UI 隐私与草稿；真实持久化行为由加密数据库测试验证。 */
class ReadOnlyBookRepository(initial: BookData) : BookRepository {
    val data = MutableStateFlow(initial)
    override fun observeBook(): Flow<BookData> = data
    override suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth?): Unit = unsupported()
    override suspend fun deleteAsset(month: YearMonth): Unit = unsupported()
    override suspend fun saveIncome(income: Income, importFingerprint: String?): Unit = unsupported()
    override suspend fun deleteIncome(id: String): Unit = unsupported()
    override suspend fun addChannel(name: String): Unit = unsupported()
    override suspend fun renameChannel(id: String, name: String): Unit = unsupported()
    override suspend fun deleteChannel(id: String): Unit = unsupported()
    override suspend fun setSmsAutoImport(enabled: Boolean): Unit = unsupported()
    override suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean): Boolean = unsupported()
    private fun unsupported(): Nothing = throw AssertionError("隐私测试不应写入账务")
}
