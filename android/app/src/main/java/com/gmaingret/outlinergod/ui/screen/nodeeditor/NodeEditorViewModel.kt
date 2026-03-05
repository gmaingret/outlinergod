package com.gmaingret.outlinergod.ui.screen.nodeeditor

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gmaingret.outlinergod.sync.SyncConstants
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.prototype.FractionalIndex
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.FileRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import com.gmaingret.outlinergod.ui.mapper.mapToFlatList
import com.gmaingret.outlinergod.sync.toBookmarkSyncRecord
import com.gmaingret.outlinergod.sync.toSettingsSyncRecord
import com.gmaingret.outlinergod.sync.toDocumentSyncRecord
import com.gmaingret.outlinergod.sync.toNodeSyncRecord
import com.gmaingret.outlinergod.sync.toBookmarkEntity
import com.gmaingret.outlinergod.sync.toDocumentEntity
import com.gmaingret.outlinergod.sync.toNodeEntity
import com.gmaingret.outlinergod.sync.toSettingsEntity
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NodeEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nodeDao: NodeDao,
    private val authRepository: AuthRepository,
    private val hlcClock: HlcClock,
    private val syncRepository: SyncRepository,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>,
    private val fileRepository: FileRepository,
) : ViewModel(), ContainerHost<NodeEditorUiState, NodeEditorSideEffect> {

    override val container = container<NodeEditorUiState, NodeEditorSideEffect>(NodeEditorUiState())

    private var titleJob: Job? = null
    private val undoStack = ArrayDeque<List<FlatNode>>()
    private val redoStack = ArrayDeque<List<FlatNode>>()
    private val undoDeletedIds = ArrayDeque<List<String>>()

    private fun pushUndoSnapshot(current: List<FlatNode>) {
        if (undoStack.size >= 50) undoStack.removeFirst()
        undoStack.addLast(current)
        // Always push a parallel empty entry so undoDeletedIds stays aligned with undoStack
        undoDeletedIds.addLast(emptyList())
        redoStack.clear()
    }

    fun loadDocument(documentId: String, rootNodeId: String? = null) = intent {
        reduce { state.copy(documentId = documentId, rootNodeId = rootNodeId, status = NodeEditorStatus.Loading) }
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            documentDao.getDocumentById(documentId).filterNotNull().collect { doc ->
                intent { reduce { state.copy(documentTitle = doc.title) } }
            }
        }
        nodeDao.getNodesByDocument(documentId).collect { nodes ->
            // Only auto-create empty root node when not zoomed in to a sub-node
            if (nodes.isEmpty() && rootNodeId == null) {
                val deviceId = authRepository.getDeviceId().first()
                val now = System.currentTimeMillis()
                val hlc = hlcClock.generate(deviceId)
                val userId = authRepository.getUserId().filterNotNull().first()
                val rootNode = NodeEntity(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    userId = userId,
                    content = "",
                    contentHlc = hlc,
                    note = "",
                    noteHlc = "",
                    parentId = documentId,
                    parentIdHlc = hlc,
                    sortOrder = "a0",
                    sortOrderHlc = hlc,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now,
                )
                nodeDao.insertNode(rootNode)
                return@collect
            }
            // Use rootNodeId as the tree root when zoomed in; otherwise use documentId
            val treeRoot = rootNodeId ?: documentId
            // When zooming in, pre-filter to only the subtree rooted at rootNodeId
            // to avoid orphan-handling noise in mapToFlatList
            val nodesForMapper = if (rootNodeId != null) {
                filterSubtree(nodes, rootNodeId)
            } else {
                nodes
            }
            val flatNodes = mapToFlatList(nodesForMapper, treeRoot)
            val rootContent = if (rootNodeId != null) {
                nodes.firstOrNull { it.id == rootNodeId }?.content
            } else null
            reduce {
                state.copy(
                    status = NodeEditorStatus.Success,
                    flatNodes = flatNodes,
                    rootNodeContent = rootContent,
                )
            }
        }
    }

    fun onEnterPressed(nodeId: String, cursorPosition: Int) = intent {
        val flatNodes = state.flatNodes
        val index = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (index == -1) return@intent

        val currentNode = flatNodes[index].entity
        val leftContent = currentNode.content.substring(0, cursorPosition)
        val rightContent = currentNode.content.substring(cursorPosition)

        val deviceId = authRepository.getDeviceId().first()
        val now = System.currentTimeMillis()
        val hlc = hlcClock.generate(deviceId)

        // Find siblings (nodes with same parentId) sorted by sortOrder
        val siblings = flatNodes
            .filter { it.entity.parentId == currentNode.parentId }
            .sortedBy { it.entity.sortOrder }
        val siblingIndex = siblings.indexOfFirst { it.entity.id == nodeId }
        val nextSiblingSortOrder = if (siblingIndex + 1 < siblings.size) {
            siblings[siblingIndex + 1].entity.sortOrder
        } else {
            null
        }

        val newSortOrder = FractionalIndex.generateKeyBetween(
            currentNode.sortOrder,
            nextSiblingSortOrder
        )

        val newNodeId = UUID.randomUUID().toString()

        if (cursorPosition == 0) {
            // At start: insert blank node before, keep current node's content
            val blankNode = NodeEntity(
                id = newNodeId,
                documentId = currentNode.documentId,
                userId = currentNode.userId,
                content = "",
                contentHlc = hlc,
                note = "",
                noteHlc = "",
                parentId = currentNode.parentId,
                parentIdHlc = hlc,
                sortOrder = newSortOrder,
                sortOrderHlc = hlc,
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now,
            )
            // The blank node should go BEFORE the current node.
            // We need a sort order before the current node, not after.
            val prevSiblingSortOrder = if (siblingIndex > 0) {
                siblings[siblingIndex - 1].entity.sortOrder
            } else {
                null
            }
            val beforeSortOrder = FractionalIndex.generateKeyBetween(
                prevSiblingSortOrder,
                currentNode.sortOrder
            )
            nodeDao.insertNode(blankNode.copy(sortOrder = beforeSortOrder, sortOrderHlc = hlc))
        } else {
            // Split: update current with left half, insert new node with right half
            val updatedCurrent = currentNode.copy(
                content = leftContent,
                contentHlc = hlc,
                updatedAt = now,
                deviceId = deviceId,
            )
            nodeDao.updateNode(updatedCurrent)

            val newNode = NodeEntity(
                id = newNodeId,
                documentId = currentNode.documentId,
                userId = currentNode.userId,
                content = rightContent,
                contentHlc = hlc,
                note = "",
                noteHlc = "",
                parentId = currentNode.parentId,
                parentIdHlc = hlc,
                sortOrder = newSortOrder,
                sortOrderHlc = hlc,
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now,
            )
            nodeDao.insertNode(newNode)
        }

        reduce { state.copy(focusedNodeId = newNodeId) }
    }

    fun onBackspaceOnEmptyNode(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val index = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (index == -1) return@intent

        val node = flatNodes[index].entity
        if (node.content.isNotEmpty() || node.note.isNotEmpty()) return@intent

        val deviceId = authRepository.getDeviceId().first()
        val now = System.currentTimeMillis()
        val hlc = hlcClock.generate(deviceId)

        nodeDao.softDeleteNode(nodeId, now, hlc, now)

        val precedingNodeId = if (index > 0) flatNodes[index - 1].entity.id else null
        reduce { state.copy(focusedNodeId = precedingNodeId) }
    }

    fun moveFocus(direction: FocusDirection) = intent {
        val flatNodes = state.flatNodes
        if (flatNodes.isEmpty()) return@intent

        val currentIndex = if (state.focusedNodeId != null) {
            flatNodes.indexOfFirst { it.entity.id == state.focusedNodeId }
        } else {
            -1
        }

        val newIndex = when (direction) {
            FocusDirection.Down -> {
                if (currentIndex < flatNodes.size - 1) currentIndex + 1 else currentIndex
            }
            FocusDirection.Up -> {
                if (currentIndex > 0) currentIndex - 1 else currentIndex
            }
        }

        if (newIndex >= 0 && newIndex < flatNodes.size) {
            reduce { state.copy(focusedNodeId = flatNodes[newIndex].entity.id) }
        }
    }

    fun navigateUp() = intent {
        postSideEffect(NodeEditorSideEffect.NavigateUp)
    }

    fun toggleCollapsed(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val flatNode = flatNodes.firstOrNull { it.entity.id == nodeId } ?: return@intent
        val entity = flatNode.entity
        val newCollapsed = if (entity.collapsed == 1) 0 else 1

        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        val now = System.currentTimeMillis()

        nodeDao.updateNode(
            entity.copy(
                collapsed = newCollapsed,
                collapsedHlc = hlc,
                updatedAt = now,
                deviceId = deviceId,
            )
        )
    }

    // --- P4-10: Drag-and-drop reorder + indent/outdent ---

    fun reorderNodes(fromIndex: Int, toIndex: Int) = intent {
        val flatNodes = state.flatNodes
        if (fromIndex == toIndex || fromIndex !in flatNodes.indices || toIndex !in flatNodes.indices) return@intent

        val node = flatNodes[fromIndex]

        // Collect the block: the node plus all its descendants (deeper nodes immediately following)
        val blockEnd = ((fromIndex + 1)..flatNodes.lastIndex)
            .firstOrNull { flatNodes[it].depth <= node.depth } ?: flatNodes.size
        val blockSize = blockEnd - fromIndex
        val block = flatNodes.subList(fromIndex, blockEnd).toList()

        // Remove the block from the list
        val withoutBlock = flatNodes.toMutableList()
        withoutBlock.subList(fromIndex, blockEnd).clear()

        // Compute where to insert in the shortened list
        val insertAt = if (toIndex > fromIndex) {
            (toIndex - blockSize + 1).coerceIn(0, withoutBlock.size)
        } else {
            toIndex.coerceIn(0, withoutBlock.size)
        }
        withoutBlock.addAll(insertAt, block)

        // Enforce depth context:
        //  - Too deep (blockHead.depth > nodeAbove.depth + 1): clamp to nodeAbove.depth + 1,
        //    reparent block head to nodeAbove.
        //  - Too shallow (blockHead.depth < nodeAbove.depth): adopt nodeAbove.depth,
        //    reparent block head to become a sibling of nodeAbove.
        //  - Same or within one level: no depth change needed.
        val nodeAbove = withoutBlock.getOrNull(insertAt - 1)
        val aboveDepth = nodeAbove?.depth ?: 0
        val minParentId = state.rootNodeId ?: state.documentId
        val blockHeadDepth = withoutBlock[insertAt].depth
        // When there is a node above: allow depth in [aboveDepth, aboveDepth+1].
        // When dropped at the top (no nodeAbove): must be depth=0 (root level).
        val maxAllowedDepth = if (nodeAbove != null) aboveDepth + 1 else 0
        val minAllowedDepth = aboveDepth
        when {
            blockHeadDepth > maxAllowedDepth -> {
                // Too deep: reparent to become a child of nodeAbove
                val depthDelta = blockHeadDepth - maxAllowedDepth
                val newParentId = nodeAbove?.entity?.id ?: minParentId
                for (i in insertAt until insertAt + blockSize) {
                    val n = withoutBlock[i]
                    withoutBlock[i] = n.copy(
                        entity = if (i == insertAt) n.entity.copy(parentId = newParentId) else n.entity,
                        depth = n.depth - depthDelta,
                    )
                }
            }
            blockHeadDepth < minAllowedDepth -> {
                // Too shallow: reparent to become a sibling of nodeAbove
                val depthDelta = blockHeadDepth - minAllowedDepth
                val newParentId = if (aboveDepth == 0) minParentId else nodeAbove!!.entity.parentId
                for (i in insertAt until insertAt + blockSize) {
                    val n = withoutBlock[i]
                    withoutBlock[i] = n.copy(
                        entity = if (i == insertAt) n.entity.copy(parentId = newParentId) else n.entity,
                        depth = n.depth - depthDelta,
                    )
                }
            }
            // else: depth is within [aboveDepth, aboveDepth+1] — no adjustment needed
        }

        // Recompute sort orders and parent IDs, then persist
        val recomputed = recomputeFlatNodes(withoutBlock)

        // Optimistic UI update
        reduce { state.copy(flatNodes = recomputed) }

        // Persist changed nodes to Room
        persistReorderedNodes(flatNodes, recomputed)
        resetInactivityTimer()
    }

    fun indentNode(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val nodeIndex = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (nodeIndex < 0) return@intent

        val node = flatNodes[nodeIndex]

        // Find the previous sibling (same depth and parent) — it becomes the new parent
        val prevSibling = flatNodes.subList(0, nodeIndex)
            .lastOrNull { it.depth == node.depth && it.entity.parentId == node.entity.parentId }
            ?: return@intent

        val result = flatNodes.toMutableList()
        result[nodeIndex] = node.copy(
            entity = node.entity.copy(parentId = prevSibling.entity.id),
            depth = node.depth + 1,
        )
        // Shift all descendants by +1
        var i = nodeIndex + 1
        while (i < result.size && result[i].depth > node.depth) {
            result[i] = result[i].copy(depth = result[i].depth + 1)
            i++
        }

        pushUndoSnapshot(flatNodes)
        val recomputed = recomputeFlatNodes(result)
        reduce { state.copy(flatNodes = recomputed, canUndo = true, canRedo = false) }
        persistReorderedNodes(flatNodes, recomputed)
    }

    fun outdentNode(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val nodeIndex = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (nodeIndex < 0) return@intent

        val node = flatNodes[nodeIndex]
        if (node.depth == 0) return@intent // already at root

        // Find the grandparent id by looking at current parent's parentId
        val parentNode = flatNodes.subList(0, nodeIndex)
            .lastOrNull { it.entity.id == node.entity.parentId }
        val grandparentId = parentNode?.entity?.parentId

        val result = flatNodes.toMutableList()
        result[nodeIndex] = node.copy(
            entity = node.entity.copy(parentId = grandparentId),
            depth = node.depth - 1,
        )
        // Shift all descendants by -1
        var i = nodeIndex + 1
        while (i < result.size && result[i].depth > node.depth) {
            result[i] = result[i].copy(depth = result[i].depth - 1)
            i++
        }

        pushUndoSnapshot(flatNodes)
        val recomputed = recomputeFlatNodes(result)
        reduce { state.copy(flatNodes = recomputed, canUndo = true, canRedo = false) }
        persistReorderedNodes(flatNodes, recomputed)
    }

    fun moveUp(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val nodeIndex = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (nodeIndex <= 0) return@intent
        val node = flatNodes[nodeIndex]

        // Find previous sibling (same parentId, same depth)
        val prevSiblingIndex = (nodeIndex - 1 downTo 0)
            .firstOrNull { flatNodes[it].depth == node.depth && flatNodes[it].entity.parentId == node.entity.parentId }
            ?: return@intent

        val blockEnd = ((nodeIndex + 1)..flatNodes.lastIndex)
            .firstOrNull { flatNodes[it].depth <= node.depth } ?: flatNodes.size
        val block = flatNodes.subList(nodeIndex, blockEnd).toList()
        val withoutBlock = flatNodes.toMutableList()
        withoutBlock.subList(nodeIndex, blockEnd).clear()
        val insertAt = prevSiblingIndex.coerceIn(0, withoutBlock.size)
        withoutBlock.addAll(insertAt, block)

        pushUndoSnapshot(flatNodes)
        val recomputed = recomputeFlatNodes(withoutBlock)
        reduce { state.copy(flatNodes = recomputed, canUndo = true, canRedo = false) }
        persistReorderedNodes(flatNodes, recomputed)
        resetInactivityTimer()
    }

    fun moveDown(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val nodeIndex = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (nodeIndex < 0) return@intent
        val node = flatNodes[nodeIndex]

        val blockEnd = ((nodeIndex + 1)..flatNodes.lastIndex)
            .firstOrNull { flatNodes[it].depth <= node.depth } ?: flatNodes.size
        val blockSize = blockEnd - nodeIndex

        // Find next sibling (same parentId, same depth, at or after blockEnd)
        val nextSiblingIndex = (blockEnd..flatNodes.lastIndex)
            .firstOrNull { flatNodes[it].depth == node.depth && flatNodes[it].entity.parentId == node.entity.parentId }
            ?: return@intent

        val block = flatNodes.subList(nodeIndex, blockEnd).toList()
        val withoutBlock = flatNodes.toMutableList()
        withoutBlock.subList(nodeIndex, blockEnd).clear()
        val insertAt = (nextSiblingIndex - blockSize + 1).coerceIn(0, withoutBlock.size)
        withoutBlock.addAll(insertAt, block)

        pushUndoSnapshot(flatNodes)
        val recomputed = recomputeFlatNodes(withoutBlock)
        reduce { state.copy(flatNodes = recomputed, canUndo = true, canRedo = false) }
        persistReorderedNodes(flatNodes, recomputed)
        resetInactivityTimer()
    }

    fun undo() = intent {
        if (undoStack.isEmpty()) return@intent
        val snapshot = undoStack.removeLast()
        val deletedIds = if (undoDeletedIds.isNotEmpty()) undoDeletedIds.removeLast() else emptyList()
        val current = state.flatNodes
        redoStack.addLast(current)
        reduce { state.copy(flatNodes = snapshot, canUndo = undoStack.isNotEmpty(), canRedo = true) }
        persistReorderedNodes(current, snapshot)
        // Restore soft-deleted nodes for this undo step (only if this was a delete op)
        if (deletedIds.isNotEmpty()) {
            val deviceId = authRepository.getDeviceId().first()
            val hlc = hlcClock.generate(deviceId)
            val now = System.currentTimeMillis()
            nodeDao.restoreNodes(deletedIds, hlc, now)
        }
    }

    fun redo() = intent {
        if (redoStack.isEmpty()) return@intent
        val snapshot = redoStack.removeLast()
        val current = state.flatNodes
        undoStack.addLast(current)
        reduce { state.copy(flatNodes = snapshot, canUndo = true, canRedo = redoStack.isNotEmpty()) }
        persistReorderedNodes(current, snapshot)
    }

    fun openAttachmentPicker() = intent {
        postSideEffect(NodeEditorSideEffect.OpenAttachmentPicker)
    }

    fun uploadAttachment(nodeId: String, uri: Uri, mimeType: String) = intent {
        reduce { state.copy(syncStatus = SyncStatus.Syncing) }
        val result = fileRepository.uploadFile(nodeId, uri, mimeType)
        result.fold(
            onSuccess = { uploaded ->
                val flatNode = state.flatNodes.firstOrNull { it.entity.id == nodeId } ?: return@intent
                val newContent = "${flatNode.entity.content} [${uploaded.filename}](${uploaded.url})".trim()
                onContentChanged(nodeId, newContent)
                reduce { state.copy(syncStatus = SyncStatus.Idle) }
            },
            onFailure = {
                reduce { state.copy(syncStatus = SyncStatus.Error) }
                postSideEffect(NodeEditorSideEffect.ShowError("Upload failed: ${it.message}"))
            }
        )
    }

    fun switchToNote(nodeId: String) = intent {
        if (nodeId in state.expandedNoteIds) {
            reduce { state.copy(expandedNoteIds = state.expandedNoteIds - nodeId) }
            postSideEffect(NodeEditorSideEffect.FocusContent(nodeId))
        } else {
            reduce { state.copy(expandedNoteIds = state.expandedNoteIds + nodeId) }
            postSideEffect(NodeEditorSideEffect.FocusNote(nodeId))
        }
    }

    fun onNodeFocusGained(nodeId: String) = intent {
        reduce { state.copy(focusedNodeId = nodeId) }
    }

    private suspend fun persistReorderedNodes(oldFlatNodes: List<FlatNode>, newFlatNodes: List<FlatNode>) {
        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        val now = System.currentTimeMillis()

        val oldMap = oldFlatNodes.associateBy { it.entity.id }
        for (newFlatNode in newFlatNodes) {
            val oldFlatNode = oldMap[newFlatNode.entity.id] ?: continue
            val oldEntity = oldFlatNode.entity
            val newEntity = newFlatNode.entity

            val sortOrderChanged = oldEntity.sortOrder != newEntity.sortOrder
            val parentIdChanged = oldEntity.parentId != newEntity.parentId

            if (sortOrderChanged || parentIdChanged) {
                nodeDao.updateNode(
                    newEntity.copy(
                        sortOrderHlc = if (sortOrderChanged) hlc else newEntity.sortOrderHlc,
                        parentIdHlc = if (parentIdChanged) hlc else newEntity.parentIdHlc,
                        updatedAt = now,
                        deviceId = deviceId,
                    )
                )
            }
        }
    }

    // --- P4-8: Debounced persistence ---

    private val contentDebounceJobs = mutableMapOf<String, Job>()
    private val noteDebounceJobs = mutableMapOf<String, Job>()

    fun onContentChanged(nodeId: String, newContent: String) {
        // Optimistic in-memory state update
        intent {
            reduce {
                state.copy(
                    flatNodes = state.flatNodes.map {
                        if (it.entity.id == nodeId) it.copy(entity = it.entity.copy(content = newContent))
                        else it
                    }
                )
            }
        }

        // Debounced Room write
        contentDebounceJobs[nodeId]?.cancel()
        contentDebounceJobs[nodeId] = viewModelScope.launch {
            delay(300)
            persistContentChange(nodeId, newContent)
        }
        resetInactivityTimer()
    }

    fun onNoteChanged(nodeId: String, newNote: String) {
        // Optimistic in-memory state update
        intent {
            reduce {
                state.copy(
                    flatNodes = state.flatNodes.map {
                        if (it.entity.id == nodeId) it.copy(entity = it.entity.copy(note = newNote))
                        else it
                    }
                )
            }
        }

        // Debounced Room write
        noteDebounceJobs[nodeId]?.cancel()
        noteDebounceJobs[nodeId] = viewModelScope.launch {
            delay(300)
            persistNoteChange(nodeId, newNote)
        }
        resetInactivityTimer()
    }

    fun onNodeFocusLost(nodeId: String) {
        // Flush any pending content debounce immediately
        contentDebounceJobs[nodeId]?.cancel()
        contentDebounceJobs.remove(nodeId)

        noteDebounceJobs[nodeId]?.cancel()
        noteDebounceJobs.remove(nodeId)

        viewModelScope.launch {
            val entity = nodeDao.getNodesByDocumentSync(container.stateFlow.value.documentId)
                .firstOrNull { it.id == nodeId } ?: return@launch
            val currentState = container.stateFlow.value
            val flatNode = currentState.flatNodes.firstOrNull { it.entity.id == nodeId } ?: return@launch
            val inMemoryEntity = flatNode.entity

            // Only persist if in-memory content/note differs from DB
            if (inMemoryEntity.content != entity.content || inMemoryEntity.note != entity.note) {
                val deviceId = authRepository.getDeviceId().first()
                val hlc = hlcClock.generate(deviceId)
                val now = System.currentTimeMillis()
                nodeDao.updateNode(
                    entity.copy(
                        content = inMemoryEntity.content,
                        contentHlc = if (inMemoryEntity.content != entity.content) hlc else entity.contentHlc,
                        note = inMemoryEntity.note,
                        noteHlc = if (inMemoryEntity.note != entity.note) hlc else entity.noteHlc,
                        updatedAt = now,
                        deviceId = deviceId,
                    )
                )
            }
        }
    }

    private suspend fun persistContentChange(nodeId: String, content: String) {
        val documentId = container.stateFlow.value.documentId
        val entity = nodeDao.getNodesByDocumentSync(documentId)
            .firstOrNull { it.id == nodeId } ?: return
        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        nodeDao.updateNode(
            entity.copy(
                content = content,
                contentHlc = hlc,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
            )
        )
    }

    private suspend fun persistNoteChange(nodeId: String, note: String) {
        val documentId = container.stateFlow.value.documentId
        val entity = nodeDao.getNodesByDocumentSync(documentId)
            .firstOrNull { it.id == nodeId } ?: return
        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        nodeDao.updateNode(
            entity.copy(
                note = note,
                noteHlc = hlc,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
            )
        )
    }

    // --- P4-12: Note editor ---

    fun toggleNote(nodeId: String) = intent {
        val current = state.expandedNoteIds
        val updated = if (nodeId in current) current - nodeId else current + nodeId
        reduce { state.copy(expandedNoteIds = updated) }
    }

    // --- P4-11: Node actions ---

    fun addChildNode(parentNodeId: String) = intent {
        val deviceId = authRepository.getDeviceId().first()
        val now = System.currentTimeMillis()
        val hlc = hlcClock.generate(deviceId)
        val documentId = state.documentId

        // Find existing children of the parent to determine sort order
        val siblings = state.flatNodes.filter { it.entity.parentId == parentNodeId }
        val lastSiblingSortOrder = siblings.maxByOrNull { it.entity.sortOrder }?.entity?.sortOrder

        val newSortOrder = FractionalIndex.generateKeyBetween(lastSiblingSortOrder, null)
        val newNodeId = UUID.randomUUID().toString()

        val parentEntity = state.flatNodes.firstOrNull { it.entity.id == parentNodeId }?.entity
        val newNode = NodeEntity(
            id = newNodeId,
            documentId = documentId,
            userId = parentEntity?.userId ?: "",
            content = "",
            contentHlc = hlc,
            note = "",
            noteHlc = "",
            parentId = parentNodeId,
            parentIdHlc = hlc,
            sortOrder = newSortOrder,
            sortOrderHlc = hlc,
            deviceId = deviceId,
            createdAt = now,
            updatedAt = now,
        )

        nodeDao.insertNode(newNode)
        reduce { state.copy(focusedNodeId = newNodeId) }
    }

    fun deleteNode(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val index = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (index == -1) return@intent

        val deviceId = authRepository.getDeviceId().first()
        val now = System.currentTimeMillis()
        val hlc = hlcClock.generate(deviceId)

        // Collect the node and all its descendants (contiguous in DFS flat list)
        val nodeDepth = flatNodes[index].depth
        val idsToDelete = mutableListOf(nodeId)
        for (i in index + 1 until flatNodes.size) {
            if (flatNodes[i].depth > nodeDepth) idsToDelete.add(flatNodes[i].entity.id)
            else break
        }

        // Push undo snapshot with deleted IDs so toolbar Undo can restore them
        if (undoStack.size >= 50) undoStack.removeFirst()
        undoStack.addLast(flatNodes)
        undoDeletedIds.addLast(idsToDelete)
        redoStack.clear()

        nodeDao.softDeleteNodes(idsToDelete, now, hlc, now)

        val precedingNodeId = if (index > 0) flatNodes[index - 1].entity.id else null
        reduce { state.copy(focusedNodeId = precedingNodeId, canUndo = true, canRedo = false) }
    }

    fun restoreNode(nodeId: String) = intent {
        val node = nodeDao.getNodeById(nodeId).first() ?: return@intent
        val deletedAt = node.deletedAt ?: return@intent
        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        val now = System.currentTimeMillis()
        // Restore all nodes in the same document deleted at the same timestamp (full subtree)
        val allNodes = nodeDao.getNodesByDocumentIncludingDeleted(state.documentId)
        val idsToRestore = allNodes
            .filter { it.deletedAt == deletedAt }
            .map { it.id }
        nodeDao.restoreNodes(idsToRestore, hlc, now)
        resetInactivityTimer()
    }

    private val colorDebounceJobs = mutableMapOf<String, Job>()

    fun onColorChanged(nodeId: String, color: Int) {
        // Optimistic in-memory update
        intent {
            reduce {
                state.copy(
                    flatNodes = state.flatNodes.map {
                        if (it.entity.id == nodeId) it.copy(entity = it.entity.copy(color = color))
                        else it
                    }
                )
            }
        }

        // Debounced Room write
        colorDebounceJobs[nodeId]?.cancel()
        colorDebounceJobs[nodeId] = viewModelScope.launch {
            delay(300)
            persistColorChange(nodeId, color)
        }
        resetInactivityTimer()
    }

    private suspend fun persistColorChange(nodeId: String, color: Int) {
        val documentId = container.stateFlow.value.documentId
        val entity = nodeDao.getNodesByDocumentSync(documentId)
            .firstOrNull { it.id == nodeId } ?: return
        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        nodeDao.updateNode(
            entity.copy(
                color = color,
                colorHlc = hlc,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
            )
        )
    }

    fun onCompletedToggled(nodeId: String) = intent {
        val flatNode = state.flatNodes.firstOrNull { it.entity.id == nodeId } ?: return@intent
        val entity = flatNode.entity
        val newCompleted = if (entity.completed == 0) 1 else 0

        val deviceId = authRepository.getDeviceId().first()
        val hlc = hlcClock.generate(deviceId)
        val now = System.currentTimeMillis()

        nodeDao.updateNode(
            entity.copy(
                completed = newCompleted,
                completedHlc = hlc,
                updatedAt = now,
                deviceId = deviceId,
            )
        )

        // Optimistic update
        reduce {
            state.copy(
                flatNodes = state.flatNodes.map {
                    if (it.entity.id == nodeId) it.copy(entity = it.entity.copy(completed = newCompleted))
                    else it
                }
            )
        }
        resetInactivityTimer()
    }

    // --- P4-13: Sync trigger ---

    private var inactivityTimerJob: Job? = null

    private fun triggerSync() = intent {
        try {
            reduce { state.copy(syncStatus = SyncStatus.Syncing) }

            val deviceId = authRepository.getDeviceId().first()
            val lastSyncHlc = dataStore.data.map { prefs ->
                prefs[SyncConstants.LAST_SYNC_HLC_KEY] ?: "0"
            }.first()

            // Pull changes from server
            val pullResult = syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)
            pullResult.getOrThrow().let { response ->
                if (response.nodes.isNotEmpty()) {
                    nodeDao.upsertNodes(response.nodes.map { it.toNodeEntity() })
                }
                if (response.documents.isNotEmpty()) {
                    documentDao.upsertDocuments(response.documents.map { it.toDocumentEntity() })
                }
                if (response.bookmarks.isNotEmpty()) {
                    bookmarkDao.upsertBookmarks(response.bookmarks.map { it.toBookmarkEntity() })
                }
                response.settings?.let { settings ->
                    settingsDao.upsertSettings(settings.toSettingsEntity())
                }
            }

            // Build and push local changes
            val userId = authRepository.getUserId().filterNotNull().first()
            val pendingNodes = nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingDocs = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingBookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingSettings = settingsDao.getPendingSettings(userId, lastSyncHlc, deviceId)

            val pushPayload = SyncPushPayload(
                deviceId = deviceId,
                nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
                documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
                bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null },
                settings = pendingSettings?.toSettingsSyncRecord()
            )

            val pushResult = syncRepository.push(pushPayload)
            pushResult.getOrThrow().let { response ->
                if (response.conflicts.nodes.isNotEmpty()) {
                    nodeDao.upsertNodes(response.conflicts.nodes.map { it.toNodeEntity() })
                }
                if (response.conflicts.documents.isNotEmpty()) {
                    documentDao.upsertDocuments(response.conflicts.documents.map { it.toDocumentEntity() })
                }
                if (response.conflicts.bookmarks.isNotEmpty()) {
                    bookmarkDao.upsertBookmarks(response.conflicts.bookmarks.map { it.toBookmarkEntity() })
                }
                response.conflicts.settings?.let { settings ->
                    settingsDao.upsertSettings(settings.toSettingsEntity())
                }

                dataStore.edit { prefs ->
                    prefs[SyncConstants.LAST_SYNC_HLC_KEY] = response.serverHlc
                }
            }

            reduce { state.copy(syncStatus = SyncStatus.Idle) }
        } catch (_: Exception) {
            reduce { state.copy(syncStatus = SyncStatus.Error) }
        }
    }

    fun onScreenResumed() {
        viewModelScope.launch {
            triggerSync()
        }
        resetInactivityTimer()
    }

    fun onScreenPaused() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = null
    }

    fun resetInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = viewModelScope.launch {
            delay(30_000)
            triggerSync()
        }
    }

    companion object {

        /**
         * Returns only the descendant nodes of [rootNodeId] (not rootNodeId itself).
         * Uses BFS from rootNodeId to collect all descendant IDs, then filters the node list.
         * The returned list is suitable for passing to mapToFlatList(result, rootNodeId).
         */
        fun filterSubtree(nodes: List<NodeEntity>, rootNodeId: String): List<NodeEntity> {
            val childrenMap = nodes
                .filter { it.deletedAt == null }
                .groupBy { it.parentId }

            val included = mutableSetOf<String>()
            val queue = ArrayDeque<String>()

            // Start BFS from rootNodeId's children (not rootNodeId itself)
            childrenMap[rootNodeId]?.forEach { child ->
                queue.add(child.id)
            }

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                included.add(current)
                childrenMap[current]?.forEach { child ->
                    if (child.id !in included) queue.add(child.id)
                }
            }

            return nodes.filter { it.id in included }
        }

        fun recomputeFlatNodes(nodes: List<FlatNode>): List<FlatNode> {
            // Collect indices per parent, in flat-list order (= correct sibling order)
            val parentToIndices = linkedMapOf<String?, MutableList<Int>>()
            nodes.forEachIndexed { idx, node ->
                parentToIndices.getOrPut(node.entity.parentId) { mutableListOf() }.add(idx)
            }

            val result = nodes.toMutableList()
            for ((_, indices) in parentToIndices) {
                indices.forEachIndexed { rank, nodeIdx ->
                    val charIdx = rank.coerceAtMost(FractionalIndex.DIGITS.length - 1)
                    val newSortOrder = "a${FractionalIndex.DIGITS[charIdx]}"
                    val current = result[nodeIdx]
                    result[nodeIdx] = current.copy(
                        entity = current.entity.copy(sortOrder = newSortOrder),
                    )
                }
            }
            return result
        }
    }
}

data class NodeEditorUiState(
    val status: NodeEditorStatus = NodeEditorStatus.Loading,
    val flatNodes: List<FlatNode> = emptyList(),
    val focusedNodeId: String? = null,
    val documentId: String = "",
    val rootNodeId: String? = null,
    val documentTitle: String = "",
    val rootNodeContent: String? = null,
    val expandedNoteIds: Set<String> = emptySet(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed class NodeEditorStatus {
    data object Loading : NodeEditorStatus()
    data object Success : NodeEditorStatus()
    data class Error(val message: String) : NodeEditorStatus()
}

sealed class NodeEditorSideEffect {
    data object NavigateUp : NodeEditorSideEffect()
    data class ShowError(val message: String) : NodeEditorSideEffect()
    data class FocusNote(val nodeId: String) : NodeEditorSideEffect()
    data class FocusContent(val nodeId: String) : NodeEditorSideEffect()
    data object OpenAttachmentPicker : NodeEditorSideEffect()
}

enum class FocusDirection { Up, Down }
