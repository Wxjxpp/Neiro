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
import com.wxjxpp.musicplayer.core.lyrics.EmbeddedLyricsReader
import com.wxjxpp.musicplayer.core.lyrics.LyricsLocator
import com.wxjxpp.musicplayer.core.lyrics.LyricsParserRegistry
import com.wxjxpp.musicplayer.core.lyrics.defaultLyricsParserRegistry
import com.wxjxpp.musicplayer.core.model.Quality
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.net.HttpClient
import com.wxjxpp.musicplayer.core.player.Media3PlayerController
import com.wxjxpp.musicplayer.core.player.PlayerController
import com.wxjxpp.musicplayer.core.scanner.AndroidMediaScanner
import com.wxjxpp.musicplayer.core.scanner.AndroidMetadataReader
import com.wxjxpp.musicplayer.core.scanner.MediaScanner
import com.wxjxpp.musicplayer.core.scanner.MetadataReader
import com.wxjxpp.musicplayer.core.search.OnlineSearchRepository
import com.wxjxpp.musicplayer.core.source.DefaultMusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.LocalMusicSource
import com.wxjxpp.musicplayer.core.source.MusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.OnlineMusicSource
import com.wxjxpp.musicplayer.core.source.online.defaultOnlinePlatforms
import com.wxjxpp.musicplayer.core.together.NoopTogetherTransport
import com.wxjxpp.musicplayer.core.together.TogetherTransport
import com.wxjxpp.musicplayer.core.userapi.UserApiAction
import com.wxjxpp.musicplayer.core.userapi.UserApiClient
import com.wxjxpp.musicplayer.core.userapi.UserApiEngine
import com.wxjxpp.musicplayer.core.userapi.UserApiHttpClient
import com.wxjxpp.musicplayer.core.userapi.UserApiStatus
import com.wxjxpp.musicplayer.core.userapi.UserApiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** 在线聚合搜索。 */
    val onlineSearch: OnlineSearchRepository

    /** 面向用户的一次性提示（音源导入失败、取流失败等）。 */
    val messages: SharedFlow<String>

    /** 启用某个自定义音源脚本。 */
    fun activateUserApi(id: String)

    /** 停用当前脚本。 */
    fun deactivateUserApi()

    /** 发一条提示。 */
    fun notify(message: String)
}

