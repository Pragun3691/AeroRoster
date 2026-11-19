import { useRemote } from '../../api/useRemote';
import { useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { assignmentService } from '../../api/services/assignmentService';
import { flightService } from '../../api/services/flightService';
import { normalizeApiError } from '../../api/errors';
import type { NormalizedApiError } from '../../api/errors';
import { readCrew, readFlights, readPages, roleLabels, utcDisplay } from '../../api/live';
import type { AssignmentRequest, AssignmentView, BackendCrewRole, CrewView, EligibilityDecision, FlightView, FlightCrewCoverage } from '../../types/api';
import { Button } from '../../components/common/Controls';
import { Cell, Coverage, Eligibility, Failure, Field, LiveHeader, MutationForm, Panel, RecordLink, Register, RecordStatus, RemoteState, Select } from '../../components/common/LiveData';
async function readDirectory() {
    const [crew, flights] = await Promise.all([readCrew(), readFlights()]);
    const items: AssignmentView[] = [], coverage: FlightCrewCoverage[] = [];
    for (const flight of flights) {
        items.push(...await readPages(params => flightService.assignments(flight.id, { ...params, activeOnly: false })));
        coverage.push((await flightService.coverage(flight.id)).data);
    }
    return { crew, flights, items, coverage };
}
export function AssignmentsManagementPage() { const { assignmentId } = useParams(); return assignmentId ? <Detail key={assignmentId} id={Number(assignmentId)}/> : <Directory />; }
function Directory() {
    const state = useRemote(readDirectory), [q, setQ] = useState(''), [status, setStatus] = useState('ALL'), [sort, setSort] = useState('departure'), [flight, setFlight] = useState('ALL'), [form, setForm] = useState(false);
    const back = () => { setForm(false); state.reload(); }, data = state.data;
    if (form && data)
        return <AssignForm crew={data.crew} flights={data.flights} back={back}/>;
    const name = (id: number) => data?.crew.find(x => x.id === id)?.name ?? `Crew ${id}`;
    const shown = data?.items.filter(x => `${x.flightNumber} ${name(x.crewId)} ${roleLabels[x.assignedRole]}`.toLowerCase().includes(q.toLowerCase())).filter(x => status === 'ALL' || x.status === status).filter(x => flight === 'ALL' || String(x.flightId) === flight).sort((a, b) => sort === 'crew' ? name(a.crewId).localeCompare(name(b.crewId)) : sort === 'status' ? a.status.localeCompare(b.status) : a.dutyStartTime.localeCompare(b.dutyStartTime));
    return <div className="space-y-6"><LiveHeader title="Assignment management"><Button disabled={!data} onClick={() => setForm(true)}>Review assignment</Button></LiveHeader><RemoteState state={state}/>{data && <><Panel title="Assignment summary"><p>{data.items.length} total | {data.items.filter(x => x.status === 'ACTIVE').length} active | {data.items.filter(x => x.status === 'CANCELLED').length} cancelled</p><p>Flight-scoped history, including cancelled records.</p></Panel><section className="grid gap-3 border border-line bg-white p-4 lg:grid-cols-4"><Field label="Search assignments"><input className="control w-full" placeholder="Search flight, crew, or role" value={q} onChange={e => setQ(e.target.value)}/></Field><Select label="Assignment status" value={status} onChange={setStatus} options={[["ALL", "All statuses"], ["ACTIVE", "Active"], ["CANCELLED", "Cancelled"]]}/><Select label="Flight scope" value={flight} onChange={setFlight} options={[["ALL", "All loaded flights"], ...data.flights.map(x => [String(x.id), x.flightNumber] as [
            string,
            string
        ])]}/><Select label="Sort by" value={sort} onChange={setSort} options={[["departure", "Duty start"], ["crew", "Crew member"], ["status", "Status"]]}/></section><Register headings={['Flight', 'Crew', 'Role', 'Duty window', 'Status', 'Created']} empty={!shown?.length}>{shown?.map(x => <tr className="border-b border-line" key={x.id}><Cell><RecordLink to={`/assignments/${x.id}`}>{x.flightNumber}</RecordLink></Cell><Cell>{name(x.crewId)}</Cell><Cell>{roleLabels[x.assignedRole]}</Cell><Cell>{utcDisplay(x.dutyStartTime)} to {utcDisplay(x.dutyEndTime)}</Cell><Cell><RecordStatus value={x.status}/></Cell><Cell>{utcDisplay(x.createdAt)}</Cell></tr>)}</Register>{data.coverage.filter(x => flight === 'ALL' || String(x.flightId) === flight).map(x => <Coverage key={x.flightId} data={x}/>)}</>}</div>;
}
function AssignForm({ crew, flights, back }: {
    crew: CrewView[];
    flights: FlightView[];
    back: () => void;
}) {
    const [v, setV] = useState<AssignmentRequest>({ crewId: 0, flightId: 0, assignedRole: 'PILOT' }), [preview, setPreview] = useState<{
        request: AssignmentRequest;
        decision: EligibilityDecision;
    } | null>(null), [error, setError] = useState<NormalizedApiError>(), [busy, setBusy] = useState(false);
    const generation = useRef(0);
    function change(next: AssignmentRequest) { generation.current++; setV(next); setPreview(null); setError(undefined); }
    async function check(e: React.FormEvent) { e.preventDefault(); const ticket = ++generation.current, request = { ...v }; setBusy(true); setError(undefined); setPreview(null); try {
        const response = await assignmentService.eligibility(request);
        if (ticket === generation.current)
            setPreview({ request, decision: response.data });
    }
    catch (e) {
        if (ticket === generation.current)
            setError(normalizeApiError(e));
    }
    finally {
        setBusy(false);
    } }
    return <div className="space-y-6"><LiveHeader title="Review assignment"/><p>Preview does not reserve a slot. The backend rechecks all scheduling rules at assignment time.</p>{error && <Failure error={error}/>}<Panel title="Assignment request"><form onSubmit={check}><fieldset disabled={busy} className="grid gap-5 md:grid-cols-3"><Field label="Crew member"><select required aria-label="Crew member" className="control w-full" value={v.crewId || ''} onChange={e => change({ ...v, crewId: Number(e.target.value) })}><option value="">Select crew</option>{crew.map(x => <option key={x.id} value={x.id}>{x.name} | {roleLabels[x.role]}</option>)}</select></Field><Field label="Flight"><select required aria-label="Flight" className="control w-full" value={v.flightId || ''} onChange={e => change({ ...v, flightId: Number(e.target.value) })}><option value="">Select flight</option>{flights.map(x => <option key={x.id} value={x.id}>{x.flightNumber} | {x.status}</option>)}</select></Field><Select label="Assigned role" value={v.assignedRole} onChange={role => change({ ...v, assignedRole: role as BackendCrewRole })} options={Object.entries(roleLabels)}/></fieldset><Button type="submit" disabled={busy}>{busy ? 'Checking...' : 'Check eligibility'}</Button></form><Button secondary disabled={busy} onClick={back}>Discard draft and reload</Button></Panel>{preview && <><Eligibility decision={preview.decision}/>{preview.decision.eligible && <MutationForm key={generation.current} title="Create assignment" prepare={() => () => assignmentService.create(preview.request)} done={back} close={back}/>}</>}</div>;
}
function Detail({ id }: {
    id: number;
}) {
    const load = useMemo(() => async () => { const x = (await assignmentService.get(id)).data; const coverage = (await flightService.coverage(x.flightId)).data; return { x, coverage }; }, [id]), state = useRemote(load), [action, setAction] = useState<'cancel' | 'reassign' | null>(null);
    const back = () => { setAction(null); state.reload(); }, x = state.data?.x;
    return <div className="space-y-6"><LiveHeader title={`Assignment ${id}`}><RecordLink to="/assignments">Register</RecordLink></LiveHeader><RemoteState state={state}/>{x && <><Panel title="Assignment details"><p>{x.flightNumber} | Crew {x.crewId} | {roleLabels[x.assignedRole]} | {x.status}</p><p>{utcDisplay(x.dutyStartTime)} to {utcDisplay(x.dutyEndTime)}</p><p>Created {utcDisplay(x.createdAt)} | Version {x.version}</p><p>Cancellation retains history. Reassignment creates a new record for the same crew, flight and role after cancellation; backend eligibility applies.</p><Button secondary onClick={() => setAction(x.status === 'ACTIVE' ? 'cancel' : 'reassign')}>{x.status === 'ACTIVE' ? 'Review cancellation' : 'Review reassignment'}</Button></Panel>{action && <MutationForm title={action === 'cancel' ? 'Cancel assignment' : 'Reassign cancelled assignment'} prepare={() => () => action === 'cancel' ? assignmentService.cancel(x) : assignmentService.reassign(x)} done={back} close={back}/>}<Coverage data={state.data!.coverage}/></>}</div>;
}
