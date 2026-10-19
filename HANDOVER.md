# HANDOVER.md

Everything you need to run, test and extend GymKhata.

---

## 1. What was built

A multi-tenant gym management web app (monorepo) with a strict two-tier account
model: one platform `SUPER_ADMIN` and exactly one `GYM_OWNER` per gym. The owner
runs the entire gym from a single login — there are no staff sub-accounts by
design.

Delivered and verified:

- Backend (Spring Boot 3.5.16 / Java 21 / MongoDB) with feature-based packages,
  JWT access tokens + rotating refresh tokens, strict tenant scoping, and
  unique-index enforcement of the one-owner-per-gym rule.
- Frontend (Vite 8 / React 19 / TypeScript / Tailwind 4) as an installable PWA
  with English + Hindi, a public landing/pricing/signup/pay site, the owner app
  and the super-admin console.
- India domain correctness: paise-only money, GST back-calculation, gapless
  invoice numbering, credit notes, UPI deep links + QR, 12-digit UTR handling,
  WhatsApp outbox with quiet hours and per-gym bilingual templates.
- DPDP: member data export, anonymising erase that keeps invoices and payments,
  and an audit log with masked phone numbers.
- 59 backend tests and 7 frontend tests, all passing (see `TEST_REPORT.md`).

---

## 2. Run it

### Prerequisites (already present on this machine)

| Tool | Version | Note |
|---|---|---|
| JDK | 21.0.12.1 | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` |
| Maven | 3.9.16 | `C:\Program Files (x86)\apache-maven-3.9.16` |
| Node / npm | v24.19.0 / 11.17.0 | Vite toolchain only |
| MongoDB | 9.0.2 | already running as a Windows service on `localhost:27017` |

> The machine's global `JAVA_HOME` points at a JRE 8, which Maven cannot use.
> `scripts/resolve-jdk.ps1` locates a JDK 17+ and sets it **for the build only** —
> the global environment is never modified.

### One command — batch (double-click friendly)

```bat
start-all.bat
```

Opens a console window per service (backend on :8080, Vite on :5173), waits
until both answer, then opens the browser. `start-all.bat build` builds the
project first. Stop with `stop-all.bat`.

Two things this launcher does not assume, because assuming them breaks it:

- **the `java` on `PATH` is a JRE 8**, not a JDK — the jar is Java 21, so the
  launcher locates a real JDK 17+ and puts its `bin` on `PATH` for its own
  process only (the global `PATH` and `JAVA_HOME` are never modified);
- **the frontend is on :5173**, which `vite.config.ts` pins with
  `strictPort: true` so it can never silently drift to :5174 (the backend CORS
  allowlist only lists :5173).

### One command — PowerShell (headless, log files)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1   # stop what it started
powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1 -WhatIf  # dry run
```

`stop-all.ps1` only touches processes recorded in `logs/gymkhata-processes.json`
(verified by PID **and** start time), so it never kills an unrelated process
and never stops MongoDB.

> Use one launcher or the other — the two track their processes differently.

### Build and run by hand

```powershell
# stop any running copy first, otherwise the jar is locked
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force

powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
java -jar backend\target\gymkhata-backend-0.1.0.jar
```

Open <http://localhost:8080>.

`build-all.ps1` builds the frontend, copies `frontend/dist` into
`backend/src/main/resources/static`, then packages the Spring Boot jar, so the
jar serves both the API and the PWA on one port.

Options:
- `-SkipTests` — skip the backend test run during packaging.
- `-JavaHome <path>` — force a specific JDK.

### Development

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-dev.ps1
# Vite dev server: http://localhost:5173  (proxies /api to :8080)
# Spring Boot    : http://localhost:8080
powershell -ExecutionPolicy Bypass -File scripts\stop-dev.ps1
```

---

## 3. First things to try

1. Open <http://localhost:8080/pricing> — the public price page, driven by config.
2. Log in as the super admin (`+919000000001` / `ChangeMe@123`) → create a gym and
   its owner in one step.
3. Log in as the owner → **Plans** → add a plan (price is all-inclusive, e.g. 1180).
4. **Members** → add a member with WhatsApp opt-in.
5. Open the member → **Renew** → pick the plan → **Generate invoice**.
   Check the invoice: `GK/26-27/000001`, `TAX_INVOICE`, SAC 999723, taxable
   112381 + CGST 2809 + SGST 2810 = 118000.
6. **Billing** → open the invoice → **Record payment** (cash/card), or open the
   pay link to see the UPI deep link.
7. **Check-ins** → check the member in; try checking them in twice (should be
   rejected).
8. **Messages** → **Generate today's reminders** → tap **Send on WhatsApp**
   (opens `wa.me` with the message) → **Mark sent**.
9. **Reports** → revenue, expiries, check-in trends, members per plan.
10. **Settings** → set the UPI VPA, edit the English/Hindi WhatsApp templates,
    switch GST on/off. **More → Import** → paste a CSV of members.
11. Suspend the gym from the admin console and try to log in as the owner →
    `403 ACCOUNT_SUSPENDED`.

---

## 4. Test it

```powershell
# backend: 59 tests
cd backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
mvn test

# frontend: 7 tests + type check
cd frontend
npm test
npx tsc -b

