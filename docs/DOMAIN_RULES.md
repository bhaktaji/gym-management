# Domain Rules — India-Specific Financial, Regulatory & Gym Operations Logic

All pure domain logic lives in `backend/src/main/java/com/gymkhata/domain/` and is unit-tested with zero framework mocks.

---

## 1. Money (`Money.java`)
- Every monetary amount is stored and computed as an **integer number of paise** (`long`). No `float` or `double` is used anywhere in billing, commissions, or imports (`ImporterService` parses CSV amounts with `BigDecimal` and `movePointRight(2)`).
- `Money.formatIndian(paise)` renders `₹1,23,456.00` using Indian Lakh/Crore digit grouping.
- Plan and PT package prices are entered **all-inclusive** (GST included).

## 2. GST (`GstCalculator.java`)
Prices are all-inclusive, so tax is back-calculated using integer paise arithmetic with `BigDecimal` `HALF_UP` rounding:
```
taxable = round(total × 100 ÷ (100 + rate))
tax     = total − taxable
CGST    = floor(tax ÷ 2)
SGST    = tax − CGST
```
- Intra-state supplies split tax into `CGST + SGST`; inter-state supplies (where member state differs from gym state) use `IGST`.
- `taxable + CGST + SGST + IGST == total` holds for every input without a single paisa of rounding drift (`GstCalculatorTest.java`).
- Default membership SAC is `999723` (`18%` prior to `2025-09-22`, `5%` from `2025-09-22` onwards). Default Personal Training SAC is `999799` (`5%`).
- When `gstRegistered = false`, GymKhata issues a `BILL_OF_SUPPLY` with zero tax lines.

## 3. Gapless Invoice & Credit Note Numbering (`InvoiceNumbering.java`, `BillingService.java`)
- Format: `PFX/26-27/000001` (prefix + Indian Financial Year `1 April – 31 March` + 6-digit sequence).
- Sequence numbers are allocated atomically via `mongoTemplate.findAndModify` (`$inc` + `upsert`).
- If invoice persistence fails after sequence allocation, a compensating `VOID` invoice is written with that number so the fiscal sequence remains strictly gapless.
- Refunds and cancellations issue a Credit Note in the `CN` series (`CN/26-27/000001`).

## 4. Reception-Free Attendance & Self Check-In (`CheckinService.java`, `GeofenceAndQrUtils.java`)
- **Pure Date & Payment Truth:** Membership status (`ACTIVE` vs `EXPIRED`) depends solely on dates and payments, never on check-ins.
- **Disabled by Default:** `attendanceEnabled` defaults to `false` on new gyms (`Gym.java:108`). When `false`, all attendance APIs return `403 FEATURE_DISABLED` and zero check-in/attendance UI elements render anywhere in the owner app.
- **Self Check-In & Device Binding:** Members receive a random 4-digit PIN at enrolment (`MemberService.java:152`). On first scan at `/g/{gymSlug}/in`, entering `phone + PIN` binds an `HttpOnly` cookie (`gk_device_token`, max 2 devices per member) so future check-ins take 1 tap.
- **PIN Lockout & Proxy Safety:** Failed PIN attempts are tracked per `phone + clientIp` (extracted via trusted-proxy-aware `RateLimitFilter.extractClientIp`) with exponential backoff after 5 failures (**60s → 300s → 900s → 3600s**). One attacker cannot lock out other members on shared gym Wi-Fi or lock out the real member on their own network, and the owner can unlock immediately via `POST /api/v1/members/{id}/reset-pin-lockout`.

## 5. Personal Training & Trainer Commissions (`CommissionCalculator.java`, `CommissionService.java`)
- **Rule Resolution Order:** `PtPackage.commissionRuleOverride` → `Trainer.commissionRule` → `Gym.Settings.defaultCommissionRule`.
- **Supported Rule Types:** `PERCENT_OF_TAXABLE` (basis points on pre-GST taxable value), `FIXED_PER_PACKAGE`, `FIXED_PER_SESSION`, and progressive marginal `SLAB` tiers.
- **Proportional Payment Accrual:** Under `ON_PAYMENT` (default), commissions accrue strictly in proportion to confirmed payments collected (`computeProportionalPaymentAccrual`), ensuring the sum of partial-payment accruals equals the exact total package commission with zero rounding drift (`CommissionCalculatorTest.java`).
- **Rule Immutability & Credit Note Reversal:** Each `CommissionEntry` snapshots its `CommissionRule` at accrual time. Issuing a Credit Note posts a negative `REVERSAL` entry for that invoice.

## 6. DPDP Act 2023 Compliance (`MemberService.java`, `MongoConfig.java`)
- Explicit WhatsApp opt-in (`whatsappOptIn` + `whatsappOptInAt`) and quiet hours (`21:00–08:00` IST).
- Right to Access (`GET /api/v1/members/{id}/export`) and Right to Erasure (`POST /api/v1/members/{id}/erase`), using a partial unique index on `(gymId, phone)` so anonymising multiple members (`phone: null`) never triggers duplicate key errors while retaining tax invoices.
