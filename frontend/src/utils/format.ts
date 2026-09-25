export const formatDateTime = (value?: string | null) =>
  value
    ? new Date(value).toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

export const formatDate = (value?: string | null) =>
  value
    ? new Date(value).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
      })
    : '—';

export const formatMoney = (value?: number | null, currency = '') => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return `${currency ? currency + ' ' : ''}${Number(value).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
};

export const relativeTime = (value?: string | null) => {
  if (!value) return '—';
  const diff = new Date(value).getTime() - Date.now();
  const abs = Math.abs(diff);
  const minutes = Math.round(abs / 60000);
  const suffix = diff >= 0 ? 'from now' : 'ago';
  if (minutes < 1) return `just now`;
  if (minutes < 60) return `${minutes} min ${suffix}`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ${suffix}`;
  const days = Math.round(hours / 24);
  return `${days} d ${suffix}`;
};

export const DAY_NAMES = [
  '',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
];

export const todayIso = () => new Date().toISOString().slice(0, 10);

export const isoDaysAgo = (days: number) =>
  new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);

export const downloadText = (fileName: string, content: string) => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

export const parseAccountList = (text: string) =>
  text
    .split(/[\n,;]+/)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [accountNumber, ...rest] = entry.split(/[\t|]+|\s{2,}/);
      return {
        accountNumber: accountNumber.trim(),
        accountName: rest.join(' ').trim() || undefined,
      };
    });
