package com.wxjxpp.neiro.app.navigation

/**
 * 应用导航目的地。
 *
 * 新增页面：在这里加一个 object，然后在 MusicPlayerApp 的 RouteContent 里加一条分支。
 * 侧边抽屉的菜单项直接引用这些路由，不重复写字符串。
 */
sealed class Destination(val route: String) {

    /** 首页：本地歌曲列表。 */
    data object Home : Destination("home")
    /** 发现页：今日推荐 / 热榜 / 排行榜 / 新歌 / 猜你喜欢（在线内容）。 */
    data object Discover : Destination("discover")
    /** 专辑墙：双列网格展示本地曲库专辑。 */
    data object Albums : Destination("albums")
    data object Search : Destination("search")
    data object Library : Destination("library")
    data object Playlists : Destination("playlists")
    data object MusicSources : Destination("music_sources")
    data object Diary : Destination("diary")
    data object Together : Destination("together")
    data object Report : Destination("report")
    data object Settings : Destination("settings")

    /** 带参数的目的地。 */
    data object PlaylistDetail : Destination("playlist/{playlistId}") {
        const val ARG_ID = "playlistId"
        fun of(playlistId: String) = "playlist/$playlistId"
    }
}