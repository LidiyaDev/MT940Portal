import Keycloak from 'keycloak-js';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api, setAuthToken, USE_MOCK } from '../api';
import { mock, setMockUser } from '../api/mockApi';
import type { UserProfile } from '../api/types';

export interface Identity extends UserProfile {
  /** Demo mode only: which persona is acting. */
  persona?: string;
}

interface AuthState {
  identity: Identity | null;
  ready: boolean;
  keycloakEnabled: boolean;
  signIn: (persona?: string) => void | Promise<void>;
  signOut: () => void;
  switchPersona: (persona: string) => void;
  refreshToken: () => Promise<string | null>;
}

const KEYCLOAK_ENABLED = import.meta.env.VITE_KEYCLOAK_ENABLED === 'true';

export const PERSONAS: Record<string, { username: string; fullName: string; authorities: string[] }> = {
  maker: {
    username: 'abebe.maker',
    fullName: 'Abebe Bekele (Maker)',
    authorities: ['ROLE_MT940_MAKER', 'ROLE_MT940_VIEWER'],
  },
  checker: {
    username: 'almaz.checker',
    fullName: 'Almaz Tesfaye (Checker)',
    authorities: ['ROLE_MT940_CHECKER', 'ROLE_MT940_VIEWER'],
  },
  admin: {
    username: 'sami.admin',
    fullName: 'Samuel Girma (Admin)',
    authorities: ['ROLE_MT940_ADMIN', 'ROLE_MT940_MAKER', 'ROLE_MT940_CHECKER'],
  },
  viewer: {
    username: 'hana.viewer',
    fullName: 'Hana Desta (Read only)',
    authorities: ['ROLE_MT940_VIEWER'],
  },
};

const STORAGE_KEY = 'mt940.persona';

const AuthContext = createContext<AuthState | undefined>(undefined);

let keycloakInstance: Keycloak | null = null;

function getKeycloak() {
  if (!keycloakInstance) {
    keycloakInstance = new Keycloak({
      url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
      realm: import.meta.env.VITE_KEYCLOAK_REALM || 'mt940',
      clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'mt940-portal-ui',
    });
  }
  return keycloakInstance;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [ready, setReady] = useState(false);

  const loadProfile = useCallback(async (): Promise<Identity> => {
    try {
      const profile = await api.getMe();
      return { ...profile, persona: localStorage.getItem(STORAGE_KEY) ?? 'maker' };
    } catch {
      // Fall back to the locally known persona so the UI still renders.
      const persona = localStorage.getItem(STORAGE_KEY) ?? 'maker';
      const p = PERSONAS[persona];
      return {
        username: p.username,
        fullName: p.fullName,
        authorities: p.authorities,
        isMaker: p.authorities.includes('ROLE_MT940_MAKER'),
        isChecker: p.authorities.includes('ROLE_MT940_CHECKER'),
        isAdmin: p.authorities.includes('ROLE_MT940_ADMIN'),
        roles: {
          maker: 'ROLE_MT940_MAKER',
          checker: 'ROLE_MT940_CHECKER',
          admin: 'ROLE_MT940_ADMIN',
          viewer: 'ROLE_MT940_VIEWER',
        },
        persona,
      };
    }
  }, []);

  const applyPersona = useCallback(
    async (persona: string) => {
      const p = PERSONAS[persona] ?? PERSONAS.maker;
      localStorage.setItem(STORAGE_KEY, persona);
      if (USE_MOCK) {
        setMockUser(p);
      }
      setIdentity(await loadProfile());
    },
    [loadProfile]
  );

  useEffect(() => {
    (async () => {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (KEYCLOAK_ENABLED) {
        const kc = getKeycloak();
        try {
          const authenticated = await kc.init({
            onLoad: 'login-required',
            checkLoginIframe: false,
            pkceMethod: 'S256',
          });
          if (!authenticated) {
            setReady(true);
            return;
          }
          setAuthToken(kc.token ?? null);
          const profile = await loadProfile();
          setIdentity({ ...profile, persona: 'keycloak' });
          setReady(true);
          return;
        } catch (error) {
          console.error('Keycloak initialisation failed', error);
        }
      } else if (stored) {
        if (USE_MOCK) setMockUser(PERSONAS[stored] ?? PERSONAS.maker);
        setIdentity(await loadProfile());
      }
      setReady(true);
    })();
  }, [loadProfile]);

  const signIn = useCallback(
    (persona = 'maker') => {
      if (KEYCLOAK_ENABLED) {
        return getKeycloak().login();
      }
      return void applyPersona(persona);
    },
    [applyPersona]
  );

  const signOut = useCallback(() => {
    setIdentity(null);
    setAuthToken(null);
    if (KEYCLOAK_ENABLED) {
      getKeycloak().logout();
    }
  }, []);

  const refreshToken = useCallback(async () => {
    if (KEYCLOAK_ENABLED) {
      const kc = getKeycloak();
      try {
        await kc.updateToken(60);
      } catch {
        kc.login();
        return null;
      }
      setAuthToken(kc.token ?? null);
      return kc.token ?? null;
    }
    return localStorage.getItem('mt940.token');
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      identity,
      ready,
      keycloakEnabled: KEYCLOAK_ENABLED,
      signIn,
      signOut,
      switchPersona: (persona: string) => void applyPersona(persona),
      refreshToken,
    }),
    [identity, ready, signIn, signOut, applyPersona, refreshToken]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}

/** Guards a control that only makers (or admins) may use. */
export function usePermissions() {
  const { identity } = useAuth();
  return {
    canMake: Boolean(identity?.isMaker || identity?.isAdmin),
    canCheck: Boolean(identity?.isChecker || identity?.isAdmin),
    canAdmin: Boolean(identity?.isAdmin),
    identity,
  };
}
