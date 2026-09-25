import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import type {
  Account,
  Client,
  DeliverySchedule,
  EmailConfig,
  Statement,
} from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { ErrorBox } from '../components/ErrorBox';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';
import { usePermissions } from '../auth/AuthContext';
import { DAY_NAMES, formatDateTime, formatMoney, parseAccountList } from '../utils/format';

type Tab = 'accounts' | 'schedules' | 'recipients' | 'statements';

export function ClientDetail() {
  const { id } = useParams();
  const clientId = Number(id);
  const { canMake } = usePermissions();

  const [tab, setTab] = useState<Tab>('accounts');
  const [client, setClient] = useState<Client | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [schedules, setSchedules] = useState<DeliverySchedule[]>([]);
  const [configs, setConfigs] = useState<EmailConfig[]>([]);
  const [statements, setStatements] = useState<Statement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [accountModal, setAccountModal] = useState<Account | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [scheduleModal, setScheduleModal] = useState<DeliverySchedule | null>(null);
  const [configModal, setConfigModal] = useState<EmailConfig | null>(null);

  const load = useCallback(async () => {
    try {
      const [clientData, accountData, scheduleData, configData, statementData] = await Promise.all([
        api.getClient(clientId),
        api.listAccounts(clientId),
        api.listSchedules(clientId),
        api.listEmailConfigs(clientId),
        api.listStatements({ clientId }),
      ]);
      setClient(clientData);
      setAccounts(accountData);
      setSchedules(scheduleData);
      setConfigs(configData);
      setStatements(statementData);
      setError('');
    } catch (err: unknown) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [clientId]);

  useEffect(() => {
    void load();
  }, [load]);

  const queue = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      setNotice(message);
      setAccountModal(null);
      setScheduleModal(null);
      setConfigModal(null);
      setBulkOpen(false);
      await load();
    } catch (err: unknown) {
      setError((err as Error).message);
    }
  };

  if (loading) return <Loading />;
  if (!client) return <ErrorBox message="Client not found" />;

  return (
    <>
      <div className="inline">
        <Link to="/clients" className="btn btn-sm">
          ← Clients
        </Link>
        <h2 style={{ fontSize: 18 }}>
          {client.clientName} <span className="mono muted">({client.clientCode})</span>
        </h2>
        <Badge>{client.status}</Badge>
        {client.pendingChange && <Badge tone="warn">pending {client.pendingChange.operation}</Badge>}
      </div>

      {notice && <div className="alert alert-ok">{notice}</div>}
      <ErrorBox message={error} />

      <div className="card">
        <div className="card-body">
          <dl className="detail-list">
            <dt>Contact person</dt>
            <dd>{client.contactPerson || '—'}</dd>
            <dt>Email</dt>
            <dd>{client.primaryEmail || '—'}</dd>
            <dt>Phone</dt>
            <dd>{client.phone || '—'}</dd>
            <dt>Address</dt>
            <dd>{client.address || '—'}</dd>
            <dt>Currency / timezone</dt>
            <dd>
              {client.defaultCurrency} · {client.timezone}
            </dd>
            <dt>Sender / receiver LT</dt>
            <dd className="mono">
              {client.senderLtAddress || 'global default'} → {client.receiverLtAddress || 'global default'}
            </dd>
            <dt>Last updated</dt>
            <dd>
              {formatDateTime(client.updatedAt)} {client.updatedBy ? `by ${client.updatedBy}` : ''}
            </dd>
          </dl>
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          {(['accounts', 'schedules', 'recipients', 'statements'] as Tab[]).map((item) => (
            <button
              key={item}
              className={`tab${tab === item ? ' active' : ''}`}
              onClick={() => setTab(item)}
            >
              {item === 'accounts' && `Accounts (${accounts.length})`}
              {item === 'schedules' && `Delivery schedules (${schedules.length})`}
              {item === 'recipients' && `Recipients (${configs.length})`}
              {item === 'statements' && `Statements (${statements.length})`}
            </button>
          ))}
        </div>

        <div className="card-body tight">
          {tab === 'accounts' && (
            <AccountsTab
              accounts={accounts}
              canMake={canMake}
              onAdd={() =>
                setAccountModal({
                  id: 0,
                  clientId,
                  accountNumber: '',
                  currency: client.defaultCurrency,
                  status: 'ACTIVE',
                })
              }
              onBulk={() => setBulkOpen(true)}
              onEdit={(account) => setAccountModal(account)}
              onStatus={(account, status) =>
                queue(
                  () => api.changeAccountStatus(account.id, status),
                  `Status change for ${account.accountNumber} submitted for approval.`
                )
              }
            />
          )}

          {tab === 'schedules' && (
            <SchedulesTab
              schedules={schedules}
              accounts={accounts}
              canMake={canMake}
              onAdd={() =>
                setScheduleModal({
                  id: 0,
                  clientId,
                  frequency: 'DAILY',
                  sendTime: '07:00',
                  timezone: client.timezone,
                  periodStrategy: 'PREVIOUS_PERIOD',
                  enabled: true,
                  includeZeroTransactionStatements: false,
                })
              }
              onEdit={(schedule) => setScheduleModal(schedule)}
              onToggle={(schedule, enabled) =>
                queue(
                  () => api.toggleSchedule(schedule.id, enabled),
                  `Schedule ${enabled ? 'activation' : 'deactivation'} submitted for approval.`
                )
              }
              onDelete={(schedule) =>
                queue(
                  () => api.deleteSchedule(schedule.id),
                  `Deletion of schedule ${schedule.id} submitted for approval.`
                )
              }
            />
          )}

          {tab === 'recipients' && (
            <RecipientsTab
              configs={configs}
              canMake={canMake}
              onAdd={() =>
                setConfigModal({ id: 0, clientId, enabled: true, isDefault: false } as EmailConfig)
              }
              onEdit={(config) => setConfigModal(config)}
              onDelete={(config) =>
                queue(
                  () => api.deleteEmailConfig(config.id),
                  `Deletion of "${config.name}" submitted for approval.`
                )
              }
            />
          )}

          {tab === 'statements' && <StatementsTab statements={statements} />}
        </div>
      </div>

      {accountModal && (
        <AccountModal
          account={accountModal}
          client={client}
          onClose={() => setAccountModal(null)}
          onSubmit={(draft) =>
            queue(
              () =>
                accountModal.id
                  ? api.updateAccount(accountModal.id, draft)
                  : api.createAccounts(clientId, [draft]),
              `Account ${draft.accountNumber} submitted for approval.`
            )
          }
        />
      )}

      {bulkOpen && (
        <BulkAccountModal
          client={client}
          onClose={() => setBulkOpen(false)}
          onSubmit={(accounts) =>
            queue(
              () => api.createAccounts(clientId, accounts),
              `${accounts.length} account(s) submitted for approval as one request.`
            )
          }
        />
      )}

      {scheduleModal && (
        <ScheduleModal
          schedule={scheduleModal}
          accounts={accounts}
          onClose={() => setScheduleModal(null)}
          onSubmit={(draft) =>
            queue(
              () =>
                scheduleModal.id
                  ? api.updateSchedule(scheduleModal.id, draft)
                  : api.createSchedule(clientId, draft),
              `Delivery schedule submitted for approval.`
            )
          }
        />
      )}

      {configModal && (
        <ConfigModal
          config={configModal}
          onClose={() => setConfigModal(null)}
          onSubmit={(draft) =>
            queue(
              () =>
                configModal.id
                  ? api.updateEmailConfig(configModal.id, draft)
                  : api.createEmailConfig(clientId, draft),
              `Email configuration submitted for approval.`
            )
          }
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------------------

function AccountsTab({
  accounts,
  canMake,
  onAdd,
  onBulk,
  onEdit,
  onStatus,
}: {
  accounts: Account[];
  canMake: boolean;
  onAdd: () => void;
  onBulk: () => void;
  onEdit: (account: Account) => void;
  onStatus: (account: Account, status: string) => void;
}) {
  if (accounts.length === 0) {
    return (
      <Empty
        title="No accounts yet"
        message="Add one account, or paste a list to onboard several at once."
        action={
          canMake ? (
            <div className="inline" style={{ justifyContent: 'center' }}>
              <button className="btn btn-primary" onClick={onAdd}>
                + Add account
              </button>
              <button className="btn" onClick={onBulk}>
                + Add multiple
              </button>
            </div>
          ) : undefined
        }
      />
    );
  }

  return (
    <>
      <div className="card-header" style={{ borderTop: 'none' }}>
        <span className="spacer" />
        <button className="btn btn-sm" disabled={!canMake} onClick={onBulk}>
          + Add multiple
        </button>
        <button className="btn btn-primary btn-sm" disabled={!canMake} onClick={onAdd}>
          + Add account
        </button>
      </div>
      <div className="table-scroll">
        <table className="data">
          <thead>
            <tr>
              <th>Account number</th>
              <th>Name</th>
              <th>CCY</th>
              <th>Branch</th>
              <th className="right">Last stmt no.</th>
              <th className="right">Statements</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {accounts.map((account) => (
              <tr key={account.id}>
                <td className="mono">{account.accountNumber}</td>
                <td>{account.accountName || '—'}</td>
                <td>{account.currency}</td>
                <td>{account.branchCode || '—'}</td>
                <td className="right mono">{account.lastStatementNumber ?? 0}</td>
                <td className="right">{account.statementCount ?? 0}</td>
                <td>
                  <Badge>{account.status}</Badge>
                  {account.pendingChange && (
                    <div className="small muted">pending {account.pendingChange.operation.toLowerCase()}</div>
                  )}
                </td>
                <td>
                  <div className="row-actions">
                    <button className="btn btn-sm" disabled={!canMake} onClick={() => onEdit(account)}>
                      Edit
                    </button>
                    {account.status === 'ACTIVE' ? (
                      <button
                        className="btn btn-sm"
                        disabled={!canMake}
                        onClick={() => onStatus(account, 'DORMANT')}
                      >
                        Make dormant
                      </button>
                    ) : (
                      <button
                        className="btn btn-sm"
                        disabled={!canMake}
                        onClick={() => onStatus(account, 'ACTIVE')}
                      >
                        Activate
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function SchedulesTab({
  schedules,
  accounts,
  canMake,
  onAdd,
  onEdit,
  onToggle,
  onDelete,
}: {
  schedules: DeliverySchedule[];
  accounts: Account[];
  canMake: boolean;
  onAdd: () => void;
  onEdit: (schedule: DeliverySchedule) => void;
  onToggle: (schedule: DeliverySchedule, enabled: boolean) => void;
  onDelete: (schedule: DeliverySchedule) => void;
}) {
  const accountOf = (id?: number | null) => accounts.find((a) => a.id === id);

  return (
    <>
      <div className="card-header" style={{ borderTop: 'none' }}>
        <span className="spacer" />
        <button className="btn btn-primary btn-sm" disabled={!canMake} onClick={onAdd}>
          + New schedule
        </button>
      </div>

      {schedules.length === 0 ? (
        <Empty
          title="No delivery schedules"
          message="A schedule controls how often the generated MT940 is emailed to this client."
          action={
            canMake ? (
              <button className="btn btn-primary" onClick={onAdd}>
                + New schedule
              </button>
            ) : undefined
          }
        />
      ) : (
        <div className="table-scroll">
          <table className="data">
            <thead>
              <tr>
                <th>Name</th>
                <th>Account</th>
                <th>Frequency</th>
                <th>Time</th>
                <th>Period covered</th>
                <th>Last sent</th>
                <th>Next run</th>
                <th>Enabled</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {schedules.map((schedule) => (
                <tr key={schedule.id}>
                  <td>
                    <strong>{schedule.name || `Schedule ${schedule.id}`}</strong>
                    {schedule.pendingChange && (
                      <div className="small muted">pending {schedule.pendingChange.operation.toLowerCase()}</div>
                    )}
                  </td>
                  <td className="mono">
                    {schedule.accountId ? accountOf(schedule.accountId)?.accountNumber ?? schedule.accountId : 'All accounts'}
                  </td>
                  <td>
                    <Badge tone="brand">{schedule.frequency}</Badge>
                    {schedule.frequency === 'WEEKLY' && schedule.dayOfWeek && (
                      <div className="small muted">{DAY_NAMES[schedule.dayOfWeek]}</div>
                    )}
                    {schedule.frequency === 'MONTHLY' && schedule.dayOfMonth && (
                      <div className="small muted">day {schedule.dayOfMonth}</div>
                    )}
                  </td>
                  <td className="nowrap">
                    {schedule.sendTime}
                    <div className="small faint">{schedule.timezone}</div>
                  </td>
                  <td className="small muted">{schedule.periodStrategy?.replace('_', ' ').toLowerCase()}</td>
                  <td className="small nowrap">{formatDateTime(schedule.lastSentAt)}</td>
                  <td className="small nowrap">{formatDateTime(schedule.nextRunAt)}</td>
                  <td>
                    <Badge tone={schedule.enabled ? 'ok' : 'neutral'}>
                      {schedule.enabled ? 'ON' : 'OFF'}
                    </Badge>
                  </td>
                  <td>
                    <div className="row-actions">
                      <button className="btn btn-sm" disabled={!canMake} onClick={() => onEdit(schedule)}>
                        Edit
                      </button>
                      <button
                        className="btn btn-sm"
                        disabled={!canMake}
                        onClick={() => onToggle(schedule, !schedule.enabled)}
                      >
                        {schedule.enabled ? 'Disable' : 'Enable'}
                      </button>
                      <button className="btn btn-sm btn-danger" disabled={!canMake} onClick={() => onDelete(schedule)}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function RecipientsTab({
  configs,
  canMake,
  onAdd,
  onEdit,
  onDelete,
}: {
  configs: EmailConfig[];
  canMake: boolean;
  onAdd: () => void;
  onEdit: (config: EmailConfig) => void;
  onDelete: (config: EmailConfig) => void;
}) {
  return (
    <>
      <div className="card-header" style={{ borderTop: 'none' }}>
        <span className="spacer" />
        <button className="btn btn-primary btn-sm" disabled={!canMake} onClick={onAdd}>
          + New configuration
        </button>
      </div>

      {configs.length === 0 ? (
        <Empty
          title="No recipient configuration"
          message="Without one the statement is sent to the client's primary email address."
          action={
            canMake ? (
              <button className="btn btn-primary" onClick={onAdd}>
                + New configuration
              </button>
            ) : undefined
          }
        />
      ) : (
        <div className="table-scroll">
          <table className="data">
            <thead>
              <tr>
                <th>Name</th>
                <th>To</th>
                <th>CC</th>
                <th>BCC</th>
                <th>Default</th>
                <th>Enabled</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {configs.map((config) => (
                <tr key={config.id}>
                  <td>
                    <strong>{config.name || `Config ${config.id}`}</strong>
                    {config.pendingChange && (
                      <div className="small muted">pending {config.pendingChange.operation.toLowerCase()}</div>
                    )}
                  </td>
                  <td className="small">{config.toAddresses || '—'}</td>
                  <td className="small">{config.ccAddresses || '—'}</td>
                  <td className="small">{config.bccAddresses || '—'}</td>
                  <td>{config.isDefault ? <Badge tone="brand">default</Badge> : '—'}</td>
                  <td>
                    <Badge tone={config.enabled ? 'ok' : 'neutral'}>{config.enabled ? 'ON' : 'OFF'}</Badge>
                  </td>
                  <td>
                    <div className="row-actions">
                      <button className="btn btn-sm" disabled={!canMake} onClick={() => onEdit(config)}>
                        Edit
                      </button>
                      <button className="btn btn-sm btn-danger" disabled={!canMake} onClick={() => onDelete(config)}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function StatementsTab({ statements }: { statements: Statement[] }) {
  if (statements.length === 0) {
    return (
      <Empty
        title="No statements generated yet"
        message="Generate one on demand, or wait for the next scheduled run."
        action={
          <Link className="btn btn-primary btn-sm" to="/statements">
            Generate a statement
          </Link>
        }
      />
    );
  }
  return (
    <div className="table-scroll">
      <table className="data">
        <thead>
          <tr>
            <th>Reference</th>
            <th>Account</th>
            <th>Period</th>
            <th className="right">Txns</th>
            <th className="right">Opening</th>
            <th className="right">Closing</th>
            <th>Delivery</th>
            <th>Generated</th>
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
                {statement.openingMark} {formatMoney(statement.openingBalance)}
              </td>
              <td className="right mono">
                {statement.closingMark} {formatMoney(statement.closingBalance)}
              </td>
              <td>
                <Badge>{statement.deliveryStatus}</Badge>
              </td>
              <td className="small nowrap">{formatDateTime(statement.generatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---------------------------------------------------------------------------

function AccountModal({
  account,
  client,
  onClose,
  onSubmit,
}: {
  account: Account;
  client: Client;
  onClose: () => void;
  onSubmit: (draft: Partial<Account>) => void;
}) {
  const [draft, setDraft] = useState<Partial<Account>>(account);
  const isNew = !account.id;

  return (
    <Modal
      title={isNew ? 'Add account' : `Edit account ${account.accountNumber}`}
      subtitle="Submitted to the maker-checker queue."
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            disabled={!draft.accountNumber}
            onClick={() => onSubmit(draft)}
          >
            Submit for approval
          </button>
        </>
      }
    >
      <div className="form-grid">
        <div className="field">
          <label>Account number *</label>
          <input
            value={draft.accountNumber ?? ''}
            onChange={(e) => setDraft({ ...draft, accountNumber: e.target.value.toUpperCase() })}
            placeholder="1144355935012"
          />
        </div>
        <div className="field">
          <label>Account name</label>
          <input
            value={draft.accountName ?? ''}
            onChange={(e) => setDraft({ ...draft, accountName: e.target.value })}
            placeholder={client.clientName}
          />
        </div>
        <div className="field">
          <label>Currency</label>
          <input
            value={draft.currency ?? 'ETB'}
            maxLength={3}
            onChange={(e) => setDraft({ ...draft, currency: e.target.value.toUpperCase() })}
          />
        </div>
        <div className="field">
          <label>Branch code</label>
          <input value={draft.branchCode ?? ''} onChange={(e) => setDraft({ ...draft, branchCode: e.target.value })} />
        </div>
        <div className="field">
          <label>IBAN</label>
          <input value={draft.iban ?? ''} onChange={(e) => setDraft({ ...draft, iban: e.target.value })} />
        </div>
        <div className="field">
          <label>BIC</label>
          <input
            value={draft.bic ?? ''}
            onChange={(e) => setDraft({ ...draft, bic: e.target.value.toUpperCase() })}
          />
        </div>
        <div className="field">
          <label>Status</label>
          <select value={draft.status ?? 'ACTIVE'} onChange={(e) => setDraft({ ...draft, status: e.target.value as Account['status'] })}>
            <option value="ACTIVE">Active</option>
            <option value="DORMANT">Dormant</option>
            <option value="INACTIVE">Inactive</option>
            <option value="CLOSED">Closed</option>
          </select>
        </div>
        <div className="field">
          <label>Statement number seed</label>
          <input
            type="number"
            value={draft.statementNumberSeed ?? 0}
            onChange={(e) => setDraft({ ...draft, statementNumberSeed: Number(e.target.value) })}
          />
        </div>
      </div>
    </Modal>
  );
}

function BulkAccountModal({
  client,
  onClose,
  onSubmit,
}: {
  client: Client;
  onClose: () => void;
  onSubmit: (accounts: Partial<Account>[]) => void;
}) {
  const [text, setText] = useState('');
  const [currency, setCurrency] = useState(client.defaultCurrency);
  const parsed = parseAccountList(text);

  return (
    <Modal
      title="Add multiple accounts"
      subtitle="One account per line. Optionally follow the number with a name (tabs, pipes or two spaces)."
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            disabled={parsed.length === 0}
            onClick={() =>
              onSubmit(
                parsed.map((entry) => ({
                  accountNumber: entry.accountNumber,
                  accountName: entry.accountName,
                  currency,
                  status: 'ACTIVE' as const,
                }))
              )
            }
          >
            Submit {parsed.length || ''} for approval
          </button>
        </>
      }
    >
      <div className="field">
        <label>Account numbers</label>
        <textarea
          className="bulk-textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={'1144355935020\tCollection A\n1144355935021\tCollection B\n1144355935022'}
        />
        <span className="hint">{parsed.length} account(s) detected.</span>
      </div>
      <div className="field">
        <label>Default currency for this batch</label>
        <input
          value={currency}
          maxLength={3}
          onChange={(e) => setCurrency(e.target.value.toUpperCase())}
          style={{ width: 100 }}
        />
      </div>
      {parsed.length > 0 && (
        <div className="table-scroll">
          <table className="data">
            <thead>
              <tr>
                <th>#</th>
                <th>Account number</th>
                <th>Name</th>
                <th>CCY</th>
              </tr>
            </thead>
            <tbody>
              {parsed.map((entry, index) => (
                <tr key={index}>
                  <td className="muted">{index + 1}</td>
                  <td className="mono">{entry.accountNumber}</td>
                  <td>{entry.accountName || client.clientName}</td>
                  <td>{currency}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  );
}

function ScheduleModal({
  schedule,
  accounts,
  onClose,
  onSubmit,
}: {
  schedule: DeliverySchedule;
  accounts: Account[];
  onClose: () => void;
  onSubmit: (draft: Partial<DeliverySchedule>) => void;
}) {
  const [draft, setDraft] = useState<Partial<DeliverySchedule>>(schedule);
  const isNew = !schedule.id;

  return (
    <Modal
      title={isNew ? 'New delivery schedule' : 'Edit delivery schedule'}
      subtitle="Submitted to the maker-checker queue."
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={() => onSubmit(draft)}>
            Submit for approval
          </button>
        </>
      }
    >
      <div className="form-grid">
        <div className="field">
          <label>Name</label>
          <input
            value={draft.name ?? ''}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
            placeholder="Daily treasury statement"
          />
        </div>
        <div className="field">
          <label>Account</label>
          <select
            value={draft.accountId ?? ''}
            onChange={(e) =>
              setDraft({ ...draft, accountId: e.target.value ? Number(e.target.value) : null })
            }
          >
            <option value="">All active accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountNumber}
                {account.accountName ? ` — ${account.accountName}` : ''}
              </option>
            ))}
          </select>
          <span className="hint">Leave on “all accounts” to deliver one statement per account.</span>
        </div>
        <div className="field">
          <label>Frequency</label>
          <select
            value={draft.frequency ?? 'DAILY'}
            onChange={(e) => setDraft({ ...draft, frequency: e.target.value as DeliverySchedule['frequency'] })}
          >
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </div>
        {draft.frequency === 'WEEKLY' && (
          <div className="field">
            <label>Day of week</label>
            <select
              value={draft.dayOfWeek ?? 1}
              onChange={(e) => setDraft({ ...draft, dayOfWeek: Number(e.target.value) })}
            >
              {DAY_NAMES.slice(1).map((name, index) => (
                <option key={name} value={index + 1}>
                  {name}
                </option>
              ))}
            </select>
          </div>
        )}
        {draft.frequency === 'MONTHLY' && (
          <div className="field">
            <label>Day of month</label>
            <input
              type="number"
              min={1}
              max={31}
              value={draft.dayOfMonth ?? 1}
              onChange={(e) => setDraft({ ...draft, dayOfMonth: Number(e.target.value) })}
            />
            <span className="hint">Days beyond month length clamp to the last day.</span>
          </div>
        )}
        <div className="field">
          <label>Send time (HH:mm)</label>
          <input
            value={draft.sendTime ?? '07:00'}
            onChange={(e) => setDraft({ ...draft, sendTime: e.target.value })}
            placeholder="07:00"
          />
        </div>
        <div className="field">
          <label>Timezone</label>
          <input
            value={draft.timezone ?? 'Africa/Addis_Ababa'}
            onChange={(e) => setDraft({ ...draft, timezone: e.target.value })}
          />
        </div>
        <div className="field">
          <label>Period covered</label>
          <select
            value={draft.periodStrategy ?? 'PREVIOUS_PERIOD'}
            onChange={(e) =>
              setDraft({ ...draft, periodStrategy: e.target.value as DeliverySchedule['periodStrategy'] })
            }
          >
            <option value="PREVIOUS_PERIOD">Previous full period</option>
            <option value="ROLLING_7">Rolling 7 days</option>
            <option value="ROLLING_30">Rolling 30 days</option>
          </select>
          <span className="hint">
            Daily = yesterday · Weekly = the last 7 days · Monthly = the previous calendar month.
          </span>
        </div>
      </div>

      <label className="checkbox">
        <input
          type="checkbox"
          checked={draft.enabled ?? true}
          onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
        />
        Enabled
      </label>
      <label className="checkbox" style={{ marginTop: 8 }}>
        <input
          type="checkbox"
          checked={draft.includeZeroTransactionStatements ?? false}
          onChange={(e) => setDraft({ ...draft, includeZeroTransactionStatements: e.target.checked })}
        />
        Send even when the period has no transactions
      </label>
    </Modal>
  );
}

function ConfigModal({
  config,
  onClose,
  onSubmit,
}: {
  config: EmailConfig;
  onClose: () => void;
  onSubmit: (draft: Partial<EmailConfig>) => void;
}) {
  const [draft, setDraft] = useState<Partial<EmailConfig>>(config);

  return (
    <Modal
      wide
      title={config.id ? 'Edit recipient configuration' : 'New recipient configuration'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            disabled={!draft.toAddresses && !draft.ccAddresses && !draft.bccAddresses}
            onClick={() => onSubmit(draft)}
          >
            Submit for approval
          </button>
        </>
      }
    >
      <div className="form-grid">
        <div className="field">
          <label>Name</label>
          <input value={draft.name ?? ''} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </div>
      </div>
      <div className="field">
        <label>To</label>
        <input
          value={draft.toAddresses ?? ''}
          onChange={(e) => setDraft({ ...draft, toAddresses: e.target.value })}
          placeholder="finance@client.example, treasury@client.example"
        />
        <span className="hint">Comma or semicolon separated.</span>
      </div>
      <div className="form-grid">
        <div className="field">
          <label>CC</label>
          <input value={draft.ccAddresses ?? ''} onChange={(e) => setDraft({ ...draft, ccAddresses: e.target.value })} />
        </div>
        <div className="field">
          <label>BCC</label>
          <input
            value={draft.bccAddresses ?? ''}
            onChange={(e) => setDraft({ ...draft, bccAddresses: e.target.value })}
          />
        </div>
      </div>
      <div className="field">
        <label>Subject template</label>
        <input
          value={draft.subjectTemplate ?? ''}
          onChange={(e) => setDraft({ ...draft, subjectTemplate: e.target.value })}
          placeholder="MT940 Statement - Account ${accountNumber} - ${periodFrom} to ${periodTo}"
        />
      </div>
      <div className="field">
        <label>Body template</label>
        <textarea
          style={{ minHeight: 150 }}
          value={draft.bodyTemplate ?? ''}
          onChange={(e) => setDraft({ ...draft, bodyTemplate: e.target.value })}
        />
        <span className="hint">
          Placeholders: {'${clientName}, ${clientCode}, ${accountNumber}, ${statementReference}, ${statementNumber}, ${periodFrom}, ${periodTo}, ${currency}, ${openingBalance}, ${closingBalance}, ${transactionCount}, ${fileName}, ${fromName}'}
        </span>
      </div>
      <div className="inline">
        <label className="checkbox">
          <input
            type="checkbox"
            checked={draft.enabled ?? true}
            onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
          />
          Enabled
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={draft.isDefault ?? false}
            onChange={(e) => setDraft({ ...draft, isDefault: e.target.checked })}
          />
          Default for this client
        </label>
      </div>
    </Modal>
  );
}
