package com.gmaingret.outlinergod.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gmaingret.outlinergod.ui.screen.*

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.LOGIN,
        modifier = modifier
    ) {
        composable(AppRoutes.LOGIN) {
            LoginScreen()
        }
        composable(AppRoutes.DOCUMENT_LIST) {
            DocumentListScreen()
        }
        composable(AppRoutes.DOCUMENT_DETAIL) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            DocumentDetailScreen(documentId = documentId)
        }
        composable(AppRoutes.NODE_EDITOR) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId") ?: ""
            NodeEditorScreen(nodeId = nodeId)
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen()
        }
        composable(AppRoutes.BOOKMARKS) {
            BookmarksScreen()
        }
    }
}
