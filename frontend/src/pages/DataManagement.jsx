import { useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Download, FileCheck2, LogIn, LogOut, Trash2, Upload } from 'lucide-react';
import { apiErrorMessage } from '../api/client';
import { deleteOrder, downloadReport, getOrders, uploadOrders } from '../api/endpoints';
import { useAuth } from '../context/AuthContext';
import { useFilters } from '../context/FilterContext';
import { useApi } from '../hooks/useApi';
import { Badge, Card, EmptyState, ErrorState, LoadingState, Notice, PageHeader, SYNTHETIC_NOTICE } from '../components/ui';
import { categoryLabel, dateTime, num } from '../utils/format';

const REQUIRED = 'order_id, order_time, food_item, quantity, city';
const OPTIONAL = 'food_category, serving_size_g, serving_unit (g|ml), calories_kcal, sugar_g, sodium_mg, saturated_fat_g, protein_g, data_source, nutrition_source, nutrition_source_url';
const MAX_BYTES = 10 * 1024 * 1024;

function AdminLogin() {
  const { login } = useAuth();
  const [form, setForm] = useState({ username: 'admin', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(await login(form.username, form.password));
    setBusy(false);
  }

  return (
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end" aria-label="Admin sign in">
      <div>
        <label className="label" htmlFor="admin-user">Admin username</label>
        <input id="admin-user" className="input" autoComplete="username" value={form.username}
          onChange={(e) => setForm({ ...form, username: e.target.value })} required />
      </div>
      <div>
        <label className="label" htmlFor="admin-pass">Password</label>
        <input id="admin-pass" type="password" className="input" autoComplete="current-password" value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })} required />
      </div>
      <button type="submit" className="btn-primary" disabled={busy}><LogIn size={16} aria-hidden="true" /> {busy ? 'Signing in…' : 'Sign in'}</button>
      {error && <p className="text-sm text-red-300 sm:col-span-3" role="alert">{error}</p>}
      <p className="text-xs text-ink-muted sm:col-span-3">Uploading and deleting data require the admin credentials set in the server's <code>.env</code> (ADMIN_USERNAME / ADMIN_PASSWORD). Viewing data does not.</p>
    </form>
  );
}

