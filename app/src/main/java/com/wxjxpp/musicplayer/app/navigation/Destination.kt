package com.wxjxpp.musicplayer.app.navigation

/**
 * 应用导航目的地。
 *
 * 新增页面：在这里加一个 object，然后在 AppNavHost 里加一条 composable。
 * 侧边抽屉的菜单项直接引用这些路由，不重复写字符串。
 */
sealed class Destination(val route: String) {

    data object Home : Destination("home")
    data object Search : Destination("search")
    data object Library : Destination("library")
    data object Playlists : Destination("playlists")
    data object MusicSources : Destination("music_sources")
    data object PlayerDetail : Destination("player")
    data object Diary : Destination("diary")
    data object Together : Destination("together")
    data object Report : Destination("report")
    data object Settings : Destination("settings")

    /** 带参数的目的地示例。 */
    data object PlaylistDetail : Destination("playlist/{playlistId}") {
        const val ARG_ID = "playlistId"
        fun of(playlistId: String) = "playlist/$playlistId"
    }
}