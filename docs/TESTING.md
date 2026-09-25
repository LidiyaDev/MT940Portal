# Testing guide

Work through the levels in order. Levels 0–2 need nothing but Node and Maven; levels 3–6
bring in the real infrastructure.

| Level | What you need | What it proves |
|---|---|---|
| 0 | Node only | The UI and the end-to-end business flow |
| 1 | Java 17 + Maven | MT940 output, schedule arithmetic, text handling |
| 2 | + Maven | The real Spring Boot API, generation, scheduling, maker/checker, audit |
| 3 | + Keycloak | Authentication and role enforcement |
| 4 | + Oracle | The production schema |
| 5 | + core banking feed | Real transaction data |
| 6 | + notification endpoint | Real email delivery |

---

## Level 0 — Click through the UI (nothing to install)

The preview is already running. The React app uses an in-memory backend, so the whole flow
works without the Spring Boot service, Oracle or Keycloak.

```
cd frontend && npm install && npm run dev      # http://localhost:5173
```

Sign in as **Maker**, then walk this script:

| Step | Where | Expect |
|---|---|---|
| 1 | Dashboard | 2 clients, 5 accounts, 3 pending approvals, upcoming deliveries |
| 2 | Clients → **+ New client** | Submit → banner says "submitted for approval" and the client **does not** appear in the table |
| 3 | Clients | The new client only appears after a checker approves it |
| 4 | Clients → AMBO001 → Accounts → **+ Add multiple** | Paste two account numbers, one per line → one approval request covering both |
| 5 | Delivery schedules → **+ New schedule** | Set Weekly / Tuesday / 06:45 → queued for approval |
| 6 | Statements → pick client + account + dates → **Preview** | MT940 text appears; check `:20:`, `:25:`, `:28C:`, `:60F:`, `:61:`, `:86:`, `:62F:` |
| 7 | Statements → **Generate & store** | Statement row appears, closing balance = opening − debits + credits |
| 8 | Statements → **Request delivery** | Goes to the approval queue; nothing is emailed yet |
| 9 | Top bar → switch to **Checker** | |
| 10 | Approvals → **Review** on any request | Current vs proposed JSON side by side, plus the change summary |
| 11 | Approvals → **Approve & apply** | The change lands immediately and the record updates |
| 12 | Sign in as Maker, raise a request, then try to approve it as **Maker** | Approve is disabled with "a maker cannot approve their own request" |
| 13 | Audit log | Every step above is listed with actor, action, before/after and outcome |
| 14 | Delivery log | The delivery recorded in step 8→11 shows the recipient, provider and response |

To prove the front end talks to a real backend, set `VITE_USE_MOCK=false` in
`frontend/.env.local` and repeat — the screens are unchanged.

---

## Level 1 — Unit tests

```bash
cd backend
mvn test
```

Three suites:

- **`Mt940GeneratorTest`** — the important one. It renders the bank's reference statement and
  asserts the output **byte for byte**. If this fails, the MT940 no longer matches what the
  bank expects. It also covers credit entries, reference truncation, optional tags
  (`:13D:`, `:90D:`, `:90C:`, `:64:`), the funds-code switch, blank lines and balance
  derivation.
- **`SwiftTextTest`** — amount formatting (`2340` → `2340,00`), balance marks, 16-character
  reference folding, SWIFT character-set sanitisation, `:86:` wrapping, fixed-width padding.
- **`ScheduleCalculatorTest`** — next-fire time for daily/weekly/monthly (including clamping
  31 Feb-style cases) and the period each run covers.

---

## Level 2 — The real backend, no Oracle / Keycloak / core banking

This is where you test the actual Spring Boot API. Two stubs cover the external dependencies:
the mock bank services below stand in for the core banking feed and the email endpoint, H2
replaces Oracle, and an auth bypass replaces Keycloak.

### 2a. Start the stub services

```bash
node tools/mock-bank-services/server.mjs
```

