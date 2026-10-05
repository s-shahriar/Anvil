package com.syed.slate.ui.screen

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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Apartment
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
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
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
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.SearchText
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.rich.RichText
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.isDark
import com.syed.slate.ui.theme.TopicColors
import androidx.compose.ui.platform.LocalContext
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
    // Financial Terms
    "Star" to Icons.Filled.Star, "Building2" to Icons.Filled.Apartment, "Banknote" to Icons.Filled.Payments, "BarChart2" to Icons.Filled.BarChart,
    "Leaf" to Icons.Filled.Eco, "RefreshCw" to Icons.Filled.Autorenew, "Scale" to Icons.Filled.Balance, "FileText" to Icons.Filled.Description,
    "Eye" to Icons.Filled.Visibility, "Waves" to Icons.Filled.Waves, "ArrowLeftRight" to Icons.Filled.SwapHoriz, "Ruler" to Icons.Filled.Straighten,
    "Home" to Icons.Filled.Home, "ClipboardList" to Icons.AutoMirrored.Filled.Assignment, "CheckSquare" to Icons.Filled.CheckBox,
    "Files" to Icons.Filled.FileCopy, "PenLine" to Icons.Filled.Edit, "DollarSign" to Icons.Filled.AttachMoney, "Package" to Icons.Filled.Inventory2,
    "Bitcoin" to Icons.Filled.CurrencyBitcoin, "AlertTriangle" to Icons.Filled.Warning, "Hash" to Icons.Filled.Tag, "Search" to Icons.Filled.Search,
    "Folder" to Icons.Filled.Folder, "AlertCircle" to Icons.Filled.Error, "Target" to Icons.Filled.TrackChanges, "Award" to Icons.Filled.EmojiEvents,
)

private fun JSONArray?.strings(): List<String> = this?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()

/** One reference card: used by General's Written » Data (from the database) and Financial Terms (bundled). */
class DataCardModel(val cat: String?, val serial: Int, val icon: String, val title: String, val subtitle: String, val body: String, val tip: String?, val issues: List<String>, val benefits: List<String>) {
    val haystack: String = SearchText.normalize(HtmlParser.plainText("$title $subtitle $body ${cat.orEmpty()} ${tip.orEmpty()}"))
}

/** "Written » Data": the reference cards (a figure and what it means), from the offline copy of General. */
@Composable
fun WrittenDataScreen(vm: SlateViewModel, onBack: () -> Unit) {
    val m = vm.module(ModuleId.GENERAL)
    val state by m.content.state.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    val dark = LocalPalette.current.isDark
    val catNames = remember(content) { content?.writtenCategories.orEmpty().associate { it.getString("id") to it.getString("name") } }
    val categories = remember(content, dark) {
        content?.writtenCategories.orEmpty().map { it.getString("name") to TopicColors.parse(it.optString("color"), dark, Color.Gray) }
    }
    val cards = remember(content) {
        content?.writtenCards.orEmpty().map { c ->
            DataCardModel(
                catNames[c.optString("category_id")], c.optInt("serial"), c.optString("icon"), c.optString("title"), c.optString("subtitle"),
                c.optString("body"), c.optString("tip").takeIf { it.isNotEmpty() && !c.isNull("tip") },
                c.optJSONArray("issues").strings(), c.optJSONArray("benefits").strings(),
            )
        }
    }
    DataCardsScreen("Written · Data", "যেকোনো টপিক খুঁজুন...", categories, cards, onBack)
}

/** Category chips, search, and a list of reference cards. Shared by every card-based reference page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCardsScreen(title: String, searchHint: String, categories: List<Pair<String, Color>>, cards: List<DataCardModel>, onBack: () -> Unit) {
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val catColor = categories.toMap()
    val tokens = SearchText.tokens(query)
    val shown = cards.filter { (cat == null || it.cat == cat) && (tokens.isEmpty() || SearchText.matches(it.haystack, tokens)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle(title, style = MaterialTheme.typography.titleLarge) },
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
                        items(categories.map { it.first }, key = { it }) { name ->
                            val color = catColor[name] ?: Color.Gray
                            FilterChip(
                                cat == name, { cat = name }, { Text(name) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = .18f), selectedLabelColor = color),
                            )
                        }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(searchHint) })
                }
            }
            if (shown.isEmpty()) item { Text("কোনো ফলাফল পাওয়া যায়নি", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            items(shown, key = { it.serial }) { c -> DataCard(c, catColor[c.cat] ?: MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DataCard(c: DataCardModel, color: Color) {
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

/** General » Utility » ফিনান্সিয়াল টার্ম: 51 bundled reference cards in 8 categories. */
@Composable
fun FinancialTermsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val fallback = p.text3
    val (categories, cards) = remember(p.isDark) {
        val root = JSONObject(ctx.assets.open("utility/finance.json").bufferedReader().use { it.readText() })
        val cats = root.getJSONArray("categories").let { a -> List(a.length()) { a.getJSONObject(it).let { o -> o.getString("name") to TopicColors.parse(o.optString("color"), p.isDark, fallback) } } }
        val cs = root.getJSONArray("cards").let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { o -> DataCardModel(o.optString("cat"), o.getInt("id"), o.optString("icon"), o.getString("title"), o.optString("subtitle"), o.getString("body"), null, emptyList(), emptyList()) } }
        }
        cats to cs
    }
    DataCardsScreen("ফিনান্সিয়াল টার্ম", "যেকোনো টার্ম খুঁজুন...", categories, cards, onBack)
}
