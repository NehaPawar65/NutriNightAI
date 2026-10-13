import { useCallback, useEffect, useRef, useState } from 'react';
import { apiErrorMessage } from '../api/client';

/**
 * Runs an async loader whenever its dependencies change. Stale responses (from an earlier filter
 * selection) are ignored so fast filter changes never show mismatched data.
 */
export function useApi(loader, deps = [], { enabled = true } = {}) {
  const [state, setState] = useState({ data: null, loading: enabled, error: null });
  const requestId = useRef(0);

  const load = useCallback(() => {
    if (!enabled) return;
    const id = ++requestId.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    loader()
      .then((data) => {
        if (id === requestId.current) setState({ data, loading: false, error: null });
      })
      .catch((err) => {
        if (id === requestId.current) setState({ data: null, loading: false, error: apiErrorMessage(err) });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, ...deps]);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, reload: load };
}
