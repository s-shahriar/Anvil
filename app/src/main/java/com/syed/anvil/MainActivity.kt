package com.syed.anvil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.syed.anvil.ui.AnvilNav
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.ThemeMode

class MainActivity : ComponentActivity() {
    private val vm: AnvilViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AnvilNav(vm = vm, dark = dark, activity = this)
        }
    }
}
