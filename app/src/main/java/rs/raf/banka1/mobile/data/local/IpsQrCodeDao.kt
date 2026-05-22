package rs.raf.banka1.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IpsQrCodeDao {

    @Query("SELECT * FROM ips_qr_codes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<IpsQrCodeEntity>>

    @Query("SELECT * FROM ips_qr_codes WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<IpsQrCodeEntity>>

    @Insert
    suspend fun insert(entity: IpsQrCodeEntity): Long

    @Update
    suspend fun update(entity: IpsQrCodeEntity)

    @Query("DELETE FROM ips_qr_codes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ips_qr_codes WHERE id = :id")
    suspend fun getById(id: Long): IpsQrCodeEntity?
}
