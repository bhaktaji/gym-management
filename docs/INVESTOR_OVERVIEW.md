# GymKhata — Investor & Product Briefing

## Executive Summary
**GymKhata** is a vertical multi-tenant SaaS platform purpose-built for independent, single-location gym owners across Tier-2, Tier-3, and Tier-4 India.

India's fitness market is expanding at `[ADD: verified industry CAGR and citation]`, comprising `[ADD: verified count of independent gyms in India and source]`. Approximately `[ADD: verified software penetration % and source]` utilize modern software; the remainder rely on paper registers (*khata* books), spreadsheets, or fragmented WhatsApp groups. Existing enterprise solutions cost `[ADD: verified competitor pricing range and source]`, assume dedicated full-time reception staff, and lack native Indian GST/UPI/DPDP workflows.

GymKhata provides an operational system configured at **₹999/month** (`monthly-price-paise: 99900` in `backend/src/main/resources/application.yml:39`), built specifically for independent Indian gym ownership.

---

## The Core Product Innovations

### 1. Reception-Free Operations
Independent gyms frequently operate without dedicated front-desk staff.
- **Pure Date & Payment Truth:** Membership status derives strictly from calendar validity (`MembershipDates.java`) and collected payments (`PaymentService.java`). Daily check-in is completely decoupled from revenue operations and defaults to **OFF** (`attendanceEnabled = false` in `Gym.java:108`).
- **Zero-Hardware Attendance:** Instead of `[ADD: hardware biometric turnstile cost and source]` hardware, GymKhata generates an A4 printable QR poster (`PdfRenderer.renderQrPoster`, `GET /api/v1/checkins/poster.pdf`).
- **Frictionless Member Identification:** Random **4-digit PIN** at enrolment (`MemberService.java:152`) bound to the member's browser cookie (`gk_device_token`, max **2 bound devices** per member in `CheckinService.java:59`, **365-day** cookie TTL in `PublicAttendanceController.java:78`). Subsequent check-ins require **1 tap**.
- **Brute-Force & Proxy Safety:** PIN verification enforces **5 max attempts** (`CheckinService.MAX_PIN_ATTEMPTS = 5`) with exponential backoff (**60s → 300s → 900s → 3600s**, `CheckinService.java:428`) keyed on `phone + clientIp` behind trusted proxies (`RateLimitFilter.java:120`), plus instant owner unlock (`POST /api/v1/members/{id}/reset-pin-lockout`).
- **Soft Enforcement:** Expired members are never blocked at the door; the self-check-in screen displays a `RED` status badge (`CheckinService.java:708`) with an immediate UPI payment link (`UpiUtils.buildDeepLink`) to renew.

### 2. Personal Training & Cash-Flow Protected Commissions
Personal Training contributes `[ADD: verified PT share of gym profit margin and source]`, and manual trainer commission tracking can cause disputes.
- **Proportional Accrual:** When gym owners collect fees in instalments (e.g., **60% advance, 40% later**, verified in `CommissionCalculatorTest.java` and `e2e/prod-flows.spec.ts`), GymKhata accrues trainer commission **strictly in proportion to payments collected** (`CommissionCalculator.computeProportionalPaymentAccrual`).
- **Rule Immutability:** Commission rules (`PERCENT_OF_TAXABLE`, `FIXED_PER_PACKAGE`, `FIXED_PER_SESSION`, `SLAB`) are snapshotted onto each `CommissionEntry` (`CommissionService.java`). Revising a trainer's rate never alters historical ledger entries.
- **Automated Reversals:** Issuing a Credit Note (`BillingService.issueCreditNote`) automatically posts a negative `REVERSAL` entry (`CommissionService.reverseForCreditNote`) to the trainer's ledger.
- **One-Click WhatsApp Statements:** Owners share monthly earnings statements with trainers via WhatsApp (`wa.me` deep link), CSV export, or PDF receipt (`GET /api/v1/trainers/{id}/commissions/receipt.pdf`).

### 3. Indian Regulatory & Payment Native
- **GST Compliance:** Full integer-paise back-calculation (`GstCalculator.java`) for India's fitness GST rules: **18%** historical / standard membership rate and **5%** rate effective `2025-09-22` (`application.yml:69-75`, SAC `999723` for gym memberships and SAC `999799` at **5%** for PT in `BillingService.java:32`).
- **UPI Deep-Linking:** Generates NPCI-compliant UPI deep links (`upi://pay?pa=...`, `UpiUtils.java`) with **12-digit UTR** validation (`PaymentService.java:137`) for direct bank settlement with **0% platform MDR** in direct-UPI mode.
- **DPDP Act 2023 Readiness:** Built-in Right to Access (`GET /api/v1/members/{id}/export`) and Right to Erasure (`POST /api/v1/members/{id}/erase`) anonymising PII while preserving fiscal invoice records.

---

## Unit Economics & Target Metrics

| Metric | Value / Placeholder | Source |
| :--- | :--- | :--- |
| **Monthly Subscription Price** | **₹999 / month** (`99900` paise) | `backend/src/main/resources/application.yml:39` (`app.pricing.monthly-price-paise`) |
| **Annual Subscription Price** | `[ADD: verified annual plan price if offered]` | Not configured in codebase |
| **Target Gyms (Year 1)** | `[ADD: Year 1 gym subscription target]` | Business financial model (external to repo) |
| **Annual Recurring Revenue (ARR)** | `[ADD: Year 1 ARR projection]` | Business financial model (external to repo) |
| **Gross Margin** | `[ADD: verified cloud hosting + support gross margin %]` | Cloud Run + MongoDB Atlas billing model (external to repo) |
| **Customer Acquisition Cost (CAC)** | `[ADD: verified CAC from pilot acquisition channels]` | Go-to-market actuals (external to repo) |
| **LTV / CAC Ratio** | `[ADD: verified LTV/CAC ratio and retention months]` | Cohort retention actuals (external to repo) |

---

## Technology & Verification Metrics (Sourced from Codebase & Build Output)
- **Backend Runtime:** Java **21** + Spring Boot **3.5.16** (`backend/pom.xml:8,19`).
- **Automated Backend Tests:** **62** JUnit 5 + Spring Boot integration tests across **20** test classes (`mvn test`).
- **Frontend PWA Bundle:** React **19** + Vite **8** with route-level code splitting (`frontend/src/App.tsx`); main entry chunk is **59.32 kB** (`19.86 kB` gzipped) (`npm run build`), responsive down to **390px** mobile viewports (`playwright.config.ts:22`, **12** E2E runs across desktop and 390px mobile).
- **1-Owner-Per-Gym Multi-Tenancy:** Enforced by a unique partial MongoDB index on `(gymId, role=GYM_OWNER)` (`MongoConfig.java:37`) and verified in `OneOwnerPerGymTest.java`.
