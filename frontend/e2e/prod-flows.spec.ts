import { test, expect } from '@playwright/test'

test.describe('Production Readiness E2E Suite (Real Backend + MongoDB)', () => {

  test('(a) Real Backend: Owner Login, Enrol Member, Invoice, 60/40 Partial Payment, Sell PT Package, Commission Accrues on 60% first', async ({ page }) => {
    const suffix = String(Date.now()).slice(-6)
    const testPhone = '987' + String(Date.now()).slice(-7)

    // 1. Login via real /api/v1/auth/login against seeded gym BAJRANG-LKO
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010001')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')

    // 2. Enrol a new member via real API through UI / page context
    const tokensStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'))
    expect(tokensStr).toBeTruthy()
    const { accessToken } = JSON.parse(tokensStr!)

    // Create member via real backend
    const memberRes = await page.evaluate(async ({ token, phone, sfx }) => {
      const r = await fetch('/api/v1/members', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          name: `Arjun Kapoor ${sfx}`,
          phone,
          whatsappOptIn: true,
          state: 'Uttar Pradesh',
        }),
      })
      return await r.json()
    }, { token: accessToken, phone: testPhone, sfx: suffix })
    expect(memberRes.id).toBeTruthy()

    // Create trainer (10% = 1000 bps) and PT Package (₹10,500 inclusive of 5% GST -> ₹10,000 taxable base)
    const ptSetup = await page.evaluate(async ({ token, memberId, sfx }) => {
      const trRes = await fetch('/api/v1/trainers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          name: `Coach Kabir ${sfx}`,
          phone: '988' + sfx + '1',
          defaultCommissionRule: {
            type: 'PERCENT',
            rateBps: 1000,
          },
        }),
      })
      const trainer = await trRes.json()

      const pkgRes = await fetch('/api/v1/pt/packages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          name: `12-Session Strength PT ${sfx}`,
          packageType: 'SESSION_PACK',
          totalSessions: 12,
          validityDays: 60,
          pricePaise: 1050000,
        }),
      })
      const pkg = await pkgRes.json()

      const enrRes = await fetch('/api/v1/pt/enrolments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          memberId,
          trainerId: trainer.id,
          ptPackageId: pkg.id,
        }),
      })
      const enrolment = await enrRes.json()

      // Record 60% partial payment (60% of ₹10,500 = ₹6,300 = 630,000 paise)
      const pay60 = await fetch('/api/v1/payments/counter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          invoiceId: enrolment.invoiceId,
          amountPaise: 630000,
          method: 'CASH',
        }),
      })
      await pay60.json()

      const stmt60Res = await fetch(`/api/v1/commissions/statement?trainerId=${trainer.id}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      const stmt60 = await stmt60Res.json()

      // Record remaining 40% payment (40% of ₹10,500 = ₹4,200 = 420,000 paise)
      const pay40 = await fetch('/api/v1/payments/counter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          invoiceId: enrolment.invoiceId,
          amountPaise: 420000,
          method: 'CASH',
        }),
      })
      await pay40.json()

      const stmt100Res = await fetch(`/api/v1/commissions/statement?trainerId=${trainer.id}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      const stmt100 = await stmt100Res.json()

      return { trainer, pkg, enrolment, stmt60, stmt100 }
    }, { token: accessToken, memberId: memberRes.id, sfx: suffix })

    // Verify 60% partial payment accrued exactly ₹600 (60,000 paise) first, then 40% brought total to ₹1,000 (100,000 paise)
    expect(ptSetup.stmt60.netPayablePaise).toBe(60000)
    expect(ptSetup.stmt100.netPayablePaise).toBe(100000)

    // Navigate to PT page in browser UI and verify trainer appears
    await page.goto('/app/pt')
    await expect(page.locator(`text=Coach Kabir ${suffix}`)).toBeVisible()
  })

  test('(b) Real Backend: Enable Attendance, QR Page, Phone+PIN Bind, 1-Tap Check-in, Expired Member Sees Red Status + Pay Link', async ({ page }) => {
    const sfx = String(Date.now()).slice(-6)
    const activePhone = '971' + String(Date.now()).slice(-7)
    const expiredPhone = '972' + String(Date.now()).slice(-7)

    // Login as BAJRANG-LKO owner (which has attendanceEnabled=true)
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010001')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')

    const tokensStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'))
    const { accessToken } = JSON.parse(tokensStr!)

    // Create active member with plan + expired/unsubscribed member with gym UPI VPA so payLink is shown
    const seeded = await page.evaluate(async ({ token, actPhone, expPhone, suffix }) => {
      const gymRes = await fetch('/api/v1/tenant/gym', {
        headers: { Authorization: `Bearer ${token}` },
      })
      const gym = await gymRes.json()

      // Ensure strictQrMode is false for standard QR test
      gym.settings.attendanceEnabled = true
      gym.settings.strictQrMode = false
      await fetch('/api/v1/tenant/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify(gym.settings),
      })

      const plansRes = await fetch('/api/v1/plans', { headers: { Authorization: `Bearer ${token}` } })
      const plans = await plansRes.json()
      const planId = plans[0].id

      // Active member
      const m1Res = await fetch('/api/v1/members', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ name: `Active Sneha ${suffix}`, phone: actPhone, whatsappOptIn: true }),
      })
      const m1 = await m1Res.json()
      await fetch('/api/v1/memberships', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ memberId: m1.id, planId }),
      })

      // Member with no active membership -> receives RED badge + UPI payLink on self check-in
      const m2Res = await fetch('/api/v1/members', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ name: `Expired Rohan ${suffix}`, phone: expPhone, whatsappOptIn: true }),
      })
      const m2 = await m2Res.json()

      return { slug: gym.slug, m1Pin: m1.generatedPin, m2Pin: m2.generatedPin }
    }, { token: accessToken, actPhone: activePhone, expPhone: expiredPhone, suffix: sfx })

    // Visit public QR check-in page for active member
    await page.goto(`/g/${seeded.slug}/in`)
    await page.fill('[data-testid="self-checkin-phone"]', activePhone)
    await page.fill('[data-testid="self-checkin-pin"]', seeded.m1Pin)
    await page.click('[data-testid="self-checkin-submit"]')

    await expect(page.locator('[data-testid="self-checkin-result"]')).toBeVisible()
    await expect(page.locator('[data-testid="self-checkin-status-message"]')).toContainText('Active till')

    // Clear bound device token to test expired member on same page
    await page.evaluate((slug) => localStorage.removeItem(`gk_device_${slug}`), seeded.slug)
    await page.context().clearCookies()

    await page.goto(`/g/${seeded.slug}/in`)
    await page.fill('[data-testid="self-checkin-phone"]', expiredPhone)
    await page.fill('[data-testid="self-checkin-pin"]', seeded.m2Pin)
    await page.click('[data-testid="self-checkin-submit"]')

    await expect(page.locator('[data-testid="self-checkin-result"]')).toBeVisible()
    await expect(page.locator('[data-testid="self-checkin-pay-link"]')).toBeVisible()
  })

  test('(c) Real Backend: Attendance Disabled Endpoints Return 403 FEATURE_DISABLED and UI is Hidden in Nav', async ({ page }) => {
    // Login as SURYA-JPR owner (+919810010003) which has attendanceEnabled=false
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010003')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')

    const tokensStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'))
    const { accessToken } = JSON.parse(tokensStr!)

    // Direct API call to /api/v1/checkins/today returns 403 FEATURE_DISABLED
    const apiCheck = await page.evaluate(async (token) => {
      const r = await fetch('/api/v1/checkins/today', {
        headers: { Authorization: `Bearer ${token}` },
      })
      const body = await r.json()
      return { status: r.status, code: body.code }
    }, accessToken)
    expect(apiCheck.status).toBe(403)
    expect(apiCheck.code).toBe('FEATURE_DISABLED')

    // Bottom nav does NOT show check-in tab when disabled
    await expect(page.locator('nav a[href="/app/checkins"]')).toHaveCount(0)
  })

  test('(d) Real Backend: Hindi Invoice PDF Download', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010001')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')

    const tokensStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'))
    const { accessToken } = JSON.parse(tokensStr!)

    const pdfCheck = await page.evaluate(async (token) => {
      const invRes = await fetch('/api/v1/billing/invoices', {
        headers: { Authorization: `Bearer ${token}` },
      })
      const invoices = await invRes.json()
      const firstId = invoices[0].id
      const pdfRes = await fetch(`/api/v1/billing/invoices/${firstId}/pdf`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      const buf = await pdfRes.arrayBuffer()
      const header = new TextDecoder().decode(buf.slice(0, 5))
      return { status: pdfRes.status, header, byteLength: buf.byteLength }
    }, accessToken)

    expect(pdfCheck.status).toBe(200)
    expect(pdfCheck.header).toBe('%PDF-')
    expect(pdfCheck.byteLength).toBeGreaterThan(1000)
  })

  test('(e) Real Backend Attendance OFF (SURYA-JPR): Every owner route renders zero check-in / check-out / inside now / attendance text, and dashboard screenshot saved', async ({ page }) => {
    // Login as SURYA-JPR owner (+919810010003) where attendanceEnabled = false
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010003')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')
    await page.waitForSelector('h1')

    // Save screenshot of owner dashboard with attendance OFF
    await page.screenshot({ path: 'screenshots/dashboard-attendance-off.png' })

    // Grab first member ID so we also test /app/members/:id
    const tokensStr = await page.evaluate(() => localStorage.getItem('gymkhata.tokens'))
    const { accessToken } = JSON.parse(tokensStr!)
    const firstMemberId = await page.evaluate(async (token) => {
      const r = await fetch('/api/v1/members', { headers: { Authorization: `Bearer ${token}` } })
      const list = await r.json()
      return list[0]?.id
    }, accessToken)

    const ownerRoutes = [
      '/app',
      '/app/members',
      ...(firstMemberId ? [`/app/members/${firstMemberId}`] : []),
      '/app/billing',
      '/app/pt',
      '/app/plans',
      '/app/reports',
      '/app/leads',
      '/app/messages',
      '/app/more',
      '/app/settings',
    ]

    for (const route of ownerRoutes) {
      await page.goto(route)
      await page.waitForSelector('h1')
      const bodyText = await page.innerText('body')
      expect(bodyText, `Route ${route} leaked forbidden attendance/checkin text`).not.toMatch(/check.?in|check.?out|inside now|attendance/i)
    }
  })

  test('(f) Real Backend Attendance ON (BAJRANG-LKO): Dashboard has no manual check-in buttons, shows Today\'s visits card, and dashboard screenshot saved', async ({ page }) => {
    // Login as BAJRANG-LKO owner (+919810010001) where attendanceEnabled = true
    await page.goto('/login')
    await page.fill('input[inputmode="tel"]', '+919810010001')
    await page.fill('input[type="password"]', 'DemoOwner@2026')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/app')
    await page.waitForSelector('h1')

    // Dashboard shows "Today's visits" count card
    await expect(page.locator('text=Today\'s visits')).toBeVisible()

    // Assert dashboard has NO manual check-in or check-out buttons
    await expect(page.locator('button:has-text("Check in"), a:has-text("Check in"), a:has-text("Check someone in")')).toHaveCount(0)
    await expect(page.locator('button:has-text("Check out"), a:has-text("Check out")')).toHaveCount(0)

    // Save screenshot of owner dashboard with attendance ON
    await page.screenshot({ path: 'screenshots/dashboard-attendance-on.png' })
  })

})
