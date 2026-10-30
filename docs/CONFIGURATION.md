# Configuration Reference

All settings have working development defaults in `backend/src/main/resources/application.yml` and can be overridden via environment variables. Under `--spring.profiles.active=prod` (`application-prod.yml`), `ProdConfigValidator` enforces fail-fast startup validation.

| Variable | Dev Default | Prod Requirement | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` | Active Spring profile (`dev`, `demo`, `prod`) |
| `MONGODB_URI` | `mongodb://localhost:27017/gymkhata_dev` | **Required** (`mongodb+srv://...`) | MongoDB connection string |
| `SERVER_PORT` / `PORT` | `8080` | `8080` | HTTP server port |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | `https://...` | Base URL encoded into printable QR posters |
| `JWT_SECRET` | dev placeholder | **Required** (>= 32 bytes, non-default) | HMAC-SHA256 signing secret; fails fast in `prod` if weak or default |
| `SUPER_ADMIN_PHONE` | `+919000000001` | **Required** (E.164) | Seeded platform admin phone |
| `SUPER_ADMIN_PASSWORD` | `ChangeMe@123` | **Required** (must not be `ChangeMe@123` or `DemoAdmin@2026`) | Seeded platform admin password |
| `SUPER_ADMIN_NAME` | `Platform Admin` | `Platform Admin` | Seeded platform admin display name |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | **Required** (no `*` or `localhost`) | Comma-separated allowed browser origins |
| `RATE_LIMIT_ENABLED` | `true` | `true` | Enables sliding-window IP rate limiter (`RateLimitFilter`) |
| `RATE_LIMIT_RPM` | `60` | `30` | Max requests per minute per client IP on auth & public UTR endpoints |
| `SWAGGER_ENABLED` | `true` | `false` | Enables `/swagger-ui.html` and `/api-docs` |
| `SIGNUP_AUTO_ACTIVATE` | `false` | `false` | `true` → self-signed-up gyms start `ACTIVE` instead of `PENDING` |
| `MESSAGING_PROVIDER` | `clicktochat` | `clicktochat` | `clicktochat` (production-ready `wa.me`) \| `whatsappcloud` (Coming Soon) \| `mock` |
| `PAYMENT_PROVIDER` | `directupi` | `directupi` | `directupi` (production-ready zero-MDR UPI QR + UTR) \| `razorpay` (Coming Soon) \| `mock` |

## Proxy & Forwarded Headers (`application.yml` & `application-prod.yml`)
- `server.forward-headers-strategy: framework` with `server.tomcat.remoteip.internal-proxies` restricted to loopback, RFC1918 private ranges, and Cloud Run internal proxies (`169.254.x.x`, `10.x.x.x`, `172.16-31.x.x`, `192.168.x.x`).
- `RateLimitFilter.extractClientIp` only honours `X-Forwarded-For` when the immediate peer is a trusted proxy and walks right-to-left to select the client IP appended by the proxy, preventing header spoofing.

## Profiles
- `dev`: Local MongoDB (`gymkhata_dev`), console logging.
- `demo`: Seeds 3 Indian demo gyms (`BAJRANG-LKO` with `attendanceEnabled=true`; `IRONCORE-IND` and `SURYA-JPR` with `attendanceEnabled=false`). Refuses to run if `prod` profile is active or if the database name does not contain `demo` or `dev`.
- `prod`: Enforces `ProdConfigValidator`, structured ECS JSON logging to `stdout`, hides stack traces, disables Swagger by default, and sets `RATE_LIMIT_RPM=30`.
