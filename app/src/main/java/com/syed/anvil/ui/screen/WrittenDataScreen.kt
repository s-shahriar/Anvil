package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.SearchText
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.rich.HtmlParser
import com.syed.anvil.ui.rich.RichText
import com.syed.anvil.ui.theme.LocalPalette
import com.syed.anvil.ui.theme.isDark
import com.syed.anvil.ui.theme.TopicColors
import org.json.JSONArray
import org.json.JSONObject

/** The data stores Lucide icon names; these are the closest Material ones. */
private val iconMap: Map<String, ImageVector> = mapOf(
    "TrendingUp" to Icons.Filled.TrendingUp, "TrendingDown" to Icons.Filled.TrendingDown, "Users" to Icons.Filled.Groups,
    "Percent" to Icons.Filled.Percent, "HandCoins" to Icons.Filled.VolunteerActivism, "Globe" to Icons.Filled.Public,
    "ShoppingBag" to Icons.Filled.ShoppingBag, "PiggyBank" to Icons.Filled.Savings, "Wallet" to Icons.Filled.AccountBalanceWallet,
    "ShieldAlert" to Icons.Filled.GppMaybe, "Receipt" to Icons.Filled.Receipt, "GraduationCap" to Icons.Filled.School,
    "Database" to Icons.Filled.Storage, "Landmark" to Icons.Filled.AccountBalance, "Gauge" to Icons.Filled.Speed,
    "Ship" to Icons.Filled.DirectionsBoat, "CreditCard" to Icons.Filled.CreditCard, "Layers" to Icons.Filled.Layers,
    "Zap" to Icons.Filled.Bolt, "Flame" to Icons.Filled.LocalFireDepartment, "PieChart" to Icons.Filled.PieChart,
)

private fun JSONArray?.strings(): List<String> = this?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()

private class Card(val cat: String?, val serial: Int, val icon: String, val title: String, val subtitle: String, val body: String, val tip: String?, val issues: List<String>, val benefits: List<String>) {
    val haystack: String = SearchText.normalize(HtmlParser.plainText("$title $subtitle $body ${cat.orEmpty()} ${tip.orEmpty()}"))
}

/** "Written » Data": the reference cards (a figure and what it means), filtered by category and searchable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrittenDataScreen(vm: AnvilViewModel, onBack: () -> Unit) {
    val m = vm.module(ModuleId.GENERAL)
    val state by m.content.state.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    val p = LocalPalette.current
    val dark = p.isDark
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val catNames = remember(content) { content?.writtenCategories.orEmpty().associate { it.getString("id") to it.getString("name") } }
    val catColor = remember(content, dark) {
        content?.writtenCategories.orEmpty().associate { it.getString("name") to TopicColors.parse(it.optString("color"), dark, Color.Gray) }
    }
    val cards = remember(content) {
        content?.writtenCards.orEmpty().map { c ->
            Card(
                catNames[c.optString("category_id")], c.optInt("serial"), c.optString("icon"), c.optString("title"), c.optString("subtitle"),
                c.optString("body"), c.optString("tip").takeIf { it.isNotEmpty() && !c.isNull("tip") },
                c.optJSONArray("issues").strings(), c.optJSONArray("benefits").strings(),
            )
        }
    }
    val tokens = SearchText.tokens(query)
    val shown = cards.filter { (cat == null || it.cat == cat) && (tokens.isEmpty() || SearchText.matches(it.haystack, tokens)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Written · Data", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(cat == null, { cat = null }, { Text("সব") }) }
                        items(catNames.values.toList(), key = { it }) { name ->
                            val color = catColor[name] ?: Color.Gray
                            FilterChip(
                                cat == name, { cat = name }, { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = .18f), selectedLabelColor = color),
                            )
                        }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("যেকোনো টপিক খুঁজুন...") })
                }
            }
            if (shown.isEmpty()) item { Text("কোনো ফলাফল পাওয়া যায়নি", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            items(shown, key = { it.serial }) { c -> DataCard(c, catColor[c.cat] ?: MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DataCard(c: Card, color: Color) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(iconMap[c.icon] ?: Icons.Filled.Storage, null, tint = color)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                    Text("#${c.serial}", style = MaterialTheme.typography.labelMedium, color = p.text3)
                }
                Text(c.subtitle, style = MaterialTheme.typography.bodySmall, color = p.text3)
            }
        }
        // The body is HTML lines joined with <br>; each line is a bullet.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.body.split("<br>").filter { it.isNotBlank() }.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(color))
                    RichText(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (c.issues.isNotEmpty()) Effects("সমস্যা", Icons.Filled.Warning, p.warn, c.issues)
        if (c.benefits.isNotEmpty()) Effects("সুফল", Icons.Filled.CheckCircle, p.ok, c.benefits)
        c.tip?.let {
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp), tint = color)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Folded by default; issues and benefits unfold independently. */
@Composable
private fun Effects(label: String, icon: ImageVector, color: Color, items: List<String>) {
    var open by rememberSaveable(label, items.firstOrNull()) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(color.copy(alpha = .10f))) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = color)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = color)
        }
        if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•", color = color); Text(it, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}
