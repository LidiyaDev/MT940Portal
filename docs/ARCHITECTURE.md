# Architecture

## Modules

```
backend/   Spring Boot 3.3 / Java 17 / Maven   → mt940-portal.jar
frontend/  React 18 + Vite + TypeScript
```

The backend is a stateless OAuth2 **resource server**: it validates Keycloak bearer tokens and
never redirects to a login page. The React app owns the login flow (Keycloak JS) and sends
`Authorization: Bearer …` on every call.

## Data model

```
MT_CLIENT ─1─────*─ MT_ACCOUNT ─1─────*─ MT_STATEMENT ─1──*─ MT_STATEMENT_TXN
    │                    │                    │
    │                    │                    └────*── MT_DELIVERY_LOG
    ├──*─ MT_DELIVERY_SCHEDULE ─────────────────────────┘
    └──*─ MT_EMAIL_CONFIG

MT_APPROVAL_REQUEST   maker/checker queue (entity type, operation, proposed payload)
MT_AUDIT_LOG          append-only activity trail
```

`MT_APPROVAL_REQUEST` and `MT_AUDIT_LOG` are deliberately not child entities of anything — a
request can target any entity type, and audit rows must survive the deletion of the record
they describe.

## Request flow

### Read

```
React → GET /api/clients → ClientService.list() → MT_CLIENT
```

### Write (maker)

```
React → POST /api/clients
      → ClientService.submitCreate()
      → validates (unique code, …)
      → ApprovalRequestFactory.submit()      writes MT_APPROVAL_REQUEST (PENDING)
      → AuditService.record(CLIENT_CREATE)
      → 202 Accepted { approvalId }
```

Nothing is written to `MT_CLIENT` at this point. The UI shows the pending change on the
client row so a second edit cannot be queued on top of it.

### Write (checker)

```
React → POST /api/approvals/{id}/approve
      → ApprovalService.approve()
      → status PENDING?  ·  reviewer ≠ requester?  ·  reviewer has checker role?
      → ApprovalApplier for the entity type → ClientService.applyCreate()
      → MT_CLIENT row written
      → AuditService.record(CLIENT_CREATE) with before/after
```

Appliers (`service/approval/*ApprovalApplier`) depend on the entity services but never on
`ApprovalService`, so there is no cycle back into the queue.

## Statement generation

```
StatementService.prepare()
  ├─ resolve settings: global SwiftProperties.copyWith(client overrides)
  ├─ TransactionServiceClient.fetchTransactions(account, from, to)
  │     POST {accountNumber, startDate "01-Sep-26", endDate "24-Sep-26"}
  ├─ map each row → Mt940Transaction (D/C mark, amount, 16x reference, type code)
  ├─ opening = first row's beginning_balance
  ├─ closing = last row's closing_balance
  ├─ statement number = max(account.seed, account.last) + 1
  └─ Mt940Generator.render()
```

The rendered text is stored in `MT_STATEMENT.CONTENT` (CLOB) **and** written to
`mt940.storage.base-dir` as `{account}_{yyyyMMdd}.txt`. The database copy is authoritative —
re-downloads and re-sends read from it even if the file is cleaned up.

## Scheduling

`DeliveryJob` polls every `mt940.scheduler.poll-interval-ms` (60 s by default):

```
findDueSchedules(now)                       enabled AND next_run_at <= now
  └─ for each schedule
       ├─ period = ScheduleCalculator.period(schedule, scheduledFor)
       ├─ accounts = schedule.account ?: all active accounts of the client
       └─ for each account (failures isolated per account)
            ├─ skip if no transactions and !includeZeroTransactionStatements
            ├─ StatementService.generate()
            ├─ StatementDeliveryService.send()
            └─ DeliveryLog written (SENT / FAILED + provider response)
     finally markSent(schedule, runAt) → next_run_at = next occurrence
```

A schedule whose window was missed by more than `misfire-threshold-minutes` is rescheduled
rather than replayed, and a run in progress prevents the next tick from overlapping it.

Each schedule carries its own timezone, so `07:00` means 07:00 for the client.

## Email delivery

`EmailProvider` has two implementations, selected by `mt940.email.provider`:

- `LoggingEmailProvider` (default, `mock`) — records what would have been sent.
- `RestEmailProvider` (`rest`) — POSTs to `mt940.email.rest.url` using the payload template in
  `mt940.email.rest.payload-template`.

The template resolves `{{to}}`, `{{cc}}`, `{{bcc}}`, `{{subject}}`, `{{body}}`,
`{{fileName}}`, `{{contentType}}`, `{{fileBase64}}`, `{{correlationId}}` and any client
variable, so adapting to the bank's real notification service is a configuration change:

```yaml
mt940:
  email:
    provider: rest
    rest:
      url: https://notify.bank.internal/v1/messages
      auth: { enabled: true, header-name: X-Api-Key, token: ${NOTIFY_API_KEY} }
      payload-template: >
        {"to":"{{to}}","subject":"{{subject}}","text":"{{body}}",
         "attachments":[{"name":"{{fileName}}","mime":"{{contentType}}","data":"{{fileBase64}}"}]}
```

Failures retry `mt940.email.retry.max-attempts` times with linear backoff; every attempt is
logged to `MT_DELIVERY_LOG`.

## Audit trail

`AuditService.record()` runs in its own transaction (`REQUIRES_NEW`), so an audit entry
survives the rollback of the business transaction that triggered it — a failed change is
still traceable. Each row captures actor, roles, action, entity, before/after JSON, outcome,
IP address, user agent, session id and a correlation id that is also returned as the
`X-Correlation-Id` response header and passed to the notification service.

`AuditAspect` additionally wraps anything annotated `@Auditable` (for example statement
downloads) and records the outcome and duration.

## Testing

```bash
cd backend && mvn test
```

`Mt940GeneratorTest` pins the generator to the bank's reference statement, so any drift in
the output format fails the build.
