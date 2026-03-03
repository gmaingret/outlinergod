package com.gmaingret.outlinergod.sync

import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.network.model.BookmarkSyncRecord
import com.gmaingret.outlinergod.network.model.DocumentSyncRecord
import com.gmaingret.outlinergod.network.model.NodeSyncRecord
import com.gmaingret.outlinergod.network.model.SettingsSyncRecord

fun NodeSyncRecord.toNodeEntity() = NodeEntity(
    id = id,
    documentId = documentId,
    userId = userId,
    content = content,
    contentHlc = contentHlc,
    note = note,
    noteHlc = noteHlc,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    completed = completed,
    completedHlc = completedHlc,
    color = color,
    colorHlc = colorHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DocumentSyncRecord.toDocumentEntity() = DocumentEntity(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    type = type,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BookmarkSyncRecord.toBookmarkEntity() = BookmarkEntity(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    targetType = targetType,
    targetTypeHlc = targetTypeHlc,
    targetDocumentId = targetDocumentId,
    targetDocumentIdHlc = targetDocumentIdHlc,
    targetNodeId = targetNodeId,
    targetNodeIdHlc = targetNodeIdHlc,
    query = query,
    queryHlc = queryHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SettingsSyncRecord.toSettingsEntity() = SettingsEntity(
    userId = userId,
    theme = theme,
    themeHlc = themeHlc,
    density = density,
    densityHlc = densityHlc,
    showGuideLines = showGuideLines,
    showGuideLinesHlc = showGuideLinesHlc,
    showBacklinkBadge = showBacklinkBadge,
    showBacklinkBadgeHlc = showBacklinkBadgeHlc,
    deviceId = deviceId,
    updatedAt = updatedAt
)

fun NodeEntity.toNodeSyncRecord() = NodeSyncRecord(
    id = id,
    documentId = documentId,
    userId = userId,
    content = content,
    contentHlc = contentHlc,
    note = note,
    noteHlc = noteHlc,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    completed = completed,
    completedHlc = completedHlc,
    color = color,
    colorHlc = colorHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DocumentEntity.toDocumentSyncRecord() = DocumentSyncRecord(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    type = type,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BookmarkEntity.toBookmarkSyncRecord() = BookmarkSyncRecord(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    targetType = targetType,
    targetTypeHlc = targetTypeHlc,
    targetDocumentId = targetDocumentId,
    targetDocumentIdHlc = targetDocumentIdHlc,
    targetNodeId = targetNodeId,
    targetNodeIdHlc = targetNodeIdHlc,
    query = query,
    queryHlc = queryHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SettingsEntity.toSettingsSyncRecord() = SettingsSyncRecord(
    id = userId,
    userId = userId,
    theme = theme,
    themeHlc = themeHlc,
    density = density,
    densityHlc = densityHlc,
    showGuideLines = showGuideLines,
    showGuideLinesHlc = showGuideLinesHlc,
    showBacklinkBadge = showBacklinkBadge,
    showBacklinkBadgeHlc = showBacklinkBadgeHlc,
    deviceId = deviceId,
    updatedAt = updatedAt
)
