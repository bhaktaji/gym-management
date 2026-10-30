# GymKhata

> **One login. Your whole gym. Nothing hidden.**

GymKhata is a multi-tenant gym management web app for independent gyms in
tier-2/3 India. It is mobile-first, installable as a PWA, and priced on one flat
all-in monthly fee — no per-member and no per-message charges.

The account model is deliberately strict:

| Role | How many | What it can do |
|---|---|---|
| `SUPER_ADMIN` | one per platform | Onboard gyms, manage each gym's single owner account, suspend/activate a gym, track the manual SaaS subscription |
| `GYM_OWNER` | **exactly one per gym** | Everything else — members, plans, memberships, billing, UPI payments, check-ins, WhatsApp outbox, leads, reports, CSV import, gym settings |

There are **no sub-staff logins**. Every gym has one owner, and that owner runs
the whole gym from a single account.

---

## Core features

- **Enquiry → enrolment → payment → invoice → check-in → renewal reminder → renewal** — the full gym loop.
- **Members & memberships** — enrol once, renew in seconds, always see who is active and who is expiring. Renewing supersedes the previous active membership.
- **GST-correct billing** — prices are entered all-inclusive; the system back-calculates taxable value, CGST/SGST (or IGST for inter-state), and renders the amount in words. Tax invoices (or a Bill of Supply when the gym is not GST-registered) with SAC code, GSTIN and a downloadable PDF.
- **Gapless invoice numbering** — `PFX/26-27/000123` per gym per financial year, from an atomic counter. Credit notes live in their own `CN` series; issued invoices are immutable.
- **UPI payments** — a standard `upi://pay` deep link and QR for the gym's own VPA. Members submit a 12-digit UTR on a tokenized public pay page (stays pending until the owner confirms); cash/card are recorded at the counter. Partial payments, balance tracking, duplicate-UTR and overpayment rejection.
- **Check-ins** — one check-in per member per day, blocked without a valid active membership, with a visible "who is inside now" list.
- **WhatsApp outbox** — today's renewal reminders with one-tap `wa.me` links, per-gym editable templates in English and Hindi, opt-in captured at enrolment, and 21:00–08:00 IST quiet hours.
- **Leads** — capture walk-ins and convert a lead straight into a member.
- **Reports** — revenue by month, expiring memberships, check-in trends, members per plan.
- **CSV import** — members and payments, with per-row error reporting.
- **Data protection (DPDP)** — member data export (JSON), an anonymising erase that keeps invoices and payments, and an audit log of owner and super-admin actions with masked phone numbers.
- **Bilingual** — the whole UI is available in English and Hindi.
- **Public pages** — landing, pricing (driven by config), gym self-signup, login, and a tokenized member pay page.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16, Spring Web, Spring Security (JWT access token + rotating refresh token), Spring Data MongoDB, Bean Validation, springdoc-openapi, OpenPDF (PDF), ZXing (QR) |
| Frontend | Vite 8, React 19, TypeScript, React Router 7, TanStack Query 5, react-hook-form + zod, Tailwind CSS 4, react-i18next, vite-plugin-pwa, Recharts |
| Database | MongoDB 9 (standalone — no multi-document transactions) |
| Build | Maven 3.9 (backend), npm (frontend) |
| Deployment | One executable Spring Boot jar that also serves the built PWA |

---

## Repository layout

