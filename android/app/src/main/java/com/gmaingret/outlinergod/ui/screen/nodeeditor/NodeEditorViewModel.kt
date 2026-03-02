package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.prototype.FractionalIndex
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.ui.mapper.FlatNode
import com.gmaingret.outlinergod.ui.mapper.mapToFlatList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
) : ViewModel(), ContainerHost<NodeEditorUiState, NodeEditorSideEffect> {

    override val container = container<NodeEditorUiState, NodeEditorSideEffect>(NodeEditorUiState())

    fun loadDocument(documentId: String) = intent {
        reduce { state.copy(documentId = documentId, status = NodeEditorStatus.Loading) }
        nodeDao.getNodesByDocument(documentId).collect { nodes ->
            val flatNodes = mapToFlatList(nodes, documentId)
            reduce {
                state.copy(
                    status = NodeEditorStatus.Success,
                    flatNodes = flatNodes,
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

        // Recompute sort orders and parent IDs, then persist
        val recomputed = recomputeFlatNodes(withoutBlock)

        // Optimistic UI update
        reduce { state.copy(flatNodes = recomputed) }

        // Persist changed nodes to Room
        persistReorderedNodes(flatNodes, recomputed)
    }

    fun indentNode(nodeId: String) = intent {
        val flatNodes = state.flatNodes
        val nodeIndex = flatNodes.indexOfFirst { it.entity.id == nodeId }
        if (nodeIndex <= 0) return@intent

        val node = flatNodes[nodeIndex]
        val predecessor = flatNodes[nodeIndex - 1]

        // Can only indent if predecessor is at same depth (becomes new parent)
        if (predecessor.depth != node.depth) return@intent

        val result = flatNodes.toMutableList()
        result[nodeIndex] = node.copy(
            entity = node.entity.copy(parentId = predecessor.entity.id),
            depth = node.depth + 1,
        )
        // Shift all descendants by +1
        var i = nodeIndex + 1
        while (i < result.size && result[i].depth > node.depth) {
            result[i] = result[i].copy(depth = result[i].depth + 1)
            i++
        }

        val recomputed = recomputeFlatNodes(result)
        reduce { state.copy(flatNodes = recomputed) }
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

        val recomputed = recomputeFlatNodes(result)
        reduce { state.copy(flatNodes = recomputed) }
        persistReorderedNodes(flatNodes, recomputed)
    }

    companion object {
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

    // --- P4-11: Context menu actions ---

    fun showContextMenu(nodeId: String) = intent {
        reduce { state.copy(contextMenuNodeId = nodeId) }
    }

    fun dismissContextMenu() = intent {
        reduce { state.copy(contextMenuNodeId = null) }
    }

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

        nodeDao.softDeleteNode(nodeId, now, hlc, now)

        val precedingNodeId = if (index > 0) flatNodes[index - 1].entity.id else null
        reduce { state.copy(focusedNodeId = precedingNodeId) }
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
    }
}

data class NodeEditorUiState(
    val status: NodeEditorStatus = NodeEditorStatus.Loading,
    val flatNodes: List<FlatNode> = emptyList(),
    val focusedNodeId: String? = null,
    val documentId: String = "",
    val contextMenuNodeId: String? = null,
    val expandedNoteIds: Set<String> = emptySet(),
)

sealed class NodeEditorStatus {
    data object Loading : NodeEditorStatus()
    data object Success : NodeEditorStatus()
    data class Error(val message: String) : NodeEditorStatus()
}

sealed class NodeEditorSideEffect {
    data object NavigateUp : NodeEditorSideEffect()
    data class ShowError(val message: String) : NodeEditorSideEffect()
}

enum class FocusDirection { Up, Down }
