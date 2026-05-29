package rs.raf.banka1.mobile.data.remote.responses

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Mirror of the order-service `OrderResponse` (enriched variant returned by
 * `GET orders/my-orders/paged`). Enums are kept as raw strings to stay tolerant of
 * unknown backend values; timestamps arrive as ISO-8601 strings.
 */
@JsonClass(generateAdapter = true)
data class OrderResponseDto(
    @field:Json(name = "id")
    val id: Long? = null,

    @field:Json(name = "listingId")
    val listingId: Long? = null,

    @field:Json(name = "orderType")
    val orderType: String? = null,

    @field:Json(name = "quantity")
    val quantity: Int? = null,

    @field:Json(name = "contractSize")
    val contractSize: Int? = null,

    @field:Json(name = "pricePerUnit")
    val pricePerUnit: Double? = null,

    @field:Json(name = "direction")
    val direction: String? = null,

    @field:Json(name = "status")
    val status: String? = null,

    @field:Json(name = "remainingPortions")
    val remainingPortions: Int? = null,

    @field:Json(name = "approximatePrice")
    val approximatePrice: Double? = null,

    @field:Json(name = "fee")
    val fee: Double? = null,

    @field:Json(name = "ticker")
    val ticker: String? = null,

    @field:Json(name = "securityName")
    val securityName: String? = null,

    @field:Json(name = "listingType")
    val listingType: String? = null,

    @field:Json(name = "executionPrice")
    val executionPrice: Double? = null,

    @field:Json(name = "createdAt")
    val createdAt: String? = null,

    @field:Json(name = "executedAt")
    val executedAt: String? = null
)
