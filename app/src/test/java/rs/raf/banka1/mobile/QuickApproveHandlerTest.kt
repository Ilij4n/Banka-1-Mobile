package rs.raf.banka1.mobile

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.raf.banka1.mobile.core.push.ApproveResult
import rs.raf.banka1.mobile.core.push.QuickApproveHandler
import rs.raf.banka1.mobile.data.apis.VerificationApi
import rs.raf.banka1.mobile.data.local.VerificationCodeDao
import rs.raf.banka1.mobile.data.local.VerificationCodeEntity
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.GenerateRequest
import rs.raf.banka1.mobile.data.remote.requests.ValidateRequest
import rs.raf.banka1.mobile.data.remote.responses.GenerateResponse
import rs.raf.banka1.mobile.data.remote.responses.StatusResponse
import rs.raf.banka1.mobile.data.remote.responses.ValidateResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class QuickApproveHandlerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `VERIFIED response marks session used and returns Verified`() = runTest {
        val fakeApi = FakeVerificationApiForHandler(
            validateResult = NetworkResult.Success(ValidateResponse(valid = true, status = "VERIFIED", remainingAttempts = 3))
        )
        val fakeDao = FakeVerificationCodeDaoForHandler()
        val handler = QuickApproveHandler(fakeApi, fakeDao)

        val result = handler.approve(sessionId = 42L, code = "123456")

        assertThat(result).isInstanceOf(ApproveResult.Verified::class)
        assertThat(fakeDao.markedUsedSessionIds).isEqualTo(listOf("42"))
    }

    @Test
    fun `wrong code returns Failed with remaining attempts in message`() = runTest {
        val fakeApi = FakeVerificationApiForHandler(
            validateResult = NetworkResult.Success(ValidateResponse(valid = false, status = "PENDING", remainingAttempts = 2))
        )
        val fakeDao = FakeVerificationCodeDaoForHandler()
        val handler = QuickApproveHandler(fakeApi, fakeDao)

        val result = handler.approve(sessionId = 42L, code = "000000")

        assertThat(result).isInstanceOf(ApproveResult.Failed::class)
        assertThat((result as ApproveResult.Failed).reason).contains("2")
        assertThat(fakeDao.markedUsedSessionIds).isEmpty()
    }

    @Test
    fun `EXPIRED status returns Failed with expiry message`() = runTest {
        val fakeApi = FakeVerificationApiForHandler(
            validateResult = NetworkResult.Success(ValidateResponse(valid = false, status = "EXPIRED", remainingAttempts = 0))
        )
        val fakeDao = FakeVerificationCodeDaoForHandler()
        val handler = QuickApproveHandler(fakeApi, fakeDao)

        val result = handler.approve(sessionId = 42L, code = "123456")

        assertThat(result).isInstanceOf(ApproveResult.Failed::class)
        assertThat((result as ApproveResult.Failed).reason).contains("istekao")
        assertThat(fakeDao.markedUsedSessionIds).isEmpty()
    }

    @Test
    fun `network error returns Failed without marking session used`() = runTest {
        val fakeApi = FakeVerificationApiForHandler(
            validateResult = NetworkResult.Exception(RuntimeException("network failure"))
        )
        val fakeDao = FakeVerificationCodeDaoForHandler()
        val handler = QuickApproveHandler(fakeApi, fakeDao)

        val result = handler.approve(sessionId = 42L, code = "123456")

        assertThat(result).isInstanceOf(ApproveResult.Failed::class)
        assertThat(fakeDao.markedUsedSessionIds).isEmpty()
    }
}

// ---- Fakes ----

private class FakeVerificationApiForHandler(
    private val validateResult: NetworkResult<ValidateResponse>
) : VerificationApi {

    override suspend fun generate(request: GenerateRequest): NetworkResult<GenerateResponse> =
        NetworkResult.Success(GenerateResponse(sessionId = 1L))

    override suspend fun validate(request: ValidateRequest): NetworkResult<ValidateResponse> =
        validateResult

    override suspend fun getSessionStatus(sessionId: Long): NetworkResult<StatusResponse> =
        NetworkResult.Success(StatusResponse(sessionId = sessionId, status = "PENDING"))
}

private class FakeVerificationCodeDaoForHandler : VerificationCodeDao {

    val markedUsedSessionIds = mutableListOf<String>()

    override fun observeAll(): Flow<List<VerificationCodeEntity>> = flowOf(emptyList())

    override fun observeActiveCount(now: Long): Flow<Int> = flowOf(0)

    override suspend fun insert(entity: VerificationCodeEntity) {}

    override suspend fun markUsed(id: Long) {}

    override suspend fun markUsedBySessionId(sessionId: String) {
        markedUsedSessionIds.add(sessionId)
    }

    override suspend fun deleteById(id: Long) {}

    override suspend fun deleteExpired(now: Long) {}

    override suspend fun deleteAll() {}
}
