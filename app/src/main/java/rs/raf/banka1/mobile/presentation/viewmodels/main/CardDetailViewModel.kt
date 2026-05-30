package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.apis.CardApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.data.remote.responses.CardResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.navigation.Routes
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val accountApi: AccountApi,
    private val cardApi: CardApi,
    savedStateHandle: SavedStateHandle
) : BaseMviViewModel<CardDetailContract.UiState, CardDetailContract.UiEvent, CardDetailContract.SideEffect>(
    CardDetailContract.UiState()
) {

    private val route = savedStateHandle.toRoute<Routes.MainFlow.CardDetail>()

    init {
        loadCard()
    }

    override fun setEvent(event: CardDetailContract.UiEvent) {
        when (event) {
            is CardDetailContract.UiEvent.Refresh -> loadCard()
            is CardDetailContract.UiEvent.ClearError -> setState { copy(error = null) }
            is CardDetailContract.UiEvent.ShowBlockDialog -> setState { copy(showBlockDialog = true) }
            is CardDetailContract.UiEvent.DismissBlockDialog -> setState { copy(showBlockDialog = false) }
            is CardDetailContract.UiEvent.BlockCard -> blockCard()
        }
    }

    private fun loadCard() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            if (route.cardId > 0L) {
                // Preferred path: fetch card directly by ID (avoids account-details cards:[] bug)
                val cardDeferred = async { cardApi.getCardById(route.cardId) }
                val accountDeferred = async { accountApi.getAccountDetailsByNumber(route.accountNumber) }

                val cardResult = cardDeferred.await()
                val accountResult = accountDeferred.await()

                val account = (accountResult as? NetworkResult.Success)?.data

                when (cardResult) {
                    is NetworkResult.Success -> {
                        val dto = cardResult.data
                        // Map CardDetailDto → CardResponseDto so the existing screen composables work
                        val card = CardResponseDto(
                            id = dto.id,
                            cardNumber = dto.cardNumber,
                            cardType = dto.cardType,
                            status = dto.status,
                            expiryDate = dto.expirationDate,
                            accountNumber = dto.accountNumber
                        )
                        setState { copy(isLoading = false, card = card, account = account) }
                    }
                    is NetworkResult.Error -> setState { copy(isLoading = false, error = cardResult.toErrorData()) }
                    is NetworkResult.Exception -> setState { copy(isLoading = false, error = cardResult.toErrorData()) }
                    is NetworkResult.Ignored -> setState { copy(isLoading = false) }
                }
            } else {
                // Fallback: try to find the card inside account-details (only works if backend populates it)
                when (val result = accountApi.getAccountDetailsByNumber(route.accountNumber)) {
                    is NetworkResult.Success -> {
                        val account = result.data
                        val card = account.cards?.firstOrNull { it.cardNumber == route.cardNumber }
                        setState { copy(isLoading = false, card = card, account = account) }
                    }
                    is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
                    is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
                    is NetworkResult.Ignored -> setState { copy(isLoading = false) }
                }
            }
        }
    }

    private fun blockCard() {
        val cardId = state.value.card?.id ?: run {
            setState { copy(error = ErrorData(title = "Greška", message = "ID kartice nije dostupan.")) }
            return
        }
        viewModelScope.launch {
            setState { copy(isBlocking = true) }
            when (val result = cardApi.blockCard(cardId)) {
                is NetworkResult.Success -> {
                    setState { copy(isBlocking = false, showBlockDialog = false) }
                    loadCard()
                }
                is NetworkResult.Error -> setState { copy(isBlocking = false, showBlockDialog = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isBlocking = false, showBlockDialog = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isBlocking = false, showBlockDialog = false) }
            }
        }
    }
}

interface CardDetailContract {
    data class UiState(
        val isLoading: Boolean = false,
        val card: CardResponseDto? = null,
        val account: AccountDetailsResponseDto? = null,
        val error: ErrorData? = null,
        val showBlockDialog: Boolean = false,
        val isBlocking: Boolean = false
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data object ClearError : UiEvent
        data object ShowBlockDialog : UiEvent
        data object DismissBlockDialog : UiEvent
        data object BlockCard : UiEvent
    }

    sealed interface SideEffect
}
