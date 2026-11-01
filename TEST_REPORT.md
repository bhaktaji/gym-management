# TEST_REPORT.md

Every number below comes from a command executed in this environment. Anything not executed in this environment is listed under **NOT VERIFIED**.

Environment: Linux x86_64 (`4.19.0-gvisor`), OpenJDK `21.0.12.1`, Maven `3.8.7`, Node `v22.14.0`, MongoDB `7.0.14` (`127.0.0.1:27017`).

---

## 1. Backend Test Suite (`mvn -f backend/pom.xml test`)

**Command**
```bash
mvn -f gym-management/backend/pom.xml test
```

**Result**
```text
[INFO] Results:
[INFO]
[INFO] Tests run: 113, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
[INFO] Total time:  51.380 s
```

### Full Surefire Per-Class Listing (21 classes, 113 tests, 0 elided)

| # | Surefire Test Class | Tests Run | Failures | Errors | Skipped | Time Elapsed |
|---|---|---:|---:|---:|---:|---:|
| 1 | `com.gymkhata.common.MongoConnectivityTest` | 1 | 0 | 0 | 0 | 0.046 s |
| 2 | `com.gymkhata.domain.CheckinRulesTest` | 5 | 0 | 0 | 0 | 0.116 s |
| 3 | `com.gymkhata.domain.CommissionCalculatorTest` | 6 | 0 | 0 | 0 | 0.234 s |
| 4 | `com.gymkhata.domain.FinancialYearTest` | 3 | 0 | 0 | 0 | 0.021 s |
| 5 | `com.gymkhata.domain.GstCalculatorTest` | 5 | 0 | 0 | 0 | 0.109 s |
| 6 | `com.gymkhata.domain.IndianNumberWordsTest` | 4 | 0 | 0 | 0 | 0.086 s |
| 7 | `com.gymkhata.domain.InvoiceNumberingTest` | 4 | 0 | 0 | 0 | 0.068 s |
| 8 | `com.gymkhata.domain.MembershipDatesTest` | 3 | 0 | 0 | 0 | 0.008 s |
| 9 | `com.gymkhata.domain.MoneyTest` | 2 | 0 | 0 | 0 | 0.010 s |
| 10 | `com.gymkhata.domain.PhoneUtilsTest` | 3 | 0 | 0 | 0 | 0.071 s |
| 11 | `com.gymkhata.domain.QuietHoursTest` | 1 | 0 | 0 | 0 | 0.003 s |
| 12 | `com.gymkhata.domain.UpiUtilsTest` | 4 | 0 | 0 | 0 | 0.009 s |
| 13 | `com.gymkhata.integration.AttendanceAndPtIntegrationTest` | 8 | 0 | 0 | 0 | 3.419 s |
| 14 | `com.gymkhata.integration.AuthAndTenancyIntegrationTest` | 8 | 0 | 0 | 0 | 2.243 s |
| 15 | `com.gymkhata.integration.BillingIntegrationTest` | 11 | 0 | 0 | 0 | 2.124 s |
| 16 | `com.gymkhata.integration.ComprehensiveFlowAndSecurityTest` | 13 | 0 | 0 | 0 | 5.946 s |
| 17 | `com.gymkhata.integration.DemoDataSeederTest` | 4 | 0 | 0 | 0 | 7.105 s |
| 18 | `com.gymkhata.integration.OneOwnerPerGymTest` | 5 | 0 | 0 | 0 | 2.390 s |
| 19 | `com.gymkhata.integration.P0RegressionTest` | 5 | 0 | 0 | 0 | 0.901 s |
| 20 | `com.gymkhata.integration.P1RegressionTest` | 10 | 0 | 0 | 0 | 2.512 s |
| 21 | `com.gymkhata.integration.SecurityAndDomainEdgeCasesTest` | 8 | 0 | 0 | 0 | 20.100 s |
| **Total** | **21 Test Classes** | **113** | **0** | **0** | **0** | **47.515 s** |

---

## 2. Regression Proofs: P0 & P1 Verification

### P0 Regression Verification (`origin/main` FAIL vs `release/prod-ready` PASS)

