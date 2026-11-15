package app.pleossupercare.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.pleossupercare.phone.data.CarRequest

/**
 * 차에서 온 알림 요청(트렁크 짐 · 뒷좌석)을 중요도 높은 알림으로 띄웁니다.
 * 앱이 떠 있는 동안(앞 또는 뒤) 서버를 보다가 새 알림이 오면 띄웁니다. 푸시(FCM)는 실제 서비스 설계입니다.
 */
object Notifier {
    private const val CHANNEL = "car_reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "차에서 온 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "트렁크에 짐이 남았는지 등 차 앱이 보낸 알림"
                enableVibration(true)
            })
        }
    }

    fun reminder(context: Context, r: CarRequest) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, r.id.hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (r.kind == "trunk_reminder") "트렁크 확인" else "뒷좌석 확인"
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(r.text ?: "차에서 온 알림")
            .setStyle(Notification.BigTextStyle().bigText(r.text ?: "차에서 온 알림"))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(r.id.hashCode(), n)
    }

    fun cancel(context: Context, requestId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(requestId.hashCode())
    }
}
