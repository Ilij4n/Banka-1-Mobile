package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import rs.raf.banka1.mobile.data.apis.OrderApi
import rs.raf.banka1.mobile.data.paging.OrderFilters
import rs.raf.banka1.mobile.data.paging.OrderPagingSource
import rs.raf.banka1.mobile.data.remote.responses.OrderResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class MyOrdersViewModel @Inject constructor(
    private val orderApi: OrderApi
) : BaseMviViewModel<MyOrdersContract.UiState, MyOrdersContract.UiEvent, MyOrdersContract.SideEffect>(
    MyOrdersContract.UiState()
) {

    private val filters = MutableStateFlow(OrderFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingData: Flow<PagingData<OrderResponseDto>> = filters
        .flatMapLatest { active ->
            Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    initialLoadSize = PAGE_SIZE,
                    enablePlaceholders = false
                )
            ) { OrderPagingSource(orderApi, active) }.flow
        }
        .cachedIn(viewModelScope)

    override fun setEvent(event: MyOrdersContract.UiEvent) {
        when (event) {
            is MyOrdersContract.UiEvent.SelectStatus -> {
                filters.update { it.copy(status = event.status) }
                setState { copy(status = event.status) }
            }
            is MyOrdersContract.UiEvent.SelectType -> {
                filters.update { it.copy(listingType = event.listingType) }
                setState { copy(listingType = event.listingType) }
            }
            is MyOrdersContract.UiEvent.SelectDateRange -> {
                filters.update { it.copy(dateFrom = event.dateFrom, dateTo = event.dateTo) }
                setState { copy(dateFrom = event.dateFrom, dateTo = event.dateTo) }
            }
            is MyOrdersContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}

interface MyOrdersContract {
    data class UiState(
        val status: String = "ALL",
        val listingType: String? = null,
        val dateFrom: String? = null,
        val dateTo: String? = null,
        val error: ErrorData? = null
    )

    sealed interface UiEvent {
        data class SelectStatus(val status: String) : UiEvent
        data class SelectType(val listingType: String?) : UiEvent
        data class SelectDateRange(val dateFrom: String?, val dateTo: String?) : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect
}
