package com.syed.anvil.ui

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.ui.screen.HomeScreen
import com.syed.anvil.ui.screen.ModuleScreen
import com.syed.anvil.ui.screen.SettingsScreen
import com.syed.anvil.ui.screen.TopicScreen
import com.syed.anvil.ui.theme.AnvilTheme
import com.syed.anvil.ui.theme.Scope

private fun ModuleId.scope() = if (this == ModuleId.GENERAL) Scope.GENERAL else Scope.ICT

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
        composable("module/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = ModuleId.entries.first { it.key == entry.arguments!!.getString("id") }
            Themed(id.scope()) {
                ModuleScreen(vm, id, onBack = { nav.popBackStack() }, onTopic = { g, t -> nav.navigate("topic/${id.key}/$g/$t") })
            }
        }
        composable("topic/{id}/{group}/{topic}") { entry ->
            val a = entry.arguments!!
            val id = ModuleId.entries.first { it.key == a.getString("id") }
            Themed(id.scope()) {
                TopicScreen(vm, id, a.getString("group")!!, a.getString("topic")!!, onBack = { nav.popBackStack() })
            }
        }
    }
}
