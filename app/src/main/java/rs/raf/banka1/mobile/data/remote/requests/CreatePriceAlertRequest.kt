package rs.raf.banka1.mobile.data.remote.requests

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePriceAlertRequest(
    @field:Json(name = "listingId")
    val listingId: Long,

    @field:Json(name = "condition")
    val condition: String,

    @field:Json(name = "threshold")
    val threshold: Double,

    @field:Json(name = "notificationType")
    val notificationType: String
)
