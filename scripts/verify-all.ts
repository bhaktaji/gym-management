const BASE = 'http://127.0.0.1:3000'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) {
    throw new Error(`ASSERTION FAILED: ${msg}`)
  }
}

async function req(path: string, opts: { method?: string; body?: unknown; token?: string; gymId?: string } = {}) {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (opts.token) headers['Authorization'] = `Bearer ${opts.token}`
  if (opts.gymId) headers['X-Gym-Id'] = opts.gymId
  const res = await fetch(`${BASE}${path}`, {
    method: opts.method || 'GET',
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  })
  const contentType = res.headers.get('content-type') || ''
  let data: any = null
  if (contentType.includes('application/json')) {
    data = await res.json()
  } else {
    data = await res.text()
  }
  return { status: res.status, data, headers: res.headers }
}

async function run() {
  console.log('=== 1. Verifying Public Config, Build Info & Service Worker ===')
  const buildInfo = await req('/api/v1/build-info')
  assert(buildInfo.status === 200 && buildInfo.data.commitHash, 'build-info must return commitHash')

  const pubCfg = await req('/api/v1/config/public')
  assert(pubCfg.status === 200, 'config/public 200')
  assert(pubCfg.data.pricing.monthlyPricePaise === 99900, 'monthlyPricePaise === 99900')
  assert(Array.isArray(pubCfg.data.pricing.included) && pubCfg.data.pricing.included.length > 0, 'pricing.included must be array')
  assert(Array.isArray(pubCfg.data.pricing.notCharged) && pubCfg.data.pricing.notCharged.length > 0, 'pricing.notCharged must be array')

  const sw = await req('/sw.js')
  assert(sw.status === 200 && String(sw.data).includes('skipWaiting'), 'sw.js must be served')
  console.log('✓ Public config, pricing arrays, build-info & sw.js verified')

  console.log('=== 2. Verifying Super Admin Login (9915110640 / 12345678*&^%$#@!) & Pending ABC Gym ===')
  const adminLogin = await req('/api/v1/auth/login', {
    method: 'POST',
    body: { phone: '9915110640', password: '12345678*&^%$#@!' },
  })
  assert(adminLogin.status === 200 && adminLogin.data.user.role === 'SUPER_ADMIN', 'Super Admin login must succeed')
  const adminToken = adminLogin.data.accessToken

  const gymsBefore = await req('/api/v1/admin/gyms', { token: adminToken })
  assert(gymsBefore.status === 200 && Array.isArray(gymsBefore.data), 'admin/gyms must return array')
  const abcGym = gymsBefore.data.find((g: any) => g.name === 'ABC Gym')
  assert(abcGym && abcGym.status === 'PENDING', 'ABC Gym must be present in PENDING status for Super Admin approval')
  console.log('✓ Super Admin login & ABC Gym pending approval verified')

  console.log('=== 3. Verifying New Gym Self-Signup -> Super Admin Approval -> Owner Login ===')
  const sfx = String(Date.now()).slice(-5)
  const signupPhone = `98765${sfx}`
  const signupRes = await req('/api/v1/auth/signup', {
    method: 'POST',
    body: {
      gymName: `Verify Fitness ${sfx}`,
      ownerName: 'Verify Owner',
      phone: signupPhone,
      email: 'verify@example.com',
      city: 'Delhi',
      state: 'Delhi',
      password: 'Password@123',
    },
  })
  assert(signupRes.status === 201 && signupRes.data.status === 'PENDING', 'New signup must be PENDING')
  const newGymId = signupRes.data.gymId

  // Pending gym owner cannot login before approval
  const preLogin = await req('/api/v1/auth/login', {
    method: 'POST',
    body: { phone: signupPhone, password: 'Password@123' },
  })
  assert(preLogin.status === 403, 'Pending gym owner must be blocked before approval')

  // Super Admin sees it and approves it
  const approveRes = await req(`/api/v1/admin/gyms/${newGymId}/status`, {
    method: 'POST',
    token: adminToken,
    body: { status: 'ACTIVE' },
  })
  assert(approveRes.status === 200 && approveRes.data.status === 'ACTIVE', 'Super Admin approval must set ACTIVE')

  // Test Super Admin update owner, update subscription, password reset
  const updOwner = await req(`/api/v1/admin/gyms/${newGymId}/owner`, {
    method: 'PUT',
    token: adminToken,
    body: { phone: signupPhone, email: 'updated@example.com' },
  })
  assert(updOwner.status === 200, 'Super Admin update owner must succeed')

  const updSub = await req(`/api/v1/admin/gyms/${newGymId}/subscription`, {
    method: 'PUT',
    token: adminToken,
    body: { validUntil: '2027-12-31' },
  })
  assert(updSub.status === 200, 'Super Admin update subscription must succeed')

  const rstPwd = await req(`/api/v1/admin/gyms/${newGymId}/password-reset`, {
    method: 'POST',
    token: adminToken,
  })
  assert(rstPwd.status === 200 && rstPwd.data.resetToken, 'Super Admin password reset must return resetToken')

  // Approved owner can now log in!
  const postLogin = await req('/api/v1/auth/login', {
    method: 'POST',
    body: { phone: signupPhone, password: 'Password@123' },
  })
  assert(postLogin.status === 200 && postLogin.data.user.role === 'GYM_OWNER', 'Approved gym owner must log in')
  console.log('✓ Self-signup -> Super Admin approval -> Owner login & Admin actions verified')

  console.log('=== 4. Verifying Gym Owner Dashboard & All Features (BAJRANG-LKO) ===')
  const ownerLogin = await req('/api/v1/auth/login', {
    method: 'POST',
    body: { phone: '+919810010001', password: 'DemoOwner@2026' },
  })
  assert(ownerLogin.status === 200 && ownerLogin.data.user.gymId === 'BAJRANG-LKO', 'BAJRANG-LKO login')
  const ownerToken = ownerLogin.data.accessToken

  // Verify token refresh preserves GYM_OWNER role (never overwrites with SUPER_ADMIN!)
  const refreshed = await req('/api/v1/auth/refresh', {
    method: 'POST',
    body: { refreshToken: ownerLogin.data.refreshToken },
  })
  assert(refreshed.status === 200 && refreshed.data.user.role === 'GYM_OWNER', 'Refresh must preserve GYM_OWNER')

  // Dashboard & Reports
  const dash = await req('/api/v1/reports/dashboard', { token: ownerToken })
  assert(dash.status === 200 && typeof dash.data.activeMembers === 'number', 'dashboard stats')
  const rev = await req('/api/v1/reports/revenue', { token: ownerToken })
  assert(rev.status === 200 && Array.isArray(rev.data), 'revenue report array')
  const exp = await req('/api/v1/reports/expiring?days=30', { token: ownerToken })
  assert(exp.status === 200 && Array.isArray(exp.data), 'expiring report array')
  const chkRep = await req('/api/v1/reports/checkins?days=30', { token: ownerToken })
  assert(chkRep.status === 200 && Array.isArray(chkRep.data), 'checkins report array')
  const plnRep = await req('/api/v1/reports/plans', { token: ownerToken })
  assert(plnRep.status === 200 && Array.isArray(plnRep.data), 'plans report array')

  // Members & Member Detail
  const members = await req('/api/v1/members', { token: ownerToken })
  assert(members.status === 200 && Array.isArray(members.data) && members.data.length > 0, 'members list')
  const m1 = await req('/api/v1/members/m-1', { token: ownerToken })
  assert(m1.status === 200 && m1.data.id === 'm-1' && !Array.isArray(m1.data), 'single member detail object')
  const pinReset = await req('/api/v1/members/m-1/reset-pin', { method: 'POST', token: ownerToken })
  assert(pinReset.status === 200 && pinReset.data.pin.length === 4, 'member PIN reset')

  // Billing, Invoices, PDF, Balance, PayLink
  const invoices = await req('/api/v1/billing/invoices', { token: ownerToken })
  assert(invoices.status === 200 && Array.isArray(invoices.data) && invoices.data.length > 0, 'invoices list')
  const invId = invoices.data[0].id
  const bal = await req(`/api/v1/billing/invoices/${invId}/balance`, { token: ownerToken })
  assert(bal.status === 200 && typeof bal.data.balancePaise === 'number', 'invoice balance')
  const payLink = await req(`/api/v1/billing/invoices/${invId}/pay-link`, { token: ownerToken })
  assert(payLink.status === 200 && payLink.data.upiDeepLink, 'invoice pay-link')
  const pdfRes = await fetch(`${BASE}/api/v1/billing/invoices/${invId}/pdf`, {
    headers: { Authorization: `Bearer ${ownerToken}` },
  })
  const pdfBuf = await pdfRes.arrayBuffer()
  const pdfHeader = new TextDecoder().decode(pdfBuf.slice(0, 5))
  assert(pdfRes.status === 200 && pdfHeader === '%PDF-' && pdfBuf.byteLength > 1000, 'invoice PDF download')

  // PT & 60/40 Proportional Commission Accrual
  const trRes = await req('/api/v1/trainers', {
    method: 'POST',
    token: ownerToken,
    body: {
      name: `Coach Test ${sfx}`,
      phone: `98888${sfx}`,
      defaultCommissionRule: { type: 'PERCENT', rateBps: 1000 },
    },
  })
  const pkgRes = await req('/api/v1/pt/packages', {
    method: 'POST',
    token: ownerToken,
    body: {
      name: `12-Session PT ${sfx}`,
      packageType: 'SESSION_BASED',
      totalSessions: 12,
      validityDays: 60,
      pricePaise: 1050000,
    },
  })
  const enrRes = await req('/api/v1/pt/enrolments', {
    method: 'POST',
    token: ownerToken,
    body: {
      memberId: 'm-1',
      trainerId: trRes.data.id,
      ptPackageId: pkgRes.data.id,
    },
  })
  await req('/api/v1/payments/counter', {
    method: 'POST',
    token: ownerToken,
    body: { invoiceId: enrRes.data.invoiceId, amountPaise: 630000, method: 'CASH' },
  })
  const stmt60 = await req(`/api/v1/commissions/statement?trainerId=${trRes.data.id}`, { token: ownerToken })
  assert(stmt60.data.balanceDuePaise === 60000 && stmt60.data.netPayablePaise === 60000, '60% payment accrues ₹600 commission')

  await req('/api/v1/payments/counter', {
    method: 'POST',
    token: ownerToken,
    body: { invoiceId: enrRes.data.invoiceId, amountPaise: 420000, method: 'CASH' },
  })
  const stmt100 = await req(`/api/v1/commissions/statement?trainerId=${trRes.data.id}`, { token: ownerToken })
  assert(stmt100.data.balanceDuePaise === 100000 && stmt100.data.netPayablePaise === 100000, '40% payment brings total commission to ₹1,000')
  console.log('✓ Members, Billing, PDF, PT & 60/40 Commission Accrual verified')

  console.log('=== 5. Verifying Check-ins, Public Self Check-In, Messaging, Leads & CSV Import ===')
  const chkAnalytics = await req('/api/v1/checkins/analytics?notSeenDays=14', { token: ownerToken })
  assert(chkAnalytics.status === 200 && Array.isArray(chkAnalytics.data.peakHours), 'checkins analytics')

  const pubInfo = await req('/api/v1/public/attendance/bajrang-lko/info')
  assert(pubInfo.status === 200 && pubInfo.data.attendanceEnabled === true, 'public attendance info')

  const selfChk = await req('/api/v1/public/attendance/bajrang-lko/checkin', {
    method: 'POST',
    body: { phone: '+919811110001', pin: pinReset.data.pin },
  })
  assert(selfChk.status === 200 && selfChk.data.badge === 'GREEN', 'active member self check-in returns GREEN')

  // Attendance disabled check on SURYA-JPR (+919810010003)
  const suryaLogin = await req('/api/v1/auth/login', {
    method: 'POST',
    body: { phone: '+919810010003', password: 'DemoOwner@2026' },
  })
  const suryaChk = await req('/api/v1/checkins/today', { token: suryaLogin.data.accessToken })
  assert(suryaChk.status === 403 && suryaChk.data.code === 'FEATURE_DISABLED', 'Attendance OFF returns 403 FEATURE_DISABLED')

  // Messaging, Leads, CSV Import
  const outbox = await req('/api/v1/messaging/outbox', { token: ownerToken })
  assert(outbox.status === 200 && Array.isArray(outbox.data), 'messaging outbox')
  const leads = await req('/api/v1/leads', { token: ownerToken })
  assert(leads.status === 200 && Array.isArray(leads.data), 'leads list')
  const csvImp = await req('/api/v1/import/members', {
    method: 'POST',
    token: ownerToken,
    body: { csv: 'name,phone\nImported Member,9876543999' },
  })
  assert(csvImp.status === 200 && csvImp.data.success === 1, 'CSV member import')

  console.log('✓ ALL API & UI DATA CONTRACTS VERIFIED 100% SUCCESSFULLY!')
}

run().catch((e) => {
  console.error(e)
  process.exit(1)
})
