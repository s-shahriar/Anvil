package com.syed.slate.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The web apps colour topics with `oklch(L C hue)` over twelve fixed hues, lighter in dark mode. Android has no
 * oklch, so this converts it to sRGB. Data refers to them as "var(--topic-N)".
 */
object TopicColors {
    private val hues = doubleArrayOf(25.0, 55.0, 85.0, 115.0, 145.0, 175.0, 200.0, 225.0, 250.0, 280.0, 315.0, 350.0)

    /** [n] is 1..12 (wraps). */
    fun of(n: Int, dark: Boolean): Color {
        val hue = hues[((n - 1) % 12 + 12) % 12]
        return if (dark) oklch(0.76, 0.09, hue) else oklch(0.55, 0.10, hue)
    }

    /** "var(--topic-8)" or "#RRGGBB"; anything else falls back to [fallback]. */
    fun parse(value: String?, dark: Boolean, fallback: Color): Color {
        val v = value?.trim() ?: return fallback
        Regex("""var\(--topic-(\d+)\)""").find(v)?.let { return of(it.groupValues[1].toInt(), dark) }
        if (Regex("#[0-9a-fA-F]{6}").matches(v)) return Color(0xFF000000 or v.drop(1).toLong(16))
        return fallback
    }

    fun oklch(l: Double, c: Double, hDeg: Double): Color {
        val h = Math.toRadians(hDeg)
        val a = c * cos(h); val b = c * sin(h)
        val l_ = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m_ = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s_ = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        fun gamma(x: Double) = (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0)
        val r = gamma(4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_)
        val g = gamma(-1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_)
        val bl = gamma(-0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_)
        return Color(r.toFloat(), g.toFloat(), bl.toFloat())
    }
}