function ImportSummary({ result }) {
  const stats = [
    ['Rows in file', result.totalRows], ['Valid rows', result.validRows], ['Invalid rows', result.invalidRows],
    ['Duplicates in file', result.duplicateRowsInFile], ['Already imported', result.duplicateRowsInDatabase],
    [result.dryRun ? 'Would import' : 'Imported', result.dryRun ? result.validRows - result.duplicateRowsInDatabase : result.importedRows],
    ['Late-night rows', result.lateNightRows], ['New food items', result.newFoodItems],
  ];
  return (
    <div className="mt-4 flex flex-col gap-4" data-testid="import-result">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={result.dryRun ? 'accent' : 'good'}>{result.dryRun ? 'Validation only (nothing saved)' : `Imported · batch #${result.batchId ?? '—'}`}</Badge>
        <span className="text-sm text-ink-secondary">{result.fileName}</span>
        {result.foodItemsClassifiedByAi > 0 && <Badge tone="violet">{result.foodItemsClassifiedByAi} item(s) classified by AI</Badge>}
      </div>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {stats.map(([label, value]) => (
          <div key={label} className="rounded-lg bg-midnight-900 p-3">
            <dt className="text-xs text-ink-muted">{label}</dt>
            <dd className="text-lg font-semibold text-ink">{num(value)}</dd>
          </div>
        ))}
      </dl>
      {result.warnings.length > 0 && (
        <Notice tone="warning"><ul className="list-disc pl-4">{result.warnings.map((w) => <li key={w}>{w}</li>)}</ul></Notice>
      )}
      {result.errors.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-red-200">Validation errors</h3>
          <div className="max-h-64 overflow-auto rounded-lg border border-red-500/20">
            <table className="table-base" aria-label="Validation errors">
              <thead><tr><th>Row</th><th>Column</th><th>Problem</th></tr></thead>
              <tbody>
                {result.errors.map((e, i) => (
                  <tr key={`${e.row}-${e.column}-${i}`}><td>{e.row}</td><td><code>{e.column}</code></td><td className="text-red-200">{e.message}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {result.preview.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-ink">Preview (first {result.preview.length} valid rows)</h3>
          <div className="overflow-x-auto rounded-lg border border-midnight-700">
            <table className="table-base" aria-label="Preview">
              <thead><tr><th>Row</th><th>Local time</th><th>Late night</th><th>Food item</th><th>Category</th><th>Qty</th><th>City</th><th>kcal/serving</th><th>Source</th></tr></thead>
              <tbody>
                {result.preview.map((p) => (
                  <tr key={p.row}>
                    <td>{p.row}</td><td>{dateTime(p.localOrderTime)}</td>
                    <td>{p.lateNight ? <Badge tone="accent">Yes</Badge> : 'No'}</td>
                    <td className="text-ink">{p.foodItem}</td><td>{categoryLabel(p.category)}</td><td>{p.quantity}</td>
                    <td>{p.city}</td><td>{num(p.caloriesKcal)}</td><td>{p.dataSource}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function UploadPanel({ onImported }) {
  const { user, logout } = useAuth();
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);
  const inputRef = useRef(null);

  function choose(e) {
    const f = e.target.files?.[0] || null;
    setResult(null);
    setError(null);
    if (f && !f.name.toLowerCase().endsWith('.csv')) {
      setError('Please choose a .csv file.');
      setFile(null);
      return;
    }
    if (f && f.size > MAX_BYTES) {
      setError('The file is larger than the 10 MB limit.');
      setFile(null);
      return;
    }
    setFile(f);
  }

  async function send(dryRun) {
    if (!file) return;
    setBusy(dryRun ? 'validate' : 'import');
    setError(null);
    try {
      const r = await uploadOrders(file, dryRun);
      setResult(r);
      if (!dryRun && r.importedRows > 0) onImported();
    } catch (err) {
      if (err.response?.status === 401) logout();
      setError(apiErrorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card title="Upload order CSV" subtitle={`Required columns: ${REQUIRED}`}
      actions={user && <button type="button" className="btn-secondary" onClick={logout}><LogOut size={14} aria-hidden="true" /> Sign out</button>}>
      {!user ? <AdminLogin /> : (
        <>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <input ref={inputRef} id="csv-file" type="file" accept=".csv,text/csv" onChange={choose}
              className="block w-full text-sm text-ink-secondary file:mr-3 file:rounded-lg file:border-0 file:bg-midnight-700 file:px-3 file:py-2 file:text-sm file:text-ink hover:file:bg-midnight-600"
              aria-label="CSV file" />
            <div className="flex gap-2">
              <button type="button" className="btn-secondary" disabled={!file || !!busy} onClick={() => send(true)}>
                <FileCheck2 size={16} aria-hidden="true" /> {busy === 'validate' ? 'Validating…' : 'Validate & preview'}
              </button>
              <button type="button" className="btn-primary" disabled={!file || !!busy} onClick={() => send(false)}>
                <Upload size={16} aria-hidden="true" /> {busy === 'import' ? 'Importing…' : 'Import'}
              </button>
            </div>
          </div>
          <p className="mt-2 text-xs text-ink-muted">Optional columns: {OPTIONAL}. Max 10 MB. Columns that could identify people (names, phone numbers, addresses) are ignored and never stored; order IDs are stored only as one-way hashes.</p>
          {error && <div className="mt-3"><ErrorState message={error} /></div>}
          {result && <ImportSummary result={result} />}
        </>
      )}
    </Card>
  );
}

function OrdersTable({ refreshKey }) {
  const { filters } = useFilters();
  const { user, logout } = useAuth();
  const [query, setQuery] = useState({ search: '', lateNightOnly: false, sort: 'orderTimestamp,desc', size: 25 });
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState(null);
  const key = JSON.stringify([filters, query, page, refreshKey]);
  const orders = useApi(() => getOrders({ ...filters, ...query, lateNightOnly: query.lateNightOnly || undefined, page }), [key]);

  const update = (patch) => { setQuery((q) => ({ ...q, ...patch })); setPage(0); };

  async function remove(id) {
    if (!window.confirm('Delete this order permanently?')) return;
    try {
      await deleteOrder(id);
      setMessage('Order deleted.');
      orders.reload();
    } catch (err) {
      if (err.response?.status === 401) logout();
      setMessage(apiErrorMessage(err));
    }
  }

  async function exportCsv() {
    try {
      const name = await downloadReport('orders', filters, { lateNightOnly: query.lateNightOnly || undefined });
      setMessage(`Downloaded ${name}`);
    } catch (err) {
      setMessage(apiErrorMessage(err));
    }
  }

  const d = orders.data;
  return (
    <Card title="Imported orders" subtitle="Filtered by the global filters above plus the options below"
      actions={<button type="button" className="btn-secondary" onClick={exportCsv}><Download size={14} aria-hidden="true" /> Export CSV</button>}>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-4">
        <div className="sm:col-span-2">
          <label className="label" htmlFor="o-search">Search food item</label>
          <input id="o-search" className="input" placeholder="e.g. pizza" value={query.search} maxLength={100}
            onChange={(e) => update({ search: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="o-sort">Sort</label>
          <select id="o-sort" className="input" value={query.sort} onChange={(e) => update({ sort: e.target.value })}>
            <option value="orderTimestamp,desc">Newest first</option>
            <option value="orderTimestamp,asc">Oldest first</option>
            <option value="quantity,desc">Quantity</option>
            <option value="city,asc">City</option>
            <option value="foodItem,asc">Food item</option>
            <option value="localHour,asc">Hour of day</option>
          </select>
        </div>
        <label className="flex items-end gap-2 pb-2 text-sm text-ink-secondary">
          <input type="checkbox" className="h-4 w-4 accent-cyan-400" checked={query.lateNightOnly}
            onChange={(e) => update({ lateNightOnly: e.target.checked })} />
          Late-night only
        </label>
      </div>
      {message && <p className="mb-3 text-sm text-ink-secondary" role="status">{message}</p>}
      {orders.loading && !d && <LoadingState />}
      {orders.error && <ErrorState message={orders.error} onRetry={orders.reload} />}
      {d && d.totalElements === 0 && <EmptyState title="No orders found" message="Import a CSV or change the filters." />}
      {d && d.totalElements > 0 && (
        <>
          <div className="overflow-x-auto">
            <table className="table-base" aria-label="Orders">
              <thead>
                <tr><th>Ref</th><th>Local time</th><th>Analysis day</th><th>Late night</th><th>Food item</th><th>Category</th><th>Qty</th><th>City</th><th>kcal/order</th><th>Source</th>{user && <th><span className="sr-only">Actions</span></th>}</tr>
              </thead>
              <tbody>
                {d.content.map((o) => (
                  <tr key={o.id}>
                    <td><code className="text-xs">{o.orderRef}</code></td>
                    <td>{dateTime(o.localOrderTime)}</td><td>{o.analysisDate}</td>
                    <td>{o.lateNight ? <Badge tone="accent">Yes</Badge> : 'No'}</td>
                    <td className="text-ink">{o.foodItem}</td><td>{categoryLabel(o.category)}</td><td>{o.quantity}</td>
                    <td>{o.city}</td><td>{num(o.caloriesKcalPerOrder)}</td><td>{o.dataSource}</td>
                    {user && (
                      <td>
                        <button type="button" className="btn-danger px-2 py-1" onClick={() => remove(o.id)} aria-label={`Delete order ${o.orderRef}`}>
                          <Trash2 size={14} aria-hidden="true" />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-ink-secondary">
            <span>{num(d.totalElements)} orders · page {d.page + 1} of {d.totalPages}</span>
            <div className="flex items-center gap-2">
              <select className="input w-auto" aria-label="Rows per page" value={query.size} onChange={(e) => update({ size: Number(e.target.value) })}>
                {[10, 25, 50, 100].map((s) => <option key={s} value={s}>{s} / page</option>)}
              </select>
              <button type="button" className="btn-secondary px-2" disabled={d.page === 0} onClick={() => setPage(d.page - 1)} aria-label="Previous page"><ChevronLeft size={16} /></button>
              <button type="button" className="btn-secondary px-2" disabled={d.page + 1 >= d.totalPages} onClick={() => setPage(d.page + 1)} aria-label="Next page"><ChevronRight size={16} /></button>
            </div>
          </div>
        </>
      )}
    </Card>
  );
}

export default function DataManagement() {
  const { reloadOptions } = useFilters();
  const [refreshKey, setRefreshKey] = useState(0);
  return (
    <>
      <PageHeader title="Data management" description="Upload, validate, preview and browse anonymised food-order records." />
      <div className="flex flex-col gap-4">
        <Notice>{SYNTHETIC_NOTICE} Use <code>data/sample_food_orders.csv</code> to get started.</Notice>
        <UploadPanel onImported={() => { setRefreshKey((k) => k + 1); reloadOptions(); }} />
        <OrdersTable refreshKey={refreshKey} />
      </div>
    </>
  );
}
