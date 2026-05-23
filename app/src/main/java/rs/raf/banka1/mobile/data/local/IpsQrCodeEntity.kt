package rs.raf.banka1.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ips_qr_codes")
data class IpsQrCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long? = null,
    val recipientAccount: String,
    val recipientName: String,
    val amount: Double? = null,
    val currency: String = "RSD",
    val paymentCode: String? = null,
    val referenceNumber: String? = null,
    val purpose: String? = null,
    val payerInfo: String? = null,
    val rawIpsString: String,
    val createdAt: Long
)