```
transaction feed : POST http://localhost:5468/phiBela/getAmboTransaction
notification     : POST http://localhost:5468/notify
sent messages    : GET  http://localhost:5468/notify/inbox
```

It speaks the same contract as the real feed — `dd-MMM-yy` dates in, the documented envelope
out — and it logs every request so you can confirm the date format the backend sends.

```bash
curl -s -X POST http://localhost:5468/phiBela/getAmboTransaction \
  -H 'Content-Type: application/json' \
  -d '{"accountNumber":"1144355935012","startDate":"01-Sep-26","endDate":"24-Sep-26"}'
```

### 2b. Start the backend

```bash
cd backend
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=" \
    --mt940.security.dev-bypass.enabled=true \
    --mt940.security.dev-bypass.roles=MT940_MAKER,MT940_CHECKER,MT940_ADMIN \
    --mt940.transaction-source.base-url=http://localhost:5468 \
    --mt940.email.provider=mock"
```

Uses H2 in memory (`jdbc:h2:mem:mt940db`, console at `/h2-console`) so no Oracle is needed.
Swagger UI is at <http://localhost:8080/swagger-ui.html>.

### 2c. Exercise the API

```bash
API=http://localhost:8080/api

# Dashboard
curl -s $API/dashboard | jq

# Create a client -> goes to the approval queue, nothing written yet
curl -s -X POST "$API/clients" -H 'Content-Type: application/json' -d '{
  "clientCode":"TEST001","clientName":"Test Client PLC",
  "primaryEmail":"ops@test.example","defaultCurrency":"ETB",
  "timezone":"Africa/Addis_Ababa","status":"ACTIVE"}' | jq

# Queue is not empty
curl -s "$API/approvals?status=PENDING" | jq '.content | length'

# Confirm the client really is NOT live yet
curl -s "$API/clients?search=TEST001" | jq '.content | length'    # -> 0

# Approve it (as a different user - flip the bypass user, or run with the checker role)
curl -s -X POST "$API/approvals/1/approve" \
  -H 'Content-Type: application/json' -d '{"note":"approved"}' | jq

# Now it is live
curl -s "$API/clients?search=TEST001" | jq '.content[0].clientCode'
```

Then accounts, schedules and statements:

```bash
CLIENT=1   # the id returned above

# Two accounts in one request
curl -s -X POST "$API/clients/$CLIENT/accounts/bulk" -H 'Content-Type: application/json' -d '{
  "clientId":'"$CLIENT"',
  "accounts":[{"accountNumber":"1144355935012","currency":"ETB"},
              {"accountNumber":"1144355935013","currency":"ETB"}]}' | jq

# Daily delivery at 07:00 for all accounts
curl -s -X POST "$API/clients/$CLIENT/schedules" -H 'Content-Type: application/json' -d '{
  "frequency":"DAILY","sendTime":"07:00","timezone":"Africa/Addis_Ababa",
  "periodStrategy":"PREVIOUS_PERIOD","enabled":true}' | jq

# Recipient list
curl -s -X POST "$API/clients/$CLIENT/email-config" -H 'Content-Type: application/json' -d '{
  "name":"Ops","toAddresses":"ops@test.example","enabled":true,"isDefault":true}' | jq
```

Approve each of those, then generate:

```bash
# Preview only - renders, stores nothing
curl -s -X POST "$API/statements/preview?accountId=1&periodFrom=2026-09-01&periodTo=2026-09-24" | jq -r '.content'

# Generate and store
curl -s -X POST "$API/statements/generate" -H 'Content-Type: application/json' -d '{
  "accountId":1,"periodFrom":"2026-09-01","periodTo":"2026-09-24","persist":true}' | jq

# Download the file
curl -s -OJ "$API/statements/1/download"

# Request delivery (queued - needs a checker)
curl -s -X POST "$API/statements/send-request" -H 'Content-Type: application/json' -d '{
  "accountId":1,"periodFrom":"2026-09-01","periodTo":"2026-09-24","recipients":[]}' | jq
```

