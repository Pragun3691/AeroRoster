import { AlertTriangle, BadgeAlert, ShieldAlert, Wrench } from 'lucide-react'
import { StatusBadge } from '../../components/common/StatusBadge'
import type { AlertItem } from './dashboardData'

const icons = {
  qualification: BadgeAlert,
  coverage: AlertTriangle,
  conflict: ShieldAlert,
  maintenance: Wrench,
}

export function OperationalAlerts({ alerts }: { alerts: AlertItem[] }) {
  return (
    <section className="panel-card">
      <header className="flex items-center justify-between border-b border-line bg-slate-50/60 px-5 py-4">
        <div>
          <h2 className="font-semibold text-ink">Operational alerts</h2>
          <p className="mt-0.5 text-xs text-muted">Backend coverage responses requiring review.</p>
        </div>
        <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
          {alerts.length} open
        </span>
      </header>
      <div className="divide-y divide-line">
        {alerts.map(alert => {
          const Icon = icons[alert.kind]
          return (
            <article key={alert.id} className="flex items-start gap-3.5 px-5 py-4 transition hover:bg-slate-50/50">
              <Icon className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-ink">{alert.title}</p>
                <p className="mt-1 text-xs leading-5 text-muted">{alert.detail}</p>
              </div>
              <StatusBadge tone={alert.tone}>Review</StatusBadge>
            </article>
          )
        })}
        {alerts.length === 0 && (
          <div className="p-6 text-center text-xs text-muted">
            No active operational alerts. Minimum staffing satisfied across scheduled flights.
          </div>
        )}
      </div>
    </section>
  )
}
