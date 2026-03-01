import { describe, it, expect } from 'vitest'
import {
  mergeNodes,
  mergeDocuments,
  mergeBookmarks,
  type NodeSyncRecord,
  type DocumentSyncRecord,
  type BookmarkSyncRecord,
} from './merge.js'

// HLC constants for testing — lexicographically ordered: HLC_T0 < HLC_T1 < HLC_T2
// Format matches P1-12 spec: <wall_ms_16hex>-<counter_4hex>-<deviceId>
const HLC_T0 = '0000000000000000-0000-device0' // wall = 0
const HLC_T1 = '0000000000000064-0000-device1' // wall = 0x64 = 100 ms
const HLC_T2 = '00000000000000c8-0000-device2' // wall = 0xc8 = 200 ms

// ---------------------------------------------------------------------------
// Helpers — build complete sync records with sensible defaults
// ---------------------------------------------------------------------------

function makeBaseNode(overrides: Partial<NodeSyncRecord> = {}): NodeSyncRecord {
  return {
    id: 'node-1',
    document_id: 'doc-1',
    content: 'hello',
    content_hlc: HLC_T1,
    note: '',
    note_hlc: HLC_T1,
    parent_id: null,
    parent_id_hlc: HLC_T1,
    sort_order: 'a0',
    sort_order_hlc: HLC_T1,
    completed: 0,
    completed_hlc: HLC_T1,
    color: 0,
    color_hlc: HLC_T1,
    collapsed: 0,
    collapsed_hlc: HLC_T1,
    deleted_at: null,
    deleted_hlc: HLC_T1,
    device_id: 'device1',
    ...overrides,
  }
}

function makeBaseDocument(overrides: Partial<DocumentSyncRecord> = {}): DocumentSyncRecord {
  return {
    id: 'doc-1',
    user_id: 'user-1',
    title: 'My Document',
    title_hlc: HLC_T1,
    type: 'document',
    parent_id: null,
    parent_id_hlc: HLC_T1,
    sort_order: 'a0',
    sort_order_hlc: HLC_T1,
    collapsed: 0,
    collapsed_hlc: HLC_T1,
    deleted_at: null,
    deleted_hlc: HLC_T1,
    device_id: 'device1',
    created_at: 1000,
    updated_at: 1000,
    ...overrides,
  }
}

