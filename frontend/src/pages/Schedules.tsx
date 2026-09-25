import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { Client, DeliverySchedule } from '../api/types';
import { Loading } from '../components/Loading';
import { Empty } from '../components/Empty';
import { Badge } from '../components/Badge';
import { formatDateTime, relativeTime } from '../utils/format';

export function Schedules() {
  const [rows, setRows] = useState<Array<{ client: Client; schedule: DeliverySchedule }>>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const page = await api.listClients({ size: 100 });
      const all: Array<{ client: Client; schedule: DeliverySchedule }> = [];
      for (const client of page.content) {
        const schedules = await api.listSchedules(client.id);
        schedules.forEach((schedule) => all.push({ client, schedule }));
      }
      all.sort((a, b) => {
        const left = a.schedule.nextRunAt ?? '';
        const right = b.schedule.nextRunAt ?? '';
        return left < right ? -1 : left > right ? 1 : 0;
      });
      setRows(all);
      setLoading(false);
    })();
  }, []);

  if (loading) return <Loading />;

  return (
    <div className="card">
      <div className="card-header">
        <h2>Delivery schedules</h2>
        <span className="spacer" />
        <span className="small muted">{rows.length} schedule(s) across all clients</span>
      </div>
      <div className="card-body tight">
        {rows.length === 0 ? (
          <Empty
            title="No schedules configured"
            message="Open a client to configure how often statements are generated and emailed."
            action={
              <Link className="btn btn-primary" to="/clients">
                Go to clients
              </Link>
            }
          />
        ) : (
          <div className="table-scroll">
            <table className="data">
              <thead>
                <tr>
                  <th>Client</th>
                  <th>Account</th>
                  <th>Frequency</th>
                  <th>Send time</th>
                  <th>Period covered</th>
                  <th>Last sent</th>
                  <th>Next run</th>
                  <th>State</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map(({ client, schedule }) => (
                  <tr key={schedule.id}>
                    <td>
                      <Link to={`/clients/${client.id}`}>{client.clientCode}</Link>
                      <div className="small muted">{client.clientName}</div>
                    </td>
                    <td className="mono">{schedule.accountNumber ?? 'All accounts'}</td>
                    <td>
                      <Badge tone="brand">{schedule.frequency}</Badge>
                    </td>
                    <td className="nowrap">
                      {schedule.sendTime}
                      <div className="small faint">{schedule.timezone}</div>
                    </td>
                    <td className="small muted">
                      {schedule.periodStrategy?.replace('_', ' ').toLowerCase()}
                    </td>
                    <td className="small nowrap">{formatDateTime(schedule.lastSentAt)}</td>
                    <td className="small nowrap">
                      {formatDateTime(schedule.nextRunAt)}
                      <div className="small faint">{relativeTime(schedule.nextRunAt)}</div>
                    </td>
                    <td>
                      <Badge tone={schedule.enabled ? 'ok' : 'neutral'}>
                        {schedule.enabled ? 'ON' : 'OFF'}
                      </Badge>
                      {schedule.pendingChange && (
                        <div className="small muted">
                          pending {schedule.pendingChange.operation.toLowerCase()}
                        </div>
                      )}
                    </td>
                    <td className="right">
                      <Link className="btn btn-sm" to={`/clients/${client.id}`}>
                        Manage
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
  );
}