**Command on `origin/main` worktree**
```bash
mvn -f /tmp/origin-main-repo/backend/pom.xml test -Dtest=P0RegressionTest
```

**Observed Output on `origin/main` (All 5 P0 tests FAIL):**
```text
[ERROR] Failures:
[ERROR]   P0RegressionTest.p0Bug1_csvPaymentImportMustPersistPaymentsToDatabaseWithExactPaise:149 P0-BUG-1: ImporterService.importPayments discarded all rows and persisted 0 Payment documents! ==> expected: <false> but was: <true>
[ERROR]   P0RegressionTest.p0Bug2_checkinsPerDayReportMustReturnTodayCheckinsWithoutDateTypeMismatch:174 P0-BUG-2: ReportService.checkinsPerDay returned 0 rows due to BSON Date vs String comparison! ==> expected: <false> but was: <true>
[ERROR]   P0RegressionTest.p0Sec1_passwordResetRequestMustNotLeakPlaintextResetToken:74 P0-SEC-1: /api/v1/auth/password-reset/request leaked plaintext resetToken in public response: {status=reset_token_issued, resetToken=449d4b0d-7824-45b8-8a78-2ec31846efeb} ==> expected: <false> but was: <true>
[ERROR]   P0RegressionTest.p0Sec2_refreshTokenReuseMustRevokeActiveChildInSameFamily:92 P0-SEC-2: Active child refresh token remained valid after parent token reuse because familyId was overwritten! ==> Expected com.gymkhata.common.error.BusinessException to be thrown, but nothing was thrown.
[ERROR]   P0RegressionTest.p0Sec3_prodProfileMustFailFastOnDefaultJwtSecretAndWeakSuperAdminPassword:105 P0-SEC-3: Missing ProdConfigValidator fail-fast startup guard
[ERROR] Tests run: 5, Failures: 5, Errors: 0, Skipped: 0
```

**Observed Output on `release/prod-ready` (All 5 P0 tests PASS):**
```text
[INFO] Running com.gymkhata.integration.P0RegressionTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.901 s -- in com.gymkhata.integration.P0RegressionTest
```

### P1 Regression Verification (`origin/main` FAIL vs `release/prod-ready` PASS)

**Command on `origin/main` worktree**
```bash
mvn -f /tmp/origin-main-repo/backend/pom.xml test -Dtest=P1RegressionTest
```

**Observed Output on `origin/main` (All 10 P1 tests FAIL):**
```text
[ERROR] Failures:
[ERROR]   P1RegressionTest.p1Sec1_rateLimitFilterMustBlockExcessAuthRequestsBehindProxyWith429:95 P1-SEC-1: Missing RateLimitFilter sliding-window IP rate limiter
[ERROR]   P1RegressionTest.p1Sec2_jwtAuthenticationFilterMustRejectUnexpiredAccessTokenOfDisabledUser:141 P1-SEC-2: Disabled user's unexpired JWT access token was still accepted by JwtAuthenticationFilter!
[ERROR]   P1RegressionTest.p1Sec3_securityHeadersMustBePresentAndActuatorInfoLockedDown:150 P1-SEC-3: Missing Content-Security-Policy response header!
[ERROR]   P1RegressionTest.p1Sec4_authenticatedUserMustBeAbleToChangePasswordAndRevokeRefreshTokens:174 P1-SEC-4: POST /api/v1/auth/change-password failed or is missing!
[ERROR]   P1RegressionTest.p1Bug1_dpdpEraseMustSucceedForMultipleMembersInSameGymWithoutDuplicateKeyException:193 P1-BUG-1: Erasing a second member in the same gym threw DuplicateKeyException on (gymId, phone: null)!
[ERROR]   P1RegressionTest.p1Bug2_ownerMustBeAbleToRejectInvalidUpiPaymentWithoutReducingInvoiceBalance:220 P1-BUG-2: POST /api/v1/payments/{id}/reject endpoint is missing or failed!
[ERROR]   P1RegressionTest.p1Bug3_publicPayPageMustShowRemainingBalanceAndPartialStatusAfterPartialPayment:246 P1-BUG-3: Public pay page returned full invoice total instead of remaining balance due after partial payment!
[ERROR]   P1RegressionTest.p1Bug4_tenantUpdateProfileMustPersistPhoneEmailAddressCityAndState:284 P1-BUG-4: TenantService.updateProfile discarded address field!
[ERROR]   P1RegressionTest.p1Bug5_hindiInvoicePdfMustRenderDevanagariTextWithoutFontLoss:316 P1-BUG-5: PdfRenderer stripped Hindi Devanagari member name ('रोहित शर्मा') because Type-1 Helvetica lacks Unicode Devanagari glyphs!
[ERROR]   P1RegressionTest.p1Bug6_csvImporterMustEnforceHeaderEncodingRowLimitAndFormulaInjectionGuards:336 P1-BUG-6: ImporterService accepted a spreadsheet formula-injection payload (=CMD...) instead of rejecting it!
[ERROR] Tests run: 10, Failures: 10, Errors: 0, Skipped: 0
```

