# Keycloak setup

The backend only validates tokens; all user and role administration happens in Keycloak.

## 1. Realm

Create a realm named `mt940` (or point `KEYCLOAK_ISSUER_URI` at your existing realm).

## 2. Realm roles

Create four realm roles — the names are configurable via `mt940.security.*`:

| Role | Can do |
|---|---|
| `MT940_MAKER` | Raise changes to clients, accounts, schedules, recipients; request manual delivery |
| `MT940_CHECKER` | Approve or reject what a maker raised |
| `MT940_ADMIN` | Both maker and checker. Intended for break-glass use |
| `MT940_VIEWER` | Read everything, change nothing |

Realm roles can also be replaced by client roles under `resource_access` — the converter
(`KeycloakJwtAuthenticationConverter`) collects both.

## 3. Client for the UI

Create a **public** client `mt940-portal-ui`:

- Standard flow: on
- Direct access grants: off
- Valid redirect URIs: `http://localhost:5173/*`
- Web origins: `http://localhost:5173`
- PKCE: S256

No client is needed for the backend — it is a bearer-only resource server.

## 4. Users

Assign each operator at least one of the four roles. Remember the maker/checker rule:
**the person who raises a request cannot approve it**, so you need at least two people (or
two accounts) to complete a change end to end.

## 5. Configuration

```bash
export KEYCLOAK_ISSUER_URI=https://keycloak.bank.internal/realms/mt940
export FRONTEND_ORIGIN=https://mt940.bank.internal
```

```yaml
# backend/src/main/resources/application.yml
spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI}
mt940.cors.allowed-origins: ${FRONTEND_ORIGIN}
```

```bash
# frontend/.env.local
VITE_KEYCLOAK_ENABLED=true
VITE_KEYCLOAK_URL=https://keycloak.bank.internal
VITE_KEYCLOAK_REALM=mt940
VITE_KEYCLOAK_CLIENT_ID=mt940-portal-ui
VITE_USE_MOCK=false
```

The JWKS keys are fetched lazily on the first token validation, so the service still starts
when Keycloak is briefly unavailable. If Keycloak sits behind a reverse proxy whose advertised
issuer host is not reachable from the backend, set `mt940.keycloak.jwk-set-uri` explicitly.

## 6. Running without Keycloak

For local development set:

```yaml
mt940.security.dev-bypass.enabled: true
mt940.security.dev-bypass.roles: MT940_MAKER,MT940_CHECKER
```

Every request is then authenticated as a fixed operator. **This is hard-disabled in the
`prod` profile** and must never be switched on outside a development machine.
