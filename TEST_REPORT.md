# TEST_REPORT.md

Every number below comes from a command that was actually run in this
environment. Commands are listed with the exact invocation.

Environment: Windows 11, PowerShell 5.1, JDK 21.0.12.1, Node v24.19.0,
npm 11.17.0, MongoDB 9.0.2 (standalone, `localhost:27017`).

---

## 1. Backend test suite

**Command**

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
mvn clean test
```

**Result**

```
[INFO] Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 1.1 Unit tests — pure domain logic (35 tests)

| Test class | Tests | What it locks down |
|---|---|---|
| `MoneyTest` | 2 | Indian digit grouping (₹1,23,456.00), paise conversion |
| `GstCalculatorTest` | 5 | Back-calculation from an all-in total; CGST/SGST split; IGST; "lines sum exactly to total" proven for 100k+ generated cases; rejects negative totals |
| `FinancialYearTest` | 3 | FY boundary (31 Mar / 1 Apr), `26-27` labels, label validation |
| `InvoiceNumberingTest` | 4 | `PFX/26-27/000123` format, prefix rules, number validation |
| `IndianNumberWordsTest` | 4 | Amount in words with lakh/crore grouping and paise |
| `PhoneUtilsTest` | 3 | E.164 normalisation of 10-digit / 0-prefixed / +91 / bare-91 input; rejects invalid; masking for logs |
| `UpiUtilsTest` | 4 | VPA regex, 12-digit UTR, `upi://pay` deep link contents, amount formatting without floats |
| `CheckinRulesTest` | 5 | Check-in decisions: allowed, inactive member, no membership, expired, double check-in |
| `QuietHoursTest` | 1 | 21:00–08:00 IST window boundaries |
| `MembershipDatesTest` | 3 | Inclusive expiry date, days remaining, invalid duration |
| `MongoConnectivityTest` | 1 | MongoDB reachable; reports topology (`setName=null` → standalone) |

### 1.2 Integration tests — real MongoDB, throwaway database (24 tests)

Test database: `gymkhata_test_<random>` (created per run, dropped by a JVM
shutdown hook). Documents are cleared between tests; the app creates the
indexes on startup. No other database is read or dropped.

