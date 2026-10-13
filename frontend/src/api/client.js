import axios from 'axios';

const AUTH_KEY = 'nutrinight.adminAuth';

// Empty base URL = same origin (/api is proxied by Vite in dev and by nginx in Docker).
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120000,
});

export function getStoredAuth() {
  try {
    return sessionStorage.getItem(AUTH_KEY);
  } catch {
    return null;
  }
}

export function storeAuth(token) {
  try {
    if (token) sessionStorage.setItem(AUTH_KEY, token);
    else sessionStorage.removeItem(AUTH_KEY);
  } catch {
    /* storage unavailable: credentials stay in memory only */
  }
}

export function basicToken(username, password) {
  return 'Basic ' + btoa(unescape(encodeURIComponent(`${username}:${password}`)));
}

api.interceptors.request.use((config) => {
  const token = getStoredAuth();
  if (token && !config.headers.Authorization) config.headers.Authorization = token;
  return config;
});

/** Turns any API failure into a user-readable message. */
export function apiErrorMessage(error) {
  if (!error) return 'Unknown error';
  if (error.response) {
    const body = error.response.data;
    if (body && typeof body === 'object' && body.message) {
      const details = Array.isArray(body.details) && body.details.length
        ? ': ' + body.details.map((d) => `${d.field} ${d.message}`).join('; ')
        : '';
      return body.message + details;
    }
    return `Request failed with status ${error.response.status}`;
  }
  if (error.code === 'ECONNABORTED') return 'The request timed out. Please try again.';
  return 'Cannot reach the NutriNight backend. Check that the server is running.';
}

/** Builds query params, dropping empty values. */
export function params(obj) {
  return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}
