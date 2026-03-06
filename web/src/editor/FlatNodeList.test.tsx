import { render, screen, fireEvent } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { useRef } from 'react'
import { FlatNodeList } from './FlatNodeList'
import type { TreeNode } from './treeHelpers'

// Mock dnd-kit so render tests don't need a real pointer environment.
// DndContext and DragOverlay are replaced with no-ops; SortableContext passes children through.
vi.mock('@dnd-kit/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@dnd-kit/core')>()
  return {
    ...actual,
    DndContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    DragOverlay: () => null,
  }
})
vi.mock('@dnd-kit/sortable', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@dnd-kit/sortable')>()
  return {
    ...actual,
    SortableContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    useSortable: () => ({
      attributes: {},
      listeners: {},
      setNodeRef: () => {},
      setActivatorNodeRef: () => {},
      transform: null,
      transition: null,
      isDragging: false,
    }),
  }
})

// ---- Tree builder helpers ----

function makeNode(overrides: Partial<TreeNode> & { id: string; content: string }): TreeNode {
  return {
    document_id: 'doc-1',
    note: '',
    parent_id: null,
    sort_order: 'a0000',
    completed: false,
    color: 0,
    collapsed: false,
    deleted_at: null,
    created_at: 0,
    updated_at: 0,
    children: [],
    ...overrides,
  }
}

// Sample tree:
//  root (has child)
//    child
const child = makeNode({ id: 'child', content: 'Child node', parent_id: 'root' })
const root = makeNode({ id: 'root', content: 'Root node', children: [child] })

// ---- Wrapper component so we can pass inputRefs ----

function Wrapper({
  roots,
  onTreeChange = vi.fn(),
  queueChange = vi.fn(),
  onContentChange = vi.fn(),
  onKeyDown = vi.fn(),
  onGlyphClick = vi.fn(),
  onCollapseToggle = vi.fn(),
}: {
  roots: TreeNode[]
  onTreeChange?: (r: TreeNode[]) => void
  queueChange?: (n: TreeNode) => void
  onContentChange?: (nodeId: string, value: string) => void
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>, nodeId: string) => void
  onGlyphClick?: (nodeId: string) => void
  onCollapseToggle?: (nodeId: string) => void
}) {
  const inputRefs = useRef(new Map<string, HTMLInputElement>())
  return (
    <FlatNodeList
      roots={roots}
      onTreeChange={onTreeChange}
      queueChange={queueChange}
      onContentChange={onContentChange}
      onKeyDown={onKeyDown}
      onGlyphClick={onGlyphClick}
      onCollapseToggle={onCollapseToggle}
      inputRefs={inputRefs}
    />
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('FlatNodeList render', () => {
  it("renders a drag handle (aria-label='drag handle') for each node", () => {
    render(<Wrapper roots={[root]} />)
    // Drag handles are aria-hidden so we query by attribute, not role
    const handles = document.querySelectorAll('[aria-label="drag handle"]')
    // root + child = 2 nodes
    expect(handles.length).toBe(2)
  })

  it('grip handle appears before bullet glyph in DOM order', () => {
    render(<Wrapper roots={[root]} />)
    const handles = document.querySelectorAll('[aria-label="drag handle"]')
    const glyphs = screen.getAllByLabelText('zoom in')

    // For the first row (root), handle should come before glyph
    const handle = handles[0] as Element
    const glyph = glyphs[0] as Element
    const position = handle.compareDocumentPosition(glyph)
    expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('each node row is indented by depth * 24px (paddingLeft style)', () => {
    render(<Wrapper roots={[root]} />)
    // Child node is at depth 1 → paddingLeft should be '24px'
    const childInput = screen.getByDisplayValue('Child node')
    const childRow = childInput.closest('.flex.items-center') as HTMLElement
    expect(childRow.style.paddingLeft).toBe('24px')

    // Root node is at depth 0 → paddingLeft should be '0px'
    const rootInput = screen.getByDisplayValue('Root node')
    const rootRow = rootInput.closest('.flex.items-center') as HTMLElement
    expect(rootRow.style.paddingLeft).toBe('0px')
  })

  it('calls onGlyphClick with nodeId when bullet glyph is clicked', () => {
    const onGlyphClick = vi.fn()
    render(<Wrapper roots={[root]} onGlyphClick={onGlyphClick} />)
    const zoomBtn = screen.getAllByLabelText('zoom in')[0]
    fireEvent.click(zoomBtn)
    expect(onGlyphClick).toHaveBeenCalledWith('root')
  })

  it('calls onCollapseToggle with nodeId when collapse arrow is clicked (node with children)', () => {
    const onCollapseToggle = vi.fn()
    render(<Wrapper roots={[root]} onCollapseToggle={onCollapseToggle} />)
    // Root has children so collapse button should be visible
    const collapseBtn = screen.getByLabelText('collapse')
    fireEvent.click(collapseBtn)
    expect(onCollapseToggle).toHaveBeenCalledWith('root')
  })

  it('calls onContentChange when text is typed in an input', () => {
    const onContentChange = vi.fn()
    render(<Wrapper roots={[root]} onContentChange={onContentChange} />)
    const input = screen.getByDisplayValue('Root node')
    fireEvent.change(input, { target: { value: 'Root node updated' } })
    expect(onContentChange).toHaveBeenCalledWith('root', 'Root node updated')
  })

  it("collapsed node's children are not rendered in flat list", () => {
    const collapsedRoot = makeNode({
      id: 'root',
      content: 'Root node',
      collapsed: true,
      children: [child],
    })
    render(<Wrapper roots={[collapsedRoot]} />)
    // Child should not appear when parent is collapsed
    expect(screen.queryByDisplayValue('Child node')).toBeNull()
    // Root should still appear
    expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
  })
})
