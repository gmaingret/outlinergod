package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.ui.common.MarkdownVisualTransformation
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ZWS = "\u200B"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NodeEditorScreen(
    documentId: String,
    rootNodeId: String? = null,
    onNavigateUp: () -> Unit,
    onZoomIn: (String) -> Unit = {},
    viewModel: NodeEditorViewModel = hiltViewModel()
) {
    val state by viewModel.container.stateFlow.collectAsState()
    var noteToFocusId by remember { mutableStateOf<String?>(null) }
    var contentToFocusId by remember { mutableStateOf<String?>(null) }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { /* uri -> attachment handling (future) */ }

    LaunchedEffect(documentId, rootNodeId) {
        viewModel.loadDocument(documentId, rootNodeId)
    }

    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is NodeEditorSideEffect.NavigateUp -> onNavigateUp()
                is NodeEditorSideEffect.ShowError -> { /* handled via state */ }
                is NodeEditorSideEffect.FocusNote -> noteToFocusId = sideEffect.nodeId
                is NodeEditorSideEffect.FocusContent -> contentToFocusId = sideEffect.nodeId
                is NodeEditorSideEffect.OpenAttachmentPicker -> attachmentPickerLauncher.launch("*/*")
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
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

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
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    val focusedNode = state.flatNodes.firstOrNull { it.entity.id == state.focusedNodeId }
                    if (focusedNode != null) {
                        NodeActionToolbar(
                            canUndo = state.canUndo,
                            canRedo = state.canRedo,
                            onIndent = { viewModel.indentNode(focusedNode.entity.id) },
                            onOutdent = { viewModel.outdentNode(focusedNode.entity.id) },
                            onMoveUp = { viewModel.moveUp(focusedNode.entity.id) },
                            onMoveDown = { viewModel.moveDown(focusedNode.entity.id) },
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onAddAttachment = { viewModel.openAttachmentPicker() },
                            onSwitchToNote = { viewModel.switchToNote(focusedNode.entity.id) },
                            modifier = Modifier.imePadding()
                        )
                    }
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
                        key = { it.entity.id },
                        contentType = { "node" }
                    ) { flatNode ->
                        ReorderableItem(reorderState, key = flatNode.entity.id) { isDragging ->
                            val currentNodeId = flatNode.entity.id
                            val swipeState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            viewModel.onCompletedToggled(currentNodeId)
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            viewModel.deleteNode(currentNodeId)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Node deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.restoreNode(currentNodeId)
                                                }
                                            }
                                            true
                                        }
                                        SwipeToDismissBoxValue.Settled -> true
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = swipeState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val color = when (swipeState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color),
                                        contentAlignment = when (swipeState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            else -> Alignment.CenterEnd
                                        }
                                    ) {
                                        Icon(
                                            imageVector = when (swipeState.dismissDirection) {
                                                SwipeToDismissBoxValue.StartToEnd ->
                                                    if (flatNode.entity.completed == 1) Icons.AutoMirrored.Filled.Undo
                                                    else Icons.Default.Check
                                                else -> Icons.Default.Delete
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            ) {
                                Surface(
                                    tonalElevation = if (isDragging) 4.dp else 0.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        ),
                                ) {
                                    NodeRow(
                                        flatNode = flatNode,
                                        isFocused = state.focusedNodeId == flatNode.entity.id,
                                        isNoteExpanded = flatNode.entity.id in state.expandedNoteIds || flatNode.entity.note.isNotBlank(),
                                        shouldFocusNote = noteToFocusId == flatNode.entity.id,
                                        shouldFocusContent = contentToFocusId == flatNode.entity.id,
                                        onNoteFocused = { noteToFocusId = null },
                                        onContentFocused = { contentToFocusId = null },
                                        onFocusGained = { viewModel.onNodeFocusGained(flatNode.entity.id) },
                                        onContentChanged = { viewModel.onContentChanged(flatNode.entity.id, it) },
                                        onEnterPressed = { cursor -> viewModel.onEnterPressed(flatNode.entity.id, cursor) },
                                        onBackspaceOnEmpty = { viewModel.onBackspaceOnEmptyNode(flatNode.entity.id) },
                                        onFocusLost = { viewModel.onNodeFocusLost(flatNode.entity.id) },
                                        onNoteChanged = { viewModel.onNoteChanged(flatNode.entity.id, it) },
                                        onToggleNote = { viewModel.toggleNote(flatNode.entity.id) },
                                        onGlyphTap = { onZoomIn(flatNode.entity.id) },
                                        onToggleCollapse = {
                                            viewModel.toggleCollapsed(flatNode.entity.id)
                                        },
                                        onIndent = { viewModel.indentNode(flatNode.entity.id) },
                                        onOutdent = { viewModel.outdentNode(flatNode.entity.id) },
                                    )
                                }
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
private fun NodeRow(
    flatNode: FlatNode,
    isFocused: Boolean,
    isNoteExpanded: Boolean,
    shouldFocusNote: Boolean,
    shouldFocusContent: Boolean,
    onNoteFocused: () -> Unit,
    onContentFocused: () -> Unit,
    onFocusGained: () -> Unit,
    onContentChanged: (String) -> Unit,
    onEnterPressed: (Int) -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onFocusLost: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onToggleNote: () -> Unit,
    onGlyphTap: () -> Unit,
    onToggleCollapse: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    var textFieldValue by remember(flatNode.entity.id) {
        val displayText = flatNode.entity.content.ifEmpty { ZWS }
        mutableStateOf(TextFieldValue(displayText, selection = TextRange(displayText.length)))
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(shouldFocusNote) {
        if (shouldFocusNote) {
            noteFocusRequester.requestFocus()
            onNoteFocused()
        }
    }

    LaunchedEffect(shouldFocusContent) {
        if (shouldFocusContent) {
            focusRequester.requestFocus()
            onContentFocused()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indentation
            Spacer(modifier = Modifier.width((flatNode.depth * 24).dp))

            // Glyph: filled dot, or directional arrow if has children
            if (flatNode.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(flatNode.entity.id) {
                            awaitEachGesture {
                                val down = awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                                var totalDrag = 0f
                                var fired = false
                                while (!fired) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    totalDrag += change.position.x - change.previousPosition.x
                                    val threshPx = 40.dp.toPx()
                                    if (totalDrag > threshPx) {
                                        onIndent(); fired = true; change.consume()
                                    } else if (totalDrag < -threshPx) {
                                        onOutdent(); fired = true; change.consume()
                                    }
                                }
                            }
                        }
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
                // Filled dot glyph -- tap = zoom in, horizontal drag = indent/outdent
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(flatNode.entity.id) {
                            awaitEachGesture {
                                val down = awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                                var totalDrag = 0f
                                var fired = false
                                while (!fired) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    totalDrag += change.position.x - change.previousPosition.x
                                    val threshPx = 40.dp.toPx()
                                    if (totalDrag > threshPx) {
                                        onIndent(); fired = true; change.consume()
                                    } else if (totalDrag < -threshPx) {
                                        onOutdent(); fired = true; change.consume()
                                    }
                                }
                            }
                        }
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
            Column(
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        val oldText = textFieldValue.text

                        // Detect Enter key: newline inserted
                        val newlineIndex = newText.indexOf('\n')
                        if (newlineIndex >= 0 && !oldText.contains('\n')) {
                            // Strip ZWS from position calculation
                            val cleanPosition = newText.substring(0, newlineIndex).replace(ZWS, "").length
                            onEnterPressed(cleanPosition)
                            return@BasicTextField
                        }

                        // Detect Backspace on empty node via ZWS sentinel
                        if (oldText == ZWS && newText.isEmpty()) {
                            onBackspaceOnEmpty()
                            return@BasicTextField
                        }

                        // Normal edit: strip ZWS from the text
                        val cleanText = newText.replace(ZWS, "")
                        if (cleanText.isEmpty()) {
                            textFieldValue = TextFieldValue(ZWS, selection = TextRange(1))
                            onContentChanged("")
                        } else {
                            textFieldValue = newValue.copy(text = cleanText)
                            onContentChanged(cleanText)
                        }
                    },
                    visualTransformation = MarkdownVisualTransformation,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (flatNode.entity.completed == 1)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (flatNode.entity.completed == 1)
                            TextDecoration.LineThrough
                        else
                            TextDecoration.None
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onFocusGained()
                            } else {
                                onFocusLost()
                            }
                        }
                )
            }

            // Note toggle button
            IconButton(
                onClick = onToggleNote,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Toggle note",
                    modifier = Modifier.size(16.dp),
                    tint = if (isNoteExpanded || flatNode.entity.note.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        // Note field (shown when expanded or note has content)
        if (isNoteExpanded) {
            var noteValue by remember(flatNode.entity.id, flatNode.entity.note) {
                mutableStateOf(flatNode.entity.note)
            }

            BasicTextField(
                value = noteValue,
                onValueChange = { newNote ->
                    noteValue = newNote
                    onNoteChanged(newNote)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ((flatNode.depth * 24) + 28).dp)
                    .focusRequester(noteFocusRequester),
                decorationBox = { innerTextField ->
                    if (noteValue.isEmpty()) {
                        Text(
                            text = "Add a note...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun NodeActionToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddAttachment: () -> Unit,
    onSwitchToNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(modifier = modifier) {
        IconButton(onClick = onIndent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Indent")
        }
        IconButton(onClick = onOutdent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = "Outdent")
        }
        IconButton(onClick = onMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
        }
        IconButton(onClick = onAddAttachment) {
            Icon(Icons.Default.AttachFile, contentDescription = "Add attachment")
        }
        IconButton(onClick = onSwitchToNote) {
            Icon(Icons.Default.Edit, contentDescription = "Switch to note")
        }
    }
}
