# Data Model

MongoDB collections managed programmatically in `MongoConfig.java`. Every tenant-scoped document carries `gymId`, which is always extracted from the authenticated JWT principal (`SecurityUtils.currentGymId()`).

| Collection | Key fields | Indexes (`MongoConfig.java`) |
|---|---|---|
| `users` | `role` (`SUPER_ADMIN` \| `GYM_OWNER`), `gymId` (null for super admin), `name`, `phone`, `email`, `passwordHash`, `status` (`ACTIVE`/`DISABLED`), `lastLoginAt` | `phone` unique; `email` unique sparse; **`gymId` unique where `role=GYM_OWNER`** (partial index → at most one owner per gym) |
| `gyms` | `name`, `code` (unique), `slug` (unique), `ownerName`, `phone`, `email`, `address`, `city`, `state`, `gstin`, `gstRegistered`, `invoicePrefix`, `status` (`PENDING`/`ACTIVE`/`SUSPENDED`), `subscriptionValidUntil`, `planPricePaise`, `settings{upiVpa, upiName, quietHoursEnabled, attendanceEnabled (default false), strictQrMode, selfCheckoutEnabled, autoCloseDurationMinutes (120), closingTimeIst (22:00), notSeenDaysThreshold (14), commissionTrigger, defaultCommissionRule}` | `code` unique; `slug` unique sparse |
| `members` | `gymId`, `name`, `phone` (E.164, nullable after DPDP erase), `email`, `gender`, `dob`, `address`, `state`, `status`, `whatsappOptIn`, `whatsappOptInAt`, `joinedAt`, `notes`, `pinHash`, `pinFailedAttempts`, `pinLockedUntil`, `boundDevices[]` | `gymId + phone` unique **partial index (`phone` type is string)** so multiple DPDP-erased members (`phone: null`) do not collide |
| `plans` | `gymId`, `name`, `durationDays`, `pricePaise` (all-inclusive), `joiningFeePaise`, `lateRenewalPolicy`, `active` | `gymId` |
| `memberships` | `gymId`, `memberId`, `planId`, `planName` (snapshot), `startDate`, `expiryDate`, `pricePaise` (snapshot), `frozenDays`, `status` | `gymId + memberId` |
| `invoices` | `gymId`, `invoiceNumber`, `financialYear`, `type` (`TAX_INVOICE`/`BILL_OF_SUPPLY`/`RECEIPT`/`CREDIT_NOTE`/`VOID`), `memberId`, `membershipId`, `ptEnrolmentId`, `lines[]`, `totalPaise`, `amountInWords`, `status` (`ISSUED`/`VOID`), `voidReason`, `placeOfSupplyState`, `supplierState`, `gstRegistered` (snapshot), `gstin` (snapshot), `payToken` | `gymId + invoiceNumber` unique; `payToken` unique sparse |
| `payments` | `gymId`, `invoiceId`, `memberId`, `amountPaise`, `method` (`UPI`/`CASH`/`CARD`), `status` (`PENDING`/`CONFIRMED`/`FAILED`/`REFUNDED`), `utr`, `failureReason`, `recordedBy` (`OWNER`/`MEMBER`/`IMPORT`), `confirmedAt` | `gymId + utr` unique (partial, `utr` is string) |
| `checkins` | `gymId`, `memberId`, `memberName` (snapshot), `checkInAt`, `checkOutAt`, `autoClosed`, `date` (IST `LocalDate`), `method` (`QR_SELF`/`MANUAL`), `withinGeofence`, `membershipBadge` | `gymId + date` |
| `trainers` | `gymId`, `name`, `phone`, `active`, `commissionRule` (`type`, `value`, `slabs[]`) | `gymId` |
| `pt_packages` | `gymId`, `name`, `type` (`SESSION_BASED`/`TIME_BASED`), `durationDays`, `totalSessions`, `pricePaise`, `sacCode`, `commissionRuleOverride`, `active` | `gymId` |
| `pt_enrolments` | `gymId`, `memberId`, `memberName`, `trainerId`, `trainerName`, `ptPackageId`, `packageName`, `invoiceId`, `totalSessions`, `deliveredSessions`, `startDate`, `expiryDate`, `status` | `gymId + memberId`; `gymId + trainerId` |
| `commission_entries` | `gymId`, `trainerId`, `trainerName`, `ptEnrolmentId`, `invoiceId`, `paymentId`, `creditNoteId`, `memberId`, `memberName`, `packageName`, `type` (`ACCRUAL`/`REVERSAL`), `amountPaise`, `basisTaxablePaise`, `ruleSnapshot`, `month` (`YYYY-MM`), `status` (`ACCRUED`/`PAID`/`REVERSED`), `paidAt` | `gymId + trainerId + month` |
| `leads` | `gymId`, `name`, `phone`, `source`, `status` (`NEW`/`CONTACTED`/`CONVERTED`/`LOST`), `notes`, `convertedToMemberId` | `gymId + phone` |
| `outbox_messages` | `gymId`, `memberId`, `memberName`, `phone`, `type`, `channel`, `templateKey`, `language`, `variables`, `waLink`, `status` (`PENDING`/`QUIET_HOURS`/`SENT`/`FAILED`), `sentAt`, `date` | `gymId + status` |
| `counters` | `_id` = `<gymId>\|<fy>\|<INV\|CN>`, `seq` | `_id` (default) |
| `audit_logs` | `gymId`, `actorId`, `actorRole`, `action`, `entity`, `entityId`, `detail` (phones masked), `createdAt` | `gymId + createdAt` (descending) |
| `refresh_tokens` | `userId`, `tokenHash`, `familyId`, `expiresAt`, `revokedAt`, `replacedBy` | `tokenHash` unique; `familyId`; `expiresAt` (TTL index) |
| `password_reset_tokens` | `userId`, `tokenHash`, `expiresAt`, `usedAt` | `tokenHash` unique; `expiresAt` (TTL index) |
