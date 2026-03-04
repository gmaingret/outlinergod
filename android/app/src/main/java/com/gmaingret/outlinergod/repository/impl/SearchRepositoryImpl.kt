package com.gmaingret.outlinergod.repository.impl

import androidx.sqlite.db.SimpleSQLiteQuery
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.repository.SearchRepository
import com.gmaingret.outlinergod.search.SearchQueryParser
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val nodeDao: NodeDao
) : SearchRepository {

    override suspend fun searchNodes(query: String, userId: String): List<NodeEntity> {
        val parsed = SearchQueryParser.parse(query)

        // Guard: if there are no FTS terms, running an empty MATCH throws SQLite exception
        if (parsed.ftsTerms.isBlank()) return emptyList()

        val sql = buildString {
            append(
                """SELECT n.* FROM nodes n
                   JOIN nodes_fts fts ON n.rowid = fts.rowid
                   WHERE nodes_fts MATCH ?
                   AND n.user_id = ?
                   AND n.deleted_at IS NULL"""
            )
            if (parsed.isCompleted != null) {
                append(" AND n.completed = ${if (parsed.isCompleted) 1 else 0}")
            }
            if (parsed.color != null) {
                append(" AND n.color = ${parsed.color}")
            }
            append(" ORDER BY n.updated_at DESC")
        }

        val rawQuery = SimpleSQLiteQuery(sql, arrayOf(parsed.ftsTerms, userId))
        val results = nodeDao.searchFts(rawQuery)

        // Post-filter for in:note and in:title
        // Strip trailing '*' that was added for prefix matching before doing contains()
        val matchTerm = parsed.ftsTerms.removeSuffix("*")
        return when {
            parsed.inNote -> results.filter { it.note.contains(matchTerm, ignoreCase = true) }
            parsed.inTitle -> results.filter { it.content.contains(matchTerm, ignoreCase = true) }
            else -> results
        }
    }
}
