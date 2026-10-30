# GymKhata — Demo Environment & Seed Dataset (`DEMO_DATA.md`)

> **WARNING — DEMO PROFILE ONLY:**  
> All credentials and data documented in this file exist **exclusively** in the `demo` profile (`gymkhata_demo` database).  
> `DemoDataSeeder` is hard-coded to throw an `IllegalStateException` (`DEMO_SEED_REFUSED`) if:
> 1. The `prod` or `production` Spring profile is active, **OR**
> 2. The `demo` Spring profile is **not** active, **OR**
> 3. The target MongoDB database name does not contain `"demo"` or `"dev"`.

---

## 1. How to Run or Reset the Demo Dataset

### Linux / macOS
```bash
# Seed idempotently (safe to run multiple times; skips if already seeded)
./scripts/seed-demo.sh

# Reset and re-seed from scratch (wipes gymkhata_demo and rebuilds)
./scripts/seed-demo.sh --reset
```

### Windows (PowerShell)
```powershell
# Seed idempotently
.\scripts\seed-demo.ps1

# Reset and re-seed from scratch
.\scripts\seed-demo.ps1 -Reset
```

---

## 2. Demo Logins (Demo-Only Passwords)

All phone numbers may be entered in any Indian format (`+91 98100 10001`, `09810010001`, `9810010001`, `+91-98100-10001`).

