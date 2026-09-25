import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import type { ApprovalRequest, ApprovalStatus } from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { ErrorBox } from '../components/ErrorBox';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { useAuth, usePermissions } from '../auth/AuthContext';
import { formatDateTime, relativeTime } from '../utils/format';

export function Approvals() {
  const [rows, setRows] = useState<ApprovalRequest[]>([]);
  const [tab, setTab] = useState<'PENDING' | 'HISTORY'>('PENDING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<ApprovalRequest | null>(null);
  const [note, setNote] = useState('');
  const { identity, canCheck } = usePermissions();
  const { identity: authIdentity } = useAuth();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data =
        tab === 'PENDING' ? await api.listApprovals('PENDING') : await api.listApprovals();
      setRows(data.filter((row) => (tab === 'PENDING' ? row.status === 'PENDING' : row.status !== 'PENDING')));
      setError('');
    } catch (err: unknown) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    void load();
  }, [load]);

  const decide = async (action: 'approve' | 'reject' | 'cancel') => {
    if (!selected) return;
    setError('');
    try {
      if (action === 'approve') await api.approve(selected.id, note);
      if (action === 'reject') await api.reject(selected.id, note);
      if (action === 'cancel') await api.cancel(selected.id, note);
      setNotice(
        action === 'approve'
          ? 'Request approved and the change has been applied.'
          : action === 'reject'
            ? 'Request rejected.'
            : 'Request cancelled.'
      );
      setSelected(null);
      setNote('');
      await load();
    } catch (err: unknown) {
      setError((err as Error).message);
    }
  };

  const isOwn = (row: ApprovalRequest) =>
    row.requestedBy === (authIdentity?.username ?? identity?.username);

  return (
    <>
      {notice && <div className="alert alert-ok">{notice}</div>}
      <ErrorBox message={error} />

      {!canCheck && (
        <div className="alert alert-info">
          You are signed in as <strong>{identity?.username}</strong> without the checker role, so you can
          raise requests but cannot approve them. Switch to the checker persona in the top bar to see the
          approval path.
        </div>
      )}

      <div className="card">
        <div className="tabs">
          <button className={`tab${tab === 'PENDING' ? ' active' : ''}`} onClick={() => setTab('PENDING')}>
            Pending ({rows.filter((r) => r.status === 'PENDING').length})
          </button>
          <button className={`tab${tab === 'HISTORY' ? ' active' : ''}`} onClick={() => setTab('HISTORY')}>
            History
          </button>
        </div>

        <div className="card-body tight">
          {loading ? (
            <Loading />
          ) : rows.length === 0 ? (
            <Empty
              title={tab === 'PENDING' ? 'Nothing waiting for review' : 'No decisions recorded yet'}
              message={
                tab === 'PENDING'
                  ? 'Any change made by a maker shows up here until a checker approves or rejects it.'
                  : 'Approved, rejected and cancelled requests are listed here.'
              }
            />
          ) : (
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Entity</th>
                    <th>Operation</th>
                    <th>Target</th>
                    <th>Requested by</th>
                    <th>When</th>
                    <th>Reviewed by</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id}>
                      <td className="mono small">{row.id}</td>
                      <td className="small">{row.entityType.replace('_', ' ').toLowerCase()}</td>
                      <td>
                        <Badge>{row.operation}</Badge>
                      </td>
                      <td>
                        <strong>{row.entityLabel}</strong>
                        {row.diffSummary && (
                          <div className="small muted">{row.diffSummary}</div>
                        )}
                      </td>
                      <td className="small">{row.requestedBy}</td>
                      <td className="small nowrap">
                        {relativeTime(row.requestedAt)}
                        <div className="small faint">{formatDateTime(row.requestedAt)}</div>
                      </td>
                      <td className="small">{row.reviewedBy ?? '—'}</td>
                      <td>
                        <Badge>{row.status}</Badge>
                      </td>
                      <td className="right">
                        <button className="btn btn-sm" onClick={() => setSelected(row)}>
                          Review
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

      {selected && (
        <Modal
          wide
          title={`${selected.operation} ${selected.entityType.replace('_', ' ').toLowerCase()} — ${selected.entityLabel}`}
          subtitle={`Raised by ${selected.requestedBy} ${relativeTime(selected.requestedAt)}`}
          onClose={() => {
            setSelected(null);
            setNote('');
          }}
          footer={
            selected.status === 'PENDING' ? (
              <>
                <button
                  className="btn"
                  onClick={() => {
                    setSelected(null);
                    setNote('');
                  }}
                >
                  Close
                </button>
                <button
                  className="btn btn-danger"
                  disabled={!canCheck}
                  title={isOwn(selected) ? 'A maker cannot decide on their own request' : undefined}
                  onClick={() => decide('reject')}
                >
                  Reject
                </button>
                <button
                  className="btn btn-success"
                  disabled={!canCheck}
                  title={isOwn(selected) ? 'A maker cannot approve their own request' : undefined}
                  onClick={() => decide('approve')}
                >
                  Approve &amp; apply
                </button>
              </>
            ) : (
              <button className="btn" onClick={() => setSelected(null)}>
                Close
              </button>
            )
          }
        >
          {selected.requestReason && (
            <div className="alert alert-info">
              <strong>Reason given:</strong> {selected.requestReason}
            </div>
          )}

          {selected.diffSummary && (
            <div className="kv-note" style={{ marginBottom: 14 }}>
              <strong>Change summary:</strong> {selected.diffSummary}
            </div>
          )}

          {selected.status === 'PENDING' && isOwn(selected) && (
            <div className="alert alert-warn" style={{ marginBottom: 14 }}>
              You raised this request. Maker-checker rules stop the same person from approving their own
              work, so switch to the checker persona to complete the review.
            </div>
          )}

          <div className="diff-grid">
            <div>
              <div className="small muted" style={{ marginBottom: 6 }}>
                Current state
              </div>
              <pre className="json-view">{selected.currentJson ?? '— new record —'}</pre>
            </div>
            <div>
              <div className="small muted" style={{ marginBottom: 6 }}>
                Proposed state
              </div>
              <pre className="json-view">{selected.payloadJson ?? '—'}</pre>
            </div>
          </div>

          {selected.status === 'PENDING' && (
            <div className="field" style={{ marginTop: 16 }}>
              <label>Review note (optional)</label>
              <textarea
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder="Recorded in the audit trail alongside the decision."
              />
            </div>
          )}

          {selected.status !== 'PENDING' && (
            <dl className="detail-list" style={{ marginTop: 16 }}>
              <dt>Decision</dt>
              <dd>
                <Badge>{selected.status}</Badge>
              </dd>
              <dt>Reviewed by</dt>
              <dd>{selected.reviewedBy ?? '—'}</dd>
              <dt>Reviewed at</dt>
              <dd>{formatDateTime(selected.reviewedAt)}</dd>
              <dt>Note</dt>
              <dd>{selected.reviewNote || '—'}</dd>
            </dl>
          )}
        </Modal>
      )}
    </>
  );
}
