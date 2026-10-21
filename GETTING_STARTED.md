# Getting Started with GymKhata

A step-by-step guide to running GymKhata on your own machine.

---

## 1. Prerequisites

Install these first. Versions in **bold** are what the project was built and
tested with; the ranges are the supported minimums.

| Tool | Version | Check with |
|---|---|---|
| **JDK** | 17+ (**21** used) | `java -version` and `javac -version` |
| **Maven** | 3.6+ (**3.9.16** used) | `mvn -v` |
| **Node.js** | 20.19+ (22.12+ used) | `node -v` |
| **npm** | 9+ (**11.17** used) | `npm -v` |
| **MongoDB** | 6+ (**9.0** used) | must answer on `localhost:27017` |
| **curl** | any (optional) | `curl --version` |

> **Node 18 will not work** — Vite 8 requires `^20.19.0` or `>=22.12.0`.
>
> **On Windows with a JRE on PATH:** if `java -version` reports `1.8.x`, you
> have a JRE, not a JDK. Install a JDK 17+ and the launchers will find it
> automatically. They never change your global `JAVA_HOME` or `PATH`.

---

## 2. Clone the repository

```powershell
git clone https://github.com/bhaktaji/gym-management.git
cd gym-management
```

---

## 3. Configure the environment

All configuration lives in `backend/src/main/resources/application.yml`, driven
by environment variables with working development defaults. **There is nothing
you have to create to get started** — the defaults boot the stack locally.

To see every available knob, look at:

- `backend/.env.example` — the annotated list of environment variables
- `backend/src/main/resources/application.yml` — the defaults themselves

The values you are most likely to change:

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/gymkhata_dev` | Database connection |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `SUPER_ADMIN_PHONE` | `+919000000001` | Seeded platform admin |
| `SUPER_ADMIN_PASSWORD` | `ChangeMe@123` | Seeded platform admin password |
| `SIGNUP_AUTO_ACTIVATE` | `false` | Self-signed-up gyms start `PENDING` |
| `MESSAGING_PROVIDER` | `clicktochat` | `clicktochat` \| `whatsappcloud` \| `mock` |
| `PAYMENT_PROVIDER` | `directupi` | `directupi` \| `mock` \| `razorpay` |

**Security:** the default JWT secret and super-admin password are **development
placeholders**. For anything outside local development, set `JWT_SECRET` and
`SUPER_ADMIN_PASSWORD` (the `prod` profile refuses to start with the development
secret). See `backend/.env.example` for the full list.

**Database safety:** the app only uses `gymkhata_dev` and (for tests)
`gymkhata_test_<random>`. It never reads or drops any other database.

---

## 4. Install dependencies

The backend downloads its dependencies from Maven Central on first build —
nothing to install by hand.

The frontend needs `node_modules` once:

```powershell
cd frontend
npm install
cd ..
```

---

## 5. Build

### Option A — let the launcher build for you

```bat
start-all.bat build
```

This runs `scripts\build-all.ps1`, which:

1. builds the frontend (`tsc -b` + `vite build`),
2. copies `frontend/dist` into `backend/src/main/resources/static` so the jar
   serves the PWA,
3. packages the Spring Boot jar into `backend/target/`.

### Option B — build manually

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
```

Options: `-SkipTests` to skip the backend test run.

The result is `backend/target/gymkhata-backend-0.1.0.jar`.

---

## 6. Run

### Option A — batch launcher (recommended for Windows)

```bat
start-all.bat
```

It starts, in order and verifying each step:

```
[1/4] Checking MongoDB...      reuse the running instance, or start the service
[2/4] Starting the backend...  Spring Boot on :8080
[3/4] Starting the frontend... Vite on :5173
[4/4] Waiting for both to answer... then opens your browser
```

Each service gets its own console window, so logs stay readable. Stop everything:

```bat
stop-all.bat
```

### Option B — PowerShell launcher (headless, log files)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1
```

The services run in the background and write to `logs/`
(`backend.log`, `backend.err.log`, `frontend.log`, `frontend.err.log`).

### Option C — run the jar directly

```powershell
java -jar backend\target\gymkhata-backend-0.1.0.jar
```

Useful when you only want the backend and will serve the frontend separately.

### Option D — hot reload for development

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-dev.ps1
```

Backend on `:8080`, Vite on `:5173` with automatic reload. Stop with
`scripts\stop-dev.ps1`.

> Use **one** launcher or the other — `start-all.bat` and `start-all.ps1` track
> their processes differently.

---

## 7. Verify it is working

| Check | Expected |
|---|---|
| Backend health | `http://localhost:8080/actuator/health` → `{"status":"UP"}` |
| Frontend | `http://localhost:5173` → the app shell |
| API through the Vite proxy | `http://localhost:5173/api/v1/config/public` → `{"name":"GymKhata",…}` |
| Interactive API docs | `http://localhost:8080/swagger-ui.html` |
| Super-admin console | `http://localhost:8080/admin` |
| PWA manifest | `http://localhost:8080/manifest.json` |

### Log in

1. Open **http://localhost:5173/login**.
2. Super admin: `+919000000001` / `ChangeMe@123`.
3. You land on **http://localhost:8080/admin** — create a gym and its owner.
4. Log in as that owner to reach the gym console at **http://localhost:5173/app**.

### Smoke-test the core loop

1. **Plans** → add a plan, e.g. `Monthly`, 30 days, `1180` (all-inclusive).
2. **Members** → add a member, leave "Send WhatsApp reminders" on.
3. Open the member → **Renew** → pick the plan → **Generate invoice**.
   - Expect `GK/26-27/000001`, `TAX_INVOICE`, total `₹1,180.00`, and a line with
     SAC `999723`, taxable `₹1,123.81`, CGST `₹28.09`, SGST `₹28.10`.
4. **Billing** → open the invoice → **Record payment** (cash), then watch the
   balance drop.
5. **Check-ins** → check the member in; try again — it is rejected (409).
6. **Messages** → **Generate today's reminders** → **Send on WhatsApp** (opens a
   `wa.me` link) → **Mark sent**.
7. **Settings** → set your UPI VPA and edit the English/Hindi templates.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `No backend jar in ...` | Build first: `scripts\build-all.ps1`, or `start-all.bat build`. |
| `No JDK 17 or newer could be located` | Install a JDK 17+ (21 recommended) — the `java` on `PATH` may be a JRE 8. |
| `Port 8080 is already in use` | Another program owns `:8080`. Stop it, or pass `-BackendPort`. |
| `node_modules is missing` | Run `cd frontend; npm install` once. |
| `MongoDB did not become ready` | Check `Get-Service MongoDB` (Windows) or start your own `mongod` on `:27017`. |
| The backend window shows a stack trace | Read it — common causes are a missing MongoDB and the JRE-8-on-PATH problem. |
| The frontend comes up on :5174 | `vite.config.ts` sets `strictPort: true`, so Vite fails loudly instead. Close whatever holds `:5173`. |
| `ACCOUNT_SUSPENDED` | The gym was suspended in the admin console; reactivate it there. |
| Want to see what `stop-all` would do | `scripts\stop-all.ps1 -WhatIf` |

Logs live in `logs/` and are git-ignored.

---

## 9. Next steps

- Read [`docs/API.md`](docs/API.md) for every endpoint.
- Read [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) for collections and indexes.
- Read [`docs/DOMAIN_RULES.md`](docs/DOMAIN_RULES.md) for the India-specific rules.
- Read [`TEST_REPORT.md`](TEST_REPORT.md) for what is verified and how.
