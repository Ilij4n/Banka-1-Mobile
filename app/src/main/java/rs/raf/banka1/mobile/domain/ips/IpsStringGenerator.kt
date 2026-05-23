package rs.raf.banka1.mobile.domain.ips

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpsStringGenerator @Inject constructor() {

    fun build(fields: IpsFields): String = buildString {
        append("K:PR|V:01|C:1")
        append("|R:${fields.recipientAccount}")
        append("|N:${fields.recipientName}")
        fields.amount?.let { amount ->
            val intPart = amount.toLong()
            val decPart = Math.round((amount - intPart) * 100)
            append("|I:RSD${intPart},${decPart.toString().padStart(2, '0')}")
        }
        fields.paymentCode?.let { append("|SF:$it") }
        fields.purpose?.let { append("|S:$it") }
        fields.referenceNumber?.let { append("|RO:$it") }
        fields.payerInfo?.let { append("|P:$it") }
    }
}
