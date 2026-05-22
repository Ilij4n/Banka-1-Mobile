package rs.raf.banka1.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import rs.raf.banka1.mobile.data.local.IpsQrCodeEntity

interface IpsRepository {
    fun observeAll(): Flow<List<IpsQrCodeEntity>>
    fun observeForUser(userId: Long): Flow<List<IpsQrCodeEntity>>
    suspend fun save(entity: IpsQrCodeEntity): Long
    suspend fun update(entity: IpsQrCodeEntity)
    suspend fun delete(id: Long)
}
