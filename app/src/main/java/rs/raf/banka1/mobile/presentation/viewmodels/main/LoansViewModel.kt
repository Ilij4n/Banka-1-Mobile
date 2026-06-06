package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.LoanApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.LoanResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanApi: LoanApi
) : BaseMviViewModel<LoansContract.UiState, LoansContract.UiEvent, LoansContract.SideEffect>(
    LoansContract.UiState()
) {

    init { loadLoans() }

    override fun setEvent(event: LoansContract.UiEvent) {
        when (event) {
            is LoansContract.UiEvent.Refresh -> loadLoans()
            is LoansContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    private fun loadLoans() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            when (val result = loanApi.getClientLoans()) {
                is NetworkResult.Success -> {
                    val sorted = result.data.content.sortedWith(Comparator { a, b ->
                        val aDate = a.nextInstallmentDate
                        val bDate = b.nextInstallmentDate
                        when {
                            aDate == null && bDate == null -> 0
                            aDate == null -> 1
                            bDate == null -> -1
                            else -> aDate.compareTo(bDate)
                        }
                    })
                    val soonestLoanNumber = sorted.firstOrNull {
                        it.status == "ACTIVE" && it.nextInstallmentDate != null
                    }?.loanNumber
                    setState {
                        copy(
                            isLoading = false,
                            loans = sorted,
                            soonestLoanNumber = soonestLoanNumber
                        )
                    }
                }
                is NetworkResult.Error -> {
                    setState { copy(isLoading = false, error = result.toErrorData()) }
                }
                is NetworkResult.Exception -> {
                    setState { copy(isLoading = false, error = result.toErrorData()) }
                }
                is NetworkResult.Ignored -> {
                    setState { copy(isLoading = false) }
                }
            }
        }
    }
}

interface LoansContract {
    data class UiState(
        val isLoading: Boolean = false,
        val loans: List<LoanResponseDto> = emptyList(),
        val soonestLoanNumber: Long? = null,
        val error: ErrorData? = null
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect
}
