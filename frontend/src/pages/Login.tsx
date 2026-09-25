import { useAuth, PERSONAS } from '../auth/AuthContext';
import { USE_MOCK } from '../api';

const DESCRIPTIONS: Record<string, string> = {
  maker: 'Creates and edits clients, accounts, schedules and recipients. Cannot approve.',
  checker: 'Reviews and approves or rejects what a maker submitted. Cannot approve own work.',
  admin: 'Holds both maker and checker authority. Intended for break-glass use only.',
  viewer: 'Read only access to clients, statements, schedules and the audit trail.',
};

export function Login() {
  const { signIn, keycloakEnabled } = useAuth();

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand" style={{ padding: '0 0 16px' }}>
          <div className="brand-mark">MT</div>
          <div className="brand-text">
            <strong style={{ fontSize: 15 }}>MT940 Statement Portal</strong>
            <span>Client statements &amp; scheduled delivery</span>
          </div>
        </div>

        <h1>Sign in</h1>
        <p className="lede">
          {keycloakEnabled
            ? 'Authentication is handled by Keycloak. You will be redirected to the realm login page.'
            : 'Keycloak is not configured, so the portal is running with a local operator picker. Pick the role you want to act as.'}
        </p>

        {keycloakEnabled ? (
          <button className="btn btn-primary btn-block" onClick={() => signIn()}>
            Continue with Keycloak
          </button>
        ) : (
          <div className="persona-picker">
            {Object.entries(PERSONAS).map(([key, persona]) => (
              <button key={key} className="persona-option" onClick={() => signIn(key)}>
                <strong>{persona.fullName}</strong>
                <span>{DESCRIPTIONS[key]}</span>
              </button>
            ))}
          </div>
        )}

        <div className="divider" />
        <div className="kv-note">
          <strong>Data source:</strong>{' '}
          {USE_MOCK
            ? 'in-memory demonstration data. Set VITE_USE_MOCK=false and run the Spring Boot service to use the real API and Oracle database.'
            : 'live Spring Boot API.'}
        </div>
      </div>
    </div>
  );
}
