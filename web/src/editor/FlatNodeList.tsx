import { useState } from 'react'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  closestCenter,
} from '@dnd-kit/core'
import type { DragStartEvent, DragMoveEvent, DragEndEvent, DragOverEvent } from '@dnd-kit/core'
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { flattenTree, reorderNode } from './treeHelpers'
import type { FlatNode, TreeNode } from './treeHelpers'

// ---- Internal helpers ----

function getProjection(
  flatNodes: FlatNode[],
  activeId: string,
  overId: string,
  offsetLeft: number
): { depth: number; parentId: string | null } {
  const overIndex = flatNodes.findIndex(fn => fn.node.id === overId)
  const activeIndex = flatNodes.findIndex(fn => fn.node.id === activeId)
  const activeItem = flatNodes[activeIndex]
  if (!activeItem) return { depth: 0, parentId: null }

  // Project position after move
  const newItems = arrayMove(flatNodes, activeIndex, overIndex)
  const previousItem = newItems[overIndex - 1]
  const nextItem = newItems[overIndex + 1]

  const dragDepth = Math.round(offsetLeft / 24)
  const projectedDepth = activeItem.depth + dragDepth
  const maxDepth = previousItem ? previousItem.depth + 1 : 0
  const minDepth = nextItem ? nextItem.depth : 0
  const depth = Math.min(Math.max(projectedDepth, minDepth), maxDepth)

  // Find parentId by walking back through newItems looking for depth - 1
  let parentId: string | null = null
  if (depth > 0 && previousItem) {
    if (previousItem.depth === depth - 1) {
      parentId = previousItem.node.id
    } else {
      // Walk backwards to find first node at depth - 1
      for (let i = overIndex - 1; i >= 0; i--) {
        if (newItems[i].depth === depth - 1) {
          parentId = newItems[i].node.id
          break
        }
      }
    }
  }

  return { depth, parentId }
}

function collectDescendants(roots: TreeNode[], nodeId: string): TreeNode[] {
  function dfs(nodes: TreeNode[]): TreeNode[] {
    for (const n of nodes) {
      if (n.id === nodeId) {
        const result: TreeNode[] = [n]
        function collectAll(children: TreeNode[]) {
          for (const c of children) {
            result.push(c)
            collectAll(c.children)
          }
        }
        collectAll(n.children)
        return result
      }
      const found = dfs(n.children)
      if (found.length) return found
    }
    return []
  }
  return dfs(roots)
}

function countDescendants(node: TreeNode): number {
  return node.children.reduce((sum, c) => sum + 1 + countDescendants(c), 0)
}

// ---- SortableNodeRow sub-component ----

interface SortableNodeRowProps {
  flatNode: FlatNode
  onContentChange: (nodeId: string, value: string) => void
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>, nodeId: string) => void
  onGlyphClick: (nodeId: string) => void
  onCollapseToggle: (nodeId: string) => void
  inputRefs: React.MutableRefObject<Map<string, HTMLInputElement>>
  isDropTarget: boolean
  projectedDepth: number
}

function SortableNodeRow({
  flatNode,
  onContentChange,
  onKeyDown,
  onGlyphClick,
  onCollapseToggle,
  inputRefs,
  isDropTarget,
  projectedDepth,
}: SortableNodeRowProps) {
  const { node, depth } = flatNode
  const hasChildren = node.children.length > 0

  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    isDragging,
  } = useSortable({ id: node.id, transition: null })

  const style = {
    transform: CSS.Transform.toString(transform),
    opacity: isDragging ? 0.3 : 1,
    paddingLeft: `${depth * 24}px`,
  }

  return (
    <>
      {isDropTarget && (
        <div
          className="h-0.5 bg-blue-500 pointer-events-none"
          style={{ marginLeft: `${projectedDepth * 24}px` }}
        />
      )}
      <div
        ref={setNodeRef}
        style={style}
        className="group flex items-center py-1"
      >
        {/* Grip handle — aria-hidden so it doesn't appear in getAllByRole('button') queries */}
        <button
          ref={setActivatorNodeRef}
          {...listeners}
          {...attributes}
          aria-label="drag handle"
          aria-hidden="true"
          style={{ touchAction: 'none' }}
          className="opacity-0 group-hover:opacity-100 w-4 h-4 cursor-grab active:cursor-grabbing text-gray-400 flex-shrink-0 flex items-center justify-center text-xs"
        >
          ☰
        </button>
        {/* Bullet glyph — always zooms in */}
        <button
          data-node-id={node.id}
          onClick={() => onGlyphClick(node.id)}
          className="flex-shrink-0 w-5 h-5 flex items-center justify-center text-gray-400 hover:text-gray-600 cursor-pointer"
          aria-label="zoom in"
        >
          ●
        </button>
        {/* Directional arrow for collapse/expand — only shown when node has children */}
        {hasChildren && (
          <button
            onClick={() => onCollapseToggle(node.id)}
            className="flex-shrink-0 w-4 h-4 flex items-center justify-center text-gray-400 hover:text-gray-600 cursor-pointer text-xs"
            aria-label={node.collapsed ? 'expand' : 'collapse'}
          >
            {node.collapsed ? '▸' : '▾'}
          </button>
        )}
        <input
          ref={el => {
            if (el) inputRefs.current.set(node.id, el)
            else inputRefs.current.delete(node.id)
          }}
          type="text"
          value={node.content}
          onChange={e => onContentChange(node.id, e.target.value)}
          onKeyDown={e => onKeyDown(e, node.id)}
          className="flex-1 ml-1 outline-none text-sm text-gray-900 bg-transparent"
        />
      </div>
    </>
  )
}

