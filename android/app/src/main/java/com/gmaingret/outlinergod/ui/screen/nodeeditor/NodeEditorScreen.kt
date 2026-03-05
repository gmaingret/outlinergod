package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmaingret.outlinergod.BuildConfig
import com.gmaingret.outlinergod.ui.common.MarkdownVisualTransformation
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val nodeId = state.focusedNodeId ?: return@rememberLauncherForActivityResult
        viewModel.uploadAttachment(nodeId, uri, mimeType)
    }

    LaunchedEffect(documentId, rootNodeId) {
        viewModel.loadDocument(documentId, rootNodeId)
    }

    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is NodeEditorSideEffect.NavigateUp -> onNavigateUp()
                is NodeEditorSideEffect.ShowError -> {
                    scope.launch { snackbarHostState.showSnackbar(sideEffect.message) }
                }
                is NodeEditorSideEffect.FocusNote -> noteToFocusId = sideEffect.nodeId
                is NodeEditorSideEffect.FocusContent -> contentToFocusId = sideEffect.nodeId
                is NodeEditorSideEffect.OpenAttachmentPicker -> attachmentPickerLauncher.launch("*/*")
            }
        }
    }

    // GAP-V2: Wire lifecycle to sync callbacks
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onScreenPaused()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            val focusManager = LocalFocusManager.current
            val density = LocalDensity.current
            val indentPx = with(density) { 48.dp.toPx() }
            var dragDepthOffset by remember { mutableStateOf(0) }
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                viewModel.reorderNodes(from.index, to.index, dragDepthOffset)
            }
            val imageLoader = remember(state.authToken) {
                val token = state.authToken
                if (token == null) ImageLoader(context)
                else ImageLoader.Builder(context)
                    .components {
                        add(OkHttpNetworkFetcherFactory(
                            callFactory = {
                                OkHttpClient.Builder()
                                    .addInterceptor { chain ->
                                        chain.proceed(
                                            chain.request().newBuilder()
                                                .addHeader("Authorization", "Bearer $token")
                                                .build()
                                        )
                                    }
                                    .build()
                            }
                        ))
                    }
                    .build()
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val breadcrumb = buildString {
                                if (state.documentTitle.isNotEmpty()) append(state.documentTitle)
                                else append(state.documentId.take(8))
                                state.rootNodeContent?.let { content ->
                                    append(" > ")
                                    append(content.ifEmpty { "…" })
                                }
                            }
                            Text(
                                text = breadcrumb,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
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
                            isNoteActive = focusedNode.entity.id in state.expandedNoteIds,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startX = down.position.x
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (reorderState.isAnyItemDragging) {
                                        val deltaX = change.position.x - startX
                                        dragDepthOffset = (deltaX / indentPx).roundToInt().coerceIn(-3, 3)
                                    }
                                }
                                dragDepthOffset = 0
                            }
                        }
                ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.flatNodes,
                        key = { it.entity.id },
                        contentType = { "node" }
                    ) { flatNode ->
                        val currentNodeId = flatNode.entity.id
                        val isFocused = state.focusedNodeId == flatNode.entity.id
                        ReorderableItem(reorderState, key = flatNode.entity.id) { isDragging ->
                            Column {
                                // Drop indicator: 2dp primary-colored line at top of the dragged item
                                if (isDragging) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .padding(start = (flatNode.depth * 24).dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
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
                                            }
                                        )
                                        .clickable(enabled = !isFocused) {
                                            viewModel.onNodeFocusGained(flatNode.entity.id)
                                        },
                                ) {
                                    NodeRow(
                                        flatNode = flatNode,
                                        isFocused = isFocused,
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
                                        onGlyphTap = { onZoomIn(flatNode.entity.id) },
                                        onToggleCollapse = { viewModel.toggleCollapsed(flatNode.entity.id) },
                                        imageLoader = imageLoader,
                                    )
                                }
                            }
                            } // Column (drop indicator wrapper)
                        } // ReorderableItem
                    }
                    // Trailing spacer — tap empty space below nodes clears focus
                    item {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    focusManager.clearFocus()
                                    viewModel.clearFocus()
                                }
                        )
                    }
                }
                } // Box (pointer input wrapper)
            }
        }
    }
}

private val ATTACH_REGEX = Regex("""^ATTACH\|(.+?)\|(.+?)\|(.+)$""")

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
    onGlyphTap: () -> Unit,
    onToggleCollapse: () -> Unit,
    imageLoader: ImageLoader,
) {
    val focusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    val isAttachment = flatNode.entity.content.startsWith("ATTACH|")
    var textFieldValue by remember(flatNode.entity.id) {
        val displayText = flatNode.entity.content.ifEmpty { ZWS }
        mutableStateOf(TextFieldValue(displayText, selection = TextRange(displayText.length)))
    }

    LaunchedEffect(isFocused) {
        if (isFocused && !isAttachment) {
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
        if (shouldFocusContent && !isAttachment) {
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

            // Glyph: filled dot — tap = zoom in
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onGlyphTap() },
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

            Spacer(modifier = Modifier.width(4.dp))

            // Content field (or attachment display)
            val attachMatch = if (isAttachment) ATTACH_REGEX.find(flatNode.entity.content) else null
            if (attachMatch != null) {
                val mimeType = attachMatch.groupValues[1]
                val filename = attachMatch.groupValues[2]
                val relativeUrl = attachMatch.groupValues[3]
                val isImage = mimeType.startsWith("image/")
                if (isImage) {
                    val fullUrl = BuildConfig.BASE_URL + relativeUrl
                    Column(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = filename,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = filename,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = filename,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (!isFocused) {
                // Not in edit mode — plain Text, zero gesture competition.
                // The Surface's pointerInput handles tap (→ focus) and long-press (→ drag).
                val displayStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (flatNode.entity.completed == 1)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (flatNode.entity.completed == 1)
                        TextDecoration.LineThrough
                    else
                        TextDecoration.None
                )
                Text(
                    text = flatNode.entity.content,
                    style = displayStyle,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
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
            }

            // Collapse/expand arrow: right-aligned, only shown for nodes with children
            if (flatNode.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (flatNode.entity.collapsed == 1)
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (flatNode.entity.collapsed == 1) "Expand" else "Collapse",
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    isNoteActive: Boolean,
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
        IconButton(onClick = onOutdent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = "Outdent")
        }
        IconButton(onClick = onIndent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Indent")
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
            Icon(
                Icons.Default.Edit,
                contentDescription = if (isNoteActive) "Hide note" else "Show note",
                tint = if (isNoteActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
