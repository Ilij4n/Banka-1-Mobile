package rs.raf.banka1.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VerificationCodeEntity::class,
        ExchangeRateEntity::class,
        IpsQrCodeEntity::class,
        NotificationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class BankaDatabase : RoomDatabase() {
    abstract fun verificationCodeDao(): VerificationCodeDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun ipsQrCodeDao(): IpsQrCodeDao
    abstract fun notificationDao(): NotificationDao
}
