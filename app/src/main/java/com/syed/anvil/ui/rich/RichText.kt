package com.syed.anvil.ui.rich

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.syed.anvil.AnvilApp
import com.syed.anvil.ui.theme.LocalPalette

private fun annotate(p: Block.Paragraph): AnnotatedString = buildAnnotatedString {
    for (r in p.runs) {
        val deco = buildList {
            if (r.underline) add(TextDecoration.Underline)
            if (r.strike) add(TextDecoration.LineThrough)
        }
        withStyle(
            SpanStyle(
                fontWeight = if (r.bold) FontWeight.Bold else null,
                fontStyle = if (r.italic) FontStyle.Italic else null,
                textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
                baselineShift = when (r.script) {
                    Script.SUP -> BaselineShift.Superscript
                    Script.SUB -> BaselineShift.Subscript
                    Script.NONE -> null
                },
                fontSize = if (r.script == Script.NONE) TextUnit.Unspecified else 0.75.em,
            ),
        ) { append(r.text) }
    }
}

/** Renders quiz-content HTML: styled paragraphs, with embedded images shown from the offline image cache. */
@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
) {
    val blocks = remember(html) { HtmlParser.parse(html) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) when (b) {
            is Block.Paragraph -> Text(remember(b) { annotate(b) }, style = style, color = color)
            is Block.Image -> RemoteImage(b.url)
        }
    }
}

/**
 * ICT multiple-choice questions are plain text: the first line is the prompt and any further lines are code,
 * which keeps its indentation in a horizontally scrollable monospace block.
 */
@Composable
fun PlainQuestionText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val nl = text.indexOf('\n')
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (nl < 0) text else text.substring(0, nl), style = style)
        if (nl >= 0) {
            val p = LocalPalette.current
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(text.substring(nl + 1).trimEnd(), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.syed.anvil.ui.theme.Mono, fontSize = 13.sp, lineHeight = 19.sp), softWrap = false)
            }
        }
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as AnvilApp
    val online by app.connectivity.online.collectAsState()
    val bmp: Bitmap? by produceState<Bitmap?>(null, url, online) { value = app.images.get(url, allowNetwork = online) }
    val b = bmp
    if (b != null) {
        Image(
            b.asImageBitmap(), null,
            modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.FillWidth, alignment = Alignment.TopStart,
        )
    } else {
        Box(modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.small).background(LocalPalette.current.elevated), contentAlignment = Alignment.Center) {
            Text(if (online) "Loading image…" else "Image not available offline", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text3, modifier = Modifier.padding(8.dp))
        }
    }
}
