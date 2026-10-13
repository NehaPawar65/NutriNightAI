import { useState } from 'react';
import { Download, Printer } from 'lucide-react';
import { apiErrorMessage } from '../api/client';
import { downloadReport, getCategories, getNutrition, getOverview, getReportMetadata } from '../api/endpoints';
import FilterBar from '../components/FilterBar';
import { AsyncSection, Card, ErrorState, PageHeader } from '../components/ui';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { hourLabel, num, pct } from '../utils/format';

const EXPORTS = [
  ['summary', 'Summary', 'Key metrics, period, data sources and limitations'],
  ['orders', 'Orders', 'Every filtered order line (anonymised references)'],
  ['hourly', 'Hourly', 'Orders per hour, weekday vs weekend'],
  ['categories', 'Categories', 'Late-night category distribution and nutrient means'],
  ['nutrition', 'Nutrition', 'Late-night nutrient totals, means and coverage'],
  ['geographic', 'Geographic', 'City summary with small-group suppression'],
];

function PrintableReport({ filters }) {
  const key = JSON.stringify(filters);
  const meta = useApi(() => getReportMetadata(filters), [key]);
  const overview = useApi(() => getOverview(filters), [key]);
  const categories = useApi(() => getCategories(filters, true), [key]);
  const nutrition = useApi(() => getNutrition(filters, true), [key]);

  return (
    <article className="card print-area" aria-label="Printable report">
      <header className="mb-4 border-b border-midnight-700 pb-4">
        <h2 className="text-xl font-semibold text-ink">NutriNight AI — Late-night food order report</h2>
        <AsyncSection state={meta}>
          {(m) => (
            <div className="mt-2 grid grid-cols-1 gap-1 text-sm text-ink-secondary sm:grid-cols-2">
              <p><strong>Selected period:</strong> {m.period.from} to {m.period.to}</p>
              <p><strong>Generated:</strong> {m.generatedAt.replace('T', ' ')}</p>
              <p><strong>Filters:</strong> city {m.filters.city}, category {m.filters.category}</p>
              <p><strong>Late-night window:</strong> {m.lateNightWindow.start}–{m.lateNightWindow.end} ({m.lateNightWindow.timezone})</p>
              <p><strong>Order data sources:</strong> {m.dataSources.join(', ') || '—'}</p>
              <p><strong>Nutrition sources:</strong> {m.nutritionSources.map((s) => s.name).join('; ') || '—'}</p>
            </div>
          )}
        </AsyncSection>
      </header>

      <section className="mb-5">
        <h3 className="mb-2 font-semibold text-ink">Overview</h3>
        <AsyncSection state={overview} isEmpty={(o) => o.totalOrders === 0} emptyTitle="No orders in this period">
          {(o) => (
            <table className="table-base"><tbody>
              <tr><td>Total orders</td><td className="text-right">{num(o.totalOrders)}</td></tr>
              <tr><td>Late-night orders</td><td className="text-right">{num(o.lateNightOrders)} ({pct(o.lateNightPercentage)})</td></tr>
              <tr><td>Peak hour / peak late-night hour</td><td className="text-right">{hourLabel(o.peakHour)} / {hourLabel(o.peakLateNightHour)}</td></tr>
              <tr><td>Mean energy per late-night order</td><td className="text-right">{num(o.meanKcalPerLateNightOrder)} kcal</td></tr>
              <tr><td>Data available</td><td className="text-right">{o.scope.dataStart} to {o.scope.dataEnd}</td></tr>
            </tbody></table>
          )}
        </AsyncSection>
      </section>

      <section className="mb-5">
        <h3 className="mb-2 font-semibold text-ink">Late-night categories</h3>
        <AsyncSection state={categories} isEmpty={(c) => c.totalOrders === 0} emptyTitle="No late-night orders">
          {(c) => (
            <table className="table-base">
              <thead><tr><th>Category</th><th className="text-right">Orders</th><th className="text-right">Share</th><th className="text-right">Mean kcal / order</th></tr></thead>
              <tbody>{c.categories.map((x) => <tr key={x.category}><td>{x.label}</td><td className="text-right">{num(x.orders)}</td><td className="text-right">{pct(x.sharePercent)}</td><td className="text-right">{num(x.meanPerOrder.CALORIES)}</td></tr>)}</tbody>
            </table>
          )}
        </AsyncSection>
      </section>

      <section className="mb-5">
        <h3 className="mb-2 font-semibold text-ink">Late-night nutrition</h3>
        <AsyncSection state={nutrition} isEmpty={(n) => n.orderCount === 0} emptyTitle="No late-night orders">
          {(n) => (
            <table className="table-base">
              <thead><tr><th>Nutrient</th><th className="text-right">Mean per order</th><th className="text-right">Mean per serving</th><th className="text-right">Total</th><th className="text-right">Coverage</th></tr></thead>
              <tbody>{n.nutrients.map((s) => <tr key={s.nutrient}><td>{s.label} ({s.unit})</td><td className="text-right">{num(s.meanPerOrder, 1)}</td><td className="text-right">{num(s.meanPerServing, 1)}</td><td className="text-right">{num(s.total)}</td><td className="text-right">{pct(s.coveragePercent)}</td></tr>)}</tbody>
            </table>
          )}
        </AsyncSection>
      </section>

      <section>
        <h3 className="mb-2 font-semibold text-ink">Methodology and limitations</h3>
        <AsyncSection state={meta}>
          {(m) => <ul className="list-disc space-y-1 pl-5 text-sm text-ink-secondary">{m.limitations.map((l) => <li key={l}>{l}</li>)}</ul>}
        </AsyncSection>
      </section>
    </article>
  );
}

export default function Reports() {
  const { filters } = useFilters();
  const [busy, setBusy] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  async function exportType(type) {
    setBusy(type);
    setError(null);
    setMessage(null);
    try {
      setMessage(`Downloaded ${await downloadReport(type, filters)}`);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader title="Reports" description="Export CSV analyses or print a report for the selected period."
        actions={<button type="button" className="btn-primary" onClick={() => window.print()}><Printer size={16} aria-hidden="true" /> Print report</button>} />
      <FilterBar />
      <div className="no-print mb-4">
        <Card title="CSV exports" subtitle="Each export applies the filters above">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {EXPORTS.map(([type, label, desc]) => (
              <button key={type} type="button" onClick={() => exportType(type)} disabled={!!busy}
                className="flex items-start gap-3 rounded-lg border border-midnight-600 bg-midnight-900 p-3 text-left transition-colors hover:border-accent disabled:opacity-60">
                <Download size={18} className="mt-0.5 text-accent" aria-hidden="true" />
                <span>
                  <span className="block text-sm font-medium text-ink">{busy === type ? 'Preparing…' : `${label} CSV`}</span>
                  <span className="block text-xs text-ink-muted">{desc}</span>
                </span>
              </button>
            ))}
          </div>
          {message && <p className="mt-3 text-sm text-ink-secondary" role="status">{message}</p>}
          {error && <div className="mt-3"><ErrorState message={error} /></div>}
        </Card>
      </div>
      <PrintableReport filters={filters} />
    </>
  );
}
