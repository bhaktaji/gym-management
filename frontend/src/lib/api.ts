const TOKEN_KEY = 'gymkhata.tokens'

export type Tokens = { accessToken: string; refreshToken: string }

export function getTokens(): Tokens | null {
  const raw = localStorage.getItem(TOKEN_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Tokens
  } catch {
    return null
  }
}

export function setTokens(tokens: Tokens | null) {
  if (tokens) localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens))
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const tokens = getTokens()
  if (!tokens?.refreshToken) return null
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch('/api/v1/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        })
        if (!res.ok) {
          setTokens(null)
          return null
        }
        const data = await res.json()
        setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
        return data.accessToken as string
      } catch {
        return null
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

type RequestOptions = {
  method?: string
  body?: unknown
  auth?: boolean
  raw?: boolean
}

export async function api<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, raw = false } = options
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const tokens = getTokens()
    if (tokens) headers['Authorization'] = `Bearer ${tokens.accessToken}`
  }

  const send = async (): Promise<Response> =>
    fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })

  let res = await send()

  if (res.status === 401 && auth && getTokens()?.refreshToken) {
    const fresh = await refreshAccessToken()
    if (fresh) {
      headers['Authorization'] = `Bearer ${fresh}`
      res = await send()
    } else {
      onUnauthorized?.()
    }
  }

  if (!res.ok) {
    let code = 'ERROR'
    let message = res.statusText
    try {
      const data = await res.json()
      code = data.code ?? code
      message = data.message ?? message
    } catch {
      // non-JSON error body
    }
    throw new ApiError(res.status, code, message)
  }

  if (raw) return (await res.blob()) as T
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
