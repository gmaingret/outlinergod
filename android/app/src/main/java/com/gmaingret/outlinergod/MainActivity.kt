package com.gmaingret.outlinergod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.gmaingret.outlinergod.ui.navigation.AppNavHost
import com.gmaingret.outlinergod.ui.theme.OutlinerGodTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OutlinerGodTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
