package com.syed.slate.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.syed.slate.ui.theme.LocalPalette

/** The web admin's "NO_SUB": a deliberate "no sub-topic" choice, distinct from "not answered yet". */
internal const val NO_SUB = "__none__"
internal const val NEW_SUB = "__new__"

/** Strips tags and collapses whitespace, like the web's stripTags. */
internal fun stripTags(s: String?): String = (s ?: "").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * The admin's section switcher: one pill container, icons only — the active section also spells its name
 * (the web does the same with titles/aria-labels on each icon).
 */
@Composable
internal fun AdminTabBar(tabs: List<Triple<String, ImageVector, String>>, active: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (key, icon, label) ->
            val on = key == active
            Row(
                Modifier.weight(if (on) 2.2f else 1f).heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                    .background(if (on) p.primary else Color.Transparent)
                    .clickable { onSelect(key) }.animateContentSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, label, Modifier.size(20.dp), tint = if (on) p.onPrimary else p.text2)
                if (on) Text(
                    label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = p.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A native-select look: rounded field, value + chevron, a dropdown menu. [options] are (value, label) pairs. */
@Composable
internal fun StyledSelect(
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    allLabel: String? = null,
    invalid: Boolean = false,
    optional: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.first == value }?.second
    val empty = selected == null
    val label = selected ?: allLabel ?: placeholder ?: ""
    val edge = when {
        invalid -> p.bad
        empty && !optional && allLabel == null && placeholder != null -> p.warn
        else -> p.outline
    }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(p.elevated)
                .border(BorderStroke(if (invalid) 1.5.dp else 1.dp, edge), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { open = true }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = if (empty && allLabel == null) p.text3 else p.text,
                fontWeight = if (empty) null else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = p.text3)
        }
        DropdownMenu(open, { open = false }) {
            if (allLabel != null) DropdownMenuItem({ Text(allLabel) }, { onChange(""); open = false })
            options.forEach { (v, name) ->
                DropdownMenuItem(
                    { Text(name, fontWeight = if (v == value) FontWeight.Bold else null) },
                    { onChange(v); open = false },
                )
            }
        }
    }
}

/** A small rounded chip. */
@Composable
internal fun Chip(
    text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: ImageVector? = null,
    mono: Boolean = false, onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.clip(CircleShape).background(bg).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(12.dp), tint = fg) }
        Text(
            text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) com.syed.slate.ui.theme.Mono else null,
        )
        trailing?.let { Icon(it, null, Modifier.size(12.dp), tint = fg.copy(alpha = .7f)) }
    }
}

/** Square icon button used for move / delete on rows. */
@Composable
internal fun SquareIconButton(icon: ImageVector, tint: Color, desc: String, enabled: Boolean = true, size: androidx.compose.ui.unit.Dp = 40.dp, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(18.dp), tint = if (enabled) tint else tint.copy(alpha = .35f)) }
}

/** « ‹ 1 … 7 [8] 9 … 45 › » — the web's windowed numeric pager. */
@Composable
internal fun AdminPager(page: Int, pageCount: Int, onPage: (Int) -> Unit) {
    if (pageCount <= 1) return
    val p = LocalPalette.current
    val set = sortedSetOf(0, pageCount - 1)
    for (q in page - 1..page + 1) if (q in 0 until pageCount) set.add(q)
    val items = ArrayList<Int>() // -1 marks an ellipsis
    var prev = -1
    for (n in set) { if (n - prev > 1) items.add(-1); items.add(n); prev = n }
    @Composable fun nav(icon: ImageVector, enabled: Boolean, desc: String, to: Int) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { onPage(to.coerceIn(0, pageCount - 1)) },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, desc, Modifier.size(18.dp), tint = if (enabled) p.text2 else p.text3.copy(alpha = .4f)) }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        nav(Icons.Filled.FirstPage, page > 0, "First page", 0)
        nav(Icons.Filled.ChevronLeft, page > 0, "Previous page", page - 1)
        items.forEach { n ->
            if (n < 0) Box(Modifier.size(width = 22.dp, height = 38.dp), contentAlignment = Alignment.Center) { Text("…", color = p.text3) }
            else Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (n == page) p.primary else p.surface)
                    .border(1.dp, if (n == page) p.primary else p.outline, RoundedCornerShape(10.dp)).clickable { onPage(n) },
                contentAlignment = Alignment.Center,
            ) { Text("${n + 1}", style = MaterialTheme.typography.labelLarge, color = if (n == page) p.onPrimary else p.text2, fontWeight = FontWeight.SemiBold) }
        }
        nav(Icons.Filled.ChevronRight, page < pageCount - 1, "Next page", page + 1)
        nav(Icons.Filled.LastPage, page < pageCount - 1, "Last page", pageCount - 1)
    }
}

/** The web's centered modal card: icon coin, title, chips, snippet, warning text, actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminModal(
    icon: ImageVector, iconTint: Color, title: String, busy: Boolean, onCancel: () -> Unit,
    meta: List<Pair<String, Boolean>> = emptyList(), snippet: String? = null, warn: String? = null,
    body: @Composable () -> Unit = {}, actions: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = { if (!busy) onCancel() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(iconTint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (meta.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.forEach { (t, isCat) -> if (isCat) Chip(t, p.text2, p.elevated) else Chip(t, p.text3, p.elevated) }
            }
            if (snippet != null) Text(
                snippet.ifEmpty { "(image-only)" },
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.elevated).padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            body()
            if (warn != null) Text(warn, style = MaterialTheme.typography.bodySmall, color = p.text3)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
internal fun ModalButton(text: String, filled: Boolean, tint: Color? = null, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = if (filled) (tint ?: p.primary).copy(alpha = if (enabled) 1f else .35f) else p.surface
    val fg = if (filled) Color.White else p.text2
    Row(
        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .let { if (filled) it else it.border(1.dp, p.outline, RoundedCornerShape(14.dp)) }
            .clickable(enabled = enabled && !busy, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = fg)
        else icon?.let { Icon(it, null, Modifier.size(15.dp), tint = fg) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

/** A chip that opens a dropdown of (value, label) options — compact category / sub-topic picking on a card. */
@Composable
internal fun ChipSelect(
    label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit,
    fg: Color, bg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Chip(label, fg, bg, icon = icon, trailing = Icons.Filled.ExpandMore, onClick = if (enabled) ({ open = true }) else null)
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, name) ->
                DropdownMenuItem({ Text(name, fontWeight = if (v == value) FontWeight.Bold else null) }, { onChange(v); open = false })
            }
        }
    }
}
