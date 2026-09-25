import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { api, USE_MOCK } from '../api';
import { useAuth } from '../auth/AuthContext';

const NAV = [
  { group: 'Operations' },
  { to: '/', label: 'Dashboard', icon: '▤', end: true },
  { to: '/clients', label: 'Clients', icon: '◫' },
  { to: '/statements', label: 'Statements', icon: '≣' },
  { to: '/schedules', label: 'Delivery schedules', icon: '◷' },
  { group: 'Control' },
  { to: '/approvals', label: 'Approvals', icon: '✓', badge: true },
  { to: '/deliveries', label: 'Delivery log', icon: '✉' },
  { to: '/audit', label: 'Audit log', icon: '☰' },
  { group: 'Configuration' },
  { to: '/settings', label: 'Settings', icon: '⚙' },
];

export function Layout({ children }: { children: React.ReactNode }) {
  const { identity, signOut, switchPersona, keycloakEnabled } = useAuth();
  const location = useLocation();
  const [pending, setPending] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    api
      .listApprovals('PENDING')
      .then((rows) => !cancelled && setPending(rows.length))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  const initials =
    (identity?.fullName ?? identity?.username ?? '?')
      .split(' ')
      .map((part) => part[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">MT</div>
          <div className="brand-text">
            <strong>MT940 Portal</strong>
            <span>Statement operations</span>
          </div>
        </div>

        {NAV.map((item, index) =>
          item.group ? (
            <div className="nav-group-title" key={`group-${index}`}>
              {item.group}
            </div>
          ) : (
            <NavLink
              key={item.to}
              to={item.to!}
              end={item.end}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              <span className="icon">{item.icon}</span>
              <span>{item.label}</span>
              {item.badge && pending > 0 && <span className="nav-badge">{pending}</span>}
            </NavLink>
          )
        )}

        <div style={{ marginTop: 'auto', padding: '12px 10px 0' }}>
          <div className="small" style={{ color: '#475569', lineHeight: 1.5 }}>
            {USE_MOCK ? 'Demonstration data' : 'Live backend'}
            <br />
            v0.1.0
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div>
            <h1>MT940 Statement Portal</h1>
            <div className="subtitle">
              Client onboarding · statement generation · scheduled delivery
            </div>
          </div>

          <div className="topbar-right">
            <span className={`mode-pill${USE_MOCK ? '' : ' live'}`}>
              {USE_MOCK ? 'MOCK API' : 'LIVE API'}
            </span>

            {!keycloakEnabled && (
              <select
                value={identity?.persona ?? 'maker'}
                onChange={(event) => switchPersona(event.target.value)}
                style={{ width: 'auto' }}
                title="Switch the acting operator (demonstration mode)"
              >
                <option value="maker">Maker</option>
                <option value="checker">Checker</option>
                <option value="admin">Admin</option>
                <option value="viewer">Read only</option>
              </select>
            )}

            <div className="user-chip">
              <div className="avatar">{initials}</div>
              <div className="who">
                <strong>{identity?.fullName ?? identity?.username}</strong>
                <span>
                  {(identity?.authorities ?? [])
                    .map((role) => role.replace('ROLE_MT940_', '').toLowerCase())
                    .join(' · ')}
                </span>
              </div>
            </div>

            <button className="btn-sm" onClick={signOut}>
              Sign out
            </button>
          </div>
        </header>

        <main className="content">{children}</main>
      </div>
    </div>
  );
}