```
.
├── start-all.bat              one-click launcher: MongoDB → backend → frontend, then opens the browser
├── stop-all.bat               closes what start-all.bat started (never touches MongoDB)
├── GETTING_STARTED.md         step-by-step local setup
├── README.md                  this file
├── ASSUMPTIONS.md             decisions taken where the spec was silent
├── ENV_REPORT.md              the toolchain detected on the reference machine
├── HANDOVER.md                how to run, test and extend the project
├── TEST_REPORT.md             what was verified, with real counts
│
├── backend/                   Spring Boot API
│   ├── pom.xml
│   ├── .env.example           every config knob, with placeholder values
│   └── src/main/
│       ├── java/com/gymkhata/
│       │   ├── domain/        PURE logic — money, GST, financial year, invoice
│       │   │                  numbering, Indian number words, phones, UPI, check-in
│       │   │                  decisions. No Spring imports, fully unit-tested.
│       │   ├── auth/          login, JWT, refresh-token rotation, password reset
│       │   ├── admin/         super-admin console (gyms + their single owner)
│       │   ├── tenant/        gym profile, settings, plan & usage
│       │   ├── member/        members, DPDP export / erase
│       │   ├── plan/          membership plans
│       │   ├── membership/    enrolments
│       │   ├── billing/       invoices, gapless numbering, credit notes,
│       │   │                  UPI payments, PDF, provider adapters
│       │   ├── checkin/       check-ins
│       │   ├── messaging/     WhatsApp outbox + provider adapters
│       │   ├── lead/          leads and conversion
│       │   ├── report/        aggregations
│       │   ├── importer/      CSV import
│       │   ├── audit/         audit-log viewer
│       │   └── common/        config, security, errors, audit writer, clock
│       └── resources/
│           ├── application.yml          all config, with dev defaults
│           ├── application-dev.yml
│           └── application-prod.yml
│
├── frontend/                  Vite + React + TypeScript PWA
│   ├── src/
│   │   ├── pages/public/      landing, pricing, signup, login, pay page
│   │   ├── pages/app/         owner console (dashboard, members, billing, …)
│   │   ├── pages/admin/       super-admin console
│   │   ├── components/        shells + UI primitives
│   │   ├── auth/              auth context
│   │   ├── i18n/              English + Hindi
│   │   └── lib/               API client + formatting
│   ├── vite.config.ts         dev server on :5173, proxying /api to :8080
│   └── package.json
│
├── docs/
│   ├── API.md                 every endpoint, with the error envelope
│   ├── DATA_MODEL.md          collections, indexes, tenancy
│   └── DOMAIN_RULES.md        the India-specific rules, and how they are implemented
│
└── scripts/
    ├── build-all.ps1          build frontend → copy into backend → package jar
    ├── start-all.ps1          headless launcher (background services + logs/)
    ├── stop-all.ps1           stops only what start-all.ps1 started
    ├── run-dev.ps1            backend + Vite with hot reload
    ├── stop-dev.ps1
    ├── resolve-jdk.ps1        locates a JDK 17+ without touching global config
    ├── process-tracking.ps1   shared PID / HTTP / TCP helpers
    └── test-start-all.ps1     17-check startup lifecycle test
```

---

## Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| **JDK** | **17+** (21 recommended) | Spring Boot 3.5.16 runs on 17+; the reference build used OpenJDK 21.0.12 |
| **Maven** | **3.6+** | Reference build used 3.9.16 |
| **Node.js** | **20.19+** (22.12+ recommended) | Vite 8 requires `^20.19.0 \|\| >=22.12.0`. Node 18 is **not** enough. |
| **npm** | 9+ | Ships with Node |
| **MongoDB** | **6+** (9 recommended) | Must be reachable on `localhost:27017`. Standalone is fine — the app uses no multi-document transactions. |
| **curl** | any | Used for readiness probes (Windows 10 1803+ ships it). Optional. |

> **Windows users:** the reference machine had a **JRE 8 on `PATH`**, which cannot
> run the Java 21 jar. Both launchers locate a real JDK 17+ themselves and put it
> on `PATH` **for their own process only** — your global `JAVA_HOME` and `PATH`
> are never modified.

---

## Quick start

The fastest way to run everything. Pick one launcher.

### Option A — batch (console windows, double-click friendly)

```bat
start-all.bat
```

It reuses or starts MongoDB, opens a console window for the backend and one for
the frontend, waits until both are answering, then opens your browser.
`start-all.bat build` builds first. Stop with `stop-all.bat`.

