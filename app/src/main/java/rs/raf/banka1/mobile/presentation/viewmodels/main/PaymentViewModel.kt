package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val accountApi: AccountApi,
    private val verificationApi: VerificationApi,
    private val transactionApi: TransactionApi,
    private val userPrefs: UserPreferencesRepository
) : BaseMviViewModel<PaymentContract.UiState, PaymentContract.UiEvent, PaymentContract.SideEffect>(
    PaymentContract.UiState()
) {

    init {
        loadAccounts()
    }

    override fun setEvent(event: PaymentContract.UiEvent) {
        when (event) {
            is PaymentContract.UiEvent.FromAccountSelected -> setState { copy(fromAccount = event.account) }
            is PaymentContract.UiEvent.FieldChanged -> onFieldChanged(event.field, event.value)
            is PaymentContract.UiEvent.RequestOtp -> requestOtp()
            is PaymentContract.UiEvent.SubmitPayment -> submitPayment()
            is PaymentContract.UiEvent.ResendOtp -> resendOtp()
            is PaymentContract.UiEvent.BackToForm -> setState { copy(step = PaymentContract.Step.FORM, otpCode = "", remainingOtpAttempts = null) }
            is PaymentContract.UiEvent.ClearError -> setState { copy(error = null) }
            is PaymentContract.UiEvent.OpenVerificationCodes -> sendEffect { PaymentContract.SideEffect.OpenVerificationCodes }
            is PaymentContract.UiEvent.DismissResult -> setState { copy(paymentResult = null) }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            when (val result = accountApi.getMyAccounts(page = 0, size = 50)) {
                is NetworkResult.Success -> setState {
                    copy(
                        isLoading = false,
                        accounts = result.data.content ?: emptyList(),
                        fromAccount = result.data.content?.firstOrNull()
                    )
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
                is NetworkResult.Success -> setState {
                    copy(isLoading = false, sessionId = result.data.sessionId, step = PaymentContract.Step.OTP)
                }
                is NetworkResult.Error -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(isLoading = false, error = result.toErrorData()) }
                is NetworkResult.Ignored -> setState { copy(isLoading = false) }
            }
        }
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
                is NetworkResult.Success -> setState {
                    copy(isResending = false, sessionId = result.data.sessionId, otpCode = "", remainingOtpAttempts = null)
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
                        doPayment(s, sessionId)
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
        val paymentResult: rs.raf.banka1.mobile.data.remote.responses.NewPaymentResponseDto? = null
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
    }

    sealed interface SideEffect {
        data object OpenVerificationCodes : SideEffect
    }
}
