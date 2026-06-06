package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.apis.CardApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.data.remote.responses.CardResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class AccountsCardsViewModel @Inject constructor(
    private val accountApi: AccountApi,
    private val cardApi: CardApi
) : BaseMviViewModel<AccountsCardsContract.UiState, AccountsCardsContract.UiEvent, AccountsCardsContract.SideEffect>(
    AccountsCardsContract.UiState()
) {

    override fun setEvent(event: AccountsCardsContract.UiEvent) {
        when (event) {
            is AccountsCardsContract.UiEvent.SelectTab -> setState { copy(selectedTab = event.tab) }
            is AccountsCardsContract.UiEvent.Refresh -> loadData()
            is AccountsCardsContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            when (val result = accountApi.getMyAccounts(page = 0, size = 100)) {
                is NetworkResult.Success -> {
                    val summaries = result.data.content

                    // Fetch all account details in parallel
                    val accounts = summaries.map { summary ->
                        async {
                            val number = summary.brojRacuna ?: return@async summary
                            when (val detail = accountApi.getAccountDetailsByNumber(number)) {
                                is NetworkResult.Success -> detail.data
                                else -> summary
                            }
                        }
                    }.awaitAll()

                    val clientId = accounts.firstOrNull()?.vlasnik
                    val cards: List<CardWithAccount> = if (clientId != null) {
                        when (val cardListResult = cardApi.getClientCards(clientId)) {
                            is NetworkResult.Success -> {
                                // Fetch full card details in parallel — gives us accurate status, type, expiry
                                cardListResult.data.mapNotNull { summary ->
                                    val id = summary.id ?: return@mapNotNull null
                                    async {
                                        val account = accounts.find { it.brojRacuna == summary.accountNumber }
                                        val card = when (val r = cardApi.getCardById(id)) {
                                            is NetworkResult.Success -> CardResponseDto(
                                                id = r.data.id,
                                                cardNumber = r.data.cardNumber,
                                                cardType = r.data.cardType,
                                                status = r.data.status,
                                                expiryDate = r.data.expirationDate,
                                                accountNumber = r.data.accountNumber
                                            )
                                            // If detail fetch fails, show card without status
                                            else -> CardResponseDto(
                                                id = summary.id,
                                                cardNumber = summary.maskedCardNumber,
                                                accountNumber = summary.accountNumber
                                            )
                                        }
                                        CardWithAccount(
                                            card = card,
                                            accountName = account?.nazivRacuna
                                                ?: account?.brojRacuna
                                                ?: summary.accountNumber
                                                ?: "",
                                            accountId = clientId
                                        )
                                    }
                                }.awaitAll()
                            }
                            else -> emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    setState { copy(isLoading = false, accounts = accounts, cards = cards) }
                }
                is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
    }
}

data class CardWithAccount(
    val card: CardResponseDto,
    val accountName: String,
    val accountId: Long?
)

enum class AccountsCardsTab { ACCOUNTS, CARDS }

interface AccountsCardsContract {
    data class UiState(
        val isLoading: Boolean = false,
        val selectedTab: AccountsCardsTab = AccountsCardsTab.ACCOUNTS,
        val accounts: List<AccountDetailsResponseDto> = emptyList(),
        val cards: List<CardWithAccount> = emptyList(),
        val error: ErrorData? = null
    )

    sealed interface UiEvent {
        data class SelectTab(val tab: AccountsCardsTab) : UiEvent
        data object Refresh : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect
}
