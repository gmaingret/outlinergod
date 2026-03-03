package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.ui.common.MarkdownVisualTransformation
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ZWS = "\u200B"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                                    isNoteExpanded = flatNode.entity.id in state.expandedNoteIds || flatNode.entity.note.isNotBlank(),
                                    onContentChanged = { viewModel.onContentChanged(flatNode.entity.id, it) },
                                    onEnterPressed = { cursor -> viewModel.onEnterPressed(flatNode.entity.id, cursor) },
                                    onBackspaceOnEmpty = { viewModel.onBackspaceOnEmptyNode(flatNode.entity.id) },
                                    onFocusLost = { viewModel.onNodeFocusLost(flatNode.entity.id) },
                                    onNoteChanged = { viewModel.onNoteChanged(flatNode.entity.id, it) },
                                    onToggleNote = { viewModel.toggleNote(flatNode.entity.id) },
                                    onGlyphTap = { /* zoom in -- wired in future task */ },
                                    onToggleCollapse = {
                                        viewModel.toggleCollapsed(flatNode.entity.id)
                                    },
                                    onLongPress = {
                                        viewModel.showContextMenu(flatNode.entity.id)
                                    },
                                    onIndent = { viewModel.indentNode(flatNode.entity.id) },
                                    onOutdent = { viewModel.outdentNode(flatNode.entity.id) },
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

            // Context menu bottom sheet
            if (state.contextMenuNodeId != null) {
                val contextNodeId = state.contextMenuNodeId!!
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissContextMenu() }
                ) {
                    ListItem(
                        headlineContent = { Text("Add Child") },
                        modifier = Modifier.clickable {
                            viewModel.addChildNode(contextNodeId)
                            viewModel.dismissContextMenu()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Indent") },
                        modifier = Modifier.clickable {
                            viewModel.indentNode(contextNodeId)
                            viewModel.dismissContextMenu()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Outdent") },
                        modifier = Modifier.clickable {
                            viewModel.outdentNode(contextNodeId)
                            viewModel.dismissContextMenu()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Toggle Completed") },
                        modifier = Modifier.clickable {
                            viewModel.onCompletedToggled(contextNodeId)
                            viewModel.dismissContextMenu()
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Delete") },
                        modifier = Modifier.clickable {
                            viewModel.deleteNode(contextNodeId)
                            viewModel.dismissContextMenu()
                        }
                    )
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
    onContentChanged: (String) -> Unit,
    onEnterPressed: (Int) -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onFocusLost: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onToggleNote: () -> Unit,
    onGlyphTap: () -> Unit,
    onToggleCollapse: () -> Unit,
    onLongPress: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember(flatNode.entity.id) {
        val displayText = flatNode.entity.content.ifEmpty { ZWS }
        mutableStateOf(TextFieldValue(displayText, selection = TextRange(displayText.length)))
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
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
            // The glyph area also serves as the drag handle (via dragModifier) and
            // horizontal drag for indent (right) / outdent (left).
            if (flatNode.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(flatNode.entity.id) {
                            // Initial pass (capture, outer→inner): fires before longPressDraggableHandle
                            // which runs in Main pass. Consuming on threshold prevents DnD activating
                            // on a quick horizontal swipe.
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
                // Filled dot glyph -- tap = zoom in, long-press = drag, horizontal drag = indent/outdent
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

            // Content field — long-press on parent Column using Initial pass (capture phase).
            // Initial pass fires before BasicTextField's Main-pass text-selection handler,
            // so our timer runs to completion and onLongPress() fires reliably.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(flatNode.entity.id) {
                        awaitEachGesture {
                            val firstDown = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                            var longPressTriggered = false
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                                    if (!change.pressed) break
                                    val dx = change.position.x - firstDown.position.x
                                    val dy = change.position.y - firstDown.position.y
                                    if (dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop) break
                                }
                            } ?: run { longPressTriggered = true }
                            if (longPressTriggered) onLongPress()
                        }
                    }
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
                        // When user backspaces on a node showing only ZWS, the text becomes empty
                        if (oldText == ZWS && newText.isEmpty()) {
                            onBackspaceOnEmpty()
                            return@BasicTextField
                        }

                        // Normal edit: strip ZWS from the text
                        val cleanText = newText.replace(ZWS, "")
                        if (cleanText.isEmpty()) {
                            // Node is now empty — restore the ZWS sentinel so backspace keeps working
                            textFieldValue = TextFieldValue(ZWS, selection = TextRange(1))
                            onContentChanged("")
                        } else {
                            textFieldValue = newValue.copy(text = cleanText)
                            onContentChanged(cleanText)
                        }
                    },
                    visualTransformation = MarkdownVisualTransformation,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
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
                    .padding(start = ((flatNode.depth * 24) + 28).dp),
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