| Test class | Tests | What it locks down |
|---|---|---|
| `OneOwnerPerGymTest` | 5 | **1 owner per gym** at service level *and* at the database level (partial unique index on `users.gymId` where `role=GYM_OWNER`); different gyms each have one; SUPER_ADMIN (`gymId=null`) is excluded from the index; user phone globally unique |
| `BillingIntegrationTest` | 11 | Gapless invoice numbers (`TG/26-27/000001…3`); GST lines sum exactly to the total (₹1,180 → taxable 112381 + CGST 2809 + SGST 2810 = 118000); IGST for inter-state; no tax lines when not GST-registered; partial payments; overpayment rejected; duplicate UTR rejected; invalid UTR rejected; owner confirmation settles the balance; credit notes in their own `CN` series; voided invoice cannot be paid |
| `AuthAndTenancyIntegrationTest` | 8 | Super admin creates a gym + its single owner; login works with any accepted phone format; duplicate phone rejected; suspended gym owner cannot log in (`ACCOUNT_SUSPENDED`); self-signup creates a `PENDING` gym + owner; refresh-token rotation and reuse detection; **tenant isolation** (owner A cannot see owner B's members or plans); member phone unique per gym only |

**Verified failure modes exercised in the integration suite**

- second `GYM_OWNER` for the same gym → `DuplicateKeyException` (DB index)
- payment above the balance → `OVERPAYMENT`
- second payment with the same UTR → `DUPLICATE_UTR`
- UTR that is not 12 digits → `INVALID_UTR`
- owner of a suspended gym → `403 ACCOUNT_SUSPENDED`
- reuse of a rotated refresh token → `REFRESH_REUSE_DETECTED`

---

## 2. Frontend test suite

**Command**

```powershell
cd frontend
npm test
```

**Result**

```
Test Files  1 passed (1)
     Tests  7 passed (7)
```

`src/lib/format.test.ts` covers the money formatter (Indian grouping, negatives,
missing values, symbol toggle), the date formatter and the date-input helper.

**Type check**

```powershell
npx tsc -b --force
```
Exit code 0, no errors.

**Production build**

```powershell
npm run build
```
```
✓ built in 2.36s
dist/manifest.json                          0.46 kB
dist/index.html                             0.78 kB
dist/assets/index-*.css                    28.92 kB │ gzip:  5.76 kB
dist/assets/index-*.js                    952.11 kB │ gzip: 277.57 kB
dist/sw.js, dist/workbox-*.js
```

**Full-stack build (frontend bundle → backend jar)**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -SkipTests
```
Result: `Build complete.` — jar at `backend/target/gymkhata-backend-0.1.0.jar`.

---

## 3. Live end-to-end verification against the running jar

The packaged jar was started and the following was exercised with real HTTP
calls (PowerShell `Invoke-RestMethod`):

| Scenario | Observed result |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}` |
| `GET /` (SPA shell) | 200 `text/html`, contains `<div id="root">` and the app title |
| `GET /app/members`, `/pay/abc123`, `/pricing` (client-side routes) | 200 — all forward to `index.html` |
| `GET /manifest.json` | 200 `application/json`, `name=GymKhata`, `start_url=/app`, `display=standalone` |
| `GET /sw.js` | 200 — Workbox service worker |
| `GET /api/v1/config/public` | `{"name":"GymKhata","pricing":{"monthlyPricePaise":99900,…}}` |
| `GET /api/v1/members` without a token | **401** (API is protected) |
| Super-admin login → `GET /api/v1/admin/gyms` | 1 gym listed with owner, masked phone `+91*****3210`, city, GST flag, active-member count, status, subscription date, last login |
| Admin creates gym + owner | `{"status":"created","gymId":"…","code":"SHA"}` |
| Owner login → tenant APIs | dashboard, members, plans, invoices, outbox, reports, plan-usage all return data scoped to that gym |
| Suspend gym → owner logs in | **403** `ACCOUNT_SUSPENDED` |
| Reactivate gym → owner logs in | succeeds again |
| Refresh token rotation | first refresh issues a new token; reusing the old one → **403** `REFRESH_REUSE_DETECTED` |
| Enrol member → generate invoice | `GK/26-27/000001`, `TAX_INVOICE`, total 118000, words "One Thousand One Hundred Eighty Rupees Only", line `SAC 999723 taxable 112381 CGST 2809 SGST 2810 rate 5%` |
| Gapless numbering | generated `000001`, `000002`, `000003`, `000004` — no gaps |
| Credit note | `CN/26-27/000001`, total `-50000` |
| Void invoice | status `VOID`, reason recorded, number retained |
| Partial cash payment | ₹500 confirmed, balance ₹680 tracked |
| Member submits UTR on the public pay page | `PENDING_VERIFICATION` |
| Owner confirms UTR | `CONFIRMED`, balance 0 |
| Duplicate UTR | rejected (`DUPLICATE_UTR`) |
| Overpayment | rejected (`OVERPAYMENT`) |
| Check-in | succeeds for a member with an active membership |
| Second check-in same day | **409** `ALREADY_CHECKED_IN` |
| WhatsApp outbox | generated for a member expiring within 7 days; `https://wa.me/919811223344?text=…`; mark-sent works |
| Lead → member conversion | lead becomes `CONVERTED` and a member is created |
| CSV import (members) | 1 row imported, 1 row reported as an error (per-row errors returned) |
| DPDP export | returns `{member, memberships}` JSON |
| DPDP erase | name → "Erased Member", phone and email cleared; invoices/payments retained |
| Invoice PDF | 1629 bytes, header `%PDF-1.5` |
| UPI deep link | `upi://pay?pa=shaktifitness%40okicici&pn=Shakti+Fitness&am=0.00&cu=INR&tn=GK%2F26-27%2F000001` |
| Reports | revenue by month, expiring memberships, check-ins per day, members per plan |

---

## 4. Startup scripts

**Command**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-start-all.ps1
```

**Result: ALL 17 CHECKS PASSED**

| # | Check | Observed |
|---|---|---|
| 1 | `start-all.ps1` exits 0 (cold start) | exit 0 |
| 2 | backend `/actuator/health` returns UP | `"status":"UP"` |
| 3 | the service on :8080 really is GymKhata | `/api/v1/config/public` → `"name":"GymKhata"` |
| 4 | frontend on :5173 serves the app shell | 200, body contains `id="root"` |
| 5 | backend can reach MongoDB | API answers |
| 6 | second `start-all.ps1` run exits 0 | exit 0 |
| 7 | no duplicate processes created by the re-run | tracked-process count unchanged |
| 8 | re-run reused the running backend | log: "already running and healthy" |
| 9 | re-run reused the running frontend | log: "already running" |
| 10 | `stop-all.ps1 -WhatIf` exits 0 | exit 0 |
| 11 | dry-run stops nothing | tracked-process count unchanged |
| 12 | `stop-all.ps1` exits 0 | exit 0 |
| 13 | no tracked process left running | tracked set empty afterwards |
| 14 | **unrelated processes untouched** | the 3 unrelated `node`/`java` processes (other tooling) all survived; the stop killed a 7-process GymKhata tree only |
| 15 | port 8080 free again | closed |
| 16 | port 5173 free again | closed |
| 17 | MongoDB left running | still accepting connections |

### 4.1 Error paths, exercised individually

| Scenario | Command | Observed |
|---|---|---|
| Missing jar | removed `backend\target\*.jar`, then `start-all.ps1` | `Backend jar not found ...` + how to build; **exit 4**; frontend never started |
| Foreign service on :8080 | bound a plain TCP listener on 8080, then `start-all.ps1` | `Port 8080 is already in use by something that is not a healthy GymKhata backend`; **exit 3**; frontend never started |
| `-Build` while the backend runs | `start-all.ps1 -Build` with the backend up | `The backend is running (pid N) and would block the jar from being overwritten.` + remedy; **exit 1**; no build attempted |
| MongoDB unreachable | `start-all.ps1 -MongoPort 27117 -MongoTimeoutSec 15` | service detected by name, wait loop ran, `MongoDB did not become ready within 15s`; **exit 2**; backend/frontend never started |

### 4.2 PowerShell syntax

All eight scripts parse cleanly with
`[System.Management.Automation.Language.Parser]::ParseFile` (0 errors):
`build-all.ps1`, `process-tracking.ps1`, `resolve-jdk.ps1`, `run-dev.ps1`,
`start-all.ps1`, `stop-all.ps1`, `stop-dev.ps1`, `test-start-all.ps1`.

### 4.3 What could NOT be verified here

| Item | Reason |
|---|---|
| Starting the MongoDB **Windows service** from a stopped state | This shell cannot stop the service (`Cannot open MongoDB service on computer '.'` — no elevation). The service *detection* (by name and display name) and the *failure* path (unreachable port → service found → wait → timeout → exit 2) were both verified; the actual `Start-Service` call was not executed against a stopped service. |
| The script running inside an interactive console with a visible window | The verification ran the scripts headless with redirected output. `Start-Process -WindowStyle Hidden` gives each service its own hidden console, so they are not tied to the caller's window. |
| Behaviour when the parent PowerShell is closed by the user | The services were confirmed to still be running and healthy 3 minutes after the launching script exited, but a real console close was not simulated. |

---

## 5. SKIPPED / NOT COVERED

| Item | Reason |
|---|---|
| **Browser UI tests** (Playwright/Cypress) | No Chrome and no Edge are installed on this machine (`C:\Program Files\Google\Chrome\Application\chrome.exe` = False, `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` = False). The UI was verified by production build, type check, unit tests and live HTTP checks of the served bundle — not by rendering it in a browser. |
| **Live WhatsApp Cloud API call** | No provider credentials; the adapter is behind a flag and makes no network call. `WhatsAppCloudProvider` is labelled NOT PRODUCTION-TESTED. |
| **Live Razorpay API call** | No provider credentials; the adapter is behind a flag and is labelled NOT PRODUCTION-TESTED. |
| **Email / SMS delivery** | No provider is wired up. Password-reset tokens are returned by the API as a documented stand-in for the delivery channel. |
| **Load / performance testing** | Out of scope for this build. |
| **Multi-document transaction tests** | MongoDB runs standalone here, so the application deliberately uses no multi-document transactions; the compensating paths (VOID invoice on insert failure, gym delete on owner-insert failure) are implemented but the failure injection is not automated. |
| **mongosh inspection** | mongosh is not installed; the database was inspected with the MongoDB Java driver from the test suite. |

---

## 6. Bugs found and fixed during the build

Each of these was found by a test or a live check, then fixed and re-verified.

| # | Bug | Fix |
|---|---|---|
| 1 | Invoice generation failed with `ConflictingUpdateOperators` — the counter update used `inc("seq")` **and** `setOnInsert("seq")` on the same path | Use `$inc` alone with upsert; `$inc` creates the field with value 1 on insert |
| 2 | Public pay page returned 403 when a member submitted a UTR — `submitUpi` resolved the gym from the JWT, but the pay page is unauthenticated | Resolve `gymId` from the invoice; the pay token is the authorisation |
| 3 | Check-in failed with "non unique result" when a member had two ACTIVE memberships | Enrol now supersedes the previous active membership; the active-membership lookup takes the latest expiry date |
| 4 | A suspended gym owner could still log in (only the user status was checked) | Login and refresh also check the **gym** status → `403 ACCOUNT_SUSPENDED` |
| 5 | 500 on every page — the security error handler used a bare `new ObjectMapper()` that could not serialise `Instant` | Inject Spring Boot's configured `ObjectMapper` |
| 6 | SPA routes and static assets returned 403/500 | Security now permits the SPA shell and assets; only `/api/**` is protected |
| 7 | Unique indexes were silently missing in tests — the shared base class dropped the test database (and its indexes) after the first test class | One test database per run, dropped at JVM exit; documents cleared between tests |
| 8 | Tailwind v4 rejected `@apply` of custom classes | Custom classes declared with `@utility` (Tailwind v4 syntax) |
| 9 | `IndianNumberWords` threw `ArrayIndexOutOfBounds` for values ≥ 100 | Added hundreds handling |
| 10 | Phone masking revealed two digits of the subscriber number | Mask keeps only the country code and the last 4 digits |
| 11 | Invoice PDF meta table passed two strings where a (text, font) pair was expected | Added a dedicated label/value cell |
| 12 | PWA manifest was served as `application/octet-stream` | Use `manifest.json`, served correctly as `application/json` |
| 13 | The build script used the machine's JRE 8 for Maven | `scripts/resolve-jdk.ps1` finds a JDK 17+ and sets it for the build only |
| 14 | `stop-all.ps1` crashed with "Cannot overwrite variable PID" — `$pid` is a read-only automatic variable in PowerShell | Renamed the local variable to `$procId` |
| 15 | The stop script reported a stale entry as cleaned up but left it in the state file | The stale branch now calls `Remove-GymKhataService` |
| 16 | `stop-all.ps1` had to walk a 4-level process tree (`cmd → node npm-cli → cmd → node vite`) | `Get-DescendantProcessIds` walks recursively and stops children before parents |
| 17 | The MongoDB driver check never engaged — `Get-ChildItem -Filter "bson-[0-9]*.jar"` does not match that pattern, and it would also have matched `bson-record-codec` | Filter with a regex on the file name and pick the newest version that has **all** required artifacts |
| 18 | The MongoDB readiness timeout was overshot (67 s for a 10 s limit) because the driver's default server-selection timeout is 30 s | Added `serverSelectionTimeoutMS`/`connectTimeoutMS` to the ping URI, and check the TCP port first so a dead server fails in ~1.5 s |
| 19 | The startup verification hung: `& powershell ... *> $log` waits for EOF on the redirected handle, which the spawned services inherit and hold open | The test now uses `Start-Process` + `Wait-Process` on the specific PID |
| 20 | The test could not read the exit code of a `Start-Process` child (`ExitCode` is empty in Windows PowerShell) | Cache the process handle (`$null = $proc.Handle`) before the process exits |
| 21 | A "no unrelated process was killed" assertion was flaky | The test now only counts long-lived unrelated processes (a short-lived helper that exits on its own is not "killed") |
