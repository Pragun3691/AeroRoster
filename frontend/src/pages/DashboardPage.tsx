import { useRemote } from '../api/useRemote';
import { useState } from 'react';
import { flightService } from '../api/services/flightService';
import { qualificationService } from '../api/services/qualificationService';
import { unavailabilityService } from '../api/services/unavailabilityService';
import { readAircraft, readCrew, readFlights, readPages, utcDisplay } from '../api/live';
import type { AssignmentView, CrewQualificationView, FlightCrewCoverage, UnavailabilityView } from '../types/api';
import { Button } from '../components/common/Controls';
import { Coverage, LiveHeader, Panel, RemoteState, Select } from '../components/common/LiveData';
import { FlightOperationsTable } from './dashboard/FlightOperationsTable';
import { FlightStatusChart } from './dashboard/OperationalCharts';
import { OperationalAlerts } from './dashboard/OperationalAlerts';
import { OperationalMetric } from './dashboard/OperationalMetric';
import type { AlertItem, DemoFlight } from './dashboard/dashboardData';
async function readDashboard() {
    const [flights, crew, aircraft] = await Promise.all([readFlights(), readCrew(), readAircraft()]);
    const coverage: FlightCrewCoverage[] = [], assignments: AssignmentView[] = [], qualifications: CrewQualificationView[] = [], periods: UnavailabilityView[] = [];
    for (const f of flights) {
        coverage.push((await flightService.coverage(f.id)).data);
        assignments.push(...await readPages(params => flightService.assignments(f.id, { ...params, activeOnly: false })));
    }
    for (const c of crew) {
        qualifications.push(...(await qualificationService.list(c.id)).data);
        periods.push(...await readPages(params => unavailabilityService.list(c.id, params)));
    }
    return { flights, crew, aircraft, coverage, assignments, qualifications, periods };
}
export function DashboardPage() {
    const state = useRemote(readDashboard), [filter, setFilter] = useState('all'), data = state.data;
    const rows: DemoFlight[] = data?.flights.map(f => { const c = data.coverage.find(c => c.flightId === f.id)!; return { id: String(f.id), number: f.flightNumber, origin: f.sourceIataCode, destination: f.destinationIataCode, aircraft: f.aircraftRegistration, departure: utcDisplay(f.departureTime), status: f.status, tone: f.status === 'SCHEDULED' ? 'info' : f.status === 'COMPLETED' ? 'success' : 'neutral', coverage: c.minimumStaffingSatisfied ? 'Minimum staffing satisfied' : 'Coverage incomplete', attention: f.status === 'SCHEDULED' && !c.minimumStaffingSatisfied }; }) ?? [];
    const visible = rows.filter(x => filter === 'all' || (filter === 'attention' ? x.attention : x.status === 'SCHEDULED'));
    const alerts: AlertItem[] = data?.coverage.filter(c => c.flightStatus === 'SCHEDULED' && !c.minimumStaffingSatisfied).map(c => ({ id: String(c.flightId), kind: 'coverage', title: `${data.flights.find(f => f.id === c.flightId)!.flightNumber} coverage incomplete`, detail: c.reasons.map(r => `${r.code}: ${r.explanation}`).join('; '), tone: 'warning' })) ?? [];
    const today = new Date().toISOString().slice(0, 10);
    return <div className="space-y-6"><LiveHeader title="Operational dashboard"><Button secondary onClick={state.reload}>Reload records</Button></LiveHeader><RemoteState state={state}/>{data && <><p className="border-l-4 border-teal bg-white px-4 py-3 text-sm">All loaded database records | UTC. Snapshot reads span multiple requests and are not an atomic roster view.</p><section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5" aria-label="Operational summary">{[
                ['Total crew', data.crew.length, 'All personnel records'], ['Active crew', data.crew.filter(x => x.active).length, 'Administrative status; eligibility not implied'], ['Active aircraft', data.aircraft.filter(x => x.active).length, 'Administrative status; availability not implied'], ['Scheduled flights', data.flights.filter(x => x.status === 'SCHEDULED').length, 'All dates'], ['Active assignments', data.assignments.filter(x => x.status === 'ACTIVE').length, 'Flight-scoped histories'],
            ].map(([label, value, detail]) => <OperationalMetric key={String(label)} label={String(label)} value={String(value)} detail={String(detail)} tone="info"/>)}</section><Select label="Flight view" value={filter} onChange={setFilter} options={[["all", "All flights"], ["scheduled", "Scheduled only"], ["attention", "Attention required"]]}/><div className="grid gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(320px,0.9fr)]"><section><h2 className="mb-3 font-semibold">Flight operations</h2><FlightOperationsTable flights={visible}/></section><Panel title="Crew availability"><p>Unavailable as an aggregate metric. Administrative activity, unavailability periods and assignment counts do not establish scheduling eligibility.</p><p>{data.periods.length} recorded unavailability periods | {data.qualifications.length} held qualifications | {data.qualifications.filter(x => x.validUntil !== null && x.validUntil < today).length} expired on the current UTC date.</p></Panel></div><div className="grid gap-6 xl:grid-cols-2"><OperationalAlerts alerts={alerts}/><Panel title="Recent activity"><p>Unavailable: the frozen API has no activity feed. No activity records or trends are simulated.</p></Panel></div><FlightStatusChart items={['SCHEDULED', 'COMPLETED', 'CANCELLED'].map((label, index) => ({ label, value: data.flights.filter(f => f.status === label).length, color: ['#3B82F6', '#16A6A0', '#D97706'][index] }))}/><Panel title="Fleet utilization"><p>Unavailable: no utilization or trend metric is provided by the API.</p></Panel>{data.coverage.filter(c => c.flightStatus === 'SCHEDULED').map(c => <Coverage key={c.flightId} data={c}/>)}</>}</div>;
}
