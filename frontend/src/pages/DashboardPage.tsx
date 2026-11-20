import { useState } from 'react'
import { Info } from 'lucide-react'
import { useRemote } from '../api/useRemote'
import { flightService } from '../api/services/flightService'
import { qualificationService } from '../api/services/qualificationService'
import { unavailabilityService } from '../api/services/unavailabilityService'
import { readAircraft, readCrew, readFlights, readPages, utcDisplay } from '../api/live'
import type { AssignmentView, CrewQualificationView, FlightCrewCoverage, UnavailabilityView } from '../types/api'
import { Button } from '../components/common/Controls'
import { Coverage, LiveHeader, Panel, RemoteState, Select } from '../components/common/LiveData'
import { FlightOperationsTable } from './dashboard/FlightOperationsTable'
import { FlightStatusChart } from './dashboard/OperationalCharts'
import { OperationalAlerts } from './dashboard/OperationalAlerts'
import { OperationalMetric } from './dashboard/OperationalMetric'
import type { AlertItem, DemoFlight } from './dashboard/dashboardData'

async function readDashboard() {
  const [flights, crew, aircraft] = await Promise.all([readFlights(), readCrew(), readAircraft()])
  const coverage: FlightCrewCoverage[] = []
  const assignments: AssignmentView[] = []
  const qualifications: CrewQualificationView[] = []
  const periods: UnavailabilityView[] = []

  for (const f of flights) {
    coverage.push((await flightService.coverage(f.id)).data)
    assignments.push(...(await readPages(params => flightService.assignments(f.id, { ...params, activeOnly: false }))))
  }

  for (const c of crew) {
    qualifications.push(...(await qualificationService.list(c.id)).data)
    periods.push(...(await readPages(params => unavailabilityService.list(c.id, params))))
  }

  return { flights, crew, aircraft, coverage, assignments, qualifications, periods }
}

