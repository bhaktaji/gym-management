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
[INFO] Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
[INFO] Total time:  01:15 min
```

### Full Surefire Per-Class Listing (20 classes, 88 tests, 0 elided)

| # | Surefire Test Class | Tests Run | Failures | Errors | Skipped | Time Elapsed |
|---|---|---:|---:|---:|---:|---:|
| 1 | `com.gymkhata.common.MongoConnectivityTest` | 1 | 0 | 0 | 0 | 0.050 s |
| 2 | `com.gymkhata.domain.CheckinRulesTest` | 5 | 0 | 0 | 0 | 0.005 s |
| 3 | `com.gymkhata.domain.CommissionCalculatorTest` | 6 | 0 | 0 | 0 | 0.010 s |
| 4 | `com.gymkhata.domain.FinancialYearTest` | 3 | 0 | 0 | 0 | 0.005 s |
| 5 | `com.gymkhata.domain.GstCalculatorTest` | 5 | 0 | 0 | 0 | 0.073 s |
| 6 | `com.gymkhata.domain.IndianNumberWordsTest` | 4 | 0 | 0 | 0 | 0.003 s |
| 7 | `com.gymkhata.domain.InvoiceNumberingTest` | 4 | 0 | 0 | 0 | 0.005 s |
| 8 | `com.gymkhata.domain.MembershipDatesTest` | 3 | 0 | 0 | 0 | 0.003 s |
| 9 | `com.gymkhata.domain.MoneyTest` | 2 | 0 | 0 | 0 | 0.002 s |
| 10 | `com.gymkhata.domain.PhoneUtilsTest` | 3 | 0 | 0 | 0 | 0.003 s |
| 11 | `com.gymkhata.domain.QuietHoursTest` | 1 | 0 | 0 | 0 | 0.004 s |
| 12 | `com.gymkhata.domain.UpiUtilsTest` | 4 | 0 | 0 | 0 | 0.005 s |
| 13 | `com.gymkhata.integration.AttendanceAndPtIntegrationTest` | 3 | 0 | 0 | 0 | 2.625 s |
| 14 | `com.gymkhata.integration.AuthAndTenancyIntegrationTest` | 8 | 0 | 0 | 0 | 2.053 s |
| 15 | `com.gymkhata.integration.BillingIntegrationTest` | 11 | 0 | 0 | 0 | 1.877 s |
| 16 | `com.gymkhata.integration.ComprehensiveFlowAndSecurityTest` | 7 | 0 | 0 | 0 | 6.196 s |
| 17 | `com.gymkhata.integration.DemoDataSeederTest` | 4 | 0 | 0 | 0 | 12.220 s |
| 18 | `com.gymkhata.integration.OneOwnerPerGymTest` | 5 | 0 | 0 | 0 | 2.362 s |
| 19 | `com.gymkhata.integration.P0RegressionTest` | 5 | 0 | 0 | 0 | 0.812 s |
| 20 | `com.gymkhata.integration.SecurityAndDomainEdgeCasesTest` | 4 | 0 | 0 | 0 | 12.810 s |
| **Total** | **20 Test Classes** | **88** | **0** | **0** | **0** | **41.123 s** |

---

## 2. P0 Regression Verification (`origin/main` FAIL vs `release/prod-ready` PASS)

**Command on `origin/main` worktree**
```bash
mvn -f /tmp/origin-main-repo/backend/pom.xml test -Dtest=P0RegressionTest -DargLine="-javaagent:${AGENT_JAR}"
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
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s -- in com.gymkhata.integration.P0RegressionTest
```

---

## 3. Frontend Unit & Component Tests (`vitest`)

**Command**
```bash
npm --prefix gym-management/frontend test
```

**Result**
```text
 ✓ src/pages/app/CommissionStatement.test.tsx (1 test) 137ms
 ✓ src/pages/app/AttendanceSettings.test.tsx (1 test) 247ms
 ✓ src/pages/app/PersonalTrainingSaleForm.test.tsx (1 test) 123ms
 ✓ src/lib/format.test.ts (7 tests) 35ms

 Test Files  4 passed (4)
      Tests  10 passed (10)
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
dist/assets/index-BZqvqyim.css             32.48 kB │ gzip:   6.36 kB
dist/assets/index-CDoWVkgl.js              59.32 kB │ gzip:  16.75 kB
dist/assets/vendor-i18n-BTIOiLe-.js        48.49 kB │ gzip:  15.87 kB
dist/assets/vendor-libs-BoHeomUW.js       159.48 kB │ gzip:  48.05 kB
dist/assets/vendor-react-BtdcXA1F.js      431.94 kB │ gzip: 133.31 kB
dist/assets/vendor-charts-D9bvYMVk.js     437.55 kB │ gzip: 126.21 kB
✓ built in 1.31s
PWA v2.0.0
mode      generateSW
precache  34 entries (1322.26 KiB)
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

