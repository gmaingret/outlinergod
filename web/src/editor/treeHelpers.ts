// Pure tree helper functions for the node editor.
// sort_order is always TEXT (fractional index) — never parseFloat or Number() on it.

export interface NodeResponse {
  id: string
  document_id: string
  content: string
  note: string
  parent_id: string | null
  sort_order: string
  completed: boolean
  color: number
  collapsed: boolean
  deleted_at: number | null
  created_at: number
  updated_at: number
}

export interface TreeNode extends NodeResponse {
  children: TreeNode[]
}

// Mutates sort_order in-place for each sibling using 'a' + index padded to 4 digits.
// e.g., 'a0000', 'a0001', 'a0002'
export function recomputeSortOrders(siblings: TreeNode[]): void {
  for (let i = 0; i < siblings.length; i++) {
    siblings[i].sort_order = 'a' + i.toString().padStart(4, '0')
  }
}

// Build a nested tree from a flat NodeResponse array.
// Orphaned nodes (unknown parent_id) become roots.
// Sorts children at every level lexicographically by sort_order.
export function buildTree(nodes: NodeResponse[]): TreeNode[] {
  const map = new Map<string, TreeNode>()
  const roots: TreeNode[] = []

  for (const n of nodes) {
    map.set(n.id, { ...n, children: [] })
  }

  for (const n of nodes) {
    const node = map.get(n.id)!
    if (n.parent_id && map.has(n.parent_id)) {
      map.get(n.parent_id)!.children.push(node)
    } else {
      roots.push(node)
    }
  }

  const sortChildren = (nodes: TreeNode[]): void => {
    nodes.sort((a, b) =>
      a.sort_order < b.sort_order ? -1 : a.sort_order > b.sort_order ? 1 : 0
    )
    for (const n of nodes) sortChildren(n.children)
  }

  sortChildren(roots)
  return roots
}

// BFS search for a node by id. Returns the node (with all descendants) or null.
export function filterSubtree(roots: TreeNode[], rootNodeId: string): TreeNode | null {
  const queue: TreeNode[] = [...roots]
  while (queue.length) {
    const node = queue.shift()!
    if (node.id === rootNodeId) return node
    queue.push(...node.children)
  }
  return null
}

// Internal: DFS to find a node with its parent context.
interface NodeWithParent {
  node: TreeNode
  parent: TreeNode | null
  siblings: TreeNode[]
  index: number
}

function findNodeWithParent(
  roots: TreeNode[],
  nodeId: string,
  parent: TreeNode | null = null
): NodeWithParent | null {
  const list = parent ? parent.children : roots
  for (let i = 0; i < list.length; i++) {
    if (list[i].id === nodeId) {
      return { node: list[i], parent, siblings: list, index: i }
    }
    const found = findNodeWithParent(roots, nodeId, list[i])
    if (found) return found
  }
  return null
}

// Insert a pre-built newNode immediately after afterNodeId in the same parent's children list.
// Calls recomputeSortOrders on the affected siblings. Returns the mutated roots.
export function insertSiblingNode(
  roots: TreeNode[],
  afterNodeId: string,
  newNode: TreeNode
): TreeNode[] {
  const found = findNodeWithParent(roots, afterNodeId)
  if (!found) return roots

  const { siblings, index } = found
  siblings.splice(index + 1, 0, newNode)
  recomputeSortOrders(siblings)

  return roots
}

// Make the target node a child of its immediately preceding sibling.
// No-op if the target is the first child or first root (no preceding sibling).
export function indentNode(roots: TreeNode[], nodeId: string): TreeNode[] {
  const found = findNodeWithParent(roots, nodeId)
  if (!found) return roots

  const { node, siblings, index } = found
  if (index === 0) return roots  // no preceding sibling

  const precedingSibling = siblings[index - 1]

  // Remove from current position
  siblings.splice(index, 1)
  recomputeSortOrders(siblings)

  // Append to preceding sibling's children
  node.parent_id = precedingSibling.id
  precedingSibling.children.push(node)
  recomputeSortOrders(precedingSibling.children)

  return roots
}

// FlatNode — the unit the drag-and-drop layer works with.
export interface FlatNode {
  node: TreeNode
  depth: number
  parentId: string | null
}

