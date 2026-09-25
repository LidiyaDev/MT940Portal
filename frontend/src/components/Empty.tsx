import type { ReactNode } from 'react';

export function Empty({
  title,
  message,
  action,
}: {
  title: string;
  message?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <h4>{title}</h4>
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}
