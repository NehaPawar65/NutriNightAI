export const CATEGORY_LABELS = {
  FAST_FOOD: 'Fast food',
  FRIED_SNACKS: 'Fried snacks',
  SUGARY_BEVERAGES: 'Sugary beverages',
  DESSERTS: 'Desserts',
  BALANCED_MEALS: 'Balanced meals',
  FRUITS_VEGETABLES: 'Fruits and vegetables',
  OTHER: 'Other / mixed dishes',
};

export const NUTRIENTS = [
  { key: 'CALORIES', label: 'Energy', unit: 'kcal' },
  { key: 'SUGAR', label: 'Sugar', unit: 'g' },
  { key: 'SODIUM', label: 'Sodium', unit: 'mg' },
  { key: 'SATURATED_FAT', label: 'Saturated fat', unit: 'g' },
  { key: 'PROTEIN', label: 'Protein', unit: 'g' },
];

export const categoryLabel = (c) => CATEGORY_LABELS[c] || c;

export function num(value, digits = 0) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
  return Number(value).toLocaleString('en-IN', { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

export function compact(value) {
  if (value === null || value === undefined) return '—';
  const n = Number(value);
  if (Math.abs(n) >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
  if (Math.abs(n) >= 1e3) return `${(n / 1e3).toFixed(n >= 1e4 ? 0 : 1)}K`;
  return String(Math.round(n * 10) / 10);
}

export const pct = (value, digits = 1) => (value === null || value === undefined ? '—' : `${Number(value).toFixed(digits)}%`);

export const hourLabel = (h) => (h === null || h === undefined ? '—' : `${String(h).padStart(2, '0')}:00`);

export function dateTime(iso) {
  if (!iso) return '—';
  return iso.replace('T', ' ').slice(0, 16);
}

/** Hours ordered from noon so the 22:00-04:00 window is contiguous on charts. */
export const NIGHT_ORDER = [...Array(24).keys()].map((i) => (i + 12) % 24);