class DefaultAppContainer(
    private val application: Application,
) : AppContainer {

    override val appScope = CoroutineScope(SupervisorJob())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    override fun notify(message: String) {
        _messages.tryEmit(message)
    }

    // === 网络 ===
    private val httpClient = HttpClient()

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
    private val embeddedLyricsReader = EmbeddedLyricsReader(application.contentResolver)

    /**
     * 歌词查找器需要音源注册表来兜底在线歌词，而注册表的构造又依赖曲库仓库，
     * 曲库仓库又依赖歌词查找器。用 lambda 延迟取值打破这个环。
     */
    private val lyricsLocator = LyricsLocator(
        resolver = application.contentResolver,
        parsers = lyricsParsers,
        embeddedReader = embeddedLyricsReader,
        sourceRegistry = { registry },
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
    override val userApiStore: UserApiStore = UserApiStore(application, httpClient)
    override val userApiEngine: UserApiEngine = UserApiEngine(application)
    private val userApiClient = UserApiClient(userApiEngine)

    /** 脚本不能直接联网，统一由宿主代发请求。 */
    private val userApiHttpClient = UserApiHttpClient(userApiEngine, appScope, httpClient)

    /** 当前启用脚本的能力表，供在线音源判断能否取流。 */
    @Volatile
    private var activeCapabilities: Map<String, List<String>> = emptyMap()

    /** 正在启用的脚本 id，用于把 init 结果落回存储。 */
    @Volatile
    private var activatingId: String? = null

    // === 音源注册表 ===
    private val onlineSources = defaultOnlinePlatforms(httpClient).map { platform ->
        OnlineMusicSource(
            platform = platform,
            userApiClient = userApiClient,
            supportedActions = { activeCapabilities },
        )
    }

    private val registry = DefaultMusicSourceRegistry(
        listOf(LocalMusicSource(songRepository)) + onlineSources,
    )
    override val sourceRegistry: MusicSourceRegistry = registry

    override val onlineSearch = OnlineSearchRepository(onlineSources)

    override val togetherTransport: TogetherTransport = NoopTogetherTransport()

    init {
        // 在线歌曲取流：交给对应平台的音源
        media3Controller.remoteUrlResolver = { song -> resolveRemoteUrl(song) }
        media3Controller.onPlaybackError = { message -> notify(message) }

        // 脚本发起的 HTTP 请求交给宿主执行
        appScope.launch {
            userApiEngine.actions.collect { action -> onUserApiAction(action) }
        }
        // 随机策略跟随设置
        appScope.launch {
            appSettings.observeShuffleMode().collect { media3Controller.setShuffleMode(it) }
        }
        // 启动时恢复上次启用的音源脚本
        appScope.launch {
            appSettings.observeActiveUserApiId().collect { id ->
                if (id != null && id != activatingId && userApiEngine.status !is UserApiStatus.Ready) {
                    activateUserApi(id)
                }
            }
        }
    }

    /** 按歌曲所属平台解析播放地址。 */
    private suspend fun resolveRemoteUrl(song: Song): Media3PlayerController.RemoteUrl {
        val location = song.location as? com.wxjxpp.musicplayer.core.model.MediaLocation.Remote
            ?: return Media3PlayerController.RemoteUrl.Failure("这不是在线歌曲")
        val source = registry.find(location.sourceId) as? OnlineMusicSource
            ?: return Media3PlayerController.RemoteUrl.Failure("找不到音源：${location.sourceId}")
        val quality = appSettings.currentQuality()
        return when (val result = source.resolvePlayUrlDetailed(song, quality)) {
            is OnlineMusicSource.PlayUrlResult.Success ->
                Media3PlayerController.RemoteUrl.Success(result.url)

            is OnlineMusicSource.PlayUrlResult.Failure ->
                Media3PlayerController.RemoteUrl.Failure(result.reason)
        }
    }

    private fun onUserApiAction(action: UserApiAction) {
        when (action) {
            is UserApiAction.Request -> userApiHttpClient.handle(action)

            is UserApiAction.CancelRequest -> userApiHttpClient.cancel(action.requestKey)

            is UserApiAction.Init -> {
                val id = activatingId
                if (action.status) {
                    activeCapabilities = action.supportedActions
                    if (id != null) {
                        userApiStore.updateCapabilities(
                            id = id,
                            actions = action.supportedActions,
                            qualities = action.supportedQualities,
                        )
                        userApiStore.apis.value.firstOrNull { it.id == id }
                            ?.let { userApiEngine.markReady(it) }
                        appScope.launch { appSettings.setActiveUserApiId(id) }
                        notify("音源已启用：支持 ${action.supportedActions.keys.joinToString("、")}")
                    }
                } else {
                    activeCapabilities = emptyMap()
                    notify(action.errorMessage ?: "音源初始化失败")
                }
            }

            is UserApiAction.ShowUpdateAlert ->
                notify("「${action.name}」有更新：${action.log.take(120)}")

            is UserApiAction.Log -> if (action.level == "error") notify("音源脚本错误：${action.message.take(120)}")

            else -> Unit
        }
    }

    override fun activateUserApi(id: String) {
        appScope.launch {
            val info = userApiStore.apis.value.firstOrNull { it.id == id }
            if (info == null) {
                notify("音源不存在，可能已被删除")
                return@launch
            }
            val script = userApiStore.readScript(id)
            if (script.isNullOrBlank()) {
                notify("音源脚本文件丢失，请重新导入")
                return@launch
            }
            activatingId = id
            activeCapabilities = emptyMap()
            userApiEngine.loadScript(info, script)
        }
    }

    override fun deactivateUserApi() {
        activatingId = null
        activeCapabilities = emptyMap()
        userApiEngine.destroy()
        appScope.launch { appSettings.setActiveUserApiId(null) }
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