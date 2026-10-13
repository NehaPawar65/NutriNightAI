import { createContext, useContext, useMemo, useState } from 'react';
import { getFilterOptions } from '../api/endpoints';
import { useApi } from '../hooks/useApi';

const FilterContext = createContext(null);
const EMPTY = { from: '', to: '', city: '', category: '' };

/** Global analysis filters shared by every page, plus the available options from the backend. */
export function FilterProvider({ children }) {
  const [filters, setFilters] = useState(EMPTY);
  const options = useApi(getFilterOptions, []);

  const value = useMemo(() => ({
    filters,
    setFilter: (key, val) => setFilters((f) => ({ ...f, [key]: val })),
    resetFilters: () => setFilters(EMPTY),
    options: options.data,
    optionsError: options.error,
    reloadOptions: options.reload,
  }), [filters, options.data, options.error, options.reload]);

  return <FilterContext.Provider value={value}>{children}</FilterContext.Provider>;
}

export function useFilters() {
  const ctx = useContext(FilterContext);
  if (!ctx) throw new Error('useFilters must be used inside FilterProvider');
  return ctx;
}
