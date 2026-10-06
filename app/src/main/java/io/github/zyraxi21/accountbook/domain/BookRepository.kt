package io.github.zyraxi21.accountbook.domain

import io.github.zyraxi21.accountbook.sms.ParsedIcbcIncome
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface BookRepository {
    fun observeBook(): Flow<BookData>
    suspend fun saveAsset(snapshot: MonthlyAssetSnapshot, originalMonth: YearMonth? = null)
    suspend fun deleteAsset(month: YearMonth)
    suspend fun saveIncome(income: Income, importFingerprint: String? = null)
    suspend fun deleteIncome(id: String)
    suspend fun addChannel(name: String)
    suspend fun renameChannel(id: String, name: String)
    suspend fun deleteChannel(id: String)
    suspend fun setSmsAutoImport(enabled: Boolean)
    suspend fun importSms(parsed: ParsedIcbcIncome, requireAutoEnabled: Boolean = true): Boolean
}
