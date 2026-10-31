# GymKhata — Gym Owner Handbook (जिम मालिक गाइड)

Welcome to **GymKhata** — the multi-tenant gym management system designed for independent gyms across India.

---

# Part 1: English Guide

## 1. Core Philosophy: Reception-Free Operations
GymKhata is built on a simple reality: **Gym reception is not always staffed.**
- Membership validity and status derive **strictly from dates and payments** (`MembershipDates.java`, `PaymentService.java`) — never from whether someone checked in today.
- If a member pays and has active dates, they are active. If their membership expires, they are expired.
- Daily check-in is **100% optional** and **OFF by default** (`attendanceEnabled = false` in `Gym.java:108`). When disabled, zero attendance or check-in menus/widgets appear in your owner app.

## 2. Enrolling Members & Self Check-In PIN
When you enrol a new member on `/app/members`:
1. **Random 4-digit PIN:** GymKhata automatically generates a random **4-digit PIN** (`0000`–`9999`, `MemberService.java:152`) stored as a BCrypt hash.
2. **Shown Once:** The PIN is displayed to you immediately in a welcome banner with a pre-filled WhatsApp welcome message.
3. **WhatsApp Welcome:** Tap the WhatsApp button to send the welcome text to the member:
   > *"Welcome to PowerFit Gym, Rohan! Your 4-digit self check-in PIN is 4821. Scan the QR at /g/powerfit-gym/in to check in."*
4. **PIN Reset, Per-Phone Unbound Budget & Instant Lockout Reset:** If a member forgets their PIN or gets locked out after **5 failed attempts** per IP (`CheckinService.MAX_PIN_ATTEMPTS = 5`), or if **20 failed attempts across rotating IPs in 24 hours** (`CheckinService.MAX_UNBOUND_PHONE_FAILURES_24H = 20`) trigger owner-approval mode for new devices (`PENDING_OWNER_APPROVAL`), you can approve their pending device (`POST /api/v1/members/{id}/pending-devices/{requestId}/approve`), click **Reset PIN** (`POST /api/v1/members/{id}/reset-pin`), or unlock them (`POST /api/v1/members/{id}/reset-pin-lockout`) on their profile. Members with an already-bound device can continue checking in uninterrupted even during an attack.

## 3. Self-Service QR Attendance (Optional)
Enable attendance under **Settings → Self-Service QR Entry & Visits**:
- **Download A4 Poster:** Click **Download A4 Poster** (`GET /api/v1/checkins/poster.pdf`). Print the PDF on A4 paper and place it at your gym entrance.
- **Member Flow:**
  - **First Scan:** The member scans the QR code (`/g/{gymSlug}/in`), enters their **10-digit** Indian mobile number (`PhoneUtils.java`) and **4-digit PIN**. GymKhata binds their phone browser with an `HttpOnly` cookie (`gk_device_token`, valid for **365 days**, up to **2 bound devices** per member, `CheckinService.MAX_BOUND_DEVICES = 2`).
  - **Later Scans:** Checking in takes **1 tap**.
  - **Status Screen (Never Blocks Entry):**
    - **Green Badge:** Active membership with more than **7 days** remaining (`CheckinService.java`).
    - **Amber Badge:** Active membership expiring within **7 days** (`daysLeft <= 7`, `CheckinService.java`).
    - **Red Badge:** Expired or unpaid membership (`CheckinService.java`) with a 1-tap UPI payment link (`/pay/{payToken}` or `upi://pay`) and your gym's contact number.
- **Strict Mode (Optional):** Enable *Strict Mode: Rotating 60s QR Token*. Open `/g/{gymSlug}/screen` on a tablet or screen at the gym. The HMAC-SHA256 QR token rotates every **60 seconds** (`GeofenceAndQrUtils.ROTATION_WINDOW_SECONDS = 60`, accepting current + previous window).
- **Soft Geofence:** Optionally configure your gym's latitude, longitude, and radius (default **100 meters**, `Gym.java:114`). Off-premise check-ins are logged with `withinGeofence = false` for your review without blocking the member.
- **Auto-Close:** Open sessions automatically close after **120 minutes (2 hours)** (`autoCloseDurationMinutes = 120`, `Gym.java:116`) or at your configured closing time (`closingTimeIst`, default `22:00`, `Gym.java:115`).

