import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'

// Global fetch mock — replaced per test
global.fetch = vi.fn()

function StatusDisplay() {
  const { state } = useAuth()
  if (state.isLoading) return <div>loading</div>
  if (state.accessToken) return <div>authed</div>
  return <div>not-authed</div>
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('AUTH-02: silent refresh on mount', () => {
  it('shows not-authed when no refresh_token in localStorage', async () => {
    render(<AuthProvider><StatusDisplay /></AuthProvider>)
    await waitFor(() => expect(screen.getByText('not-authed')).toBeInTheDocument())
  })

  it('silently refreshes when refresh_token exists in localStorage', async () => {
    localStorage.setItem('refresh_token', 'stored-rt')
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ token: 'new-access-token', refresh_token: 'new-rt' }),
    })
    render(<AuthProvider><StatusDisplay /></AuthProvider>)
    await waitFor(() => expect(screen.getByText('authed')).toBeInTheDocument())
    expect(localStorage.getItem('refresh_token')).toBe('new-rt')
  })

  it('shows not-authed when refresh call fails (expired token)', async () => {
    localStorage.setItem('refresh_token', 'expired-rt')
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ ok: false })
    render(<AuthProvider><StatusDisplay /></AuthProvider>)
    await waitFor(() => expect(screen.getByText('not-authed')).toBeInTheDocument())
    expect(localStorage.getItem('refresh_token')).toBeNull()
  })
})

describe('AUTH-01: login with Google id_token', () => {
  it('calls /api/auth/google with { id_token, device_id } and stores refresh token', async () => {
    let loginFn: ((idToken: string) => Promise<void>) | null = null
    function LoginCapture() {
      const { login } = useAuth()
      loginFn = login
      return null
    }

    render(<AuthProvider><LoginCapture /><StatusDisplay /></AuthProvider>)
    await waitFor(() => expect(screen.getByText('not-authed')).toBeInTheDocument())

    // Trigger login — no refresh_token in localStorage so mount makes no fetch call
    ;(global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ token: 'at', refresh_token: 'rt', user: { id: 'uid' } }),
    })
    await loginFn!('test-id-token')

    expect(global.fetch).toHaveBeenCalledWith(
      '/api/auth/google',
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"id_token":"test-id-token"'),
      }),
    )
    await waitFor(() => expect(screen.getByText('authed')).toBeInTheDocument())
  })
})
