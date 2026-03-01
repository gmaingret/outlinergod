/**
 * Per-field LWW (Last-Write-Wins) merge for node sync records.
 *
 * Merge rule: for each field independently, the version whose HLC string is
 * lexicographically greater wins. Deletion (non-null deleted_at with highest
 * deleted_hlc) always wins over concurrent field edits by the same logic.
 *
 * Tiebreaker when two HLCs are equal: use a deterministic comparison on the
 * VALUE itself (lexicographic max for strings, numeric max for numbers).
 * This tiebreaker makes the function mathematically commutative for ALL inputs
 * including the equal-HLC edge case, which cannot occur in production (because
 * HLC strings embed a unique device_id, guaranteeing distinctness).
 *
 * Properties guaranteed:
 *   - Commutative:  merge(a, b) === merge(b, a)  for every field
 *   - Idempotent:   merge(a, merge(a, b)) === merge(a, b)
 */

export interface NodeSyncRecord {
  id: string
  document_id: string
  content: string
  content_hlc: string
  note: string
  note_hlc: string
  parent_id: string | null
  parent_id_hlc: string
  sort_order: string
  sort_order_hlc: string
  completed: number
  completed_hlc: string
  color: number
  color_hlc: string
  collapsed: number
  collapsed_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
}

// ---------------------------------------------------------------------------
// Field-level LWW helpers (all commutative)
// ---------------------------------------------------------------------------

/** Returns the higher of two strings (lexicographic). */
function maxStr(a: string, b: string): string {
  return a >= b ? a : b
}

/**
 * LWW for string values.
 * When HLCs are strictly ordered, the winner is clear.
 * When HLCs are equal, pick the lexicographically larger value as a
 * deterministic, commutative tiebreaker.
 */
function lwwStr(aVal: string, aHlc: string, bVal: string, bHlc: string): string {
  if (aHlc > bHlc) return aVal
  if (bHlc > aHlc) return bVal
  return aVal >= bVal ? aVal : bVal
}

/** LWW for integer values. */
function lwwInt(aVal: number, aHlc: string, bVal: number, bHlc: string): number {
  if (aHlc > bHlc) return aVal
  if (bHlc > aHlc) return bVal
  return aVal >= bVal ? aVal : bVal
}

/** LWW for nullable integer values (used for deleted_at). */
function lwwNullableInt(
  aVal: number | null,
  aHlc: string,
  bVal: number | null,
  bHlc: string,
): number | null {
  if (aHlc > bHlc) return aVal
  if (bHlc > aHlc) return bVal
  // HLCs are equal — use a commutative tiebreaker:
  // non-null > null; among two non-nulls pick the larger timestamp.
  if (aVal === null && bVal === null) return null
  if (aVal === null) return bVal
  if (bVal === null) return aVal
  return aVal >= bVal ? aVal : bVal
}

// ---------------------------------------------------------------------------
// Node merge
// ---------------------------------------------------------------------------

/**
 * Merge two NodeSyncRecords using per-field LWW.
 * `a` and `b` must share the same `id`.
 */
export function mergeNodes(a: NodeSyncRecord, b: NodeSyncRecord): NodeSyncRecord {
  const content = lwwStr(a.content, a.content_hlc, b.content, b.content_hlc)
  const content_hlc = maxStr(a.content_hlc, b.content_hlc)

  const note = lwwStr(a.note, a.note_hlc, b.note, b.note_hlc)
  const note_hlc = maxStr(a.note_hlc, b.note_hlc)

  // parent_id is nullable; convert null to '' for string comparison, then back
  const rawParentId = lwwStr(
    a.parent_id ?? '',
    a.parent_id_hlc,
    b.parent_id ?? '',
    b.parent_id_hlc,
  )
  const parent_id: string | null = rawParentId === '' ? null : rawParentId
  const parent_id_hlc = maxStr(a.parent_id_hlc, b.parent_id_hlc)

  const sort_order = lwwStr(a.sort_order, a.sort_order_hlc, b.sort_order, b.sort_order_hlc)
  const sort_order_hlc = maxStr(a.sort_order_hlc, b.sort_order_hlc)

  const completed = lwwInt(a.completed, a.completed_hlc, b.completed, b.completed_hlc)
  const completed_hlc = maxStr(a.completed_hlc, b.completed_hlc)

  const color = lwwInt(a.color, a.color_hlc, b.color, b.color_hlc)
  const color_hlc = maxStr(a.color_hlc, b.color_hlc)

  const collapsed = lwwInt(a.collapsed, a.collapsed_hlc, b.collapsed, b.collapsed_hlc)
  const collapsed_hlc = maxStr(a.collapsed_hlc, b.collapsed_hlc)

  // Deletion wins when deleted_hlc is highest — handled automatically because
  // deletedAt is just another per-field LWW register.
  const deleted_at = lwwNullableInt(a.deleted_at, a.deleted_hlc, b.deleted_at, b.deleted_hlc)
  const deleted_hlc = maxStr(a.deleted_hlc, b.deleted_hlc)

  // device_id: set to the device that produced the overall highest HLC.
  // When the max HLCs are equal, use the lexicographically larger device_id as
  // a deterministic, commutative tiebreaker.
  const allHlcsA = [
    a.content_hlc, a.note_hlc, a.parent_id_hlc, a.sort_order_hlc,
    a.completed_hlc, a.color_hlc, a.collapsed_hlc, a.deleted_hlc,
  ]
  const allHlcsB = [
    b.content_hlc, b.note_hlc, b.parent_id_hlc, b.sort_order_hlc,
    b.completed_hlc, b.color_hlc, b.collapsed_hlc, b.deleted_hlc,
  ]
  const maxA = allHlcsA.reduce((m, v) => (v > m ? v : m), '')
  const maxB = allHlcsB.reduce((m, v) => (v > m ? v : m), '')
  const device_id =
    maxB > maxA ? b.device_id
    : maxA > maxB ? a.device_id
    : a.device_id >= b.device_id ? a.device_id : b.device_id

  return {
    id: a.id,
    document_id: a.document_id,
    content,
    content_hlc,
    note,
    note_hlc,
    parent_id,
    parent_id_hlc,
    sort_order,
    sort_order_hlc,
    completed,
    completed_hlc,
    color,
    color_hlc,
    collapsed,
    collapsed_hlc,
    deleted_at,
    deleted_hlc,
    device_id,
  }
}

