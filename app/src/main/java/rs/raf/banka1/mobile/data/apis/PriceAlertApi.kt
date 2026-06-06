package rs.raf.banka1.mobile.data.apis

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.CreatePriceAlertRequest
import rs.raf.banka1.mobile.data.remote.responses.PriceAlertDto

interface PriceAlertApi {

    @GET("price-alerts")
    suspend fun getMyAlerts(): NetworkResult<List<PriceAlertDto>>

    @POST("price-alerts")
    suspend fun create(@Body req: CreatePriceAlertRequest): NetworkResult<PriceAlertDto>

    @PATCH("price-alerts/{id}")
    suspend fun toggle(@Path("id") id: Long): NetworkResult<PriceAlertDto>

    @DELETE("price-alerts/{id}")
    suspend fun delete(@Path("id") id: Long): NetworkResult<Unit>
}
