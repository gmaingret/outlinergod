package com.gmaingret.outlinergod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.ui.navigation.AppNavHost
import com.gmaingret.outlinergod.ui.theme.OutlinerGodTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDao: SettingsDao
    @Inject lateinit var authRepository: AuthRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsFlow = authRepository.getUserId().flatMapLatest { userId ->
            if (userId != null) settingsDao.getSettings(userId) else flowOf(null)
        }

        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val isDarkTheme = when (settings?.theme) {
                "light" -> false
                "dark" -> true
                else -> true
            }
            val densityScale = when (settings?.density) {
                "cozy" -> 1.0f
                "comfortable" -> 0.95f
                "compact" -> 0.85f
                else -> 1.0f
            }
            OutlinerGodTheme(darkTheme = isDarkTheme, densityScale = densityScale) {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
