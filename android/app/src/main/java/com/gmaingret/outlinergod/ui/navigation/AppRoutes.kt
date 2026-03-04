package com.gmaingret.outlinergod.ui.navigation

object AppRoutes {
    const val LOGIN = "login"
    const val DOCUMENT_LIST = "document_list"
    const val DOCUMENT_DETAIL = "document_detail/{documentId}"
    const val NODE_EDITOR = "node_editor/{documentId}?rootNodeId={rootNodeId}"
    const val SETTINGS = "settings"
    const val BOOKMARKS = "bookmarks"
    const val SEARCH = "search"

    fun nodeEditor(documentId: String, rootNodeId: String? = null): String {
        val base = "node_editor/$documentId"
        return if (rootNodeId != null) "$base?rootNodeId=$rootNodeId" else base
    }
}
