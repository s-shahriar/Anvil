package com.syed.slate.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.slate.ui.theme.LocalPalette

/**
 * The web's QuestionPeek (`shared/QuestionPeek.jsx`): a floating copy of the question while a long open answer is read.
 *  - Label "প্রশ্ন" with a chevron when the question is longer than two lines; tapping the text expands / collapses it in
 *    place (collapsed: two lines with a fade; expanded: up to 45% of the screen, scrollable).
 *  - Up-arrow button scrolls back to the card's real header.
 *  - Topic-coloured left rule, tinted border, shadow; fades in only while the card's body is on screen.
 *  - [actions]: the card's flag bar (Nail / Important / Weak / Note / Delete) on a row under the question, so it can be
 *    used from anywhere in a long answer, not only from the card's end.
 */
@Composable
fun QuestionPeekBar(question: String?, number: Int?, onJump: () -> Unit, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val color = MaterialTheme.colorScheme.primary
    val p = LocalPalette.current
    // Keep the last question while fading out, so the bar doesn't blank mid-animation.
    var last by remember { mutableStateOf("") }
    if (question != null) last = question
    AnimatedVisibility(
        visible = question != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 },
    ) {
        var expanded by remember(last) { mutableStateOf(false) }
        // Heuristic for "does not fit in two lines" (the web measures; blocks here are several Text nodes).
        val clamped = last.lines().count { it.isNotBlank() } > 2 || last.length > 90
        val maxExpanded = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
        val shape = RoundedCornerShape(12.dp)
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = shape, color = p.elevated, shadowElevation = 8.dp,
            border = BorderStroke(1.dp, color.copy(alpha = .35f).compositeOver(p.outline)),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(color))
                Column(Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(enabled = clamped || expanded) { expanded = !expanded },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (number?.let { "Q$it · " } ?: "") + "প্রশ্ন", color = color,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                            )
                            if (clamped || expanded) Icon(
                                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                if (expanded) "ছোট করো" else "পুরো প্রশ্ন দেখো", Modifier.size(16.dp), tint = color,
                            )
                        }
                        val surface = p.elevated
                        if (expanded) {
                            Box(Modifier.heightIn(max = maxExpanded).verticalScroll(rememberScrollState())) { QuestionTextBlocks(last, editable = false) }
                        } else {
                            Box(
                                Modifier.heightIn(max = 46.dp).clip(RoundedCornerShape(0.dp)).then(
                                    if (clamped) Modifier.drawWithContent {
                                        drawContent()
                                        drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to surface))
                                    } else Modifier,
                                ),
                            ) { QuestionTextBlocks(last, editable = false, compact = true) }
                        }
                    }
                    Surface(
                        onClick = onJump, modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent, border = BorderStroke(1.dp, p.outline),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.ArrowUpward, "প্রশ্নে ফিরে যাও", Modifier.size(16.dp), tint = p.text2) }
                    }
                }
                if (actions != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.outline))
                    actions()
                }
                }
            }
        }
    }
}
