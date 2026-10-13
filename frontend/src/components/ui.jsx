import { AlertTriangle, Info, Inbox, Loader2, RefreshCw } from 'lucide-react';

export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold text-ink">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-sm text-ink-secondary">{description}</p>}
      </div>
      {actions && <div className="no-print flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ title, subtitle, actions, children, className = '' }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
          <div>
            {title && <h2 className="text-base font-semibold text-ink">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-xs text-ink-muted">{subtitle}</p>}
          </div>
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function StatTile({ label, value, hint, icon: Icon }) {
  return (
    <div className="card flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-secondary">{label}</p>
        {Icon && <Icon size={18} className="text-accent" aria-hidden="true" />}
      </div>
      <p className="text-3xl font-semibold text-ink">{value}</p>
      {hint && <p className="text-xs text-ink-muted">{hint}</p>}
    </div>
  );
}

export function LoadingState({ label = 'Loading…', className = '' }) {
  return (
    <div className={`flex items-center justify-center gap-2 py-10 text-sm text-ink-muted ${className}`} role="status" aria-live="polite">
      <Loader2 size={18} className="animate-spin" aria-hidden="true" />
      {label}
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-200" role="alert">
      <div className="flex items-start gap-2">
        <AlertTriangle size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
        <span>{message}</span>
      </div>
      {onRetry && (
        <button type="button" className="btn-secondary" onClick={onRetry}>
          <RefreshCw size={14} aria-hidden="true" /> Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title = 'No data', message, action }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-10 text-center text-sm text-ink-muted">
      <Inbox size={28} aria-hidden="true" />
      <p className="font-medium text-ink-secondary">{title}</p>
      {message && <p className="max-w-md">{message}</p>}
      {action}
    </div>
  );
}

export function Notice({ children, tone = 'info' }) {
  const styles = tone === 'warning'
    ? 'border-amber-400/30 bg-amber-400/10 text-amber-100'
    : 'border-cyan-400/25 bg-cyan-400/5 text-ink-secondary';
  return (
    <div className={`flex items-start gap-2 rounded-lg border p-3 text-xs ${styles}`}>
      <Info size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  );
}

/** Wraps an async section: loading -> error (with retry) -> empty -> content. */
export function AsyncSection({ state, isEmpty, emptyTitle, emptyMessage, children }) {
  if (state.loading && !state.data) return <LoadingState />;
  if (state.error) return <ErrorState message={state.error} onRetry={state.reload} />;
  if (!state.data || (isEmpty && isEmpty(state.data))) {
    return <EmptyState title={emptyTitle} message={emptyMessage} />;
  }
  return children(state.data);
}

export function Badge({ children, tone = 'neutral' }) {
  const tones = {
    neutral: 'bg-midnight-700 text-ink-secondary',
    accent: 'bg-cyan-500/15 text-accent-strong',
    violet: 'bg-violet-500/15 text-violet-200',
    warning: 'bg-amber-400/15 text-amber-200',
    danger: 'bg-red-500/15 text-red-200',
    good: 'bg-emerald-500/15 text-emerald-200',
  };
  return <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>;
}

export const SYNTHETIC_NOTICE = 'Data labelled SYNTHETIC is invented for development. Nutrition values are illustrative estimates, not verified restaurant data.';
