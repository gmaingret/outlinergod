package com.gmaingret.outlinergod.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * P0-1 DnD Prototype — standalone Activity (demo only, not unit-tested).
 *
 * Renders the demo tree as a flat LazyColumn with depth-based indentation.
 * Vertical reordering uses sh.calvin.reorderable (long-press handle).
 * Indent/Outdent buttons simulate the horizontal-drag gesture.
 *
 * All business logic lives in DndPrototypeViewModel companion object.
 */
class DndPrototypeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = DndPrototypeViewModel()
        setContent {
            MaterialTheme {
                DndPrototypeScreen(viewModel)
            }
        }
    }
}

@Composable
private fun DndPrototypeScreen(viewModel: DndPrototypeViewModel) {
    val nodes by viewModel.nodes.collectAsState()
    val lazyListState = rememberLazyListState()

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(nodes, key = { _, node -> node.id }) { index, node ->
            ReorderableItem(reorderState, key = node.id) { isDragging ->
                Surface(
                    tonalElevation = if (isDragging) 4.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = (node.depth * 24 + 8).dp,
                                end = 8.dp,
                                top = 4.dp,
                                bottom = 4.dp,
                            ),
                    ) {
                        // Long-press drag handle
                        Text(
                            text = "⋮⋮",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .padding(end = 8.dp),
                        )

                        // Outdent button
                        TextButton(
                            onClick = { viewModel.onHorizontalDrag(index, -49f) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("←") }

                        // Indent button
                        TextButton(
                            onClick = { viewModel.onHorizontalDrag(index, 49f) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("→") }

                        Text(
                            text = node.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
