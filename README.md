# MT940 Statement Portal

Admin portal that lets the bank onboard corporate clients, attach their accounts, generate
SWIFT **MT940** statements from the core banking transaction feed, and email those statements
to clients on a daily, weekly or monthly schedule — with maker/checker approval on every
change and a complete audit trail.

```
React 18 + Vite  ──▶  Spring Boot 3.3 (Java 17)  ──▶  Oracle
      │                        │
      │                        ├──▶ Core banking transaction endpoint
      │                        └──▶ Notification / email endpoint
      └── Keycloak (OIDC, realm roles) for authentication
```

## What it does

| Requirement | Where it lives |
|---|---|
| Add clients on request | Clients screen — create is queued for approval |
| Add single or multiple accounts per client | Client → Accounts tab, one-at-a-time or bulk paste |
| Generate MT940 per account | Statements screen — preview, then generate and store |
| Email daily / weekly / monthly at a configured time | Client → Delivery schedules tab, backed by `DeliveryJob` |
| Pluggable email endpoint | `mt940.email.*` properties, no code change needed |
| Audit log of every activity | `MT_AUDIT_LOG`, written via `AuditService` + `@Auditable` |
| Maker / checker | `MT_APPROVAL_REQUEST` — nothing is written until a checker approves |
| Keycloak authentication | OAuth2 resource server, realm roles `MT940_MAKER` / `MT940_CHECKER` / `MT940_ADMIN` / `MT940_VIEWER` |
| React front end, Spring Boot back end, Oracle | `frontend/` · `backend/` · `db/migration` |

## Reference statement

The generator reproduces the sample statement supplied by the bank, byte for byte. This is
pinned by `Mt940GeneratorTest`:

```
{1:F01DASHETAXXXXX0001020719}{2:I940RECVETAAXXXXN}{4:
:20:STMT260923935012
:25:1144355935012
:28C:20719/1
:60F:C260923ETB327060,15
:61:2609230923D2340,00NTRF879FXSA262660001
:86:CASH FCY BOUGHT AND SOLD
:62F:C260923ETB324720,15
-}
```

See [docs/MT940-FORMAT.md](docs/MT940-FORMAT.md) for the field-by-field rules that were
derived from it.

## Quick start

### Front end only (no backend required)

The React app ships with an in-memory demonstration backend so you can walk the whole flow
without Oracle, Keycloak or the core banking service:

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

The login screen lets you switch between the **maker**, **checker**, **admin** and **read
only** personas — switch to *checker* to approve what the maker raised.

### Full stack

```bash
# 1. Infrastructure: Keycloak + Oracle XE
docker compose up -d

# 2. Backend (dev profile, H2 in-memory)
cd backend
mvn spring-boot:run

# 3. Frontend pointing at the real API
cd frontend
echo "VITE_USE_MOCK=false" > .env.local
npm run dev
```

### Backend against Oracle

```bash
cd backend
export SPRING_PROFILES_ACTIVE=prod
export ORACLE_URL=jdbc:oracle:thin:@//localhost:1521/XEPDB1
export ORACLE_USER=MT940_APP
export ORACLE_PASSWORD=...
mvn spring-boot:run
```

Flyway creates the schema from `backend/src/main/resources/db/migration/V1__init.sql` on
first boot; Hibernate runs with `ddl-auto: none` so the schema stays owned by Flyway.

## Configuration

Everything environment-specific is a property. The ones you are most likely to change:

| Property | Default | Purpose |
|---|---|---|
| `mt940.transaction-source.base-url` / `.path` | `http://192.168.12.47:5468` / `/phiBela/getAmboTransaction` | Core banking transaction feed |
| `mt940.transaction-source.date-pattern` | `dd-MMM-yy` | Date format the feed expects (`01-Sep-26`) |
| `mt940.email.provider` | `mock` | `mock` logs the message, `rest` posts to `mt940.email.rest.url` |
| `mt940.email.rest.url` | – | Your notification endpoint |
| `mt940.swift.sender-lt-address` | `DASHETAXXXXX` | 12 character sender LT address in `{1:}` |
| `mt940.swift.receiver-lt-address` | `RECVETAAXXXX` | 12 character receiver LT address in `{2:}` |
| `mt940.swift.blank-line-between-tags` | `false` | Set `true` if your downstream consumer expects blank lines |
| `mt940.security.*-role` | `MT940_MAKER` … | Keycloak realm role names |
| `mt940.scheduler.zone` | `Africa/Addis_Ababa` | Zone used to interpret each schedule's send time |

Full list in `backend/src/main/resources/application.yml` and on the in-app **Settings** page.

## Maker / checker

Every mutating operation on clients, accounts, delivery schedules and recipient
configuration — plus any manual statement delivery — is stored as a pending
`MT_APPROVAL_REQUEST` instead of being applied. A user with the **checker** role reviews the
side-by-side diff and either approves (the change is applied immediately) or rejects it.

Two rules are enforced in `ApprovalService` **and** by a database check constraint
(`CK_MT_APPR_SELF`):

1. only a checker (or admin) may decide, and
2. **the person who raised the request can never approve it**.

## Repository layout

```
backend/    Spring Boot service (Maven, Java 17)
frontend/   React + Vite + TypeScript UI
docs/       Architecture, MT940 format, Keycloak setup
```

Further reading:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — data model, request flow, scheduling
- [docs/MT940-FORMAT.md](docs/MT940-FORMAT.md) — tag layout and per-client overrides
- [docs/KEYCLOAK-SETUP.md](docs/KEYCLOAK-SETUP.md) — realm, client and role configuration