## 4. Personal Training (PT) & Trainer Commissions
GymKhata provides a complete Personal Training lifecycle under `/app/pt`:
- **Trainers as Managed Records:** Trainers do not have logins (preserving the **1-owner-per-gym** security rule). Add their name, phone, and commission rule (`PERCENT_OF_TAXABLE`, `FIXED_PER_PACKAGE`, `FIXED_PER_SESSION`, or `SLAB`).
- **PT Packages:** Define session-based (e.g., 10 sessions, 60 days validity) or time-based (e.g., 30 days) packages.
  - Price is all-inclusive in integer paise with GST back-calculated (`GstCalculator.fromInclusive`).
  - GST rate, SAC code, effective date, and ITC status (`No ITC` / `With ITC`) are configurable per gym and per item type (`MEMBERSHIP`, `PT`, `ADMISSION_FEE`) in **Settings → GST Rate, SAC & Effective Date per Item Type** (`Gym.Settings.gstRules` + `application.yml:69-105`).
  - Default for `MEMBERSHIP`, `PT`, and `ADMISSION_FEE` is SAC `999723` at **5% GST (No ITC)** effective `2025-09-22` (**18% With ITC** prior to `2025-09-22`). *Confirm GST rate, SAC code, and ITC eligibility with your Chartered Accountant (CA).*
  - Every invoice and PDF prints the item's **SAC code**, **GST rate**, and **ITC status** (`[No ITC]` / `[With ITC]`).
- **Proportional Payment Accruals (Cash-Flow Safe):**
  - If a member pays **60%** of the invoice today, the trainer accrues **60%** of their total package commission today (`CommissionCalculator.computeProportionalPaymentAccrual`).
  - When the remaining **40%** is paid, the remaining **40%** commission accrues.
- **Rule Immutability:** Changing a trainer's commission rate today **never alters past accruals** in the ledger (`CommissionEntry.ruleSnapshot`).
- **Credit Note Reversals:** Issuing a Credit Note automatically posts a reversing entry to the trainer's commission ledger (`CommissionService.reverseForCreditNote`).
- **Monthly Statements:** View monthly commission statements with 1-click WhatsApp summary share, CSV export, and PDF receipt download.

---

# Part 2: हिन्दी गाइड (सरल भाषा में)

## 1. बिना रिसेप्शनिस्ट के चलने वाला सिस्टम
GymKhata इस सोच पर बना है कि हर जिम में हर समय रिसेप्शन पर स्टाफ नहीं होता:
- मेंबर का एक्टिव या एक्सपायर्ड स्टेटस **तारीख और फीस जमा होने** से तय होता है।
- रोज हाजिरी (अटेंडेंस) लगाना बिल्कुल ऑप्शनल (ऐच्छिक) है और नए जिम के लिए **डिफ़ॉल्ट रूप से बंद (OFF)** रहता है।

## 2. नया मेंबर जोड़ना और 4-अंकों का पिन (PIN)
जब आप `/app/members` में नया मेंबर जोड़ते हैं:
1. सिस्टम अपने आप उस मेंबर के लिए **4 अंकों का रैंडम पिन** बना देता है।
2. यह पिन आपको स्क्रीन पर दिखता है और साथ ही एक-क्लिक व्हाट्सएप वेलकम मैसेज भी तैयार मिलता है।
3. अगर मेंबर पिन भूल जाए, **5 गलत प्रयासों** के बाद लॉक हो जाए, या नए फोन पर **20 गलत प्रयासों (24 घंटे में)** के बाद ओनर अप्रूवल मांगे, तो उसके प्रोफाइल पर डिवाइस अप्रूव करें या **Reset PIN** बटन दबाएं। पहले से रजिस्टर (Bound) फोन बिना रुकावट चेक-इन कर सकते हैं।

