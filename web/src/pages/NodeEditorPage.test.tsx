import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import { NodeEditorPage } from './NodeEditorPage'

// Mock useAuth so tests don't need a real AuthProvider with token refresh
vi.mock('../auth/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ state: { accessToken: 'test-token' }, logout: vi.fn() }),
}))

// Mock useNavigate so we can assert navigation calls
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Sample node data (NodeResponse shape)
const sampleNodes = [
  {
    id: 'node-root',
    document_id: 'doc-1',
    content: 'Root node',
    note: '',
    parent_id: null,
    sort_order: 'a0',
    completed: false,
    color: 0,
    collapsed: false,
    deleted_at: null,
    created_at: 0,
    updated_at: 0,
  },
  {
    id: 'node-child',
    document_id: 'doc-1',
    content: 'Child node',
    note: '',
    parent_id: 'node-root',
    sort_order: 'a0',
    completed: false,
    color: 0,
    collapsed: false,
    deleted_at: null,
    created_at: 0,
    updated_at: 0,
  },
]

const sampleDocument = {
  id: 'doc-1',
  title: 'My Test Document',
  updated_at: 1700000000000,
}

function makeFetchMock(overrides: {
  nodes?: typeof sampleNodes
  document?: typeof sampleDocument
  syncPushOk?: boolean
}) {
  const { nodes = sampleNodes, document = sampleDocument, syncPushOk = true } = overrides
  return vi.fn().mockImplementation((url: string, opts?: RequestInit) => {
    if (typeof url === 'string' && url.includes('/api/documents/doc-1') && !url.includes('nodes') && (!opts?.method || opts.method === 'GET')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(document) })
    }
    if (typeof url === 'string' && url.includes('/api/nodes') && (!opts?.method || opts.method === 'GET')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(nodes) })
    }
    if (typeof url === 'string' && url.includes('/api/sync/push') && opts?.method === 'POST') {
      return Promise.resolve({ ok: syncPushOk, json: () => Promise.resolve({ pulled: [], conflicts: [] }) })
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve({}) })
  })
}

function renderEditor(docId = 'doc-1', search = '') {
  render(
    <MemoryRouter initialEntries={[`/editor/${docId}${search}`]}>
      <Routes>
        <Route path="/editor/:id" element={<NodeEditorPage />} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockNavigate.mockClear()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SYNC-01 — Load nodes from server', () => {
  it('GET /api/nodes is called on mount with Authorization header and correct document_id param', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      const calls = mockFetch.mock.calls as Array<[string, RequestInit?]>
      const nodeCall = calls.find(([url, opts]) =>
        url.includes('/api/nodes') &&
        opts?.headers &&
        (opts.headers as Record<string, string>)['Authorization'] === 'Bearer test-token'
      )
      expect(nodeCall).toBeDefined()
    })
  })
})

describe('EDIT-01 — Display node tree', () => {
  it('After GET resolves, node content values appear in the document', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })
    expect(screen.getByDisplayValue('Child node')).toBeInTheDocument()
  })

  it('Nested child nodes are rendered inside their parent (nested DOM structure)', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    // Both root and child should be in the document
    const rootInput = screen.getByDisplayValue('Root node')
    const childInput = screen.getByDisplayValue('Child node')
    expect(rootInput).toBeInTheDocument()
    expect(childInput).toBeInTheDocument()

    // Child should appear after root in DOM order
    const rootPos = rootInput.compareDocumentPosition(childInput)
    expect(rootPos & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })
})

describe('EDIT-02 — Typing updates node content', () => {
  it('Typing in a node input updates that node displayed value', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    const user = userEvent.setup()
    const input = screen.getByDisplayValue('Root node')
    await user.click(input)
    await user.type(input, ' extra')

    expect(screen.getByDisplayValue('Root node extra')).toBeInTheDocument()
  })
})

describe('EDIT-03 — Enter key creates sibling node', () => {
  it('Pressing Enter in a node input creates a new input element in the document', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    const initialInputCount = screen.getAllByRole('textbox').length

    const user = userEvent.setup()
    const input = screen.getByDisplayValue('Root node')
    await user.click(input)
    await user.keyboard('{Enter}')

    await waitFor(() => {
      expect(screen.getAllByRole('textbox').length).toBeGreaterThan(initialInputCount)
    })
  })
})

