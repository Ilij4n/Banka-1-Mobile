package rs.raf.banka1.mobile.domain.ips

data class IpsFields(
    val payerInfo: String? = null,
    val recipientAccount: String,
    val recipientName: String,
    val amount: Double? = null,
    val paymentCode: String? = null,
    val purpose: String? = null,
    val referenceNumber: String? = null
)