**Result (12 passed against live Spring Boot + MongoDB, 0 mocks, `trace: 'on'`, `screenshot: 'on'`)**
```text
Running 12 tests using 1 worker
  ✓   1 [chromium-desktop] › (a) Real Backend: Owner Login, Enrol Member, Invoice, 60/40 Partial Payment, Sell PT Package, Commission Accrues on 60% first (5.9s)
  ✓   2 [chromium-desktop] › (b) Real Backend: Enable Attendance, QR Page, Phone+PIN Bind, 1-Tap Check-in, Expired Member Sees Red Status + Pay Link (7.5s)
  ✓   3 [chromium-desktop] › (c) Real Backend: Attendance Disabled Endpoints Return 403 FEATURE_DISABLED and UI is Hidden in Nav (3.8s)
  ✓   4 [chromium-desktop] › (d) Real Backend: Hindi Invoice PDF Download (3.1s)
  ✓   5 [chromium-desktop] › (e) Real Backend Attendance OFF (SURYA-JPR): Every owner route renders zero check-in / check-out / inside now / attendance text, and dashboard screenshot saved (21.0s)
  ✓   6 [chromium-desktop] › (f) Real Backend Attendance ON (BAJRANG-LKO): Dashboard has no manual check-in buttons, shows Today's visits card, and dashboard screenshot saved (4.2s)
  ✓   7 [chromium-mobile-390] › (a) Real Backend: Owner Login, Enrol Member, Invoice, 60/40 Partial Payment, Sell PT Package, Commission Accrues on 60% first (5.7s)
  ✓   8 [chromium-mobile-390] › (b) Real Backend: Enable Attendance, QR Page, Phone+PIN Bind, 1-Tap Check-in, Expired Member Sees Red Status + Pay Link (6.6s)
  ✓   9 [chromium-mobile-390] › (c) Real Backend: Attendance Disabled Endpoints Return 403 FEATURE_DISABLED and UI is Hidden in Nav (3.5s)
  ✓  10 [chromium-mobile-390] › (d) Real Backend: Hindi Invoice PDF Download (3.1s)
  ✓  11 [chromium-mobile-390] › (e) Real Backend Attendance OFF (SURYA-JPR): Every owner route renders zero check-in / check-out / inside now / attendance text, and dashboard screenshot saved (21.2s)
  ✓  12 [chromium-mobile-390] › (f) Real Backend Attendance ON (BAJRANG-LKO): Dashboard has no manual check-in buttons, shows Today's visits card, and dashboard screenshot saved (3.2s)
  12 passed (1.6m)
```

---

## 6. NOT VERIFIED (Not Run in This Linux Container Environment)

| Item | Reason |
|---|---|
| Windows PowerShell / Batch launchers (`start-all.bat`, `scripts/*.ps1`) | Container OS is Linux (`Ubuntu 22.04` on `gvisor`), not Windows PowerShell. |
| Live Cloud Run staging deployment & MongoDB Atlas network peering | Per instruction: *"Don't deploy until I approve."* Exact commands are documented in `docs/RUNBOOK_STAGING.md`. |
| Live Meta WhatsApp Cloud API & live Razorpay payment gateway calls | Adapters are disabled behind feature flags (`WHATSAPP_CLOUD_ENABLED=false`, `RAZORPAY_ENABLED=false`) and badged `Coming Soon` in the UI. |
