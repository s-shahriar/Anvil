package com.syed.anvil.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.LongForm
import com.syed.anvil.content.PoolSet
import com.syed.anvil.ui.screen.ExamConfigScreen
import com.syed.anvil.ui.screen.ExamRunScreen
import com.syed.anvil.ui.screen.EquationScreen
import com.syed.anvil.ui.screen.FinancialTermsScreen
import com.syed.anvil.ui.screen.HomeScreen
import com.syed.anvil.ui.screen.MathFormulasScreen
import com.syed.anvil.ui.screen.ModeSelectScreen
import com.syed.anvil.ui.screen.ModuleNav
import com.syed.anvil.ui.screen.ModuleScreen
import com.syed.anvil.ui.screen.PracticeImportantScreen
import com.syed.anvil.ui.screen.PracticeScreen
import com.syed.anvil.ui.screen.QuizScreen
import com.syed.anvil.ui.screen.ReaderScreen
import com.syed.anvil.ui.screen.SavedScreen
import com.syed.anvil.ui.screen.SettingsScreen
import com.syed.anvil.ui.screen.StudyScreen
import com.syed.anvil.ui.screen.WrittenDataScreen
import com.syed.anvil.ui.theme.AnvilTheme
import com.syed.anvil.ui.theme.Scope

private fun ModuleId.scope() = if (this == ModuleId.GENERAL) Scope.GENERAL else Scope.ICT
private fun NavBackStackEntry.module() = ModuleId.entries.first { it.key == arguments!!.getString("m") }
private fun NavBackStackEntry.arg(name: String) = arguments!!.getString(name)!!

/** Back to the module's own screen, whatever sits above it. */
private fun NavHostController.toModuleHome(id: ModuleId) {
    if (!popBackStack("module/${id.key}", inclusive = false)) navigate("module/${id.key}")
}

/** Each destination is wrapped in its own scope: rust for the shell, marigold for General, blue for ICT. */
@Composable
fun AnvilNav(vm: AnvilViewModel, dark: Boolean, activity: Activity) {
    val nav = rememberNavController()

    @Composable
    fun Themed(scope: Scope, content: @Composable () -> Unit) = AnvilTheme(scope, dark) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize(), content = content)
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            Themed(Scope.SHELL) {
                HomeScreen(vm, dark, onModule = { nav.navigate("module/${it.key}") }, onSettings = { nav.navigate("settings") })
            }
        }
        composable("settings") {
            Themed(Scope.SHELL) { SettingsScreen(vm, activity, onBack = { nav.popBackStack() }) }
        }
        composable("module/{m}") { e ->
            val id = e.module()
            Themed(id.scope()) {
                ModuleScreen(
                    vm, id,
                    ModuleNav(
                        onBack = { nav.popBackStack() },
                        onTopic = { g, t -> nav.navigate(if (LongForm.isLongForm(g)) "read/${id.key}/$g/$t" else "topic/${id.key}/$g/$t") },
                        onExam = { g -> nav.navigate("exam/${id.key}?group=$g") },
                        onSaved = { kind, g -> nav.navigate("saved/${id.key}/${kind.key}/$g") },
                        onWritten = { nav.navigate("written/${id.key}") },
                        onPractice = { cat -> nav.navigate("practice/$cat") },
                        onPracticeImportant = { nav.navigate("practiceimp") },
                        onMath = { nav.navigate("math") },
                        onFinance = { nav.navigate("finance") },
                        onEquation = { t -> nav.navigate("equation/$t") },
                        onSearchHit = { item ->
                            val dest = if (LongForm.isLongForm(item)) "read" else "study"
                            nav.navigate("$dest/${id.key}/${item.group}/${item.topic}?focus=${Uri.encode(item.uid ?: "")}")
                        },
                    ),
                )
            }
        }
        composable("topic/{m}/{g}/{t}") { e ->
            val id = e.module(); val g = e.arg("g"); val t = e.arg("t")
            Themed(id.scope()) {
                ModeSelectScreen(
                    vm, id, g, t, onBack = { nav.popBackStack() },
                    onQuiz = { set -> nav.navigate("quiz/${id.key}/$g/$t?set=${set.key}") },
                    onStudy = { nav.navigate("study/${id.key}/$g/$t") },
                )
            }
        }
        composable("quiz/{m}/{g}/{t}?set={set}", listOf(navArgument("set") { type = NavType.StringType; defaultValue = "all" })) { e ->
            val id = e.module()
            Themed(id.scope()) {
                QuizScreen(vm, id, e.arg("g"), e.arg("t"), PoolSet.of(e.arguments!!.getString("set")), onBack = { nav.popBackStack() }, onHome = { nav.toModuleHome(id) })
            }
        }
        composable("study/{m}/{g}/{t}?focus={focus}", listOf(navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            val id = e.module()
            Themed(id.scope()) {
                StudyScreen(vm, id, e.arg("g"), e.arg("t"), e.arguments!!.getString("focus")?.ifEmpty { null }, onBack = { nav.popBackStack() })
            }
        }
        composable(
            "read/{m}/{g}/{t}?segment={segment}&focus={focus}",
            listOf(
                navArgument("segment") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { e ->
            val id = e.module(); val g = e.arg("g"); val t = e.arg("t")
            val focus = e.arguments!!.getString("focus")?.ifEmpty { null }
            Themed(id.scope()) {
                ReaderScreen(
                    vm, id, g, t, e.arguments!!.getString("segment"), focus,
                    onBack = { nav.popBackStack() },
                    onSegment = { seg -> nav.navigate("read/${id.key}/$g/$t?segment=${Uri.encode(seg)}") },
                    // From a search hit: replace this page with the segment page that holds the question.
                    onFocusSegment = { seg ->
                        nav.popBackStack()
                        nav.navigate("read/${id.key}/$g/$t?segment=${Uri.encode(seg)}&focus=${Uri.encode(focus ?: "")}")
                    },
                )
            }
        }
        composable("math") { Themed(Scope.GENERAL) { MathFormulasScreen(vm, onBack = { nav.popBackStack() }) } }
        composable("finance") { Themed(Scope.GENERAL) { FinancialTermsScreen(onBack = { nav.popBackStack() }) } }
        composable("equation/{t}") { e -> Themed(Scope.ICT) { EquationScreen(vm, e.arg("t"), onBack = { nav.popBackStack() }) } }
        composable("practice/{cat}") { e ->
            Themed(Scope.ICT) { PracticeScreen(vm, e.arg("cat"), onBack = { nav.popBackStack() }) }
        }
        composable("practiceimp") {
            Themed(Scope.ICT) { PracticeImportantScreen(vm, onBack = { nav.popBackStack() }) }
        }
        composable("written/{m}") { e ->
            Themed(e.module().scope()) { WrittenDataScreen(vm, onBack = { nav.popBackStack() }) }
        }
        composable("exam/{m}?group={group}", listOf(navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            val id = e.module()
            Themed(id.scope()) {
                ExamConfigScreen(vm, id, e.arguments!!.getString("group"), onBack = { nav.popBackStack() }, onStart = { nav.navigate("examrun/${id.key}") })
            }
        }
        composable("examrun/{m}") { e ->
            val id = e.module()
            Themed(id.scope()) { ExamRunScreen(vm, onBack = { nav.popBackStack() }, onHome = { nav.toModuleHome(id) }) }
        }
        composable("saved/{m}/{kind}/{g}") { e ->
            val id = e.module()
            Themed(id.scope()) { SavedScreen(vm, id, PoolSet.of(e.arg("kind")), e.arg("g"), onBack = { nav.popBackStack() }) }
        }
    }
}
