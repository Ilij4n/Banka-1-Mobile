package rs.raf.banka1.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally-stored notification (order lifecycle event, etc.) shown in the in-app inbox.
 * Mirrors the verification-code local pattern — there is no backend inbox; rows are created
 * from incoming FCM data messages.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String,
    val orderId: Long? = null,
    val receivedAt: Long,
    val isRead: Boolean = false
)
