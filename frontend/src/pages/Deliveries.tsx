import { useEffect, useState } from 'react';
import { api } from '../api';
import type { DeliveryLog } from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { formatDateTime } from '../utils/format';

export function Deliveries() {
  const [rows, setRows] = useState<DeliveryLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<DeliveryLog | null>(null);

  useEffect(() => {
    api
      .listDeliveries()
      .then(setRows)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;

  return (
    <>
      <div className="card">
        <div className="card-header">
          <h2>Email delivery log</h2>
          <span className="spacer" />
          <span className="small muted">Every attempt, including retries, is recorded.</span>
        </div>
        <div className="card-body tight">
          {rows.length === 0 ? (
            <Empty
              title="No deliveries yet"
              message="Statements emailed by the scheduler or approved manually appear here."
            />
          ) : (
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>Sent at</th>
                    <th>Client</th>
                    <th>Account</th>
                    <th>Statement</th>
                    <th>Recipients</th>
                    <th>Provider</th>
                    <th className="right">Attempt</th>
                    <th>Status</th>
                    <th>Sent by</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id}>
                      <td className="small nowrap">{formatDateTime(row.sentAt)}</td>
                      <td className="small">{row.clientCode ?? '—'}</td>
                      <td className="mono small">{row.accountNumber ?? '—'}</td>
                      <td className="mono small">{row.statementReference ?? '—'}</td>
                      <td className="small" style={{ maxWidth: 260 }}>
                        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{row.recipient}</div>
                      </td>
                      <td className="small">{row.provider}</td>
                      <td className="right">{row.attempt}</td>
                      <td>
                        <Badge>{row.status}</Badge>
                      </td>
                      <td className="small">{row.sentBy}</td>
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
        <Modal title={`Delivery ${detail.id}`} onClose={() => setDetail(null)}>
          <dl className="detail-list">
            <dt>Status</dt>
            <dd>
              <Badge>{detail.status}</Badge>
            </dd>
            <dt>Statement</dt>
            <dd className="mono">{detail.statementReference ?? '—'}</dd>
            <dt>Client / account</dt>
            <dd>
              {detail.clientCode ?? '—'} / <span className="mono">{detail.accountNumber ?? '—'}</span>
            </dd>
            <dt>Recipients</dt>
            <dd>{detail.recipient}</dd>
            <dt>Subject</dt>
            <dd>{detail.subject}</dd>
            <dt>Provider</dt>
            <dd>{detail.provider} (attempt {detail.attempt})</dd>
            <dt>Sent at</dt>
            <dd>{formatDateTime(detail.sentAt)}</dd>
            <dt>Sent by</dt>
            <dd>{detail.sentBy}</dd>
            <dt>Correlation id</dt>
            <dd className="mono small">{detail.correlationId}</dd>
          </dl>

          {detail.errorMessage && (
            <>
              <div className="divider" />
              <div className="alert alert-danger">{detail.errorMessage}</div>
            </>
          )}

          <div className="divider" />
          <div className="field">
            <label>Provider response</label>
            <pre className="json-view">{detail.providerResponse ?? '—'}</pre>
          </div>
        </Modal>
      )}
    </>
  );
}
