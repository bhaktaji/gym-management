# GymKhata — Phase 0 Technical & Security Audit (`AUDIT.md`)

**Repository audited:** `https://github.com/bhaktaji/gym-management` (commit `HEAD` on `main`)  
**Audit scope:** Read-only inspection of `backend/` (94 main Java files, 15 test Java files, `pom.xml`, YAML configs, `.env.example`), `frontend/` (42 TypeScript/React/CSS/JSON files, `package.json`, `vite.config.ts`), `scripts/` (8 PowerShell scripts + `start-all.bat` / `stop-all.bat`), and `docs/` (`README.md`, `GETTING_STARTED.md`, `HANDOVER.md`, `TEST_REPORT.md`, `ASSUMPTIONS.md`, `ENV_REPORT.md`, `docs/API.md`, `docs/CONFIGURATION.md`, `docs/DATA_MODEL.md`, `docs/DOMAIN_RULES.md`).

---

## 1. Architecture Summary

GymKhata is a multi-tenant gym management Progressive Web Application (PWA) built for independent single-location gyms in tier-2 and tier-3 cities across India.

### Backend (`backend/`)
- **Runtime & Framework:** Java 21, Spring Boot `3.5.16` (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-actuator`).
- **Packaging:** Single executable fat JAR (`gymkhata-backend-0.1.0.jar`) listening on port `8080`. The Maven `frontend-Build` profile copies `frontend/dist` into `target/classes/static`, and `SpaForwardingController` forwards non-API routes to `/index.html`.
- **Database:** Standalone MongoDB (`gymkhata` database by default). Uses explicit programmatic index creation in `MongoConfig.java` (`auto-index-creation: false`) and atomic `findAndModify` (`$inc`) counters on `invoice_sequences` for gapless fiscal-year numbering without requiring a MongoDB replica set or multi-document transactions.
- **Authentication & Authorization:**
  - Stateless HMAC-SHA256 JWT access tokens (`jjwt 0.12.6`, 15-minute TTL) paired with rotating opaque refresh tokens (30-day TTL, SHA-256 hashed in `refresh_tokens` with a `familyId`).
  - Two platform roles (`Role.java`): `SUPER_ADMIN` (`gymId == null`) and `GYM_OWNER` (bound 1:1 to a single `gymId`). No staff/trainer logins exist by design.
  - `TenantStatusInterceptor` blocks any authenticated `GYM_OWNER` request under `/api/v1/**` with HTTP `403 ACCOUNT_SUSPENDED` when the gym's status is not `ACTIVE` (cached in-memory for 30s per JVM, evicted on admin status toggle).
- **Domain Modules:**
  - `admin` (`AdminController`, `AdminService`): Platform overview, gym + owner creation, status suspension/reactivation, approval of `PENDING` self-signups, SaaS subscription tier/billing updates, owner password reset, audit log viewer.
  - `auth` (`AuthController`, `AuthService`, `JwtService`, `JwtAuthenticationFilter`): Login, self-signup (`PENDING` status), refresh token rotation, logout, password reset request/confirm.
  - `tenant` (`TenantController`, `TenantService`): Gym profile, GST configuration, UPI VPA, WhatsApp templates (EN/HI), DPDP privacy consent text.
  - `plan` & `membership` (`PlanController`, `MembershipController`, `MembershipService`): Plan catalog (integer paise pricing, duration days, optional joining fee) and member enrolments (`ACTIVE`, `SUPERSEDED`, `EXPIRED`, `CANCELLED`). Renewing automatically supersedes the previous active membership.
  - `member` (`MemberController`, `MemberService`): Member CRUD, Indian phone normalisation (`+91[6-9]\d{9}`), search/status filtering, DPDP Act 2023 JSON export and right-to-erasure anonymisation.
  - `billing` (`InvoiceController`, `PaymentController`, `PublicPayController`, `InvoiceService`, `PaymentService`, `GstCalculator`, `InvoiceNumberGenerator`, `PdfRenderer`, `UpiQrGenerator`): All-inclusive GST back-calculation (`CGST + SGST` intra-state or `IGST` inter-state; `Bill of Supply` when not GST-registered), gapless per-gym per-FY sequences (`{PREFIX}/{FY}/{00001}` and `CN/{FY}/{00001}`), OpenPDF A4 receipts, ZXing PNG QR codes, tokenised public payment page (`/pay/{token}`), 12-digit UTR submission (`PENDING` -> owner confirms -> `CONFIRMED`).
  - `checkin` (`CheckinController`, `CheckinService`): Manual check-in/out and tokenised QR/kiosk check-in, double check-in guard, expired membership warning, and "inside now" live roster.
  - `messaging` (`MessagingController`, `MessagingService`, `WaLinkAdapter`, `MetaCloudWhatsAppAdapter`, `QuietHours`): WhatsApp reminder queue (`wa.me` deep links by default), quiet hours guard (21:00–08:00 IST), template interpolation (`{memberName}`, `{gymName}`, `{expiryDate}`, `{amount}`, `{payLink}`).
  - `lead` (`LeadController`, `LeadService`): Walk-in/enquiry pipeline (`NEW`, `CONTACTED`, `TRIAL`, `CONVERTED`, `LOST`) and 1-click conversion into a `Member`.
  - `report` (`ReportController`, `ReportService`): Monthly revenue, expiring memberships, daily check-ins, and plan distribution.
  - `importer` (`ImportController`, `ImporterService`, `CsvParser`): CSV batch import for members and payments.

### Frontend (`frontend/`)
- **Stack:** Vite 8, React 19, TypeScript 5.9, Tailwind CSS 4 (`@tailwindcss/vite`), React Router v7, TanStack Query v5, React Hook Form + Zod v4, Recharts v3, `i18next` / `react-i18next` (English `en.json` + Hindi `hi.json`), `vite-plugin-pwa`.
- **Client Architecture:** `api/client.ts` wraps `fetch` with automatic Bearer token injection and a single-flight `401` refresh-token retry queue.

---

## 2. Actual Endpoints vs `docs/API.md` (Endpoint Drift & Feature Gaps)

Every `@RestController` in `backend/src/main/java/com/gymkhata` was cross-referenced against `docs/API.md`:

| Controller | Method & Path in Code | In `docs/API.md`? | Drift / Discrepancy Details |
| :--- | :--- | :---: | :--- |
| `AuthController` | `POST /api/v1/auth/logout` | **NO** | Exists in `AuthController.java:60` (deletes all refresh tokens for current user), but is undocumented in `docs/API.md`. Furthermore, `frontend/src/auth/AuthContext.tsx` never calls `/api/v1/auth/logout` on logout—it only clears `localStorage`. |
| `PlanController` | `GET /api/v1/plans/{id}` | **NO** | Exists in `PlanController.java:52` (returns single plan by ID for current gym), omitted from `docs/API.md`. |
| `MembershipController` | `GET /api/v1/memberships/{id}` | **NO** | Exists in `MembershipController.java:45` (returns single membership by ID for current gym), omitted from `docs/API.md`. |
| `LeadController` | `GET /api/v1/leads/{id}` | **NO** | Exists in `LeadController.java:56` (returns single lead by ID for current gym), omitted from `docs/API.md`. |
| `PaymentController` | `POST /api/v1/payments/{id}/reject` | **MISSING IN CODE** | `Payment.java:18` defines `STATUS_FAILED = "FAILED"`, and domain requirements specify confirming **or rejecting** a pending member-submitted UPI UTR. `PaymentController` implements `POST /api/v1/payments/{id}/confirm`, but has **no `/reject` endpoint**, leaving mistaken/fraudulent `PENDING` UTR submissions permanently stuck on invoices. |
| `AuthController` | `POST /api/v1/auth/password-reset/request` | Documented as dev-only token return | `docs/API.md` claims *"returns `{ status, resetToken }` (token returned directly in dev)"*, but `AuthController.java:75` returns `resetToken` unconditionally in **all** profiles (including `prod`), creating a critical account-takeover vulnerability. |

All other 47 endpoints across `PublicConfigController`, `AuthController`, `TenantController`, `PlanController`, `MemberController`, `MembershipController`, `InvoiceController`, `PaymentController`, `PublicPayController`, `CheckinController`, `MessagingController`, `LeadController`, `ReportController`, `ImportController`, and `AdminController` match `docs/API.md`.

---

## 3. Config Variables vs `backend/.env.example` & `docs/CONFIGURATION.md`

Cross-referencing `application.yml`, `application-dev.yml`, `application-prod.yml`, `backend/.env.example`, and `docs/CONFIGURATION.md`:

| Variable / Property | In `application*.yml` | In `backend/.env.example` | In `docs/CONFIGURATION.md` | Finding |
| :--- | :---: | :---: | :---: | :--- |
| `JWT_SECRET` | Yes (`application.yml`, `application-prod.yml`) | **MISSING** | Yes | **Critical drift:** `JWT_SECRET` is required in `prod` (`${JWT_SECRET}` with no default in `application-prod.yml`), yet is completely absent from `backend/.env.example`. |
| `CORS_ALLOWED_ORIGINS` | **NO** (hardcoded in `application.yml`) | **MISSING** | **MISSING** | **Critical drift:** `application.yml:27` hardcodes `app.cors-allowed-origins: "http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080"` with no environment variable placeholder and no entry in `application-prod.yml`. |
| `LOG_FILE` | Yes (`application-prod.yml:20`) | **MISSING** | **MISSING** | `application-prod.yml` references `${LOG_FILE:logs/gymkhata.log}`, which is undocumented in both `.env.example` and `docs/CONFIGURATION.md`. |
| `SPRING_PROFILES_ACTIVE` | Used by Spring Boot | **MISSING** | Mentioned in prose | Not listed in `backend/.env.example`. |
| `PORT` | Yes (`${PORT:8080}`) | Yes | Yes | Aligned. |
| `MONGODB_URI` | Yes | Yes | Yes | Aligned. |
| `PUBLIC_BASE_URL` | Yes | Yes | Yes | Aligned. |
| `SUPER_ADMIN_PHONE` / `PASSWORD` / `NAME` | Yes | Yes | Yes | Aligned, but `SUPER_ADMIN_PASSWORD` defaults to `ChangeMe@123` even if `prod` profile is active unless overridden. |
| `WHATSAPP_PROVIDER` / `TOKEN` / `PHONE_ID` | Yes | Yes | Yes | Aligned. |
| `PAYMENT_PROVIDER` / `RAZORPAY_*` | Yes | Yes | Yes | Aligned. |

---

## 4. Code Bugs & Doc-vs-Code Inconsistencies

1. **`ImporterService.importPayments` Never Saves Imported Payments + Uses `double` for Money (P0 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/importer/ImporterService.java:89-127`
   - **Evidence:** `importPayments()` instantiates `Payment p = new Payment()`, populates its fields, increments `success++`, and **never calls `paymentRepository.save(p)`** (`PaymentRepository` is not even injected into `ImporterService`). Every CSV payment import silently discards 100% of rows while reporting `{"success": N}`.
   - **Secondary violation:** Line 112 computes paise via `Math.round(Double.parseDouble(amount) * 100)`, directly violating `docs/DOMAIN_RULES.md` §1 (*"Never use `double` or `float` for money"*).
   - **Doc vs Code:** `ASSUMPTIONS.md` #25 states *"A payment always belongs to an invoice. There are no standalone wallet/advance payments"*, whereas `ImporterService.java:126` contains the comment `// payments without an invoice are recorded as unlinked; owner links in UI` (and no such UI or invoice-linking endpoint exists).

2. **`ReportService.checkinsPerDay` Fails / Returns Empty due to `LocalDate` vs `String` Mismatch in Raw Mongo Aggregation (P0/P1 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/report/ReportService.java:104-121`
   - **Evidence:** `Checkin.date` is a `java.time.LocalDate`, which Spring Data MongoDB persists as a BSON `Date` (UTC midnight). However, `ReportService.checkinsPerDay()` uses the low-level driver `mongoTemplate.getCollection("checkins").aggregate(...)` with `Filters.gte("date", from.toString())` (comparing a BSON `Date` against an ISO `String`), and then calls `d.getString("_id")` on the grouped `$date` field (which throws `ClassCastException: java.util.Date cannot be cast to java.lang.String` if matched).

3. **Refresh Token Rotation Breaks Family Revocation (`AuthService.java:90-108`) (P0 Security Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/auth/AuthService.java:90-108`
   - **Evidence:** `docs/DOMAIN_RULES.md` §8 and `HANDOVER.md` state that refresh tokens belong to a `familyId` so that reusing a rotated token revokes the entire family. However, `AuthService.refresh()` calls `issueTokens(user)`, which unconditionally executes `rt.setFamilyId(UUID.randomUUID().toString())`—generating a **brand-new `familyId`** on every rotation instead of preserving `stored.getFamilyId()`. Consequently, when a stolen/rotated token is replayed (`stored.getRevokedAt() != null`), `refreshTokenRepository.findByFamilyId(stored.getFamilyId())` only finds the old token and **fails to revoke the newly issued child token(s)**.

4. **DPDP Right-to-Erasure Crashes on the Second Erased Member per Gym (`MemberService.java` + `MongoConfig.java`) (P1 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/member/MemberService.java:149-165` and `backend/src/main/java/com/gymkhata/config/MongoConfig.java:24-26`
   - **Evidence:** `MemberService.erase()` sets `m.setPhone(null)`. `MongoConfig.java` defines a non-sparse unique compound index on `members`: `{ gymId: 1, phone: 1 }`. In MongoDB, a standard unique index treats `null` as a literal value, so erasing a **second** member in the same gym throws `E11000 duplicate key error collection: gymkhata.members index: gymId_1_phone_1 dup key: { gymId: "...", phone: null }`.

5. **`TenantService.updateProfile` Silently Discards `phone`, `email`, `address`, `city`, and `state` (P1 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/tenant/TenantService.java:23-41`
   - **Evidence:** `TenantController.ProfileRequest` accepts `name, phone, email, address, city, state, gstin, gstRegistered, invoicePrefix` and passes all 9 fields to `tenantService.updateProfile(...)`, but `TenantService.updateProfile` only updates `name`, `gstin`, `gstRegistered`, and `invoicePrefix`—silently ignoring `phone`, `email`, `address`, `city`, and `state`.

6. **Public Pay Page (`PublicPayController` & `PayPage.tsx`) Uses `totalPaise` Instead of `balanceDue` (P1 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/billing/PublicPayController.java:43-65` and `frontend/src/pages/public/PayPage.tsx`
   - **Evidence:** `PublicPayController.payPage()` returns `totalPaise` and generates the UPI deep link/QR for `inv.getTotalPaise()` instead of `paymentService.balanceDue(inv.getGymId(), inv.getId())`.
   - **Impact:**
     1. If a member already made a partial payment (e.g. paid ₹500 of ₹1,180), the QR code still encodes ₹1,180, and `PayPage.tsx` defaults the UTR submission amount to `totalPaise` (`118000`), which immediately fails with HTTP `400 OVERPAYMENT`.
     2. If the invoice is already fully paid (`status == "PAID"`), `PayPage.tsx` never shows the `pay.paid` (*"This invoice is already settled"*) banner—it still shows the QR code and UTR form!

7. **OpenPDF `PdfRenderer` Uses Built-in Helvetica (WinAnsiEncoding), Dropping Devanagari (Hindi) & `₹` Symbol (P1 Bug)**
   - **Location:** `backend/src/main/java/com/gymkhata/billing/PdfRenderer.java:25-31`
   - **Evidence:** `new Font(Font.HELVETICA, ...)` uses CP1252/WinAnsiEncoding, which cannot render Unicode Devanagari characters (`अ-ह`) in Hindi member names, gym names, or plan descriptions—rendering them as blank spaces in downloaded invoice PDFs.

8. **Broken i18n Keys in `Messaging.tsx` and `Login.tsx` (P1 Bug)**
   - **Location:** `frontend/src/pages/Messaging.tsx:107` and `frontend/src/pages/public/Login.tsx:111`
   - **Evidence:** `Messaging.tsx:107` calls `t('messaging.opedOut')` (typo for `messaging.optedOut`), displaying the literal string `"messaging.opedOut"` in the UI. `Login.tsx:111` calls `t('auth.phone')`, which is missing from both `en.json` and `hi.json`.

9. **Documentation Claims vs Implementation Drift (P2)**
   - `README.md` links to `docs/CONFIGURATION.md` (line 228) but omits it from the Repository Layout tree (lines 61–65).
   - `ASSUMPTIONS.md` #22 claims all 4 report endpoints use MongoDB aggregation pipelines; in reality, `ReportService.java` loads invoices, memberships, and members into JVM heap (`findByGymId`) for 3 of the 4 reports, using an aggregation pipeline only for `checkinsPerDay` (which is currently broken as noted in #2 above).
   - `AdminService.countActiveMembers` (`AdminService.java:195`) caps the count at `new CountOptions().limit(1000)`.

---

## 5. Security Review

### 5.1 Authentication, Password Reset & Session Management
- **P0 — Unauthenticated Account Takeover via `/api/v1/auth/password-reset/request`:**
  - `AuthController.requestReset` (`AuthController.java:68-76`) is public (`permitAll`) and returns `{"status": "reset_token_issued", "resetToken": "<plaintext-token>"}` directly in the HTTP response body in all profiles.
  - `Login.tsx:66-92` (`onForgot`) prompts for a phone number, reads `res.resetToken` from the API response, and immediately calls `/api/v1/auth/password-reset/confirm`. Any anonymous attacker who knows a gym owner's phone number—or the default super-admin phone `+919000000001`—can reset their password and take over the account.
- **P0 — Refresh Token Family Revocation Broken:** See Section 4, Item #3 (`issueTokens` overwrites `familyId` on refresh).
- **P1 — Suspended User Still Accepted by `JwtAuthenticationFilter`:**
  - `JwtAuthenticationFilter.java:53` loads `User` via `userRepository.findById(userId)` and authenticates the request without checking `User.STATUS_ACTIVE.equals(user.getStatus())`. If a user (`SUPER_ADMIN` or `GYM_OWNER`) is suspended while holding an unexpired 15-minute access token, `JwtAuthenticationFilter` still authenticates them.
- **P1 — Production Startup Secret Validation Incomplete:**
  - `JwtService.java:29` checks `if (activeProfile.contains("prod") && DEV_SECRET.equals(secret))`, which does not catch empty/short secrets (< 32 bytes) or profile combinations like `prod,cloud`.
  - `Bootstrapper.java` seeds the `SUPER_ADMIN` account with `ChangeMe@123` if `SUPER_ADMIN_PASSWORD` is not set, even under `prod`.
  - `CORS_ALLOWED_ORIGINS` is not validated at startup under `prod`.

### 5.2 Tenant Isolation
- **Strong foundation:** Every tenant controller derives `gymId` exclusively from `SecurityUtils.currentGymId()` (populated from the database `User` record in `JwtAuthenticationFilter`), never from URL path params, query params, or request bodies.
- **`SUPER_ADMIN` vs Tenant Endpoints:** If `SUPER_ADMIN` (`gymId == null`) calls a tenant endpoint, `SecurityUtils.currentGymId()` throws `403 NO_GYM`.
- **`PENDING` vs `SUSPENDED` Gym Status:** `TenantStatusInterceptor` blocks any non-`ACTIVE` gym on `/api/v1/**` with `403 ACCOUNT_SUSPENDED`, and `AuthService.login()` blocks login when `gym.status != ACTIVE`.

### 5.3 Rate Limiting & Brute-Force Protection
- **P0/P1 — No Rate Limiting Anywhere:** There is no rate limiting or IP/account lockout on `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/signup`, `/api/v1/auth/password-reset/**`, or `/api/v1/pay/{token}/utr`.

### 5.4 CORS, Security Headers & Actuator/Swagger Exposure
- **CORS:** Hardcoded to `localhost:5173` and `localhost:8080` in `application.yml`; not configurable via environment variable.
- **Missing HTTP Security Headers:** `SecurityConfig.java` does not set `Content-Security-Policy`, `Strict-Transport-Security` (HSTS), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` / `frame-ancestors 'none'`, or `Referrer-Policy`.
- **Actuator & Swagger Exposure:**
  - `SecurityConfig.java:46-54` sets `.anyRequest().permitAll()` for all non-`/api/**` paths. In `dev`, `/actuator/env` and `/actuator/info` are publicly accessible without authentication.
  - `/swagger-ui/**` and `/api-docs/**` are publicly accessible in `prod`.

### 5.5 Public Pay-Page Token Handling
- `Invoice.payToken` is generated using `SecureRandom` (`24` bytes = 192 bits of entropy, base64url-encoded) with a 90-day expiry (`payTokenExpiresAt`).
- **Gaps:**
  1. `MongoConfig.java` does **not** create an index on `invoices.payToken`, causing every public `/api/v1/pay/{token}` request to perform a full collection scan on `invoices`.
  2. `PublicPayController` does not reject `VOID` invoices when rendering the pay page and returns `totalPaise` instead of `balanceDue`.
  3. `PublicPayController` exposes the member's full name and invoice items to anyone with the token (acceptable for an invoice link, but must be rate-limited).

### 5.6 CSV Import Limits & Formula Injection
- **P1 — No Row Limit or Request Size Cap:** `CsvParser.parse(csv)` processes arbitrarily large strings in memory with no row cap (e.g. > 1,000 rows).
- **P1 — CSV Formula Injection (CSV Injection):** `ImporterService` and `CsvParser` do not reject or sanitise cells beginning with `=`, `+`, `-`, `@`, `\t`, or `\r`. If imported member names/notes are later exported or opened in Excel/Google Sheets, formulas execute on the user's machine.
- **P1 — Missing Column / Header Validation:** `ImporterService.importMembers` silently treats a CSV with completely wrong headers or missing `name`/`phone` columns as empty values row-by-row rather than validating required headers upfront.

### 5.7 Error Leakage, Logging & PII
- `GlobalExceptionHandler` returns structured RFC-style error bodies without stack traces on expected errors, and logs unexpected exceptions at `ERROR` while returning a generic `"Unexpected error"` message to the client.
- **PII in Logs:** `Bootstrapper.java:46` logs `Configured super-admin phone is invalid: {}` with the raw phone value. `application-prod.yml` writes plain-text logs to `logs/gymkhata.log` rather than structured JSON logs to `stdout`.

### 5.8 Missing Database Indexes (`MongoConfig.java`)
- `refresh_tokens`: No index on `tokenHash`, `userId`, or `familyId`.
- `password_reset_tokens`: No index on `tokenHash` or `userId`.
- `invoices`: No index on `payToken`.
- `audit_logs`: No index on `(gymId, createdAt)` or `createdAt`.

---

## 6. Prioritised Risk List

### P0 — Blocks Launch (Must Fix Before Any Staging/Production Deployment)
1. **[P0-SEC-1] Unauthenticated Password Reset Token Leakage (`AuthController.java:68` & `Login.tsx:66`):** Returning `resetToken` in the HTTP response allows trivial account takeover of any gym owner or super-admin account by phone number.
2. **[P0-SEC-2] Refresh Token Rotation Overwrites `familyId` (`AuthService.java:96`):** Rotating a refresh token assigns a new `UUID` to `familyId`, breaking refresh-token reuse detection and family revocation.
3. **[P0-SEC-3] Missing Production Fail-Fast Guards (`JwtService`, `Bootstrapper`, `SecurityConfig`):** `prod` profile does not fail fast on missing/weak `JWT_SECRET`, default `SUPER_ADMIN_PASSWORD` (`ChangeMe@123`), or missing `CORS_ALLOWED_ORIGINS`.
4. **[P0-BUG-1] CSV Payment Import Discards All Data & Uses `double` Math (`ImporterService.java:89-127`):** `importPayments` never saves `Payment` documents to MongoDB and uses floating-point math for currency.
5. **[P0-BUG-2] Check-ins Report Broken Due to BSON `Date` vs `String` Mismatch (`ReportService.java:104-121`):** `checkinsPerDay` compares `LocalDate` against `String` and casts `java.util.Date` to `String`.

### P1 — High Priority (Security Hardening, Core Flow Bugs & Compliance)
1. **[P1-SEC-1] No Rate Limiting / Lockout:** Login, refresh, self-signup, password reset, and public pay-page UTR endpoints lack rate limiting (`X-Forwarded-For` aware).
2. **[P1-SEC-2] Missing Security Headers & Public Swagger/Actuator in Prod:** Add CSP, HSTS, `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`; restrict Swagger and non-health Actuator endpoints in `prod`.
3. **[P1-SEC-3] CSV Import Lacks Row Limits, Header Checks & Formula Injection Guards (`=`, `+`, `-`, `@`).**
4. **[P1-SEC-4] `JwtAuthenticationFilter` Does Not Verify `user.status == ACTIVE` on Access Token Authentication.**
5. **[P1-BUG-1] DPDP Erase Unique Index Crash (`MongoConfig.java:24` & `MemberService.java:149`):** Second erased member in the same gym fails with `DuplicateKeyException` on `(gymId, phone: null)`.
6. **[P1-BUG-2] Missing `POST /api/v1/payments/{id}/reject` Endpoint & UI Action:** Gym owners can confirm a pending UPI UTR (`POST /payments/{id}/confirm`), but cannot reject an invalid/mistaken UTR.
7. **[P1-BUG-3] Public Pay Page Ignores Partial Payments & Paid/Void Status (`PublicPayController.java` & `PayPage.tsx`):** Encodes `totalPaise` instead of `balanceDue`, causing partial-payment settlements to fail with `OVERPAYMENT` and already-paid invoices to still display a payment form.
8. **[P1-BUG-4] `TenantService.updateProfile` Ignores `phone`, `email`, `address`, `city`, and `state`.**
9. **[P1-BUG-5] `PdfRenderer` Drops Hindi (Devanagari) Glyphs:** Needs an embedded Unicode Devanagari-capable TrueType font with `Identity-H` encoding.
10. **[P1-BUG-6] Frontend i18n Key Typos (`messaging.opedOut` in `Messaging.tsx`, `auth.phone` in `Login.tsx`) & Logout Not Calling `/api/v1/auth/logout`.**

### P2 — Medium Priority (Performance, Observability & Polish)
1. **[P2-PERF-1] Missing MongoDB Indexes on `refresh_tokens`, `password_reset_tokens`, `invoices.payToken`, and `audit_logs`.**
2. **[P2-PERF-2] Frontend Bundle Size (~952 kB Single Chunk):** Route-level `React.lazy` code-splitting and vendor chunk splitting (e.g. `recharts`) needed in `App.tsx` and `vite.config.ts`.
3. **[P2-OBS-1] Plain-Text File Logging in Prod:** Switch `application-prod.yml` to structured JSON logging to `stdout` with strict PII scrubbing.
4. **[P2-UX-1] Untested Adapters Exposed as Active Options:** WhatsApp Cloud (`META_CLOUD`) and Razorpay adapters should be clearly marked "Coming Soon" in UI/docs unless sandbox-verified.
5. **[P2-DOC-1] Sync `docs/API.md`, `backend/.env.example`, `README.md` Layout, and `ASSUMPTIONS.md`.**
