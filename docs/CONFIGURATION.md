# Configuration Reference

Every setting GymKhata reads, in one place. All values have working defaults in
`backend/src/main/resources/application.yml`; override any of them with an
environment variable of the same name.

| Variable | Default | Description |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/gymkhata_dev` | MongoDB connection string |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `SUPER_ADMIN_PHONE` | `+919000000001` | Seeded platform admin phone (E.164) |
| `SUPER_ADMIN_PASSWORD` | `ChangeMe@123` | Seeded platform admin password |
| `SUPER_ADMIN_NAME` | `Platform Admin` | Seeded platform admin display name |
| `SIGNUP_AUTO_ACTIVATE` | `false` | `true` → self-signed-up gyms are `ACTIVE` immediately instead of `PENDING` |
| `MESSAGING_PROVIDER` | `clicktochat` | `clicktochat` \| `whatsappcloud` \| `mock` |
| `WHATSAPP_CLOUD_ENABLED` | `false` | Enable the WhatsApp Cloud adapter |
| `WHATSAPP_CLOUD_TOKEN` | *(empty)* | WhatsApp Cloud API token |
| `WHATSAPP_CLOUD_PHONE_NUMBER_ID` | *(empty)* | WhatsApp Cloud phone-number ID |
| `WHATSAPP_CLOUD_TEMPLATE_RENEWAL_EN` | `gymkhata_renewal_en` | Cloud template name (English) |
| `WHATSAPP_CLOUD_TEMPLATE_RENEWAL_HI` | `gymkhata_renewal_hi` | Cloud template name (Hindi) |
| `PAYMENT_PROVIDER` | `directupi` | `directupi` \| `mock` \| `razorpay` |
| `RAZORPAY_ENABLED` | `false` | Enable the Razorpay adapter (**not production-tested**) |
| `RAZORPAY_KEY_ID` | *(empty)* | Razorpay key ID |
| `RAZORPAY_KEY_SECRET` | *(empty)* | Razorpay key secret |
| `JWT_SECRET` | dev placeholder | HMAC signing key. **No default under the `prod` profile** — the app refuses to start with the dev value. |

## Key settings and their defaults

### Pricing (drives the public `/pricing` page)

| Setting | Default |
|---|---|
| `app.pricing.monthly-price-paise` | `99900` (₹999.00 all-inclusive) |
| `app.pricing.currency` | `INR` |
| `app.pricing.included` | the six included items |
| `app.pricing.not-charged` | "No per-member fees, No per-message fees" |

### Security & tokens

| Setting | Default |
|---|---|
| `app.jwt.access-token-ttl-minutes` | `15` |
| `app.jwt.refresh-token-ttl-days` | `30` |
| `app.jwt.issuer` | `gymkhata` |
| `app.jwt.secret` | development placeholder |
| `app.security.password-reset-ttl-minutes` | `30` |

Refresh tokens rotate on every use; reusing a rotated token revokes the whole
family.

### GST (effective-dated rates, snapshotted onto each invoice line)

| Setting | Default |
|---|---|
| `app.gst.sac` | `999723` |
| `app.gst.rates` | `18%` from 2000-01-01; `5%` from 2025-09-22 |

A gym can switch GST registration on or off in **Settings**. When off, the
invoice is a Bill of Supply with no tax lines.

### UPI

| Setting | Default |
|---|---|
| `app.upi.vpa-regex` | `^[a-zA-Z0-9.\-_]{2,}@[a-zA-Z]{2,}$` |

The gym's own VPA and payee name are set at runtime in **Settings**, not in this
file.

### WhatsApp

| Setting | Default |
|---|---|
| `app.quiet-hours.enabled` | `true` |
| `app.quiet-hours.start` / `.end` | `21:00` / `08:00` (IST) |

Templates are editable per gym at runtime, in English and Hindi, with the
variables `{name} {planName} {expiryDate} {amountDue} {payLink} {gymName}`.

### CORS

| Setting | Default |
|---|---|
| `app.cors-allowed-origins` | `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:8080`, `http://127.0.0.1:8080` |

The Vite dev server runs on `:5173` with `strictPort: true`, so this list must
include `:5173` for the frontend to call the API from a browser.

### Profiles

| Profile | How to activate | Effect |
|---|---|---|
| `dev` (default) | automatic | Local MongoDB, development JWT secret, debug logging |
| `prod` | `--spring.profiles.active=prod` | `MONGODB_URI` and `JWT_SECRET` must come from the environment; stack traces are hidden; `JWT_SECRET` has no default |

### Actuator & docs

| Setting | Default |
|---|---|
| `management.endpoints.web.exposure.include` | `health,info` (`prod`: `health,info`, details never shown) |
| `springdoc.api-docs.path` | `/api-docs` |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` |

## Health & readiness endpoints

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall health — `{"status":"UP"}`. Includes the MongoDB health indicator, so `UP` proves the database is reachable. |
| `GET /api/v1/config/public` | Public platform config + pricing (no auth) — also a good "is the API alive" probe |

## See also

- [`backend/.env.example`](../backend/.env.example) — the annotated variable list
- [`API.md`](API.md) — every endpoint
