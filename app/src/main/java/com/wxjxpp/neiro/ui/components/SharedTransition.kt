@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Container Transform 共享元素基础设施。
 *
 * - [LocalSharedTransitionScope]：根布局 SharedTransitionLayout 提供，
 *   所有页面共享同一坐标系，跨页面/跨状态匹配共享元素
 * - [LocalNavAnimatedVisibilityScope]：当前内容块的转场作用域
 *   （路由级 AnimatedContent 或页内 Crossfade），驱动共享元素的进出场插值
 *
 * 使用方式（专辑封面从网格飞入详情头部）：
 * ```
 * val sts = LocalSharedTransitionScope.current
 * if (sts != null) with(sts) {
 *     SharedElement(key, animatedScope) { AsyncImage(...) }
 * }
 * ```
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 当前页面内容的转场作用域（见 [LocalSharedTransitionScope] 说明）。 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }