import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getTokens, setTokens, setUnauthorizedHandler } from '../lib/api'

export type AuthUser = {
  id: string
  name: string
  role: 'SUPER_ADMIN' | 'GYM_OWNER'
  gymId: string
  phone: string
}

type AuthState = {
  user: AuthUser | null
  loading: boolean
  login: (phone: string, password: string) => Promise<AuthUser>
  logout: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshUser = useCallback(async () => {
    if (!getTokens()) {
      setUser(null)
      setLoading(false)
      return
    }
    try {
      const data = await api<{ accessToken: string; refreshToken: string; user: AuthUser }>(
        '/api/v1/auth/refresh',
        { method: 'POST', body: { refreshToken: getTokens()!.refreshToken }, auth: false },
      )
      setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
      setUser(data.user)
    } catch {
      setTokens(null)
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setTokens(null)
      setUser(null)
    })
    void refreshUser()
  }, [refreshUser])

  const login = useCallback(async (phone: string, password: string) => {
    const data = await api<{ accessToken: string; refreshToken: string; user: AuthUser }>(
      '/api/v1/auth/login',
      { method: 'POST', body: { phone, password }, auth: false },
    )
    setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
    setUser(data.user)
    return data.user
  }, [])

  const logout = useCallback(() => {
    setTokens(null)
    setUser(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, logout, refreshUser }),
    [user, loading, login, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
