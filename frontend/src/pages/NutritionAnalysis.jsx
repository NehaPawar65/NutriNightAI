import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import FilterBar from '../components/FilterBar';
import { AsyncSection, Badge, Card, Notice, PageHeader, SYNTHETIC_NOTICE } from '../components/ui';
import { BAR_RADIUS_H, COLORS, ChartTooltip, LevelBadge, LevelDistribution, Legend, axisProps, compactTick, gridProps } from '../components/charts';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { getNutrition } from '../api/endpoints';
import { NUTRIENTS, num, pct } from '../utils/format';

export default function NutritionAnalysis() {
  const { filters } = useFilters();
  const [lateOnly, setLateOnly] = useState(true);
  const [nutrient, setNutrient] = useState('SUGAR');
  const nutrition = useApi(() => getNutrition(filters, lateOnly), [JSON.stringify(filters), lateOnly]);
  const meta = NUTRIENTS.find((n) => n.key === nutrient);

  return (
    <>
      <PageHeader title="Nutritional analysis"
        description="Energy, sugar, sodium, saturated fat and protein for the selected orders. Totals multiply per-serving values by quantity; missing values are reported separately, never counted as zero."
        actions={(
          <div className="flex rounded-lg border border-midnight-600 p-0.5" role="group" aria-label="Order scope">
            {[[true, 'Late-night orders'], [false, 'All orders']].map(([v, label]) => (
              <button key={label} type="button" aria-pressed={lateOnly === v} onClick={() => setLateOnly(v)}
                className={`rounded-md px-3 py-1.5 text-sm ${lateOnly === v ? 'bg-cyan-500/20 text-accent-strong' : 'text-ink-secondary hover:text-ink'}`}>
                {label}
              </button>
            ))}
          </div>
        )} />
      <FilterBar />

      <AsyncSection state={nutrition} isEmpty={(n) => n.orderCount === 0} emptyTitle="No orders for this selection"
        emptyMessage="Widen the filters or import data.">
        {(n) => (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
              {n.nutrients.map((s) => (
                <div key={s.nutrient} className="card">
                  <p className="text-sm text-ink-secondary">{s.label}</p>
                  <p className="mt-1 text-2xl font-semibold text-ink">{num(s.meanPerOrder, 1)} <span className="text-sm font-normal text-ink-muted">{s.unit}/order</span></p>
                  <dl className="mt-2 grid grid-cols-2 gap-1 text-xs">
                    <dt className="text-ink-muted">Per serving</dt><dd className="text-right text-ink-secondary">{num(s.meanPerServing, 1)} {s.unit}</dd>
                    <dt className="text-ink-muted">Total</dt><dd className="text-right text-ink-secondary">{num(s.total)} {s.unit}</dd>
                    <dt className="text-ink-muted">Coverage</dt>
                    <dd className="text-right">{s.ordersMissing > 0 ? <Badge tone="warning">{pct(s.coveragePercent)}</Badge> : <span className="text-ink-secondary">{pct(s.coveragePercent)}</span>}</dd>
                    <dt className="text-ink-muted">Missing</dt><dd className="text-right text-ink-secondary">{num(s.ordersMissing)} orders</dd>
                  </dl>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
              <Card title="Comparison between categories" subtitle={`Mean ${meta.label.toLowerCase()} per order vs per serving (${meta.unit})`}
                actions={(
                  <select className="input w-auto" aria-label="Nutrient to compare" value={nutrient} onChange={(e) => setNutrient(e.target.value)}>
                    {NUTRIENTS.map((x) => <option key={x.key} value={x.key}>{x.label}</option>)}
                  </select>
                )}>
                {(() => {
                  const data = n.byCategory.map((c) => {
                    const s = c.nutrients.find((x) => x.nutrient === nutrient);
                    return { label: c.label, perOrder: s.meanPerOrder, perServing: s.meanPerServing, coverage: s.coveragePercent };
                  }).filter((d) => d.perOrder !== null).sort((a, b) => b.perOrder - a.perOrder);
                  return (
                    <>
                      <div style={{ height: Math.max(240, data.length * 52) }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart layout="vertical" data={data} margin={{ top: 0, right: 24, left: 8, bottom: 0 }} barGap={2}>
                            <CartesianGrid {...gridProps} horizontal={false} vertical />
                            <XAxis type="number" {...axisProps} tickFormatter={compactTick} />
                            <YAxis type="category" dataKey="label" {...axisProps} width={128} />
                            <Tooltip cursor={{ fill: 'rgba(148,163,184,0.08)' }}
                              content={<ChartTooltip valueFormatter={(v) => `${num(v, 1)} ${meta.unit}`} />} />
                            <Bar dataKey="perOrder" name="Mean per order" fill={COLORS.series1} radius={BAR_RADIUS_H} maxBarSize={14} isAnimationActive={false} />
                            <Bar dataKey="perServing" name="Mean per serving" fill={COLORS.series3} radius={BAR_RADIUS_H} maxBarSize={14} isAnimationActive={false} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                      <Legend items={[{ label: 'Mean per order (serving × quantity)', color: COLORS.series1 }, { label: 'Mean per serving', color: COLORS.series3 }]} />
                    </>
                  );
                })()}
              </Card>
              <Card title="Nutritional indicator distribution" subtitle="Orders by front-of-pack level, per 100 g (food) or 100 ml (drinks)">
                <LevelDistribution rows={n.levelDistribution} />
                <p className="mt-4 text-xs text-ink-muted">Thresholds: {n.thresholds.source}{' '}
                  {n.thresholds.sourceUrl && <a className="text-accent hover:underline" href={n.thresholds.sourceUrl} target="_blank" rel="noreferrer">Source</a>}</p>
              </Card>
            </div>

            <Card title="Food items and nutrition sources" subtitle="Per-serving values as stored; missing values are flagged">
              <div className="overflow-x-auto">
                <table className="table-base" aria-label="Food items">
                  <thead>
                    <tr><th>Food item</th><th>Category</th><th className="text-right">Orders</th><th className="text-right">Serving</th><th className="text-right">kcal</th><th className="text-right">Sugar g</th><th className="text-right">Sodium mg</th><th className="text-right">Sat fat g</th><th className="text-right">Protein g</th><th>Levels (sugar / sat fat / sodium)</th><th>Source</th></tr>
                  </thead>
                  <tbody>
                    {n.foodItems.map(({ item, orders, levels }) => (
                      <tr key={item.id}>
                        <td className="text-ink">{item.name}{item.missingNutrients.length > 0 && <span className="ml-2"><Badge tone="warning">missing: {item.missingNutrients.map((m) => m.toLowerCase().replace('_', ' ')).join(', ')}</Badge></span>}</td>
                        <td>{item.categoryLabel}</td>
                        <td className="text-right">{num(orders)}</td>
                        <td className="text-right">{item.servingSize ? `${num(item.servingSize)} ${item.servingUnit}` : '—'}</td>
                        <td className="text-right">{num(item.caloriesKcal, 1)}</td>
                        <td className="text-right">{num(item.sugarG, 1)}</td>
                        <td className="text-right">{num(item.sodiumMg)}</td>
                        <td className="text-right">{num(item.saturatedFatG, 1)}</td>
                        <td className="text-right">{num(item.proteinG, 1)}</td>
                        <td><div className="flex gap-1"><LevelBadge level={levels.SUGAR} /><LevelBadge level={levels.SATURATED_FAT} /><LevelBadge level={levels.SODIUM} /></div></td>
                        <td className="max-w-[220px] text-xs">{item.nutritionSourceName || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
            <Notice>
              <ul className="list-disc pl-4">{n.definitions.map((d) => <li key={d}>{d}</li>)}</ul>
              <p className="mt-1">{SYNTHETIC_NOTICE}</p>
            </Notice>
          </div>
        )}
      </AsyncSection>
    </>
  );
}
