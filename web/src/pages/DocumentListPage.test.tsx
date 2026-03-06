import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { DocumentListPage } from './DocumentListPage'
import { vi, describe, it, expect, beforeEach } from 'vitest'

global.fetch = vi.fn()

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

// Helper: mock a successful auth refresh response (first call from AuthProvider)
function mockAuthRefresh() {
  return {
    ok: true,
    json: () =>
      Promise.resolve({ token: 'test-access-token', refresh_token: 'rt-new' }),
  }
}

// Helper: mock GET /api/documents response
function mockGetDocuments(items: Array<{ id: string; title: string; updated_at: number }>) {
  return {
    ok: true,
    json: () => Promise.resolve({ items }),
  }
}

// Helper: render DocumentListPage inside authenticated context
function renderDocList(
  mockFetchSequence: Array<{ ok: boolean; json: () => Promise<unknown> }>,
) {
  mockFetchSequence.forEach(resp =>
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(resp),
  )
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/login" element={<div>login-page</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<DocumentListPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

const sampleDocs = [
  { id: 'doc-1', title: 'First Document', updated_at: 1700000000000 },
  { id: 'doc-2', title: 'Second Document', updated_at: 1700000001000 },
]

describe('DOC-01 — View', () => {
  it('Test 1: renders skeleton rows while GET /api/documents is in-flight', async () => {
    localStorage.setItem('refresh_token', 'rt')
    // Auth refresh resolves immediately; GET /api/documents is held pending
    let resolveDocuments!: (value: unknown) => void
    ;(global.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce(mockAuthRefresh())
      .mockReturnValueOnce({
        ok: true,
        json: () => new Promise(res => { resolveDocuments = res }),
      })

    renderDocList([])

    // While documents are loading, skeleton placeholders should be visible
    await waitFor(() => {
      const skeletons = document.querySelectorAll('.animate-pulse')
      expect(skeletons.length).toBeGreaterThanOrEqual(3)
    })

    // Resolve to avoid dangling promise
    resolveDocuments({ items: [] })
  })

  it('Test 2: renders document titles after successful GET /api/documents', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )
    expect(screen.getByText('Second Document')).toBeInTheDocument()
  })

  it('Test 3: renders empty state text when GET returns empty items array', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments([])])

    await waitFor(() =>
      expect(screen.getByText('No documents yet')).toBeInTheDocument(),
    )
  })

  it('Test 4: renders "+ New document" button in populated, empty, and loading states', async () => {
    // Populated state
    localStorage.setItem('refresh_token', 'rt')
    const { unmount: u1 } = renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])
    await waitFor(() =>
      expect(screen.getByText('+ New document')).toBeInTheDocument(),
    )
    u1()

    // Empty state
    localStorage.setItem('refresh_token', 'rt')
    vi.clearAllMocks()
    const { unmount: u2 } = renderDocList([mockAuthRefresh(), mockGetDocuments([])])
    await waitFor(() =>
      expect(screen.getByText('No documents yet')).toBeInTheDocument(),
    )
    expect(screen.getByText('+ New document')).toBeInTheDocument()
    u2()

    // Loading state — button should be visible even before fetch completes
    localStorage.setItem('refresh_token', 'rt')
    vi.clearAllMocks()
    ;(global.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce(mockAuthRefresh())
      .mockReturnValueOnce({ ok: true, json: () => new Promise(() => {}) })
    renderDocList([])
    // Wait for auth to complete so DocumentListPage renders
    await waitFor(() =>
      expect(screen.getByText('+ New document')).toBeInTheDocument(),
    )
  })

  it('Test 5: renders "OutlinerGod" header and "Sign out" button', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('OutlinerGod')).toBeInTheDocument(),
    )
    expect(screen.getByText('Sign out')).toBeInTheDocument()
  })
})