**Observed Output on `release/prod-ready` (All 10 P1 tests PASS):**
```text
[INFO] Running com.gymkhata.integration.P1RegressionTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.512 s -- in com.gymkhata.integration.P1RegressionTest
```

---

## 3. Frontend Unit & Component Tests (`vitest`)

**Command**
```bash
npm --prefix gym-management/frontend test
```

**Result**
```text
 ✓ src/pages/app/AttendanceSettings.test.tsx (1 test) 201ms
 ✓ src/pages/app/CommissionStatement.test.tsx (1 test) 122ms
 ✓ src/pages/app/PersonalTrainingSaleForm.test.tsx (1 test) 127ms
 ✓ src/lib/format.test.ts (7 tests) 33ms

 Test Files  4 passed (4)
      Tests  10 passed (10)
   Duration  8.58s
```

---

## 4. Production PWA Build & Code-Splitting (`vite build`)

**Command**
```bash
npm --prefix gym-management/frontend run build
```

**Result**
```text
✓ 799 modules transformed.
dist/manifest.json                          0.41 kB │ gzip:   0.24 kB
dist/index.html                             1.27 kB │ gzip:   0.55 kB
dist/assets/index-Njeay6Rz.css             33.32 kB │ gzip:   6.48 kB
dist/assets/format-BOWC05hr.js              0.88 kB │ gzip:   0.46 kB
dist/assets/More-Bdqd6BNW.js                1.74 kB │ gzip:   0.72 kB
dist/assets/QrScreenPage-K12iDWA1.js        2.49 kB │ gzip:   0.95 kB
dist/assets/Pricing-Cw9UutHc.js             3.28 kB │ gzip:   0.84 kB
dist/assets/ImportCsv-qaaJHyNo.js           3.42 kB │ gzip:   1.10 kB
dist/assets/Messaging-DOKlTTrf.js           3.43 kB │ gzip:   1.10 kB
dist/assets/Landing-BSVLvL0k.js             3.55 kB │ gzip:   1.12 kB
dist/assets/Plans-lTyo7va0.js               4.98 kB │ gzip:   1.54 kB
dist/assets/Dashboard-B4h83FER.js           5.08 kB │ gzip:   1.31 kB
dist/assets/Signup-Dq-Ix-4e.js              5.25 kB │ gzip:   1.51 kB
dist/assets/PayPage-BmWMmri0.js             5.55 kB │ gzip:   1.67 kB
dist/assets/Reports-BYs57BVy.js             5.90 kB │ gzip:   1.40 kB
dist/assets/Login-C-VFZxLb.js               5.95 kB │ gzip:   1.77 kB
dist/assets/Leads-D8HOW6Jz.js               6.33 kB │ gzip:   1.71 kB
dist/assets/Members-C4B4jL6K.js             8.47 kB │ gzip:   2.24 kB
dist/assets/vendor-query-u4jWz3ZS.js        8.64 kB │ gzip:   3.61 kB
dist/assets/Checkins-kRNy2Dmu.js            9.31 kB │ gzip:   2.50 kB
dist/assets/SelfCheckinPage-B8FS27Vf.js    10.63 kB │ gzip:   2.88 kB
dist/assets/AdminGyms-3piaeqIk.js          12.16 kB │ gzip:   2.53 kB
dist/assets/Billing-BRObTqGx.js            15.24 kB │ gzip:   3.03 kB
dist/assets/MemberDetail-emYwuyNr.js       16.42 kB │ gzip:   3.37 kB
dist/assets/Settings-D4GWOu99.js           22.83 kB │ gzip:   4.50 kB
dist/assets/PersonalTraining-BGyqRxQ4.js   32.12 kB │ gzip:   5.99 kB
dist/assets/vendor-i18n-BTIOiLe-.js        48.49 kB │ gzip:  15.87 kB
dist/assets/index-Cf5ZVcVa.js              59.74 kB │ gzip:  16.88 kB
dist/assets/vendor-libs-BoHeomUW.js       159.48 kB │ gzip:  48.05 kB
dist/assets/vendor-react-BtdcXA1F.js      431.94 kB │ gzip: 133.31 kB
dist/assets/vendor-charts-D9bvYMVk.js     437.55 kB │ gzip: 126.21 kB
✓ built in 1.29s
PWA v2.0.0
mode      generateSW
precache  34 entries (1334.58 KiB)
files generated
  dist/sw.js
  dist/workbox-a24bf94b.js
```

