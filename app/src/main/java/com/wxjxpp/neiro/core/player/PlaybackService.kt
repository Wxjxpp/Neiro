package com.wxjxpp.neiro.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        // 关键：startForegroundService 拉起后必须在时限内调 startForeground()，
        // 否则系统直接抛 ForegroundServiceDidNotStartInTimeException 崩掉整个进程。
        // Media3 的自动通知要等 session 就位才发，点播放瞬间 session 可能还没建好——
        // 所以这里先挂一条占位通知立即进前台保命；session 就位后 Media3 会用
        // 媒体样式通知自动接管（同 id 替换）。
        ensureChannel()
        val placeholder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neiro")
            .setContentText("正在准备播放…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    placeholder,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, placeholder)
            }
            // 立即 detach：满足"已调 startForeground"的系统要求、躲过超时崩溃，
            // 同时解除与占位通知的绑定——session 就位后 Media3 会用自己的
            // 媒体样式通知重新 startForeground，不会出现两条并存。
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
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
        /** 前台服务占位通知 id：Media3 接管后沿用此 id，避免通知闪烁。 */
        const val NOTIFICATION_ID = 30001
    }
}

// Expr：下载进度取消动作（Kotlin object 内不允许 companion object，常量直接放顶层）
const val ACTION_CANCEL_DOWNLOAD = "com.wxjxpp.neiro.action.CANCEL_DOWNLOAD"
const val EXTRA_CANCEL_SONG_ID = "songId"

/**
 * 下载进度通知工具：开始下载 / 完成时在通知栏留痕。
 *
 * 单独用一个轻量对象而不是塞进 Service，避免为了一条通知拉起前台服务。
 */
/**
 * Expr：实时下载进度通知（独立渠道，带百分比进度条 + 取消按钮）。
 *
 * 与 [DownloadNotifier] 的区别：Notifier 是"开始/结束"两条留痕通知；
 * 本对象是下载过程中的**实时进度条**，完成后自动消失（由 showDone 接棒）。
 */
object DownloadProgressNotifier {
    const val CHANNEL_ID = "download_progress"
    private const val BASE_ID = 50000

    /** songId → 通知 id（同一首歌始终复用同一条通知，避免闪烁堆积）。 */
    private val ids = mutableMapOf<String, Int>()

    private fun idFor(songId: String): Int = ids.getOrPut(songId) {
        BASE_ID + songId.hashCode().mod(1000)
    }

    /** 首次回调时创建渠道 + 发出初始进度条。 */
    fun start(context: Context, songId: String, title: String, artistName: String) {
        titles[songId] = title
        artists[songId] = artistName
        channel(context)
        val builder = baseBuilder(context)
            .setContentTitle(title)
            .setContentText(artistName)
            .setProgress(100, 0, true) // total 未知 → 不定式转圈
            .setOngoing(true)
        post(context, songId, builder)
    }

    /** 进度更新：total > 0 时显示确定百分比，否则保持不定式。节流由调用方负责。 */
    fun update(context: Context, songId: String, downloaded: Long, total: Long) {
        if (!ids.containsKey(songId)) return // 已取消/未开始，忽略过期回调
        val indeterminate = total <= 0L
        val percent = if (indeterminate) 0 else (downloaded * 100 / total).toInt().coerceIn(0, 100)
        val title = titles[songId].orEmpty()
        val artist = artists[songId].orEmpty()
        val builder = baseBuilder(context)
            .setContentTitle(title.ifEmpty { "正在下载" })
            .setContentText(if (indeterminate) artist else "$artist · $percent%")
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
        post(context, songId, builder)
    }

    /** 结束（成功/失败）：撤掉进度条，交给 DownloadNotifier.showDone 留痕。 */
    fun finish(context: Context, songId: String) {
        ids.remove(songId)?.let { notifyId ->
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.cancel(notifyId) }
        }
        titles.remove(songId); artists.remove(songId)
    }

    private val titles = mutableMapOf<String, String>()
    private val artists = mutableMapOf<String, String>()

    private fun baseBuilder(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColorized(false)

    /** 组装最终通知：内容意图进 App；动作按钮发 CANCEL_DOWNLOAD 给 PlaybackService。 */
    private fun post(context: Context, songId: String, builder: NotificationCompat.Builder) {
        builder.setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "取消",
            PendingIntent.getService(
                context,
                ("cancel_$songId").hashCode(),
                Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD
                    putExtra(EXTRA_CANCEL_SONG_ID, songId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(idFor(songId), builder.build()) }
    }

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "下载进度",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
    }
}

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