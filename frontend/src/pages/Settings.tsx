import { USE_MOCK } from '../api';

const SWIFT_DEFAULTS: Array<[string, string, string]> = [
  ['Sender LT address', 'mt940.swift.sender-lt-address', 'DASHETAXXXXX'],
  ['Receiver LT address', 'mt940.swift.receiver-lt-address', 'RECVETAAXXXX'],
  ['Session number', 'mt940.swift.session-number', '0001'],
  ['ISN strategy', 'mt940.swift.isn-strategy', 'MIRROR_STATEMENT_NUMBER'],
  ['Reference prefix (:20:)', 'mt940.swift.reference-prefix', 'STMT'],
  ['Funds code', 'mt940.swift.funds-code-strategy', 'NONE'],
  ['Transaction type (:61:)', 'mt940.swift.default-transaction-type', 'NTRF'],
  ['Opening balance tag', 'mt940.swift.opening-balance-tag', 'F'],
  ['Closing balance tag', 'mt940.swift.closing-balance-tag', 'F'],
  ['Line separator', 'mt940.swift.line-separator', 'LF'],
  ['Blank line between tags', 'mt940.swift.blank-line-between-tags', 'false'],
];

const INTEGRATIONS: Array<[string, string, string]> = [
  ['Transaction endpoint', 'mt940.transaction-source.base-url + .path', 'http://192.168.12.47:5468/phiBela/getAmboTransaction'],
  ['Endpoint date format', 'mt940.transaction-source.date-pattern', 'dd-MMM-yy'],
  ['Email provider', 'mt940.email.provider', 'mock | rest'],
  ['Email endpoint', 'mt940.email.rest.url', 'set from the bank notification service'],
  ['Retry attempts / backoff', 'mt940.email.retry.*', '3 / 5000 ms'],
  ['Scheduler zone', 'mt940.scheduler.zone', 'Africa/Addis_Ababa'],
  ['Scheduler poll interval', 'mt940.scheduler.poll-interval-ms', '60000'],
];

const SECURITY: Array<[string, string, string]> = [
  ['Keycloak issuer', 'spring.security.oauth2.resourceserver.jwt.issuer-uri', 'http://localhost:8180/realms/mt940'],
  ['Maker role', 'mt940.security.maker-role', 'MT940_MAKER'],
  ['Checker role', 'mt940.security.checker-role', 'MT940_CHECKER'],
  ['Admin role', 'mt940.security.admin-role', 'MT940_ADMIN'],
  ['Viewer role', 'mt940.security.viewer-role', 'MT940_VIEWER'],
];

export function Settings() {
  return (
    <>
      <div className="alert alert-info">
        These values are read from the backend configuration. Change them in{' '}
        <code>application.yml</code> or as environment variables and restart the service — the portal
        deliberately does not let operators edit SWIFT settings from the UI without a checker.
      </div>

      <div className="card">
        <div className="card-header">
          <h2>SWIFT MT940 rendering</h2>
          <span className="spacer" />
          <span className="small muted">Defaults; each client can override them.</span>
        </div>
        <div className="card-body tight">
          <div className="table-scroll">
            <table className="data">
              <thead>
                <tr>
                  <th>Setting</th>
                  <th>Property</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {SWIFT_DEFAULTS.map(([label, property, value]) => (
                  <tr key={property}>
                    <td>{label}</td>
                    <td className="mono small muted">{property}</td>
                    <td className="mono">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2>Integrations &amp; scheduling</h2>
        </div>
        <div className="card-body tight">
          <div className="table-scroll">
            <table className="data">
              <thead>
                <tr>
                  <th>Setting</th>
                  <th>Property</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {INTEGRATIONS.map(([label, property, value]) => (
                  <tr key={property}>
                    <td>{label}</td>
                    <td className="mono small muted">{property}</td>
                    <td className="mono">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2>Authentication &amp; roles</h2>
        </div>
        <div className="card-body tight">
          <div className="table-scroll">
            <table className="data">
              <thead>
                <tr>
                  <th>Setting</th>
                  <th>Property</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {SECURITY.map(([label, property, value]) => (
                  <tr key={property}>
                    <td>{label}</td>
                    <td className="mono small muted">{property}</td>
                    <td className="mono">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2>Reference statement format</h2>
        </div>
        <div className="card-body stack">
          <p className="small muted" style={{ margin: 0 }}>
            The generator reproduces the layout of the sample statement supplied by the bank:
          </p>
          <pre className="code-block">{`{1:F01DASHETAXXXXX0001020719}{2:I940RECVETAAXXXXN}{4:
:20:STMT260923935012
:25:1144355935012
:28C:20719/1
:60F:C260923ETB327060,15
:61:2609230923D2340,00NTRF879FXSA262660001
:86:CASH FCY BOUGHT AND SOLD
:62F:C260923ETB324720,15
-}`}</pre>
          <div className="kv-note">
            Currently rendering against <strong>{USE_MOCK ? 'in-memory demonstration data' : 'the live backend'}</strong>.
            Set <code>VITE_USE_MOCK=false</code> to point the UI at Spring Boot.
          </div>
        </div>
      </div>
    </>
  );
}