function makeBaseBookmark(overrides: Partial<BookmarkSyncRecord> = {}): BookmarkSyncRecord {
  return {
    id: 'bm-1',
    user_id: 'user-1',
    title: 'My Bookmark',
    title_hlc: HLC_T1,
    target_type: 'document',
    target_type_hlc: HLC_T1,
    target_document_id: 'doc-1',
    target_document_id_hlc: HLC_T1,
    target_node_id: null,
    target_node_id_hlc: HLC_T1,
    query: null,
    query_hlc: HLC_T1,
    sort_order: 'a0',
    sort_order_hlc: HLC_T1,
    deleted_at: null,
    deleted_hlc: HLC_T1,
    device_id: 'device1',
    created_at: 1000,
    updated_at: 1000,
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('merge', () => {
  it('mergeNodes_incomingWins_whenHigherContentHlc', () => {
    const local = makeBaseNode({ content: 'old content', content_hlc: HLC_T1 })
    const incoming = makeBaseNode({
      content: 'new content',
      content_hlc: HLC_T2,
      device_id: 'device2',
    })
    const merged = mergeNodes(local, incoming)
    expect(merged.content).toBe('new content')
  })

  it('mergeNodes_localWins_onEqualHlc', () => {
    // Identical HLC and identical value: idempotency — merge(A, A) === A
    const local = makeBaseNode({ content: 'shared content', content_hlc: HLC_T1 })
    const incoming = makeBaseNode({ content: 'shared content', content_hlc: HLC_T1 })
    const merged = mergeNodes(local, incoming)
    expect(merged.content).toBe('shared content')
  })

  it('mergeNodes_isIdempotent', () => {
    const A = makeBaseNode({ content: 'hello', content_hlc: HLC_T1 })
    const B = makeBaseNode({ content: 'world', content_hlc: HLC_T2, device_id: 'device2' })
    const merged1 = mergeNodes(A, B)
    const merged2 = mergeNodes(A, merged1)
    expect(merged2).toEqual(merged1)
  })

  it('mergeNodes_isCommutative_perField', () => {
    // A wins content (higher content_hlc); B wins note (higher note_hlc).
    const A = makeBaseNode({
      content: 'content from A',
      content_hlc: HLC_T2, // A wins content
      note: 'note from A',
      note_hlc: HLC_T1, // B wins note (T2 > T1)
      device_id: 'device1',
    })
    const B = makeBaseNode({
      content: 'content from B',
      content_hlc: HLC_T1, // lower
      note: 'note from B',
      note_hlc: HLC_T2, // B wins note
      device_id: 'device2',
    })
    const AB = mergeNodes(A, B)
    const BA = mergeNodes(B, A)

    // Every syncable field must be identical regardless of argument order
    expect(AB.content).toBe(BA.content)
    expect(AB.content_hlc).toBe(BA.content_hlc)
    expect(AB.note).toBe(BA.note)
    expect(AB.note_hlc).toBe(BA.note_hlc)
    expect(AB.parent_id).toBe(BA.parent_id)
    expect(AB.parent_id_hlc).toBe(BA.parent_id_hlc)
    expect(AB.sort_order).toBe(BA.sort_order)
    expect(AB.sort_order_hlc).toBe(BA.sort_order_hlc)
    expect(AB.completed).toBe(BA.completed)
    expect(AB.completed_hlc).toBe(BA.completed_hlc)
    expect(AB.color).toBe(BA.color)
    expect(AB.color_hlc).toBe(BA.color_hlc)
    expect(AB.collapsed).toBe(BA.collapsed)
    expect(AB.collapsed_hlc).toBe(BA.collapsed_hlc)
    expect(AB.deleted_at).toBe(BA.deleted_at)
    expect(AB.deleted_hlc).toBe(BA.deleted_hlc)
    // device_id may differ — not checked per spec
  })

  it('mergeNodes_deleteWins_whenDeletedHlcIsHighest', () => {
    const A = makeBaseNode({ deleted_at: null, deleted_hlc: HLC_T1 })
    const B = makeBaseNode({
      deleted_at: 1700000000000,
      deleted_hlc: HLC_T2, // B's deletion HLC wins
      device_id: 'device2',
    })
    const merged = mergeNodes(A, B)
    expect(merged.deleted_at).toBe(1700000000000)
  })

  it('mergeNodes_contentFieldIndependent_ofDeletion', () => {
    // A: edited content at T2 (high), never deleted (deleted_hlc = T0)
    // B: blank content at T0 (low), deleted at T2 (high)
    // Expect: merge has deletion from B AND content from A
    const A = makeBaseNode({
      content: 'edited content',
      content_hlc: HLC_T2, // A wins content
      deleted_at: null,
      deleted_hlc: HLC_T0, // A never deleted
    })
    const B = makeBaseNode({
      content: '',
      content_hlc: HLC_T0, // B never set content
      deleted_at: 1700000000000,
      deleted_hlc: HLC_T2, // B wins deletion
      device_id: 'device2',
    })
    const merged = mergeNodes(A, B)
    expect(merged.deleted_at).toBe(1700000000000) // B's deleted_hlc wins
    expect(merged.content).toBe('edited content') // A's content_hlc wins
  })

  it('mergeDocuments_titleField_higherHlcWins', () => {
    const A = makeBaseDocument({ title: 'old title', title_hlc: HLC_T1 })
    const B = makeBaseDocument({
      title: 'new title',
      title_hlc: HLC_T2,
      device_id: 'device2',
    })
    const merged = mergeDocuments(A, B)
    expect(merged.title).toBe('new title')
  })

  it('mergeBookmarks_sortOrderField_higherHlcWins', () => {
    const A = makeBaseBookmark({ sort_order: 'a0', sort_order_hlc: HLC_T1 })
    const B = makeBaseBookmark({
      sort_order: 'a1',
      sort_order_hlc: HLC_T2,
      device_id: 'device2',
    })
    const merged = mergeBookmarks(A, B)
    expect(merged.sort_order).toBe('a1')
  })
})
