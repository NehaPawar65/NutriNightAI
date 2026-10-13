import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Clock, Moon, Percent, ShoppingBag } from 'lucide-react';
import FilterBar from '../components/FilterBar';
import HourlyChart from '../components/HourlyChart';
import { AsyncSection, Badge, Card, EmptyState, ErrorState, LoadingState, Notice, PageHeader, StatTile, SYNTHETIC_NOTICE } from '../components/ui';
import { BAR_RADIUS_H, COLORS, ChartTooltip, LevelDistribution, axisProps, gridProps } from '../components/charts';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { getHourly, getInsights, getNutrition, getOverview } from '../api/endpoints';
import { dateTime, hourLabel, num, pct } from '../utils/format';

export default function Dashboard() {
  const { filters } = useFilters();
  const key = JSON.stringify(filters);
  const overview = useApi(() => getOverview(filters), [key]);
  const hourly = useApi(() => getHourly(filters), [key]);
  const nutrition = useApi(() => getNutrition(filters, true), [key]);
  const insights = useApi(() => getInsights(undefined, 4), []);

  const o = overview.data;
  const hasData = o && o.totalOrders > 0;

  return (
    <>
      <PageHeader title="Dashboard"
        description="Understand nighttime eating. Analyse dietary risk indicators. Promote healthier choices. All figures are computed from the imported orders." />
      <FilterBar />

      {overview.loading && !o && <LoadingState />}
      {overview.error && <ErrorState message={overview.error} onRetry={overview.reload} />}
      {o && !hasData && (
        <Card>
          <EmptyState title="No orders for this selection"
            message="Import the sample CSV on the Data management page, or widen the filters."
            action={<Link to="/data" className="btn-primary mt-2">Go to data management</Link>} />
        </Card>
      )}

      {hasData && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatTile label="Total orders" value={num(o.totalOrders)} icon={ShoppingBag}
              hint={`${o.analysisDays} analysis days · ${o.cities} cities`} />
            <StatTile label="Late-night orders" value={num(o.lateNightOrders)} icon={Moon}
              hint={`${o.window.start}–${o.window.end} ${o.window.timezone}`} />
            <StatTile label="Share placed late at night" value={pct(o.lateNightPercentage)} icon={Percent}
              hint="of all orders in the selection" />
            <StatTile label="Peak late-night hour" value={hourLabel(o.peakLateNightHour)} icon={Clock}
              hint={`Overall peak ${hourLabel(o.peakHour)} · mean ${num(o.meanKcalPerLateNightOrder)} kcal/late-night order`} />
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Card title="Orders by hour of day" subtitle="Local time, starting at noon so the night is continuous" className="xl:col-span-2">
              <AsyncSection state={hourly}>{(h) => <HourlyChart hours={h.hours} />}</AsyncSection>
            </Card>
            <Card title="Most common late-night categories" subtitle="Share of late-night orders">
              {o.topLateNightCategories.length === 0 ? <EmptyState title="No late-night orders" /> : (
                <div style={{ height: 280 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart layout="vertical" data={o.topLateNightCategories} margin={{ top: 0, right: 40, left: 8, bottom: 0 }}>
                      <CartesianGrid {...gridProps} horizontal={false} vertical />
                      <XAxis type="number" {...axisProps} unit="%" />
                      <YAxis type="category" dataKey="label" {...axisProps} width={112} />
                      <Tooltip cursor={{ fill: 'rgba(148,163,184,0.08)' }}
                        content={<ChartTooltip valueFormatter={(v, p) => `${v}% (${num(p.payload.orders)} orders)`} />} />
                      <Bar dataKey="sharePercent" name="Share" fill={COLORS.series1} radius={BAR_RADIUS_H} maxBarSize={22}
                        isAnimationActive={false} label={{ position: 'right', fill: '#b4c0d3', fontSize: 11, formatter: (v) => `${v}%` }} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
            <Card title="Nutritional indicator distribution" subtitle="Late-night orders by front-of-pack level (per 100 g / 100 ml)">
              <AsyncSection state={nutrition} isEmpty={(n) => n.orderCount === 0} emptyTitle="No late-night orders">
                {(n) => <LevelDistribution rows={n.levelDistribution} />}
              </AsyncSection>
            </Card>
            <Card title="Recent analysis results" subtitle="Saved AI analyses"
              actions={<Link to="/ai" className="text-xs font-medium text-accent hover:underline">Open AI insights</Link>}>
              <AsyncSection state={insights} isEmpty={(d) => d.content.length === 0} emptyTitle="No analyses yet"
                emptyMessage="Run an analysis on the AI insights page; results are saved and listed here.">
                {(d) => (
                  <ul className="divide-y divide-midnight-700">
                    {d.content.map((r) => (
                      <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
                        <div>
                          <Badge tone={r.analysisType === 'CLUSTERING' ? 'violet' : 'accent'}>
                            {r.analysisType === 'CLUSTERING' ? 'Clustering' : 'Pattern analysis'}
                          </Badge>
                          <span className="ml-2 text-ink-secondary">{r.periodStart} → {r.periodEnd}</span>
                        </div>
                        <span className="text-xs text-ink-muted">
                          {r.analysisType === 'PATTERN_ANALYSIS' && r.result?.indicator?.score != null && (
                            <>Indicator {r.result.indicator.score} · </>
                          )}
                          {r.analysisType === 'CLUSTERING' && r.result?.available && <>k={r.result.k}, silhouette {r.result.silhouette} · </>}
                          {dateTime(r.createdAt)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </AsyncSection>
            </Card>
          </div>
          <div className="mt-4">
            <Notice>{SYNTHETIC_NOTICE} Data sources in this selection: {o.dataSources.join(', ') || '—'}. Figures describe ordering behaviour and nutrient indicators; they are not diagnoses.</Notice>
          </div>
        </>
      )}
    </>
  );
}
