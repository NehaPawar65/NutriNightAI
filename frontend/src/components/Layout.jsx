import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  BarChart3, BookOpen, Brain, Database, FileText, LayoutDashboard, Menu, Moon, Salad, Utensils, X,
} from 'lucide-react';
import { getHealth } from '../api/endpoints';
import { useApi } from '../hooks/useApi';

export const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/data', label: 'Data management', icon: Database },
  { to: '/late-night', label: 'Late-night analysis', icon: Moon },
  { to: '/nutrition', label: 'Nutritional analysis', icon: Utensils },
  { to: '/ai', label: 'AI insights', icon: Brain },
  { to: '/alternatives', label: 'Healthy alternatives', icon: Salad },
  { to: '/reports', label: 'Reports', icon: FileText },
  { to: '/about', label: 'About & methodology', icon: BookOpen },
];

function StatusDot({ status }) {
  const color = status === 'UP' ? 'bg-emerald-400' : status === 'DEGRADED' ? 'bg-amber-400' : 'bg-red-400';
  return <span className={`inline-block h-2 w-2 rounded-full ${color}`} aria-hidden="true" />;
}

function SystemStatus() {
  const { data, error } = useApi(getHealth, []);
  const status = error ? 'DOWN' : data?.status;
  const text = error ? 'Backend unreachable'
    : !data ? 'Checking…'
      : data.status === 'UP' ? 'All services up'
        : `Database ${data.database.toLowerCase()} · AI ${data.aiService.toLowerCase()}`;
  return (
    <div className="flex items-center gap-2 rounded-lg bg-midnight-900/60 px-3 py-2 text-xs text-ink-muted" role="status">
      {status && <StatusDot status={status} />}
      <span>{text}</span>
    </div>
  );
}

export default function Layout({ children }) {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);

  const nav = (
    <nav aria-label="Main navigation" className="flex flex-col gap-1">
      {NAV.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
            isActive ? 'bg-cyan-500/15 font-medium text-accent-strong' : 'text-ink-secondary hover:bg-midnight-700 hover:text-ink'}`}
        >
          <Icon size={18} aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  );

  const brand = (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-cyan-500/15 text-accent">
        <BarChart3 size={20} aria-hidden="true" />
      </div>
      <div>
        <p className="text-base font-semibold leading-tight text-ink">NutriNight AI</p>
        <p className="text-[11px] leading-tight text-ink-muted">Late-night food choices &amp; NCD indicators</p>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col gap-6 border-r border-midnight-700 bg-midnight-950 p-4 lg:flex">
        {brand}
        {nav}
        <div className="mt-auto"><SystemStatus /></div>
      </aside>

      {/* Mobile header + drawer */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-midnight-700 bg-midnight-950/95 px-4 py-3 backdrop-blur lg:hidden">
        {brand}
        <button type="button" className="btn-secondary px-2.5" onClick={() => setOpen(true)} aria-label="Open navigation">
          <Menu size={20} />
        </button>
      </header>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <button type="button" className="absolute inset-0 bg-black/60" aria-label="Close navigation" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 flex w-72 flex-col gap-6 bg-midnight-950 p-4">
            <div className="flex items-center justify-between">
              {brand}
              <button type="button" className="btn-secondary px-2" onClick={() => setOpen(false)} aria-label="Close navigation">
                <X size={18} />
              </button>
            </div>
            {nav}
            <div className="mt-auto"><SystemStatus /></div>
          </div>
        </div>
      )}

      <main className="px-4 py-6 sm:px-6 lg:ml-64 lg:px-8">
        <div className="mx-auto max-w-7xl">{children}</div>
      </main>
    </div>
  );
}
