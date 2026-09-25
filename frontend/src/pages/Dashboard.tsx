import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { Dashboard as DashboardData } from '../api/types';
import { Loading } from '../components/Loading';
import { ErrorBox } from '../components/ErrorBox';
import { Badge } from '../components/Badge';
import { formatDateTime, relativeTime } from '../utils/format';
import { usePermissions } from '../auth/AuthContext';

export function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState('');
  const { identity } = usePermissions();

  useEffect(() => {
    api
      .getDashboard()
      .then(setData)
      .catch((err) => setError(err?.response?.data?.detail ?? err.message));
  }, []);

  if (error) return <ErrorBox message={error} />;
  if (!data) return <Loading label="Loading dashboard…" />;

  return (
    <>
      <div className="stat-grid">
        <div className="stat">
          <div className="stat-label">Active clients</div>
          <div className="stat-value">{data.activeClients}</div>
          <div className="stat-hint">configured for statements</div>
        </div>
        <div className="stat">
          <div className="stat-label">Active accounts</div>
          <div className="stat-value">{data.activeAccounts}</div>
          <div className="stat-hint">across all clients</div>
        </div>
        <div className="stat accent-warn">
          <div className="stat-label">Pending approvals</div>
          <div className="stat-value">{data.pendingApprovals}</div>
          <div className="stat-hint">
            {identity?.isChecker || identity?.isAdmin ? 'awaiting your review' : 'awaiting a checker'}
          </div>
        </div>
        <div className="stat accent-info">
          <div className="stat-label">Statements today</div>
          <div className="stat-value">{data.statementsToday}</div>
          <div className="stat-hint">generated</div>
        </div>
        <div className="stat accent-ok">
          <div className="stat-label">Delivered today</div>
          <div className="stat-value">{data.deliveredToday}</div>
          <div className="stat-hint">emailed to clients</div>
        </div>
        <div className="stat accent-danger">
          <div className="stat-label">Failed today</div>
          <div className="stat-value">{data.failedToday}</div>
          <div className="stat-hint">check the delivery log</div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.25fr) minmax(0, 1fr)', gap: 18 }}>
        <div className="card">
          <div className="card-header">
            <h2>Next scheduled deliveries</h2>
            <span className="spacer" />
            <Link className="btn btn-sm" to="/schedules">
              Manage schedules
            </Link>
          </div>
          <div className="card-body tight">
            {data.upcoming.length === 0 ? (
              <div className="empty">
                <h4>No schedules</h4>
                <p>Create a delivery schedule for a client to start sending statements automatically.</p>
                <Link className="btn btn-primary btn-sm" to="/clients">
                  Go to clients
                </Link>
              </div>
            ) : (
              <div className="table-scroll">
                <table className="data">
                  <thead>
                    <tr>
                      <th>Client</th>
                      <th>Account</th>
                      <th>Cadence</th>
                      <th className="right">Next run</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.upcoming.map((item) => (
                      <tr key={item.scheduleId}>
                        <td>
                          <strong>{item.clientCode}</strong>
                          <div className="small muted">{item.clientName}</div>
                        </td>
                        <td className="mono">{item.accountNumber}</td>
                        <td>{item.frequency}</td>
                        <td className="right nowrap">
                          {formatDateTime(item.nextRunAt)}
                          <div className="small faint">{relativeTime(item.nextRunAt)}</div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Recent activity</h2>
            <span className="spacer" />
            <Link className="btn btn-sm" to="/audit">
              Full audit log
            </Link>
          </div>
          <div className="card-body tight">
            <div className="table-scroll">
              <table className="data">
                <thead>
                  <tr>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>When</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentActivity.map((entry) => (
                    <tr key={entry.id}>
                      <td>
                        <strong className="small">{entry.action}</strong>
                        <div className="small muted" style={{ maxWidth: 260 }}>
                          {entry.description}
                        </div>
                      </td>
                      <td>
                        <span className="small">{entry.actorUsername}</span>
                        <div>
                          <Badge tone={entry.outcome === 'SUCCESS' ? 'ok' : 'danger'}>
                            {entry.outcome}
                          </Badge>
                        </div>
                      </td>
                      <td className="nowrap small muted">{relativeTime(entry.eventTime)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
