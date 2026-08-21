package com.wxjxpp.musicplayer.core.source

import com.wxjxpp.musicplayer.core.model.Album
import com.wxjxpp.musicplayer.core.model.Artist
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.Quality
import com.wxjxpp.musicplayer.core.model.Song

/**
 * 音源插件契约。
 *
 * 每一种"歌从哪来"都实现这个接口：
 * 本地扫描、WebDAV、网易/QQ 等 API 源、自建后端……
 *
 * 关键点：
 * - [capabilities] 声明自己支持什么，UI 据此显示/隐藏入口，
 *   而不是在页面里写 `if (source == "netease")` 这种硬编码判断
 * - 不支持的能力直接返回空结果，不要抛异常
 *
 * 新增一个音源 = 新增一个实现类 + 在 [MusicSourceRegistry] 注册，
 * 不需要改任何 UI 代码。
 */
interface MusicSource {

    val id: String
    val displayName: String
    val capabilities: Set<SourceCapability>

    /** 关键字搜索。不支持时返回空。 */
    suspend fun search(query: String, page: Int = 1, pageSize: Int = 30): List<Song> = emptyList()

    /**
     * 解析真实可播放地址。
     *
     * 本地源直接回 file uri；在线源在这里做鉴权、换链、防盗链处理。
     * 返回 null 表示该音质不可用，调用方会降级重试。
     */
    suspend fun resolvePlayUrl(song: Song, quality: Quality): String?

    /** 拉取歌词原文（尚未解析）。返回 null 表示该源没有歌词。 */
    suspend fun fetchLyricsRaw(song: Song): RawLyrics? = null

    /** 已解析好的歌词。默认走 [fetchLyricsRaw] + 解析器，一般不用覆写。 */
    suspend fun fetchLyrics(song: Song): Lyrics? = null

    suspend fun songsOfAlbum(album: Album): List<Song> = emptyList()

    suspend fun songsOfArtist(artist: Artist): List<Song> = emptyList()
}

/** 音源能力声明。UI 根据这个集合决定展示哪些功能入口。 */
enum class SourceCapability {
    Search,
    Lyrics,
    Album,
    Artist,
    Playlist,
    /** 支持按音质选择（无损、Hi-Res 等）。 */
    QualitySelection,
    /** 支持下载到本地。 */
    Download,
    /** 内容来自本地存储，不需要网络。 */
    Offline,
}

/**
 * 未解析的歌词文本 + 声明的格式。
 * 交给 LyricsParser 转成 [Lyrics]。
 */
data class RawLyrics(
    val content: String,
    val declaredFormat: String? = null,
    /** 部分 API 把翻译单独给一份，这里一并带过来。 */
    val translationContent: String? = null,
    val romanizationContent: String? = null,
    /** 逐字歌词（LX 协议的 `lxlyric`），有则优先作为主歌词。 */
    val wordByWordContent: String? = null,
)

/**
 * 音源注册表。
 *
 * 应用启动时注册所有可用音源，业务层按 id 取用。
 * 之所以不用 `when(sourceId)` 硬编码，就是为了让新增音源零侵入。
 */
interface MusicSourceRegistry {
    val sources: List<MusicSource>
    fun find(id: String): MusicSource?
    fun sourcesWith(capability: SourceCapability): List<MusicSource>
}

class DefaultMusicSourceRegistry(
    initial: List<MusicSource> = emptyList(),
) : MusicSourceRegistry {

    private val registry = LinkedHashMap<String, MusicSource>()

    init {
        initial.forEach { register(it) }
    }

    fun register(source: MusicSource) {
        registry[source.id] = source
    }

    fun unregister(id: String) {
        registry.remove(id)
    }

    override val sources: List<MusicSource> get() = registry.values.toList()

    override fun find(id: String): MusicSource? = registry[id]

    override fun sourcesWith(capability: SourceCapability): List<MusicSource> =
        registry.values.filter { capability in it.capabilities }
}