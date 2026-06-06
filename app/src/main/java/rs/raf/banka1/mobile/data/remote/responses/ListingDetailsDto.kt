package rs.raf.banka1.mobile.data.remote.responses

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListingDetailsDto(
    @field:Json(name = "listingId")
    val listingId: Long? = null,

    @field:Json(name = "ticker")
    val ticker: String? = null,

    @field:Json(name = "name")
    val name: String? = null,

    @field:Json(name = "price")
    val price: Double? = null,

    @field:Json(name = "currency")
    val currency: String? = null,

    @field:Json(name = "changePercent")
    val changePercent: Double? = null
)
