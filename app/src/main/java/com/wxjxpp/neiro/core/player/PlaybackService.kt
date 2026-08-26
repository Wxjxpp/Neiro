package com.wxjxpp.neiro.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wxjxpp.neiro.MainActivity
import com.wxjxpp.neiro.R

/**
 * 媒体前台服务：承载 MediaSession，让系统控制中心/锁屏媒体控件生效。
 *
 * 架构（官方推荐做法，不自己拼通知）：
 * - 会话由 [PlaybackSessionHolder] 共享（App 内 controller 与本服务同一个 player）
 * - 会话就位后调 [addSession]，Media3 的 [DefaultMediaNotificationProvider]
 *   会自动生成 MediaStyle 通知 + 进前台，控制中心随即出现媒体控件
 * - Controller 建好 player 后广播 [ACTION_PLAYER_READY]，本服务据此补挂会话
 *   （服务启动早于 player 就绪时的补偿路径）
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attachedSession: MediaSession? = null
    private var readyReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // Media3 自带通知提供器：负责 MediaStyle 通知与前台服务生命周期
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )
        readyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_PLAYER_READY) attachSessionIfNeeded()
            }
        }
        ContextCompat.registerReceiver(
            this,
            readyReceiver,
            IntentFilter(ACTION_PLAYER_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        attachSessionIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachSessionIfNeeded()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 把共享会话挂到服务上。
     *
     * 挂上之后 Media3 会立刻构建媒体样式通知（控制中心可见）。
     * 会话尚未创建时静默返回，等 PLAYER_READY 广播或下一次 onStartCommand 再试。
     */
    private fun attachSessionIfNeeded() {
        mainHandler.post {
            val session = PlaybackSessionHolder.peek()
            if (session == null) {
                Log.d(TAG, "session not ready yet, will retry on PLAYER_READY")
                return@post
            }
            if (attachedSession === session) return@post
            runCatching {
                attachedSession?.let { removeSession(it) }
                addSession(session)
                attachedSession = session
                Log.d(TAG, "session attached; Media3 will post the media notification")
            }.onFailure { Log.w(TAG, "attach session failed", it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        PlaybackSessionHolder.peek()

    /**
     * 通知渠道。Media3 自己也会建，这里提前建好保证渠道名是中文、
     * 且用户在"通知类别"里能看到"正在播放"。
     */
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
        readyReceiver?.let { runCatching { unregisterReceiver(it) } }
        readyReceiver = null
        attachedSession = null
        // 会话本身由 Holder 管理（App 内播放器仍在用），这里不 release
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 30001
        /** Controller 建好 player 后发此广播，服务据此补挂会话。 */
        const val ACTION_PLAYER_READY = "com.wxjxpp.neiro.action.PLAYER_READY"
        private const val TAG = "PlaybackService"
    }
}

// 下载进度取消动作常量（Kotlin object 内不允许 companion object，放顶层）
const val ACTION_CANCEL_DOWNLOAD = "com.wxjxpp.neiro.action.CANCEL_DOWNLOAD"
const val EXTRA_CANCEL_SONG_ID = "songId"

/**
 * 实时下载进度通知（独立渠道，带百分比进度条 + 取消按钮）。
 * 完成后自动消失，由 [DownloadNotifier] 接棒留痕。
 */
object DownloadProgressNotifier {
    const val CHANNEL_ID = "download_progress"
    private const val BASE_ID = 50000
    private val ids = mutableMapOf<String, Int>()
    private val titles = mutableMapOf<String, String>()
    private val artists = mutableMapOf<String, String>()

    private fun idFor(songId: String) = ids.getOrPut(songId) { BASE_ID + songId.hashCode().mod(1000) }

    fun start(context: Context, songId: String, title: String, artistName: String) {
        titles[songId] = title
        artists[songId] = artistName
        channel(context)
        post(
            context, songId,
            baseBuilder(context)
                .setContentTitle(title)
                .setContentText(artistName)
                .setProgress(100, 0, true)
                .setOngoing(true),
        )
    }

    fun update(context: Context, songId: String, downloaded: Long, total: Long) {
        if (!ids.containsKey(songId)) return
        val indeterminate = total <= 0L
        val percent = if (indeterminate) 0 else (downloaded * 100 / total).toInt().coerceIn(0, 100)
        val artist = artists[songId].orEmpty()
        post(
            context, songId,
            baseBuilder(context)
                .setContentTitle(titles[songId].orEmpty().ifEmpty { "正在下载" })
                .setContentText(if (indeterminate) artist else "$artist · $percent%")
                .setProgress(100, percent, indeterminate)
                .setOngoing(true),
        )
    }

    fun finish(context: Context, songId: String) {
        ids.remove(songId)?.let { notifyId ->
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.cancel(notifyId) }
        }
        titles.remove(songId)
        artists.remove(songId)
    }

    private fun baseBuilder(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setSilent(true)

    private fun post(context: Context, songId: String, builder: NotificationCompat.Builder) {
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, "取消",
            PendingIntent.getService(
                context, ("cancel_$songId").hashCode(),
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
                        CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
    }
}

/** 下载开始/结束的留痕通知。 */
object DownloadNotifier {
    const val CHANNEL_ID = "download"
    private const val BASE_ID = 40000

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    fun showStart(context: Context, title: String, index: Int): Int = post(context, index) {
        setContentTitle("开始下载")
        setContentText(title)
        setOngoing(true)
    }

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
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        builder.configure()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(id, builder.build()) }
        return id
    }
}