| Role | Gym Code & Name | Scenario Covered | Phone | Demo Password | Expected Login Outcome |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SUPER_ADMIN`** | *(Platform Level)* | Platform administration, tenant management, approvals, audit log | `+919000000001` | `DemoAdmin@2026` | Logs into `/admin` dashboard |
| **`GYM_OWNER`** | `BAJRANG-LKO` — **Bajrang Powerhouse & Fitness (बजरंग पावरहाउस)** | **(a) GST-registered (`09AABCB1234F1Z5`), intra-state (`Uttar Pradesh` -> CGST 9% + SGST 9%), UPI VPA set (`bajrangfitness@okaxis`)** | `+919810010001` | `DemoOwner@2026` | Logs into `/app` owner dashboard |
| **`GYM_OWNER`** | `IRONCORE-IND` — **IronCore Strength Club (आयरनकोर स्ट्रेंथ क्लब)** | **(b) GST-registered (`23AAACI5678G1Z2`, `Madhya Pradesh`) with inter-state customers (`Maharashtra`, `Gujarat`, `Rajasthan`, `Uttar Pradesh` -> IGST 18%)** | `+919810010002` | `DemoOwner@2026` | Logs into `/app` owner dashboard |
| **`GYM_OWNER`** | `SURYA-JPR` — **Surya Akhada & Gym (सूर्य अखाड़ा एवं जिम)** | **(c) Not GST-registered (`Bill of Supply`, 0 tax lines, `Rajasthan`), UPI VPA `suryaakhada@paytm`** | `+919810010003` | `DemoOwner@2026` | Logs into `/app` owner dashboard |
| **`GYM_OWNER`** | `TITAN-NGP` — **Titan Muscle Factory** | **(d) `SUSPENDED` gym** (tests `403 ACCOUNT_SUSPENDED` & reactivation recovery) | `+919810010004` | `DemoOwner@2026` | Blocked with `403 ACCOUNT_SUSPENDED` until reactivated by Super Admin |
| **`GYM_OWNER`** | `KAVERI-MYS` — **Kaveri Fitness Studio** | **(e) `PENDING` self-signup gym** (awaiting Super Admin approval) | `+919810010005` | `DemoOwner@2026` | Blocked until Super Admin approves in `/admin` |

---

## 3. Seeded Dataset Breakdown

### 3.1 Members (40 per active gym + 32 in suspended gym = 152 total)
- **Bilingual Names:** Realistic Indian names in English (`Aarav Sharma`, `Priya Verma`, `Harshvardhan Joshi`, etc.) and Hindi Devanagari (`रोहित शर्मा`, `पूजा वर्मा`, `अमित कुमार यादव`, `कविता मिश्रा`, etc.).
- **Mixed Phone Formats Normalized:** Seeded via `PhoneUtils.normalizeIndianPhone` from `"91100 00000"`, `"09110000001"`, `"+91-91100-00002"`, `"919110000003"`, `"9110000004"`.
- **Lifecycle & Privacy States per Active Gym:**
  - **Slots 0–3 (4 members):** Renewed members (1 prior `SUPERSEDED` membership + 1 current `ACTIVE` membership).
  - **Slots 4–17 (14 members):** Active memberships (> 7 days remaining), including zero-rupee 7-day trial members.
  - **Slots 18–22 (5 members):** Expiring within 7 days (`+1` to `+5` days from today).
  - **Slots 23–25 (3 members):** Expiring today (`endDate == today`).
  - **Slots 26–32 (7 members):** Expired (`endDate` 5 to 35 days in the past, status `EXPIRED`).
  - **Slots 33–37 (5 members):** Never enrolled (registered walk-ins with no membership).
  - **Slots 38–39 (2 members):** DPDP Act 2023 right-to-erasure anonymised (`name = "ERASED"`, `phone = null`, `status = "INACTIVE"`, `whatsappOptIn = false`, `erasedAt` set).
  - **Communication & Email Mix:** Every 5th member has `whatsappOptIn = false`; every 3rd member has `email = null`.

### 3.2 Plans (9 per active gym)
1. **Monthly Standard (मासिक):** 30 days — `₹1,500.00` (`150000` paise)
2. **Quarterly Power (त्रैमासिक):** 90 days — `₹4,000.00` (`400000` paise)
3. **Half-Yearly Transform (अर्धवार्षिक):** 180 days — `₹7,500.00` (`750000` paise)
4. **Annual Elite (वार्षिक):** 365 days — `₹12,000.00` (`1200000` paise)
5. **Starter + Joining Fee:** 30 days — `₹1,200.00` + `₹500.00` joining fee (`120000` + `50000` paise)
6. **7-Day Free Trial (निःशुल्क ट्रायल):** 7 days — `₹0.00` (`0` paise)
7. **Express Monthly ₹999:** 30 days — `₹999.00` (`99900` paise — GST stress test: `84661` taxable + `7619` CGST + `7620` SGST = `99900`)
8. **Silver Monthly ₹1,180:** 30 days — `₹1,180.00` (`118000` paise — exact `100000` taxable + `9000` CGST + `9000` SGST)
9. **Pro Quarterly ₹3,333:** 90 days — `₹3,333.00` (`333300` paise — odd-paise GST stress test: `282458` taxable + `25421` CGST + `25421` SGST = `333300`)

### 3.3 Invoices & Payments (Spanning FY `25-26` and FY `26-27` across 31 Mar / 1 Apr)
- **FY `25-26` (29 Mar, 30 Mar, 31 Mar 2026):** `{PREFIX}/25-26/00001` through `{PREFIX}/25-26/00003`.
- **FY `26-27` (1 Apr 2026 onwards):** Sequence resets cleanly to `{PREFIX}/26-27/00001` on 1 April.
- **Deterministic Public Pay Page Tokens for Testing (`/pay/{token}`):**
  - Unpaid invoice (`ISSUED`, ₹1,500 due, with 1 prior `FAILED`/rejected UTR):
    - `/pay/demo-unpaid-token-bajrang-lko`
    - `/pay/demo-unpaid-token-ironcore-ind`
    - `/pay/demo-unpaid-token-surya-jpr`
  - Partially paid invoice (`PARTIAL`, ₹1,180 total, ₹500 cash paid, ₹680 balance due):
    - `/pay/demo-partial-token-bajrang-lko`
    - `/pay/demo-partial-token-ironcore-ind`
    - `/pay/demo-partial-token-surya-jpr`
  - Invoice with a `PENDING` UPI UTR awaiting owner confirmation/rejection:
    - `/pay/demo-pending-utr-bajrang-lko` (UTR `609012000001`)
  - Expired token invoice (`payTokenExpiresAt` in the past):
    - `/pay/demo-expired-token-bajrang-lko`
  - Voided invoice (`VOID`):
    - `/pay/demo-void-token-bajrang-lko`
- **Credit Notes:** `CN/26-27/00001` issued per active gym against an annual invoice (`STATUS_CREDIT_NOTED`).

### 3.4 Activity & CSV Fixtures
- **Check-ins:** 60 consecutive days of historical check-ins + 4 members currently **"Inside Now"** per active gym.
- **Leads:** 10 leads per active gym across `NEW`, `CONTACTED`, `TRIAL`, `CONVERTED` (linked to `convertedMemberId`), and `LOST`.
- **WhatsApp Outbox:** 7 messages per active gym across `QUEUED`, `OPENED`, `SENT`, `FAILED` (`QUIET_HOURS_DEFERRED`), and `OPTED_OUT`.
- **CSV Test Fixtures (`fixtures/csv/`):**
  - `valid-members.csv` (5 valid bilingual members with mixed phone formats)
  - `valid-payments.csv` (2 valid payments linked to invoices)
  - `bad-invalid-phone.csv` (short, invalid prefix, and alphabetic phones)
  - `bad-duplicate-phone.csv` (duplicate normalized phones within batch)
  - `bad-missing-columns.csv` (missing required `name` and `phone` headers)
  - `bad-encoding.csv` (corrupted non-UTF8 bytes `\xff\xfe\xfd`)
  - `bad-5000-rows.csv` (5,000 rows to test batch size limits)
  - `bad-formula-injection.csv` (cells starting with `=`, `+`, `-`, `@`)
