package com.gmaingret.outlinergod.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gmaingret.outlinergod.ui.screen.bookmarks.BookmarksScreen
import com.gmaingret.outlinergod.ui.screen.DocumentDetailScreen
import com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListScreen
import com.gmaingret.outlinergod.ui.screen.login.LoginScreen
import com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorScreen
import com.gmaingret.outlinergod.ui.screen.search.SearchScreen
import com.gmaingret.outlinergod.ui.screen.settings.SettingsScreen

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
                    navController.navigate(AppRoutes.nodeEditor(documentId))
                },
                onNavigateToSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                },
                onNavigateToSearch = {
                    navController.navigate(AppRoutes.SEARCH)
                },
                onNavigateToBookmarks = {
                    navController.navigate(AppRoutes.BOOKMARKS)
                }
            )
        }
        composable(AppRoutes.DOCUMENT_DETAIL) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            DocumentDetailScreen(documentId = documentId)
        }
        composable(
            route = AppRoutes.NODE_EDITOR,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
                navArgument("rootNodeId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            val rootNodeId = backStackEntry.arguments?.getString("rootNodeId")?.ifEmpty { null }
            NodeEditorScreen(
                documentId = documentId,
                rootNodeId = rootNodeId,
                onNavigateUp = { navController.navigateUp() },
                onZoomIn = { nodeId ->
                    navController.navigate(AppRoutes.nodeEditor(documentId, nodeId))
                }
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateToLogin = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.BOOKMARKS) {
            BookmarksScreen(
                onNavigateToDocument = { documentId ->
                    navController.navigate(AppRoutes.nodeEditor(documentId))
                },
                onNavigateToNodeEditor = { documentId, nodeId ->
                    navController.navigate(AppRoutes.nodeEditor(documentId, nodeId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.SEARCH) {
            SearchScreen(
                onNavigateToNodeEditor = { documentId, nodeId ->
                    navController.navigate(AppRoutes.nodeEditor(documentId, nodeId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
