import type { DashboardTone } from './dashboardData'

const accents: Record<DashboardTone, string> = {
  success: 'border-l-emerald-500',
  warning: 'border-l-amber-500',
  error: 'border-l-red-500',
  info: 'border-l-sky-500',
  neutral: 'border-l-teal',
}

export function OperationalMetric({
  label,
  value,
  detail,
  tone,
}: {
  label: string
  value: string
  detail: string
  tone: DashboardTone
}) {
  return (
    <article
      className={`rounded-lg border border-line border-l-4 bg-white p-5 shadow-card transition-all hover:shadow-card-hover ${accents[tone]}`}
    >
      <p className="text-[11px] font-bold uppercase tracking-[.14em] text-muted">
        {label}
      </p>
      <p className="mt-2 text-3xl font-extrabold tracking-tight text-ink">
        {value}
      </p>
      <p className="mt-1.5 text-xs leading-normal text-muted">
        {detail}
      </p>
    </article>
  )
}
