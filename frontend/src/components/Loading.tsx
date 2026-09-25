export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-block">
      <div style={{ display: 'grid', placeItems: 'center', gap: 10 }}>
        <span className="spinner" />
        <span className="small muted">{label}</span>
      </div>
    </div>
  );
}
