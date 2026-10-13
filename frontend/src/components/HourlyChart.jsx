import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { BAR_RADIUS_V, COLORS, ChartTooltip, Legend, axisProps, compactTick, gridProps } from './charts';
import { NIGHT_ORDER, hourLabel, num } from '../utils/format';

/**
 * Order volume by local hour, ordered from 12:00 so the late-night window is contiguous.
 * mode "total": one series, late-night hours highlighted. mode "daytype": weekday vs weekend orders per day.
 */
export default function HourlyChart({ hours, mode = 'total', height = 280 }) {
  const byHour = Object.fromEntries(hours.map((h) => [h.hour, h]));
  const data = NIGHT_ORDER.map((h) => byHour[h]).filter(Boolean).map((h) => ({ ...h, label: hourLabel(h.hour) }));

  return (
    <div>
      <div style={{ height }} data-testid="hourly-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }} barGap={2} barCategoryGap="18%">
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="label" {...axisProps} interval={1} />
            <YAxis {...axisProps} width={48} tickFormatter={compactTick} />
            <Tooltip cursor={{ fill: 'rgba(148,163,184,0.08)' }}
              content={<ChartTooltip valueFormatter={(v) => num(v, mode === 'total' ? 0 : 2)}
                labelFormatter={(l, p) => `${l}${p?.[0]?.payload?.lateNight ? ' · late-night window' : ''}`} />} />
            {mode === 'total' ? (
              <Bar dataKey="orders" name="Orders" radius={BAR_RADIUS_V} maxBarSize={24} isAnimationActive={false}>
                {data.map((d) => <Cell key={d.hour} fill={d.lateNight ? COLORS.series1 : COLORS.muted} />)}
              </Bar>
            ) : (
              <>
                <Bar dataKey="weekdayOrdersPerDay" name="Weekday (orders/day)" fill={COLORS.series1}
                  radius={BAR_RADIUS_V} maxBarSize={14} isAnimationActive={false} />
                <Bar dataKey="weekendOrdersPerDay" name="Weekend (orders/day)" fill={COLORS.series2}
                  radius={BAR_RADIUS_V} maxBarSize={14} isAnimationActive={false} />
              </>
            )}
          </BarChart>
        </ResponsiveContainer>
      </div>
      {mode === 'total' ? (
        <Legend items={[{ label: 'Late-night window (22:00–04:00)', color: COLORS.series1 }, { label: 'Other hours', color: COLORS.muted }]} />
      ) : (
        <Legend items={[{ label: 'Weekday nights (orders per day)', color: COLORS.series1 }, { label: 'Weekend nights (orders per day)', color: COLORS.series2 }]} />
      )}
    </div>
  );
}
