# GymKhata

Gym management for independent gyms in tier-2/3 India. Mobile-first, installable
as a PWA, with a two-tier account model:

- **SUPER_ADMIN** — one platform administrator. Creates gyms, manages each gym's
  single owner account, suspends/activates gyms, tracks SaaS subscription expiry.
- **GYM_OWNER** — exactly **one** owner account per gym. No sub-staff logins.
  The owner runs their whole gym from one login.

Core loop: enquiry → enrolment → payment (UPI or cash) → GST-correct invoice →
check-ins → WhatsApp renewal reminder → renewal.

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16, Spring Web, Spring Security (JWT + rotating refresh), Spring Data MongoDB, Bean Validation, springdoc-openapi, OpenPDF, ZXing |
| Frontend | Vite 8, React 19, TypeScript, React Router 7, TanStack Query 5, react-hook-form + zod, Tailwind CSS 4, react-i18next (English + Hindi), vite-plugin-pwa, Recharts |
| Database | MongoDB 9 (standalone) |
| Build | Maven 3.9 (backend), npm (frontend only) |

---

## Quick start

```powershell
# 1. full build: frontend bundle -> backend static resources -> executable jar
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1

# 2. run
java -jar backend\target\gymkhata-backend-0.1.0.jar
```

Then open **http://localhost:8080**.

> The build script finds a JDK 17+ on the machine and uses it for Maven only.
> Your global `JAVA_HOME` / `PATH` are never modified. Pass
> `-JavaHome <path>` to force a specific JDK.

### Development (hot reload)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-dev.ps1
# frontend  : http://localhost:5173  (Vite, proxies /api to :8080)
# backend   : http://localhost:8080
# stop with : scripts\stop-dev.ps1
```

### Seeded accounts

| Role | Phone | Password |
|---|---|---|
| Super admin | `+919000000001` | `ChangeMe@123` |
| (created via admin console / self-signup) | your gym owner | your choice |

The super admin is seeded on first boot only when the `users` collection is empty.
Override with `SUPER_ADMIN_PHONE` / `SUPER_ADMIN_PASSWORD` / `SUPER_ADMIN_NAME`.

---

## Configuration

All configuration is environment-variable driven with sane defaults in
`backend/src/main/resources/application.yml`. See `backend/.env.example`.

| Variable | Default | Meaning |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/gymkhata_dev` | Database |
| `SERVER_PORT` | `8080` | HTTP port |
| `SUPER_ADMIN_PHONE` / `SUPER_ADMIN_PASSWORD` | seeded values | Platform admin |
| `SIGNUP_AUTO_ACTIVATE` | `false` | Self-signed-up gyms start `PENDING` |
| `MESSAGING_PROVIDER` | `clicktochat` | `clicktochat` \| `whatsappcloud` \| `mock` |
| `WHATSAPP_CLOUD_*` | empty | WhatsApp Cloud API credentials (off by default) |
| `PAYMENT_PROVIDER` | `directupi` | `directupi` \| `mock` \| `razorpay` |
| `RAZORPAY_*` | empty | Razorpay keys (off by default, NOT PRODUCTION-TESTED) |

`app.jwt.secret` in `application.yml` is a **development** value. Set a long
random value via configuration before any real deployment.

---

## Repo layout

```
gymkhata/
  backend/          Spring Boot API (feature packages: auth, admin, tenant, member,
                    plan, membership, billing, checkin, messaging, lead, report,
                    importer, audit, common, domain)
  frontend/         Vite + React + TS PWA
  docs/             domain rules, API surface, data model
  scripts/          build-all.ps1, run-dev.ps1, stop-dev.ps1, resolve-jdk.ps1
  ENV_REPORT.md     what was detected on this machine
  ASSUMPTIONS.md    every decision taken where the spec was silent
  TEST_REPORT.md    what was actually run, with real counts
  HANDOVER.md       how to run, test and extend it
```

