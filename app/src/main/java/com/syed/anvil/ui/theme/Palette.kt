package com.syed.anvil.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Where in the app we are: each scope paints with its own colours. */
enum class Scope { SHELL, GENERAL, ICT }

/**
 * One scope's full set of colours, light and dark. The Material scheme is derived from it, and the semantic
 * colours (ok/bad/warn/info/imp) are exposed separately for the Nailed / Important / Weak markers.
 */
@Immutable
class Palette(
    val bg: Color, val surface: Color, val elevated: Color,
    val text: Color, val text2: Color, val text3: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val outline: Color,
    val ok: Color, val bad: Color, val warn: Color, val info: Color, val imp: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

object Palettes {
    // ── Anvil shell: rust ────────────────────────────────────────────────
    val shellLight = Palette(
        bg = c(0xF5EFE8), surface = c(0xFDFAF6), elevated = c(0xEFE6DC),
        text = c(0x241A14), text2 = c(0x4F4036), text3 = c(0x7A6A5D),
        primary = c(0xB7410E), onPrimary = c(0xFFF4EC), primaryContainer = c(0xF6DDD0), onPrimaryContainer = c(0x5A1F05),
        outline = c(0xD9CCBF),
        ok = c(0x4F8A5B), bad = c(0xC1553F), warn = c(0xB8691E), info = c(0x3F7A8C), imp = c(0x7657A6),
    )
    val shellDark = Palette(
        bg = c(0x17120F), surface = c(0x221B16), elevated = c(0x2C241E),
        text = c(0xF3ECE4), text2 = c(0xD4C8BB), text3 = c(0xA39384),
        primary = c(0xE27D4F), onPrimary = c(0x2A1005), primaryContainer = c(0x6E2A0A), onPrimaryContainer = c(0xFFDCC8),
        outline = c(0x3D332B),
        ok = c(0x86C08F), bad = c(0xE38C74), warn = c(0xEDA15C), info = c(0x7FB6C6), imp = c(0xB39DE6),
    )

    // ── General module: marigold, from general-quiz ──────────────────────
    val generalLight = Palette(
        bg = c(0xF4F0E9), surface = c(0xFFFDF9), elevated = c(0xF2EDE4),
        text = c(0x1F1A13), text2 = c(0x4E4538), text3 = c(0x776B5C),
        primary = c(0xA86F17), onPrimary = c(0xFBF3E4), primaryContainer = c(0xF1E3C4), onPrimaryContainer = c(0x4A3B29),
        outline = c(0xDDD3C3),
        ok = c(0x4F8A5B), bad = c(0xC1553F), warn = c(0xB8691E), info = c(0x3F7A8C), imp = c(0x7657A6),
    )
    val generalDark = Palette(
        bg = c(0x161310), surface = c(0x1F1B16), elevated = c(0x29241E),
        text = c(0xFAF6EF), text2 = c(0xD9D0C2), text3 = c(0xA89D8C),
        primary = c(0xF0BA66), onPrimary = c(0x231A0B), primaryContainer = c(0x4D3A14), onPrimaryContainer = c(0xF6DFB0),
        outline = c(0x3A342B),
        ok = c(0x86C08F), bad = c(0xE38C74), warn = c(0xEDA15C), info = c(0x7FB6C6), imp = c(0xB39DE6),
    )

    // ── ICT module: blue, from ict-quiz ──────────────────────────────────
    val ictLight = Palette(
        bg = c(0xDCE4EA), surface = c(0xFBFCFD), elevated = c(0xE6EDF1),
        text = c(0x0B151C), text2 = c(0x34454F), text3 = c(0x627380),
        primary = c(0x2E7599), onPrimary = c(0xF4FAFD), primaryContainer = c(0xCFE3EE), onPrimaryContainer = c(0x123448),
        outline = c(0xC3CFD7),
        ok = c(0x3B8A67), bad = c(0xC0564A), warn = c(0xB7802A), info = c(0x2E7599), imp = c(0x7657A6),
    )
    val ictDark = Palette(
        bg = c(0x0A0F13), surface = c(0x121A21), elevated = c(0x1B252E),
        text = c(0xF6F9FB), text2 = c(0xCDD6DC), text3 = c(0x98A6B0),
        primary = c(0x82C3E0), onPrimary = c(0x0F2A38), primaryContainer = c(0x1F4B63), onPrimaryContainer = c(0xD2EAF5),
        outline = c(0x26333D),
        ok = c(0x6CBF96), bad = c(0xE08576), warn = c(0xE3B566), info = c(0x6FB3D2), imp = c(0xB39DE6),
    )

    fun of(scope: Scope, dark: Boolean) = when (scope) {
        Scope.SHELL -> if (dark) shellDark else shellLight
        Scope.GENERAL -> if (dark) generalDark else generalLight
        Scope.ICT -> if (dark) ictDark else ictLight
    }
}

/** The active palette, for colours Material has no slot for (Nailed green, Important purple, Weak amber…). */
val LocalPalette = staticCompositionLocalOf { Palettes.shellLight }

/** Highlight marker colours, shared by both modules (they match the web apps). */
@Immutable
class HighlightColors(val fill: Color, val edge: Color)

object Highlights {
    private fun rgba(hex: Long, a: Float) = Color(0xFF000000 or hex).copy(alpha = a)
    fun light(name: String) = when (name) {
        "mint" -> HighlightColors(rgba(0x3BA078, .34f), c(0x3B8A67))
        "amber" -> HighlightColors(rgba(0xE8AA32, .46f), c(0xB7802A))
        "rose" -> HighlightColors(rgba(0xD65C80, .30f), c(0xB8506E))
        else -> HighlightColors(rgba(0x805EBE, .28f), c(0x7657A6))
    }
    fun dark(name: String) = when (name) {
        "mint" -> HighlightColors(rgba(0x6CBF96, .32f), c(0x6CBF96))
        "amber" -> HighlightColors(rgba(0xE8B964, .34f), c(0xE3B566))
        "rose" -> HighlightColors(rgba(0xE58AA3, .30f), c(0xE58AA3))
        else -> HighlightColors(rgba(0xB39DE6, .30f), c(0xB39DE6))
    }
}
