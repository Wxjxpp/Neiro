package com.wxjxpp.neiro.core.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

/**
 * 进程级共享 MediaSession。
 *
 * App 内的 [Media3PlayerController] 与系统侧的 [PlaybackService] 通过这里
 * 共享同一个会话实例，保证通知栏/锁屏控制的就是应用内正在用的那个播放器。
 */
object PlaybackSessionHolder {

    @Volatile
    private var session: MediaSession? = null

    /** 当前会话（可能为 null：尚未开始播放）。 */
    fun peek(): MediaSession? = session

    /**
     * 取已有会话；若不存在或绑的不是 [player]，则用当前 player 重建。
     * 必须在主线程调用（MediaSession 构造要求）。
     */
    fun getOrCreate(context: Context, player: Player): MediaSession =
        synchronized(this) {
            session?.takeIf { it.player == player }?.let { return it }
            runCatching { session?.release() }
            MediaSession.Builder(context, player)
                .build()
                .also { session = it }
        }

    /** 释放当前会话（服务销毁时调用）。播放器本身不在这里释放。 */
    fun releaseIfOwner() {
        synchronized(this) {
            runCatching { session?.release() }
            session = null
        }
    }
}