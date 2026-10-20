package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.EchoChatViewModel
import com.example.ui.screens.MainScreen
import com.example.ui.theme.EchoChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: EchoChatViewModel = viewModel()
            val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            val effectiveDarkTheme = isDarkMode ?: isSystemInDarkTheme()

            CompositionLocalProvider(LocalLayoutDirection provides currentLanguage.layoutDirection) {
                EchoChatTheme(darkTheme = effectiveDarkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
