package com.wxjxpp.neiro.app

import android.app.Application
import androidx.room.Room
import com.wxjxpp.neiro.core.data.DataStoreSettingsRepository
import com.wxjxpp.neiro.core.data.DiaryRepository
import com.wxjxpp.neiro.core.data.LyricsRepository
import com.wxjxpp.neiro.core.data.PlaylistRepository
import com.wxjxpp.neiro.core.data.QualityFallbackDirection
import com.wxjxpp.neiro.core.data.RoomDiaryRepository
import com.wxjxpp.neiro.core.data.RoomLyricsRepository
import com.wxjxpp.neiro.core.data.RoomPlaylistRepository
import com.wxjxpp.neiro.core.data.RoomSongRepository
import com.wxjxpp.neiro.core.data.RoomStatsRepository
import com.wxjxpp.neiro.core.data.SettingsRepository
import com.wxjxpp.neiro.core.data.SongRepository
import com.wxjxpp.neiro.core.data.StatsRepository
import com.wxjxpp.neiro.core.db.MusicDatabase
import com.wxjxpp.neiro.core.lyrics.EmbeddedLyricsReader
import com.wxjxpp.neiro.core.lyrics.LyricsLocator
import com.wxjxpp.neiro.core.lyrics.LyricsParserRegistry
import com.wxjxpp.neiro.core.lyrics.defaultLyricsParserRegistry
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.HttpClient
import com.wxjxpp.neiro.core.player.Media3PlayerController
import com.wxjxpp.neiro.core.player.PlayerController
import com.wxjxpp.neiro.core.scanner.AndroidMediaScanner
import com.wxjxpp.neiro.core.scanner.AndroidMetadataReader
import com.wxjxpp.neiro.core.scanner.MediaScanner
import com.wxjxpp.neiro.core.scanner.MetadataReader
import com.wxjxpp.neiro.core.search.OnlineSearchRepository
import com.wxjxpp.neiro.core.source.DefaultMusicSourceRegistry
import com.wxjxpp.neiro.core.source.LocalMusicSource
import com.wxjxpp.neiro.core.source.MusicSourceRegistry
import com.wxjxpp.neiro.core.source.OnlineMusicSource
import com.wxjxpp.neiro.core.source.online.defaultOnlinePlatforms
import com.wxjxpp.neiro.core.source.online.lxSourceOf
import com.wxjxpp.neiro.core.together.NoopTogetherTransport
import com.wxjxpp.neiro.core.together.TogetherTransport
import com.wxjxpp.neiro.core.userapi.UserApiAction
import com.wxjxpp.neiro.core.userapi.UserApiClient
import com.wxjxpp.neiro.core.userapi.UserApiEngine
import com.wxjxpp.neiro.core.userapi.UserApiHttpClient
import com.wxjxpp.neiro.core.userapi.UserApiStatus
import com.wxjxpp.neiro.core.userapi.UserApiStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 依赖容器（手写 DI）。
 *
 * 这是整个应用唯一的装配点。要换实现只改这个文件；
 * 要迁移到 Hilt/Koin 也只需要替换这一层，业务代码不动。
 */
interface AppContainer {
    val appScope: CoroutineScope

    /** 应用上下文（需要 ContentResolver 等系统能力时用）。 */
    val appContext: android.content.Context

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

    /** 在线歌曲/歌词下载。 */
    val downloadManager: com.wxjxpp.neiro.core.download.DownloadManager

    /** 面向用户的一次性提示（音源导入失败、取流失败等）。 */
    val messages: SharedFlow<String>
    /** 发现页数据仓库（榜单 / 猜你喜欢）。 */
    val discoverRepository: com.wxjxpp.neiro.core.discover.DiscoverRepository
    /** 启用某个自定义音源脚本。 */
    fun activateUserApi(id: String)

    /** 停用当前脚本。 */
    fun deactivateUserApi()

