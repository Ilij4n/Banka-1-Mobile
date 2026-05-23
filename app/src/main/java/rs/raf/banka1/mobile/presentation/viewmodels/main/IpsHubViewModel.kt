package rs.raf.banka1.mobile.presentation.viewmodels.main

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.apis.AccountApi
import rs.raf.banka1.mobile.data.local.IpsQrCodeEntity
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.data.repository.UserPreferencesRepository
import rs.raf.banka1.mobile.domain.ips.IpsFields
import rs.raf.banka1.mobile.domain.ips.IpsStringGenerator
import rs.raf.banka1.mobile.domain.ips.IpsStringParser
import rs.raf.banka1.mobile.domain.repository.IpsRepository
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class IpsHubViewModel @Inject constructor(
    private val ipsRepository: IpsRepository,
    private val accountApi: AccountApi,
    private val parser: IpsStringParser,
    private val generator: IpsStringGenerator,
    private val userPrefs: UserPreferencesRepository
) : BaseMviViewModel<IpsHubContract.UiState, IpsHubContract.UiEvent, IpsHubContract.SideEffect>(
    IpsHubContract.UiState()
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val ipsAdapter = moshi.adapter(IpsFields::class.java)

    init {
        observeQrCodes()
        loadAccounts()
    }

    private fun observeQrCodes() {
        viewModelScope.launch {
            val userId = userPrefs.readClientData().firstOrNull()?.id ?: return@launch
            ipsRepository.observeForUser(userId).collect { codes ->
                setState { copy(qrCodes = codes) }
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            when (val result = accountApi.getMyAccounts(page = 0, size = 50)) {
                is NetworkResult.Success -> {
                    val all = result.data.content ?: emptyList()
                    val rsd = all.filter { (it.currency ?: "").equals("RSD", ignoreCase = true) }
                    val tekuci = rsd.filter { acc ->
                        val combined = "${acc.tip ?: ""} ${acc.accountType ?: ""} ${acc.accountCategory ?: ""}"
                        combined.contains("TEKUCI", ignoreCase = true) || combined.contains("CURRENT", ignoreCase = true)
                    }
                    setState { copy(accounts = if (tekuci.isNotEmpty()) tekuci else rsd) }
                }
                is NetworkResult.Error -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Exception -> setState { copy(error = result.toErrorData()) }
                is NetworkResult.Ignored -> {}
            }
        }
    }

    override fun setEvent(event: IpsHubContract.UiEvent) {
        when (event) {
            is IpsHubContract.UiEvent.QrScanned -> handleQrScanned(event.rawString)
            is IpsHubContract.UiEvent.ConfirmCreate -> confirmCreate(event.fields)
            is IpsHubContract.UiEvent.ConfirmEdit -> confirmEdit(event.id, event.fields)
            is IpsHubContract.UiEvent.Delete -> deleteCode(event.id)
            is IpsHubContract.UiEvent.ShowCode -> setState { copy(showingCode = event.entity) }
            is IpsHubContract.UiEvent.DismissCode -> setState { copy(showingCode = null) }
            is IpsHubContract.UiEvent.StartCreate -> setState { copy(isCreating = true) }
            is IpsHubContract.UiEvent.StartEdit -> setState { copy(editingEntity = event.entity) }
            is IpsHubContract.UiEvent.DismissSheet -> setState { copy(isCreating = false, editingEntity = null) }
            is IpsHubContract.UiEvent.ClearError -> setState { copy(error = null) }
        }
    }

    private fun handleQrScanned(raw: String) {
        parser.parse(raw).fold(
            onSuccess = { fields ->
                val json = ipsAdapter.toJson(fields)
                val encoded = Uri.encode(json)
                sendEffect { IpsHubContract.SideEffect.NavigateToPaymentWithIps(encoded) }
            },
            onFailure = { error ->
                setState {
                    copy(error = ErrorData(title = "Greška", message = error.message ?: "Neispravan IPS QR kod"))
                }
            }
        )
    }

    private fun confirmCreate(fields: IpsFields) {
        viewModelScope.launch {
            val userId = userPrefs.readClientData().firstOrNull()?.id
            val ipsString = generator.build(fields)
            val entity = IpsQrCodeEntity(
                userId = userId,
                recipientAccount = fields.recipientAccount,
                recipientName = fields.recipientName,
                amount = fields.amount,
                paymentCode = fields.paymentCode,
                referenceNumber = fields.referenceNumber,
                purpose = fields.purpose,
                payerInfo = fields.payerInfo,
                rawIpsString = ipsString,
                createdAt = System.currentTimeMillis()
            )
            ipsRepository.save(entity)
            setState { copy(isCreating = false) }
        }
    }

    private fun confirmEdit(id: Long, fields: IpsFields) {
        viewModelScope.launch {
            val userId = userPrefs.readClientData().firstOrNull()?.id
            val ipsString = generator.build(fields)
            val entity = IpsQrCodeEntity(
                id = id,
                userId = userId,
                recipientAccount = fields.recipientAccount,
                recipientName = fields.recipientName,
                amount = fields.amount,
                paymentCode = fields.paymentCode,
                referenceNumber = fields.referenceNumber,
                purpose = fields.purpose,
                payerInfo = fields.payerInfo,
                rawIpsString = ipsString,
                createdAt = System.currentTimeMillis()
            )
            ipsRepository.update(entity)
            setState { copy(editingEntity = null) }
        }
    }

    private fun deleteCode(id: Long) {
        viewModelScope.launch {
            ipsRepository.delete(id)
        }
    }
}

interface IpsHubContract {
    data class UiState(
        val qrCodes: List<IpsQrCodeEntity> = emptyList(),
        val accounts: List<AccountDetailsResponseDto> = emptyList(),
        val isLoading: Boolean = false,
        val error: ErrorData? = null,
        val showingCode: IpsQrCodeEntity? = null,
        val editingEntity: IpsQrCodeEntity? = null,
        val isCreating: Boolean = false
    )

    sealed interface UiEvent {
        data class QrScanned(val rawString: String) : UiEvent
        data class ConfirmCreate(val fields: IpsFields) : UiEvent
        data class ConfirmEdit(val id: Long, val fields: IpsFields) : UiEvent
        data class Delete(val id: Long) : UiEvent
        data class ShowCode(val entity: IpsQrCodeEntity) : UiEvent
        data object DismissCode : UiEvent
        data object StartCreate : UiEvent
        data class StartEdit(val entity: IpsQrCodeEntity) : UiEvent
        data object DismissSheet : UiEvent
        data object ClearError : UiEvent
    }

    sealed interface SideEffect {
        data class NavigateToPaymentWithIps(val encodedPayload: String) : SideEffect
    }
}
