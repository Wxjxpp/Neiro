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