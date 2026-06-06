package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.apis.TransactionApi
import rs.raf.banka1.mobile.data.apis.TransferApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.TransactionResponseDto
import rs.raf.banka1.mobile.data.remote.responses.TransferResponseDto
import rs.raf.banka1.mobile.data.repository.UserPreferencesRepository
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.navigation.Routes
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject
import kotlin.collections.flatten

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transferApi: TransferApi,
    private val transactionApi: TransactionApi,
    private val accountApi: AccountApi,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : BaseMviViewModel<HistoryContract.UiState, HistoryContract.UiEvent, HistoryContract.SideEffect>(
    HistoryContract.UiState()
) {
    private val route = savedStateHandle.toRoute<Routes.MainFlow.History>()

    init {
        setState {
            copy(
                cardLabel = route.cardLabel,
                isFiltered = route.accountNumber != null
            )
        }
        loadData()
    }

    override fun setEvent(event: HistoryContract.UiEvent) {
        when (event) {
            is HistoryContract.UiEvent.SelectTab -> setState { copy(selectedTab = event.tab) }
            is HistoryContract.UiEvent.Refresh -> loadData()
            is HistoryContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    private fun loadData() {
        if (route.accountNumber != null) {
            loadForAccount(route.accountNumber)
        } else {
            loadAllAccounts()
        }
    }

    private fun loadForAccount(accountNumber: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            val transfersDeferred = async {
                transferApi.getTransfersForAccount(accountNumber, 0, 100)
            }
            val transactionsDeferred = async {
                transactionApi.getTransactionsForAccount(accountNumber, 0, 100)
            }

            val transfersResult = transfersDeferred.await()
            val transactionsResult = transactionsDeferred.await()

            val transfers = when (transfersResult) {
                is NetworkResult.Success -> transfersResult.data.content
                else -> emptyList()
            }
            val transactions = when (transactionsResult) {
                is NetworkResult.Success -> transactionsResult.data.content
                else -> emptyList()
            }

            val firstError = listOf(transfersResult, transactionsResult)
                .filterIsInstance<NetworkResult.Error<*>>()
                .firstOrNull()

            setState {
                copy(
                    isLoading = false,
                    transfers = transfers.sortedByDescending { it.timestamp ?: "" },
                    transactions = transactions.sortedByDescending { it.createdAt ?: "" },
                    error = firstError?.toErrorData()
                )
            }
        }
    }

    private fun loadAllAccounts() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            val clientId = userPreferencesRepository.readClientData().firstOrNull()?.id
            if (clientId == null) {
                setState {
                    copy(
                        isLoading = false,
                        error = ErrorData(title = "Greska", message = "Nije moguce ucitati istoriju.")
                    )
                }
                return@launch
            }

            val transfersDeferred = async {
                transferApi.getTransfers(clientId = clientId, page = 0, size = 100)
            }
            val accountsDeferred = async {
                accountApi.getMyAccounts(page = 0, size = 100)
            }

            val transfersResult = transfersDeferred.await()
            val accountsResult = accountsDeferred.await()

            val transfers = when (transfersResult) {
                is NetworkResult.Success -> transfersResult.data.content
                else -> emptyList()
            }

            val accountCurrencies = mutableMapOf<String, String>()
            val transactions: List<TransactionResponseDto>

            when (accountsResult) {
                is NetworkResult.Success -> {
                    val accountNumbers = accountsResult.data.content.mapNotNull { account ->
                        if (account.brojRacuna != null && account.currency != null) {
                            accountCurrencies[account.brojRacuna] = account.currency
                        }
                        account.brojRacuna
                    }

                    val deferredTransactions = accountNumbers.map { accountNumber ->
                        async {
                            transactionApi.getTransactionsForAccount(
                                accountNumber = accountNumber,
                                page = 0,
                                size = 100
                            )
                        }
                    }

                    transactions = deferredTransactions.awaitAll()
                        .mapNotNull { result ->
                            if (result is NetworkResult.Success) result.data.content else null
                        }
                        .flatten()
                        .distinctBy { it.orderNumber }
                }
                else -> transactions = emptyList()
            }

            val firstError = listOf(transfersResult, accountsResult)
                .filterIsInstance<NetworkResult.Error<*>>()
                .firstOrNull()

            setState {
                copy(
                    isLoading = false,
                    transfers = transfers.sortedByDescending { it.timestamp ?: "" },
                    transactions = transactions.sortedByDescending { it.createdAt ?: "" },
                    accountCurrencies = accountCurrencies,
                    error = firstError?.toErrorData()
                )
            }
        }
    }
}

enum class HistoryTab { TRANSFERS, TRANSACTIONS }

interface HistoryContract {
    data class UiState(
        val isLoading: Boolean = false,
        val selectedTab: HistoryTab = HistoryTab.TRANSFERS,
        val transfers: List<TransferResponseDto> = emptyList(),
        val transactions: List<TransactionResponseDto> = emptyList(),
        val accountCurrencies: Map<String, String> = emptyMap(),
        val cardLabel: String? = null,
        val isFiltered: Boolean = false,
        val error: ErrorData? = null
    )

    sealed interface UiEvent {
        data class SelectTab(val tab: HistoryTab) : UiEvent
        data object Refresh : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect
}
