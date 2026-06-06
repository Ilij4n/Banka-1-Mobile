package rs.raf.banka1.mobile.data.apis

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.ListingDetailsDto
import rs.raf.banka1.mobile.data.remote.responses.ListingSummaryDto
import rs.raf.banka1.mobile.data.remote.responses.PageResponse

interface ListingApi {

    @GET("stock/api/listings/stocks")
    suspend fun searchStocks(
        @Query("search") q: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sortBy") sortBy: String = "ticker",
        @Query("sortDirection") dir: String = "asc"
    ): NetworkResult<PageResponse<ListingSummaryDto>>

    @GET("stock/api/listings/{id}")
    suspend fun getDetails(
        @Path("id") id: Long,
        @Query("period") period: String = "DAY"
    ): NetworkResult<ListingDetailsDto>
}
