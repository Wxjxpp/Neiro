package com.wxjxpp.musicplayer.core.data

import androidx.compose.ui.graphics.Color
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.delay

/**
 * 数据仓库抽象。后续换成网络/数据库实现即可。
 */
interface SongRepository {
    suspend fun loadSongs(): List<Song>
}

class FakeSongRepository : SongRepository {

    override suspend fun loadSongs(): List<Song> {
        // 模拟网络耗时，便于观察下拉刷新动画
        delay(900)
        return SampleSongs
    }
}

private val SampleSongs = listOf(
    Song("1", "Weightless", "Marconi Union", "Ambient Works", 8 * 60_000, Color(0xFF6750A4)),
    Song("2", "Night Owl", "Broke For Free", "Directionless EP", 3 * 60_000, Color(0xFF00696D)),
    Song("3", "Sunset Drive", "Kaisar", "Neon City", 4 * 60_000, Color(0xFF8E4B10)),
    Song("4", "Paper Plane", "Lumine", "Skyline", 3 * 60_000 + 20_000, Color(0xFF4A5C92)),
    Song("5", "Slow Motion", "Rhodes Trip", "Analog Days", 5 * 60_000, Color(0xFF7D5260)),
    Song("6", "Aurora", "Silver Lake", "Northern Lights", 6 * 60_000, Color(0xFF3F6837)),
    Song("7", "Deep Blue", "Ocean Drift", "Tides", 4 * 60_000 + 40_000, Color(0xFF00639A)),
    Song("8", "City Rain", "Neko Neko", "Umbrella", 3 * 60_000 + 50_000, Color(0xFF9A4058)),
)