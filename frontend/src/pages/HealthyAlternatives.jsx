import { useEffect, useState } from 'react';
import { ArrowDown, ArrowRight, ArrowUp, Equal, HelpCircle } from 'lucide-react';
import { apiErrorMessage } from '../api/client';
import { compareFoods, getFoodItems, getRecommendations } from '../api/endpoints';
import { useApi } from '../hooks/useApi';
import { AsyncSection, Badge, Card, EmptyState, ErrorState, LoadingState, Notice, PageHeader, SYNTHETIC_NOTICE } from '../components/ui';
import { num } from '../utils/format';

const DIRECTION = {
  LOWER: { icon: ArrowDown, text: 'lower' },
  HIGHER: { icon: ArrowUp, text: 'higher' },
  SIMILAR: { icon: Equal, text: 'similar' },
  UNAVAILABLE: { icon: HelpCircle, text: 'unavailable' },
};
const ASSESSMENT_TONE = { FAVOURABLE: 'text-emerald-300', UNFAVOURABLE: 'text-red-300', NEUTRAL: 'text-ink-secondary', UNKNOWN: 'text-ink-muted' };

export function ComparisonTable({ comparison }) {
  const c = comparison;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium text-ink">{c.source.name}</span>
        <ArrowRight size={14} className="text-ink-muted" aria-hidden="true" />
        <span className="font-medium text-ink">{c.alternative.name}</span>
        {!c.sufficientData ? <Badge tone="warning">insufficient data</Badge>
          : c.recommended ? <Badge tone="good">may be preferable</Badge> : <Badge>not recommended</Badge>}
      </div>
      <div className="overflow-x-auto">
        <table className="table-base" aria-label={`Comparison of ${c.source.name} and ${c.alternative.name}`}>
          <thead>
            <tr>
              <th>Nutrient</th>
              <th className="text-right">{c.source.name}<br /><span className="normal-case">per serving ({c.source.servingSize ? `${num(c.source.servingSize)} ${c.source.servingUnit}` : 'size unknown'})</span></th>
              <th className="text-right">{c.alternative.name}<br /><span className="normal-case">per serving ({c.alternative.servingSize ? `${num(c.alternative.servingSize)} ${c.alternative.servingUnit}` : 'size unknown'})</span></th>
              <th className="text-right">Difference</th>
              {c.per100Comparable && <th className="text-right">Per 100 {c.source.servingUnit}<br /><span className="normal-case">source → alternative</span></th>}
            </tr>
          </thead>
          <tbody>
            {c.nutrients.map((n) => {
              const D = DIRECTION[n.direction];
              return (
                <tr key={n.nutrient}>
                  <td className="text-ink">{n.label} <span className="text-ink-muted">({n.unit})</span></td>
                  <td className="text-right">{num(n.sourcePerServing, 1)}</td>
                  <td className="text-right">{num(n.alternativePerServing, 1)}</td>
                  <td className={`text-right ${ASSESSMENT_TONE[n.assessment]}`}>
                    <span className="inline-flex items-center gap-1">
                      <D.icon size={13} aria-hidden="true" />
                      {D.text}{n.differencePercent != null && n.direction !== 'UNAVAILABLE' ? ` (${n.differencePercent > 0 ? '+' : ''}${n.differencePercent}%)` : ''}
                    </span>
                  </td>
                  {c.per100Comparable && <td className="text-right">{num(n.sourcePer100, 1)} → {num(n.alternativePer100, 1)}</td>}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="text-sm text-ink-secondary">{c.summary}</p>
      {!c.per100Comparable && c.sufficientData && <p className="text-xs text-ink-muted">Per-100 values are not compared because serving units differ or a serving size is missing.</p>}
    </div>
  );
}

function RecommendationsPanel({ items }) {
  const [itemId, setItemId] = useState('');
  useEffect(() => {
    if (!itemId && items.length) {
      const preferred = items.find((i) => /pizza|burger/i.test(i.name)) || items[0];
      setItemId(String(preferred.id));
    }
  }, [items, itemId]);
  const recs = useApi(() => getRecommendations(itemId), [itemId], { enabled: !!itemId });

  return (
    <Card title="Recommended alternatives" subtitle="Ranked by consistent improvements in energy, sugar, sodium and saturated fat per serving">
      <div className="mb-4 max-w-md">
        <label className="label" htmlFor="rec-item">Food ordered</label>
        <select id="rec-item" className="input" value={itemId} onChange={(e) => setItemId(e.target.value)}>
          {items.map((i) => <option key={i.id} value={i.id}>{i.name} · {i.categoryLabel}</option>)}
        </select>
      </div>
      <AsyncSection state={recs} isEmpty={(d) => d.length === 0}>
        {([group]) => (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-ink-secondary">{group.message}</p>
            {group.alternatives.map((alt) => (
              <div key={alt.recommendationId} className="rounded-lg border border-midnight-700 bg-midnight-900 p-4">
                <ComparisonTable comparison={alt.comparison} />
              </div>
            ))}
          </div>
        )}
      </AsyncSection>
    </Card>
  );
}

function ComparePanel({ items }) {
  const [ids, setIds] = useState({ a: '', b: '' });
  const [state, setState] = useState({ busy: false, error: null, data: null });

  useEffect(() => {
    if (items.length >= 2 && !ids.a) setIds({ a: String(items[0].id), b: String(items[1].id) });
  }, [items, ids.a]);

  async function compare(e) {
    e.preventDefault();
    if (ids.a === ids.b) {
      setState({ busy: false, error: 'Choose two different food items.', data: null });
      return;
    }
    setState({ busy: true, error: null, data: null });
    try {
      setState({ busy: false, error: null, data: await compareFoods(ids.a, ids.b) });
    } catch (err) {
      setState({ busy: false, error: apiErrorMessage(err), data: null });
    }
  }

  return (
    <Card title="Compare any two foods" subtitle="Per serving, and per 100 g/ml when serving units match">
      <form onSubmit={compare} className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        {[['a', 'Original food'], ['b', 'Possible alternative']].map(([k, label]) => (
          <div key={k}>
            <label className="label" htmlFor={`cmp-${k}`}>{label}</label>
            <select id={`cmp-${k}`} className="input" value={ids[k]} onChange={(e) => setIds({ ...ids, [k]: e.target.value })}>
              {items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
          </div>
        ))}
        <button type="submit" className="btn-primary" disabled={state.busy}>{state.busy ? 'Comparing…' : 'Compare'}</button>
      </form>
      <div className="mt-4">
        {state.busy && <LoadingState />}
        {state.error && <ErrorState message={state.error} />}
        {state.data && <ComparisonTable comparison={state.data} />}
      </div>
    </Card>
  );
}

export default function HealthyAlternatives() {
  const items = useApi(getFoodItems, []);
  return (
    <>
      <PageHeader title="Healthy alternatives"
        description="Suggestions use only the nutrition records available. A food is never called healthier because of a single nutrient, and no comparison is made when data is insufficient." />
      {items.loading && <LoadingState />}
      {items.error && <ErrorState message={items.error} onRetry={items.reload} />}
      {items.data && items.data.length === 0 && <Card><EmptyState title="No food items yet" message="Import order data first." /></Card>}
      {items.data && items.data.length > 0 && (
        <div className="flex flex-col gap-4">
          <RecommendationsPanel items={items.data} />
          <ComparePanel items={items.data} />
          <Notice>An alternative is suggested only when it is lower in at least two of energy, sugar, sodium and saturated fat (by 10% or more per serving), is higher in at most one, is suitable for the same eating occasion, and at least three of those four nutrients are known for both foods. {SYNTHETIC_NOTICE}</Notice>
        </div>
      )}
    </>
  );
}
