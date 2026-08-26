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
import com.wxjxpp.neiro.core.together.LitTogetherTransport
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
    /** 测试音源握手（音源页「测试握手」按钮）：返回结果描述。 */
    suspend fun testUserApiHandshake(api: com.wxjxpp.neiro.core.userapi.UserApiInfo): String
    val mediaScanner: MediaScanner
    val metadataReader: MetadataReader

    val userApiStore: UserApiStore
    val userApiEngine: UserApiEngine
    /** 在线聚合搜索。 */
    val onlineSearch: OnlineSearchRepository
    /** 用本机音源脚本解析在线歌曲的播放直链（一起听点歌用）。失败返回 Failure。 */
    suspend fun resolveRemoteUrl(song: Song): com.wxjxpp.neiro.core.player.Media3PlayerController.RemoteUrl

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
    /** 成功提示（绿色横幅）。 */
    fun notifySuccess(message: String)
    /** 中性信息提示（横幅，无着色强调）。 */
    fun notifyInfo(message: String)
    /** 成功/信息横幅队列（顶部堆叠展示）。 */
    val banners: kotlinx.coroutines.flow.StateFlow<List<Banner>>
    /** UI 关闭横幅后从队列移除。 */
    fun dismissBanner(id: Long)
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

/** 应用内横幅：id 用于堆叠去重/出队，type 决定配色。 */
data class Banner(
    val id: Long,
    val type: String,
    val message: String,
)

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
    /** 横幅队列：成功/信息类提示堆叠展示，最新在前，最多保留 4 条。 */
    private val _banners = kotlinx.coroutines.flow.MutableStateFlow<List<Banner>>(emptyList())
    override val banners: kotlinx.coroutines.flow.StateFlow<List<Banner>> = _banners
    private var bannerSeq = 0L

    private fun pushBanner(type: String, message: String) {
        val entry = Banner(id = ++bannerSeq, type = type, message = message)
        _banners.value = (listOf(entry) + _banners.value).take(4)
        // 超过 5 条兜底清理（正常由 UI 关闭回调移除）
        appScope.launch {
            kotlinx.coroutines.delay(30_000L)
            _banners.value = _banners.value.filter { it.id != entry.id }
        }
    }

    override fun notifySuccess(message: String) = pushBanner("success", message)
    override fun notifyInfo(message: String) = pushBanner("info", message)
    /** UI 关闭横幅后从队列移除。 */
    override fun dismissBanner(id: Long) {
        _banners.value = _banners.value.filter { it.id != id }
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
        com.wxjxpp.neiro.core.download.DownloadManager(
            application,
            registry,
            appSettings,
            httpClient,
        )

    /** 按脚本启用状态重建外置音源集合，并同步进注册表与搜索页。 */
    private fun refreshOnlineSources(enabled: Boolean) {
        val target = if (enabled) lxSources else emptyList()
        // 先移除旧的外置源再注册新的，避免残留
        registry.sources.filterIsInstance<OnlineMusicSource>().forEach { registry.unregister(it.id) }
        target.forEach { registry.register(it) }
        activeOnlineSources = target
    }

    override val togetherTransport: TogetherTransport = LitTogetherTransport(appSettings, httpClient, appScope)
    /** 发现页：榜单直连网易云公开接口；猜你喜欢复用聚合搜索。 */
    override val discoverRepository: com.wxjxpp.neiro.core.discover.DiscoverRepository by lazy {
        com.wxjxpp.neiro.core.discover.DiscoverRepository(
            http = httpClient,
            // 用户启用了 LX 音源后，发现页榜单歌曲动态挂到对应音源（如 "wy-lx"），可直接播放
            sourceIdProvider = {
                activeOnlineSources.firstOrNull()?.id
                    ?.takeIf { id -> activeOnlineSources.any { it.id == id } }
                    ?: registry.sources.filterIsInstance<com.wxjxpp.neiro.core.source.online.LxSourcePlatform>()
                        .firstOrNull()?.id
            },
        )
    }
    init {
        // 记录一次应用启动（听歌热力图的"启动次数"维度）
        appScope.launch { runCatching { statsRepository.recordAppLaunch() } }
        // 在线歌曲取流：交给对应平台的音源
        media3Controller.remoteUrlResolver = { song -> resolveRemoteUrl(song) }
        // 取流/播放错误改为顶部横幅（可关闭），不再用底部 Snackbar
        media3Controller.onPlaybackError = { message -> showError(message) }

        // ---- 一起听桥接：会话恢复 + 房主播放上报 ----
        runCatching { togetherTransport as com.wxjxpp.neiro.core.together.LitTogetherTransport }.getOrNull()?.let { lit ->
            appScope.launch { runCatching { lit.restoreSession() } }
            var lastErrorReportedKey = ""
            appScope.launch {
                media3Controller.state.collect { st ->
                    if (!lit.isControllerInRoom) return@collect
                    val song = st.current ?: return@collect
                    // 组装服务端 track JSON；本地文件与 WebDAV 禁止入房
                    val track = when (val loc = song.location) {
                        is com.wxjxpp.neiro.core.model.MediaLocation.Remote -> org.json.JSONObject()
                            .put("sourceId", loc.sourceId)
                            .put("songId", loc.songId)
                            .put("title", song.title)
                            .put("artist", song.artistName)
                            .put("album", song.albumTitle)
                            .put("durationMs", song.durationMs)
                            .put("cover", song.coverUri.orEmpty())
                            .put("payload", loc.payload.orEmpty())
                        is com.wxjxpp.neiro.core.model.MediaLocation.Local -> {
                            if (!loc.uri.startsWith("http")) return@collect
                            org.json.JSONObject()
                                .put("sourceId", "url")
                                .put("songId", com.wxjxpp.neiro.core.together.LitTogetherTransport.urlHash(loc.uri))
                                .put("url", loc.uri)
                                .put("title", song.title)
                                .put("artist", song.artistName)
                                .put("durationMs", song.durationMs)
                                .put("cover", song.coverUri.orEmpty())
                        }
                        else -> return@collect
                    }
                    val key = "${track.optString("sourceId")}:${track.optString("songId")}"
                    // 远端切歌后的静默窗口：跟随逻辑正在切本机播放器，此时不上报防回环
                    if (System.currentTimeMillis() - lit.lastRemoteSwitchAt < 5000) return@collect
                    // URL 加载卡死兜底：缓冲超过 5s 或进度停滞且未在播 → 上报无效源自动切歌
                    val stuck = st.isBuffering && st.positionMs <= 0L &&
                        key == (lit.currentTrackJson.value?.optString("stableKey").orEmpty())
                    if (stuck && key != lastErrorReportedKey) {
                        lastErrorReportedKey = key
                        runCatching { lit.reportTrackError() }
                        return@collect
                    }
                    // 与服务器权威态对比，仅在真正不一致时上报（防止远端触发变更被回推形成回环）
                    val pb = lit.roomStateJson.value?.optJSONObject("playback")
                    val roomKey = pb?.optJSONObject("track")?.optString("stableKey").orEmpty()
                    val roomPlaying = pb?.optBoolean("playing", false) ?: false
                    if (roomKey != key) {
                        runCatching { lit.publishCurrentTrack(track, st.positionMs, st.isPlaying) }
                    } else if (roomPlaying != st.isPlaying) {
                        runCatching { lit.publishPlayback(st.positionMs, st.isPlaying) }
                    }
                }
            }
        }

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
                if (id != null && id != activatingId && userApiEngine.status.value !is UserApiStatus.Ready) {
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
     * 在线歌曲取流：**仅使用歌曲自身平台的脚本**，绝不跨平台自动换源。
     *
     * （历史行为：当前平台失败后轮询其他 LX 平台搜同名歌——LX 脚本缺 hash 时
     * 会按歌名模糊匹配，经常返回同名不同曲的直链，导致「封面 A 放出歌 B」。
     * 已按产品要求移除。）
     *
     * 音质回退保留：同一首歌按设置方向逐级降/升音质重试，不会改变曲目本身。
     */
override suspend fun resolveRemoteUrl(song: Song): Media3PlayerController.RemoteUrl {
        val location = song.location as? com.wxjxpp.neiro.core.model.MediaLocation.Remote
            ?: return Media3PlayerController.RemoteUrl.Failure("这不是在线歌曲")
        if (activeOnlineSources.isEmpty()) {
            return Media3PlayerController.RemoteUrl.Failure("没有可用的外置音源（请先在「自定义音源」启用脚本）")
        }
        // 脚本引擎可能仍在初始化（冷启动自动恢复 / 刚点启用）：等就绪再取流，
        // 避免请求打进未完成 init 的脚本（表现为档位失败但脚本日志无任何后端尝试）
        if (userApiEngine.status.value is com.wxjxpp.neiro.core.userapi.UserApiStatus.Initializing) {
            kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                userApiEngine.status.first { it !is com.wxjxpp.neiro.core.userapi.UserApiStatus.Initializing }
            }
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
        // 只用歌曲自身平台的脚本取流；绝不跨平台搜同名歌（防串歌）
        val orderedSources = listOfNotNull(registry.find(location.sourceId) as? OnlineMusicSource)
        val failures = mutableListOf<String>()
        for (quality in qualities) {
            for (source in orderedSources) {
                val actions = activeCapabilities[source.scriptPlatformId] ?: continue
                if ("musicUrl" !in actions) {
                    failures += "[$quality] 音源脚本不支持${source.displayName}，请检查「自定义音源」"
                    continue
                }
                when (val first = source.resolvePlayUrlDetailed(song, quality)) {
                    is OnlineMusicSource.PlayUrlResult.Success ->
                        return Media3PlayerController.RemoteUrl.Success(first.url)
                    is OnlineMusicSource.PlayUrlResult.Failure -> {
                        // 聚合脚本偶发返回无明细的裸 failed（请求到达时后端列表还没装配完）：
                        // 稍候重试一次；带明细的真实失败不重试（避免双倍等待）
                        val final = if (first.reason.trim().equals("failed", ignoreCase = true)) {
                            kotlinx.coroutines.delay(1_500)
                            source.resolvePlayUrlDetailed(song, quality)
                        } else {
                            first
                        }
                        when (final) {
                            is OnlineMusicSource.PlayUrlResult.Success ->
                                return Media3PlayerController.RemoteUrl.Success(final.url)
                            is OnlineMusicSource.PlayUrlResult.Failure ->
                                failures += "[$quality] ${(final.reason.ifBlank { first.reason }).take(80)}"
                        }
                    }
                }
            }
        }
        if (orderedSources.isEmpty()) {
            return Media3PlayerController.RemoteUrl.Failure(
                "没有找到歌曲所属平台（${location.sourceId}）的音源"
            )
        }
        return Media3PlayerController.RemoteUrl.Failure(
            "「${song.title}」各音质档位均取流失败：\n" + failures.takeLast(4).joinToString("\n")
        )
    }

    /** 测试音源握手：校验脚本引擎已成功加载该音源并完成能力上报（QuickJS 执行通过）。
 *  注意：不发真实业务请求——聚合源对探测性假数据（songId=0）必然返回业务失败，
 *  那不代表「握手失败」。引擎 Ready + 能力表非空即代表脚本链路健康。 */
    override suspend fun testUserApiHandshake(api: com.wxjxpp.neiro.core.userapi.UserApiInfo): String {
        val started = System.currentTimeMillis()
        // 注意：status 是 StateFlow，必须取 .value 才是状态本体；
        // 直接对 StateFlow 做 is 判断恒为 false（上一版恒报「未启用」的根源）
        var status = userApiEngine.status.value
        // 引擎正在初始化（或刚点启用还没进入 Initializing）时等待收敛，最多 15s
        fun stillPending(): Boolean =
            status is com.wxjxpp.neiro.core.userapi.UserApiStatus.Initializing ||
                (status is com.wxjxpp.neiro.core.userapi.UserApiStatus.Idle && activatingId != null)
        if (stillPending()) {
            val deadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(200)
                status = userApiEngine.status.value
                if (!stillPending()) break
            }
        }
        val st = status
        val ready = st is com.wxjxpp.neiro.core.userapi.UserApiStatus.Ready && st.info.id == api.id
        val elapsed = System.currentTimeMillis() - started
        return when {
            ready -> {
                val platforms = api.platforms
                val actions = activeCapabilities.entries.take(3)
                    .joinToString { (k, v) -> "$k:${v.joinToString("/")}" }
                buildString {
                    append("握手成功 · ${api.name} · 引擎已加载并完成能力上报")
                    if (platforms.isNotEmpty()) append(" · 平台 ${platforms.joinToString("、")}")
                    if (actions.isNotEmpty()) append(" · 能力 {$actions}")
                    append("（${elapsed}ms）")
                }
            }
            st is com.wxjxpp.neiro.core.userapi.UserApiStatus.Failed && st.id == api.id ->
                "握手失败 · ${api.name} · ${st.message.take(120)}"
            st is com.wxjxpp.neiro.core.userapi.UserApiStatus.Initializing ->
                "握手失败 · ${api.name} · 脚本仍在初始化，请稍后再试"
            else -> {
                val activeName = (userApiEngine.status.value as? com.wxjxpp.neiro.core.userapi.UserApiStatus.Ready)
                    ?.info?.name
                if (activeName != null && activeName != api.name) {
                    "握手失败 · ${api.name} · 当前启用的是「$activeName」，请先切换到该音源再测试"
                } else {
                    "握手失败 · ${api.name} · 脚本未启用或引擎未加载（请先点 ▶ 启用）"
                }
            }
        }
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