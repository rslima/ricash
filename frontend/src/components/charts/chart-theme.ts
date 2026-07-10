/** Shared recharts styling for the dashboard charts. */

export const CHART_COLORS = [
  "var(--color-chart-1)",
  "var(--color-chart-2)",
  "var(--color-chart-3)",
  "var(--color-chart-4)",
  "var(--color-chart-5)",
]

/** contentStyle for recharts <Tooltip>, themed via the app's CSS variables. */
export const CHART_TOOLTIP_CONTENT_STYLE = {
  borderRadius: "8px",
  border: "1px solid var(--color-border)",
  background: "var(--color-card)",
  color: "var(--color-card-foreground)",
} as const
