import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Account, Client, Statement } from '../api/types';
import { Loading } from '../components/Loading';
import { ErrorBox } from '../components/ErrorBox';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { usePermissions } from '../auth/AuthContext';
import { downloadText, formatDateTime, formatMoney, isoDaysAgo, todayIso } from '../utils/format';

export function Statements() {
  const { canMake } = usePermissions();
  const [clients, setClients] = useState<Client[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [statements, setStatements] = useState<Statement[]>([]);

  const [clientId, setClientId] = useState<number | ''>('');
  const [accountId, setAccountId] = useState<number | ''>('');
  const [periodFrom, setPeriodFrom] = useState(isoDaysAgo(1));
  const [periodTo, setPeriodTo] = useState(todayIso());

  const [preview, setPreview] = useState<Statement | null>(null);
  const [generated, setGenerated] = useState<Statement | null>(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    api.listClients({ size: 100 }).then((page) => setClients(page.content)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!clientId) {
      setAccounts([]);
      return;
    }
    api.listAccounts(clientId).then(setAccounts).catch(() => undefined);
    api
      .listStatements({ clientId })
      .then(setStatements)
      .catch(() => undefined);
  }, [clientId]);

  const refresh = async () => {
    if (!clientId) return;
    setStatements(await api.listStatements({ clientId }));
  };

  const run = async (action: () => Promise<Statement>) => {
    setError('');
    try {
      return await action();
    } catch (err: unknown) {
      setError((err as Error).message);
      throw err;
    }
  };

  const onPreview = async () => {
    if (!accountId) return;
    setBusy('preview');
    try {
      setGenerated(null);
      setPreview(await run(() => api.previewStatement(Number(accountId), periodFrom, periodTo)));
    } catch {
      /* message already surfaced */
    } finally {
      setBusy('');
    }
  };

  const onGenerate = async () => {
    if (!accountId) return;
    setBusy('generate');
    try {
      const statement = await run(() => api.generateStatement(Number(accountId), periodFrom, periodTo));
      setGenerated(statement);
      setPreview(null);
      setNotice(`Statement ${statement.statementReference} generated and stored.`);
      await refresh();
    } catch {
      /* handled */
    } finally {
      setBusy('');
    }
  };

  const onRequestSend = async (statement: Statement) => {
    setBusy('send');
    try {
      await api.requestSend({
        statementId: statement.id,
        accountId: statement.accountId,
        periodFrom: statement.periodFrom,
        periodTo: statement.periodTo,
        recipients: [],
      });
      setNotice('Delivery submitted for approval. A checker must approve it before the email leaves the bank.');
    } catch (err: unknown) {
      setError((err as Error).message);
    } finally {
      setBusy('');
    }
  };

  const shown = generated ?? preview;

  return (
    <>
      {notice && <div className="alert alert-ok">{notice}</div>}
      <ErrorBox message={error} />

      <div className="card">
        <div className="card-header">
          <h2>Generate an MT940 statement</h2>
          <span className="spacer" />
          <span className="small muted">
            Transactions are pulled from the core banking endpoint for the selected period.
          </span>
        </div>
        <div className="card-body">
          <div className="filter-bar">
            <div className="field" style={{ minWidth: 220 }}>
              <label>Client</label>
              <select
                value={clientId}
                onChange={(event) => {
                  setClientId(Number(event.target.value) || '');
                  setAccountId('');
                  setPreview(null);
                  setGenerated(null);
                }}
              >
                <option value="">Select a client…</option>
                {clients.map((client) => (
                  <option key={client.id} value={client.id}>
                    {client.clientCode} — {client.clientName}
                  </option>
                ))}
              </select>
            </div>

            <div className="field" style={{ minWidth: 240 }}>
              <label>Account</label>
              <select value={accountId} onChange={(event) => setAccountId(Number(event.target.value) || '')}>
                <option value="">Select an account…</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountNumber}
                    {account.accountName ? ` — ${account.accountName}` : ''} ({account.currency})
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label>Period from</label>
              <input type="date" value={periodFrom} onChange={(event) => setPeriodFrom(event.target.value)} />
            </div>

            <div className="field">
              <label>Period to</label>
              <input type="date" value={periodTo} onChange={(event) => setPeriodTo(event.target.value)} />
            </div>

            <button className="btn" disabled={!accountId || busy !== ''} onClick={onPreview}>
              {busy === 'preview' ? <span className="spinner" /> : null} Preview
            </button>
            <button
              className="btn btn-primary"
              disabled={!accountId || busy !== '' || !canMake}
              onClick={onGenerate}
            >
              {busy === 'generate' ? <span className="spinner" /> : null} Generate &amp; store
            </button>
          </div>
        </div>
      </div>

      {shown && (
        <div className="card">
          <div className="card-header">
            <h2>
              {generated ? 'Generated statement' : 'Preview'}{' '}
              <span className="mono muted small">{shown.statementReference}</span>
            </h2>
            <span className="spacer" />
            <button
              className="btn btn-sm"
              onClick={() => downloadText(shown.fileName, shown.content ?? '')}
            >
              Download .txt
            </button>
            {generated && (
              <button
                className="btn btn-sm btn-primary"
                disabled={!canMake || busy !== ''}
                onClick={() => onRequestSend(generated)}
              >
                Request delivery by email
              </button>
            )}
          </div>
          <div className="card-body stack">
            <div className="stat-grid">
              <div className="stat">
                <div className="stat-label">Opening (:60F:)</div>
                <div className="stat-value" style={{ fontSize: 20 }}>
                  {shown.openingMark} {formatMoney(shown.openingBalance, shown.currency)}
                </div>
              </div>
              <div className="stat">
                <div className="stat-label">Closing (:62F:)</div>
                <div className="stat-value" style={{ fontSize: 20 }}>
                  {shown.closingMark} {formatMoney(shown.closingBalance, shown.currency)}
                </div>
              </div>
              <div className="stat">
                <div className="stat-label">Transactions</div>
                <div className="stat-value" style={{ fontSize: 20 }}>
                  {shown.transactionCount}
                </div>
              </div>
              <div className="stat">
                <div className="stat-label">Statement no. (:28C:)</div>
                <div className="stat-value" style={{ fontSize: 20 }}>
                  {shown.statementNumber}/{shown.pageSequence}
                </div>
              </div>
            </div>

            <div>
              <div className="small muted" style={{ marginBottom: 6 }}>
                {shown.fileName}
              </div>
              <pre className="code-block">{shown.content}</pre>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h2>Statement history</h2>
          <span className="spacer" />
          <span className="small muted">{statements.length} record(s)</span>
        </div>
        <div className="card-body tight">
          {!clientId ? (
            <div className="empty">
              <h4>Select a client</h4>
              <p>Choose a client above to see the statements generated for it.</p>
            </div>
          ) : statements.length === 0 ? (
            <div className="empty">
              <h4>No statements yet</h4>
              <p>Generate one with the controls above.</p>
            </div>
          ) : (
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Account</th>
                    <th>Period</th>
                    <th className="right">Txns</th>
                    <th className="right">Closing</th>
                    <th>Delivery</th>
                    <th>Generated</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {statements.map((statement) => (
                    <tr key={statement.id}>
                      <td className="mono">{statement.statementReference}</td>
                      <td className="mono">{statement.accountNumber}</td>
                      <td className="small nowrap">
                        {statement.periodFrom} → {statement.periodTo}
                      </td>
                      <td className="right">{statement.transactionCount}</td>
                      <td className="right mono">
                        {statement.closingMark} {formatMoney(statement.closingBalance)}
                      </td>
                      <td>
                        <Badge>{statement.deliveryStatus}</Badge>
                      </td>
                      <td className="small nowrap">{formatDateTime(statement.generatedAt)}</td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="btn btn-sm"
                            onClick={() => downloadText(statement.fileName, statement.content ?? '')}
                          >
                            Download
                          </button>
                          <button
                            className="btn btn-sm"
                            disabled={!canMake || statement.deliveryStatus === 'SENT'}
                            onClick={() => onRequestSend(statement)}
                          >
                            Request delivery
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
