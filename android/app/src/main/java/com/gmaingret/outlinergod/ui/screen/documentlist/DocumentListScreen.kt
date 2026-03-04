package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.prototype.FractionalIndex
import com.gmaingret.outlinergod.ui.common.SyncStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentListScreen(
    onNavigateToNodeEditor: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: DocumentListViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var createDocTitle by remember { mutableStateOf("") }

    // Trigger sync on screen resume
    LaunchedEffect(Unit) {
        viewModel.onScreenResumed()
    }

    // Collect side effects
    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is DocumentListSideEffect.ShowError -> {
                    snackbarHostState.showSnackbar(sideEffect.message)
                }
            }
        }
    }

    // Create document dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                createDocTitle = ""
            },
            title = { Text("New Document") },
            text = {
                OutlinedTextField(
                    value = createDocTitle,
                    onValueChange = { createDocTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createDocTitle.isNotBlank()) {
                            val items = (state as? DocumentListUiState.Success)?.items ?: emptyList()
                            val sortOrder = generateNextSortOrder(items)
                            viewModel.createDocument(
                                title = createDocTitle.trim(),
                                type = "document",
                                parentId = null,
                                sortOrder = sortOrder
                            )
                            showCreateDialog = false
                            createDocTitle = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        createDocTitle = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OutlinerGod") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create document"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sync status indicator
            when ((state as? DocumentListUiState.Success)?.syncStatus) {
                SyncStatus.Syncing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                SyncStatus.Error -> {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }

            when (val currentState = state) {
                is DocumentListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DocumentListUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.loadDocuments() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is DocumentListUiState.Success -> {
                    val visibleItems = filterCollapsedChildren(currentState.items)

                    if (visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No documents yet. Tap + to create one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = visibleItems,
                                key = { it.id }
                            ) { document ->
                                DocumentListItem(
                                    document = document,
                                    isFolder = document.type == "folder",
                                    onTap = { onNavigateToNodeEditor(document.id) },
                                    onDelete = { viewModel.deleteDocument(document.id) },
                                    onRename = { newTitle -> viewModel.renameDocument(document.id, newTitle) },
                                    onToggleCollapse = { viewModel.toggleFolderCollapse(document.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentListItem(
    document: DocumentEntity,
    isFolder: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onToggleCollapse: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(document.title) }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameText = document.title
            },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(renameText.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    renameText = document.title
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = { showContextMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = document.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            if (isFolder) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (document.collapsed == 1)
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (document.collapsed == 1) "Expand" else "Collapse"
                    )
                }
            }
        }

        // Context menu (shown on long-press)
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showContextMenu = false
                    renameText = document.title
                    showRenameDialog = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showContextMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

/**
 * Filter out children of collapsed folders.
 */
private fun filterCollapsedChildren(items: List<DocumentEntity>): List<DocumentEntity> {
    val collapsedIds = items.filter { it.collapsed == 1 }.map { it.id }.toSet()
    return items.filter { it.parentId == null || it.parentId !in collapsedIds }
}

/**
 * Generate the next sort order for a new document.
 */
internal fun generateNextSortOrder(items: List<DocumentEntity>): String {
    return if (items.isEmpty()) {
        FractionalIndex.generateKeyBetween(null, null) // "aV" (midpoint of 62-char alphabet)
    } else {
        val maxSortOrder = items.maxOf { it.sortOrder }
        FractionalIndex.generateKeyBetween(maxSortOrder, null)
    }
}
