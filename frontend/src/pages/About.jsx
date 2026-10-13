import { Card, PageHeader } from '../components/ui';

const SECTIONS = [
  {
    title: 'Objectives',
    body: [
      'Describe when and what people order from online food platforms between 22:00 and 04:00.',
      'Quantify the nutritional characteristics of late-night orders (energy, sugar, sodium, saturated fat, protein).',
      'Summarise patterns with transparent, configurable indicators and exploratory machine learning.',
      'Suggest potentially healthier alternatives from the nutrition records available.',
    ],
  },
  {
    title: 'Data sources',
    body: [
      'The bundled dataset (data/sample_food_orders.csv) is SYNTHETIC and reproducible (data/generate_sample_data.py, seed 42). Nutrition values are illustrative estimates, not verified restaurant data.',
      'Any CSV with the documented columns can replace or supplement it. Each order keeps its data_source label and each food item keeps its nutrition source.',
      'Private food-delivery platform APIs are not used.',
    ],
  },
  {
    title: 'Late-night window',
    body: [
      'Late-night = local time from 22:00 (inclusive) to 04:00 (exclusive) in the configured time zone (default Asia/Kolkata). 22:00, 23:00, 00:00 and 03:59 are included; 21:59 and 04:00 are not.',
      'Times with a UTC offset are converted to the analysis time zone first. Orders after midnight are attributed to the previous evening\'s "analysis day", so a night is never split across two dates.',
      'Weekend nights are analysis days falling on Saturday or Sunday.',
    ],
  },
  {
    title: 'Nutritional calculations',
    body: [
      'Per-order value = per-serving value × quantity. Totals add per-order values; means divide by the number of orders with a known value.',
      'Missing nutrient values are counted and reported as coverage. They are never treated as zero.',
      'Low / medium / high levels use the UK front-of-pack thresholds per 100 g (food) or 100 ml (drinks), computed from the serving size. These are applied to restaurant dishes for exploration only.',
    ],
  },
  {
    title: 'AI techniques',
    body: [
      'Classification: transparent keyword rules first; a TF-IDF (word + character n-grams) + logistic regression model trained on a small hand-labelled set is used only when no rule matches. Results state which method was used; only the model reports a probability, along with its contributing features.',
      'Pattern analysis: Pandas aggregation by hour, category, weekday/weekend and night-level recurrence.',
      'Clustering: K-Means on standardised city-night profiles, with k chosen by silhouette score. Weak structure is reported as such.',
    ],
  },
  {
    title: 'Late-Night Dietary Pattern Indicator (0–100)',
    body: [
      'LNDPI = 100 × Σ wᵢ·min(xᵢ / rᵢ, 1) / Σ wᵢ over available components: late-night share (ref 50%), mean energy per late-night order (ref 800 kcal), sugar (25 g), sodium (1000 mg), saturated fat (10 g), and the discretionary-category share (ref 100%).',
      'The reference values are anchored to WHO and EU/UK daily guidance (half of the daily limit per order). The weights are configurable assumptions and are not clinically validated. Components with less than 50% data coverage are excluded and the remaining weights are renormalised.',
      'It is a population-level descriptive indicator, not an individual\'s probability of developing an NCD.',
    ],
  },
  {
    title: 'Limitations',
    body: [
      'Orders show purchasing, not consumption: food may be shared, partly eaten or bought for others.',
      'Food-order data alone cannot establish whether anyone has obesity, diabetes or cardiovascular disease.',
      'Observed differences are associations, not causal effects. Standard servings may differ from actual portions.',
      'The classifier is trained on a small, illustrative labelled set; its evaluation metrics are shown on the AI insights page.',
    ],
  },
  {
    title: 'Privacy and responsible use',
    body: [
      'Order IDs are stored only as one-way SHA-256 hashes. Columns that may identify a person (names, phone numbers, addresses, coordinates) are ignored on import.',
      'Geography is kept at city level only. Cities with fewer than 10 orders in a selection are suppressed. No demographic attributes are inferred.',
      'Uploading and deleting data require admin credentials. Order contents are not written to logs.',
    ],
  },
  {
    title: 'Public-health relevance',
    body: [
      'Diet is a major modifiable risk factor for non-communicable diseases. Late-night eating of energy-dense, high-sugar, high-sodium foods is a pattern worth monitoring at population level.',
      'NutriNight AI helps researchers explore such patterns transparently. Its outputs should inform further, properly designed studies; they are not clinical evidence on their own.',
    ],
  },
];

export default function About() {
  return (
    <>
      <PageHeader title="About & methodology"
        description="NutriNight AI — AI Analysis of Late-Night Online Food Choices and NCD Risk. Understand nighttime eating. Analyse dietary risk indicators. Promote healthier choices." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {SECTIONS.map((s) => (
          <Card key={s.title} title={s.title}>
            <ul className="list-disc space-y-2 pl-5 text-sm text-ink-secondary">{s.body.map((b) => <li key={b}>{b}</li>)}</ul>
          </Card>
        ))}
      </div>
      <div className="mt-4 rounded-lg border border-midnight-700 p-4 text-sm text-ink-secondary">
        <p className="font-medium text-ink">Four things are kept distinct throughout the application:</p>
        <ol className="mt-2 list-decimal space-y-1 pl-5">
          <li>Observed food-order behaviour (counts, times, categories).</li>
          <li>Nutritional indicators derived from available nutrition records.</li>
          <li>Statistical associations and exploratory groupings (patterns, clusters).</li>
          <li>Clinically validated disease outcomes, which this application does not measure or predict.</li>
        </ol>
      </div>
    </>
  );
}
