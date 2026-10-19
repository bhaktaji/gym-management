# ASSUMPTIONS.md

Decisions made where the spec was silent, plus environment-driven choices.

## Environment
1. **JDK selection:** The PATH `java` is a JRE 8 (below the JDK 17 minimum), but a full
   Microsoft OpenJDK 21.0.12.1 is installed at `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\`.
   All build/run commands set `JAVA_HOME` to that JDK per-command. The global PATH is never modified.
2. **MongoDB:** A MongoDB 9.0.2 server is already running as a Windows service on
   `localhost:27017`. It is used as-is (no second instance started). Replica-set detection is
   performed via the Java driver at backend bring-up; the app is designed for standalone
   (no multi-document transactions) regardless of the result.
3. **mongosh is absent.** DB introspection/verification is done through the MongoDB Java driver
   (small verification programs run via `mvn` exec or test-scoped code), not mongosh.
4. **No browser is installed** (no Chrome/Edge). Browser-based UI verification is SKIPPED;
   the frontend is verified by build + type-check + unit tests only. This is recorded in TEST_REPORT.md.
5. **Spring Boot version:** 3.5.16 (latest stable 3.x line, compatible with JDK 21). Spring Boot
   4.x exists but 3.5.x is chosen for stability.

## Product / domain decisions
6. **Working name** `GymKhata` is kept in one config constant (`app.name` in `application.yml`,
   exposed via `/api/v1/config/public`).
7. **SaaS pricing:** one flat all-in monthly price of **₹999/month** (GST-inclusive), shown on the
   public `/pricing` page from config. No per-member or per-message fees. The Super Admin tracks
   each gym's manual subscription valid-until date.
8. **Self-signup** creates the gym in `PENDING` state plus its single `GYM_OWNER`; the gym becomes
   `ACTIVE` when the Super Admin activates it (or immediately if config flag
   `app.signup.auto-activate=true`, default false). Suspended gyms get `403 ACCOUNT_SUSPENDED`.
9. **Password reset** for gym owners is token-based: Super Admin triggers a reset token (stored
   hashed, 30-min expiry); owner sets a new password with it. No email/SMS provider exists in this
   environment, so the token is returned in the admin API response (labelled as a stand-in for the
   delivery channel).
10. **Invoice prefix** default `GK` (configurable per gym, max 3 chars, full number ≤ 16 chars).
11. **Credit notes** use series prefix `CN` per gym per financial year, same gapless counter mechanism.
12. **Place of supply:** gym state and member state are captured; IGST applies when they differ,
    CGST+SGST when they match. Both states are editable per gym; member state optional (defaults to
    gym state).
13. **UPI VPA regex:** `^[a-zA-Z0-9.\-_]{2,}@[a-zA-Z]{2,}$` (NPCI-style handle check).
14. **UTR:** exactly 12 digits, unique per gym (index-enforced).
15. **Quiet hours** 21:00–08:00 IST: the outbox still lists messages but marks them
    `QUIET_HOURS` with the scheduled send window; sending is allowed manually (owner's choice).
16. **DPDP erase:** member is anonymised (name → "Erased Member", phone/email removed, consent
    timestamps kept); invoices and payments are retained with the anonymised member reference.
17. **Audit log** records actor (owner or super-admin), action, entity, entityId, timestamp (UTC),
    and a masked phone when present (e.g. `+91*****1234`).
18. **Refresh tokens** are stored hashed in MongoDB with rotation: every refresh issues a new
    token and invalidates the old one; reuse of an already-rotated token revokes the whole family.
19. **Idempotency:** payment creation by UTR is idempotent per gym (unique index); invoice number
    allocation uses `findOneAndUpdate` `$inc` upsert; on invoice insert failure a VOID invoice is
    written with the taken number to keep the series gapless.
20. **CSV import** supports members and payments; row-level errors are collected and returned with
    a per-row report; valid rows are committed.
21. **Timezone:** all business dates use `Asia/Kolkata` via an injectable `Clock` bean fixed to IST;
    instants stored in UTC.
22. **Reports** are computed with MongoDB aggregation pipelines (revenue, expiries, check-ins,
    plan-wise members) and are read-only.
