import { useRemote } from '../../api/useRemote';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { unavailabilityService } from '../../api/services/unavailabilityService';
import { normalizeApiError } from '../../api/errors';
import type { NormalizedApiError } from '../../api/errors';
import { readCrew, readPages, utcDisplay, utcInput, utcInstant } from '../../api/live';
import type { CrewView, UnavailabilityView } from '../../types/api';
import { Button } from '../../components/common/Controls';
import { Cell, Failure, Field, LiveHeader, MutationForm, NotFound, Panel, RecordLink, Register, RemoteState, Select } from '../../components/common/LiveData';
async function readDirectory() { const crew = await readCrew(), items: UnavailabilityView[] = []; for (const x of crew)
    items.push(...await readPages(params => unavailabilityService.list(x.id, params))); return { crew, items }; }
export function UnavailabilityManagementPage() {
    const { unavailabilityId } = useParams(), state = useRemote(readDirectory), [q, setQ] = useState(''), [crew, setCrew] = useState('ALL'), [from, setFrom] = useState(''), [to, setTo] = useState(''), [sort, setSort] = useState('start'), [action, setAction] = useState<{
        kind: 'new' | 'edit' | 'remove';
        record?: UnavailabilityView;
    } | null>(null);
    const data = state.data, selected = unavailabilityId ? data?.items.find(x => x.id === Number(unavailabilityId)) : undefined;
    const back = () => { setAction(null); state.reload(); }, name = (id: number) => data?.crew.find(x => x.id === id)?.name ?? `Crew ${id}`;
    if (action && data)
        return <PeriodForm kind={action.kind} original={action.record} crew={data.crew} back={back}/>;
    const rows = data?.items.filter(x => `${name(x.crewId)} ${x.reason}`.toLowerCase().includes(q.toLowerCase())).filter(x => crew === 'ALL' || String(x.crewId) === crew).filter(x => !from || x.startTime.startsWith(from)).filter(x => !to || x.endTime.startsWith(to)).sort((a, b) => sort === 'crew' ? name(a.crewId).localeCompare(name(b.crewId)) : sort === 'end' ? a.endTime.localeCompare(b.endTime) : a.startTime.localeCompare(b.startTime));
    return <div className="space-y-6"><LiveHeader title={unavailabilityId ? 'Unavailability detail' : 'Unavailability management'}>{unavailabilityId ? <RecordLink to="/unavailability">Register</RecordLink> : <Button disabled={!data} onClick={() => setAction({ kind: 'new' })}>Add unavailability</Button>}</LiveHeader><RemoteState state={state}/>{data && (unavailabilityId ? selected ? <><Panel title="Unavailability period"><p>{name(selected.crewId)} | {selected.reason}</p><p>{utcDisplay(selected.startTime)} to {utcDisplay(selected.endTime)} | Version {selected.version}</p><Button secondary onClick={() => setAction({ kind: 'edit', record: selected })}>Edit</Button><Button secondary onClick={() => setAction({ kind: 'remove', record: selected })}>Review removal</Button></Panel><ConflictCheck crewId={selected.crewId} start={selected.startTime} end={selected.endTime}/></> : <NotFound /> : <><Panel title="Unavailability summary"><p>{data.items.length} total periods | {new Set(data.items.map(x => x.crewId)).size} crew affected</p><p>UTC periods from crew-scoped API lists.</p></Panel><section className="grid gap-3 border border-line bg-white p-4 lg:grid-cols-5"><Field label="Search unavailability"><input className="control w-full" placeholder="Search crew member or reason" value={q} onChange={e => setQ(e.target.value)}/></Field><Select label="Crew member" value={crew} onChange={setCrew} options={[["ALL", "All crew"], ...data.crew.map(x => [String(x.id), x.name] as [
            string,
            string
        ])]}/><Field label="Start date"><input aria-label="Start date" className="control w-full" type="date" value={from} onChange={e => setFrom(e.target.value)}/></Field><Field label="End date"><input aria-label="End date" className="control w-full" type="date" value={to} onChange={e => setTo(e.target.value)}/></Field><Select label="Sort by" value={sort} onChange={setSort} options={[["start", "Start time"], ["end", "End time"], ["crew", "Crew member"]]}/></section><Register headings={['Crew', 'Reason', 'Start UTC', 'End UTC', 'Actions']} empty={!rows?.length}>{rows?.map(x => <tr key={x.id} className="border-b border-line"><Cell><RecordLink to={`/unavailability/${x.id}`}>{name(x.crewId)}</RecordLink></Cell><Cell>{x.reason}</Cell><Cell>{utcDisplay(x.startTime)}</Cell><Cell>{utcDisplay(x.endTime)}</Cell><Cell><Button secondary onClick={() => setAction({ kind: 'edit', record: x })}>Edit</Button><Button secondary onClick={() => setAction({ kind: 'remove', record: x })}>Review removal</Button></Cell></tr>)}</Register></>)}</div>;
}
function ConflictCheck({ crewId, start, end }: {
    crewId: number;
    start: string;
    end: string;
}) {
    const [result, setResult] = useState<boolean>(), [error, setError] = useState<NormalizedApiError>(), [busy, setBusy] = useState(false);
    async function check() { setBusy(true); setError(undefined); setResult(undefined); try {
        setResult((await unavailabilityService.conflicts(crewId, start, end)).data.conflicts);
    }
    catch (e) {
        setError(normalizeApiError(e));
    }
    finally {
        setBusy(false);
    } }
    return <Panel title="Period overlap check"><p>This endpoint checks existing unavailability only and includes the current saved period during an edit. It does not exclude that period or establish duty eligibility. Active-duty conflicts are checked again on save.</p><Button secondary disabled={busy || !crewId || !start || !end} onClick={check}>{busy ? 'Checking...' : 'Check period overlap'}</Button>{result !== undefined && <p role="status">{result ? 'Overlap reported' : 'No period overlap reported'}</p>}{error && <Failure error={error}/>}</Panel>;
}
function PeriodForm({ kind, original, crew, back }: {
    kind: 'new' | 'edit' | 'remove';
    original?: UnavailabilityView;
    crew: CrewView[];
    back: () => void;
}) {
    const [crewId, setCrewId] = useState(original ? String(original.crewId) : ''), [start, setStart] = useState(original ? utcInput(original.startTime) : ''), [end, setEnd] = useState(original ? utcInput(original.endTime) : ''), [reason, setReason] = useState(original?.reason ?? '');
    return <div className="space-y-6"><MutationForm title={kind === 'new' ? 'Add unavailability' : kind === 'edit' ? 'Edit unavailability' : 'Remove unavailability'} done={back} close={back} prepare={() => { const owner = Number(crewId), request = { startTime: utcInstant(start), endTime: utcInstant(end), reason: reason.trim() }; return () => kind === 'new' ? unavailabilityService.create(owner, request) : kind === 'edit' ? unavailabilityService.update(original!.crewId, original!, request) : unavailabilityService.remove(original!.crewId, original!); }}>{kind === 'remove' ? <p>{original!.reason} | {utcDisplay(original!.startTime)} to {utcDisplay(original!.endTime)}</p> : <><Field label="Crew member"><select aria-label="Crew member" required disabled={Boolean(original)} className="control w-full" value={crewId} onChange={e => setCrewId(e.target.value)}><option value="">Select crew</option>{crew.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</select></Field><Field label="Start time (UTC)"><input aria-label="Start time (UTC)" required className="control w-full" placeholder="YYYY-MM-DDTHH:mm:ss" value={start} onChange={e => setStart(e.target.value)}/></Field><Field label="End time (UTC)"><input aria-label="End time (UTC)" required className="control w-full" placeholder="YYYY-MM-DDTHH:mm:ss" value={end} onChange={e => setEnd(e.target.value)}/></Field><Field label="Reason"><input aria-label="Reason" required maxLength={200} className="control w-full" value={reason} onChange={e => setReason(e.target.value)}/></Field></>}</MutationForm>{kind !== 'remove' && <ConflictCheck key={`${crewId}:${start}:${end}`} crewId={Number(crewId)} start={start ? utcInstant(start) : ''} end={end ? utcInstant(end) : ''}/>}</div>;
}
