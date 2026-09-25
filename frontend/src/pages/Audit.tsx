import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import type { AuditLog } from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { formatDateTime } from '../utils/format';

export function Audit() {
  const [rows, setRows] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [outcome, setOutcome] = useState('');
  const [detail, setDetail] = useState<AuditLog | null>(null);

  useEffect(() => {
    api
      .listAudit()
      .then(setRows)
      .finally(() => setLoading(false));
  }, []);

  const actions = useMemo(
    () => Array.from(new Set(rows.map((row) => row.action))).sort(),
    [rows]
  );
  const entityTypes = useMemo(
    () => Array.from(new Set(rows.map((row) => row.entityType ?? ''))).filter(Boolean).sort(),
    [rows]
  );

  const filtered = rows.filter((row) => {
    if (action && row.action !== action) return false;
    if (entityType && row.entityType !== entityType) return false;
    if (outcome && row.outcome !== outcome) return false;
    if (search) {
      const haystack = `${row.actorUsername ?? ''} ${row.description ?? ''} ${row.entityLabel ?? ''}`.toLowerCase();
      if (!haystack.includes(search.toLowerCase())) return false;
    }
    return true;
  });

  if (loading) return <Loading />;

  return (
    <>
      <div className="card">
        <div className="card-header">
          <h2>Audit trail</h2>
          <span className="spacer" />
          <span className="small muted">
            {filtered.length} of {rows.length} entries · append only
          </span>
        </div>
        <div className="card-body">
          <div className="filter-bar">
            <div className="field" style={{ minWidth: 240 }}>
              <label>Search</label>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Actor, description or record…"
              />
            </div>
            <div className="field">
              <label>Action</label>
              <select value={action} onChange={(event) => setAction(event.target.value)}>
                <option value="">All actions</option>
                {actions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Entity</label>
              <select value={entityType} onChange={(event) => setEntityType(event.target.value)}>
                <option value="">All entities</option>
                {entityTypes.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label>Outcome</label>
              <select value={outcome} onChange={(event) => setOutcome(event.target.value)}>
                <option value="">Any</option>
                <option value="SUCCESS">Success</option>
                <option value="FAILURE">Failure</option>
              </select>
            </div>
            <button
              className="btn"
              onClick={() => {
                setSearch('');
                setAction('');
                setEntityType('');
                setOutcome('');
              }}
            >
              Clear
            </button>
          </div>
        </div>

        <div className="card-body tight">
          {filtered.length === 0 ? (
            <Empty title="No matching entries" message="Loosen the filters to see more of the trail." />
          ) : (
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Description</th>
                    <th>Source IP</th>
                    <th>Outcome</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((row) => (
                    <tr key={row.id}>
                      <td className="small nowrap">{formatDateTime(row.eventTime)}</td>
                      <td className="small">
                        {row.actorUsername}
                        <div className="small faint">{row.actorName}</div>
                      </td>
                      <td>
                        <strong className="small">{row.action}</strong>
                      </td>
                      <td className="small">
                        {row.entityType}
                        {row.entityId && <div className="small faint mono">{row.entityId}</div>}
                      </td>
                      <td className="small" style={{ maxWidth: 380 }}>
                        {row.description}
                      </td>
                      <td className="small mono">{row.ipAddress ?? '—'}</td>
                      <td>
                        <Badge tone={row.outcome === 'SUCCESS' ? 'ok' : 'danger'}>{row.outcome}</Badge>
                      </td>
                      <td className="right">
                        <button className="btn btn-sm" onClick={() => setDetail(row)}>
                          Detail
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {detail && (
        <Modal wide title={detail.action} subtitle={formatDateTime(detail.eventTime)} onClose={() => setDetail(null)}>
          <dl className="detail-list">
            <dt>Actor</dt>
            <dd>
              {detail.actorUsername} ({detail.actorName})
            </dd>
            <dt>Roles</dt>
            <dd className="small">{detail.actorRoles || '—'}</dd>
            <dt>Entity</dt>
            <dd>
              {detail.entityType} {detail.entityId ? `#${detail.entityId}` : ''}
            </dd>
            <dt>Description</dt>
            <dd>{detail.description}</dd>
            <dt>Outcome</dt>
            <dd>
              <Badge tone={detail.outcome === 'SUCCESS' ? 'ok' : 'danger'}>{detail.outcome}</Badge>
            </dd>
            <dt>IP address</dt>
            <dd className="mono small">{detail.ipAddress ?? '—'}</dd>
            <dt>Session</dt>
            <dd className="mono small">{detail.sessionId ?? '—'}</dd>
            <dt>Correlation id</dt>
            <dd className="mono small">{detail.correlationId ?? '—'}</dd>
          </dl>

          {detail.oldValue && (
            <>
              <div className="divider" />
              <div className="field">
                <label>Before</label>
                <pre className="json-view">{detail.oldValue}</pre>
              </div>
            </>
          )}
          {detail.newValue && (
            <div className="field">
              <label>After</label>
              <pre className="json-view">{detail.newValue}</pre>
            </div>
          )}
          {detail.detail && (
            <div className="field">
              <label>Extra detail</label>
              <pre className="json-view">{detail.detail}</pre>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
