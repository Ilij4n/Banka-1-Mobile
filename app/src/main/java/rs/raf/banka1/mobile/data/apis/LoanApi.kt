package rs.raf.banka1.mobile.data.apis

import retrofit2.http.GET
import retrofit2.http.Query
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.LoanResponseDto
import rs.raf.banka1.mobile.data.remote.responses.PageResponse

interface LoanApi {

    @GET("api/loans/client")
    suspend fun getClientLoans(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100
    ): NetworkResult<PageResponse<LoanResponseDto>>
}
