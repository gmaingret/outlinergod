import { useEffect, useRef, useState, useCallback } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getOrCreateDeviceId } from '../auth/api'
import {
  buildTree,
  filterSubtree,
  indentNode,
  insertSiblingNode,
  outdentNode,
  type TreeNode,
} from '../editor/treeHelpers'
import { FlatNodeList } from '../editor/FlatNodeList'
import { hlcGenerate } from '../sync/hlc'

// ---- Types ----

interface NodeSyncRecord {
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

// ---- Helpers ----

function toSyncRecord(node: TreeNode, deviceId: string): NodeSyncRecord {
  const hlc = hlcGenerate(deviceId)
  return {
    id: node.id,
    document_id: node.document_id,
    content: node.content,
    content_hlc: hlc,
    note: node.note,
    note_hlc: hlc,
    parent_id: node.parent_id,
    parent_id_hlc: hlc,
    sort_order: node.sort_order,
    sort_order_hlc: hlc,
    completed: node.completed ? 1 : 0,
    completed_hlc: hlc,
    color: node.color,
    color_hlc: hlc,
    collapsed: node.collapsed ? 1 : 0,
    collapsed_hlc: hlc,
    deleted_at: node.deleted_at,
    deleted_hlc: hlc,
    device_id: deviceId,
  }
}

function dfsUpdateContent(nodes: TreeNode[], nodeId: string, value: string): TreeNode[] {
  return nodes.map(n => {
    if (n.id === nodeId) {
      return { ...n, content: value, children: n.children }
    }
    return { ...n, children: dfsUpdateContent(n.children, nodeId, value) }
  })
}

function dfsToggleCollapsed(nodes: TreeNode[], nodeId: string): TreeNode[] {
  return nodes.map(n => {
    if (n.id === nodeId) {
      return { ...n, collapsed: !n.collapsed, children: n.children }
    }
    return { ...n, children: dfsToggleCollapsed(n.children, nodeId) }
  })
}

function dfsFindNode(nodes: TreeNode[], nodeId: string): TreeNode | null {
  for (const n of nodes) {
    if (n.id === nodeId) return n
    const found = dfsFindNode(n.children, nodeId)
    if (found) return found
  }
  return null
}

function generateNodeId(): string {
  return crypto.randomUUID()
}

// ---- Main Page ----

export function NodeEditorPage() {
  const { state } = useAuth()
  const { id: docId } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const rootNodeId = searchParams.get('root')

  const [nodes, setNodes] = useState<TreeNode[]>([])
  const [docTitle, setDocTitle] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [syncStatus, setSyncStatus] = useState<'saved' | 'saving' | 'offline'>('saved')

  const pendingChanges = useRef<Map<string, TreeNode>>(new Map())
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const inputRefs = useRef<Map<string, HTMLInputElement>>(new Map())
  const focusNodeId = useRef<string | null>(null)
  const accessTokenRef = useRef(state.accessToken)

  // Keep accessToken ref in sync for use in async callbacks
  useEffect(() => {
    accessTokenRef.current = state.accessToken
  }, [state.accessToken])

  // Load nodes and document title on mount
  useEffect(() => {
    if (!docId) return
    setIsLoading(true)
    ;(async () => {
      try {
        const token = accessTokenRef.current
        const [nodesRes, docRes] = await Promise.all([
          fetch(`/api/nodes?document_id=${docId}`, {
            headers: { Authorization: `Bearer ${token}` },
          }),
          fetch(`/api/documents/${docId}`, {
            headers: { Authorization: `Bearer ${token}` },
          }),
        ])
        if (nodesRes.ok) {
          const data = await nodesRes.json()
          const loaded = buildTree(Array.isArray(data) ? data : data.nodes ?? [])
          if (loaded.length === 0) {
            const firstNodeId = generateNodeId()
            const firstNode: TreeNode = {
              id: firstNodeId,
              document_id: docId ?? '',
              content: '',
              note: '',
              parent_id: null,
              sort_order: 'a0000',
              completed: false,
              color: 0,
              collapsed: false,
              deleted_at: null,
              created_at: Date.now(),
              updated_at: Date.now(),
              children: [],
            }
            setNodes([firstNode])
            focusNodeId.current = firstNodeId
          } else {
            setNodes(loaded)
          }
        }
        if (docRes.ok) {
          const doc = await docRes.json()
          setDocTitle(doc.title ?? '')
        }
      } catch {
        // silent fail
      } finally {
        setIsLoading(false)
      }
    })()
  }, [docId]) // eslint-disable-line react-hooks/exhaustive-deps

  // Focus effect: after render, focus the pending node
  useEffect(() => {
    if (focusNodeId.current) {
      const el = inputRefs.current.get(focusNodeId.current)
      if (el) {
        el.focus()
        focusNodeId.current = null
      }
    }
  })

  // Flush pending changes to server
  const flushPendingChanges = useCallback(async () => {
    if (pendingChanges.current.size === 0) return
    const toFlush = new Map(pendingChanges.current)
    pendingChanges.current = new Map()

    const deviceId = getOrCreateDeviceId()
    const records: NodeSyncRecord[] = Array.from(toFlush.values()).map(n =>
      toSyncRecord(n, deviceId)
    )

    setSyncStatus('saving')
    try {
      const res = await fetch('/api/sync/push', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessTokenRef.current}`,
        },
        body: JSON.stringify({
          device_id: deviceId,
          nodes: records,
          documents: [],
          bookmarks: [],
          settings: null,
        }),
      })
      if (res.ok) {
        setSyncStatus('saved')
      } else {
        // Re-queue failed changes
        for (const [k, v] of toFlush) {
          if (!pendingChanges.current.has(k)) {
            pendingChanges.current.set(k, v)
          }
        }
        setSyncStatus('offline')
      }
    } catch {
      // Re-queue failed changes
      for (const [k, v] of toFlush) {
        if (!pendingChanges.current.has(k)) {
          pendingChanges.current.set(k, v)
        }
      }
      setSyncStatus('offline')
    }
  }, [])

  // Queue a change with 2s debounce
  const queueChange = useCallback((node: TreeNode) => {
    pendingChanges.current.set(node.id, node)
    if (debounceTimer.current) clearTimeout(debounceTimer.current)
    debounceTimer.current = setTimeout(() => {
      flushPendingChanges()
    }, 2000)
  }, [flushPendingChanges])

  // Unmount cleanup: clear timer
  useEffect(() => {
    return () => {
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current)
        debounceTimer.current = null
      }
    }
  }, [])

  // ---- Event handlers ----

  function handleContentChange(nodeId: string, value: string) {
    setNodes(prev => {
      const updated = dfsUpdateContent(prev, nodeId, value)
      // Queue the updated node
      const updatedNode = dfsFindNode(updated, nodeId)
      if (updatedNode) {
        queueChange(updatedNode)
      }
      return updated
    })
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>, nodeId: string) {
    if (e.key === 'Enter') {
      e.preventDefault()
      const newNodeId = generateNodeId()
      const parentNode = dfsFindNode(nodes, nodeId)
      const newNode: TreeNode = {
        id: newNodeId,
        document_id: docId ?? '',
        content: '',
        note: '',
        parent_id: parentNode?.parent_id ?? null,
        sort_order: '',
        completed: false,
        color: 0,
        collapsed: false,
        deleted_at: null,
        created_at: Date.now(),
        updated_at: Date.now(),
        children: [],
      }
      setNodes(prev => {
        const updated = insertSiblingNode([...prev], nodeId, newNode)
        return updated
      })
      queueChange(newNode)
      focusNodeId.current = newNodeId
    } else if (e.key === 'Tab' && !e.shiftKey) {
      e.preventDefault()
      setNodes(prev => indentNode([...prev], nodeId))
    } else if (e.key === 'Tab' && e.shiftKey) {
      e.preventDefault()
      setNodes(prev => outdentNode([...prev], nodeId))
    }
  }

  function handleGlyphClick(nodeId: string) {
    navigate({ search: `?root=${nodeId}` })
  }

  function handleCollapseToggle(nodeId: string) {
    setNodes(prev => {
      const updated = dfsToggleCollapsed(prev, nodeId)
      const node = dfsFindNode(updated, nodeId)
      if (node) queueChange(node)
      return updated
    })
  }

  // ---- Render helpers ----

  function renderSyncStatus() {
    if (syncStatus === 'saving') {
      return <span className="text-xs text-gray-400">Saving...</span>
    }
    if (syncStatus === 'offline') {
      return <span className="text-xs text-red-500">Offline</span>
    }
    return <span className="text-xs text-green-600">Saved</span>
  }

  function renderNodes() {
    if (isLoading) {
      return (
        <div className="p-4">
          <div className="animate-pulse h-4 bg-gray-200 rounded w-48 mb-2" />
          <div className="animate-pulse h-4 bg-gray-200 rounded w-64 mb-2" />
        </div>
      )
    }

    if (rootNodeId) {
      const subtree = filterSubtree(nodes, rootNodeId)
      if (!subtree) {
        return <p className="p-4 text-sm text-gray-500">Node not found.</p>
      }
      return (
        <div className="p-4">
          <h1 className="text-lg font-semibold text-gray-900 mb-3">{subtree.content}</h1>
          <FlatNodeList
            roots={subtree.children}
            onTreeChange={setNodes}
            queueChange={queueChange}
            onContentChange={handleContentChange}
            onKeyDown={handleKeyDown}
            onGlyphClick={handleGlyphClick}
            onCollapseToggle={handleCollapseToggle}
            inputRefs={inputRefs}
          />
        </div>
      )
    }

    return (
      <div className="p-4">
        <FlatNodeList
          roots={nodes}
          onTreeChange={setNodes}
          queueChange={queueChange}
          onContentChange={handleContentChange}
          onKeyDown={handleKeyDown}
          onGlyphClick={handleGlyphClick}
          onCollapseToggle={handleCollapseToggle}
          inputRefs={inputRefs}
        />
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-white">
      {/* Header */}
      <header className="flex items-center gap-4 px-6 py-4 border-b border-gray-200">
        <Link to="/" className="text-sm text-blue-600 hover:text-blue-800">
          ← Back
        </Link>
        <span className="flex-1 text-sm font-medium text-gray-900 truncate">{docTitle}</span>
        {renderSyncStatus()}
      </header>

      {/* Node tree */}
      {renderNodes()}
    </div>
  )
}
