# API reference

Base URL: `/api/v1`. Interactive docs (OpenAPI/Swagger) are served at
`/swagger-ui.html` and the raw spec at `/api-docs`.

All responses are JSON. Errors use a consistent envelope:

```json
{
  "status": 400,
  "code": "OVERPAYMENT",
  "message": "Amount exceeds balance due of 68000",
  "timestamp": "2026-10-19T14:41:09.061Z"
}
```

Common codes: `UNAUTHORIZED` (401), `FORBIDDEN` (403), `ACCOUNT_SUSPENDED`
(403), `NOT_FOUND` (404), `PHONE_TAKEN` (409), `DUPLICATE_UTR` (409),
`ALREADY_CHECKED_IN` (409), `OVERPAYMENT` (400), `INVALID_UTR` (400),
`VALIDATION_FAILED` (400).

---

## Public — no authentication

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/config/public` | App name + pricing (drives the public pricing page) |
| `POST` | `/auth/login` | `{phone, password}` → access + refresh token + user |
| `POST` | `/auth/refresh` | `{refreshToken}` → new pair (old refresh token is revoked) |
| `POST` | `/auth/signup` | Creates a `PENDING` gym + its single owner |
| `POST` | `/auth/password-reset/request` | Issues a reset token (returned as a stand-in for delivery) |
| `POST` | `/auth/password-reset/confirm` | `{token, newPassword}` |
| `GET` | `/pay/{token}` | Pay page: invoice, amount, gym, UPI VPA and deep link |
| `GET` | `/pay/{token}/qr` | PNG QR code of the UPI deep link |
| `POST` | `/pay/{token}/utr` | `{amountPaise, utr}` → `PENDING_VERIFICATION` payment |

## Super admin — `/api/v1/admin/**`

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/gyms` | All gyms with owner, masked phone, city, GST flag, active member count, status, subscription date, last login |
| `POST` | `/admin/gyms` | Creates a gym **and** its single `GYM_OWNER` in one workflow |
| `PUT` | `/admin/gyms/{gymId}/owner` | Update the owner's phone/email |
| `POST` | `/admin/gyms/{gymId}/password-reset` | Issue a reset token for the owner |
| `POST` | `/admin/gyms/{gymId}/status` | `{status: ACTIVE\|SUSPENDED}` |
| `PUT` | `/admin/gyms/{gymId}/subscription` | `{validUntil: yyyy-MM-dd}` |

## Gym owner

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/tenant/gym` | Gym profile + settings |
| `PUT` | `/tenant/profile` | Name, GSTIN, `gstRegistered`, invoice prefix |
| `PUT` | `/tenant/settings` | UPI VPA/name, quiet hours, WhatsApp templates (en/hi) |
| `GET` | `/tenant/plan-usage` | Price, subscription status, usage counters |
| `GET` | `/plans` | Active plans |
| `POST` | `/plans` | `{name, durationDays, pricePaise}` |
| `PUT` | `/plans/{id}` | Update a plan (incl. `active`) |
| `GET` | `/members` | Members (optional `?status=ACTIVE`) |
| `POST` | `/members` | Enrol a member (`whatsappOptIn` captured here) |
| `GET` | `/members/{id}` | One member |
| `PUT` | `/members/{id}` | Update a member |
| `POST` | `/members/{id}/status` | `{status: ACTIVE\|INACTIVE}` |
| `GET` | `/members/{id}/export` | **DPDP** export as JSON |
| `POST` | `/members/{id}/erase` | **DPDP** anonymise (invoices/payments retained) |
| `POST` | `/memberships` | Enrol a member into a plan |
| `GET` | `/memberships/member/{memberId}` | A member's memberships |
| `GET` | `/memberships/active` | All active memberships |
| `POST` | `/billing/invoices` | Generate the invoice for a membership (gapless number) |
| `GET` | `/billing/invoices` | Invoices, newest first |
| `GET` | `/billing/invoices/member/{memberId}` | A member's invoices |
| `GET` | `/billing/invoices/{id}` | One invoice |
| `GET` | `/billing/invoices/{id}/payments` | Payments against the invoice |
| `GET` | `/billing/invoices/{id}/balance` | Balance due |
| `GET` | `/billing/invoices/{id}/pay-link` | UPI deep link for the counter |
| `GET` | `/billing/invoices/{id}/pdf` | Invoice PDF (OpenPDF) |
| `POST` | `/billing/invoices/{id}/void` | Void (keeps the number) |
| `POST` | `/billing/invoices/{id}/credit-note` | Credit note in the `CN` series |
| `POST` | `/payments/counter` | `{invoiceId, amountPaise, method: CASH\|CARD}` → confirmed |
| `GET` | `/payments/pending` | UPI payments awaiting confirmation |
| `POST` | `/payments/{id}/confirm` | Confirm a member-submitted UPI payment |
| `POST` | `/checkins` | `{memberId}` → check in |
| `POST` | `/checkins/{id}/checkout` | Check out |
| `GET` | `/checkins/today` | Today's check-ins |
| `GET` | `/checkins/member/{memberId}` | Check-in history |
| `POST` | `/messaging/outbox/generate` | Generate today's renewal reminders |
| `GET` | `/messaging/outbox` | Today's outbox with `wa.me` links |
| `POST` | `/messaging/outbox/{id}/sent` | Mark as sent |
| `GET` | `/leads` | Leads (optional `?status=`) |
| `POST` | `/leads` | Add a lead |
| `PUT` | `/leads/{id}` | Update status/notes |
| `POST` | `/leads/{id}/convert` | Convert a lead into a member |
| `GET` | `/reports/dashboard` | Summary counters |
| `GET` | `/reports/revenue` | Confirmed revenue by month (12 months) |
| `GET` | `/reports/expiring?days=30` | Memberships expiring soon |
| `GET` | `/reports/checkins?days=30` | Check-ins per day |
| `GET` | `/reports/plans` | Active members per plan |
| `POST` | `/import/members` | `{csv}` → `{totalRows, success, errors[]}` |
| `POST` | `/import/payments` | `{csv}` → same shape |
| `GET` | `/audit/logs?limit=100` | Audit log (own gym only, unless super admin) |

## Authentication

Send the access token as a bearer token:

```
Authorization: Bearer <accessToken>
```

Access tokens last 15 minutes; refresh tokens 30 days and **rotate** on every
use. Reusing a rotated refresh token revokes the whole token family and the
user must log in again. A suspended gym is rejected with
`403 ACCOUNT_SUSPENDED` on login, on refresh and on every tenant API call.