// DFS traversal producing a flat ordered list. Respects node.collapsed.
// Children of collapsed nodes are omitted from the result.
export function flattenTree(
  roots: TreeNode[],
  depth = 0,
  parentId: string | null = null,
  result: FlatNode[] = []
): FlatNode[] {
  for (const node of roots) {
    result.push({ node, depth, parentId })
    if (!node.collapsed && node.children.length > 0) {
      flattenTree(node.children, depth + 1, node.id, result)
    }
  }
  return result
}


// Reorder a node in the tree by moving it to targetIndex in the visible flat list at newDepth.
// Mutates TreeNode objects in place (same pattern as indentNode/outdentNode). Returns roots.
export function reorderNode(
  roots: TreeNode[],
  dragId: string,
  targetIndex: number,
  newDepth: number
): TreeNode[] {
  const flatNodes = flattenTree(roots)
  const activeEntry = flatNodes.find(fn => fn.node.id === dragId)
  if (!activeEntry) return roots

  const draggedNode = activeEntry.node

  // --- Step 1: remove dragged node from its current parent ---
  const draggedFound = findNodeWithParent(roots, dragId)
  if (!draggedFound) return roots

  const { siblings: oldSiblings, index: oldIndex } = draggedFound
  oldSiblings.splice(oldIndex, 1)
  recomputeSortOrders(oldSiblings)

  // --- Step 2: determine new parent ---
  // Re-flatten after removal so targetIndex is resolved against the updated tree
  const flatAfterRemoval = flattenTree(roots)

  let newParentId: string | null = null
  let newParentNode: TreeNode | null = null

  if (newDepth > 0) {
    // Look backwards from targetIndex - 1 for first entry at depth newDepth - 1
    const searchEnd = Math.min(targetIndex, flatAfterRemoval.length)
    for (let i = searchEnd - 1; i >= 0; i--) {
      if (flatAfterRemoval[i].depth === newDepth - 1) {
        newParentId = flatAfterRemoval[i].node.id
        newParentNode = flatAfterRemoval[i].node
        break
      }
    }
    // Fall back to root if no valid parent found
    if (newParentId === null) {
      newDepth = 0
    }
  }

  // --- Step 3: update parent_id on dragged node ---
  draggedNode.parent_id = newParentId

  // --- Step 4: insert dragged node into new parent's children list ---
  if (newParentNode) {
    // Derive insert position by counting parent's children appearing before targetIndex in flat list
    let childrenBeforeTarget = 0
    for (let i = 0; i < Math.min(targetIndex, flatAfterRemoval.length); i++) {
      if (flatAfterRemoval[i].parentId === newParentId) {
        childrenBeforeTarget++
      }
    }
    newParentNode.children.splice(childrenBeforeTarget, 0, draggedNode)
    recomputeSortOrders(newParentNode.children)
  } else {
    // Insert at root level
    const clampedTarget = Math.min(targetIndex, roots.length)
    roots.splice(clampedTarget, 0, draggedNode)
    recomputeSortOrders(roots)
  }

  return roots
}

// Promote the target node to be a sibling immediately after its parent.
// No-op if the target is already at root level (no parent).
export function outdentNode(roots: TreeNode[], nodeId: string): TreeNode[] {
  const found = findNodeWithParent(roots, nodeId)
  if (!found) return roots

  const { node, parent, siblings: parentSiblings, index: nodeIndex } = found
  if (!parent) return roots  // already at root level

  // Find parent's position in grandparent context
  const parentFound = findNodeWithParent(roots, parent.id)
  const grandparentSiblings = parentFound ? parentFound.siblings : roots
  const parentIndex = grandparentSiblings.findIndex(n => n.id === parent.id)

  // Remove node from parent's children
  parentSiblings.splice(nodeIndex, 1)
  recomputeSortOrders(parentSiblings)

  // Insert node immediately after parent in grandparent's list
  node.parent_id = parentFound ? parentFound.parent?.id ?? null : null
  grandparentSiblings.splice(parentIndex + 1, 0, node)
  recomputeSortOrders(grandparentSiblings)

  return roots
}