    /** 发一条提示。 */
    fun notify(message: String)
    /** 顶部错误横幅（可关闭，替代 Snackbar 展示取流失败等错误）。 */
    val errorBanner: kotlinx.coroutines.flow.MutableStateFlow<String?>
    fun showError(message: String) {
        errorBanner.value = message
        // 防止横幅永久残留：15 秒后自动清除（用户提前关掉则无副作用）
        appScope.launch {
            kotlinx.coroutines.delay(15_000L)
            if (errorBanner.value == message) errorBanner.value = null
        }
    }
}

class DefaultAppContainer(
    private val application: Application,
) : AppContainer {

    override val appContext: android.content.Context get() = application

    override val appScope = CoroutineScope(SupervisorJob())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()
    /** 顶部错误横幅状态（null = 隐藏）。 */
    override val errorBanner = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
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
    override val metadataReader: MetadataReader = AndroidMetadataReader()
    override val mediaScanner: MediaScanner = AndroidMediaScanner(application.contentResolver, metadataReader)

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
        RoomStatsRepository(database.playEventDao(), database.songDao(), database.appLaunchDao())
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

    /** 网易云 Cookie 缓存（设置变化时刷新）。 */
    @Volatile
    private var neteaseCookie: String = ""

    // === 音源注册表 ===
    // 仅外置 LX 音源：每个内置平台派生一个 "xxx-lx" 源，取流完全交给脚本。
    // 内置官方取流通道已移除（合规要求），未启用脚本时在线结果不可播并给出明确提示。
    private val lxSources: List<OnlineMusicSource> = defaultOnlinePlatforms(httpClient) { neteaseCookie }
        .mapNotNull { platform ->
            lxSourceOf(platform, userApiClient)?.let { lxPlatform ->
                OnlineMusicSource(
                    platform = lxPlatform,
                    userApiClient = userApiClient,
                    supportedActions = { activeCapabilities },
                    streamResolver = { song, quality -> lxPlatform.streamUrl(song, quality) },
                )
            }
        }
    /** 当前可用的外置音源集合（随脚本启用状态变化）。 */
    @Volatile
    private var activeOnlineSources: List<OnlineMusicSource> = emptyList()
private val registry = DefaultMusicSourceRegistry(
        listOf(LocalMusicSource(songRepository)),
    )
    override val sourceRegistry: MusicSourceRegistry = registry
    override val onlineSearch = OnlineSearchRepository(sourcesProvider = { activeOnlineSources })

    override val downloadManager =
        com.wxjxpp.neiro.core.download.DownloadManager(application, registry)

    /** 按脚本启用状态重建外置音源集合，并同步进注册表与搜索页。 */
    private fun refreshOnlineSources(enabled: Boolean) {
        val target = if (enabled) lxSources else emptyList()
        // 先移除旧的外置源再注册新的，避免残留
        registry.sources.filterIsInstance<OnlineMusicSource>().forEach { registry.unregister(it.id) }
        target.forEach { registry.register(it) }
        activeOnlineSources = target
    }

    override val togetherTransport: TogetherTransport = NoopTogetherTransport()
    /** 发现页：榜单直连网易云公开接口；猜你喜欢复用聚合搜索。 */
    override val discoverRepository: com.wxjxpp.neiro.core.discover.DiscoverRepository by lazy {
        com.wxjxpp.neiro.core.discover.DiscoverRepository(http = httpClient)
    }
    init {
        // 记录一次应用启动（听歌热力图的"启动次数"维度）
        appScope.launch { runCatching { statsRepository.recordAppLaunch() } }
        // 在线歌曲取流：交给对应平台的音源
        media3Controller.remoteUrlResolver = { song -> resolveRemoteUrl(song) }
        // 取流/播放错误改为顶部横幅（可关闭），不再用底部 Snackbar
        media3Controller.onPlaybackError = { message -> showError(message) }

        // 脚本发起的 HTTP 请求交给宿主执行
        appScope.launch {
            userApiEngine.actions.collect { action -> onUserApiAction(action) }
        }
        // 随机策略跟随设置
        appScope.launch {
            appSettings.observeShuffleMode().collect { media3Controller.setShuffleMode(it) }
        }
        // 启动时恢复上次启用的音源脚本；引擎状态变化时同步外置源集合
        appScope.launch {
            appSettings.observeActiveUserApiId().collect { id ->
                if (id != null && id != activatingId && userApiEngine.status !is UserApiStatus.Ready) {
                    activateUserApi(id)
                }
                if (id == null) refreshOnlineSources(enabled = false)
            }
        }
        appScope.launch {
            userApiEngine.status.collect { status ->
                when (status) {
                    is UserApiStatus.Ready -> refreshOnlineSources(enabled = true)
                    is UserApiStatus.Idle, is UserApiStatus.Failed -> refreshOnlineSources(enabled = false)
                    is UserApiStatus.Initializing -> Unit
                }
            }
        }
        // 网易云 Cookie 跟随设置
        appScope.launch {
            appSettings.observeNeteaseCookie().collect { neteaseCookie = it }
        }
    }

    /** 按歌曲所属平台解析播放地址。 */
    /** 按用户设置的方向取相邻音质档位；已到边界返回 null。 */
    private fun neighborQuality(q: Quality, direction: QualityFallbackDirection): Quality? {
        val ladder = Quality.entries
        val idx = ladder.indexOf(q)
        val next = when (direction) {
            QualityFallbackDirection.LOWER -> idx - 1
            QualityFallbackDirection.HIGHER -> idx + 1
        }
        return ladder.getOrNull(next)
    }

    /**
     * 在线歌曲取流，带两级自动回退：
     * 1. 换源：当前平台失败 → 轮询其他 LX 平台（wy→kw→kg→tx→mg）找可用直链
     * 2. 音质：全部平台失败 → 按设置方向调整音质重试一轮
     *
     * 换源成功的歌会临时改挂到实际取流的源上播放（不改曲库元数据）。
     */
    private suspend fun resolveRemoteUrl(song: Song): Media3PlayerController.RemoteUrl {
        val location = song.location as? com.wxjxpp.neiro.core.model.MediaLocation.Remote
            ?: return Media3PlayerController.RemoteUrl.Failure("这不是在线歌曲")
        if (activeOnlineSources.isEmpty()) {
            return Media3PlayerController.RemoteUrl.Failure("没有可用的外置音源（请先在「自定义音源」启用脚本）")
        }
        val baseQuality = appSettings.currentQuality()
        val fallbackDir = appSettings.observeQualityFallbackDirection().first()

        // 音质阶梯：首选 + 回退方向逐级
        val qualities = buildList {
            add(baseQuality)
            var q = baseQuality
            while (true) {
                q = neighborQuality(q, fallbackDir) ?: break
                add(q)
            }
        }
        // 平台顺序：当前源优先，其余按注册表顺序
        val orderedSources = listOfNotNull(registry.find(location.sourceId) as? OnlineMusicSource) +
            activeOnlineSources.filter { it.id != location.sourceId }

        val failures = mutableListOf<String>()
        for (quality in qualities) {
            for (source in orderedSources) {
                // 只试脚本声明支持的源；换源时用目标源的 id 构造请求
                val actions = activeCapabilities[source.scriptPlatformId] ?: continue
                if ("musicUrl" !in actions) continue
                val targetSong = if (source.id == location.sourceId) song else song.copy(
                    id = "${source.id}:${location.songId}",
                    location = location.copy(sourceId = source.id),
                )
                val label = if (source.id == location.sourceId) "" else "（换源 ${source.displayName}）"
                when (val r = source.resolvePlayUrlDetailed(targetSong, quality)) {
                    is OnlineMusicSource.PlayUrlResult.Success ->
                        return Media3PlayerController.RemoteUrl.Success(r.url)
                    is OnlineMusicSource.PlayUrlResult.Failure ->
                        failures += "[$quality$label] ${r.reason.take(80)}"
                }
            }
        }
        return Media3PlayerController.RemoteUrl.Failure(
            "「${song.title}」所有音质/平台均失败：\n" + failures.takeLast(4).joinToString("\n")
        )
    }

    private fun onUserApiAction(action: UserApiAction) {
        when (action) {
            is UserApiAction.Request -> userApiHttpClient.handle(action)

            
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