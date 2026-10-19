# Domain rules — how the India-specific rules are implemented

This is the short version of *why* the code looks the way it does. All the
pure logic lives in `backend/src/main/java/com/gymkhata/domain/` and is
unit-tested there.

---

## Money

- Every amount is an **integer number of paise** (`long`). No `float`, no
  `double`, no `BigDecimal` in the model layer.
- `Money.formatIndian(paise)` renders `₹1,23,456.00` (Indian grouping: the
  last three digits, then groups of two).
- Plan prices are entered **all-inclusive** (GST included). The owner types
  1180, the system stores 118000 paise.

## GST

Prices are all-inclusive, so the tax is **back-calculated**:

```
taxable = round(total × 100 ÷ (100 + rate))
tax     = total − taxable
CGST    = floor(tax ÷ 2)
SGST    = tax − CGST
```

IGST replaces CGST+SGST when the place of supply (member's state) differs from
the supplier's state (gym's state). `GstCalculator.split()` guarantees
`taxable + CGST + SGST + IGST == total` for every input — the test suite
asserts this over a large generated range.

Rates are **effective-dated** (`app.gst.rates`) and the rate in force on the
invoice date is **snapshotted onto the line**. An old invoice is never
recalculated when the rates change.

Defaults: 18% for supplies before 22 Sep 2025, 5% (no input tax credit) from
22 Sep 2025 on. SAC 999723. A gym can switch `gstRegistered` on or off in
Settings; when off, the invoice is a Bill of Supply with no tax lines.

## Time

- `Asia/Kolkata` everywhere for business logic. The injected `Clock` bean
  provides `today()` (IST date) and `now()` (UTC instant).
- Business dates (membership start/expiry, invoice date, check-in date) are
  `LocalDate`. Instants (createdAt, checkInAt) are stored in UTC.
- The financial year runs **1 April → 31 March**. The label is `YY-YY`
  (`26-27` for 1 Apr 2026 → 31 Mar 2027).

## Invoice numbering

- Format `PFX/26-27/000123`: prefix up to 3 characters (`[A-Z0-9]`), the FY
  label, and a 6-digit sequence. The whole number is at most 16 characters.
- The sequence comes from a **gapless atomic counter** per gym + FY + series:

  ```java
  template.findAndModify(
      Query.query(Criteria.where("_id").is(gymId + "|" + fy + "|" + series)),
      new Update().inc("seq", 1),
      FindAndModifyOptions().upsert(true).returnNew(true),
      Counter.class);
  ```

- If the invoice insert fails **after** the number was taken, a `VOID` invoice
  is written with that same number. The series stays gapless and auditable.
- Corrections are **credit notes** in their own `CN` series (`CN/26-27/000001`).
  Issued invoices are never edited.

## Membership dates

A plan of N days bought on `startDate` is valid for N calendar days
**inclusive**: `expiry = startDate + N − 1`. A 30-day plan bought on 19 Oct
2026 is valid through 17 Nov 2026.

Enrolling again supersedes the previous active membership (it is marked
`EXPIRED`) and the new one starts the day after the old one expires, so a
member never has two ambiguous "current" memberships.

## UPI

- The gym collects on **its own VPA** (set in Settings).
- The deep link is the standard one:

  ```
  upi://pay?pa=<vpa>&pn=<payee name>&am=<rupees>&cu=INR&tn=<invoice number>
  ```

  The invoice number goes in the note so the payment can be traced back.
- The QR image is rendered server-side with ZXing (`GET /api/v1/pay/{token}/qr`).
- A **member** submits a 12-digit UTR on the public pay page; the payment stays
  `PENDING_VERIFICATION` until the owner confirms it. An **owner** recording a
  payment at the counter (cash/card) confirms it immediately.
- A duplicate UTR within a gym is rejected (unique index on `gymId + utr`).
- Partial payments are allowed; the balance is
  `invoice total − sum(CONFIRMED payments)`. Overpayment is rejected.

## WhatsApp

- Default provider is **ClickToChat**: the outbox shows the day's due
  reminders, each with a one-tap `https://wa.me/91XXXXXXXXXX?text=…` link.
  The owner taps, sends, and marks it sent.
- `WhatsAppCloudProvider` implements the same interface behind
  `app.messaging.provider=whatsappcloud` (NOT PRODUCTION-TESTED).
- Every member has `whatsappOptIn`, captured at enrolment, with a consent
  timestamp. Opted-out members get **no** automated messages.
- **Quiet hours** are 21:00–08:00 IST. Messages generated in that window are
  marked `QUIET_HOURS` instead of `PENDING`.
- Templates are editable per gym in English and Hindi. Available variables:

  `{name} {planName} {expiryDate} {amountDue} {payLink} {gymName}`

## Phone numbers

- Stored E.164: `+91` followed by 10 digits starting 6–9.
- Accepted input: `9876543210`, `09876543210`, `+919876543210`,
  `919876543210` and `+91 98765 43210`.
- **Unique per gym** for members, **globally unique** for user accounts.
- Masked in logs and audit entries: `+91*****3210`.

## Check-in decisions

`CheckinRules.evaluate()` is a pure function returning one of:

| Decision | Meaning |
|---|---|
| `ALLOWED` | member active, membership active, not expired, not checked in today |
| `MEMBER_INACTIVE` | the member record is inactive |
| `NO_ACTIVE_MEMBERSHIP` | no active membership row |
| `MEMBERSHIP_EXPIRED` | the active membership expired before today |
| `ALREADY_CHECKED_IN_TODAY` | an open check-in already exists for today |

## Data protection (DPDP)

- Only fields the gym actually needs are collected.
- `whatsappOptIn` + `whatsappOptInAt` record consent.
- **Export**: `GET /api/v1/members/{id}/export` returns the member and their
  memberships as JSON.
- **Erase**: `POST /api/v1/members/{id}/erase` anonymises the member (name
  becomes "Erased Member", phone/email/address/DOB/notes are cleared, opt-in
  is revoked). **Invoices and payments are kept** because tax records must be
  retained.
- The audit log records owner and super-admin actions with masked phone
  numbers.
