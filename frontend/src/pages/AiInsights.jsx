import { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Brain, Play, Tags } from 'lucide-react';
import { apiErrorMessage } from '../api/client';
import { classifyFoods, getInsights, getModelStatus, runAnalysis } from '../api/endpoints';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { AsyncSection, Badge, Card, EmptyState, ErrorState, LoadingState, Notice, PageHeader } from '../components/ui';
import { BAR_RADIUS_H, COLORS, ChartTooltip, axisProps, gridProps } from '../components/charts';
import { categoryLabel, dateTime, num, pct } from '../utils/format';

function MethodBadge({ method }) {
  if (method === 'RULE_BASED') return <Badge tone="accent">Rule-based</Badge>;
  if (method === 'ML_MODEL') return <Badge tone="violet">Trained model</Badge>;
  return <Badge>Default (no match)</Badge>;
}

function ClassifyPanel() {
  const [text, setText] = useState('Paneer tikka masala\nChocolate milkshake\nGrilled fish with salad\nMasala peanuts');
  const [method, setMethod] = useState('auto');
  const [state, setState] = useState({ busy: false, error: null, data: null });

  async function submit(e) {
    e.preventDefault();
    const items = text.split('\n').map((s) => s.trim()).filter(Boolean);
    if (!items.length) {
      setState({ busy: false, error: 'Enter at least one food description (one per line).', data: null });
      return;
    }
    if (items.length > 100) {
      setState({ busy: false, error: 'At most 100 descriptions per request.', data: null });
      return;
    }
    setState({ busy: true, error: null, data: null });
    try {
      setState({ busy: false, error: null, data: await classifyFoods(items, method) });
    } catch (err) {
      setState({ busy: false, error: apiErrorMessage(err), data: null });
    }
  }

  return (
    <Card title="Food-category classification" subtitle="Keyword rules run first; the trained TF-IDF + logistic regression model is used only when no rule matches">
      <form onSubmit={submit} className="grid gap-3 md:grid-cols-[1fr_200px]">
        <div>
          <label className="label" htmlFor="classify-text">Food descriptions (one per line)</label>
          <textarea id="classify-text" rows={5} className="input font-mono" value={text} onChange={(e) => setText(e.target.value)} />
        </div>
        <div className="flex flex-col gap-3">
          <div>
            <label className="label" htmlFor="classify-method">Method</label>
            <select id="classify-method" className="input" value={method} onChange={(e) => setMethod(e.target.value)}>
              <option value="auto">Auto (rules, then model)</option>
              <option value="rules">Rules only</option>
              <option value="model">Trained model only</option>
            </select>
          </div>
          <button type="submit" className="btn-primary" disabled={state.busy}><Tags size={16} aria-hidden="true" /> {state.busy ? 'Classifying…' : 'Classify'}</button>
        </div>
      </form>
      {state.error && <div className="mt-3"><ErrorState message={state.error} /></div>}
      {state.data && (
        <div className="mt-4 overflow-x-auto">
          <table className="table-base" aria-label="Classification results">
            <thead><tr><th>Description</th><th>Category</th><th>Method</th><th className="text-right">Confidence</th><th>Why</th></tr></thead>
            <tbody>
              {state.data.predictions.map((p, i) => (
                <tr key={`${p.text}-${i}`}>
                  <td className="text-ink">{p.text}</td>
                  <td>{categoryLabel(p.category)}</td>
                  <td><MethodBadge method={p.method} /></td>
                  <td className="text-right">{p.confidence == null ? <span className="text-ink-muted">n/a</span> : pct(p.confidence * 100, 0)}</td>
                  <td className="text-xs">
                    {p.matchedKeywords.length > 0 && <>Keywords: {p.matchedKeywords.join(', ')}</>}
                    {p.topFeatures.length > 0 && <>Top features: {p.topFeatures.slice(0, 4).map((f) => f.feature.replace(/^(word|char):/, '')).join(', ')}
                      {p.alternatives.length > 0 && <> · next: {p.alternatives.map((a) => `${categoryLabel(a.category)} ${pct(a.probability * 100, 0)}`).join(', ')}</>}</>}
                    {p.method === 'DEFAULT' && p.explanation}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-2 text-xs text-ink-muted">Model {state.data.modelAvailable ? `version ${state.data.modelVersion}` : 'not available (rules only)'}. Rule-based results carry no probability.</p>
        </div>
      )}
    </Card>
  );
}

function IndicatorView({ indicator }) {
  if (indicator.score == null) return <EmptyState title="Indicator unavailable" message="Not enough data to compute any component." />;
  const comps = indicator.components.map((c) => ({ ...c, points: c.contributionPoints ?? 0 }));
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-[220px_1fr]">
      <div className="flex flex-col items-start justify-center rounded-lg bg-midnight-900 p-4">
        <p className="text-sm text-ink-secondary">{indicator.name}</p>
        <p className="mt-1 text-5xl font-semibold text-ink">{indicator.score}<span className="text-lg text-ink-muted"> / 100</span></p>
        <div className="mt-2"><Badge tone={indicator.band === 'HIGHER' ? 'danger' : indicator.band === 'MODERATE' ? 'warning' : 'good'}>{indicator.band.toLowerCase()} band</Badge></div>
        <p className="mt-3 text-[11px] text-ink-muted">Weight coverage {pct(indicator.weightCoverage * 100, 0)} · {num(indicator.lateNightOrders)} late-night orders</p>
      </div>
      <div>
        <p className="mb-2 text-xs text-ink-muted">Contribution of each component (points out of 100)</p>
        <div style={{ height: 240 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart layout="vertical" data={comps} margin={{ top: 0, right: 48, left: 8, bottom: 0 }}>
              <CartesianGrid {...gridProps} horizontal={false} vertical />
              <XAxis type="number" {...axisProps} />
              <YAxis type="category" dataKey="label" {...axisProps} width={190} tick={{ fill: '#8a9ab5', fontSize: 11 }} />
              <Tooltip cursor={{ fill: 'rgba(148,163,184,0.08)' }}
                content={<ChartTooltip valueFormatter={(v, p) => `${v} pts (raw ${p.payload.rawValue ?? 'n/a'} ${p.payload.unit}, ref ${p.payload.reference}, weight ${p.payload.weight})`} />} />
              <Bar dataKey="points" name="Contribution" fill={COLORS.series1} radius={BAR_RADIUS_H} maxBarSize={20} isAnimationActive={false}
                label={{ position: 'right', fill: '#b4c0d3', fontSize: 11 }} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <ul className="mt-2 space-y-1 text-xs text-ink-muted">
          {indicator.components.filter((c) => !c.available).map((c) => <li key={c.key}>{c.label}: excluded ({c.unavailableReason})</li>)}
        </ul>
      </div>
      <div className="lg:col-span-2">
        <Notice>
          <p><strong>Formula:</strong> {indicator.formula}. {indicator.missingDataPolicy}</p>
          <p className="mt-1">{indicator.bandNote} {indicator.disclaimer}</p>
        </Notice>
      </div>
    </div>
  );
}

function ClusteringView({ result }) {
  if (!result.available) return <EmptyState title="Clustering not available" message={result.reason} />;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2 text-sm">
        <Badge tone="violet">k = {result.k}</Badge>
        <Badge tone={result.quality === 'WEAK' ? 'warning' : 'accent'}>silhouette {result.silhouette} · {result.quality.toLowerCase()}</Badge>
        <Badge>{num(result.unitsAnalyzed)} city-nights ({num(result.unitsExcluded)} excluded with &lt; {result.minOrdersPerUnit} orders)</Badge>
      </div>
      <p className="text-sm text-ink-secondary">{result.qualityNote}</p>
      <div className="overflow-x-auto">
        <table className="table-base" aria-label="Clusters">
          <thead><tr><th>Cluster</th><th className="text-right">City-nights</th><th className="text-right">Orders</th><th className="text-right">Discretionary</th><th className="text-right">kcal</th><th className="text-right">Sugar g</th><th className="text-right">Sodium mg</th><th>Description</th><th>Top cities</th></tr></thead>
          <tbody>
            {result.clusters.map((c) => (
              <tr key={c.id}>
                <td className="text-ink">#{c.id + 1}</td>
                <td className="text-right">{num(c.size)} ({pct(c.share * 100, 0)})</td>
                <td className="text-right">{num(c.centroid.orders, 1)}</td>
                <td className="text-right">{pct(c.centroid.discretionary_share * 100, 0)}</td>
                <td className="text-right">{num(c.centroid.mean_kcal)}</td>
                <td className="text-right">{num(c.centroid.mean_sugar, 1)}</td>
                <td className="text-right">{num(c.centroid.mean_sodium)}</td>
                <td className="text-xs">{c.description}</td>
                <td className="text-xs">{c.topCities.join(', ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-ink-muted">Candidates: {result.candidates.map((c) => `k=${c.k} (${c.silhouette})`).join(', ')}. {result.disclaimer}</p>
    </div>
  );
}

function AnalysisPanel() {
  const { filters } = useFilters();
  const history = useApi(() => getInsights('PATTERN_ANALYSIS', 10), []);
  const [selected, setSelected] = useState(null);
  const [clustering, setClustering] = useState(null);
  const [run, setRun] = useState({ busy: false, error: null });

  useEffect(() => {
    if (!selected && history.data?.content?.length) setSelected(history.data.content[0]);
  }, [history.data, selected]);

  useEffect(() => {
    if (!selected) return;
    getInsights('CLUSTERING', 10)
      .then((d) => setClustering(d.content.find((c) => Math.abs(new Date(c.createdAt) - new Date(selected.createdAt)) < 60000) || null))
      .catch(() => setClustering(null));
  }, [selected]);

  async function analyze() {
    setRun({ busy: true, error: null });
    try {
      const res = await runAnalysis({ from: filters.from || null, to: filters.to || null, city: filters.city || null, includeClustering: true });
      setSelected(res.patternAnalysis);
      setClustering(res.clustering);
      setRun({ busy: false, error: res.clusteringError ? `Clustering skipped: ${res.clusteringError}` : null });
      history.reload();
    } catch (err) {
      setRun({ busy: false, error: apiErrorMessage(err) });
    }
  }

  const a = selected?.result;
  return (
    <Card title="Consumption-pattern analysis" subtitle="Runs on the stored orders for the global date range and city filters"
      actions={<button type="button" className="btn-primary" onClick={analyze} disabled={run.busy}><Play size={16} aria-hidden="true" /> {run.busy ? 'Analysing…' : 'Run analysis'}</button>}>
      {run.error && <div className="mb-3"><ErrorState message={run.error} /></div>}
      {history.loading && !a && <LoadingState />}
      {history.error && <ErrorState message={history.error} onRetry={history.reload} />}
      {!history.loading && !a && !history.error && <EmptyState title="No analysis yet" message="Press “Run analysis” to analyse the selected period." />}
      {history.data?.content?.length > 1 && (
        <div className="mb-4">
          <label className="label" htmlFor="analysis-history">Saved analyses</label>
          <select id="analysis-history" className="input max-w-md" value={selected?.id || ''}
            onChange={(e) => setSelected(history.data.content.find((r) => String(r.id) === e.target.value))}>
            {history.data.content.map((r) => <option key={r.id} value={r.id}>#{r.id} · {r.periodStart} → {r.periodEnd} · {dateTime(r.createdAt)}</option>)}
          </select>
        </div>
      )}
      {a && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {[['Orders analysed', num(a.summary.totalOrders)], ['Late-night share', pct(a.summary.lateNightShare * 100)],
              ['Peak late-night hour', `${String(a.summary.peakLateNightHour ?? '—').padStart(2, '0')}:00`], ['Analysis days', num(a.summary.analysisDays)]]
              .map(([l, v]) => <div key={l} className="rounded-lg bg-midnight-900 p-3"><p className="text-xs text-ink-muted">{l}</p><p className="text-lg font-semibold text-ink">{v}</p></div>)}
          </div>
          <div>
            <h3 className="mb-2 text-sm font-semibold text-ink">Key observations</h3>
            <ul className="list-disc space-y-1 pl-5 text-sm text-ink-secondary">{a.insights.map((i) => <li key={i}>{i}</li>)}</ul>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-semibold text-ink">Dietary pattern indicator</h3>
            <IndicatorView indicator={a.indicator} />
          </div>
          {a.recurrence?.available && (
            <div>
              <h3 className="mb-2 text-sm font-semibold text-ink">Recurring late-night categories</h3>
              <p className="mb-2 text-xs text-ink-muted">{a.recurrence.note}</p>
              <div className="overflow-x-auto">
                <table className="table-base" aria-label="Recurrence">
                  <thead><tr><th>Category</th><th className="text-right">Nights with orders</th><th className="text-right">Share of {a.recurrence.nightsInPeriod} nights</th></tr></thead>
                  <tbody>{a.recurrence.categories.map((c) => <tr key={c.category}><td className="text-ink">{c.label}</td><td className="text-right">{c.nightsWithOrders}</td><td className="text-right">{pct(c.shareOfNights * 100, 0)}</td></tr>)}</tbody>
                </table>
              </div>
            </div>
          )}
          <div>
            <h3 className="mb-2 text-sm font-semibold text-ink">Exploratory clustering of city-nights</h3>
            {clustering ? <ClusteringView result={clustering.result} /> : <p className="text-sm text-ink-muted">No clustering result saved with this analysis.</p>}
          </div>
          <Notice><ul className="list-disc pl-4">{a.methodology.map((m) => <li key={m}>{m}</li>)}</ul></Notice>
        </div>
      )}
    </Card>
  );
}

function ModelStatusPanel() {
  const status = useApi(getModelStatus, []);
  return (
    <Card title="Model evaluation" subtitle="Reported by the AI service; versions are recorded in the database">
      <AsyncSection state={status}>
        {(s) => {
          if (!s.aiServiceReachable) return <ErrorState message={s.message} onRetry={status.reload} />;
          const c = s.modelInfo.classifier;
          return (
            <div className="flex flex-col gap-3 text-sm">
              {!c.available ? <Notice tone="warning">{c.note}</Notice> : (
                <>
                  <div className="flex flex-wrap gap-2">
                    <Badge tone="violet"><Brain size={12} aria-hidden="true" /> {c.modelName}</Badge>
                    <Badge>v{c.modelVersion}</Badge><Badge>{c.datasetVersion}</Badge><Badge>{c.nTrainingExamples} labelled examples</Badge>
                  </div>
                  <p className="text-ink-secondary">{c.algorithm}</p>
                  <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
                    {[['Hold-out accuracy', c.metrics.holdoutAccuracy], ['Hold-out macro-F1', c.metrics.holdoutMacroF1],
                      ['5-fold CV macro-F1', `${c.metrics.cvMacroF1Mean} ± ${c.metrics.cvMacroF1Std}`], ['Hold-out size', c.metrics.holdoutSize]]
                      .map(([l, v]) => <div key={l} className="rounded-lg bg-midnight-900 p-3"><dt className="text-xs text-ink-muted">{l}</dt><dd className="text-lg font-semibold text-ink">{v}</dd></div>)}
                  </dl>
                  <div className="overflow-x-auto">
                    <table className="table-base" aria-label="Per-class metrics">
                      <thead><tr><th>Category</th><th className="text-right">Precision</th><th className="text-right">Recall</th><th className="text-right">F1</th><th className="text-right">Support</th></tr></thead>
                      <tbody>{Object.entries(c.metrics.perClass).map(([k, v]) => (
                        <tr key={k}><td className="text-ink">{categoryLabel(k)}</td><td className="text-right">{v.precision}</td><td className="text-right">{v.recall}</td><td className="text-right">{v.f1}</td><td className="text-right">{v.support}</td></tr>
                      ))}</tbody>
                    </table>
                  </div>
                  <p className="text-xs text-ink-muted">{c.note}</p>
                </>
              )}
              <p className="text-xs text-ink-muted">{s.modelInfo.classificationStrategy} Rule base: {s.modelInfo.ruleBased.keywordCount} keywords.</p>
              {s.recordedRuns.length > 0 && <p className="text-xs text-ink-muted">Recorded model runs: {s.recordedRuns.map((r) => `${r.modelVersion} (${dateTime(r.createdAt)})`).join(', ')}</p>}
            </div>
          );
        }}
      </AsyncSection>
    </Card>
  );
}

export default function AiInsights() {
  return (
    <>
      <PageHeader title="AI insights" description="Classification, consumption-pattern analysis, a transparent dietary indicator and exploratory clustering. Results describe populations of orders, never individuals' health." />
      <div className="flex flex-col gap-4">
        <ClassifyPanel />
        <AnalysisPanel />
        <ModelStatusPanel />
      </div>
    </>
  );
}
