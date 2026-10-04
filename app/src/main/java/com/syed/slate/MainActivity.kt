package com.syed.slate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.syed.slate.ui.SlateNav
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.ThemeMode

class MainActivity : ComponentActivity() {
    private val vm: SlateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SlateNav(vm = vm, dark = dark, activity = this)
        }
    }
}
