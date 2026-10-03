package com.syed.anvil.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** True when the left-hand layout is chosen: the bars that hold actions are mirrored, so thumbs reach them. */
val LocalLeftHand = staticCompositionLocalOf { false }

/**
 * Mirrors [content] for left-handed use by laying it out right-to-left. Only for bars and button groups: body text
 * is never wrapped in this, so reading direction and alignment stay as they are.
 */
@Composable
fun HandMirror(content: @Composable () -> Unit) {
    if (LocalLeftHand.current) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content) else content()
}

/** A top app bar that swaps its back button and actions in the left-hand layout (the back arrow flips with it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnvilTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors,
) {
    HandMirror { TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions, colors = colors) }
}

/** Arrangement helper for rows that should put their action group first when left-handed. */
fun <T> handOrdered(left: Boolean, first: T, second: T): Pair<T, T> = if (left) second to first else first to second
