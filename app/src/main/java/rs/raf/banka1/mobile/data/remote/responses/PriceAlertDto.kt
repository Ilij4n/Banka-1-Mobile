package rs.raf.banka1.mobile.data.remote.responses

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PriceAlertDto(
    @field:Json(name = "id")
    val id: Long? = null,

    @field:Json(name = "listingId")
    val listingId: Long? = null,

    @field:Json(name = "condition")
    val condition: String? = null,

    @field:Json(name = "threshold")
    val threshold: Double? = null,

    @field:Json(name = "notificationType")
    val notificationType: String? = null,

    @field:Json(name = "active")
    val active: Boolean = false,

    @field:Json(name = "createdAt")
    val createdAt: String? = null,

    @field:Json(name = "lastTriggeredAt")
    val lastTriggeredAt: String? = null
)
