package rs.raf.banka1.mobile.domain.ips

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpsStringParser @Inject constructor() {

    fun parse(raw: String): Result<IpsFields> {
        val segments = raw.split("|")
        if (segments.isEmpty()) return Result.failure(Exception("Prazan QR kod"))

        val map = mutableMapOf<String, String>()
        for (segment in segments) {
            val colonIdx = segment.indexOf(':')
            if (colonIdx < 0) continue
            val tag = segment.substring(0, colonIdx)
            val value = segment.substring(colonIdx + 1)
            map[tag] = value
        }

        if (map["K"] != "PR") {
            return Result.failure(Exception("Neispravan IPS QR kod"))
        }

        val account = map["R"]
            ?: return Result.failure(Exception("Nedostaje broj računa primaoca"))
        if (!account.matches(Regex("^\\d{18,19}$"))) {
            return Result.failure(Exception("Neispravan format broja računa"))
        }

        val name = map["N"]
            ?: return Result.failure(Exception("Nedostaje naziv primaoca"))

        val amount: Double? = map["I"]?.let { amountStr ->
            if (!amountStr.startsWith("RSD")) {
                return Result.failure(Exception("IPS QR kod mora koristiti RSD valutu"))
            }
            val numStr = amountStr.removePrefix("RSD").replace(",", ".")
            numStr.toDoubleOrNull()
                ?: return Result.failure(Exception("Neispravan iznos u QR kodu"))
        }

        return Result.success(
            IpsFields(
                recipientAccount = account,
                recipientName = name,
                amount = amount,
                paymentCode = map["SF"],
                purpose = map["S"],
                referenceNumber = map["RO"],
                payerInfo = map["P"]
            )
        )
    }
}
