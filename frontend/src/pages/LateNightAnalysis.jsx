import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import FilterBar from '../components/FilterBar';
import HourlyChart from '../components/HourlyChart';
import { AsyncSection, Badge, Card, Notice, PageHeader } from '../components/ui';
import { BAR_RADIUS_H, COLORS, ChartTooltip, axisProps, compactTick, gridProps } from '../components/charts';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { getCategories, getGeographic, getHourly } from '../api/endpoints';
import { NUTRIENTS, num, pct } from '../utils/format';

function CategoryChart({ data, dataKey, name, unit = '', formatter }) {
  return (
    <div style={{ height: Math.max(220, data.length * 38) }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart layout="vertical" data={data} margin={{ top: 0, right: 56, left: 8, bottom: 0 }}>
          <CartesianGrid {...gridProps} horizontal={false} vertical />
          <XAxis type="number" {...axisProps} tickFormatter={compactTick} unit={unit} />
          <YAxis type="category" dataKey="label" {...axisProps} width={128} />
          <Tooltip cursor={{ fill: 'rgba(148,163,184,0.08)' }} content={<ChartTooltip valueFormatter={formatter} />} />
          <Bar dataKey={dataKey} name={name} fill={COLORS.series1} radius={BAR_RADIUS_H} maxBarSize={22} isAnimationActive={false}
            label={{ position: 'right', fill: '#b4c0d3', fontSize: 11, formatter }} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export default function LateNightAnalysis() {
  const { filters } = useFilters();
  const key = JSON.stringify(filters);
  const hourly = useApi(() => getHourly(filters), [key]);
  const categories = useApi(() => getCategories(filters, true), [key]);
  const geo = useApi(() => getGeographic(filters), [key]);
  const [nutrient, setNutrient] = useState('CALORIES');
  const meta = NUTRIENTS.find((n) => n.key === nutrient);

  return (
    <>
      <PageHeader title="Late-night analysis"
        description="Ordering between 22:00 (inclusive) and 04:00 (exclusive) local time, compared across hours, categories, weekday and weekend nights, and cities." />
      <FilterBar />

      <div className="grid grid-cols-1 gap-4">
        <Card title="Hourly order volume: weekday vs weekend nights" subtitle="Average orders per analysis day of each type">
          <AsyncSection state={hourly} isEmpty={(h) => h.hours.every((x) => x.orders === 0)} emptyTitle="No orders for this selection">
            {(h) => (
              <>
                <HourlyChart hours={h.hours} mode="daytype" />
                <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-2">
                  {h.dayTypes.map((d) => (
                    <div key={d.dayType} className="rounded-lg bg-midnight-900 p-4">
                      <div className="mb-2 flex items-center gap-2">
                        <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: d.dayType === 'WEEKDAY' ? COLORS.series1 : COLORS.series2 }} aria-hidden="true" />
                        <h3 className="text-sm font-semibold text-ink">{d.dayType === 'WEEKDAY' ? 'Weekday nights' : 'Weekend nights'}</h3>
                        <span className="text-xs text-ink-muted">{d.days} days</span>
                      </div>
                      <dl className="grid grid-cols-2 gap-2 text-sm">
                        <dt className="text-ink-muted">Late-night orders / day</dt><dd className="text-right text-ink">{num(d.lateNightOrdersPerDay, 2)}</dd>
                        <dt className="text-ink-muted">Late-night share</dt><dd className="text-right text-ink">{pct(d.lateNightSharePercent)}</dd>
                        <dt className="text-ink-muted">Mean kcal / late-night order</dt><dd className="text-right text-ink">{num(d.meanKcalPerLateNightOrder)}</dd>
                        <dt className="text-ink-muted">Discretionary share (late night)</dt><dd className="text-right text-ink">{pct(d.discretionarySharePercent)}</dd>
                      </dl>
                    </div>
                  ))}
                </div>
                <p className="mt-3 text-xs text-ink-muted">{h.note} Differences are descriptive, not tested causal effects.</p>
              </>
            )}
          </AsyncSection>
        </Card>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <Card title="Food-category distribution" subtitle="Share of late-night orders">
            <AsyncSection state={categories} isEmpty={(c) => c.totalOrders === 0} emptyTitle="No late-night orders">
              {(c) => <CategoryChart data={c.categories} dataKey="sharePercent" name="Share" formatter={(v) => `${v}%`} />}
            </AsyncSection>
          </Card>
          <Card title="Nutrition by category" subtitle={`Mean ${meta.label.toLowerCase()} per late-night order (${meta.unit})`}
            actions={(
              <select className="input w-auto" aria-label="Nutrient" value={nutrient} onChange={(e) => setNutrient(e.target.value)}>
                {NUTRIENTS.map((n) => <option key={n.key} value={n.key}>{n.label}</option>)}
              </select>
            )}>
            <AsyncSection state={categories} isEmpty={(c) => c.totalOrders === 0} emptyTitle="No late-night orders">
              {(c) => (
                <CategoryChart
                  data={c.categories.map((x) => ({ label: x.label, value: x.meanPerOrder[nutrient] }))
                    .filter((x) => x.value !== null).sort((a, b) => b.value - a.value)}
                  dataKey="value" name={`${meta.label} (${meta.unit})`} formatter={(v) => num(v, 1)} />
              )}
            </AsyncSection>
          </Card>
        </div>

        <Card title="Geographic summary" subtitle="City level only; small groups are suppressed to protect privacy">
          <AsyncSection state={geo} isEmpty={(g) => g.cities.length === 0} emptyTitle="No orders for this selection">
            {(g) => (
              <>
                <div className="overflow-x-auto">
                  <table className="table-base" aria-label="Geographic summary">
                    <thead><tr><th>City</th><th className="text-right">Orders</th><th className="text-right">Late-night orders</th><th className="text-right">Late-night share</th><th className="text-right">Mean kcal / late-night order</th><th className="text-right">Discretionary share</th></tr></thead>
                    <tbody>
                      {g.cities.map((c) => (
                        <tr key={c.city}>
                          <td className="text-ink">{c.city} {c.suppressed && <Badge>suppressed</Badge>}</td>
                          <td className="text-right">{c.suppressed ? `< ${g.minGroupSize}` : num(c.orders)}</td>
                          <td className="text-right">{num(c.lateNightOrders)}</td>
                          <td className="text-right">{pct(c.lateNightSharePercent)}</td>
                          <td className="text-right">{num(c.meanKcalPerLateNightOrder)}</td>
                          <td className="text-right">{pct(c.discretionarySharePercent)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="mt-3"><Notice>{g.note}</Notice></div>
              </>
            )}
          </AsyncSection>
        </Card>
      </div>
    </>
  );
}