// ---- FlatNodeList props contract ----

interface FlatNodeListProps {
  roots: TreeNode[]
  onTreeChange: (newRoots: TreeNode[]) => void
  queueChange: (node: TreeNode) => void
  onContentChange: (nodeId: string, value: string) => void
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>, nodeId: string) => void
  onGlyphClick: (nodeId: string) => void
  onCollapseToggle: (nodeId: string) => void
  inputRefs: React.MutableRefObject<Map<string, HTMLInputElement>>
}

// ---- FlatNodeList component ----

export function FlatNodeList({
  roots,
  onTreeChange,
  queueChange,
  onContentChange,
  onKeyDown,
  onGlyphClick,
  onCollapseToggle,
  inputRefs,
}: FlatNodeListProps) {
  const [activeId, setActiveId] = useState<string | null>(null)
  const [overId, setOverId] = useState<string | null>(null)
  const [offsetLeft, setOffsetLeft] = useState(0)

  const flatNodes = flattenTree(roots)
  const itemIds = flatNodes.map(fn => fn.node.id)

  const projected =
    activeId && overId
      ? getProjection(flatNodes, activeId, overId, offsetLeft)
      : null

  function handleDragStart({ active }: DragStartEvent) {
    setActiveId(active.id as string)
    setOverId(active.id as string)
    setOffsetLeft(0)
  }

  function handleDragMove({ delta }: DragMoveEvent) {
    setOffsetLeft(delta.x)
  }

  function handleDragOver({ over }: DragOverEvent) {
    setOverId(over ? String(over.id) : null)
  }

  function handleDragEnd({ active, over }: DragEndEvent) {
    if (over && active.id !== over.id && projected) {
      const overIndex = flatNodes.findIndex(fn => fn.node.id === over.id)
      const newRoots = reorderNode(roots, active.id as string, overIndex, projected.depth)
      // Queue dragged node + all descendants for sync
      const moved = collectDescendants(newRoots, active.id as string)
      moved.forEach(n => queueChange(n))
      onTreeChange(newRoots)
    }
    setActiveId(null)
    setOverId(null)
    setOffsetLeft(0)
  }

  function handleDragCancel() {
    setActiveId(null)
    setOverId(null)
    setOffsetLeft(0)
  }

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
  )

  const activeNode = activeId ? flatNodes.find(fn => fn.node.id === activeId) : null
  const childCount = activeNode ? countDescendants(activeNode.node) : 0

  const overIndex = overId ? flatNodes.findIndex(fn => fn.node.id === overId) : -1

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={handleDragStart}
      onDragMove={handleDragMove}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
        <div className="relative">
          {flatNodes.map((fn, i) => (
            <SortableNodeRow
              key={fn.node.id}
              flatNode={fn}
              onContentChange={onContentChange}
              onKeyDown={onKeyDown}
              onGlyphClick={onGlyphClick}
              onCollapseToggle={onCollapseToggle}
              inputRefs={inputRefs}
              isDropTarget={activeId !== null && overIndex === i}
              projectedDepth={projected?.depth ?? fn.depth}
            />
          ))}
        </div>
      </SortableContext>
      <DragOverlay dropAnimation={null}>
        {activeId ? (
          <div className="bg-white border border-blue-400 shadow-lg rounded px-3 py-1 text-sm opacity-90">
            {activeNode?.node.content}
            {childCount > 0 && (
              <span className="ml-2 text-xs text-gray-500">(+{childCount})</span>
            )}
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  )
}
