package com.wxjxpp.musicplayer.app

import android.app.Application
import androidx.room.Room
import com.wxjxpp.musicplayer.core.data.DataStoreSettingsRepository
import com.wxjxpp.musicplayer.core.data.DiaryRepository
import com.wxjxpp.musicplayer.core.data.LyricsRepository
import com.wxjxpp.musicplayer.core.data.PlaylistRepository
import com.wxjxpp.musicplayer.core.data.RoomDiaryRepository
import com.wxjxpp.musicplayer.core.data.RoomLyricsRepository
import com.wxjxpp.musicplayer.core.data.RoomPlaylistRepository
import com.wxjxpp.musicplayer.core.data.RoomSongRepository
import com.wxjxpp.musicplayer.core.data.RoomStatsRepository
import com.wxjxpp.musicplayer.core.data.SettingsRepository
import com.wxjxpp.musicplayer.core.data.SongRepository
import com.wxjxpp.musicplayer.core.data.StatsRepository
import com.wxjxpp.musicplayer.core.db.MusicDatabase
import com.wxjxpp.musicplayer.core.lyrics.LyricsLocator
import com.wxjxpp.musicplayer.core.lyrics.LyricsParserRegistry
import com.wxjxpp.musicplayer.core.lyrics.defaultLyricsParserRegistry
import com.wxjxpp.musicplayer.core.player.Media3PlayerController
import com.wxjxpp.musicplayer.core.player.PlayerController
import com.wxjxpp.musicplayer.core.scanner.AndroidMediaScanner
import com.wxjxpp.musicplayer.core.scanner.AndroidMetadataReader
import com.wxjxpp.musicplayer.core.scanner.MediaScanner
import com.wxjxpp.musicplayer.core.scanner.MetadataReader
import com.wxjxpp.musicplayer.core.source.DefaultMusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.LocalMusicSource
import com.wxjxpp.musicplayer.core.source.MusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.UserApiSource
import com.wxjxpp.musicplayer.core.together.NoopTogetherTransport
import com.wxjxpp.musicplayer.core.together.TogetherTransport
import com.wxjxpp.musicplayer.core.userapi.UserApiAction
import com.wxjxpp.musicplayer.core.userapi.UserApiEngine
import com.wxjxpp.musicplayer.core.userapi.UserApiHttpClient
import com.wxjxpp.musicplayer.core.userapi.UserApiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 依赖容器（手写 DI）。
 *
 * 这是整个应用唯一的装配点。要换实现只改这个文件；
 * 要迁移到 Hilt/Koin 也只需要替换这一层，业务代码不动。
 */
interface AppContainer {
    val appScope: CoroutineScope

    val songRepository: SongRepository
    val playlistRepository: PlaylistRepository
    val lyricsRepository: LyricsRepository
    val statsRepository: StatsRepository
    val diaryRepository: DiaryRepository
    val settingsRepository: SettingsRepository

    /** 需要访问随机/循环等扩展设置时用这个具体类型。 */
    val appSettings: DataStoreSettingsRepository

    val playerController: PlayerController
    val sourceRegistry: MusicSourceRegistry
    val lyricsParsers: LyricsParserRegistry
    val togetherTransport: TogetherTransport
    val mediaScanner: MediaScanner
    val metadataReader: MetadataReader

    val userApiStore: UserApiStore
    val userApiEngine: UserApiEngine

    /** 启用某个自定义音源脚本。 */
    fun activateUserApi(id: String)
}

class DefaultAppContainer(
    private val application: Application,
) : AppContainer {

    override val appScope = CoroutineScope(SupervisorJob())

    // === 数据库 ===
    private val database = Room.databaseBuilder(
        application,
        MusicDatabase::class.java,
        MusicDatabase.NAME,
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    // === 扫描与元数据 ===
    override val mediaScanner: MediaScanner = AndroidMediaScanner(application.contentResolver)
    override val metadataReader: MetadataReader = AndroidMetadataReader()

    // === 歌词 ===
    override val lyricsParsers: LyricsParserRegistry = defaultLyricsParserRegistry()
    private val lyricsLocator = LyricsLocator(
        resolver = application.contentResolver,
        parsers = lyricsParsers,
        metadataReader = metadataReader,
    )

    // === 数据层（Room + DataStore） ===
    override val songRepository: SongRepository =
        RoomSongRepository(database.songDao(), mediaScanner, metadataReader)
    override val playlistRepository: PlaylistRepository =
        RoomPlaylistRepository(database.playlistDao())
    override val lyricsRepository: LyricsRepository =
        RoomLyricsRepository(database.lyricsDao(), lyricsLocator)
    override val statsRepository: StatsRepository =
        RoomStatsRepository(database.playEventDao(), database.songDao())
    override val diaryRepository: DiaryRepository =
        RoomDiaryRepository(database.diaryDao())
    override val appSettings = DataStoreSettingsRepository(application)
    override val settingsRepository: SettingsRepository = appSettings

    // === 播放层 ===
    private val media3Controller = Media3PlayerController(application, appScope)
    override val playerController: PlayerController = media3Controller

    // === 自定义音源（QuickJS） ===
    override val userApiStore: UserApiStore = UserApiStore(application)
    override val userApiEngine: UserApiEngine = UserApiEngine(application)

    /** 脚本不能直接联网，统一由宿主代发请求。 */
    private val userApiHttpClient = UserApiHttpClient(userApiEngine, appScope)

    // === 音源注册表 ===
    private val registry = DefaultMusicSourceRegistry(
        listOf(LocalMusicSource(songRepository)),
    )
    override val sourceRegistry: MusicSourceRegistry = registry

    override val togetherTransport: TogetherTransport = NoopTogetherTransport()

    init {
        // 脚本发起的 HTTP 请求交给宿主执行
        appScope.launch {
            userApiEngine.actions.collect { action ->
                if (action is UserApiAction.Request) userApiHttpClient.handle(action)
            }
        }
        // 随机策略跟随设置
        appScope.launch {
            appSettings.observeShuffleMode().collect { media3Controller.setShuffleMode(it) }
        }
        // 启动时恢复上次启用的音源脚本
        appScope.launch {
            val activeId = appSettings.observeActiveUserApiId()
            activeId.collect { id -> if (id != null) activateUserApi(id) }
        }
    }

    override fun activateUserApi(id: String) {
        appScope.launch {
            val info = userApiStore.apis.value.firstOrNull { it.id == id } ?: return@launch
            val script = userApiStore.readScript(id) ?: return@launch
            userApiEngine.loadScript(info, script)
            registry.register(UserApiSource(userApiEngine, info))
            appSettings.setActiveUserApiId(id)
        }
    }

    fun shutdown() {
        playerController.release()
        userApiEngine.destroy()
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