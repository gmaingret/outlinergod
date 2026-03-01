/**
 * P2-15 / P2-16: Sync routes (full Phase 2 implementation)
 *
 * GET  /api/sync/changes  — pull changes since a given HLC
 * POST /api/sync/changes  — push locally pending changes (per-field LWW merge)
 *
 * Replaces P0-3 prototype with real auth, user_id scoping, and all entity types.
 */
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import { requireAuth } from '../middleware/auth.js'
import {
  mergeNodes,
  mergeDocuments,
  mergeBookmarks,
  type NodeSyncRecord,
  type DocumentSyncRecord,
  type BookmarkSyncRecord,
} from '../merge.js'
import { hlcGenerate, hlcReceive } from '../hlc/hlc.js'

// ---------------------------------------------------------------------------
// Settings sync record (no mergeSettings in merge.ts — handled inline)
// ---------------------------------------------------------------------------

export interface SettingsSyncRecord {
  id: string
  user_id: string
  theme: string
  theme_hlc: string
  density: string
  density_hlc: string
  show_guide_lines: number
  show_guide_lines_hlc: string
  show_backlink_badge: number
  show_backlink_badge_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const SERVER_DEVICE_ID = 'server'
const BODY_LIMIT = 5 * 1024 * 1024 // 5 MB

// ---------------------------------------------------------------------------
// Node DB helpers
// ---------------------------------------------------------------------------

function upsertNode(sqlite: InstanceType<typeof Database>, rec: NodeSyncRecord & { user_id: string }): void {
  sqlite
    .prepare(
      `INSERT INTO nodes (
        id, document_id, user_id, content, content_hlc, note, note_hlc,
        parent_id, parent_id_hlc, sort_order, sort_order_hlc,
        completed, completed_hlc, color, color_hlc,
        collapsed, collapsed_hlc, deleted_at, deleted_hlc, device_id,
        created_at, updated_at
      ) VALUES (
        @id, @document_id, @user_id, @content, @content_hlc, @note, @note_hlc,
        @parent_id, @parent_id_hlc, @sort_order, @sort_order_hlc,
        @completed, @completed_hlc, @color, @color_hlc,
        @collapsed, @collapsed_hlc, @deleted_at, @deleted_hlc, @device_id,
        @created_at, @updated_at
      )
      ON CONFLICT(id) DO UPDATE SET
        document_id    = excluded.document_id,
        content        = excluded.content,
        content_hlc    = excluded.content_hlc,
        note           = excluded.note,
        note_hlc       = excluded.note_hlc,
        parent_id      = excluded.parent_id,
        parent_id_hlc  = excluded.parent_id_hlc,
        sort_order     = excluded.sort_order,
        sort_order_hlc = excluded.sort_order_hlc,
        completed      = excluded.completed,
        completed_hlc  = excluded.completed_hlc,
        color          = excluded.color,
        color_hlc      = excluded.color_hlc,
        collapsed      = excluded.collapsed,
        collapsed_hlc  = excluded.collapsed_hlc,
        deleted_at     = excluded.deleted_at,
        deleted_hlc    = excluded.deleted_hlc,
        device_id      = excluded.device_id,
        updated_at     = excluded.updated_at`,
    )
    .run(rec)
}

function upsertDocument(sqlite: InstanceType<typeof Database>, rec: DocumentSyncRecord): void {
  sqlite
    .prepare(
      `INSERT INTO documents (
        id, user_id, title, title_hlc, type, parent_id, parent_id_hlc,
        sort_order, sort_order_hlc, collapsed, collapsed_hlc,
        deleted_at, deleted_hlc, device_id, created_at, updated_at
      ) VALUES (
        @id, @user_id, @title, @title_hlc, @type, @parent_id, @parent_id_hlc,
        @sort_order, @sort_order_hlc, @collapsed, @collapsed_hlc,
        @deleted_at, @deleted_hlc, @device_id, @created_at, @updated_at
      )
      ON CONFLICT(id) DO UPDATE SET
        title          = excluded.title,
        title_hlc      = excluded.title_hlc,
        parent_id      = excluded.parent_id,
        parent_id_hlc  = excluded.parent_id_hlc,
        sort_order     = excluded.sort_order,
        sort_order_hlc = excluded.sort_order_hlc,
        collapsed      = excluded.collapsed,
        collapsed_hlc  = excluded.collapsed_hlc,
        deleted_at     = excluded.deleted_at,
        deleted_hlc    = excluded.deleted_hlc,
        device_id      = excluded.device_id,
        updated_at     = excluded.updated_at`,
    )
    .run(rec)
}

function upsertBookmark(sqlite: InstanceType<typeof Database>, rec: BookmarkSyncRecord): void {
  sqlite
    .prepare(
      `INSERT INTO bookmarks (
        id, user_id, node_id, document_id, sort_order, sort_order_hlc,
        deleted_at, deleted_hlc, device_id, created_at, updated_at
      ) VALUES (
        @id, @user_id, @node_id, @document_id, @sort_order, @sort_order_hlc,
        @deleted_at, @deleted_hlc, @device_id, @created_at, @updated_at
      )
      ON CONFLICT(id) DO UPDATE SET
        sort_order     = excluded.sort_order,
        sort_order_hlc = excluded.sort_order_hlc,
        deleted_at     = excluded.deleted_at,
        deleted_hlc    = excluded.deleted_hlc,
        device_id      = excluded.device_id,
        updated_at     = excluded.updated_at`,
    )
    .run(rec)
}

function upsertSettings(sqlite: InstanceType<typeof Database>, rec: SettingsSyncRecord): void {
  sqlite
    .prepare(
      `INSERT INTO settings (
        id, user_id, theme, theme_hlc, density, density_hlc,
        show_guide_lines, show_guide_lines_hlc, show_backlink_badge, show_backlink_badge_hlc,
        device_id, created_at, updated_at
      ) VALUES (
        @id, @user_id, @theme, @theme_hlc, @density, @density_hlc,
        @show_guide_lines, @show_guide_lines_hlc, @show_backlink_badge, @show_backlink_badge_hlc,
        @device_id, @created_at, @updated_at
      )
      ON CONFLICT(id) DO UPDATE SET
        theme                = excluded.theme,
        theme_hlc            = excluded.theme_hlc,
        density              = excluded.density,
        density_hlc          = excluded.density_hlc,
        show_guide_lines     = excluded.show_guide_lines,
        show_guide_lines_hlc = excluded.show_guide_lines_hlc,
        show_backlink_badge  = excluded.show_backlink_badge,
        show_backlink_badge_hlc = excluded.show_backlink_badge_hlc,
        device_id            = excluded.device_id,
        updated_at           = excluded.updated_at`,
    )
    .run(rec)
}

// ---------------------------------------------------------------------------
// Per-field LWW merge for settings (inline — no mergeSettings in merge.ts)
// ---------------------------------------------------------------------------

function mergeSettings(stored: SettingsSyncRecord, incoming: SettingsSyncRecord): SettingsSyncRecord {
  const theme = stored.theme_hlc >= incoming.theme_hlc ? stored.theme : incoming.theme
  const theme_hlc = stored.theme_hlc >= incoming.theme_hlc ? stored.theme_hlc : incoming.theme_hlc

  const density = stored.density_hlc >= incoming.density_hlc ? stored.density : incoming.density
  const density_hlc = stored.density_hlc >= incoming.density_hlc ? stored.density_hlc : incoming.density_hlc

  const show_guide_lines = stored.show_guide_lines_hlc >= incoming.show_guide_lines_hlc ? stored.show_guide_lines : incoming.show_guide_lines
  const show_guide_lines_hlc = stored.show_guide_lines_hlc >= incoming.show_guide_lines_hlc ? stored.show_guide_lines_hlc : incoming.show_guide_lines_hlc

  const show_backlink_badge = stored.show_backlink_badge_hlc >= incoming.show_backlink_badge_hlc ? stored.show_backlink_badge : incoming.show_backlink_badge
  const show_backlink_badge_hlc = stored.show_backlink_badge_hlc >= incoming.show_backlink_badge_hlc ? stored.show_backlink_badge_hlc : incoming.show_backlink_badge_hlc

  // device_id: whichever record has the higher max HLC
  const hlcs = [stored.theme_hlc, stored.density_hlc, stored.show_guide_lines_hlc, stored.show_backlink_badge_hlc]
  const maxStored = hlcs.reduce((a, b) => (a >= b ? a : b))
  const iHlcs = [incoming.theme_hlc, incoming.density_hlc, incoming.show_guide_lines_hlc, incoming.show_backlink_badge_hlc]
  const maxIncoming = iHlcs.reduce((a, b) => (a >= b ? a : b))
  const device_id = maxIncoming > maxStored ? incoming.device_id : stored.device_id

  return {
    id: stored.id,
    user_id: stored.user_id,
    theme,
    theme_hlc,
    density,
    density_hlc,
    show_guide_lines,
    show_guide_lines_hlc,
    show_backlink_badge,
    show_backlink_badge_hlc,
    device_id,
    created_at: Math.min(stored.created_at, incoming.created_at),
    updated_at: Math.max(stored.updated_at, incoming.updated_at),
  }
}

/**
 * Check if incoming won every field against stored (fully accepted).
 */
function isNodeFullyAccepted(incoming: NodeSyncRecord, stored: NodeSyncRecord): boolean {
  return (
    incoming.content_hlc >= stored.content_hlc &&
    incoming.note_hlc >= stored.note_hlc &&
    incoming.parent_id_hlc >= stored.parent_id_hlc &&
    incoming.sort_order_hlc >= stored.sort_order_hlc &&
    incoming.completed_hlc >= stored.completed_hlc &&
    incoming.color_hlc >= stored.color_hlc &&
    incoming.collapsed_hlc >= stored.collapsed_hlc &&
    incoming.deleted_hlc >= stored.deleted_hlc
  )
}

function isDocumentFullyAccepted(incoming: DocumentSyncRecord, stored: DocumentSyncRecord): boolean {
  return (
    incoming.title_hlc >= stored.title_hlc &&
    incoming.parent_id_hlc >= stored.parent_id_hlc &&
    incoming.sort_order_hlc >= stored.sort_order_hlc &&
    incoming.collapsed_hlc >= stored.collapsed_hlc &&
    incoming.deleted_hlc >= stored.deleted_hlc
  )
}

function isBookmarkFullyAccepted(incoming: BookmarkSyncRecord, stored: BookmarkSyncRecord): boolean {
  return (
    incoming.sort_order_hlc >= stored.sort_order_hlc &&
    incoming.deleted_hlc >= stored.deleted_hlc
  )
}

function isSettingsFullyAccepted(incoming: SettingsSyncRecord, stored: SettingsSyncRecord): boolean {
  return (
    incoming.theme_hlc >= stored.theme_hlc &&
    incoming.density_hlc >= stored.density_hlc &&
    incoming.show_guide_lines_hlc >= stored.show_guide_lines_hlc &&
    incoming.show_backlink_badge_hlc >= stored.show_backlink_badge_hlc
  )
}

// ---------------------------------------------------------------------------
// Route plugin
// ---------------------------------------------------------------------------

export function createSyncRoutes(sqlite: InstanceType<typeof Database>) {
  return async function syncRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET /sync/changes — pull changes since a given HLC
    // -----------------------------------------------------------------------
    fastify.get('/sync/changes', { preHandler: requireAuth }, async (req, reply) => {
      const query = req.query as Record<string, string>
      const since = query.since
      const deviceId = query.device_id

      if (!since || !deviceId) {
        return reply.status(400).send({ error: 'Missing required parameters' })
      }

      const userId = req.user!.id

      // Nodes: any HLC column > since AND device_id != requester
      const nodes = sqlite
        .prepare(
          `SELECT * FROM nodes
           WHERE user_id = ?
           AND (content_hlc > ? OR note_hlc > ? OR parent_id_hlc > ? OR sort_order_hlc > ?
                OR completed_hlc > ? OR color_hlc > ? OR collapsed_hlc > ? OR deleted_hlc > ?)
           AND device_id != ?`,
        )
        .all(userId, since, since, since, since, since, since, since, since, deviceId) as NodeSyncRecord[]

      // Documents: any HLC column > since AND device_id != requester
      const documents = sqlite
        .prepare(
          `SELECT * FROM documents
           WHERE user_id = ?
           AND (title_hlc > ? OR parent_id_hlc > ? OR sort_order_hlc > ?
                OR collapsed_hlc > ? OR deleted_hlc > ?)
           AND device_id != ?`,
        )
        .all(userId, since, since, since, since, since, deviceId) as DocumentSyncRecord[]

      // Settings: single row, null if none or no changes
      const settingsRow = sqlite
        .prepare(
          `SELECT * FROM settings
           WHERE user_id = ?
           AND (theme_hlc > ? OR density_hlc > ? OR show_guide_lines_hlc > ? OR show_backlink_badge_hlc > ?)
           AND device_id != ?`,
        )
        .get(userId, since, since, since, since, deviceId) as SettingsSyncRecord | undefined

      // Bookmarks: any HLC column > since AND device_id != requester
      const bookmarks = sqlite
        .prepare(
          `SELECT * FROM bookmarks
           WHERE user_id = ?
           AND (sort_order_hlc > ? OR deleted_hlc > ?)
           AND device_id != ?`,
        )
        .all(userId, since, since, deviceId) as BookmarkSyncRecord[]

      const server_hlc = hlcGenerate(SERVER_DEVICE_ID)

      return {
        server_hlc,
        nodes,
        documents,
        settings: settingsRow ?? null,
        bookmarks,
      }
    })

    // -----------------------------------------------------------------------
    // POST /sync/changes — push locally pending changes (per-field LWW merge)
    // -----------------------------------------------------------------------
    fastify.post('/sync/changes', { preHandler: requireAuth }, async (req, reply) => {
      // Check payload size via content-length header
      const contentLength = parseInt(req.headers['content-length'] ?? '0')
      if (contentLength > BODY_LIMIT) {
        return reply.status(413).send({ error: 'Payload too large' })
      }

      const body = req.body as {
        device_id?: string
        nodes?: NodeSyncRecord[]
        documents?: DocumentSyncRecord[]
        bookmarks?: BookmarkSyncRecord[]
        settings?: SettingsSyncRecord | null
      }

      if (!body.device_id) {
        return reply.status(400).send({ error: 'Missing device_id' })
      }

      const userId = req.user!.id
      const now = Date.now()
      const incomingHlcs: string[] = []

      // --- Nodes ---
      const acceptedNodeIds: string[] = []
      const conflictNodes: NodeSyncRecord[] = []

      for (const incoming of body.nodes ?? []) {
        // Collect HLCs for receive()
        incomingHlcs.push(
          incoming.content_hlc, incoming.note_hlc, incoming.parent_id_hlc,
          incoming.sort_order_hlc, incoming.completed_hlc, incoming.color_hlc,
          incoming.collapsed_hlc, incoming.deleted_hlc,
        )

        const stored = sqlite
          .prepare('SELECT * FROM nodes WHERE id = ? AND user_id = ?')
          .get(incoming.id, userId) as (NodeSyncRecord & { user_id: string; created_at: number; updated_at: number }) | undefined

        if (!stored) {
          // New node: insert with user_id
          upsertNode(sqlite, {
            ...incoming,
            user_id: userId,
            created_at: (incoming as unknown as { created_at?: number }).created_at ?? now,
            updated_at: now,
          } as NodeSyncRecord & { user_id: string })
          acceptedNodeIds.push(incoming.id)
        } else {
          const merged = mergeNodes(stored as unknown as NodeSyncRecord, incoming)
          upsertNode(sqlite, { ...merged, user_id: userId, created_at: stored.created_at, updated_at: now } as NodeSyncRecord & { user_id: string })

          if (isNodeFullyAccepted(incoming, stored as unknown as NodeSyncRecord)) {
            acceptedNodeIds.push(incoming.id)
          } else {
            conflictNodes.push(merged)
          }
        }
      }

      // --- Documents ---
      const acceptedDocumentIds: string[] = []
      const conflictDocuments: DocumentSyncRecord[] = []

      for (const incoming of body.documents ?? []) {
        incomingHlcs.push(
          incoming.title_hlc, incoming.parent_id_hlc, incoming.sort_order_hlc,
          incoming.collapsed_hlc, incoming.deleted_hlc,
        )

        const stored = sqlite
          .prepare('SELECT * FROM documents WHERE id = ? AND user_id = ?')
          .get(incoming.id, userId) as DocumentSyncRecord | undefined

        if (!stored) {
          upsertDocument(sqlite, { ...incoming, user_id: userId, created_at: incoming.created_at ?? now, updated_at: now })
          acceptedDocumentIds.push(incoming.id)
        } else {
          const merged = mergeDocuments(stored, incoming)
          upsertDocument(sqlite, { ...merged, user_id: userId, updated_at: now })

          if (isDocumentFullyAccepted(incoming, stored)) {
            acceptedDocumentIds.push(incoming.id)
          } else {
            conflictDocuments.push(merged)
          }
        }
      }

      // --- Bookmarks ---
      const acceptedBookmarkIds: string[] = []
      const conflictBookmarks: BookmarkSyncRecord[] = []

      for (const incoming of body.bookmarks ?? []) {
        incomingHlcs.push(incoming.sort_order_hlc, incoming.deleted_hlc)

        const stored = sqlite
          .prepare('SELECT * FROM bookmarks WHERE id = ? AND user_id = ?')
          .get(incoming.id, userId) as BookmarkSyncRecord | undefined

        if (!stored) {
          upsertBookmark(sqlite, { ...incoming, user_id: userId, created_at: incoming.created_at ?? now, updated_at: now })
          acceptedBookmarkIds.push(incoming.id)
        } else {
          const merged = mergeBookmarks(stored, incoming)
          upsertBookmark(sqlite, { ...merged, user_id: userId, updated_at: now })

          if (isBookmarkFullyAccepted(incoming, stored)) {
            acceptedBookmarkIds.push(incoming.id)
          } else {
            conflictBookmarks.push(merged)
          }
        }
      }

      // --- Settings ---
      let conflictSettings: SettingsSyncRecord | null = null

      if (body.settings) {
        const incoming = body.settings
        incomingHlcs.push(incoming.theme_hlc, incoming.density_hlc, incoming.show_guide_lines_hlc, incoming.show_backlink_badge_hlc)

        const stored = sqlite
          .prepare('SELECT * FROM settings WHERE user_id = ?')
          .get(userId) as SettingsSyncRecord | undefined

        if (!stored) {
          upsertSettings(sqlite, {
            ...incoming,
            user_id: userId,
            created_at: incoming.created_at ?? now,
            updated_at: now,
          })
        } else {
          const merged = mergeSettings(stored, incoming)
          upsertSettings(sqlite, { ...merged, updated_at: now })

          if (!isSettingsFullyAccepted(incoming, stored)) {
            conflictSettings = merged
          }
        }
      }

      // Advance server HLC past all incoming HLCs
      for (const hlc of incomingHlcs) {
        if (hlc) hlcReceive(hlc, SERVER_DEVICE_ID)
      }

      const server_hlc = hlcGenerate(SERVER_DEVICE_ID)

      return {
        server_hlc,
        accepted_node_ids: acceptedNodeIds,
        accepted_document_ids: acceptedDocumentIds,
        accepted_bookmark_ids: acceptedBookmarkIds,
        conflicts: {
          nodes: conflictNodes,
          documents: conflictDocuments,
          bookmarks: conflictBookmarks,
          settings: conflictSettings,
        },
      }
    })
  }
}
