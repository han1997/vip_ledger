package com.han1997.vipledger.feature.ledger.ui.autobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.domain.model.TxnFilter
import com.han1997.vipledger.core.domain.repository.*
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoBookViewModel @Inject constructor(
    private val repository: AutoBookRepository, private val control: AutoCaptureControl,
    private val ledgers: LedgerRepository, private val accounts: AccountRepository, private val txns: TxnRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AutoBookUiState())
    val uiState = state.asStateFlow()
    init {
        watch { ledgers.observeAll().collect { values -> state.update { it.copy(ledgers = values.filter { l -> !l.isArchived && l.currencyCode == "CNY" }) } } }
        watch { control.runtime.collect { value -> state.update { it.copy(runtime = value) } } }
        watch {
            repository.observeConfig().collectLatest { config ->
                state.update { it.copy(config = config, form = null, bindingAccount = "") }
                combine(accounts.observeByLedger(config.ledgerId), repository.observeBindings(config.ledgerId),
                    repository.observeCandidates(config.ledgerId), txns.observe(TxnFilter(config.ledgerId))) { a, b, c, t ->
                    state.update { it.copy(accounts = a.filter { account -> account.isUsable }, bindings = b, candidates = c, txns = t) }
                }.collect()
            }
        }
        onAction(AutoBookAction.Refresh)
    }
    private fun watch(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { state.update { it.copy(failed = true) } }
    } }
    fun onAction(action: AutoBookAction) {
        val current = state.value
        when (action) {
            is AutoBookAction.Config -> run { repository.saveConfig(action.value) }
            is AutoBookAction.BindingApp -> state.update { it.copy(bindingApp = action.value) }
            is AutoBookAction.BindingToken -> state.update { it.copy(bindingToken = action.value) }
            is AutoBookAction.BindingAccount -> state.update { it.copy(bindingAccount = action.value) }
            AutoBookAction.Bind -> run { repository.bind(AccountBinding(current.config.ledgerId, current.bindingApp, current.bindingToken, current.bindingAccount)) }
            is AutoBookAction.Unbind -> run { repository.removeBinding(action.value) }
            is AutoBookAction.Select -> state.update { it.copy(form = CandidateForm(action.value.id,
                action.value.facts.type ?: TransactionType.EXPENSE,
                action.value.facts.amountCents?.let { cents -> Money.ofCents(cents).toDisplayString() }.orEmpty(),
                action.value.accountId.orEmpty(), action.value.targetAccountId.orEmpty(), action.value.facts.merchant,
                action.value.refundOfTxnId.orEmpty(), action.value.duplicateTxnId.orEmpty())) }
            is AutoBookAction.Edit -> state.update { it.copy(form = action.value) }
            AutoBookAction.Confirm -> current.form?.let { form -> form.confirmation()?.let { input -> run { repository.confirm(form.id, input) } } }
            AutoBookAction.Link -> current.form?.let { form -> run { repository.link(form.id, form.linkId) } }
            is AutoBookAction.Ignore -> run { repository.ignore(action.id) }
            is AutoBookAction.OpenSetting -> if (!control.openSetting(action.value)) state.update { it.copy(failed = true) }
            AutoBookAction.Refresh -> state.update { it.copy(permissions = control.permissions()) }
            AutoBookAction.DismissError -> state.update { it.copy(failed = false) }
        }
    }
    private fun run(block: suspend () -> AppResult<*>) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                val result = block()
                state.update { it.copy(busy = false, failed = result is AppResult.Failure,
                    form = if (result is AppResult.Success) null else it.form) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { state.update { it.copy(busy = false, failed = true) } }
        }
    }
}
