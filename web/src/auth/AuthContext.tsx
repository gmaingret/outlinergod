import { createContext, useContext, useEffect, useState } from 'react'
import { getOrCreateDeviceId } from './api'

export interface AuthState {
  accessToken: string | null
  userId: string | null
  isLoading: boolean
}

interface AuthContextValue {
  state: AuthState
  login: (idToken: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodeUserId(token: string): string | null {
  try {
    return JSON.parse(atob(token.split('.')[1])).sub ?? null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    accessToken: null,
    userId: null,
    isLoading: true,
  })

  useEffect(() => {
    const refreshToken = localStorage.getItem('refresh_token')
    if (!refreshToken) {
      setState(s => ({ ...s, isLoading: false }))
      return
    }
    fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    })
      .then(r => (r.ok ? r.json() : Promise.reject()))
      .then((data: { token: string; refresh_token: string }) => {
        localStorage.setItem('refresh_token', data.refresh_token)
        setState({
          accessToken: data.token,
          userId: decodeUserId(data.token),
          isLoading: false,
        })
      })
      .catch(() => {
        localStorage.removeItem('refresh_token')
        setState({ accessToken: null, userId: null, isLoading: false })
      })
  }, [])

  const login = async (idToken: string) => {
    const deviceId = getOrCreateDeviceId()
    const res = await fetch('/api/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id_token: idToken, device_id: deviceId }),
    })
    if (!res.ok) throw new Error('Auth failed')
    const data: { token: string; refresh_token: string; user?: { id: string } } = await res.json()
    localStorage.setItem('refresh_token', data.refresh_token)
    setState({ accessToken: data.token, userId: data.user?.id ?? decodeUserId(data.token), isLoading: false })
  }

  const logout = () => {
    localStorage.removeItem('refresh_token')
    setState({ accessToken: null, userId: null, isLoading: false })
  }

  return (
    <AuthContext.Provider value={{ state, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