describe('DOC-02 — Create', () => {
  it('Test 6: clicking "+ New document" shows an inline text input (autoFocused)', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('+ New document'))

    // An input should appear (autoFocused)
    const input = screen.getByRole('textbox')
    expect(input).toBeInTheDocument()
    expect(document.activeElement).toBe(input)
  })

  it('Test 7: typing title and pressing Enter calls POST /api/documents and appends new doc', async () => {
    localStorage.setItem('refresh_token', 'rt')
    const newDoc = { id: 'doc-3', title: 'My New Doc', updated_at: 1700000002000, type: 'document', sort_order: 'aV' }
    const mockPost = {
      ok: true,
      json: () => Promise.resolve(newDoc),
    }
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs), mockPost])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('+ New document'))

    const input = screen.getByRole('textbox')
    await user.type(input, 'My New Doc')
    await user.keyboard('{Enter}')

    await waitFor(() => {
      const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls
      const postCall = calls.find(
        (c: [string, RequestInit]) => c[0] === '/api/documents' && c[1]?.method === 'POST',
      )
      expect(postCall).toBeDefined()
    })

    await waitFor(() =>
      expect(screen.getByText('My New Doc')).toBeInTheDocument(),
    )
  })

  it('Test 8: pressing Escape cancels creation with no POST call', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('+ New document'))

    const input = screen.getByRole('textbox')
    await user.type(input, 'Draft title')
    await user.keyboard('{Escape}')

    // Input should disappear
    await waitFor(() =>
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument(),
    )

    // No POST should have been called
    const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls
    const postCall = calls.find(
      (c: [string, RequestInit]) => c[0] === '/api/documents' && c[1]?.method === 'POST',
    )
    expect(postCall).toBeUndefined()
  })
})

describe('DOC-03 — Rename', () => {
  it('Test 9: right-clicking a document row shows context menu with "Rename" and "Delete" options', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const row = screen.getByText('First Document')
    fireEvent.contextMenu(row)

    await waitFor(() =>
      expect(screen.getByText('Rename')).toBeInTheDocument(),
    )
    expect(screen.getByText('Delete')).toBeInTheDocument()
  })

  it('Test 10: clicking "Rename" shows pre-filled input; Enter calls PATCH and updates title', async () => {
    localStorage.setItem('refresh_token', 'rt')
    const mockPatch = {
      ok: true,
      json: () =>
        Promise.resolve({ id: 'doc-1', title: 'Renamed Doc', updated_at: 1700000003000 }),
    }
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs), mockPatch])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const row = screen.getByText('First Document')
    fireEvent.contextMenu(row)

    await waitFor(() =>
      expect(screen.getByText('Rename')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('Rename'))

    // Input should appear pre-filled with the existing title
    const input = screen.getByRole('textbox') as HTMLInputElement
    expect(input).toBeInTheDocument()
    expect(input.value).toBe('First Document')

    // Clear and type new title, then submit
    await user.clear(input)
    await user.type(input, 'Renamed Doc')
    await user.keyboard('{Enter}')

    await waitFor(() => {
      const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls
      const patchCall = calls.find(
        (c: [string, RequestInit]) =>
          typeof c[0] === 'string' &&
          c[0].startsWith('/api/documents/doc-1') &&
          c[1]?.method === 'PATCH',
      )
      expect(patchCall).toBeDefined()
    })

    await waitFor(() =>
      expect(screen.getByText('Renamed Doc')).toBeInTheDocument(),
    )
  })
})

describe('DOC-04 — Delete', () => {
  it('Test 11: clicking "Delete" in context menu shows confirmation dialog with document title', async () => {
    localStorage.setItem('refresh_token', 'rt')
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs)])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const row = screen.getByText('First Document')
    fireEvent.contextMenu(row)

    await waitFor(() =>
      expect(screen.getByText('Delete')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('Delete'))

    // Confirmation dialog should appear containing the document title
    await waitFor(() =>
      expect(screen.getByRole('dialog')).toBeInTheDocument(),
    )
    expect(screen.getByRole('dialog')).toHaveTextContent('First Document')
  })

  it('Test 12: confirming delete calls DELETE /api/documents/:id and removes row', async () => {
    localStorage.setItem('refresh_token', 'rt')
    const mockDelete = {
      ok: true,
      json: () => Promise.resolve({ deleted_ids: ['doc-1'] }),
    }
    renderDocList([mockAuthRefresh(), mockGetDocuments(sampleDocs), mockDelete])

    await waitFor(() =>
      expect(screen.getByText('First Document')).toBeInTheDocument(),
    )

    const row = screen.getByText('First Document')
    fireEvent.contextMenu(row)

    await waitFor(() =>
      expect(screen.getByText('Delete')).toBeInTheDocument(),
    )

    const user = userEvent.setup()
    await user.click(screen.getByText('Delete'))

    // Confirm the deletion
    await waitFor(() =>
      expect(screen.getByRole('dialog')).toBeInTheDocument(),
    )
    const confirmButton = screen.getByRole('button', { name: /confirm|delete|yes/i })
    await user.click(confirmButton)

    await waitFor(() => {
      const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls
      const deleteCall = calls.find(
        (c: [string, RequestInit]) =>
          typeof c[0] === 'string' &&
          c[0].includes('/api/documents/doc-1') &&
          c[1]?.method === 'DELETE',
      )
      expect(deleteCall).toBeDefined()
    })

    await waitFor(() =>
      expect(screen.queryByText('First Document')).not.toBeInTheDocument(),
    )
  })
})
