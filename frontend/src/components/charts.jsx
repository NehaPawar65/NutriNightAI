/** Shared chart pieces. Colours come from CSS tokens validated for the dark card surface. */
export const COLORS = {
  series1: 'var(--series-1)',
  series2: 'var(--series-2)',
  series3: 'var(--series-3)',
  muted: '#41557d',
  grid: 'var(--grid)',
  axis: 'var(--axis)',
  tick: '#8a9ab5',
  good: 'var(--status-good)',
  warning: 'var(--status-warning)',
  critical: 'var(--status-critical)',
  unknown: 'var(--status-unknown)',
};

export const axisProps = {
  stroke: COLORS.axis,
  tick: { fill: COLORS.tick, fontSize: 12 },
  tickLine: false,
};

export const compactTick = (v) => (Math.abs(v) >= 1000 ? `${Math.round(v / 100) / 10}K` : `${Math.round(v * 10) / 10}`);

export const gridProps ={ stroke: COLORS.grid, strokeDasharray: '0', vertical: false };

/** 4px rounded data-end, square at the baseline. */
export const BAR_RADIUS_V = [4, 4, 0, 0];
export const BAR_RADIUS_H = [0, 4, 4, 0];

export function ChartTooltip({ active, payload, label, labelFormatter, valueFormatter }) {
  if (!active || !payload || !payload.length) return null;
  return (
    <div className="rounded-lg border border-midnight-600 bg-midnight-950/95 px-3 py-2 text-xs shadow-lg">
      <p className="mb-1 font-medium text-ink">{labelFormatter ? labelFormatter(label, payload) : label}</p>
      {payload.map((p) => (
        <div key={p.dataKey} className="flex items-center gap-2 text-ink-secondary">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: p.color || p.fill }} aria-hidden="true" />
          <span>{p.name}:</span>
          <span className="font-medium text-ink">{valueFormatter ? valueFormatter(p.value, p) : p.value}</span>
        </div>
      ))}
    </div>
  );
}

/** Legend with swatches beside text (text stays in ink colours). */
export function Legend({ items }) {
  return (
    <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-secondary">
      {items.map((i) => (
        <span key={i.label} className="inline-flex items-center gap-1.5">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: i.color }} aria-hidden="true" />
          {i.label}
        </span>
      ))}
    </div>
  );
}

const LEVEL_META = [
  { key: 'low', label: 'Low', color: COLORS.good },
  { key: 'medium', label: 'Medium', color: COLORS.warning },
  { key: 'high', label: 'High', color: COLORS.critical },
  { key: 'unknown', label: 'Unknown (missing data)', color: COLORS.unknown },
];

/** Stacked 100% bar per nutrient showing LOW/MEDIUM/HIGH/UNKNOWN shares, with labels and a legend. */
export function LevelDistribution({ rows }) {
  return (
    <div className="flex flex-col gap-4">
      {rows.map((r) => {
        const total = r.low + r.medium + r.high + r.unknown;
        return (
          <div key={r.nutrient}>
            <div className="mb-1 flex justify-between text-xs">
              <span className="font-medium text-ink">{r.label}</span>
              <span className="text-ink-muted">{total.toLocaleString()} orders</span>
            </div>
            <div className="flex h-5 w-full gap-[2px] overflow-hidden rounded" role="img"
              aria-label={`${r.label}: ${LEVEL_META.map((m) => `${m.label} ${r[m.key]}`).join(', ')}`}>
              {LEVEL_META.map((m) => r[m.key] > 0 && (
                <div key={m.key} title={`${m.label}: ${r[m.key].toLocaleString()} (${((r[m.key] / total) * 100).toFixed(1)}%)`}
                  style={{ width: `${(r[m.key] / total) * 100}%`, background: m.color }} />
              ))}
            </div>
            <p className="mt-1 text-[11px] text-ink-muted">
              {LEVEL_META.map((m) => `${m.label.split(' ')[0]} ${total ? ((r[m.key] / total) * 100).toFixed(0) : 0}%`).join(' · ')}
            </p>
          </div>
        );
      })}
      <Legend items={LEVEL_META} />
    </div>
  );
}

export function LevelBadge({ level }) {
  const meta = {
    LOW: ['Low', 'good'], MEDIUM: ['Medium', 'warning'], HIGH: ['High', 'danger'], UNKNOWN: ['Unknown', 'neutral'],
  }[level] || [level, 'neutral'];
  const tone = { good: 'bg-emerald-500/15 text-emerald-200', warning: 'bg-amber-400/15 text-amber-200',
    danger: 'bg-red-500/15 text-red-200', neutral: 'bg-midnight-700 text-ink-muted' }[meta[1]];
  return <span className={`rounded px-1.5 py-0.5 text-[11px] font-medium ${tone}`}>{meta[0]}</span>;
}
