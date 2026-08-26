package com.wxjxpp.neiro.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wxjxpp.neiro.MainActivity

/**
 * 媒体前台服务：承载 MediaSession，让系统媒体通知/控制中心生效。
 *
 * 关键设计：
 * - onCreate 立即建临时 player + 占位前台通知，躲过 20 秒死线
 * - 接收 PLAYER_READY 广播后绑定真实 player，创建 MediaNotification 供控制中心
 * - onGetSession 始终返回非 null session，确保控制中心能找到
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var tempPlayer: Player? = null
    private var notifyManager: MediaNotification.Provider? = null
    private lateinit var playerReadyReceiver: BroadcastReceiver

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // 注册广播接收器，等待 Controller 通知 player 就绪
        playerReadyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_PLAYER_READY) {
                    Log.d(TAG, "player ready broadcast received")
                    mainHandler.post { bindToRealPlayer() }
                }
            }
        }
        registerReceiver(playerReadyReceiver, IntentFilter(ACTION_PLAYER_READY))
        // 立即建临时 player + 占位前台通知
        mainHandler.post {
            runCatching {
                tempPlayer = createTempPlayer()
                session = PlaybackSessionHolder.getOrCreate(this, tempPlayer!!)
                startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
                Log.d(TAG, "service created with placeholder session")
            }.onFailure { e ->
                Log.e(TAG, "placeholder setup failed", e)
            }
        }
    }

    private fun createTempPlayer(): Player =
        androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            .build()

    /** Controller 通知 player 就绪后调用：绑定真实 player + 创建媒体样式通知。 */
    private fun bindToRealPlayer() {
        val realPlayer = PlaybackSessionHolder.peek()?.player
            ?: run { Log.w(TAG, "no player available"); return }
        // 替换 session
        session?.release()
        session = PlaybackSessionHolder.getOrCreate(this, realPlayer)
        // 更新前台通知为媒体样式
        startForeground(NOTIFICATION_ID, buildMediaStyleNotification(session!!, realPlayer))
        // 监听播放状态变化更新通知
        realPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                mainHandler.post {
                    session?.let { startForeground(NOTIFICATION_ID, buildMediaStyleNotification(it, realPlayer)) }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mainHandler.post {
                    session?.let { startForeground(NOTIFICATION_ID, buildMediaStyleNotification(it, realPlayer)) }
                }
            }
        })
        Log.d(TAG, "bound to real player, media controls should appear in control center")
    }

    private fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neiro")
            .setContentText("正在初始化…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(makeMainIntent())
            .build()

    private fun buildMediaStyleNotification(mediaSession: MediaSession, player: Player): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(makeMainIntent())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val meta = player.mediaMetadata
        builder.setContentTitle(meta.title?.toString() ?: "Neiro")
            .setContentText(meta.artist?.toString() ?: "")
        // MediaStyle 让控制中心识别这是媒体控件（使用标准 Notification.MediaStyle）
        val style = android.app.Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(session?.sessionToken)
        builder.setStyle(style)
        return builder.build()
    }

    private fun makeMainIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session // 非 null，控制中心能找到

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "正在播放", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) },
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(playerReadyReceiver) }
        session?.release()
        tempPlayer?.release()
        PlaybackSessionHolder.releaseIfOwner()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 30001
        const val ACTION_PLAYER_READY = "com.wxjxpp.neiro.action.PLAYER_READY"
        private const val TAG = "PlaybackService"
    }
}

// 下载进度取消动作常量（Kotlin object 内不允许 companion object，放顶层）
const val ACTION_CANCEL_DOWNLOAD = "com.wxjxpp.neiro.action.CANCEL_DOWNLOAD"
const val EXTRA_CANCEL_SONG_ID = "songId"

/**
 * 实时下载进度通知（独立渠道，带百分比进度条 + 取消按钮）。
 * 完成后自动消失，由 DownloadNotifier 接棒留痕。
 */
object DownloadProgressNotifier {
    const val CHANNEL_ID = "download_progress"
    private const val BASE_ID = 50000
    private val ids = mutableMapOf<String, Int>()
    private val titles = mutableMapOf<String, String>()
    private val artists = mutableMapOf<String, String>()

    private fun idFor(songId: String) = ids.getOrPut(songId) { BASE_ID + songId.hashCode().mod(1000) }

    fun start(context: Context, songId: String, title: String, artistName: String) {
        titles[songId] = title; artists[songId] = artistName
        channel(context)
        post(context, songId, baseBuilder(context)
            .setContentTitle(title)
            .setContentText(artistName)
            .setProgress(100, 0, true)
            .setOngoing(true))
    }

    fun update(context: Context, songId: String, downloaded: Long, total: Long) {
        if (!ids.containsKey(songId)) return
        val indeterminate = total <= 0L
        val percent = if (indeterminate) 0 else (downloaded * 100 / total).toInt().coerceIn(0, 100)
        post(context, songId, baseBuilder(context)
            .setContentTitle(titles[songId].ifEmpty { "正在下载" })
            .setContentText(if (indeterminate) artists[songId].orEmpty() else "${artists[songId].orEmpty()} · $percent%")
            .setProgress(100, percent, indeterminate)
            .setOngoing(true))
    }

    fun finish(context: Context, songId: String) {
        ids.remove(songId)?.let { notifyId ->
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notifyId)
        }
        titles.remove(songId); artists.remove(songId)
    }

    private fun baseBuilder(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColorized(false)

    private fun post(context: Context, songId: String, builder: NotificationCompat.Builder) {
        builder.setContentIntent(PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, "取消",
            PendingIntent.getService(context, ("cancel_$songId").hashCode(),
                Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD; putExtra(EXTRA_CANCEL_SONG_ID, songId)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(idFor(songId), builder.build())
    }

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW)
                        .apply { setShowBadge(false) })
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
                    NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    fun showStart(context: Context, title: String, index: Int): Int = post(context, index) {
        setContentTitle("开始下载"); setContentText(title); setOngoing(true)
    }

    fun showDone(context: Context, title: String, message: String, index: Int): Int = post(context, index) {
        setContentTitle(title); setContentText(message)
        setStyle(Notification.BigTextStyle().bigText(message)); setOngoing(false)
    }

    private inline fun post(context: Context, slot: Int, configure: Notification.Builder.() -> Unit): Int {
        channel(context)
        val id = BASE_ID + slot.coerceIn(0, 999)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else @Suppress("DEPRECATION") {
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setAutoCancel(true)
        builder.configure()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, builder.build())
        return id
    }
}