---

## API surface (all under `/api/v1`)

Public (no auth): `auth/login`, `auth/refresh`, `auth/signup`,
`auth/password-reset/*`, `config/public`, `pay/{token}`, `pay/{token}/qr`,
`pay/{token}/utr`.

Super admin: `admin/gyms` (list, create), `admin/gyms/{id}/owner`,
`admin/gyms/{id}/password-reset`, `admin/gyms/{id}/status`,
`admin/gyms/{id}/subscription`.

Gym owner: `members`, `members/{id}`, `members/{id}/export`, `members/{id}/erase`,
`plans`, `memberships`, `billing/invoices`, `billing/invoices/{id}/void`,
`billing/invoices/{id}/credit-note`, `billing/invoices/{id}/pdf`,
`billing/invoices/{id}/pay-link`, `payments/counter`, `payments/{id}/confirm`,
`payments/pending`, `checkins`, `messaging/outbox`, `leads`, `leads/{id}/convert`,
`reports/*`, `import/members`, `import/payments`, `tenant/gym`, `tenant/profile`,
`tenant/settings`, `tenant/plan-usage`, `audit/logs`.

Interactive docs: **http://localhost:8080/swagger-ui.html**

---

## India domain rules implemented

- **Money** is always integer paise. Display uses Indian grouping (₹1,23,456.00).
- **GST** back-calculated from the all-in price:
  `taxable = round(total × 100 ÷ (100 + rate))`, `tax = total − taxable`,
  `CGST = floor(tax ÷ 2)`, `SGST = tax − CGST`. IGST when the place of supply
  differs from the supplier's state. Rates are effective-dated (18% before
  22 Sep 2025, 5% after) and snapshotted onto the invoice line.
  Lines always sum exactly to the invoice total.
- **Time**: business dates are `LocalDate` in `Asia/Kolkata`; instants are stored
  in UTC. Financial year runs 1 Apr → 31 Mar.
- **Invoice numbers** are gapless per gym per FY: `PFX/26-27/000123`
  (prefix ≤ 3 chars, total ≤ 16). If an insert fails after the number was
  taken, a VOID invoice is written with that number so the series stays gapless.
- **Credit notes** live in their own `CN` series. Issued invoices are immutable.
- **UPI** uses the gym's own VPA; `upi://pay` deep link + QR; the member submits a
  12-digit UTR on the tokenized public pay page and it stays
  `PENDING_VERIFICATION` until the owner confirms. Duplicate UTRs and
  overpayments are rejected. Partial payments are allowed.
- **WhatsApp** outbox lists the day's due reminders with one-tap `wa.me` links.
  Opt-in is captured per member, quiet hours are 21:00–08:00 IST, and templates
  are editable per gym in English and Hindi.
- **Phones** are stored E.164 (`+91` + 10 digits starting 6–9). Unique per gym for
  members, globally unique for user accounts.
- **DPDP**: member data export (JSON) and anonymising erase that keeps invoices
  and payments. Audit log of owner and super-admin actions with masked phones.

---

## Tests

```powershell
# backend: 59 tests (35 unit + 24 integration against a throwaway MongoDB)
cd backend; mvn test

# frontend: 7 tests + type check
cd frontend; npm test; npx tsc -b
```

See `TEST_REPORT.md` for the full list and what is **not** covered.

---

## Known limitations

- MongoDB is used in **standalone** mode, so there are no multi-document
  transactions. Consistency is achieved with single-document atomic updates,
  unique indexes, idempotency keys and compensating steps.
- The WhatsApp Cloud and Razorpay adapters are behind flags and are **not
  production-tested** (no live provider call is made).
- No email/SMS provider is wired up: password reset tokens are returned directly
  by the API as a stand-in for the delivery channel.
- Browser-based UI tests were not run (no Chrome/Edge on this machine) — the
  frontend is verified by build, type check and unit tests.