// ---------------------------------------------------------------------------
// Document record
// ---------------------------------------------------------------------------

export interface DocumentSyncRecord {
  id: string
  user_id: string
  title: string
  title_hlc: string
  type: string // 'document' | 'folder' — immutable after creation
  parent_id: string | null
  parent_id_hlc: string
  sort_order: string
  sort_order_hlc: string
  collapsed: number
  collapsed_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

/**
 * Merge two DocumentSyncRecords using per-field LWW.
 * `a` and `b` must share the same `id`.
 */
export function mergeDocuments(
  a: DocumentSyncRecord,
  b: DocumentSyncRecord,
): DocumentSyncRecord {
  const title = lwwStr(a.title, a.title_hlc, b.title, b.title_hlc)
  const title_hlc = maxStr(a.title_hlc, b.title_hlc)

  const rawParentId = lwwStr(
    a.parent_id ?? '',
    a.parent_id_hlc,
    b.parent_id ?? '',
    b.parent_id_hlc,
  )
  const parent_id: string | null = rawParentId === '' ? null : rawParentId
  const parent_id_hlc = maxStr(a.parent_id_hlc, b.parent_id_hlc)

  const sort_order = lwwStr(a.sort_order, a.sort_order_hlc, b.sort_order, b.sort_order_hlc)
  const sort_order_hlc = maxStr(a.sort_order_hlc, b.sort_order_hlc)

  const collapsed = lwwInt(a.collapsed, a.collapsed_hlc, b.collapsed, b.collapsed_hlc)
  const collapsed_hlc = maxStr(a.collapsed_hlc, b.collapsed_hlc)

  const deleted_at = lwwNullableInt(a.deleted_at, a.deleted_hlc, b.deleted_at, b.deleted_hlc)
  const deleted_hlc = maxStr(a.deleted_hlc, b.deleted_hlc)

  const allHlcsA = [a.title_hlc, a.parent_id_hlc, a.sort_order_hlc, a.collapsed_hlc, a.deleted_hlc]
  const allHlcsB = [b.title_hlc, b.parent_id_hlc, b.sort_order_hlc, b.collapsed_hlc, b.deleted_hlc]
  const maxA = allHlcsA.reduce((m, v) => (v > m ? v : m), '')
  const maxB = allHlcsB.reduce((m, v) => (v > m ? v : m), '')
  const device_id =
    maxB > maxA ? b.device_id
    : maxA > maxB ? a.device_id
    : a.device_id >= b.device_id ? a.device_id : b.device_id

  return {
    id: a.id,
    user_id: a.user_id,
    title,
    title_hlc,
    type: a.type, // immutable — take from local
    parent_id,
    parent_id_hlc,
    sort_order,
    sort_order_hlc,
    collapsed,
    collapsed_hlc,
    deleted_at,
    deleted_hlc,
    device_id,
    created_at: Math.min(a.created_at, b.created_at),
    updated_at: Math.max(a.updated_at, b.updated_at),
  }
}

// ---------------------------------------------------------------------------
// Bookmark record
// ---------------------------------------------------------------------------

export interface BookmarkSyncRecord {
  id: string
  user_id: string
  node_id: string
  document_id: string
  sort_order: string
  sort_order_hlc: string
  deleted_at: number | null
  deleted_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

/**
 * Merge two BookmarkSyncRecords using per-field LWW.
 * `a` and `b` must share the same `id`.
 */
export function mergeBookmarks(
  a: BookmarkSyncRecord,
  b: BookmarkSyncRecord,
): BookmarkSyncRecord {
  const sort_order = lwwStr(a.sort_order, a.sort_order_hlc, b.sort_order, b.sort_order_hlc)
  const sort_order_hlc = maxStr(a.sort_order_hlc, b.sort_order_hlc)

  const deleted_at = lwwNullableInt(a.deleted_at, a.deleted_hlc, b.deleted_at, b.deleted_hlc)
  const deleted_hlc = maxStr(a.deleted_hlc, b.deleted_hlc)

  const allHlcsA = [a.sort_order_hlc, a.deleted_hlc]
  const allHlcsB = [b.sort_order_hlc, b.deleted_hlc]
  const maxA = allHlcsA.reduce((m, v) => (v > m ? v : m), '')
  const maxB = allHlcsB.reduce((m, v) => (v > m ? v : m), '')
  const device_id =
    maxB > maxA ? b.device_id
    : maxA > maxB ? a.device_id
    : a.device_id >= b.device_id ? a.device_id : b.device_id

  return {
    id: a.id,
    user_id: a.user_id,
    node_id: a.node_id,
    document_id: a.document_id,
    sort_order,
    sort_order_hlc,
    deleted_at,
    deleted_hlc,
    device_id,
    created_at: Math.min(a.created_at, b.created_at),
    updated_at: Math.max(a.updated_at, b.updated_at),
  }
}
