# Data model

MongoDB collections. Every tenant-scoped document carries `gymId`.
The `gymId` is always taken from the authenticated principal — never from the
request.

| Collection | Key fields | Indexes |
|---|---|---|
| `users` | `role` (`SUPER_ADMIN` \| `GYM_OWNER`), `gymId` (null for super admin), `name`, `phone`, `email`, `passwordHash`, `status`, `lastLoginAt` | `phone` unique; `email` unique sparse; **`gymId` unique where `role=GYM_OWNER`** (partial index → at most one owner per gym) |
| `gyms` | `name`, `code` (unique), `ownerName`, `phone`, `email`, `address`, `city`, `state`, `gstin`, `gstRegistered`, `invoicePrefix`, `status` (`PENDING`/`ACTIVE`/`SUSPENDED`), `subscriptionValidUntil`, `planPricePaise`, `settings{upiVpa, upiName, quietHoursEnabled, templatesEn, templatesHi}` | `code` unique |
| `members` | `gymId`, `name`, `phone` (E.164), `email`, `gender`, `dob`, `address`, `state`, `status`, `whatsappOptIn`, `whatsappOptInAt`, `joinedAt`, `notes` | `gymId + phone` unique |
| `plans` | `gymId`, `name`, `durationDays`, `pricePaise` (all-inclusive), `active` | `gymId` |
| `memberships` | `gymId`, `memberId`, `planId`, `planName` (snapshot), `startDate`, `expiryDate`, `pricePaise` (snapshot), `status` | `gymId + memberId` |
| `invoices` | `gymId`, `invoiceNumber`, `financialYear`, `type` (`TAX_INVOICE`/`BILL_OF_SUPPLY`/`RECEIPT`/`CREDIT_NOTE`/`VOID`), `memberId`, `membershipId`, `lines[]`, `totalPaise`, `amountInWords`, `status` (`ISSUED`/`VOID`), `voidReason`, `placeOfSupplyState`, `supplierState`, `gstRegistered` (snapshot), `gstin` (snapshot), `payToken` | `gymId + invoiceNumber` unique |
| `payments` | `gymId`, `invoiceId`, `memberId`, `amountPaise`, `method` (`UPI`/`CASH`/`CARD`), `status` (`PENDING_VERIFICATION`/`CONFIRMED`/`FAILED`/`REFUNDED`), `utr`, `recordedBy` (`OWNER`/`MEMBER`), `confirmedAt` | `gymId + utr` unique (partial, `utr` exists) |
| `checkins` | `gymId`, `memberId`, `memberName` (snapshot), `checkInAt`, `checkOutAt`, `date` (IST) | `gymId + date` |
| `leads` | `gymId`, `name`, `phone`, `source`, `status` (`NEW`/`CONTACTED`/`CONVERTED`/`LOST`), `notes`, `convertedToMemberId` | `gymId + phone` |
| `outbox_messages` | `gymId`, `memberId`, `memberName`, `phone`, `type`, `channel`, `templateKey`, `language`, `variables`, `waLink`, `status` (`PENDING`/`QUIET_HOURS`/`SENT`/`FAILED`), `sentAt`, `date` | `gymId + status` |
| `counters` | `_id` = `<gymId>\|<fy>\|<INV\|CN>`, `seq` | `_id` (default) |
| `audit_logs` | `gymId`, `actorId`, `actorRole`, `action`, `entity`, `entityId`, `detail` (phones masked), `createdAt` | — |
| `refresh_tokens` | `userId`, `tokenHash`, `familyId`, `expiresAt`, `revokedAt`, `replacedBy` | — |
| `password_reset_tokens` | `userId`, `tokenHash`, `expiresAt`, `usedAt` | — |

## Invoice line

```jsonc
{
  "description": "Membership - Monthly",
  "sac": "999723",
  "quantity": 1,
  "ratePaise": 118000,      // all-inclusive
  "taxablePaise": 112381,   // GST is back-calculated from the all-in price
  "gstRate": 5,
  "cgstPaise": 2809,
  "sgstPaise": 2810,
  "igstPaise": 0,
  "totalPaise": 118000      // taxable + CGST + SGST + IGST
}
```

## Tenancy

- `SecurityUtils.currentGymId()` reads the `gymId` claim from the authenticated
  JWT. Every repository query in the tenant features takes that value.
- `TenantStatusInterceptor` blocks suspended gyms from tenant APIs with
  `403 ACCOUNT_SUSPENDED` (gym status is cached for 30 s).
- `GET /api/v1/audit/logs` returns only the caller's own gym's entries, unless
  the caller is the super admin.
