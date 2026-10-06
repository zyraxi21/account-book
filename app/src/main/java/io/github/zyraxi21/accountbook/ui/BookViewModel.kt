package io.github.zyraxi21.accountbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.*
import io.github.zyraxi21.accountbook.sms.IcbcSmsParser
import io.github.zyraxi21.accountbook.sms.SmsParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class BookUiState(val loading: Boolean = true, val data: BookData = BookData(), val storageError: BookError? = null)
data class BalanceDraft(val channelId: String, val name: String, val selected: Boolean, val amount: String = "")
data class AssetDraft(val originalMonth: YearMonth?, val registeredAt: Instant, val balances: List<BalanceDraft>, val liability: String = "")
data class IncomeDraft(val id: String, val title: String, val amount: String, val receivedAt: Instant,
                       val source: IncomeSource, val fingerprint: String? = null)
data class ChannelDraft(val id: String? = null, val name: String = "")

/** 所有敏感草稿仅存在 ViewModel 中，不写入 SavedStateHandle 或持久化 Bundle。 */
class BookViewModel(
    private val repository: BookRepository,
    private val parser: IcbcSmsParser = IcbcSmsParser(),
    private val clock: Clock = Clock.system(BOOK_ZONE),
) : ViewModel() {
    private val _state = MutableStateFlow(BookUiState())
    val state = _state.asStateFlow()
    private val _privacyHidden = MutableStateFlow(true)
    val privacyHidden = _privacyHidden.asStateFlow()
    private val _selectedMonth = MutableStateFlow(YearMonth.now(clock))
    val selectedMonth = _selectedMonth.asStateFlow()
    private val _assetDraft = MutableStateFlow<AssetDraft?>(null)
    val assetDraft = _assetDraft.asStateFlow()
    private val _incomeDraft = MutableStateFlow<IncomeDraft?>(null)
    val incomeDraft = _incomeDraft.asStateFlow()
    private val _channelDraft = MutableStateFlow<ChannelDraft?>(null)
    val channelDraft = _channelDraft.asStateFlow()
    private val _smsText = MutableStateFlow<String?>(null)
    val smsText = _smsText.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        _state.value = _state.value.copy(loading = true, storageError = null)
        observation = viewModelScope.launch {
            try {
                repository.observeBook().collect { _state.value = BookUiState(loading = false, data = it) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, storageError = (error as? BookException)?.error ?: BookError.STORAGE_UNAVAILABLE)
            }
        }
    }

    fun togglePrivacy() { _privacyHidden.value = !_privacyHidden.value; _message.value = null }
    fun hidePrivateData() { _privacyHidden.value = true; _message.value = null }
    fun moveMonth(delta: Long) { _selectedMonth.value = _selectedMonth.value.plusMonths(delta) }
    fun currentMonth() { _selectedMonth.value = YearMonth.now(clock) }
    fun dismissMessage() { _message.value = null }
    fun notifyMessage(resource: Int) { _message.value = resource }

    private fun allowEdit(): Boolean {
        if (_privacyHidden.value) { _message.value = R.string.privacy_reveal_first; return false }
        return !_state.value.loading && _state.value.storageError == null && !_busy.value
    }

    fun openAssets() {
        if (!allowEdit()) return
        val data = _state.value.data
        val snapshot = data.snapshot(_selectedMonth.value)
        val selected = snapshot?.balances?.map { it.channelId } ?: data.nextRegistrationChannels().map { it.id }
        val existing = snapshot?.balances?.associateBy { it.channelId }.orEmpty()
        val ids = (selected + data.activeChannels.map { it.id }).distinct()
        val channels = data.channels.associateBy { it.id }
        val time = snapshot?.registeredAt ?: if (_selectedMonth.value == YearMonth.now(clock)) clock.instant()
            else _selectedMonth.value.atEndOfMonth().atTime(23, 59).atZone(BOOK_ZONE).toInstant()
        _assetDraft.value = AssetDraft(snapshot?.month, time, ids.mapNotNull { id -> channels[id]?.let { channel ->
            BalanceDraft(id, existing[id]?.channelName ?: channel.name, id in selected, existing[id]?.amount?.inputText().orEmpty())
        } }, snapshot?.liability?.inputText().orEmpty())
    }

    fun updateAssetDate(time: Instant) { _assetDraft.value = _assetDraft.value?.copy(registeredAt = time) }
    fun updateBalance(id: String, amount: String? = null, selected: Boolean? = null) {
        _assetDraft.value = _assetDraft.value?.let { draft -> draft.copy(balances = draft.balances.map {
            if (it.channelId == id) it.copy(amount = amount ?: it.amount, selected = selected ?: it.selected) else it
        }) }
    }
    fun updateLiability(value: String) { _assetDraft.value = _assetDraft.value?.copy(liability = value) }
    fun closeAssetDraft() { if (!_busy.value) _assetDraft.value = null }
    fun saveAsset() {
        if (!allowEdit()) return
        val draft = _assetDraft.value ?: return
        perform {
            val snapshot = MonthlyAssetSnapshot(draft.registeredAt, draft.balances.filter { it.selected }.map {
                ChannelBalance(it.channelId, it.name, Money.parse(it.amount))
            }, Money.parse(draft.liability.ifBlank { "0" }))
            repository.saveAsset(snapshot, draft.originalMonth)
            _selectedMonth.value = snapshot.month
            _assetDraft.value = null
        }
    }
    fun deleteAsset() { if (allowEdit()) perform(R.string.deleted) { repository.deleteAsset(_selectedMonth.value) } }

    fun openIncome(income: Income? = null) {
        if (!allowEdit()) return
        _incomeDraft.value = income?.let { IncomeDraft(it.id, it.title, it.amount.inputText(), it.receivedAt, it.source) }
            ?: IncomeDraft(UUID.randomUUID().toString(), "", "", clock.instant(), IncomeSource.MANUAL)
    }
    fun updateIncome(title: String? = null, amount: String? = null, time: Instant? = null) {
        _incomeDraft.value = _incomeDraft.value?.let { it.copy(title = title ?: it.title, amount = amount ?: it.amount, receivedAt = time ?: it.receivedAt) }
    }
    fun closeIncomeDraft() { if (!_busy.value) _incomeDraft.value = null }
    fun saveIncome() {
        if (!allowEdit()) return
        val draft = _incomeDraft.value ?: return
        perform {
            repository.saveIncome(Income(draft.id, draft.title, Money.parse(draft.amount, positive = true), draft.receivedAt, draft.source), draft.fingerprint)
            _incomeDraft.value = null
        }
    }
    fun deleteIncome(id: String) { if (allowEdit()) perform(R.string.deleted) { repository.deleteIncome(id) } }
    fun openSmsInput() { if (allowEdit()) _smsText.value = "" }
    fun updateSmsInput(text: String) { _smsText.value = text.take(4096) }
    fun closeSmsInput() { _smsText.value = null }
    fun parseSms() {
        if (!allowEdit()) return
        when (val result = parser.parse(_smsText.value.orEmpty(), clock.instant())) {
            is SmsParseResult.Success -> {
                val parsed = result.income
                _incomeDraft.value = IncomeDraft(UUID.randomUUID().toString(), parsed.title, parsed.amount.inputText(), parsed.receivedAt, IncomeSource.SMS, parsed.fingerprint)
                _smsText.value = null
            }
            is SmsParseResult.Failure -> _message.value = result.error.resource()
        }
    }

    fun openChannel(channel: Channel? = null) {
        if (allowEdit()) _channelDraft.value = ChannelDraft(channel?.id, channel?.name.orEmpty())
    }
    fun updateChannelName(name: String) { _channelDraft.value = _channelDraft.value?.copy(name = name) }
    fun closeChannelDraft() { if (!_busy.value) _channelDraft.value = null }
    fun saveChannel() {
        if (!allowEdit()) return
        val draft = _channelDraft.value ?: return
        perform {
            if (draft.id == null) repository.addChannel(draft.name) else repository.renameChannel(draft.id, draft.name)
            _channelDraft.value = null
        }
    }
    fun deleteChannel(id: String) { if (allowEdit()) perform(R.string.deleted) { repository.deleteChannel(id) } }
    fun setSmsEnabled(enabled: Boolean) { perform { repository.setSmsAutoImport(enabled) } }

    private fun perform(successMessage: Int = R.string.saved, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try { action(); _message.value = successMessage
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { _message.value = ((error as? BookException)?.error ?: BookError.STORAGE_UNAVAILABLE).resource()
            } finally { _busy.value = false }
        }
    }

    class Factory(private val repository: BookRepository, private val parser: IcbcSmsParser) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BookViewModel::class.java))
            return BookViewModel(repository, parser) as T
        }
    }
}

