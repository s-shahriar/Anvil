package com.syed.anvil.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.syed.anvil.R

/** Bundled so the app looks the same offline. Bangla falls back to the system's Noto Sans Bengali. */
val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** Code and ASCII diagrams. Covers box-drawing and block characters; Bangla falls back to the system font. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

private fun s(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

val AnvilType = Typography(
    displaySmall = s(FontWeight.Bold, 34, 40, -0.9),
    headlineLarge = s(FontWeight.Bold, 28, 34, -0.6),
    headlineMedium = s(FontWeight.Bold, 24, 30, -0.5),
    headlineSmall = s(FontWeight.SemiBold, 20, 26, -0.3),
    titleLarge = s(FontWeight.SemiBold, 18, 24, -0.2),
    titleMedium = s(FontWeight.SemiBold, 16, 22),
    titleSmall = s(FontWeight.Medium, 14, 20),
    // Looser leading than Latin-only text needs: Bangla matras sit above and below the line.
    bodyLarge = s(FontWeight.Normal, 16, 26),
    bodyMedium = s(FontWeight.Normal, 14, 22),
    bodySmall = s(FontWeight.Normal, 12, 18),
    labelLarge = s(FontWeight.SemiBold, 14, 18),
    labelMedium = s(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = s(FontWeight.Medium, 11, 14, 0.3),
)
