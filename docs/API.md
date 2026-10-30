# API Reference

Base URL: `/api/v1`. Interactive docs (OpenAPI/Swagger) are served at `/swagger-ui.html` and the raw spec at `/api-docs` in `dev`/`demo` (disabled by default in `prod` via `SWAGGER_ENABLED=false` and restricted to `SUPER_ADMIN`).

All responses are JSON (except PDF/PNG/CSV downloads). Errors use a consistent envelope:

```json
{
  "status": 400,
  "code": "OVERPAYMENT",
  "message": "Amount exceeds balance due of 68000",
  "timestamp": "2026-10-30T10:41:09.061Z"
}
```

Common codes: `UNAUTHORIZED` (401), `FORBIDDEN` (403), `ACCOUNT_SUSPENDED` (403), `FEATURE_DISABLED` (403), `NOT_FOUND` (404), `PHONE_TAKEN` (409), `DUPLICATE_UTR` (409), `ALREADY_CHECKED_IN` (409), `OVERPAYMENT` (400), `INVALID_UTR` (400), `PIN_LOCKED` (429), `RATE_LIMITED` (429).

---

## Public — No Authentication

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/config/public` | App name + pricing (drives the public pricing page) |
| `POST` | `/auth/login` | `{phone, password}` → access + refresh token + user |
| `POST` | `/auth/refresh` | `{refreshToken}` → new pair (rotates token while preserving `familyId`; reuse of rotated token revokes entire family) |
| `POST` | `/auth/logout` | `{refreshToken}` → revokes refresh token family |
| `POST` | `/auth/signup` | Creates a `PENDING` gym + its single owner |
| `POST` | `/auth/password-reset/request` | `{phone}` → `{"status":"reset_requested"}` (never leaks reset token in public response) |
| `POST` | `/auth/password-reset/confirm` | `{token, newPassword}` → resets password and revokes active sessions |
| `GET` | `/pay/{token}` | Public pay page: invoice, `totalPaise`, `paidPaise`, `amountPaise` (remaining balance due), status (`ISSUED`/`PARTIAL`/`PAID`/`VOID`), gym, UPI VPA and deep link |
| `GET` | `/pay/{token}/qr` | PNG QR code of the UPI deep link |
| `POST` | `/pay/{token}/utr` | `{amountPaise, utr}` → `PENDING` UPI payment for owner verification |
| `GET` | `/public/attendance/{gymSlug}/info` | Self check-in page metadata + bound-device status (`403 FEATURE_DISABLED` if `attendanceEnabled=false`) |
| `GET` | `/public/attendance/{gymSlug}/screen` | Rotating 60-second HMAC QR token for `/g/{gymSlug}/screen` |
| `POST` | `/public/attendance/{gymSlug}/checkin` | Self check-in via `gk_device_token` cookie or first-time `{phone, pin}`; returns `GREEN`/`AMBER`/`RED` badge + renewal `payLink` |
| `POST` | `/public/attendance/{gymSlug}/checkout` | Optional self check-out (`"I'm leaving"`) when `selfCheckoutEnabled=true` |
| `POST` | `/public/attendance/{gymSlug}/pt-session-done` | Member self-service tap to decrement 1 PT session |

## Super Admin — `/api/v1/admin/**`

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/gyms` | All gyms with owner, masked phone, city, GST flag, active member count, status, subscription date, last login |
| `POST` | `/admin/gyms` | Creates a gym **and** its single `GYM_OWNER` in one workflow |
| `PUT` | `/admin/gyms/{gymId}/owner` | Update the owner's phone/email |
| `POST` | `/admin/gyms/{gymId}/password-reset` | Issues a single-use password reset token for the gym owner (displayed once to `SUPER_ADMIN`) |
| `POST` | `/admin/gyms/{gymId}/status` | `{status: ACTIVE\|SUSPENDED}` |
| `PUT` | `/admin/gyms/{gymId}/subscription` | `{validUntil: yyyy-MM-dd}` |

## Gym Owner — Authenticated (`ROLE_GYM_OWNER`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/password-reset` | `{currentPassword, newPassword}` → authenticated owner password change |
| `GET` | `/tenant/gym` | Gym profile + settings |
| `PUT` | `/tenant/profile` | Name, `phone`, `email`, `address`, `city`, `state`, GSTIN, `gstRegistered`, invoice prefix |
| `PUT` | `/tenant/settings` | UPI VPA/name, quiet hours, WhatsApp templates (en/hi), `attendanceEnabled`, `strictQrMode`, `selfCheckoutEnabled`, geofence, PT commission defaults |
| `GET` | `/tenant/plan-usage` | Price, subscription status, usage counters |
| `GET` | `/plans` | Active plans |
| `POST` | `/plans` | `{name, durationDays, pricePaise, joiningFeePaise, lateRenewalPolicy}` |
| `PUT` | `/plans/{id}` | Update a plan (incl. `active`) |
| `GET` | `/members` | Members (optional `?status=ACTIVE`) |
| `POST` | `/members` | Enrol a member (`whatsappOptIn`, generates 4-digit PIN + welcome WhatsApp text) |
| `GET` | `/members/{id}` | One member |
| `PUT` | `/members/{id}` | Update a member |
| `POST` | `/members/{id}/status` | `{status: ACTIVE\|INACTIVE}` |
| `POST` | `/members/{id}/reset-pin` | Generates a new 4-digit PIN and clears any PIN lockout |
| `POST` | `/members/{id}/reset-pin-lockout` | Instantly clears `phone + IP` and member PIN lockouts without changing the PIN |
| `POST` | `/members/{id}/revoke-device` | `{deviceId}` (or empty for all) → revokes bound check-in devices |
| `GET` | `/members/{id}/export` | **DPDP** export as JSON |
| `POST` | `/members/{id}/erase` | **DPDP** anonymise (invoices/payments retained; multiple erased members supported) |
| `POST` | `/memberships` | Enrol/renew a member into a plan |
| `POST` | `/memberships/{id}/freeze` | `{days}` → freeze/extend an active membership |
| `GET` | `/memberships/member/{memberId}` | A member's memberships |
| `GET` | `/memberships/active` | All active memberships |
| `POST` | `/billing/invoices` | Generate the invoice for a membership (gapless number) |
| `GET` | `/billing/invoices` | Invoices, newest first |
| `GET` | `/billing/invoices/member/{memberId}` | A member's invoices |
| `GET` | `/billing/invoices/{id}` | One invoice |
| `GET` | `/billing/invoices/{id}/payments` | Payments against the invoice |
| `GET` | `/billing/invoices/{id}/balance` | Balance due |
| `GET` | `/billing/invoices/{id}/pay-link` | UPI deep link for the counter |
| `GET` | `/billing/invoices/{id}/pdf` | Invoice PDF (OpenPDF with embedded Unicode Devanagari font) |
| `POST` | `/billing/invoices/{id}/void` | Void (keeps the number) |
| `POST` | `/billing/invoices/{id}/credit-note` | Credit note in the `CN` series (triggers proportional PT commission reversal if applicable) |
| `POST` | `/payments/counter` | `{invoiceId, amountPaise, method: CASH\|CARD}` → confirmed (triggers proportional PT commission accrual if PT invoice) |
| `GET` | `/payments/pending` | UPI payments awaiting confirmation |
| `POST` | `/payments/{id}/confirm` | Confirm a member-submitted UPI payment |
| `POST` | `/payments/{id}/reject` | `{reason}` → reject a member-submitted UPI payment (`STATUS_FAILED`) |
| `POST` | `/checkins` | `{memberId}` → manual owner fallback check-in (requires `attendanceEnabled=true`) |
| `GET` | `/checkins/today` | Today's visit log (requires `attendanceEnabled=true`) |
| `GET` | `/checkins/member/{memberId}` | Member visit history (requires `attendanceEnabled=true`) |
| `GET` | `/checkins/analytics` | Today's visits, 30-day peak hours, and "Not seen in N days" list (requires `attendanceEnabled=true`) |
| `POST` | `/checkins/nudge/{memberId}` | Queue a bilingual "We missed you" WhatsApp reminder |
| `GET` | `/checkins/poster.pdf` | Printable A4 QR self check-in poster PDF |
| `GET` / `POST` / `PUT` | `/trainers` | Manage trainers and their commission rules |
| `GET` / `POST` / `PUT` | `/pt-packages` | Manage session-based and time-based PT packages |
| `GET` / `POST` | `/pt-enrolments` | Sell a PT package to a member + generate GST invoice |
| `POST` | `/pt-enrolments/{id}/session-done` | Owner records a delivered PT session |
| `GET` | `/trainers/{id}/commissions?month=YYYY-MM` | Trainer monthly commission statement |
| `GET` | `/trainers/{id}/commissions/csv?month=YYYY-MM` | Export trainer commission statement as CSV |
| `GET` | `/trainers/{id}/commissions/receipt.pdf?month=YYYY-MM` | Download trainer commission statement PDF |
| `POST` | `/trainers/{id}/commissions/mark-paid` | Mark accrued commission entries for `month` as `PAID` |
| `POST` | `/messaging/outbox/generate` | Generate today's renewal reminders |
| `GET` | `/messaging/outbox` | Today's outbox with `wa.me` links |
| `POST` | `/messaging/outbox/{id}/sent` | Mark as sent |
| `GET` / `POST` / `PUT` | `/leads` | Manage walk-in/enquiry leads and convert via `POST /leads/{id}/convert` |
| `GET` | `/reports/dashboard` | Summary counters + dues list |
| `GET` | `/reports/revenue` | Confirmed revenue by month (12 months) |
| `GET` | `/reports/expiring?days=30` | Memberships expiring soon |
| `GET` | `/reports/checkins?days=30` | Check-ins per day (`LocalDate` range query) |
| `GET` | `/reports/plans` | Active members per plan |
| `POST` | `/import/members` | `{csv}` → `{totalRows, success, errors[]}` (enforces 1,000-row limit, header check, UTF-8 check, formula-injection guard) |
| `POST` | `/import/payments` | `{csv}` → persists `Payment` documents using exact `BigDecimal` paise conversion |
| `GET` | `/audit/logs?limit=100` | Audit log (own gym only, unless super admin) |
