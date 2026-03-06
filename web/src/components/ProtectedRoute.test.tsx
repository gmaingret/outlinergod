import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

global.fetch = vi.fn()

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: false })
})

describe('AUTH-03: protected route redirect', () => {
  it('redirects unauthenticated user to /login', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/editor/test']}>
          <Routes>
            <Route path="/login" element={<div>login-page</div>} />
            <Route element={<ProtectedRoute />}>
              <Route path="/editor/:id" element={<div>editor-page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByText('login-page')).toBeInTheDocument())
  })

  it('renders protected content when authenticated', async () => {
    localStorage.setItem('refresh_token', 'valid-rt')
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ token: 'at', refresh_token: 'new-rt' }),
    })
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/editor/test']}>
          <Routes>
            <Route path="/login" element={<div>login-page</div>} />
            <Route element={<ProtectedRoute />}>
              <Route path="/editor/:id" element={<div>editor-page</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByText('editor-page')).toBeInTheDocument())
  })
})
