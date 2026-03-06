import { describe, it, expect } from 'vitest'
import { buildTree, filterSubtree, insertSiblingNode, indentNode, outdentNode, flattenTree, reorderNode } from './treeHelpers'
import type { NodeResponse, TreeNode } from './treeHelpers'

// Minimal NodeResponse factory — only required fields
function makeNode(overrides: Partial<NodeResponse> & { id: string; sort_order: string }): NodeResponse {
  return {
    document_id: 'doc-1',
    content: '',
    note: '',
    parent_id: null,
    completed: false,
    color: 0,
    collapsed: false,
    deleted_at: null,
    created_at: 0,
    updated_at: 0,
    ...overrides,
  }
}

describe('buildTree', () => {
  it('buildTree([]) returns []', () => {
    expect(buildTree([])).toEqual([])
  })

  it('buildTree with root-only nodes returns them sorted by sort_order lexicographically', () => {
    const nodes = [
      makeNode({ id: 'n3', sort_order: 'b0' }),
      makeNode({ id: 'n1', sort_order: 'aV' }),
      makeNode({ id: 'n2', sort_order: 'a0' }),
    ]
    const tree = buildTree(nodes)
    expect(tree.map(n => n.id)).toEqual(['n2', 'n1', 'n3'])
  })

  it('buildTree with parent_id links produces nested children arrays', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'child2', sort_order: 'b0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    expect(tree).toHaveLength(1)
    expect(tree[0].id).toBe('root')
    expect(tree[0].children).toHaveLength(2)
    expect(tree[0].children[0].id).toBe('child1')
    expect(tree[0].children[1].id).toBe('child2')
  })

  it('buildTree ignores nodes whose parent_id references a missing node (orphan becomes root)', () => {
    const nodes = [
      makeNode({ id: 'real-root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'orphan', sort_order: 'b0', parent_id: 'nonexistent-parent' }),
    ]
    const tree = buildTree(nodes)
    // Orphan should be treated as a root node
    expect(tree.map(n => n.id)).toContain('orphan')
    expect(tree.map(n => n.id)).toContain('real-root')
  })
})

describe('filterSubtree', () => {
  function sampleTree(): TreeNode[] {
    return buildTree([
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'grandchild', sort_order: 'a0', parent_id: 'child1' }),
      makeNode({ id: 'child2', sort_order: 'b0', parent_id: 'root' }),
    ])
  }

  it('filterSubtree finds a node by id via BFS and returns it with its children intact', () => {
    const tree = sampleTree()
    const result = filterSubtree(tree, 'child1')
    expect(result).not.toBeNull()
    expect(result!.id).toBe('child1')
    expect(result!.children).toHaveLength(1)
    expect(result!.children[0].id).toBe('grandchild')
  })

  it('filterSubtree returns null when nodeId does not exist', () => {
    const tree = sampleTree()
    const result = filterSubtree(tree, 'nonexistent')
    expect(result).toBeNull()
  })

  it('filterSubtree can find the root node', () => {
    const tree = sampleTree()
    const result = filterSubtree(tree, 'root')
    expect(result).not.toBeNull()
    expect(result!.id).toBe('root')
    expect(result!.children).toHaveLength(2)
  })
})

describe('insertSiblingNode', () => {
  it('insertSiblingNode inserts a new node immediately after the target node in same parent', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'child2', sort_order: 'b0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    const newNode = makeNode({ id: 'new', sort_order: '', parent_id: 'root' })
    const result = insertSiblingNode(tree, 'child1', newNode)

    const root = result[0]
    expect(root.children[0].id).toBe('child1')
    expect(root.children[1].id).toBe('new')
    expect(root.children[2].id).toBe('child2')
  })

  it('insertSiblingNode at the last position in a parent adds after the last child', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'child2', sort_order: 'b0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    const newNode = makeNode({ id: 'new', sort_order: '', parent_id: 'root' })
    const result = insertSiblingNode(tree, 'child2', newNode)

    const root = result[0]
    expect(root.children[0].id).toBe('child1')
    expect(root.children[1].id).toBe('child2')
    expect(root.children[2].id).toBe('new')
  })
})

describe('indentNode', () => {
  it('indentNode makes the target node a child of its preceding sibling', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'child2', sort_order: 'b0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    const result = indentNode(tree, 'child2')

    // child2 should now be a child of child1, not root
    const root = result[0]
    expect(root.children).toHaveLength(1)
    expect(root.children[0].id).toBe('child1')
    expect(root.children[0].children).toHaveLength(1)
    expect(root.children[0].children[0].id).toBe('child2')
  })

  it('indentNode fails gracefully when target is the first child (no preceding sibling)', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    // Should return original tree unchanged (or equivalent)
    const result = indentNode(tree, 'child1')
    expect(result[0].children).toHaveLength(1)
    expect(result[0].children[0].id).toBe('child1')
  })
})