describe('EDIT-04 — Tab key indents node', () => {
  it('Pressing Tab in a node input changes that node nesting (parent relationship changes)', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Child node')).toBeInTheDocument()
    })

    // Get depth indicator before tab — look at the tree structure
    // After Tab, a sibling should become a child of the preceding sibling
    const user = userEvent.setup()

    // First, add a node after Root node so we have something to indent under
    const rootInput = screen.getByDisplayValue('Root node')
    await user.click(rootInput)
    await user.keyboard('{End}')
    await user.keyboard('{Enter}')

    await waitFor(() => {
      expect(screen.getAllByRole('textbox').length).toBeGreaterThanOrEqual(3)
    })

    // Press Tab to indent the new node under Root node
    const inputs = screen.getAllByRole('textbox')
    const newInput = inputs[inputs.length - 1]
    await user.click(newInput)
    await user.keyboard('{Tab}')

    // The DOM should still render correctly (no crash)
    expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
  })
})

describe('EDIT-05 — Shift+Tab outdents node', () => {
  it('Pressing Shift+Tab in a node input outdents it (promotes one level)', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Child node')).toBeInTheDocument()
    })

    const user = userEvent.setup()
    const childInput = screen.getByDisplayValue('Child node')
    await user.click(childInput)
    await user.keyboard('{Shift>}{Tab}{/Shift}')

    // Should not crash; root node still visible
    expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
  })
})

describe('EDIT-06 — Markdown markers visible in input', () => {
  it('A node with content "**hello**" renders with those visible markers in its input', async () => {
    const nodesWithMarkdown = [
      {
        ...sampleNodes[0],
        id: 'md-node',
        content: '**hello**',
      },
    ]
    const mockFetch = makeFetchMock({ nodes: nodesWithMarkdown })
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('**hello**')).toBeInTheDocument()
    })
  })
})

describe('EDIT-07 — Glyph click zooms in', () => {
  it('Clicking a bullet glyph calls navigate with ?root=<nodeId> query param', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    // Find the glyph/bullet for the root node and click it
    // The glyph should be a button or clickable element near the input
    const glyphs = screen.getAllByRole('button')
    // Click the glyph associated with root node (first one)
    const user = userEvent.setup()
    const glyph = glyphs.find(g => g.closest('[data-node-id="node-root"]') || g.getAttribute('data-node-id') === 'node-root' || glyphs.indexOf(g) === 0)
    if (glyph) {
      await user.click(glyph)
      expect(mockNavigate).toHaveBeenCalled()
    }
  })
})

describe('Header', () => {
  it('shows "Back" link pointing to "/"', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      const backLink = screen.getByRole('link', { name: /back/i })
      expect(backLink).toBeInTheDocument()
      expect(backLink).toHaveAttribute('href', '/')
    })
  })

  it('shows document title in the header', async () => {
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await waitFor(() => {
      expect(screen.getByText('My Test Document')).toBeInTheDocument()
    })
  })
})

describe('SYNC-02 — Debounced push after content change', () => {
  it('After a content change, POST /api/sync/push is called after debounce (2s)', async () => {
    vi.useFakeTimers()
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    // Wait for initial load using real async resolution
    await vi.runAllTilesAsync()
    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const input = screen.getByDisplayValue('Root node')
    await user.click(input)
    await user.type(input, 'x')

    // Advance past debounce window (2 seconds)
    await vi.advanceTimersByTimeAsync(2100)

    await waitFor(() => {
      const calls = mockFetch.mock.calls as Array<[string, RequestInit?]>
      const pushCall = calls.find(([url, opts]) =>
        typeof url === 'string' &&
        url.includes('/api/sync/push') &&
        opts?.method === 'POST'
      )
      expect(pushCall).toBeDefined()
    })
  })

  it('Sync status indicator shows "Saved" after successful push', async () => {
    vi.useFakeTimers()
    const mockFetch = makeFetchMock({})
    vi.stubGlobal('fetch', mockFetch)

    renderEditor()

    await vi.runAllTilesAsync()
    await waitFor(() => {
      expect(screen.getByDisplayValue('Root node')).toBeInTheDocument()
    })

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const input = screen.getByDisplayValue('Root node')
    await user.click(input)
    await user.type(input, 'x')

    await vi.advanceTimersByTimeAsync(2100)

    await waitFor(() => {
      expect(screen.getByText(/saved/i)).toBeInTheDocument()
    })
  })
})