export function DashboardPage() {
  const state = useRemote(readDashboard)
  const [filter, setFilter] = useState('all')
  const data = state.data

  const rows: DemoFlight[] =
    data?.flights.map(f => {
      const c = data.coverage.find(c => c.flightId === f.id)!
      return {
        id: String(f.id),
        number: f.flightNumber,
        origin: f.sourceIataCode,
        destination: f.destinationIataCode,
        aircraft: f.aircraftRegistration,
        departure: utcDisplay(f.departureTime),
        status: f.status,
        tone: f.status === 'SCHEDULED' ? 'info' : f.status === 'COMPLETED' ? 'success' : 'neutral',
        coverage: c.minimumStaffingSatisfied ? 'Minimum staffing satisfied' : 'Coverage incomplete',
        attention: f.status === 'SCHEDULED' && !c.minimumStaffingSatisfied,
      }
    }) ?? []

  const visible = rows.filter(x =>
    filter === 'all' ? true : filter === 'attention' ? x.attention : x.status === 'SCHEDULED'
  )

  const alerts: AlertItem[] =
    data?.coverage
      .filter(c => c.flightStatus === 'SCHEDULED' && !c.minimumStaffingSatisfied)
      .map(c => {
        const fl = data.flights.find(f => f.id === c.flightId)
        return {
          id: String(c.flightId),
          kind: 'coverage',
          title: `${fl ? fl.flightNumber : `Flight ${c.flightId}`} coverage incomplete`,
          detail: c.reasons.map(r => `${r.code}: ${r.explanation}`).join('; '),
          tone: 'warning',
        }
      }) ?? []

  const today = new Date().toISOString().slice(0, 10)

  return (
    <div className="space-y-6">
      <LiveHeader title="Operational dashboard">
        <Button secondary onClick={state.reload}>
          Reload records
        </Button>
      </LiveHeader>

      <RemoteState state={state} />

      {data && (
        <>
          {/* Operational scope notice */}
          <div className="flex items-start gap-3 rounded-lg border border-teal/20 border-l-4 border-l-teal bg-white p-4 text-xs sm:text-sm text-slate-700 shadow-xs">
            <Info size={18} className="mt-0.5 shrink-0 text-teal" aria-hidden="true" />
            <p>
              All loaded database records | UTC. Snapshot reads span multiple requests and are not an atomic roster view.
            </p>
          </div>

          {/* Key operational performance indicators */}
          <section className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5" aria-label="Operational summary">
            {([
              ['Total crew', data.crew.length, 'All personnel records', 'neutral'],
              ['Active crew', data.crew.filter(x => x.active).length, 'Administrative status; eligibility not implied', 'info'],
              ['Active aircraft', data.aircraft.filter(x => x.active).length, 'Administrative status; availability not implied', 'info'],
              ['Scheduled flights', data.flights.filter(x => x.status === 'SCHEDULED').length, 'All dates', 'info'],
              ['Active assignments', data.assignments.filter(x => x.status === 'ACTIVE').length, 'Flight-scoped histories', 'success'],
            ] as const).map(([label, value, detail, tone]) => (
              <OperationalMetric
                key={label}
                label={label}
                value={String(value)}
                detail={detail}
                tone={tone}
              />
            ))}
          </section>

          {/* Filter toolbar */}
          <div className="flex flex-col gap-3 rounded-lg border border-line bg-white p-4 shadow-card sm:flex-row sm:items-center">
            <div className="sm:w-64">
              <Select
                label="Flight view"
                value={filter}
                onChange={setFilter}
                options={[
                  ['all', 'All flights'],
                  ['scheduled', 'Scheduled only'],
                  ['attention', 'Attention required'],
                ]}
              />
            </div>
            <p className="text-xs text-muted">
              Showing {visible.length} of {rows.length} flights
            </p>
          </div>

          {/* Primary operations grid */}
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(340px,0.9fr)]">
            <section className="space-y-3">
              <h2 className="text-base font-bold tracking-tight text-ink">Flight operations</h2>
              <FlightOperationsTable flights={visible} />
            </section>

            <Panel title="Crew availability">
              <div className="space-y-3 text-xs leading-relaxed text-muted">
                <p>
                  Unavailable as an aggregate metric. Administrative activity, unavailability periods and assignment counts do not establish scheduling eligibility.
                </p>
                <div className="rounded-md border border-line bg-slate-50/70 p-3 font-medium text-ink">
                  <p>
                    {data.periods.length} recorded unavailability periods | {data.qualifications.length} held qualifications | {data.qualifications.filter(x => x.validUntil !== null && x.validUntil < today).length} expired on the current UTC date.
                  </p>
                </div>
              </div>
            </Panel>
          </div>

          {/* Operational alerts & Activity */}
          <div className="grid gap-6 lg:grid-cols-2">
            <OperationalAlerts alerts={alerts} />

            <Panel title="Recent activity">
              <div className="rounded-md border border-dashed border-slate-200 bg-slate-50/50 p-6 text-center text-xs text-muted">
                <p>Unavailable: the frozen API has no activity feed. No activity records or trends are simulated.</p>
              </div>
            </Panel>
          </div>

          {/* Flight status distribution */}
          <FlightStatusChart
            items={['SCHEDULED', 'COMPLETED', 'CANCELLED'].map((label, index) => ({
              label,
              value: data.flights.filter(f => f.status === label).length,
              color: ['#2563EB', '#0D9488', '#D97706'][index],
            }))}
          />

          <Panel title="Fleet utilization">
            <div className="rounded-md border border-dashed border-slate-200 bg-slate-50/50 p-6 text-center text-xs text-muted">
              <p>Unavailable: no utilization or trend metric is provided by the API.</p>
            </div>
          </Panel>

          {/* Real-time flight crew coverage breakdown */}
          <div className="space-y-4 pt-2">
            <h2 className="text-base font-bold tracking-tight text-ink">Scheduled flight coverage breakdown</h2>
            <div className="grid gap-4 md:grid-cols-2">
              {data.coverage
                .filter(c => c.flightStatus === 'SCHEDULED')
                .map(c => (
                  <Coverage key={c.flightId} data={c} />
                ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
