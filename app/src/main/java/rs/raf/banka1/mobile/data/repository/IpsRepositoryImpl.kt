package rs.raf.banka1.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import rs.raf.banka1.mobile.data.local.IpsQrCodeDao
import rs.raf.banka1.mobile.data.local.IpsQrCodeEntity
import rs.raf.banka1.mobile.domain.repository.IpsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IpsRepositoryImpl @Inject constructor(
    private val dao: IpsQrCodeDao
) : IpsRepository {

    override fun observeAll(): Flow<List<IpsQrCodeEntity>> = dao.observeAll()

    override fun observeForUser(userId: Long): Flow<List<IpsQrCodeEntity>> = dao.observeByUser(userId)

    override suspend fun save(entity: IpsQrCodeEntity): Long = dao.insert(entity)

    override suspend fun update(entity: IpsQrCodeEntity) = dao.update(entity)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
