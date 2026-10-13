import { RotateCcw } from 'lucide-react';
import { useFilters } from '../context/FilterContext';

/** One row of filters above the charts; values are shared across pages. */
export default function FilterBar({ showCategory = true, showCity = true }) {
  const { filters, setFilter, resetFilters, options } = useFilters();
  const invalidRange = filters.from && filters.to && filters.from > filters.to;

  return (
    <div className="card no-print mb-6">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        <div>
          <label className="label" htmlFor="f-from">From (analysis day)</label>
          <input id="f-from" type="date" className="input" value={filters.from}
            min={options?.minDate || undefined} max={options?.maxDate || undefined}
            onChange={(e) => setFilter('from', e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="f-to">To (analysis day)</label>
          <input id="f-to" type="date" className="input" value={filters.to}
            min={options?.minDate || undefined} max={options?.maxDate || undefined}
            onChange={(e) => setFilter('to', e.target.value)} />
        </div>
        {showCity && (
          <div>
            <label className="label" htmlFor="f-city">City</label>
            <select id="f-city" className="input" value={filters.city} onChange={(e) => setFilter('city', e.target.value)}>
              <option value="">All cities</option>
              {(options?.cities || []).map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        )}
        {showCategory && (
          <div>
            <label className="label" htmlFor="f-category">Category</label>
            <select id="f-category" className="input" value={filters.category} onChange={(e) => setFilter('category', e.target.value)}>
              <option value="">All categories</option>
              {(options?.categories || []).map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </div>
        )}
        <div className="flex items-end">
          <button type="button" className="btn-secondary w-full" onClick={resetFilters}>
            <RotateCcw size={14} aria-hidden="true" /> Reset filters
          </button>
        </div>
      </div>
      <p className="mt-3 text-xs text-ink-muted">
        {invalidRange
          ? <span className="text-amber-300">“From” is after “To”. Adjust the dates to see results.</span>
          : <>Data available {options?.minDate || '…'} to {options?.maxDate || '…'}. Analysis days run 04:00–04:00 ({options?.window?.timezone || 'local time'}), so after-midnight orders stay with their evening.</>}
      </p>
    </div>
  );
}