Finally:

```bash
curl -s "$API/audit" | jq '.content[0:5]'      # every action, with before/after
curl -s "$API/deliveries" | jq '.content'      # delivery attempts
```

### 2d. Prove the maker/checker rule

```bash
# Start the backend with ONLY the maker role
--mt940.security.dev-bypass.roles=MT940_MAKER

# Approving must fail with 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$API/approvals/1/approve" \
  -H 'Content-Type: application/json' -d '{}'          # -> 403

# Start it with ONLY the checker role and approve
--mt940.security.dev-bypass.roles=MT940_CHECKER        # -> 200
```

For the "cannot approve your own request" rule, raise a request and approve it while the
bypass user is unchanged — the response is 403 with
`A maker cannot approve their own request`.

### 2e. Watch the scheduler actually deliver

Set the schedule's send time to a minute or two in the future and leave the backend running:

```bash
curl -s -X POST "$API/clients/$CLIENT/schedules" -H 'Content-Type: application/json' -d '{
  "frequency":"DAILY","sendTime":"'"$(date -d '+2 minutes' +%H:%M)"'",
  "timezone":"Africa/Addis_Ababa","periodStrategy":"ROLLING_7","enabled":true}' | jq
```

Approve it, then watch the log — you should see
`Running schedule N …` followed by `Scheduled run delivered M statement(s)`.
Confirm with:

```bash
curl -s "$API/deliveries" | jq '.content[0] | {status, recipient, provider, attempt}'
```

### 2f. Test the REST email provider

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--mt940.email.provider=rest \
    --mt940.email.rest.url=http://localhost:5468/notify"

