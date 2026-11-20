import type { DashboardTone } from './dashboardData'

const toneColors: Record<DashboardTone, string> = {
  success: '#16A6A0',
  warning: '#D97706',
  error: '#DC2626',
  info: '#3B82F6',
  neutral: '#183B56',
}

export function FlightStatusChart({
  items,
}: {
  items: Array<{ label: string; value: number; color: string }>
}) {
  const max = Math.max(1, ...items.map(item => item.value))
  const total = items.reduce((acc, item) => acc + item.value, 0)

  return (
    <section className="panel-card p-5 sm:p-6">
      <header className="flex flex-col justify-between gap-1 sm:flex-row sm:items-center">
        <div>
          <h2 className="font-semibold text-ink">Flight status distribution</h2>
          <p className="text-xs text-muted">All loaded flight records, grouped by persisted status.</p>
        </div>
        <span className="text-xs font-semibold text-muted">{total} total flights</span>
      </header>

      <div className="mt-5 space-y-4">
        {items.map(item => {
          const pct = total > 0 ? Math.round((item.value / total) * 100) : 0
          return (
            <div key={item.label} className="space-y-1.5">
              <div className="flex justify-between text-xs">
                <span className="font-medium text-ink">{item.label}</span>
                <span className="font-mono text-muted">
                  {item.value} flights ({pct}%)
                </span>
              </div>
              <div
                className="h-2.5 w-full overflow-hidden rounded-full bg-slate-100"
                role="img"
                aria-label={`${item.label}: ${item.value} flights`}
              >
                <div
                  className="h-2.5 rounded-full transition-all duration-300"
                  style={{
                    width: `${(item.value / max) * 100}%`,
                    backgroundColor: item.color,
                  }}
                />
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

export function CrewAvailabilityChart({
  items,
}: {
  items: Array<{ label: string; value: number; tone: DashboardTone }>
}) {
  const total = items.reduce((sum, item) => sum + item.value, 0)

  return (
    <section className="panel-card p-5 sm:p-6">
      <header>
        <h2 className="font-semibold text-ink">Crew availability distribution</h2>
        <p className="mt-0.5 text-xs text-muted">Sample roster at the operational cut-off.</p>
      </header>

      <div
        className="mt-5 flex h-4 overflow-hidden rounded-full bg-slate-100 shadow-inner"
        role="img"
        aria-label={`Crew availability: ${items.map(item => `${item.label} ${item.value}`).join(', ')}`}
      >
        {items.map(item => (
          <div
            key={item.label}
            style={{
              width: `${(item.value / (total || 1)) * 100}%`,
              backgroundColor: toneColors[item.tone],
            }}
          />
        ))}
      </div>

      <div className="mt-4 grid grid-cols-3 gap-3">
        {items.map(item => (
          <div key={item.label} className="rounded-md border border-line bg-slate-50/50 p-2.5">
            <div className="flex items-center gap-1.5">
              <span
                className="inline-block h-2 w-2 rounded-full"
                style={{ backgroundColor: toneColors[item.tone] }}
              />
              <span className="text-[11px] font-medium text-muted">{item.label}</span>
            </div>
            <p className="mt-1 text-base font-bold text-ink">{item.value}</p>
          </div>
        ))}
      </div>
    </section>
  )
}
