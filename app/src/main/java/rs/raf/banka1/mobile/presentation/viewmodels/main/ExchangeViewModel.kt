package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.ExchangeApi
import rs.raf.banka1.mobile.data.local.ExchangeRateDao
import rs.raf.banka1.mobile.data.local.ExchangeRateEntity
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.ExchangeRateDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExchangeViewModel @Inject constructor(
    private val exchangeApi: ExchangeApi,
    private val exchangeRateDao: ExchangeRateDao
) : BaseMviViewModel<ExchangeContract.UiState, ExchangeContract.UiEvent, ExchangeContract.SideEffect>(
    ExchangeContract.UiState()
) {

    init {
        loadRates()
        loadHistory("EUR")
    }

    override fun setEvent(event: ExchangeContract.UiEvent) {
        when (event) {
            is ExchangeContract.UiEvent.Refresh -> loadRates()
            is ExchangeContract.UiEvent.SelectCurrency -> loadHistory(event.code)
        }
    }

    private fun loadRates() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            when (val result = exchangeApi.getRates()) {
                is NetworkResult.Success -> {
                    val rates = result.data.filter { it.currencyCode != "RSD" }
                    val entities = rates.mapNotNull { dto ->
                        val code = dto.currencyCode ?: return@mapNotNull null
                        ExchangeRateEntity(
                            currencyCode = code,
                            buyingRate = dto.buyingRate ?: 0.0,
                            sellingRate = dto.sellingRate ?: 0.0,
                            date = dto.date ?: ""
                        )
                    }
                    exchangeRateDao.insertAll(entities)
                    setState { copy(isLoading = false, rates = rates) }
                }
                is NetworkResult.Error -> {
                    val cached = loadCached()
                    setState { copy(isLoading = false, rates = cached, error = result.toErrorData()) }
                }
                is NetworkResult.Exception -> {
                    val cached = loadCached()
                    setState { copy(isLoading = false, rates = cached, error = result.toErrorData()) }
                }
                is NetworkResult.Ignored -> {
                    setState { copy(isLoading = false) }
                }
            }
        }
    }

    private fun loadHistory(code: String) {
        viewModelScope.launch {
            setState { copy(selectedCurrency = code, isLoadingHistory = true) }
            val to = LocalDate.now()
            val from = to.minusDays(30)
            when (val result = exchangeApi.getRateHistory(code, from.toString(), to.toString())) {
                is NetworkResult.Success -> setState { copy(isLoadingHistory = false, historyPoints = result.data) }
                is NetworkResult.Error -> setState { copy(isLoadingHistory = false, historyPoints = emptyList()) }
                is NetworkResult.Exception -> setState { copy(isLoadingHistory = false, historyPoints = emptyList()) }
                is NetworkResult.Ignored -> setState { copy(isLoadingHistory = false) }
            }
        }
    }

    private suspend fun loadCached(): List<ExchangeRateDto> {
        return exchangeRateDao.getAll()
            .filter { it.currencyCode != "RSD" }
            .map { entity ->
                ExchangeRateDto(
                    currencyCode = entity.currencyCode,
                    buyingRate = entity.buyingRate,
                    sellingRate = entity.sellingRate,
                    date = entity.date
                )
            }
    }
}

interface ExchangeContract {
    data class UiState(
        val isLoading: Boolean = false,
        val rates: List<ExchangeRateDto> = emptyList(),
        val error: ErrorData? = null,
        val selectedCurrency: String? = "EUR",
        val historyPoints: List<ExchangeRateDto> = emptyList(),
        val isLoadingHistory: Boolean = false
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data class SelectCurrency(val code: String) : UiEvent
    }

    sealed interface SideEffect
}
