package com.nextlevel.gymrat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nextlevel.gymrat.core.ApiClient
import com.nextlevel.gymrat.features.home.HomeScreen
import com.nextlevel.gymrat.features.home.HomeViewModel
import com.nextlevel.gymrat.ui.theme.GymRatTheme

/** App entry point. Mirrors `GymRatApp` on iOS: builds the API client and hosts the Home screen. */
class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(ApiClient(AppConfiguration.apiBaseUrl)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymRatTheme {
                HomeScreen(model = home)
            }
        }
    }
}
