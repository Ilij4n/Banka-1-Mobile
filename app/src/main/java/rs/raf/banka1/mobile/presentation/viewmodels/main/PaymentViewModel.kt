package rs.raf.banka1.mobile.presentation.viewmodels.main

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.apis.TransactionApi
import rs.raf.banka1.mobile.data.apis.VerificationApi
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.GenerateRequest
import rs.raf.banka1.mobile.data.remote.requests.NewPaymentDto
import rs.raf.banka1.mobile.data.remote.requests.ValidateRequest
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.data.repository.UserPreferencesRepository
import rs.raf.banka1.mobile.domain.ips.IpsFields
import rs.raf.banka1.mobile.domain.ips.IpsStringParser
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val accountApi: AccountApi,
    private val verificationApi: VerificationApi,
    private val transactionApi: TransactionApi,
    private val userPrefs: UserPreferencesRepository,
    private val ipsParser: IpsStringParser
) : BaseMviViewModel<PaymentContract.UiState, PaymentContract.UiEvent, PaymentContract.SideEffect>(
    PaymentContract.UiState()
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val ipsAdapter = moshi.adapter(IpsFields::class.java)

    private var pollingJob: Job? = null
    private val completionStarted = AtomicBoolean(false)

    // Total polls covering the 5-minute window at 2-second intervals
    private val TOTAL_POLLS = (5 * 60 * 1000L / 2_000L).toInt()

    init {
        prefillFromIpsPayload()
        loadAccounts()
    }

    private fun prefillFromIpsPayload() {
        try {
            val encoded = savedStateHandle.get<String>("ipsPayload") ?: return
            val json = Uri.decode(encoded)
            val fields = ipsAdapter.fromJson(json) ?: return
            applyIpsFields(fields)
        } catch (_: Exception) {}
    }

    private fun applyIpsFields(fields: IpsFields) {
        setState {
            copy(
                fromIps = true,
                toAccountNumber = fields.recipientAccount,
                recipientName = fields.recipientName,
                amountInput = fields.amount?.let {
                    val intPart = it.toLong()
                    val decPart = Math.round((it - intPart) * 100)
                    "$intPart,${decPart.toString().padStart(2, '0')}"
                } ?: "",
                paymentCode = fields.paymentCode?.takeIf { it.matches(Regex("^2\\d{2}$")) } ?: "289",
                paymentPurpose = fields.purpose?.takeIf { it.isNotBlank() } ?: "Plaćanje",
                referenceNumber = fields.referenceNumber ?: "",
                fieldErrors = emptyMap()
            )
        }
        val accounts = state.value.accounts
        if (accounts.isNotEmpty()) {
            val rsd = accounts.filter { (it.currency ?: "").equals("RSD", ignoreCase = true) }
            val tekuci = rsd.filter { acc ->
                val combined = "${acc.tip ?: ""} ${acc.accountType ?: ""} ${acc.accountCategory ?: ""}"
                combined.contains("TEKUCI", ignoreCase = true) || combined.contains("CURRENT", ignoreCase = true)
            }
            val preferred = tekuci.firstOrNull() ?: rsd.firstOrNull()
            if (preferred != null) setState { copy(fromAccount = preferred) }
        }
    }

    private fun handleIpsQrScanned(raw: String) {
        ipsParser.parse(raw).fold(
            onSuccess = { fields -> applyIpsFields(fields) },
            onFailure = { error ->
                setState {
                    copy(error = ErrorData(title = "Greška", message = error.message ?: "Neispravan IPS QR kod"))
                }
            }
        )
    }

    override fun setEvent(event: PaymentContract.UiEvent) {
        when (event) {
            is PaymentContract.UiEvent.FromAccountSelected -> setState { copy(fromAccount = event.account) }
            is PaymentContract.UiEvent.FieldChanged -> onFieldChanged(event.field, event.value)
            is PaymentContract.UiEvent.RequestOtp -> requestOtp()
            is PaymentContract.UiEvent.SubmitPayment -> submitPayment()
            is PaymentContract.UiEvent.ResendOtp -> resendOtp()
            is PaymentContract.UiEvent.BackToForm -> {
                cancelPolling()
                setState {
                    copy(
                        step = PaymentContract.Step.FORM,
                        otpCode = "",
                        remainingOtpAttempts = null,
                        awaitingApproval = false,
                        otpExpired = false
                    )
                }
            }
            is PaymentContract.UiEvent.ClearError -> setState { copy(error = null) }
            is PaymentContract.UiEvent.OpenVerificationCodes -> sendEffect { PaymentContract.SideEffect.OpenVerificationCodes }
            is PaymentContract.UiEvent.DismissResult -> setState { copy(paymentResult = null) }
            is PaymentContract.UiEvent.IpsQrScanned -> handleIpsQrScanned(event.rawString)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
        completionStarted.set(false)
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            when (val result = accountApi.getMyAccounts(page = 0, size = 50)) {
                is NetworkResult.Success -> {
                    val all = result.data.content ?: emptyList()
                    val filtered = if (state.value.fromIps) {
                        val rsd = all.filter { (it.currency ?: "").equals("RSD", ignoreCase = true) }
                        val tekuci = rsd.filter { acc ->
                            val combined = "${acc.tip ?: ""} ${acc.accountType ?: ""} ${acc.accountCategory ?: ""}"
                            combined.contains("TEKUCI", ignoreCase = true) || combined.contains("CURRENT", ignoreCase = true)
                        }
                        when {
                            tekuci.isNotEmpty() -> tekuci
                            rsd.isNotEmpty() -> rsd
                            else -> all
                        }
                    } else {
                        all
                    }
                    setState {
                        copy(
                            isLoading = false,
                            accounts = filtered,
                            fromAccount = filtered.firstOrNull()
                        )
                    }
                }
                is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
    }

    private fun onFieldChanged(field: PaymentContract.Field, value: String) {
        setState {
            copy(
                fieldErrors = fieldErrors - field,
                toAccountNumber = if (field == PaymentContract.Field.ToAccount) value else toAccountNumber,
                recipientName = if (field == PaymentContract.Field.Recipient) value else recipientName,
                amountInput = if (field == PaymentContract.Field.Amount) value else amountInput,
                paymentCode = if (field == PaymentContract.Field.Code) value else paymentCode,
                referenceNumber = if (field == PaymentContract.Field.Reference) value else referenceNumber,
                paymentPurpose = if (field == PaymentContract.Field.Purpose) value else paymentPurpose,
                otpCode = if (field == PaymentContract.Field.Otp) value else otpCode,
            )
        }
    }

    private fun validateForm(): Boolean {
        val s = state.value
        val errors = mutableMapOf<PaymentContract.Field, String>()

        if (!s.toAccountNumber.matches(Regex("^\\d{18,19}$"))) {
            errors[PaymentContract.Field.ToAccount] = "Broj računa mora imati 18–19 cifara"
        }
        if (s.recipientName.isBlank()) {
            errors[PaymentContract.Field.Recipient] = "Naziv primaoca je obavezan"
        }
        val amount = s.amountInput.replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            errors[PaymentContract.Field.Amount] = "Unesite ispravan iznos"
        }
        if (!s.paymentCode.matches(Regex("^2\\d{2}$"))) {
            errors[PaymentContract.Field.Code] = "Poziv na broj mora početi sa 2 i imati 3 cifre (npr. 289)"
        }
        if (s.paymentPurpose.isBlank()) {
            errors[PaymentContract.Field.Purpose] = "Svrha plaćanja je obavezna"
        }

        setState { copy(fieldErrors = errors) }
        return errors.isEmpty()
    }

    private fun requestOtp() {
        if (!validateForm()) return
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val clientData = userPrefs.readClientData().first()
            if (clientData == null) {
                setState {
                    copy(isLoading = false, error = ErrorData(title = "Greška", message = "Podaci o korisniku nisu dostupni."))
                }
                return@launch
            }
            val request = GenerateRequest(
                clientId = clientData.id,
                operationType = "PAYMENT",
                relatedEntityId = UUID.randomUUID().toString(),
                clientEmail = clientData.email
            )
            when (val result = verificationApi.generate(request)) {
                is NetworkResult.Success -> {
                    val sid = result.data.sessionId
                    cancelPolling()
                    setState {
                        copy(
                            isLoading = false,
                            sessionId = sid,
                            step = PaymentContract.Step.OTP,
                            awaitingApproval = true,
                            otpExpired = false
                        )
                    }
                    startStatusPolling(sid)
                }
                is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
    }

    private fun startStatusPolling(sessionId: Long) {
        pollingJob = viewModelScope.launch {
            repeat(TOTAL_POLLS) {
                delay(2_000)
                when (val statusResult = verificationApi.getSessionStatus(sessionId)) {
                    is NetworkResult.Success -> {
                        when (statusResult.data.status) {
                            "VERIFIED" -> {
                                completeIfVerified(sessionId)
                                return@launch
                            }
                            "EXPIRED", "CANCELLED" -> {
                                setState { copy(awaitingApproval = false, otpExpired = true) }
                                return@launch
                            }
                            else -> {} // PENDING — keep polling
                        }
                    }
                    else -> {} // network hiccup — keep polling
                }
            }
            // 5-minute window exhausted
            setState { copy(awaitingApproval = false, otpExpired = true) }
        }
    }

    private suspend fun completeIfVerified(sessionId: Long) {
        if (!completionStarted.compareAndSet(false, true)) {
            // Another path already fired completion (e.g. typed code validated first)
            setState { copy(isLoading = false) }
            return
        }
        setState { copy(awaitingApproval = false, isLoading = true) }
        doPayment(state.value, sessionId)
    }

    private fun resendOtp() {
        viewModelScope.launch {
            setState { copy(isResending = true) }
            val clientData = userPrefs.readClientData().first()
            if (clientData == null) {
                setState {
                    copy(isResending = false, error = ErrorData(title = "Greška", message = "Podaci o korisniku nisu dostupni."))
                }
                return@launch
            }
            val request = GenerateRequest(
                clientId = clientData.id,
                operationType = "PAYMENT",
                relatedEntityId = UUID.randomUUID().toString(),
                clientEmail = clientData.email
            )
            when (val result = verificationApi.generate(request)) {
                is NetworkResult.Success -> {
                    val newSid = result.data.sessionId
                    cancelPolling()
                    setState {
                        copy(
                            isResending = false,
                            sessionId = newSid,
                            otpCode = "",
                            remainingOtpAttempts = null,
                            awaitingApproval = true,
                            otpExpired = false
                        )
                    }
                    startStatusPolling(newSid)
                }
                is NetworkResult.Error -> setState { copy(isResending = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isResending = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isResending = false) }
            }
        }
    }

    private fun submitPayment() {
        val s = state.value
        val sessionId = s.sessionId ?: return
        if (s.otpCode.length != 6) return

        viewModelScope.launch {
            setState { copy(isLoading = true) }

            when (val validateResult = verificationApi.validate(ValidateRequest(sessionId, s.otpCode))) {
                is NetworkResult.Success -> {
                    val response = validateResult.data
                    if (response.status == "VERIFIED") {
                        completeIfVerified(sessionId)
                    } else {
                        setState {
                            copy(
                                isLoading = false,
                                remainingOtpAttempts = response.remainingAttempts,
                                fieldErrors = fieldErrors + (PaymentContract.Field.Otp to "Pogrešan kod. Preostalo pokušaja: ${response.remainingAttempts}")
                            )
                        }
                    }
                }
                is NetworkResult.Error -> setState { copy(isLoading = false, error = validateResult.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isLoading = false, error = validateResult.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
    }

    private suspend fun doPayment(s: PaymentContract.UiState, sessionId: Long) {
        val amount = s.amountInput.replace(",", ".").toDouble()
        val dto = NewPaymentDto(
            fromAccountNumber = s.fromAccount?.brojRacuna ?: return,
            toAccountNumber = s.toAccountNumber,
            amount = amount,
            recipientName = s.recipientName,
            paymentCode = s.paymentCode,
            referenceNumber = s.referenceNumber.ifBlank { null },
            paymentPurpose = s.paymentPurpose,
            verificationSessionId = sessionId
        )
        when (val result = transactionApi.createPayment(dto)) {
            is NetworkResult.Success -> {
                val data = result.data
                setState { copy(isLoading = false, paymentResult = data) }
            }
            is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
            is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
            is NetworkResult.Ignored -> setState { copy(isLoading = false) }
        }
    }
}

interface PaymentContract {
    enum class Step { FORM, OTP }
    enum class Field { ToAccount, Recipient, Amount, Code, Reference, Purpose, Otp }

    data class UiState(
        val step: Step = Step.FORM,
        val fromIps: Boolean = false,
        val accounts: List<AccountDetailsResponseDto> = emptyList(),
        val fromAccount: AccountDetailsResponseDto? = null,
        val toAccountNumber: String = "",
        val recipientName: String = "",
        val amountInput: String = "",
        val paymentCode: String = "",
        val referenceNumber: String = "",
        val paymentPurpose: String = "",
        val otpCode: String = "",
        val sessionId: Long? = null,
        val isLoading: Boolean = false,
        val isResending: Boolean = false,
        val fieldErrors: Map<Field, String> = emptyMap(),
        val remainingOtpAttempts: Int? = null,
        val error: ErrorData? = null,
        val paymentResult: rs.raf.banka1.mobile.data.remote.responses.NewPaymentResponseDto? = null,
        val awaitingApproval: Boolean = false,
        val otpExpired: Boolean = false,
    )

    sealed interface UiEvent {
        data class FromAccountSelected(val account: AccountDetailsResponseDto) : UiEvent
        data class FieldChanged(val field: Field, val value: String) : UiEvent
        data object RequestOtp : UiEvent
        data object SubmitPayment : UiEvent
        data object ResendOtp : UiEvent
        data object BackToForm : UiEvent
        data object ClearError : UiEvent
        data object OpenVerificationCodes : UiEvent
        data object DismissResult : UiEvent
        data class IpsQrScanned(val rawString: String) : UiEvent
    }

    sealed interface SideEffect {
        data object OpenVerificationCodes : SideEffect
    }
}
