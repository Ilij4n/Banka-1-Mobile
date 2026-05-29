package rs.raf.banka1.mobile.data.apis

import retrofit2.http.GET
import retrofit2.http.Query
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.OrderResponseDto
import rs.raf.banka1.mobile.data.remote.responses.PageResponse

interface OrderApi {

    /**
     * Filtered, paginated view of the authenticated client's own orders.
     *
     * @param status one of ALL, PENDING, APPROVED, DECLINED, DONE, CANCELLED
     * @param listingType optional STOCK / FUTURES / FOREX / OPTION filter
     * @param dateFrom optional inclusive lower bound (ISO date, e.g. 2026-05-01)
     * @param dateTo optional inclusive upper bound (ISO date)
     */
    @GET("orders/my-orders/paged")
    suspend fun getMyOrders(
        @Query("status") status: String = "ALL",
        @Query("listingType") listingType: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): NetworkResult<PageResponse<OrderResponseDto>>
}