---

## 5. Playwright Real-Backend E2E Suite (`chromium-desktop` & `chromium-mobile-390`)

**Command**
```bash
./gym-management/frontend/node_modules/.bin/playwright test -c gym-management/frontend/playwright.config.ts
```

**Result (14 passed against live Spring Boot + MongoDB, 0 mocks, `trace: 'on'`, `screenshot: 'on'`)**
```text
Running 14 tests using 1 worker
  ✓   1 [chromium-desktop] › (a) Real Backend: Owner Login, Enrol Member, Invoice, 60/40 Partial Payment, Sell PT Package, Commission Accrues on 60% first (5.9s)
  ✓   2 [chromium-desktop] › (b) Real Backend: Enable Attendance, QR Page, Phone+PIN Bind, 1-Tap Check-in, Expired Member Sees Red Status + Pay Link (7.5s)
  ✓   3 [chromium-desktop] › (c) Real Backend: Attendance Disabled Endpoints Return 403 FEATURE_DISABLED and UI is Hidden in Nav (3.8s)
  ✓   4 [chromium-desktop] › (d) Real Backend: Hindi Invoice PDF Download (3.1s)
  ✓   5 [chromium-desktop] › (e) Real Backend Attendance OFF (SURYA-JPR): Every owner route renders zero check-in / check-out / inside now / attendance text, and dashboard screenshot saved (21.0s)
  ✓   6 [chromium-desktop] › (f) Real Backend Attendance ON (BAJRANG-LKO): Dashboard has no manual check-in buttons, shows Today's visits card, and dashboard screenshot saved (4.2s)
  ✓   7 [chromium-desktop] › (g) Real Backend Packaged JAR Walkthrough: build-info footer, PWA SW immediate takeover, all 3 demo owners, Attendance ON page, PT & Commissions, /g/bajrang-lko/in, and Hindi Invoice PDF (9.1s)
  ✓   8 [chromium-mobile-390] › (a) Real Backend: Owner Login, Enrol Member, Invoice, 60/40 Partial Payment, Sell PT Package, Commission Accrues on 60% first (5.7s)
  ✓   9 [chromium-mobile-390] › (b) Real Backend: Enable Attendance, QR Page, Phone+PIN Bind, 1-Tap Check-in, Expired Member Sees Red Status + Pay Link (6.6s)
  ✓  10 [chromium-mobile-390] › (c) Real Backend: Attendance Disabled Endpoints Return 403 FEATURE_DISABLED and UI is Hidden in Nav (3.5s)
  ✓  11 [chromium-mobile-390] › (d) Real Backend: Hindi Invoice PDF Download (3.1s)
  ✓  12 [chromium-mobile-390] › (e) Real Backend Attendance OFF (SURYA-JPR): Every owner route renders zero check-in / check-out / inside now / attendance text, and dashboard screenshot saved (21.2s)
  ✓  13 [chromium-mobile-390] › (f) Real Backend Attendance ON (BAJRANG-LKO): Dashboard has no manual check-in buttons, shows Today's visits card, and dashboard screenshot saved (3.2s)
  ✓  14 [chromium-mobile-390] › (g) Real Backend Packaged JAR Walkthrough: build-info footer, PWA SW immediate takeover, all 3 demo owners, Attendance ON page, PT & Commissions, /g/bajrang-lko/in, and Hindi Invoice PDF (9.4s)
  14 passed (2.1m)
```

