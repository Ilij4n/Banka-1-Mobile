package rs.raf.banka1.mobile

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.apis.TransactionApi
import rs.raf.banka1.mobile.data.apis.VerificationApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.GenerateRequest
import rs.raf.banka1.mobile.data.remote.requests.NewPaymentDto
import rs.raf.banka1.mobile.data.remote.requests.ValidateRequest
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.data.remote.responses.GenerateResponse
import rs.raf.banka1.mobile.data.remote.responses.NewPaymentResponseDto
import rs.raf.banka1.mobile.data.remote.responses.PageResponse
import rs.raf.banka1.mobile.data.remote.responses.StatusResponse
import rs.raf.banka1.mobile.data.remote.responses.TransactionResponseDto
import rs.raf.banka1.mobile.data.remote.responses.ValidateResponse
import rs.raf.banka1.mobile.data.repository.ClientData
import rs.raf.banka1.mobile.data.repository.UserPreferencesRepository
import rs.raf.banka1.mobile.domain.ips.IpsStringParser
import rs.raf.banka1.mobile.presentation.viewmodels.main.PaymentContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.PaymentViewModel

class PaymentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeVerificationApi: FakeVerificationApiForPayment
    private lateinit var fakeTransactionApi: FakeTransactionApiForPayment
    private val userPrefs: UserPreferencesRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVerificationApi = FakeVerificationApiForPayment()
        fakeTransactionApi = FakeTransactionApiForPayment()
        every { userPrefs.readClientData() } returns flowOf(
            ClientData(id = 1L, name = "Test", lastName = "User", email = "test@test.com")
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PaymentViewModel(
        savedStateHandle = SavedStateHandle(),
        accountApi = FakeAccountApiForPayment(),
        verificationApi = fakeVerificationApi,
        transactionApi = fakeTransactionApi,
        userPrefs = userPrefs,
        ipsParser = IpsStringParser()
    )

    /** Fills the form with valid data and requests OTP. */
    private fun PaymentViewModel.fillFormAndRequestOtp() {
        setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.ToAccount, "123456789012345678"))
        setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Recipient, "Ana Anic"))
        setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Amount, "100"))
        setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Code, "289"))
        setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Purpose, "Uplata"))
        setEvent(PaymentContract.UiEvent.RequestOtp)
    }

    @Test
    fun `status poll VERIFIED triggers payment exactly once`() = runTest(testDispatcher) {
        // First poll → PENDING, second poll → VERIFIED
        fakeVerificationApi.statusQueue = ArrayDeque(listOf("PENDING", "VERIFIED"))

        val vm = createViewModel()
        advanceUntilIdle() // loadAccounts + init work completes (no delays involved)

        vm.fillFormAndRequestOtp()
        // requestOtp runs eagerly (UnconfinedTestDispatcher), polling coroutine starts
        // and suspends at delay(2_000). Do NOT call advanceUntilIdle() here — that would
        // drain all 150 poll iterations at once.

        // Advance past first 2s delay → PENDING, no payment yet
        advanceTimeBy(2_001)
        assertThat(vm.state.value.paymentResult).isNull()

        // Advance past second 2s delay → VERIFIED → payment fires
        advanceTimeBy(2_001)

        assertThat(vm.state.value.paymentResult).isNotNull()
        assertThat(fakeTransactionApi.createPaymentCallCount).isEqualTo(1)
        assertThat(vm.state.value.awaitingApproval).isFalse()
    }

    @Test
    fun `status poll EXPIRED sets otpExpired state and never calls createPayment`() = runTest(testDispatcher) {
        fakeVerificationApi.statusQueue = ArrayDeque(listOf("EXPIRED"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.fillFormAndRequestOtp()
        // polling coroutine suspended at first delay

        advanceTimeBy(2_001) // first poll → EXPIRED

        assertThat(vm.state.value.otpExpired).isTrue()
        assertThat(vm.state.value.awaitingApproval).isFalse()
        assertThat(vm.state.value.paymentResult).isNull()
        assertThat(fakeTransactionApi.createPaymentCallCount).isEqualTo(0)
    }

    @Test
    fun `typed submit and poll both see VERIFIED but createPayment fires only once`() = runTest(testDispatcher) {
        // Both validate (typed) and getSessionStatus (poll) return VERIFIED
        fakeVerificationApi.validateStatus = "VERIFIED"
        fakeVerificationApi.statusQueue = ArrayDeque(listOf("VERIFIED"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.fillFormAndRequestOtp()
        // polling coroutine suspended at first delay

        // Typed submit fires first — validate → VERIFIED → completeIfVerified (guard sets true)
        vm.setEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Otp, "123456"))
        vm.setEvent(PaymentContract.UiEvent.SubmitPayment)
        // submitPayment runs eagerly with UnconfinedTestDispatcher

        assertThat(fakeTransactionApi.createPaymentCallCount).isEqualTo(1)

        // Poll fires — guard is already true, second doPayment must not run
        advanceTimeBy(2_001)

        assertThat(fakeTransactionApi.createPaymentCallCount).isEqualTo(1)
    }
}

// ---- Fakes ----

private class FakeVerificationApiForPayment : VerificationApi {

    var validateStatus: String = "VERIFIED"
    var statusQueue: ArrayDeque<String> = ArrayDeque()

    override suspend fun generate(request: GenerateRequest): NetworkResult<GenerateResponse> =
        NetworkResult.Success(GenerateResponse(sessionId = 1L))

    override suspend fun validate(request: ValidateRequest): NetworkResult<ValidateResponse> =
        NetworkResult.Success(ValidateResponse(valid = true, status = validateStatus, remainingAttempts = 3))

    override suspend fun getSessionStatus(sessionId: Long): NetworkResult<StatusResponse> {
        val status = statusQueue.removeFirstOrNull() ?: "PENDING"
        return NetworkResult.Success(StatusResponse(sessionId = sessionId, status = status))
    }
}

private class FakeTransactionApiForPayment : TransactionApi {

    var createPaymentCallCount = 0

    override suspend fun createPayment(request: NewPaymentDto): NetworkResult<NewPaymentResponseDto> {
        createPaymentCallCount++
        return NetworkResult.Success(NewPaymentResponseDto(status = "COMPLETED", message = "OK"))
    }

    override suspend fun getTransactionsForAccount(
        accountNumber: String, page: Int, size: Int
    ): NetworkResult<PageResponse<TransactionResponseDto>> =
        NetworkResult.Success(PageResponse(content = emptyList(), totalElements = 0L, totalPages = 0, number = 0, size = 0))

    override suspend fun getTransactions(
        clientId: Long, page: Int, size: Int
    ): NetworkResult<PageResponse<TransactionResponseDto>> =
        NetworkResult.Success(PageResponse(content = emptyList(), totalElements = 0L, totalPages = 0, number = 0, size = 0))

    override suspend fun getTransactionByOrderNumber(
        orderNumber: String
    ): NetworkResult<TransactionResponseDto> =
        NetworkResult.Ignored()
}

private class FakeAccountApiForPayment : AccountApi {

    override suspend fun getMyAccounts(page: Int, size: Int): NetworkResult<PageResponse<AccountDetailsResponseDto>> =
        NetworkResult.Success(
            PageResponse(
                content = listOf(
                    AccountDetailsResponseDto(
                        brojRacuna = "123456789012345678",
                        currency = "RSD",
                        raspolozivoStanje = 10_000.0
                    )
                ),
                totalElements = 1L,
                totalPages = 1,
                number = 0,
                size = 1
            )
        )

    override suspend fun getAccountDetails(accountId: Long): NetworkResult<AccountDetailsResponseDto> =
        NetworkResult.Ignored()

    override suspend fun getAccountDetailsByNumber(accountNumber: String): NetworkResult<AccountDetailsResponseDto> =
        NetworkResult.Ignored()
}
