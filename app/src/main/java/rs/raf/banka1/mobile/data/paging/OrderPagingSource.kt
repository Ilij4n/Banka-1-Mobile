package rs.raf.banka1.mobile.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import rs.raf.banka1.mobile.data.apis.OrderApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.OrderResponseDto

/** Active filter set applied to the paged My Orders query. */
data class OrderFilters(
    val status: String = "ALL",
    val listingType: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null
)

/**
 * Network-only Paging 3 source over [OrderApi.getMyOrders]. Pages are 0-indexed to match the
 * Spring `Page` contract. No local caching / RemoteMediator — each filter change spawns a fresh
 * source (see MyOrdersViewModel).
 */
class OrderPagingSource(
    private val orderApi: OrderApi,
    private val filters: OrderFilters
) : PagingSource<Int, OrderResponseDto>() {

    override fun getRefreshKey(state: PagingState<Int, OrderResponseDto>): Int? {
        return state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(1) ?: closest?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, OrderResponseDto> {
        val page = params.key ?: 0
        return when (val result = orderApi.getMyOrders(
            status = filters.status,
            listingType = filters.listingType,
            dateFrom = filters.dateFrom,
            dateTo = filters.dateTo,
            page = page,
            size = params.loadSize
        )) {
            is NetworkResult.Success -> {
                val pageData = result.data
                LoadResult.Page(
                    data = pageData.content,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (page + 1 >= pageData.totalPages) null else page + 1
                )
            }
            is NetworkResult.Error -> LoadResult.Error(Exception(result.toErrorMessage()))
            is NetworkResult.Exception -> LoadResult.Error(result.e)
            is NetworkResult.Ignored -> LoadResult.Error(IllegalStateException("Request ignored"))
        }
    }
}
