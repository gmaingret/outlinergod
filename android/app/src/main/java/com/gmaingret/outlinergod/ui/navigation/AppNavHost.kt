package com.gmaingret.outlinergod.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gmaingret.outlinergod.ui.screen.*
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListScreen
import com.gmaingret.outlinergod.ui.screen.login.LoginScreen

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
            LoginScreen(
                onNavigateToDocumentList = {
                    navController.navigate(AppRoutes.DOCUMENT_LIST) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(AppRoutes.DOCUMENT_LIST) {
            DocumentListScreen(
                onNavigateToNodeEditor = { documentId ->
                    navController.navigate("node_editor/$documentId")
                },
                onNavigateToSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
        }
        composable(AppRoutes.DOCUMENT_DETAIL) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            DocumentDetailScreen(documentId = documentId)
        }
        composable(AppRoutes.NODE_EDITOR) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            NodeEditorScreen(documentId = documentId)
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen()
        }
        composable(AppRoutes.BOOKMARKS) {
            BookmarksScreen()
        }
    }
}
