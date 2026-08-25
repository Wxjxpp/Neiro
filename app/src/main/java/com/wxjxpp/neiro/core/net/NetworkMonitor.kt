package com.wxjxpp.neiro.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 网络连通性查询。
 *
 * 搜索空结果归因等场景需要区分「真的没有这首歌」和「根本没有联网」，
 * 这里提供同步的一次性探测：有活跃网络且具备 INTERNET 能力即视为在线。
 */
object NetworkMonitor {

    /** 当前是否有可用网络（Wi-Fi / 蜂窝 / 以太网）。 */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}