# after a delivery runs:
curl -s http://localhost:5468/notify/inbox | jq '.[0] | {to, subject, attachments}'
```

That shows the exact payload your real notification service will receive, including the
base64 attachment. Change the contract with `mt940.email.rest.payload-template`.

---

## Level 3 — Real Keycloak

```bash
docker compose up -d keycloak        # http://localhost:8180, admin/admin
```

Follow [docs/KEYCLOAK-SETUP.md](KEYCLOAK-SETUP.md): create the `mt940` realm, the four realm
roles, the `mt940-portal-ui` public client, and two users with different roles.

```bash
export KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/mt940
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--mt940.security.dev-bypass.enabled=false"
```

```bash
cd frontend
printf 'VITE_USE_MOCK=false\nVITE_KEYCLOAK_ENABLED=true\nVITE_KEYCLOAK_URL=http://localhost:8180\n' > .env.local
npm run dev
```

Tests to run:

- No token → `curl -s -o /dev/null -w '%{http_code}\n' $API/clients` → **401**
- Expired/garbage token → **401**
- Valid token, wrong role → maker tries to approve → **403**
- Valid token, right role → **200**

Get a token quickly with the password grant (only enable direct access grants on a
throwaway client):

```bash
TOKEN=$(curl -s -d 'client_id=mt940-portal-ui' -d 'username=maker.user' \
  -d 'password=...' -d 'grant_type=password' \
  http://localhost:8180/realms/mt940/protocol/openid-connect/token | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" $API/clients | jq '.content | length'
```

---

## Level 4 — Real Oracle

```bash
docker compose up -d oracle          # XEPDB1 on localhost:1521, user MT940_APP/mt940

cd backend
export SPRING_PROFILES_ACTIVE=prod
export ORACLE_URL=jdbc:oracle:thin:@//localhost:1521/XEPDB1
export ORACLE_USER=MT940_APP
export ORACLE_PASSWORD=mt940
mvn spring-boot:run
```

What to check:

1. **Flyway applied cleanly** — look for `Successfully applied 1 migration`.
2. **Hibernate validates the mapping** — `ddl-auto: none`, so any mismatch surfaces as a
   schema error rather than silently changing your DDL.
3. **The check constraint works** — try to approve your own request; Oracle should reject the
   row if the application rule were ever bypassed:

   ```sql
   SELECT constraint_name, search_condition
   FROM user_constraints WHERE table_name = 'MT_APPROVAL_REQUEST';
   ```
4. **CLOBs hold the full statement** — `SELECT DBMS_LOB.GETLENGTH(content) FROM mt_statement;`
5. **Sequences** — `SELECT MT940_SEQ.NEXTVAL FROM dual;` after a few inserts.

Then re-run the Level 2c script against Oracle.

---

## Level 5 — The real core banking feed

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --mt940.transaction-source.base-url=http://192.168.12.47:5468"
```

From a machine that can reach it. Then:

```bash
curl -s -X POST "$API/statements/preview?accountId=1&periodFrom=2026-09-01&periodTo=2026-09-24" \
  | jq -r '.content'
```

Check against the real data:

- Does `:25:` match the account you asked for?
- Does `:60F:` equal the `beginning_balance` of the **first** row?
- Does `:62F:` equal the `closing_balance` of the **last** row?
- Is `opening − debits + credits == closing`?
- Is the `:61:` D/C mark right for each row?

If your feed uses a different credit column name, tell me and I'll add the alias —
`credit_amt`, `creditAmt`, `creditAmount` and `credit_amount` are already accepted.

If it returns nothing for the range, the statement falls back to the previous closing
balance — worth confirming that's what your treasury team wants.

---

## Level 6 — The real notification endpoint

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --mt940.email.provider=rest \
  --mt940.email.rest.url=https://your-endpoint/... \
  --mt940.email.rest.auth.enabled=true \
  --mt940.email.rest.auth.header-name=Authorization \
  --mt940.email.rest.auth.token=... \
  --mt940.email.rest.payload-template='{\"to\":\"{{to}}\",\"subject\":\"{{subject}}\",\"body\":\"{{body}}\",\"attachments\":[{\"fileName\":\"{{fileName}}\",\"contentType\":\"{{contentType}}\",\"contentBase64\":\"{{fileBase64}}\"}]}'"
```

Then request a delivery, approve it, and check:

```bash
curl -s "$API/deliveries" | jq '.content[0] | {status, providerResponse, errorMessage}'
```

`providerResponse` holds whatever your endpoint returned, so you can see exactly what it
said. If it disagrees with the template, only the template needs changing.

---

## Acceptance checklist

| Requirement | How to verify |
|---|---|
| Add clients on request | Level 0 step 2, or Level 2c `POST /api/clients` |
| Single + multiple accounts | Level 0 step 4, or `POST /api/clients/{id}/accounts` and `/accounts/bulk` |
| Generate MT940 per account | `POST /api/statements/preview` and `/generate`; compare with the sample file |
| Daily / weekly / monthly at a set time | Level 2e — set the time, watch it fire, check `nextRunAt` moved |
| Email delivery | Level 2f (mock) then Level 6 (real endpoint) |
| Audit log | `GET /api/audit` — every action above is present with before/after |
| Maker / checker | Level 2d — 403 without the role, 403 on self-approval |
| Keycloak | Level 3 — 401 without a token, 403 without the role |
| Oracle | Level 4 — Flyway applied, data persisted across restarts |
| MT940 matches the bank sample | `mvn test` → `Mt940GeneratorTest` |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend will not start, Keycloak errors | JWKS is fetched lazily so this is rare; check `mt940.keycloak.jwk-set-uri`, or enable the dev bypass |
| Preview works, every statement is empty | The feed returned no rows — check `startDate`/`endDate` in the stub's log and the account number |
| Amounts off by a factor of 100 | The feed is sending minor units; adjust in `StatementService.map` |
| `204 No Content` on download | The statement has no `content` — regenerate it |
| Schedule never fires | `nextRunAt` is in the scheduler zone (`mt940.scheduler.zone`), not the schedule's own zone — check both |
| Approval is 403 but you are a checker | You are the requester. Use a second account |
