package com.wxjxpp.musicplayer.app

import android.app.Application
import com.wxjxpp.musicplayer.core.data.DiaryRepository
import com.wxjxpp.musicplayer.core.data.InMemoryDiaryRepository
import com.wxjxpp.musicplayer.core.data.InMemoryLyricsRepository
import com.wxjxpp.musicplayer.core.data.InMemoryPlaylistRepository
import com.wxjxpp.musicplayer.core.data.InMemorySettingsRepository
import com.wxjxpp.musicplayer.core.data.InMemorySongRepository
import com.wxjxpp.musicplayer.core.data.InMemoryStatsRepository
import com.wxjxpp.musicplayer.core.data.LyricsRepository
import com.wxjxpp.musicplayer.core.data.PlaylistRepository
import com.wxjxpp.musicplayer.core.data.SettingsRepository
import com.wxjxpp.musicplayer.core.data.SongRepository
import com.wxjxpp.musicplayer.core.data.StatsRepository
import com.wxjxpp.musicplayer.core.lyrics.LyricsParserRegistry
import com.wxjxpp.musicplayer.core.lyrics.defaultLyricsParserRegistry
import com.wxjxpp.musicplayer.core.player.InMemoryPlayerController
import com.wxjxpp.musicplayer.core.player.PlayerController
import com.wxjxpp.musicplayer.core.scanner.AndroidMediaScanner
import com.wxjxpp.musicplayer.core.scanner.AndroidMetadataReader
import com.wxjxpp.musicplayer.core.scanner.MediaScanner
import com.wxjxpp.musicplayer.core.scanner.MetadataReader
import com.wxjxpp.musicplayer.core.source.DefaultMusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.MusicSourceRegistry
import com.wxjxpp.musicplayer.core.together.NoopTogetherTransport
import com.wxjxpp.musicplayer.core.together.TogetherTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 依赖容器（手写 DI）。
 *
 * 这是整个模板唯一的"装配点"：把接口和实现绑在一起。
 * 后续要换成真实实现，只改这个文件；
 * 要迁移到 Hilt/Koin，也只需要替换这一层，业务代码不动。
 */
interface AppContainer {
    val appScope: CoroutineScope

    val songRepository: SongRepository
    val playlistRepository: PlaylistRepository
    val lyricsRepository: LyricsRepository
    val statsRepository: StatsRepository
    val diaryRepository: DiaryRepository
    val settingsRepository: SettingsRepository

    val playerController: PlayerController
    val sourceRegistry: MusicSourceRegistry
    val lyricsParsers: LyricsParserRegistry
    val togetherTransport: TogetherTransport
    val mediaScanner: MediaScanner
    val metadataReader: MetadataReader
}

class DefaultAppContainer(
    private val application: Application,
) : AppContainer {

    override val appScope = CoroutineScope(SupervisorJob())

    // === 扫描与元数据 ===
    override val mediaScanner: MediaScanner = AndroidMediaScanner(application.contentResolver)
    override val metadataReader: MetadataReader = AndroidMetadataReader()

    // === 数据层 ===
    // TODO 接入 Room 后替换为 RoomSongRepository 等实现
    override val songRepository: SongRepository = InMemorySongRepository(mediaScanner, metadataReader)
    override val playlistRepository: PlaylistRepository = InMemoryPlaylistRepository()
    override val lyricsRepository: LyricsRepository = InMemoryLyricsRepository()
    override val statsRepository: StatsRepository = InMemoryStatsRepository()
    override val diaryRepository: DiaryRepository = InMemoryDiaryRepository()
    override val settingsRepository: SettingsRepository = InMemorySettingsRepository()

    // === 播放层 ===
    // TODO 接入 Media3 后替换为 Media3PlayerController
    override val playerController: PlayerController = InMemoryPlayerController(appScope)

    // === 扩展点 ===
    // 新增音源在这里注册：DefaultMusicSourceRegistry(listOf(LocalSource(), WebDavSource(), ...))
    override val sourceRegistry: MusicSourceRegistry = DefaultMusicSourceRegistry()
    override val lyricsParsers: LyricsParserRegistry = defaultLyricsParserRegistry()

    // TODO 接入一起听服务后替换为 WebSocketTogetherTransport
    override val togetherTransport: TogetherTransport = NoopTogetherTransport()

    fun shutdown() {
        playerController.release()
        appScope.cancel()
    }
}

/** Application 入口，持有全局容器。 */
class MusicPlayerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}