package rs.raf.banka1.mobile.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import rs.raf.banka1.mobile.R
import rs.raf.banka1.mobile.core.di.ApplicationScope
import javax.inject.Inject

@AndroidEntryPoint
class QuickApproveReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: QuickApproveHandler

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    companion object {
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_CODE = "code"
        const val EXTRA_OPERATION_TYPE = "operationType"
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        private const val RESULT_NOTIFICATION_ID = 1004
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sessionIdStr = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val sessionId = sessionIdStr.toLongOrNull() ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, BankMessagingService.NOTIFICATION_ID)

        val pendingResult = goAsync()
        appScope.launch {
            try {
                when (val result = handler.approve(sessionId, code)) {
                    is ApproveResult.Verified -> {
                        cancelAndShowResult(context, notifId, "Odobreno", "Verifikacija je uspesna.")
                    }
                    is ApproveResult.Failed -> {
                        cancelAndShowResult(context, notifId, "Odobravanje nije uspelo", result.reason)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelAndShowResult(context: Context, originalNotifId: Int, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    BankMessagingService.CHANNEL_ID,
                    "Verifikacioni kodovi",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        nm.cancel(originalNotifId)

        val notification = NotificationCompat.Builder(context, BankMessagingService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(RESULT_NOTIFICATION_ID, notification)
    }
}
