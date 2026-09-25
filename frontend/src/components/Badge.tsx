import type { ReactNode } from 'react';

type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral' | 'brand';

const TONES: Record<string, Tone> = {
  ACTIVE: 'ok',
  SENT: 'ok',
  APPROVED: 'ok',
  SUCCESS: 'ok',
  GENERATED: 'ok',
  DORMANT: 'warn',
  PENDING: 'warn',
  INACTIVE: 'neutral',
  SUSPENDED: 'neutral',
  CLOSED: 'neutral',
  CANCELLED: 'neutral',
  SKIPPED: 'neutral',
  REJECTED: 'danger',
  FAILED: 'danger',
  FAILURE: 'danger',
  UPDATE: 'info',
  CREATE: 'brand',
  DELETE: 'danger',
  SEND: 'info',
};

export function Badge({ children, tone }: { children: ReactNode; tone?: Tone }) {
  const resolved = tone ?? TONES[String(children)] ?? 'neutral';
  return <span className={`badge badge-${resolved}`}>{children}</span>;
}
