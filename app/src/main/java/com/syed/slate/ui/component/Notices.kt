package com.syed.slate.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/** One short message: an undo offer, a sync result, connection changes. */
class Notice(
    val text: String,
    val kind: Kind = Kind.INFO,
    val actionLabel: String? = null,
    /** A newer notice of the same group replaces the older one (e.g. one Undo at a time, one sync status). */
    val group: String? = null,
    val durationMs: Long = 3_000,
    val onAction: (() -> Unit)? = null,
) {
    enum class Kind { INFO, OK, WARN, ERROR }
    val id: Long = seq.incrementAndGet()
    private companion object { val seq = AtomicLong() }
}

/** App-wide notices: posted from anywhere (repositories, screens), shown by [NoticeHost]. At most two at once. */
object Notices {
    private val _list = MutableStateFlow(emptyList<Notice>())
    val list: StateFlow<List<Notice>> = _list

    fun post(n: Notice) = synchronized(this) {
        val rest = _list.value.filter { n.group == null || it.group != n.group }
        _list.value = (rest + n).takeLast(2)
    }

    fun dismiss(n: Notice) = synchronized(this) { _list.value = _list.value.filter { it.id != n.id } }
}

/**
 * Shows [Notices] as small pills near the bottom edge. Unlike a Material snackbar they never block what is under
 * them: the pill has no pointer handling of its own, so a tap on it reaches the button or option underneath (the
 * "Next question" button, the flag chips…). Only the action word ("Undo") takes the tap. They also stay narrow and
 * short-lived, so they cover as little as possible.
 */
@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    val list by Notices.list.collectAsState()
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 14.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { n ->
                LaunchedEffect(n.id) { delay(n.durationMs); Notices.dismiss(n) }
                AnimatedVisibility(remember(n.id) { MutableTransitionState(false).apply { targetState = true } }, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                    NoticePill(n)
                }
            }
        }
    }
}

@Composable
private fun NoticePill(n: Notice) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    val (icon, tint) = when (n.kind) {
        Notice.Kind.OK -> Icons.Filled.CheckCircle to p.ok
        Notice.Kind.WARN -> Icons.Filled.CloudOff to p.warn
        Notice.Kind.ERROR -> Icons.Filled.ErrorOutline to p.bad
        Notice.Kind.INFO -> Icons.Filled.Info to p.info
    }
    Row(
        Modifier.widthIn(max = 340.dp).shadow(6.dp, shape).clip(shape).background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 12.dp, end = if (n.actionLabel != null) 4.dp else 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Text(
            n.text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (n.actionLabel != null) {
            Text(
                n.actionLabel,
                Modifier.clip(shape).background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .14f))
                    .clickable { n.onAction?.invoke(); Notices.dismiss(n) }.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
