package com.syed.slate.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Slate's loading mark: the slate board outline with a chalk tick that draws itself, holds, then rubs out.
 * Colours come from the active theme (primary + onSurfaceVariant), so it follows light/dark and each module's palette.
 */
@Composable
private fun SlateMark(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "slate-loader")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val board = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
    val chalk = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size)) { drawSlate(phase, board, chalk) }
}

private fun DrawScope.drawSlate(phase: Float, board: Color, chalk: Color) {
    val w = size.width; val h = size.height
    val stroke = (w * 0.09f).coerceAtLeast(1.5f)
    // The board: rounded rectangle, with a chalk tray under it.
    val bw = w * 0.84f; val bh = h * 0.64f
    val left = (w - bw) / 2f; val top = h * 0.06f
    drawRoundRect(board, Offset(left, top), Size(bw, bh), CornerRadius(w * 0.16f), style = Stroke(stroke))
    drawLine(board, Offset(w * 0.3f, h * 0.9f), Offset(w * 0.7f, h * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
    // The tick: drawn over the first 55% of the loop, held until 80%, rubbed out from its start by 100%.
    val tick = Path().apply {
        moveTo(w * 0.29f, h * 0.38f); lineTo(w * 0.45f, h * 0.53f); lineTo(w * 0.72f, h * 0.2f)
    }
    val pm = PathMeasure().apply { setPath(tick, false) }
    val len = pm.length
    val (from, to) = when {
        phase < .55f -> 0f to phase / .55f
        phase < .8f -> 0f to 1f
        else -> (phase - .8f) / .2f to 1f
    }
    val seg = Path()
    if (to > from) pm.getSegment(len * from, len * to, seg, true)
    drawPath(seg, chalk, style = Stroke(stroke * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Centred loading state: the animated Slate mark with an optional line of text under it. */
@Composable
fun SlateLoader(modifier: Modifier = Modifier, label: String? = null, size: Dp = 40.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        SlateMark(size)
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small inline variant, e.g. inside a button or a list row. */
@Composable
fun SlateLoaderInline(size: Dp = 20.dp) = SlateMark(size)

/** Thin indeterminate bar in the theme's primary colour: the Slate replacement for LinearProgressIndicator. */
@Composable
fun SlateLinearLoader(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "slate-bar")
    val x by t.animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val track = MaterialTheme.colorScheme.primary.copy(alpha = .16f)
    val bar = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        val bw = size.width * .35f
        val start = (x * size.width).coerceAtLeast(0f)
        val end = (x * size.width + bw).coerceAtMost(size.width)
        if (end > start) drawRoundRect(bar, Offset(start, 0f), Size(end - start, size.height), CornerRadius(size.height / 2))
    }
}
