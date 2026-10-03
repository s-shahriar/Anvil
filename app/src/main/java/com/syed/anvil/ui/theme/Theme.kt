package com.syed.anvil.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val AnvilShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Wraps content in one scope's palette. Nest it to switch scopes: the shell uses [Scope.SHELL], and entering a
 * module re-themes everything below it.
 */
@Composable
fun AnvilTheme(scope: Scope, dark: Boolean, content: @Composable () -> Unit) {
    val p = Palettes.of(scope, dark)
    // Every container level is set: Material's defaults are a lavender that belongs to none of these palettes.
    val scheme = run {
        if (dark) darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = p.primaryContainer, onSecondaryContainer = p.onPrimaryContainer,
            background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.text2,
            surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.elevated, surfaceContainerHighest = p.elevated,
            inverseSurface = p.text, inverseOnSurface = p.bg, outline = p.outline, outlineVariant = p.outline,
            error = p.bad, errorContainer = p.bad.copy(alpha = .22f), onErrorContainer = p.text,
        ) else lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = p.primaryContainer, onSecondaryContainer = p.onPrimaryContainer,
            background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.text2,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.elevated, surfaceContainerHighest = p.elevated,
            inverseSurface = p.text, inverseOnSurface = p.bg, outline = p.outline, outlineVariant = p.outline,
            error = p.bad, errorContainer = p.bad.copy(alpha = .14f), onErrorContainer = p.text,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AnvilType, shapes = AnvilShapes, content = content)
    }
}