### Option B — PowerShell (headless, log files)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
```

> Use **one** launcher or the other — they track their processes differently.

### URLs

| URL | What |
|---|---|
| http://localhost:5173 | Frontend (Vite dev server, proxies `/api` to :8080) |
| http://localhost:8080 | Backend + API (also serves the built app) |
| http://localhost:8080/admin | Super-admin console |
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:8080/actuator/health | Health check |

### Seeded accounts

| Role | Phone | Password |
|---|---|---|
| Super admin | `+919000000001` | `ChangeMe@123` |

The super admin is seeded on first boot **only when the `users` collection is
empty**. Override with `SUPER_ADMIN_PHONE` / `SUPER_ADMIN_PASSWORD`.

---

## Configuration

Everything is environment-variable driven with working dev defaults in
`backend/src/main/resources/application.yml`. Copy `backend/.env.example` to see
every knob — or read `docs/API.md` and `backend/.env.example`.

### Profiles

| Profile | Purpose |
|---|---|
| `dev` (default) | Local MongoDB on `localhost:27017`, database `gymkhata_dev` |
| `prod` | Every secret must come from the environment. `JWT_SECRET` has **no default** — the app refuses to start with the development secret. |

---

## Tests

```bash
# Backend: 88 tests across 20 test classes (41 unit + 47 integration against MongoDB)
mvn -f backend/pom.xml test

# Frontend: 10 Vitest tests across 4 files + TypeScript type check + Vite PWA build
npm --prefix frontend test
npm --prefix frontend run build

# Playwright E2E: 12 real-backend tests (6 flows × desktop & 390px mobile) with traces & screenshots
./frontend/node_modules/.bin/playwright test -c frontend/playwright.config.ts
```

The backend tests create and drop their own `gymkhata_test_<random>` database;
they never touch `gymkhata_dev`. Full results are in `TEST_REPORT.md`.

---

## API reference

Every endpoint is documented in [`docs/API.md`](docs/API.md), including the error
envelope, authentication, and the public vs super-admin vs owner groupings. The
live OpenAPI spec is at `http://localhost:8080/api-docs` and the interactive UI
at `http://localhost:8080/swagger-ui.html` (disabled by default in `prod` via
`SWAGGER_ENABLED=false`). Every configuration knob is in
[`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) and `backend/.env.example`.

---

## Security & Deployment Notes

- **Single-Container Cloud Run Deployment**: Root `Dockerfile` builds the Vite PWA into `classpath:/static/` inside the Spring Boot JAR (`SpaWebMvcConfigurer`), serving both the SPA and `/api/v1/**` from the same origin on port `8080` with zero CORS overhead.
- **Fail-Fast Production Guard (`ProdConfigValidator`)**: Under `--spring.profiles.active=prod`, the application refuses to start if `JWT_SECRET` (< 32 bytes or default), `SUPER_ADMIN_PASSWORD` (`ChangeMe@123` or `DemoAdmin@2026`), or `CORS_ALLOWED_ORIGINS` (`*` or `localhost`) are insecure.
- **Trusted Proxy & Lockout Safety**: `RateLimitFilter` and `CheckinService` extract the real client IP via `X-Forwarded-For` only when the immediate peer is a trusted proxy (`127.0.0.1`, RFC1918, Cloud Run `169.254.x.x`), walking right-to-left to prevent header spoofing. PIN lockout uses exponential backoff (`60s → 300s → 900s → 3600s`) keyed on `phone + clientIp` so one attacker cannot lock out other members or the real member on another network, with instant owner unlock (`POST /api/v1/members/{id}/reset-pin-lockout`).
- **Password Reset Security**: `POST /api/v1/auth/password-reset/request` never leaks tokens in public responses; `SUPER_ADMIN` can issue a single-use token via `/api/v1/admin/gyms/{gymId}/password-reset`.

## Known limitations

- MongoDB runs in standalone-compatible mode; the application uses single-document atomic operations (`findAndModify` `$inc`), partial unique indexes, and compensating `VOID` writes rather than multi-document transactions.
- The WhatsApp Cloud (`META_CLOUD`) and Razorpay adapters are behind feature flags (`WHATSAPP_CLOUD_ENABLED=false`, `RAZORPAY_ENABLED=false`) and labelled "Coming Soon" in the UI until live sandbox credentials are provided; `clicktochat` (`wa.me`) and `directupi` (NPCI UPI QR + 12-digit UTR) are production-ready defaults.

