package com.wxjxpp.neiro.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wxjxpp.neiro.MainActivity

/**
 * 媒体前台服务：承载 MediaSession，让系统媒体通知/锁屏控制生效。
 *
 * - 通知栏：上一首 / 暂停播放 / 下一首 / 关闭（停止服务）
 * - 会话由 [PlaybackSessionHolder] 共享给控制器，保证 App 内与系统看到的是同一个播放器
 */
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // 会话可能尚未创建（App 未播放过）：此时不强行建会话，
        // onGetSession 返回 null 即可；App 开始播放后会话就位。
        PlaybackSessionHolder.peek()?.let {
            // 确保通知渠道存在（Android 8+ 必需）
            ensureChannel()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        PlaybackSessionHolder.peek()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "正在播放",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
    }

    override fun onDestroy() {
        // 服务销毁时释放共享会话（App 内控制器仍持有 player，不影响应用内播放）
        PlaybackSessionHolder.releaseIfOwner()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        /** 通知栏关闭动作。 */
        const val ACTION_STOP = "com.wxjxpp.neiro.action.STOP_PLAYBACK"
    }
}

/**
 * 下载进度通知工具：开始下载 / 完成时在通知栏留痕。
 *
 * 单独用一个轻量对象而不是塞进 Service，避免为了一条通知拉起前台服务。
 */
object DownloadNotifier {
    const val CHANNEL_ID = "download"
    private const val BASE_ID = 40000

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "下载",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    /** 开始下载通知（静默，不响铃不震动）。 */
    fun showStart(context: Context, title: String, index: Int): Int = post(context, index) {
        setContentTitle("开始下载")
        setContentText(title)
        setOngoing(true)
    }

    /** 完成/失败通知（可被用户划走），带目标目录。 */
    fun showDone(context: Context, title: String, message: String, index: Int): Int = post(context, index) {
        setContentTitle(title)
        setContentText(message)
        setStyle(Notification.BigTextStyle().bigText(message))
        setOngoing(false)
    }

    private inline fun post(
        context: Context,
        slot: Int,
        configure: Notification.Builder.() -> Unit,
    ): Int {
        channel(context)
        val id = BASE_ID + slot.coerceIn(0, 999)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentIntent(context))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        builder.configure()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(id, builder.build()) }
        return id
    }

    private fun contentIntent(context: Context): PendingIntent? = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}