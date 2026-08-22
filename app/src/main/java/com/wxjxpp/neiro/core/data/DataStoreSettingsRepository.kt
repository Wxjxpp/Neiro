package com.wxjxpp.neiro.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.ShuffleMode
import com.wxjxpp.neiro.core.model.SongSortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore 设置持久化。
 *
 * 新增设置项：在 Keys 里加一个 key，再补一对 observe/set 方法。
 */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.settingsDataStore

    private object Keys {
        val FloatingPlayerBar = booleanPreferencesKey("floating_player_bar")
        val ReplayGain = booleanPreferencesKey("replay_gain")
        val ShowTranslation = booleanPreferencesKey("show_translation")
        val ShuffleMode = stringPreferencesKey("shuffle_mode")
        val RepeatMode = stringPreferencesKey("repeat_mode")
        val KaraokeLyrics = booleanPreferencesKey("karaoke_lyrics")
        val ActiveUserApiId = stringPreferencesKey("active_user_api_id")
        val PreferredQuality = stringPreferencesKey("preferred_quality")
        val OnlineSearchPlatform = stringPreferencesKey("online_search_platform")
        val NeteaseCookie = stringPreferencesKey("netease_cookie")
        val SongSortField = stringPreferencesKey("song_sort_field")
        val SongSortDescending = booleanPreferencesKey("song_sort_descending")
        val LyricsOffsetMs = longPreferencesKey("lyrics_offset_ms")
        val PauseOnHeadphoneDisconnect = booleanPreferencesKey("pause_on_headphone_disconnect")
        val PauseOnAudioFocusLoss = booleanPreferencesKey("pause_on_audio_focus_loss")
        val AmbientGlow = booleanPreferencesKey("ambient_glow")
    }

    override fun observeFloatingPlayerBar(): Flow<Boolean> =
        store.data.map { it[Keys.FloatingPlayerBar] ?: true }

    override suspend fun setFloatingPlayerBar(enabled: Boolean) {
        store.edit { it[Keys.FloatingPlayerBar] = enabled }
    }

    override fun observeReplayGainEnabled(): Flow<Boolean> =
        store.data.map { it[Keys.ReplayGain] ?: false }

    override suspend fun setReplayGainEnabled(enabled: Boolean) {
        store.edit { it[Keys.ReplayGain] = enabled }
    }

    override fun observeShowTranslation(): Flow<Boolean> =
        store.data.map { it[Keys.ShowTranslation] ?: true }

    override suspend fun setShowTranslation(enabled: Boolean) {
        store.edit { it[Keys.ShowTranslation] = enabled }
    }

    fun observeShuffleMode(): Flow<ShuffleMode> = store.data.map { prefs ->
        runCatching { ShuffleMode.valueOf(prefs[Keys.ShuffleMode] ?: ShuffleMode.Pseudo.name) }
            .getOrDefault(ShuffleMode.Pseudo)
    }

    suspend fun setShuffleMode(mode: ShuffleMode) {
        store.edit { it[Keys.ShuffleMode] = mode.name }
    }

    fun observeRepeatMode(): Flow<RepeatMode> = store.data.map { prefs ->
        runCatching { RepeatMode.valueOf(prefs[Keys.RepeatMode] ?: RepeatMode.All.name) }
            .getOrDefault(RepeatMode.All)
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        store.edit { it[Keys.RepeatMode] = mode.name }
    }

    fun observeKaraokeLyrics(): Flow<Boolean> =
        store.data.map { it[Keys.KaraokeLyrics] ?: true }

    suspend fun setKaraokeLyrics(enabled: Boolean) {
        store.edit { it[Keys.KaraokeLyrics] = enabled }
    }

    fun observeActiveUserApiId(): Flow<String?> =
        store.data.map { it[Keys.ActiveUserApiId]?.takeIf { id -> id.isNotBlank() } }

    suspend fun setActiveUserApiId(id: String?) {
        store.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(Keys.ActiveUserApiId) else prefs[Keys.ActiveUserApiId] = id
        }
    }

    /** 在线播放偏好音质。取流失败时上层会自动降级。 */
    fun observePreferredQuality(): Flow<Quality> = store.data.map { prefs ->
        runCatching { Quality.valueOf(prefs[Keys.PreferredQuality] ?: Quality.Standard.name) }
            .getOrDefault(Quality.Standard)
    }

    suspend fun setPreferredQuality(quality: Quality) {
        store.edit { it[Keys.PreferredQuality] = quality.name }
    }

    /** 取流时需要立即读到当前音质，不适合走 Flow。 */
    suspend fun currentQuality(): Quality = observePreferredQuality().first()

    /** 在线搜索上次选择的平台（`all` 表示聚合搜索）。 */
    fun observeOnlineSearchPlatform(): Flow<String> =
        store.data.map { it[Keys.OnlineSearchPlatform] ?: "all" }

    suspend fun setOnlineSearchPlatform(id: String) {
        store.edit { it[Keys.OnlineSearchPlatform] = id }
    }

    /** 网易云 Cookie（`MUSIC_U=xxx`），用于解锁 VIP / 无版权歌曲取流。 */
    fun observeNeteaseCookie(): Flow<String> =
        store.data.map { it[Keys.NeteaseCookie].orEmpty() }
    suspend fun setNeteaseCookie(cookie: String) {
        store.edit { it[Keys.NeteaseCookie] = cookie.trim() }
    }

    /** 歌曲列表排序字段。 */
    fun observeSongSortField(): Flow<SongSortField> = store.data.map { prefs ->
        runCatching { SongSortField.valueOf(prefs[Keys.SongSortField] ?: SongSortField.Title.name) }
            .getOrDefault(SongSortField.Title)
    }
    suspend fun setSongSortField(field: SongSortField) {
        store.edit { it[Keys.SongSortField] = field.name }
    }
    /** 歌曲列表排序方向（true = 倒序）。 */
    fun observeSongSortDescending(): Flow<Boolean> =
        store.data.map { it[Keys.SongSortDescending] ?: false }
    suspend fun setSongSortDescending(descending: Boolean) {
        store.edit { it[Keys.SongSortDescending] = descending }
    }

    /** 歌词手动偏移（毫秒），正数 = 歌词提前。 */
    fun observeLyricsOffset(): Flow<Long> =
        store.data.map { it[Keys.LyricsOffsetMs] ?: 0L }
    suspend fun setLyricsOffset(offsetMs: Long) {
        store.edit { it[Keys.LyricsOffsetMs] = offsetMs }
    }

    /** 拔出耳机自动暂停（含蓝牙断开）。 */
    fun observePauseOnHeadphoneDisconnect(): Flow<Boolean> =
        store.data.map { it[Keys.PauseOnHeadphoneDisconnect] ?: true }
    suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        store.edit { it[Keys.PauseOnHeadphoneDisconnect] = enabled }
    }

    /** 其他应用发声时自动暂停本应用播放。 */
    fun observePauseOnAudioFocusLoss(): Flow<Boolean> =
        store.data.map { it[Keys.PauseOnAudioFocusLoss] ?: true }
    suspend fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        store.edit { it[Keys.PauseOnAudioFocusLoss] = enabled }
    }

    /** 播放页动态流光背景（根据封面取色流动渐变）。 */
    fun observeAmbientGlow(): Flow<Boolean> =
        store.data.map { it[Keys.AmbientGlow] ?: true }
    suspend fun setAmbientGlow(enabled: Boolean) {
        store.edit { it[Keys.AmbientGlow] = enabled }
    }
}