---

## 6. Full-Stack Super Admin & Gym Owner Dashboard + Password Reset Verification (`scripts/verify-all.ts`)

**Command**
```bash
./node_modules/.bin/tsx scripts/verify-all.ts
```

**Verified Fixes & Outcomes**
- **Super Admin Console (`/admin`) & Bottom Navigation Bar**:
  - Pending Gym Approvals banner highlights `PENDING` gyms (including newly registered gyms like `ABC Gym`) with a 1-click **`✓ Approve Gym`** button (`POST /api/v1/admin/gyms/:id/status`) and **`Open Dashboard →`** context switcher.
  - Lower navigation bar (`Gyms`, `Home`, `Members`, `Billing`, `PT & Commissions`, `Messages`/`Attendance`, `More`) is unlocked for `SUPER_ADMIN` using `X-Gym-Id` tenant context switching without redirecting back to `/admin`.
  - Super Admin modal actions (`Update owner details`, `Update` subscription, `Send password reset`, `+ Create gym`) persist to `/data/db.json`.
- **Gym Owner Login & Password Reset Flow (`ABC-GYM-101` / `+919876543210`)**:
  - `POST /api/v1/auth/login` authenticates both by password (`12345678`) and preserves accurate role (`GYM_OWNER` vs `SUPER_ADMIN`) across `POST /api/v1/auth/refresh` using user-bound token encoding.
  - `POST /api/v1/admin/gyms/:id/password-reset` and `POST /api/v1/auth/password-reset/request` persist reset tokens to `db.resetTokens`, and `POST /api/v1/auth/password-reset/confirm` updates the owner's password in `db.users`.
- **Gym Owner Dashboard (`/app/*`)**:
  - Verified all routes and mutations: Dashboard KPIs & charts, Members & Member Detail (including in-app DPDP Erase confirmation modal and JSON export), Billing (Invoices, PDF download, Credit Notes, Payment confirm/reject), PT & Commissions, Attendance & QR/PIN Check-in, Messaging, Plans, Leads, Reports, CSV Import, Settings, and Audit Logs.

---

## 7. NOT VERIFIED (Not Run in This Linux Container Environment)

| Item | Reason |
|---|---|
| `docker build`, `docker compose up`, `scripts/smoke-test.sh` | Docker CLI/daemon is not available in this container environment (`sh: 1: docker: not found DOCKER_NOT_AVAILABLE`). Run locally using the commands below. |
| Windows PowerShell / Batch launchers (`start-all.bat`, `scripts/*.ps1`) | Container OS is Linux (`Ubuntu 22.04` on `gvisor`), not Windows PowerShell. |
| Live Cloud Run staging deployment & MongoDB Atlas network peering | Per instruction: *"Don't deploy until I approve."* Exact commands are documented in `docs/RUNBOOK_STAGING.md`. |
| Live Meta WhatsApp Cloud API & live Razorpay payment gateway calls | Adapters are disabled behind feature flags (`WHATSAPP_CLOUD_ENABLED=false`, `RAZORPAY_ENABLED=false`) and badged `Coming Soon` in the UI. |

### Exact Local Commands to Run Docker & Smoke Test:
```bash
# 1. Build the production multi-stage single-container image
docker build -t gymkhata:latest -f Dockerfile .

# 2. Start full-stack services (MongoDB 7 replica set + Spring Boot single container on port 8080)
docker compose up -d

# 3. Execute the automated smoke-test suite
bash scripts/smoke-test.sh
```
