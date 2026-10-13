import { api, params } from './client';

const filterParams = (f = {}) => params({ from: f.from, to: f.to, city: f.city, category: f.category });

export const getHealth = () => api.get('/api/health').then((r) => r.data);
export const getFilterOptions = () => api.get('/api/analytics/filters').then((r) => r.data);

export const getOverview = (f) => api.get('/api/analytics/overview', { params: filterParams(f) }).then((r) => r.data);
export const getHourly = (f) => api.get('/api/analytics/hourly', { params: filterParams(f) }).then((r) => r.data);
export const getCategories = (f, lateNightOnly = true) =>
  api.get('/api/analytics/categories', { params: { ...filterParams(f), lateNightOnly } }).then((r) => r.data);
export const getNutrition = (f, lateNightOnly = true) =>
  api.get('/api/analytics/nutrition', { params: { ...filterParams(f), lateNightOnly } }).then((r) => r.data);
export const getGeographic = (f) =>
  api.get('/api/analytics/geographic', { params: params({ from: f?.from, to: f?.to, category: f?.category }) }).then((r) => r.data);

export const getOrders = (q) => api.get('/api/orders', { params: params(q) }).then((r) => r.data);
export const deleteOrder = (id) => api.delete(`/api/orders/${id}`);
export const uploadOrders = (file, dryRun) => {
  const form = new FormData();
  form.append('file', file);
  return api.post('/api/orders/upload', form, { params: { dryRun } }).then((r) => r.data);
};
export const checkAuth = (token) => api.get('/api/auth/me', { headers: { Authorization: token } }).then((r) => r.data);

export const classifyFoods = (items, method) => api.post('/api/ai/classify', { items, method }).then((r) => r.data);
export const runAnalysis = (body) => api.post('/api/ai/analyze', body).then((r) => r.data);
export const getInsights = (type, size = 5) => api.get('/api/ai/insights', { params: params({ type, size }) }).then((r) => r.data);
export const getModelStatus = () => api.get('/api/ai/model-status').then((r) => r.data);

export const getFoodItems = () => api.get('/api/food-items').then((r) => r.data);
export const getRecommendations = (foodItemId) =>
  api.get('/api/recommendations', { params: params({ foodItemId }) }).then((r) => r.data);
export const compareFoods = (sourceId, alternativeId) =>
  api.get('/api/recommendations/compare', { params: { sourceId, alternativeId } }).then((r) => r.data);

export const getReportMetadata = (f) => api.get('/api/reports/metadata', { params: filterParams(f) }).then((r) => r.data);

/** Downloads a CSV export through the API (so auth/proxy settings apply) and saves it. */
export async function downloadReport(type, f, extra = {}) {
  const response = await api.get('/api/reports/export', {
    params: params({ type, ...filterParams(f), ...extra }),
    responseType: 'blob',
  });
  const disposition = response.headers['content-disposition'] || '';
  const match = /filename="?([^"]+)"?/.exec(disposition);
  const name = match ? match[1] : `nutrinight-${type}.csv`;
  const url = URL.createObjectURL(response.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  return name;
}