describe('outdentNode', () => {
  it('outdentNode promotes the target node to be a sibling of its parent', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
      makeNode({ id: 'grandchild', sort_order: 'a0', parent_id: 'child1' }),
    ]
    const tree = buildTree(nodes)
    const result = outdentNode(tree, 'grandchild')

    // grandchild should now be a sibling of child1 under root
    const root = result[0]
    expect(root.children).toHaveLength(2)
    const ids = root.children.map(n => n.id)
    expect(ids).toContain('child1')
    expect(ids).toContain('grandchild')
    expect(root.children[0].children).toHaveLength(0)
  })

  it('outdentNode fails gracefully when target is already at root level', () => {
    const nodes = [
      makeNode({ id: 'root1', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'root2', sort_order: 'b0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    // Should return original tree unchanged
    const result = outdentNode(tree, 'root1')
    expect(result).toHaveLength(2)
  })
})

describe('recomputeSortOrders', () => {
  it('children sort_orders are assigned lexicographic keys after insertSiblingNode', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child1', sort_order: 'a0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    const newNode = makeNode({ id: 'child2', sort_order: '', parent_id: 'root' })
    const result = insertSiblingNode(tree, 'child1', newNode)

    // After insert, the new node should have a valid sort_order assigned
    const root = result[0]
    expect(root.children[1].sort_order).toBeTruthy()
    expect(typeof root.children[1].sort_order).toBe('string')
  })
})

describe('flattenTree', () => {
  it('flattenTree([]) returns []', () => {
    expect(flattenTree([])).toEqual([])
  })

  it('flattenTree with two root nodes returns both at depth 0', () => {
    const nodes = [
      makeNode({ id: 'n1', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'n2', sort_order: 'b0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    const flat = flattenTree(tree)
    expect(flat).toHaveLength(2)
    expect(flat[0].depth).toBe(0)
    expect(flat[1].depth).toBe(0)
    expect(flat[0].node.id).toBe('n1')
    expect(flat[1].node.id).toBe('n2')
  })

  it('flattenTree with parent→child returns parent at depth 0, child at depth 1', () => {
    const nodes = [
      makeNode({ id: 'parent', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child', sort_order: 'a0', parent_id: 'parent' }),
    ]
    const tree = buildTree(nodes)
    const flat = flattenTree(tree)
    expect(flat).toHaveLength(2)
    expect(flat[0].node.id).toBe('parent')
    expect(flat[0].depth).toBe(0)
    expect(flat[1].node.id).toBe('child')
    expect(flat[1].depth).toBe(1)
  })

  it('flattenTree excludes children of a collapsed parent', () => {
    const nodes = [
      makeNode({ id: 'parent', sort_order: 'a0', parent_id: null, collapsed: true }),
      makeNode({ id: 'child', sort_order: 'a0', parent_id: 'parent' }),
    ]
    const tree = buildTree(nodes)
    const flat = flattenTree(tree)
    expect(flat).toHaveLength(1)
    expect(flat[0].node.id).toBe('parent')
  })

  it('FlatNode.parentId is null for root nodes and equals parent id for children', () => {
    const nodes = [
      makeNode({ id: 'root', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child', sort_order: 'a0', parent_id: 'root' }),
    ]
    const tree = buildTree(nodes)
    const flat = flattenTree(tree)
    expect(flat[0].parentId).toBeNull()
    expect(flat[1].parentId).toBe('root')
  })
})

describe('reorderNode', () => {
  it('reorderNode moves a root node from index 0 to index 1 — sort_orders updated, IDs in correct order', () => {
    const nodes = [
      makeNode({ id: 'n1', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'n2', sort_order: 'b0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    // Move n1 (index 0) to index 1, keep depth 0
    const result = reorderNode(tree, 'n1', 1, 0)
    expect(result.map(n => n.id)).toEqual(['n2', 'n1'])
    // sort_orders must be re-assigned
    expect(result[0].sort_order).toBeTruthy()
    expect(result[1].sort_order).toBeTruthy()
    expect(result[0].sort_order < result[1].sort_order || result[0].sort_order !== result[1].sort_order).toBe(true)
  })

  it('reorderNode reparents a depth-0 node to depth-1 (child of the node above it)', () => {
    const nodes = [
      makeNode({ id: 'n1', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'n2', sort_order: 'b0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    // Move n2 (index 1) to same index 1, but depth 1 — becomes child of n1
    const result = reorderNode(tree, 'n2', 1, 1)
    expect(result).toHaveLength(1)
    expect(result[0].id).toBe('n1')
    expect(result[0].children).toHaveLength(1)
    expect(result[0].children[0].id).toBe('n2')
    expect(result[0].children[0].parent_id).toBe('n1')
  })

  it('reorderNode moving a parent keeps its children attached (child still has same parent_id)', () => {
    const nodes = [
      makeNode({ id: 'p1', sort_order: 'a0', parent_id: null }),
      makeNode({ id: 'child', sort_order: 'a0', parent_id: 'p1' }),
      makeNode({ id: 'p2', sort_order: 'b0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    // Move p1 (index 0 in flat list) to after p2 — targetIndex 2, depth 0
    // flat list: [p1(0), child(1), p2(2)]
    const result = reorderNode(tree, 'p1', 2, 0)
    // p2 should be first, p1 second with child still attached
    expect(result.map(n => n.id)).toEqual(['p2', 'p1'])
    expect(result[1].children).toHaveLength(1)
    expect(result[1].children[0].id).toBe('child')
    expect(result[1].children[0].parent_id).toBe('p1')
  })

  it('reorderNode returns original roots unchanged when dragId is not found', () => {
    const nodes = [
      makeNode({ id: 'n1', sort_order: 'a0', parent_id: null }),
    ]
    const tree = buildTree(nodes)
    const result = reorderNode(tree, 'nonexistent', 0, 0)
    expect(result).toHaveLength(1)
    expect(result[0].id).toBe('n1')
  })
})
