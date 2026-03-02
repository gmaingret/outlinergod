package com.gmaingret.outlinergod.ui.mapper

import com.gmaingret.outlinergod.db.entity.NodeEntity

data class FlatNode(
    val entity: NodeEntity,
    val depth: Int,
    val hasChildren: Boolean,
)
