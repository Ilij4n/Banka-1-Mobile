package rs.raf.banka1.mobile.presentation.viewmodels.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.data.local.NotificationDao
import rs.raf.banka1.mobile.data.local.NotificationEntity
import rs.raf.banka1.mobile.presentation.viewmodels.BaseMviViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao
) : BaseMviViewModel<NotificationsContract.UiState, NotificationsContract.UiEvent, NotificationsContract.SideEffect>(
    NotificationsContract.UiState()
) {

    init {
        notificationDao.observeAll()
            .onEach { entities ->
                setState { copy(notifications = entities.map { it.toUiModel() }, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override fun setEvent(event: NotificationsContract.UiEvent) {
        when (event) {
            is NotificationsContract.UiEvent.MarkRead ->
                viewModelScope.launch { notificationDao.markRead(event.id) }
            is NotificationsContract.UiEvent.Delete ->
                viewModelScope.launch { notificationDao.deleteById(event.id) }
            is NotificationsContract.UiEvent.MarkAllRead ->
                viewModelScope.launch { notificationDao.markAllRead() }
            is NotificationsContract.UiEvent.ClearAll ->
                viewModelScope.launch { notificationDao.deleteAll() }
        }
    }
}

private fun NotificationEntity.toUiModel() = NotificationsContract.NotificationUiModel(
    id = id,
    type = type,
    title = title,
    body = body,
    orderId = orderId,
    receivedAt = receivedAt,
    isRead = isRead
)

interface NotificationsContract {

    data class UiState(
        val notifications: List<NotificationUiModel> = emptyList(),
        val isLoading: Boolean = true
    )

    sealed interface UiEvent {
        data class MarkRead(val id: Long) : UiEvent
        data class Delete(val id: Long) : UiEvent
        data object MarkAllRead : UiEvent
        data object ClearAll : UiEvent
    }

    sealed interface SideEffect

    data class NotificationUiModel(
        val id: Long,
        val type: String,
        val title: String,
        val body: String,
        val orderId: Long?,
        val receivedAt: Long,
        val isRead: Boolean
    )
}
