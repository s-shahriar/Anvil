package com.syed.slate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** True when the left-hand layout is chosen: the bars that hold actions are mirrored, so thumbs reach them. */
val LocalLeftHand = staticCompositionLocalOf { false }

/** True when the dark theme is active, so the top bar's theme button can show the one you'd switch to. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Provided once at the nav root: flips the layout from any top bar (null hides the toggle). */
val LocalToggleHand = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Provided once at the nav root: flips dark/light from any top bar (null hides the button). */
val LocalToggleTheme = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Mirrors [content] for left-handed use by laying it out right-to-left. Only for bars and button groups: body text
 * is never wrapped in this, so reading direction and alignment stay as they are.
 */
@Composable
fun HandMirror(content: @Composable () -> Unit) {
    if (LocalLeftHand.current) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content) else content()
}

/**
 * A top app bar that swaps its back button and actions in the left-hand layout (the back arrow flips with it).
 * The hand toggle is always the last action, so the layout can be switched from any screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlateTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors,
) {
    HandMirror {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = {
                actions()
                // The web's constant trio: hand, theme, sync — same place on every screen.
                val toggleTheme = LocalToggleTheme.current
                if (toggleTheme != null) IconButton(onClick = toggleTheme) {
                    Icon(
                        if (LocalDarkTheme.current) Icons.Filled.WbSunny else Icons.Filled.DarkMode,
                        if (LocalDarkTheme.current) "Dark theme: switch to light" else "Light theme: switch to dark",
                    )
                }
                val openQueue = LocalOpenSyncQueue.current
                if (openQueue != null) IconButton(onClick = openQueue) {
                    Icon(Icons.Filled.CloudSync, "Sync queue")
                }
                val toggle = LocalToggleHand.current
                if (toggle != null) IconButton(onClick = toggle) {
                    Icon(
                        Icons.Filled.PanTool,
                        if (LocalLeftHand.current) "Left-hand layout: switch to right" else "Right-hand layout: switch to left",
                        tint = if (LocalLeftHand.current) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = .55f),
                    )
                }
            },
            colors = colors,
        )
    }
}

/** Arrangement helper for rows that should put their action group first when left-handed. */
fun <T> handOrdered(left: Boolean, first: T, second: T): Pair<T, T> = if (left) second to first else first to second
