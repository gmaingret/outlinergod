package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.ui.common.MarkdownVisualTransformation
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    documentId: String,
    onNavigateUp: () -> Unit,
    viewModel: NodeEditorViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsState()

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is NodeEditorSideEffect.NavigateUp -> onNavigateUp()
                is NodeEditorSideEffect.ShowError -> { /* handled via state */ }
            }
        }
    }

    when (state.status) {
        is NodeEditorStatus.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is NodeEditorStatus.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (state.status as NodeEditorStatus.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is NodeEditorStatus.Success -> {
            val haptic = LocalHapticFeedback.current
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                viewModel.reorderNodes(from.index, to.index)
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Editor") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateUp) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(
                        items = state.flatNodes,
                        key = { it.entity.id }
                    ) { flatNode ->
                        ReorderableItem(reorderState, key = flatNode.entity.id) { isDragging ->
                            Surface(
                                tonalElevation = if (isDragging) 4.dp else 0.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                NodeRow(
                                    flatNode = flatNode,
                                    isFocused = state.focusedNodeId == flatNode.entity.id,
                                    onContentChanged = { viewModel.onContentChanged(flatNode.entity.id, it) },
                                    onEnterPressed = { cursor -> viewModel.onEnterPressed(flatNode.entity.id, cursor) },
                                    onBackspaceOnEmpty = { viewModel.onBackspaceOnEmptyNode(flatNode.entity.id) },
                                    onFocusLost = { viewModel.onNodeFocusLost(flatNode.entity.id) },
                                    onGlyphTap = { /* zoom in -- wired in future task */ },
                                    onToggleCollapse = {
                                        viewModel.toggleCollapsed(flatNode.entity.id)
                                    },
                                    dragModifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    flatNode: FlatNode,
    isFocused: Boolean,
    onContentChanged: (String) -> Unit,
    onEnterPressed: (Int) -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onFocusLost: () -> Unit,
    onGlyphTap: () -> Unit,
    onToggleCollapse: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember(flatNode.entity.id, flatNode.entity.content) {
        mutableStateOf(TextFieldValue(flatNode.entity.content))
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indentation
        Spacer(modifier = Modifier.width((flatNode.depth * 24).dp))

        // Glyph: filled dot, or directional arrow if has children
        // The glyph area also serves as the drag handle via dragModifier
        if (flatNode.hasChildren) {
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier
                    .size(24.dp)
                    .then(dragModifier)
            ) {
                Icon(
                    imageVector = if (flatNode.entity.collapsed == 1)
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (flatNode.entity.collapsed == 1) "Expand" else "Collapse",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            // Filled dot glyph -- tap = zoom in, long-press = drag
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(dragModifier)
                    .clickable(onClick = onGlyphTap),
                contentAlignment = Alignment.Center
            ) {
                val color = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(
                        color = color,
                        radius = size.minDimension / 2,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Content field
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val newText = newValue.text
                val oldText = textFieldValue.text

                // Detect Enter key: newline inserted
                val newlineIndex = newText.indexOf('\n')
                if (newlineIndex >= 0 && !oldText.contains('\n')) {
                    // Enter pressed at the newline position
                    onEnterPressed(newlineIndex)
                    return@BasicTextField
                }

                // Detect Backspace on empty node
                if (oldText.isEmpty() && newText.isEmpty() && newValue.selection == TextRange(0)) {
                    onBackspaceOnEmpty()
                    return@BasicTextField
                }

                textFieldValue = newValue
                onContentChanged(newValue.text)
            },
            visualTransformation = MarkdownVisualTransformation,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onFocusLost()
                    }
                }
        )
    }
}
