import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { useAuth } from './auth/AuthContext';
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Clients } from './pages/Clients';
import { ClientDetail } from './pages/ClientDetail';
import { Statements } from './pages/Statements';
import { Schedules } from './pages/Schedules';
import { Approvals } from './pages/Approvals';
import { Audit } from './pages/Audit';
import { Deliveries } from './pages/Deliveries';
import { Settings } from './pages/Settings';

export default function App() {
  const { identity, ready } = useAuth();

  if (!ready) {
    return (
      <div className="login-page">
        <span className="spinner" />
      </div>
    );
  }

  if (!identity) {
    return <Login />;
  }

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/clients" element={<Clients />} />
        <Route path="/clients/:id" element={<ClientDetail />} />
        <Route path="/statements" element={<Statements />} />
        <Route path="/schedules" element={<Schedules />} />
        <Route path="/approvals" element={<Approvals />} />
        <Route path="/deliveries" element={<Deliveries />} />
        <Route path="/audit" element={<Audit />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
