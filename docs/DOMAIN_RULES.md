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
- **Configurable Per Gym & Per Item Type (`GstConfig.java`, `Gym.Settings.gstRules`):** GST rate, SAC code, effective date, and ITC status (`WITH_ITC` / `NO_ITC`) are configurable per item type (`MEMBERSHIP`, `PT`, `ADMISSION_FEE`) globally in `application.yml` (`app.gst.items.*`) and overridable per gym in `Gym.Settings.gstRules`.
- **Default Rates & SAC (Confirm with your CA):** All three item types (`MEMBERSHIP`, `PT`, `ADMISSION_FEE`) default to SAC `999723` (`18% With ITC` prior to `2025-09-22`, `5% No ITC` effective `2025-09-22` per 56th GST Council Notification — *confirm classification and ITC eligibility with your Chartered Accountant*).
- Every invoice line and rendered PDF prints the resolved **SAC code**, **GST rate (`@5%` / `@18%`)**, and **ITC status (`[No ITC]` / `[With ITC]`)**.
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
- **PIN Lockout, Per-Phone Unbound Budget & Proxy Safety:**
  - Failed PIN attempts are tracked per `phone + clientIp` (extracted via trusted-proxy-aware `RateLimitFilter.extractClientIp`) with exponential backoff after 5 failures (**60s → 300s → 900s → 3600s**).
  - To prevent an attacker who rotates IPs from brute-forcing all 10,000 PINs against one phone, `CheckinService` enforces a **per-phone unbound failure budget of 20 failures per 24 hours across all IPs** (`MAX_UNBOUND_PHONE_FAILURES_24H = 20`). When exhausted, unbound wrong-PIN guesses return `429 PIN_PHONE_BUDGET_EXHAUSTED`, the gym owner is alerted (`OutboxMessage` `TYPE_ALERT`), and any new-device binding with the valid PIN creates a pending device request (`PENDING_OWNER_APPROVAL`) requiring owner approval (`POST /api/v1/members/{id}/pending-devices/{requestId}/approve`).
  - **Bound Devices Unaffected:** Members checking in with an already-bound device (`gk_device_token`) are completely unaffected during an active IP-rotation attack. The owner can also reset lockouts and budgets immediately via `POST /api/v1/members/{id}/reset-pin-lockout`.

## 5. Personal Training & Trainer Commissions (`CommissionCalculator.java`, `CommissionService.java`)
- **Rule Resolution Order:** `PtPackage.commissionRuleOverride` → `Trainer.commissionRule` → `Gym.Settings.defaultCommissionRule`.
- **Supported Rule Types:** `PERCENT_OF_TAXABLE` (basis points on pre-GST taxable value), `FIXED_PER_PACKAGE`, `FIXED_PER_SESSION`, and progressive marginal `SLAB` tiers.
- **Proportional Payment Accrual:** Under `ON_PAYMENT` (default), commissions accrue strictly in proportion to confirmed payments collected (`computeProportionalPaymentAccrual`), ensuring the sum of partial-payment accruals equals the exact total package commission with zero rounding drift (`CommissionCalculatorTest.java`).
- **Rule Immutability & Credit Note Reversal:** Each `CommissionEntry` snapshots its `CommissionRule` at accrual time. Issuing a Credit Note posts a negative `REVERSAL` entry for that invoice.

## 6. DPDP Act 2023 Compliance (`MemberService.java`, `MongoConfig.java`)
- Explicit WhatsApp opt-in (`whatsappOptIn` + `whatsappOptInAt`) and quiet hours (`21:00–08:00` IST).
- Right to Access (`GET /api/v1/members/{id}/export`) and Right to Erasure (`POST /api/v1/members/{id}/erase`), using a partial unique index on `(gymId, phone)` so anonymising multiple members (`phone: null`) never triggers duplicate key errors while retaining tax invoices.
