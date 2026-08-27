@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Container Transform 共享元素基础设施。
 *
 * [LocalSharedTransitionScope]：根布局 SharedTransitionLayout 提供，
 * 所有页面共享同一坐标系，跨页面/跨状态匹配共享元素。
 * 共享元素的动画作用域（animatedVisibilityScope）应使用驱动转场的
 * 那一层 AnimatedContent 自身的作用域（`this`），而非全局注入——
 * 挂错作用域会导致共享元素匹配不到活动转场、静默退化为淡入淡出。
 *
 * 使用方式（专辑封面从网格飞入详情头部）：
 * ```
 * val sts = LocalSharedTransitionScope.current
 * if (sts != null) with(sts) {
 *     Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = animScope)
 * }
 * ```
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
/**
 * 路由级 AnimatedContent 的动画作用域：跨页面共享元素（如歌曲页 SearchBar ↔ 搜索页
 * SearchBar 容器变换）两端都必须挂到驱动路由转场的同一 AnimatedContent 上。
 * 由外壳在 AnimatedContent body 内 provide 当前 `this`，页面内取用。
 */
val LocalRouteAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 底栏下沉联动通道：页面手势（如字母索引拖球快移）调用
 * `LocalBottomBarSink.current(true/false)` 请求播放栏下沉出屏/回归。
 * 默认空实现——未接线的预览/独立场景安全。
 */
val LocalBottomBarSink = compositionLocalOf<(Boolean) -> Unit> { {} }