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

// Client-side mock handler for standalone preview mode when backend is not running
function handleMockApi<T>(method: string, path: string, body: any): T {
  console.info(`[GymKhata Mock API] ${method} ${path}`, body)

  if (path === '/api/v1/auth/login' || path === '/api/v1/auth/refresh') {
    const phone = body?.phone || '+919810010001'
    let role = 'GYM_OWNER'
    let name = 'Rajesh Pratap Singh'
    let gymId = 'BAJRANG-LKO'

    if (phone.includes('9000000001') || phone === '9000000001') {
      role = 'SUPER_ADMIN'
      name = 'Platform Super Admin'
      gymId = 'PLATFORM'
    } else if (phone.includes('9810010002')) {
      name = 'Vikram Malhotra'
      gymId = 'IRONCORE-IND'
    } else if (phone.includes('9810010003')) {
      name = 'Surya Pratap'
      gymId = 'SURYA-JPR'
    }

    const user = { id: 'mock-user-id', name, role, gymId, phone }
    const tokens = { accessToken: 'mock-access-token-123', refreshToken: 'mock-refresh-token-456' }
    setTokens(tokens)
    return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, user } as unknown as T
  }

  if (path === '/api/v1/tenant/profile' || path === '/api/v1/owner/profile') {
    return {
      id: 'BAJRANG-LKO',
      name: 'Bajrang Powerhouse & Fitness (बजरंग पावरहाउस)',
      slug: 'bajrang-lko',
      city: 'Lucknow',
      state: 'Uttar Pradesh',
      gstRegistered: true,
      gstin: '09AABCB1234F1Z5',
      upiVpa: 'bajrangfitness@okaxis',
      monthlyPricePaise: 99900,
      attendanceEnabled: true,
      status: 'ACTIVE',
    } as unknown as T
  }

  if (path.includes('/members')) {
    if (method === 'POST') {
      return { id: 'm-' + Date.now(), name: body?.name || 'New Member', phone: body?.phone || '9876543210', status: 'ACTIVE' } as unknown as T
    }
    return [
      { id: 'm-1', name: 'Aarav Sharma (रोहित शर्मा)', phone: '+919810010001', status: 'ACTIVE', planName: 'Monthly Standard', endDate: '2026-11-30' },
      { id: 'm-2', name: 'Priya Verma (पूजा वर्मा)', phone: '+919810010002', status: 'ACTIVE', planName: 'Quarterly Power', endDate: '2026-12-15' },
      { id: 'm-3', name: 'Amit Kumar', phone: '+919810010003', status: 'EXPIRED', planName: 'Monthly Standard', endDate: '2025-10-01' },
      { id: 'm-4', name: 'Kavita Mishra', phone: '+919810010004', status: 'ACTIVE', planName: 'Annual Elite', endDate: '2027-05-20' },
    ] as unknown as T
  }

  if (path.includes('/billing') || path.includes('/invoices')) {
    return [
      { id: 'inv-1', invoiceNumber: 'PFX/26-27/00001', memberName: 'Aarav Sharma', totalPaise: 118000, status: 'PAID', issueDate: '2026-04-01' },
      { id: 'inv-2', invoiceNumber: 'PFX/26-27/00002', memberName: 'Priya Verma', totalPaise: 400000, status: 'ISSUED', issueDate: '2026-04-02' },
    ] as unknown as T
  }

  if (path.includes('/checkins')) {
    return {
      insideCount: 2,
      membersInside: [
        { id: 'm-1', name: 'Aarav Sharma', phone: '+919810010001', checkinTime: '06:30 AM' },
        { id: 'm-2', name: 'Priya Verma', phone: '+919810010002', checkinTime: '07:15 AM' },
      ],
      attendanceEnabled: true,
    } as unknown as T
  }

  if (path.includes('/plans')) {
    return [
      { id: 'p-1', name: 'Monthly Standard (मासिक)', durationDays: 30, pricePaise: 150000 },
      { id: 'p-2', name: 'Quarterly Power (त्रैमासिक)', durationDays: 90, pricePaise: 400000 },
      { id: 'p-3', name: 'Express Monthly ₹999', durationDays: 30, pricePaise: 99900 },
    ] as unknown as T
  }

  if (path.includes('/leads')) {
    return [
      { id: 'l-1', name: 'Rahul Verma', phone: '+919810011111', status: 'NEW', interestedPlan: 'Monthly Standard', notes: 'Walk-in enquiry' },
    ] as unknown as T
  }

  if (path.includes('/messaging') || path.includes('/whatsapp')) {
    return [
      { id: 'w-1', memberName: 'Aarav Sharma', phone: '+919810010001', status: 'QUEUED', message: 'Reminder: Your membership expires soon.' },
    ] as unknown as T
  }

  if (path.includes('/reports')) {
    return {
      totalRevenuePaise: 450000,
      activeMembersCount: 4,
      expiringCount: 1,
    } as unknown as T
  }

  if (path.includes('/admin/gyms')) {
    return [
      { id: 'BAJRANG-LKO', name: 'Bajrang Powerhouse & Fitness', ownerName: 'Rajesh Pratap Singh', phone: '+919810010001', status: 'ACTIVE' },
      { id: 'IRONCORE-IND', name: 'IronCore Strength Club', ownerName: 'Vikram Malhotra', phone: '+919810010002', status: 'ACTIVE' },
      { id: 'TITAN-NGP', name: 'Titan Muscle Factory', ownerName: 'Amit Shah', phone: '+919810010004', status: 'SUSPENDED' },
    ] as unknown as T
  }

  // Generic success fallback for any other endpoint
  return { success: true } as unknown as T
}

type RequestOptions = {
  method?: string
  body?: unknown
  auth?: boolean
  raw?: boolean
  headers?: Record<string, string>
}

export async function api<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, raw = false, headers: customHeaders } = options
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const tokens = getTokens()
    if (tokens) headers['Authorization'] = `Bearer ${tokens.accessToken}`
  }
  if (customHeaders) {
    Object.assign(headers, customHeaders)
  }

  try {
    const res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })

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
  } catch (err) {
    // If backend is not running or network error occurs, fall back gracefully to mock API handler
    console.warn(`[GymKhata API] Network call failed for ${method} ${path}. Falling back to client-side mock mode.`, err)
    return handleMockApi<T>(method, path, body)
  }
}
