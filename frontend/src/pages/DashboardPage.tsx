import { CalendarDays, Filter } from 'lucide-react'
import { useMemo, useState } from 'react'
import { PageHeader } from '../components/common/PageHeader'
import { CrewAvailabilityPanel } from './dashboard/CrewAvailabilityPanel'
import { demoFlights, dashboardDate, flightStatus, operationalAlerts, operationalMetrics, crewAvailability, recentActivity, type FlightFilter } from './dashboard/dashboardData'
import { FlightOperationsTable } from './dashboard/FlightOperationsTable'
import { CrewAvailabilityChart, FlightStatusChart } from './dashboard/OperationalCharts'
import { OperationalAlerts } from './dashboard/OperationalAlerts'
import { OperationalMetric } from './dashboard/OperationalMetric'
import { RecentActivity } from './dashboard/RecentActivity'

export function DashboardPage() {
  const [filter, setFilter] = useState<FlightFilter>('all')
  const visibleFlights = useMemo(() => demoFlights.filter(flight => filter === 'all' || (filter === 'attention' ? flight.attention : flight.status === 'Scheduled')), [filter])
  return <div className="space-y-6"><PageHeader eyebrow="Flight operations · demo environment" title="Operational dashboard" description="A single-view readiness snapshot for the sample operating day. This data is for interface development and is not connected to the live roster." action={<div className="flex items-center gap-2 text-sm text-muted"><CalendarDays size={17} aria-hidden="true" /><span>{dashboardDate} · UTC</span></div>} />
    <section className="flex flex-col justify-between gap-3 border-l-4 border-teal bg-white px-4 py-3 sm:flex-row sm:items-center"><div><p className="text-sm font-semibold text-ink">Demo operational picture</p><p className="mt-0.5 text-xs text-muted">Counts, alerts, and activity are realistic sample data—not live database results.</p></div><label className="flex items-center gap-2 text-sm font-medium text-ink"><Filter size={16} aria-hidden="true" /><span className="sr-only">Flight view</span><select className="control h-9" value={filter} onChange={event => setFilter(event.target.value as FlightFilter)}><option value="all">All upcoming flights</option><option value="scheduled">Scheduled only</option><option value="attention">Attention required</option></select></label></section>
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5" aria-label="Operational summary">{operationalMetrics.map(metric => <OperationalMetric key={metric.label} {...metric} />)}</section>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(320px,0.9fr)]"><section><div className="mb-3 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="font-semibold text-ink">Upcoming flight operations</h2><p className="mt-1 text-sm text-muted">Sorted by scheduled departure · sample schedule · UTC</p></div><span className="text-xs font-semibold text-muted">{visibleFlights.length} shown</span></div><FlightOperationsTable flights={visibleFlights} /></section><CrewAvailabilityPanel items={crewAvailability} /></div>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(360px,0.75fr)]"><OperationalAlerts alerts={operationalAlerts} /><RecentActivity activity={recentActivity} /></div>
    <section><div className="mb-3"><h2 className="font-semibold text-ink">Operational distribution</h2><p className="mt-1 text-sm text-muted">Compact planning signals, not a live analytics feed.</p></div><div className="grid gap-6 lg:grid-cols-2"><FlightStatusChart items={flightStatus} /><CrewAvailabilityChart items={crewAvailability} /></div></section>
  </div>
}