fun BookError.resource(): Int = when (this) {
    BookError.INVALID_AMOUNT -> R.string.error_amount
    BookError.INCOME_MUST_BE_POSITIVE -> R.string.error_income_positive
    BookError.AMOUNT_OVERFLOW -> R.string.error_amount_overflow
    BookError.TITLE_REQUIRED -> R.string.error_title
    BookError.CHANNEL_REQUIRED -> R.string.error_channel_required
    BookError.CHANNEL_NAME_REQUIRED -> R.string.error_channel_name
    BookError.CHANNEL_NAME_EXISTS -> R.string.error_channel_exists
    BookError.CHANNEL_UNAVAILABLE -> R.string.error_channel_unavailable
    BookError.MONTH_EXISTS -> R.string.error_month_exists
    BookError.RECORD_NOT_FOUND -> R.string.error_record_missing
    BookError.INVALID_DATE -> R.string.error_date
    BookError.SMS_FORMAT -> R.string.error_sms_format
    BookError.SMS_DATE -> R.string.error_sms_date
    BookError.SMS_DUPLICATE -> R.string.error_sms_duplicate
    BookError.STORAGE_UNAVAILABLE -> R.string.error_storage
    BookError.STORAGE_KEY_MISSING -> R.string.error_key_missing
    BookError.STORAGE_DATABASE_MISSING -> R.string.error_database_missing
    BookError.STORAGE_CORRUPTED -> R.string.error_storage_corrupted
}
