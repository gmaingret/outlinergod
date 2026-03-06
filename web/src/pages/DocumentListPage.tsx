import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

type DocumentItem = { id: string; title: string; updated_at: number }

type EditingState =
  | { mode: 'none' }
  | { mode: 'creating'; title: string }
  | { mode: 'renaming'; id: string; title: string }

type ContextMenu = { x: number; y: number; documentId: string; documentTitle: string } | null
type DeleteConfirm = { id: string; title: string } | null

export function DocumentListPage() {
  const { state, logout } = useAuth()
  const navigate = useNavigate()

  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [editing, setEditing] = useState<EditingState>({ mode: 'none' })
  const [contextMenu, setContextMenu] = useState<ContextMenu>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<DeleteConfirm>(null)

  const inputRef = useRef<HTMLInputElement>(null)

  // Fetch documents on mount / token change
  useEffect(() => {
    if (!state.accessToken) return
    setIsLoading(true)
    ;(async () => {
      try {
        const res = await fetch('/api/documents', {
          headers: { Authorization: `Bearer ${state.accessToken}` },
        })
        if (res.ok) {
          const data: { items: DocumentItem[] } = await res.json()
          const sorted = [...data.items].sort((a, b) => b.updated_at - a.updated_at)
          setDocuments(sorted)
        }
      } catch {
        // silent fail
      } finally {
        setIsLoading(false)
      }
    })()
  }, [state.accessToken])

  // Dismiss context menu on any window click
  useEffect(() => {
    if (!contextMenu) return
    const handler = () => setContextMenu(null)
    window.addEventListener('click', handler)
    return () => window.removeEventListener('click', handler)
  }, [contextMenu])

  // Auto-focus the inline input when editing starts
  useEffect(() => {
    if (editing.mode !== 'none' && inputRef.current) {
      inputRef.current.focus()
    }
  }, [editing.mode])

  // ---- Handlers ----

  function handleSignOut() {
    logout()
    navigate('/login')
  }

  function handleNewDocument() {
    setEditing({ mode: 'creating', title: '' })
  }

  function handleEditingChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (editing.mode === 'creating') {
      setEditing({ mode: 'creating', title: e.target.value })
    } else if (editing.mode === 'renaming') {
      setEditing({ mode: 'renaming', id: editing.id, title: e.target.value })
    }
  }

  function cancelEdit() {
    setEditing({ mode: 'none' })
  }

  async function confirmCreate() {
    if (editing.mode !== 'creating') return
    const title = editing.title.trim() || 'Untitled'
    setEditing({ mode: 'none' })
    try {
      const res = await fetch('/api/documents', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${state.accessToken}`,
        },
        body: JSON.stringify({ title, type: 'document', sort_order: Date.now().toString() }),
      })
      if (res.ok) {
        const newDoc: DocumentItem = await res.json()
        setDocuments(prev => [newDoc, ...prev])
      }
    } catch {
      // silent fail
    }
  }

  async function confirmRename() {
    if (editing.mode !== 'renaming') return
    const { id, title } = editing
    const trimmedTitle = title.trim() || 'Untitled'
    setEditing({ mode: 'none' })
    try {
      const res = await fetch(`/api/documents/${id}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${state.accessToken}`,
        },
        body: JSON.stringify({ title: trimmedTitle }),
      })
      if (res.ok) {
        const updated: DocumentItem = await res.json()
        setDocuments(prev =>
          prev.map(d => (d.id === id ? { ...d, title: updated.title, updated_at: updated.updated_at } : d)),
        )
      }
    } catch {
      // silent fail
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      if (editing.mode === 'creating') confirmCreate()
      else if (editing.mode === 'renaming') confirmRename()
    }
    if (e.key === 'Escape') cancelEdit()
  }

  function handleRowContextMenu(e: React.MouseEvent, doc: DocumentItem) {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, documentId: doc.id, documentTitle: doc.title })
  }

  function handleRowClick(doc: DocumentItem) {
    setContextMenu(null)
    navigate(`/editor/${doc.id}`)
  }

  function handleRename() {
    if (!contextMenu) return
    setEditing({ mode: 'renaming', id: contextMenu.documentId, title: contextMenu.documentTitle })
    setContextMenu(null)
  }

  function handleDeleteClick() {
    if (!contextMenu) return
    setDeleteConfirm({ id: contextMenu.documentId, title: contextMenu.documentTitle })
    setContextMenu(null)
  }

  async function handleDeleteConfirm() {
    if (!deleteConfirm) return
    const { id } = deleteConfirm
    setDeleteConfirm(null)
    try {
      const res = await fetch(`/api/documents/${id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${state.accessToken}` },
      })
      if (res.ok) {
        setDocuments(prev => prev.filter(d => d.id !== id))
      }
    } catch {
      // silent fail
    }
  }

  // ---- Render ----

  return (
    <div className="min-h-screen bg-white">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
        <span className="text-lg font-semibold text-gray-900">OutlinerGod</span>
        <button
          onClick={handleSignOut}
          className="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
        >
          Sign out
        </button>
      </header>

      {/* New document button */}
      <div className="p-4">
        <button
          onClick={handleNewDocument}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-md cursor-pointer"
        >
          + New document
        </button>
      </div>

      {/* Inline create input */}
      {editing.mode === 'creating' && (
        <div className="flex items-center px-4 py-3 border-b border-gray-100">
          <input
            ref={inputRef}
            type="text"
            value={editing.title}
            onChange={handleEditingChange}
            onKeyDown={handleKeyDown}
            placeholder="Document title"
            autoFocus
            className="flex-1 outline-none text-sm text-gray-900"
          />
        </div>
      )}

      {/* Loading skeletons */}
      {isLoading && (
        <div>
          {[0, 1, 2].map(i => (
            <div
              key={i}
              className="animate-pulse flex items-center px-4 py-3 border-b border-gray-100"
            >
              <div className="h-4 bg-gray-200 rounded w-48" />
            </div>
          ))}
        </div>
      )}

      {/* Empty state */}
      {!isLoading && documents.length === 0 && editing.mode !== 'creating' && (
        <p className="px-4 py-3 text-sm text-gray-500">
          <span>No documents yet</span>
          {'. Click \'+ New document\' to create one.'}
        </p>
      )}

      {/* Document list */}
      {!isLoading && documents.map(doc => (
        <div key={doc.id}>
          {editing.mode === 'renaming' && editing.id === doc.id ? (
            <div className="flex items-center px-4 py-3 border-b border-gray-100">
              <span className="mr-2">📄</span>
              <input
                ref={inputRef}
                type="text"
                value={editing.title}
                onChange={handleEditingChange}
                onKeyDown={handleKeyDown}
                autoFocus
                className="flex-1 outline-none text-sm text-gray-900"
              />
            </div>
          ) : (
            <div
              className="flex items-center px-4 py-3 border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
              onClick={() => handleRowClick(doc)}
              onContextMenu={e => handleRowContextMenu(e, doc)}
            >
              <span className="mr-2">📄</span>
              <span className="text-sm text-gray-900">{doc.title}</span>
            </div>
          )}
        </div>
      ))}

      {/* Context menu */}
      {contextMenu && (
        <div
          className="fixed z-50 bg-white border border-gray-200 rounded shadow-lg py-1 min-w-32"
          style={{ top: contextMenu.y, left: contextMenu.x }}
          onClick={e => e.stopPropagation()}
        >
          <button
            className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 cursor-pointer"
            onClick={handleRename}
          >
            Rename
          </button>
          <button
            className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 cursor-pointer"
            onClick={handleDeleteClick}
          >
            Delete
          </button>
        </div>
      )}

      {/* Delete confirmation dialog */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div role="dialog" className="bg-white rounded-lg p-6 max-w-sm w-full mx-4 shadow-xl">
            <p className="text-sm text-gray-700 mb-4">
              Delete &apos;{deleteConfirm.title}&apos;? This cannot be undone.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 cursor-pointer"
                onClick={() => setDeleteConfirm(null)}
              >
                Cancel
              </button>
              <button
                className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded cursor-pointer"
                onClick={handleDeleteConfirm}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