## 3. क्यूआर कोड से सेल्फ चेक-इन
**Settings → Self-Service QR Entry & Visits** में जाकर इसे चालू करें:
- **A4 पोस्टर डाउनलोड करें:** 'Download A4 Poster' पर क्लिक करें और जिम के दरवाजे पर लगाएं।
- **मेंबर कैसे चेक-इन करेगा?**
  - पहली बार: मेंबर क्यूआर स्कैन करेगा, अपना **10 अंकों** का मोबाइल नंबर और **4-अंकों** का पिन डालेगा। (एक मेंबर के अधिकतम **2 फोन** रजिस्टर हो सकते हैं)।
  - अगली बार: सिर्फ **1 टैप** में चेक-इन!
  - **स्क्रीन पर क्या दिखेगा?**
    - **हरा (Green):** 7 दिन से अधिक की वैधता बाकी है।
    - **पीला (Amber):** 7 दिन या उससे कम में एक्सपायर होने वाला है — फीस रिन्यू करने का यूपीआई लिंक।
    - **लाल (Red):** एक्सपायर हो चुका है — तुरंत यूपीआई से पेमेंट करने का बटन।

## 4. पर्सनल ट्रेनिंग (PT) और ट्रेनर कमीशन
- **ट्रेनर का खाता:** ट्रेनर का नाम, फोन और कमीशन का नियम सेट करें।
- **जितना पैसा आया, उतना ही कमीशन:** अगर मेंबर ने **60%** फीस दी है, तो ट्रेनर को **60%** कमीशन ही क्रेडिट होगा। जब बची हुई **40%** फीस आएगी, तभी बाकी कमीशन जुड़ेगा।
- **महीने का हिसाब:** हर महीने का कमीशन स्टेटमेंट देखें, व्हाट्सएप पर ट्रेनर को भेजें और पीडीएफ रसीद डाउनलोड करें।

---

## Appendix: Source Verification Table for All Numbers in This Guide

| Number / Claim | Codebase Source |
| :--- | :--- |
| `attendanceEnabled = false` by default | `backend/src/main/java/com/gymkhata/admin/model/Gym.java:108` |
| Random **4-digit PIN** (`%04d`) | `backend/src/main/java/com/gymkhata/member/MemberService.java:152` |
| **10-digit** Indian mobile normalization (`+91`) | `backend/src/main/java/com/gymkhata/domain/PhoneUtils.java` |
| Max **5 failed PIN attempts** per IP + exponential backoff (**60s, 300s, 900s, 3600s**) | `backend/src/main/java/com/gymkhata/checkin/CheckinService.java:58,524` |
| Per-phone unbound PIN budget **20 failures / 24h** across all IPs | `backend/src/main/java/com/gymkhata/checkin/CheckinService.java:60-61` |
| Max **2 bound devices** per member, **365-day** cookie TTL | `CheckinService.java:62`, `PublicAttendanceController.java:78` |
| Amber badge threshold **<= 7 days** | `backend/src/main/java/com/gymkhata/checkin/CheckinService.java` |
| Rotating QR window **60 seconds** | `backend/src/main/java/com/gymkhata/domain/GeofenceAndQrUtils.java:16` |
| Default geofence radius **100 meters** | `backend/src/main/java/com/gymkhata/admin/model/Gym.java:114` |
| Auto-close open sessions after **120 minutes (2 hours)** or `22:00` IST | `backend/src/main/java/com/gymkhata/admin/model/Gym.java:115-116` |
| Configurable GST (`MEMBERSHIP`, `PT`, `ADMISSION_FEE`): default SAC `999723`, **18% With ITC** prior to `2025-09-22`, **5% No ITC** from `2025-09-22` (confirm with CA) | `backend/src/main/resources/application.yml:69-105`, `GstConfig.java` |
| **60% / 40%** proportional commission accrual | `CommissionCalculatorTest.java`, `frontend/e2e/prod-flows.spec.ts` |
