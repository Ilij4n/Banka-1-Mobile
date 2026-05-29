package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.ListingApi
import rs.raf.banka1.mobile.data.apis.PriceAlertApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.CreatePriceAlertRequest
import rs.raf.banka1.mobile.data.remote.responses.ListingSummaryDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class PriceAlertsViewModel @Inject constructor(
    private val priceAlertApi: PriceAlertApi,
    private val listingApi: ListingApi
) : BaseMviViewModel<PriceAlertsContract.UiState, PriceAlertsContract.UiEvent, PriceAlertsContract.SideEffect>(
    PriceAlertsContract.UiState()
) {

    init {
        setEvent(PriceAlertsContract.UiEvent.Load)
    }

    override fun setEvent(event: PriceAlertsContract.UiEvent) {
        when (event) {
            is PriceAlertsContract.UiEvent.Load -> loadAlerts()
            is PriceAlertsContract.UiEvent.OpenCreate -> setState { copy(showCreateSheet = true) }
            is PriceAlertsContract.UiEvent.DismissCreate -> setState {
                copy(
                    showCreateSheet = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                    selectedListing = null,
                    condition = "ABOVE",
                    thresholdInput = "",
                    isSubmitting = false
                )
            }
            is PriceAlertsContract.UiEvent.Search -> onSearch(event.q)
            is PriceAlertsContract.UiEvent.SelectListing -> setState {
                copy(selectedListing = event.dto, searchQuery = event.dto.ticker ?: "", searchResults = emptyList())
            }
            is PriceAlertsContract.UiEvent.SetCondition -> setState { copy(condition = event.c) }
            is PriceAlertsContract.UiEvent.SetThreshold -> setState { copy(thresholdInput = event.s) }
            is PriceAlertsContract.UiEvent.Submit -> submit()
            is PriceAlertsContract.UiEvent.Toggle -> toggleAlert(event.id)
            is PriceAlertsContract.UiEvent.Delete -> deleteAlert(event.id)
            is PriceAlertsContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            when (val result = priceAlertApi.getMyAlerts()) {
                is NetworkResult.Success -> {
                    val models = result.data.map { dto ->
                        PriceAlertsContract.AlertUiModel(
                            id = dto.id ?: 0L,
                            listingId = dto.listingId ?: 0L,
                            condition = dto.condition ?: "",
                            threshold = dto.threshold ?: 0.0,
                            active = dto.active,
                            createdAt = dto.createdAt,
                            ticker = null,
                            name = null
                        )
                    }
                    setState { copy(alerts = models, isLoading = false) }
                    enrichAlerts(models)
                }
                is NetworkResult.Error -> setState {
                    copy(isLoading = false, error = result.toErrorData())
                }
                is NetworkResult.Exception -> setState {
                    copy(isLoading = false, error = result.toErrorData())
                }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
    }

    private fun enrichAlerts(models: List<PriceAlertsContract.AlertUiModel>) {
        val distinctIds = models.map { it.listingId }.distinct()
        distinctIds.forEach { listingId ->
            viewModelScope.launch {
                val result = listingApi.getDetails(listingId, "DAY")
                if (result is NetworkResult.Success) {
                    val details = result.data
                    setState {
                        copy(alerts = alerts.map { alert ->
                            if (alert.listingId == listingId) {
                                alert.copy(ticker = details.ticker, name = details.name)
                            } else alert
                        })
                    }
                }
            }
        }
    }

    private fun onSearch(q: String) {
        setState { copy(searchQuery = q) }
        if (q.length < 1) {
            setState { copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            when (val result = listingApi.searchStocks(q = q)) {
                is NetworkResult.Success -> setState { copy(searchResults = result.data.content) }
                else -> {}
            }
        }
    }

    private fun submit() {
        val s = state.value
        val listing = s.selectedListing ?: return
        val threshold = s.thresholdInput.toDoubleOrNull()
        if (threshold == null || threshold <= 0.0) return

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            val req = CreatePriceAlertRequest(
                listingId = listing.listingId ?: return@launch,
                condition = s.condition,
                threshold = threshold,
                notificationType = "PUSH"
            )
            when (val result = priceAlertApi.create(req)) {
                is NetworkResult.Success -> {
                    setEvent(PriceAlertsContract.UiEvent.DismissCreate)
                    loadAlerts()
                }
                is NetworkResult.Error -> setState {
                    copy(isSubmitting = false, error = result.toErrorData())
                }
                is NetworkResult.Exception -> setState {
                    copy(isSubmitting = false, error = result.toErrorData())
                }
                is NetworkResult.Ignored -> setState { copy(isSubmitting = false) }
            }
        }
    }

    private fun toggleAlert(id: Long) {
        viewModelScope.launch {
            when (val result = priceAlertApi.toggle(id)) {
                is NetworkResult.Success -> {
                    val updated = result.data
                    setState {
                        copy(alerts = alerts.map { alert ->
                            if (alert.id == id) alert.copy(active = updated.active) else alert
                        })
                    }
                }
                is NetworkResult.Error -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Ignored -> {}
            }
        }
    }

    private fun deleteAlert(id: Long) {
        viewModelScope.launch {
            when (val result = priceAlertApi.delete(id)) {
                is NetworkResult.Success -> setState {
                    copy(alerts = alerts.filter { it.id != id })
                }
                is NetworkResult.Error -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Ignored -> {}
            }
        }
    }
}

interface PriceAlertsContract {

    data class UiState(
        val alerts: List<AlertUiModel> = emptyList(),
        val isLoading: Boolean = true,
        val error: ErrorData? = null,
        val showCreateSheet: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<ListingSummaryDto> = emptyList(),
        val selectedListing: ListingSummaryDto? = null,
        val condition: String = "ABOVE",
        val thresholdInput: String = "",
        val isSubmitting: Boolean = false
    )

    data class AlertUiModel(
        val id: Long,
        val listingId: Long,
        val condition: String,
        val threshold: Double,
        val active: Boolean,
        val createdAt: String?,
        val ticker: String?,
        val name: String?
    )

    sealed interface UiEvent {
        data object Load : UiEvent
        data object OpenCreate : UiEvent
        data object DismissCreate : UiEvent
        data class Search(val q: String) : UiEvent
        data class SelectListing(val dto: ListingSummaryDto) : UiEvent
        data class SetCondition(val c: String) : UiEvent
        data class SetThreshold(val s: String) : UiEvent
        data object Submit : UiEvent
        data class Toggle(val id: Long) : UiEvent
        data class Delete(val id: Long) : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect
}
