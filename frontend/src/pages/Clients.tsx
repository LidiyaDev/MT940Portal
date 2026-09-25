import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { Client } from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { ErrorBox } from '../components/ErrorBox';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { usePermissions } from '../auth/AuthContext';

const emptyClient: Partial<Client> = {
  defaultCurrency: 'ETB',
  timezone: 'Africa/Addis_Ababa',
  status: 'ACTIVE',
  emit13d: false,
  emit64: false,
  emit65: false,
  emit90d: false,
  blankLineBetweenTags: false,
  statementNumberSeed: 0,
};

export function Clients() {
  const [clients, setClients] = useState<Client[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<Partial<Client> | null>(null);
  const { canMake } = usePermissions();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.listClients({ search: search || undefined, size: 100 });
      setClients(page.content);
      setError('');
    } catch (err: unknown) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    const timer = setTimeout(load, 200);
    return () => clearTimeout(timer);
  }, [load]);

  const submit = async () => {
    if (!editing) return;
    try {
      await api.createClient(editing);
      setNotice('Client submitted for approval. A checker must approve it before it becomes active.');
      setEditing(null);
      await load();
    } catch (err: unknown) {
      setError((err as Error).message);
    }
  };

  return (
    <>
      {notice && <div className="alert alert-ok">{notice}</div>}
      <ErrorBox message={error} />

      <div className="card">
        <div className="card-header">
          <h2>Clients</h2>
          <span className="spacer" />
          <input
            placeholder="Search by name or code…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            style={{ width: 240 }}
          />
          <button className="btn btn-primary" disabled={!canMake} onClick={() => setEditing({ ...emptyClient })}>
            + New client
          </button>
        </div>

        <div className="card-body tight">
          {loading ? (
            <Loading />
          ) : clients.length === 0 ? (
            <Empty
              title="No clients yet"
              message="Add a client to start configuring MT940 statements and delivery schedules."
              action={
                canMake ? (
                  <button className="btn btn-primary" onClick={() => setEditing({ ...emptyClient })}>
                    + New client
                  </button>
                ) : undefined
              }
            />
          ) : (
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Client</th>
                    <th>Contact</th>
                    <th>Currency</th>
                    <th className="right">Accounts</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {clients.map((client) => (
                    <tr key={client.id} className="clickable">
                      <td className="mono">
                        <Link to={`/clients/${client.id}`}>{client.clientCode}</Link>
                      </td>
                      <td>
                        <strong>{client.clientName}</strong>
                        {client.pendingChange && (
                          <div className="small muted">
                            pending {client.pendingChange.operation.toLowerCase()} ·{' '}
                            {client.pendingChange.requestedBy}
                          </div>
                        )}
                      </td>
                      <td className="small muted">
                        {client.contactPerson || '—'}
                        <div>{client.primaryEmail || '—'}</div>
                      </td>
                      <td>{client.defaultCurrency}</td>
                      <td className="right">{client.accountCount ?? 0}</td>
                      <td>
                        <Badge>{client.status}</Badge>
                      </td>
                      <td className="right">
                        <Link className="btn btn-sm" to={`/clients/${client.id}`}>
                          Open
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {editing && (
        <Modal
          title="New client"
          subtitle="Submitted to the maker-checker queue — a checker must approve it."
          onClose={() => setEditing(null)}
          footer={
            <>
              <button className="btn" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                disabled={!editing.clientCode || !editing.clientName}
                onClick={submit}
              >
                Submit for approval
              </button>
            </>
          }
        >
          <div className="form-grid">
            <div className="field">
              <label>Client code *</label>
              <input
                value={editing.clientCode ?? ''}
                onChange={(e) => setEditing({ ...editing, clientCode: e.target.value.toUpperCase() })}
                placeholder="AMBO001"
              />
              <span className="hint">Unique short code, stored upper case.</span>
            </div>
            <div className="field">
              <label>Client name *</label>
              <input
                value={editing.clientName ?? ''}
                onChange={(e) => setEditing({ ...editing, clientName: e.target.value })}
                placeholder="Ambo Mineral Water PLC"
              />
            </div>
            <div className="field">
              <label>Primary email</label>
              <input
                type="email"
                value={editing.primaryEmail ?? ''}
                onChange={(e) => setEditing({ ...editing, primaryEmail: e.target.value })}
              />
            </div>
            <div className="field">
              <label>Contact person</label>
              <input
                value={editing.contactPerson ?? ''}
                onChange={(e) => setEditing({ ...editing, contactPerson: e.target.value })}
              />
            </div>
            <div className="field">
              <label>Phone</label>
              <input
                value={editing.phone ?? ''}
                onChange={(e) => setEditing({ ...editing, phone: e.target.value })}
              />
            </div>
            <div className="field">
              <label>Default currency</label>
              <input
                value={editing.defaultCurrency ?? 'ETB'}
                maxLength={3}
                onChange={(e) => setEditing({ ...editing, defaultCurrency: e.target.value.toUpperCase() })}
              />
              <span className="hint">Used as the :60F:/:62F: currency.</span>
            </div>
            <div className="field">
              <label>Timezone</label>
              <input
                value={editing.timezone ?? ''}
                onChange={(e) => setEditing({ ...editing, timezone: e.target.value })}
              />
            </div>
            <div className="field">
              <label>Statement number seed</label>
              <input
                type="number"
                value={editing.statementNumberSeed ?? 0}
                onChange={(e) => setEditing({ ...editing, statementNumberSeed: Number(e.target.value) })}
              />
              <span className="hint">Starting point for tag :28C:.</span>
            </div>
          </div>

          <div className="divider" />
          <div className="field">
            <label>SWIFT overrides (optional)</label>
            <div className="form-grid">
              <div className="field">
                <label>Sender LT address</label>
                <input
                  value={editing.senderLtAddress ?? ''}
                  maxLength={12}
                  onChange={(e) => setEditing({ ...editing, senderLtAddress: e.target.value.toUpperCase() })}
                  placeholder="Defaults to DASHETAXXXXX"
                />
              </div>
              <div className="field">
                <label>Receiver LT address</label>
                <input
                  value={editing.receiverLtAddress ?? ''}
                  maxLength={12}
                  onChange={(e) => setEditing({ ...editing, receiverLtAddress: e.target.value.toUpperCase() })}
                  placeholder="Defaults to RECVETAAXXXX"
                />
              </div>
            </div>
            <div className="inline" style={{ marginTop: 6 }}>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={!!editing.emit90d}
                  onChange={(e) => setEditing({ ...editing, emit90d: e.target.checked })}
                />
                Emit :90D:/:90C: totals
              </label>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={!!editing.emit64}
                  onChange={(e) => setEditing({ ...editing, emit64: e.target.checked })}
                />
                Emit :64: available balance
              </label>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={!!editing.emit65}
                  onChange={(e) => setEditing({ ...editing, emit65: e.target.checked })}
                />
                Emit :65: forward balance
              </label>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={!!editing.emit13d}
                  onChange={(e) => setEditing({ ...editing, emit13d: e.target.checked })}
                />
                Emit :13D:
              </label>
            </div>
          </div>

          <div className="field">
            <label>Remarks</label>
            <textarea
              value={editing.remarks ?? ''}
              onChange={(e) => setEditing({ ...editing, remarks: e.target.value })}
            />
          </div>
        </Modal>
      )}
    </>
  );
}
