package com.gmaingret.outlinergod.prototype

/**
 * A single node in the flattened tree representation.
 *
 * @param id        Unique identifier
 * @param content   Display text
 * @param depth     Indentation level (0 = root)
 * @param parentId  Id of the parent node, or null for root-level nodes
 * @param sortOrder Fractional-index string for ordering among siblings
 */
data class FlatNode(
    val id: String,
    val content: String,
    val depth: Int,
    val parentId: String?,
    val sortOrder: String,
)
