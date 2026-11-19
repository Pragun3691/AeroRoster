import { useRemote } from '../../api/useRemote';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { qualificationService } from '../../api/services/qualificationService';
import { readCrew, readReferences, roleLabels } from '../../api/live';
import type { CrewQualificationView, CrewView, QualificationView } from '../../types/api';
import { Button } from '../../components/common/Controls';
import { Cell, Field, LiveHeader, MutationForm, NotFound, Panel, RecordLink, Register, RemoteState, Select } from '../../components/common/LiveData';
async function readDirectory() {
    const [crew, refs] = await Promise.all([readCrew(), readReferences()]);
    const items: CrewQualificationView[] = [];
    for (const member of crew)
        items.push(...(await qualificationService.list(member.id)).data);
    return { crew, items, ...refs };
}
const expiryState = (date: string | null) => date === null ? 'No expiry' : date < new Date().toISOString().slice(0, 10) ? 'Expired' : 'Current date validity';
export function QualificationsManagementPage() {
    const { qualificationId } = useParams(), state = useRemote(readDirectory);
    const [q, setQ] = useState(''), [role, setRole] = useState('ALL'), [type, setType] = useState('ALL'), [status, setStatus] = useState('ALL'), [sort, setSort] = useState('crew');
    const [action, setAction] = useState<{
        kind: 'grant' | 'expiry' | 'revoke';
        record?: CrewQualificationView;
    } | null>(null);
    const back = () => { setAction(null); state.reload(); };
    const data = state.data, selected = qualificationId ? data?.items.find(x => x.id === Number(qualificationId)) : undefined;
    const crewName = (id: number) => data?.crew.find(x => x.id === id)?.name ?? `Crew ${id}`;
    if (data && action)
        return <QualificationForm kind={action.kind} original={action.record} crew={data.crew} requirements={data.requirements} back={back}/>;
    const rows = data?.items.filter(x => `${crewName(x.crewId)} ${x.model}`.toLowerCase().includes(q.toLowerCase())).filter(x => role === 'ALL' || x.requiredRole === role).filter(x => type === 'ALL' || String(x.aircraftTypeId) === type).filter(x => status === 'ALL' || expiryState(x.validUntil) === status).sort((a, b) => sort === 'crew' ? crewName(a.crewId).localeCompare(crewName(b.crewId)) : (a.validUntil ?? '9999').localeCompare(b.validUntil ?? '9999'));
    return <div className="space-y-6"><LiveHeader title={qualificationId ? 'Qualification detail' : 'Qualifications management'}>{qualificationId ? <RecordLink to="/qualifications">Register</RecordLink> : <Button disabled={!data} onClick={() => setAction({ kind: 'grant' })}>Grant qualification</Button>}</LiveHeader><RemoteState state={state}/>{data && (qualificationId ? selected ? <><Panel title="Crew qualification"><p>{crewName(selected.crewId)} | {selected.model} | {roleLabels[selected.requiredRole]}</p><p>Valid until: {selected.validUntil ?? 'No expiry'} | Held record {selected.id} | Version {selected.version}</p><Button secondary onClick={() => setAction({ kind: 'expiry', record: selected })}>Update expiry</Button><Button secondary onClick={() => setAction({ kind: 'revoke', record: selected })}>Review revocation</Button></Panel></> : <NotFound /> : <><section className="grid gap-3 border border-line bg-white p-4 lg:grid-cols-5"><Field label="Search qualifications"><input className="control w-full" placeholder="Search crew or aircraft type" value={q} onChange={e => setQ(e.target.value)}/></Field><Select label="Crew role" value={role} onChange={setRole} options={[["ALL", "All roles"], ...Object.entries(roleLabels)]}/><Select label="Aircraft type" value={type} onChange={setType} options={[["ALL", "All types"], ...data.types.map(x => [String(x.id), x.model] as [
            string,
            string
        ])]}/><Select label="Expiry status" value={status} onChange={setStatus} options={[["ALL", "All statuses"], ["No expiry", "No expiry"], ["Expired", "Expired"], ["Current date validity", "Current date validity"]]}/><Select label="Sort by" value={sort} onChange={setSort} options={[["crew", "Crew member"], ["expiry", "Expiry"]]}/></section><Panel title="Qualification summary"><p>{data.items.length} held qualifications | {data.items.filter(x => expiryState(x.validUntil) === 'Expired').length} expired on the current UTC date</p><p>Expiry display is administrative only. Flight-date eligibility is checked by the backend.</p></Panel><Register headings={['Crew', 'Aircraft type', 'Role', 'Valid until', 'State', 'Actions']} empty={!rows?.length}>{rows?.map(x => <tr className="border-b border-line" key={x.id}><Cell><RecordLink to={`/qualifications/${x.id}`}>{crewName(x.crewId)}</RecordLink></Cell><Cell>{x.model}</Cell><Cell>{roleLabels[x.requiredRole]}</Cell><Cell>{x.validUntil ?? 'No expiry'}</Cell><Cell>{expiryState(x.validUntil)}</Cell><Cell><Button secondary onClick={() => setAction({ kind: 'expiry', record: x })}>Update expiry</Button><Button secondary onClick={() => setAction({ kind: 'revoke', record: x })}>Review revocation</Button></Cell></tr>)}</Register><Panel title="Aircraft-type qualification requirements">{data.requirements.map(x => <p key={x.id}>{x.model} | {roleLabels[x.requiredRole]} | Requirement {x.id}</p>)}{!data.requirements.length && <p>No requirements</p>}<p>Held records are shown above. Missing holdings do not constitute a frontend eligibility decision.</p></Panel></>)}</div>;
}
function QualificationForm({ kind, original, crew, requirements, back }: {
    kind: 'grant' | 'expiry' | 'revoke';
    original?: CrewQualificationView;
    crew: CrewView[];
    requirements: QualificationView[];
    back: () => void;
}) {
    const [crewId, setCrewId] = useState(original ? String(original.crewId) : ''), [requirement, setRequirement] = useState(''), [date, setDate] = useState(original?.validUntil ?? '');
    return <MutationForm title={kind === 'grant' ? 'Grant qualification' : kind === 'expiry' ? 'Update qualification expiry' : 'Revoke qualification'} done={back} close={back} prepare={() => {
            const expiry = date || null, owner = Number(crewId), qualificationId = Number(requirement);
            return () => kind === 'grant' ? qualificationService.grant(owner, { qualificationId, validUntil: expiry }) : kind === 'expiry' ? qualificationService.updateExpiry(original!.crewId, original!, { validUntil: expiry }) : qualificationService.revoke(original!.crewId, original!);
        }}>{kind === 'grant' ? <><Field label="Crew member"><select aria-label="Crew member" required className="control w-full" value={crewId} onChange={e => setCrewId(e.target.value)}><option value="">Select crew</option>{crew.map(x => <option key={x.id} value={x.id}>{x.name} | {roleLabels[x.role]}</option>)}</select></Field><Field label="Qualification requirement"><select aria-label="Qualification requirement" required className="control w-full" value={requirement} onChange={e => setRequirement(e.target.value)}><option value="">Select requirement</option>{requirements.map(x => <option key={x.id} value={x.id}>{x.model} | {roleLabels[x.requiredRole]}</option>)}</select></Field></> : <p>Held record {original!.id} for crew {original!.crewId}. Current expiry: {original!.validUntil ?? 'No expiry'}. Backend history checks apply.</p>}{kind !== 'revoke' && <Field label="Valid until (blank clears expiry)"><input aria-label="Valid until (blank clears expiry)" className="control w-full" type="date" value={date} onChange={e => setDate(e.target.value)}/></Field>}</MutationForm>;
}