# startup scripts: start -> reuse -> dry-run -> stop lifecycle (17 checks)
powershell -ExecutionPolicy Bypass -File scripts\test-start-all.ps1
```

The backend tests create a throwaway MongoDB database
(`gymkhata_test_<random>`) and drop it when the test JVM exits. They never
touch `gymkhata_dev` or any other database.

Full results, including what is **not** covered, are in `TEST_REPORT.md`.

---

## 5. Where the important code is

| Concern | Location |
|---|---|
| One-command startup (batch, console windows) | `start-all.bat` |
| One-command startup (PowerShell, headless) | `scripts/start-all.ps1` |
| Shutdown | `stop-all.bat` (console windows) / `scripts/stop-all.ps1` (tracked pids) |
| Process tracking / HTTP & TCP probes | `scripts/process-tracking.ps1` |
| JDK discovery (17+) | `scripts/resolve-jdk.ps1` (shared with the build) |
| Startup script verification | `scripts/test-start-all.ps1` |
| Money / GST / FY / numbering / words / phones / UPI / check-in rules | `backend/src/main/java/com/gymkhata/domain/` (pure, no Spring) |
| JWT + refresh rotation | `backend/.../auth/security/`, `backend/.../auth/AuthService.java` |
| Tenant isolation | `SecurityUtils.currentGymId()` (gymId always comes from the JWT) + `TenantStatusInterceptor` |
| One-owner-per-gym | `UserRepository` + partial unique index in `MongoConfig` |
| Gapless invoice numbers | `BillingService.nextNumber()` (`findAndModify` + `$inc` + upsert) |
| Invoice + GST construction | `BillingService.buildLines()` |
| Payments | `PaymentService` |
| PDF | `billing/pdf/PdfRenderer.java` (OpenPDF) |
| QR | `PublicPayController` (ZXing) |
| WhatsApp outbox | `messaging/MessagingService.java`, providers in `messaging/` |
| Audit log | `common/audit/AuditService.java`, viewer in `audit/` |
| Frontend shell / nav | `frontend/src/components/AppShell.tsx` |
| Public pages | `frontend/src/pages/public/` |
| Owner app pages | `frontend/src/pages/app/` |
| Super admin console | `frontend/src/pages/admin/AdminGyms.tsx` |
| Translations | `frontend/src/i18n/en.json`, `frontend/src/i18n/hi.json` |

---

## 6. Things to know before you extend it

1. **No multi-document transactions.** MongoDB is standalone here. Use
   single-document atomic updates, unique indexes, idempotency keys and
   compensating steps (see `BillingService.writeVoid` and
   `AdminService.createGymWithOwner`).
2. **Never take `gymId` from a request body or query parameter.** It must come
   from the authenticated principal (`SecurityUtils.currentGymId()`).
3. **Money is integer paise.** Never use `double` or `float` for money.
4. **Invoice lines must always sum exactly to the invoice total.** The GST
   helper guarantees it; keep it that way when adding line types.
5. **Invoice numbers are gapless.** Any number taken from the counter must
   either become an invoice or a VOID invoice.
6. **GST rates are effective-dated** in `app.gst.rates` and are snapshotted onto
   the line at issue time — never recompute an old invoice.
7. **Business dates are `LocalDate` in IST**; instants are stored in UTC. Use
   the injected `Clock`, never `LocalDate.now()` directly.
8. **Quiet hours and WhatsApp opt-in are respected automatically** — do not
   create a message for a member who has not opted in.
9. **The frontend bundle is copied into the backend** by `build-all.ps1`. If you
   change the frontend, re-run it (or `npm run build` + copy) before packaging.
10. **`app.jwt.secret` is a development value.** Change it before any real
    deployment, along with the super-admin password.
11. **The startup scripts never touch global state.** They set `JAVA_HOME` only
    inside their own process. `start-all.ps1` / `stop-all.ps1` communicate
    through `logs/gymkhata-processes.json`; anything not in that file is left
    alone.
12. **The frontend must run on :5173.** `vite.config.ts` sets
    `strictPort: true` so Vite fails loudly instead of drifting to :5174,
    which the backend CORS allowlist does not include. If you change the port,
    change `app.cors-allowed-origins` in `application.yml` too.

---

## 7. Ideas for the next iteration

- Delivery channels: connect a real WhatsApp Business provider and an
  email/SMS provider so password reset and receipts are actually delivered.
- Super-admin reporting across gyms (revenue, churn, MRR from subscription dates).
- Member self-service portal (view plan, book a class, freeze a membership).
- Class/schedule management and attendance per class.
- Trainer-side payouts if a gym later wants trainer logins (this would be a
  deliberate change to the strict one-owner rule).
- Automated reminders scheduled daily instead of on demand.
- Backup/restore of a gym's data as a portable archive.

---

## 8. Honest status

Everything in `TEST_REPORT.md` marked as verified was run and observed. The
following are **not** production-ready and are labelled as such in the code and
the report:

- WhatsApp Cloud and Razorpay adapters (flagged, no live calls, no credentials).
- Password reset delivery (token returned by the API instead of being sent).
- Browser UI verification (no browser installed on this machine).
- Rate limiting, CSRF hardening beyond the stateless API, and encryption at
  rest for backups — none are implemented.
