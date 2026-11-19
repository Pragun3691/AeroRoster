import { createContext, useContext, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { normalizeApiError } from '../../api/errors'
import type { NormalizedApiError } from '../../api/errors'
import type { ApiFieldError, EligibilityDecision, FlightCrewCoverage } from '../../types/api'
import { roleLabels } from '../../api/live'
import { Button } from './Controls'
import { DataTable, EmptyState, TableHead } from './DataTable'
import { PageHeader } from './PageHeader'
import { StatusBadge } from './StatusBadge'

const MutationErrors = createContext<ApiFieldError[]>([])
const fieldNames: Record<string, string> = {
  Name: 'name', Role: 'role', 'Licence number': 'licenseNo', 'Experience years': 'experienceYears',
  'Contact number': 'contactNo', Registration: 'registration', 'Aircraft type': 'aircraftTypeId',
  'Flight number': 'flightNumber', 'Origin airport': 'sourceAirportId', 'Destination airport': 'destinationAirportId',
  Aircraft: 'aircraftId', 'Departure time (UTC)': 'departureTime', 'Arrival time (UTC)': 'arrivalTime',
  'Crew member': 'crewId', 'Assigned role': 'assignedRole', Flight: 'flightId',
  'Qualification requirement': 'qualificationId', 'Valid until (blank clears expiry)': 'validUntil',
  'Start time (UTC)': 'startTime', 'End time (UTC)': 'endTime', Reason: 'reason',
}
export function RecordStatus({ value }: { value: 'ACTIVE' | 'CANCELLED' | 'SCHEDULED' | 'COMPLETED' }) {
  const labels = { ACTIVE: 'Active', CANCELLED: 'Cancelled', SCHEDULED: 'Scheduled', COMPLETED: 'Completed' }
  return <StatusBadge tone={value === 'CANCELLED' ? 'neutral' : value === 'SCHEDULED' ? 'info' : 'success'}>{labels[value]}</StatusBadge>
}

export function Failure({ error }: { error: NormalizedApiError }) {
  return <section role="alert" className="border-l-4 border-red-600 bg-white p-4 text-sm">
    <p>{error.kind}{error.code ? ` · ${error.code}` : ''}: {error.message}</p>
    {error.kind === 'stale-version' && <p>The record changed. Your draft is preserved. Close the draft, reload the record and review it before starting a new edit. This edit cannot be retried.</p>}
    {error.fieldErrors.map((field, index) => <p key={index}>{field.field}: {field.message}</p>)}
    {error.eligibility && <Eligibility decision={error.eligibility} />}
  </section>
}
export function RemoteState({ state }: { state: { loading: boolean; error?: NormalizedApiError; reload: () => void } }) {
  return <>{state.loading && <p role="status">Loading records…</p>}{state.error && <><Failure error={state.error} /><Button secondary onClick={state.reload}>Reload records</Button></>}</>
}
export function LiveHeader({ title, children }: { title: string; children?: ReactNode }) {
  return <PageHeader eyebrow="Local operations" title={title} description="Database-backed records. Scheduling decisions remain backend-owned." action={children} />
}
export function Panel({ title, children }: { title: string; children: ReactNode }) {
  return <section className="border border-line bg-white"><h2 className="border-b border-line px-5 py-4 font-semibold">{title}</h2><div className="space-y-4 p-5">{children}</div></section>
}
export function Field({ label, children }: { label: string; children: ReactNode }) {
  const errors = useContext(MutationErrors).filter(x => x.field === fieldNames[label] || x.field.endsWith(`.${fieldNames[label]}`))
  return <label className="block text-sm font-semibold">{label}<div className="mt-1">{children}</div>{errors.map((x, index) => <span key={index} className="mt-1 block text-xs text-red-700">{x.message}</span>)}</label>
}
export function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: (readonly [string, string])[] }) {
  return <Field label={label}><select aria-label={label} className="control w-full" value={value} onChange={e => onChange(e.target.value)}>{options.map(([key, text]) => <option key={key} value={key}>{text}</option>)}</select></Field>
}
export function Register({ headings, children, empty }: { headings: string[]; children: ReactNode; empty: boolean }) {
  return empty ? <EmptyState title="No records match" detail="There are no records for the current selection. Adjust the filters or add a record." /> : <DataTable><TableHead>{headings.map(x => <th className="px-4 py-3" key={x}>{x}</th>)}</TableHead><tbody>{children}</tbody></DataTable>
}
export function Cell({ children }: { children: ReactNode }) { return <td className="px-4 py-4 text-sm">{children}</td> }
export function RecordLink({ to, children }: { to: string; children: ReactNode }) { return <Link className="font-semibold text-navy hover:underline" to={to}>{children}</Link> }
export function NotFound() { return <EmptyState title="Record unavailable" detail="The requested record is not present. Return to the directory or reload records." /> }

/** Freeze command + original version at review; disable duplicates and stale retries. */
export function MutationForm({ title, children, prepare, done, close }: {
  title: string; children?: ReactNode; prepare: () => () => Promise<unknown>; done: () => void; close: () => void;
}) {
  const [command, setCommand] = useState<(() => Promise<unknown>) | null>(null)
  const [error, setError] = useState<NormalizedApiError>()
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const submitting = useRef(false)
  async function commit() {
    if (!command || submitting.current || saved || error?.kind === 'stale-version') return
    submitting.current = true
    setBusy(true)
    setError(undefined)
    try { await command(); setSaved(true); done() } catch (e) { setError(normalizeApiError(e)); setCommand(null) }
    finally { submitting.current = false; setBusy(false) }
  }
  return <MutationErrors.Provider value={error?.fieldErrors ?? []}><Panel title={title}>{error && <Failure error={error} />}{saved ? <><p role="status">Saved successfully.</p><Button onClick={done}>Reload affected records</Button></> : <form onSubmit={e => { e.preventDefault(); if (!busy && error?.kind !== 'stale-version') setCommand(() => prepare()) }}>
    <fieldset disabled={busy || Boolean(command)} className="grid gap-5 md:grid-cols-2">{children}</fieldset>
    {command ? <section role="dialog" aria-label="Confirm operation" className="mt-4 space-y-3 border border-amber-200 bg-amber-50 p-4"><p>Confirm {title.toLowerCase()} using the reviewed values? Backend checks apply.</p><Button disabled={busy} onClick={commit}>{busy ? 'Saving…' : 'Confirm operation'}</Button><Button secondary disabled={busy} onClick={() => setCommand(null)}>Keep editing</Button></section> : <Button type="submit" disabled={busy || error?.kind === 'stale-version'}>Review operation</Button>}
  </form>}{!saved && <Button secondary disabled={busy} onClick={close}>Discard draft and reload</Button>}</Panel></MutationErrors.Provider>
}
export function Eligibility({ decision }: { decision: EligibilityDecision }) {
  return <Panel title={decision.eligible ? 'Eligible' : 'Not eligible'}><p>{decision.policyNotice}</p>{decision.reasons.map(r => <p key={r.code}>{r.code}: {r.explanation}</p>)}{decision.dutyWindow && <p>Duty: {decision.dutyWindow.start} to {decision.dutyWindow.end}</p>}{decision.dutyLoad && <p>Duty load: 24h {decision.dutyLoad.maximumRolling24Hours}; 7d {decision.dutyLoad.maximumRolling7Days}; UTC day {decision.dutyLoad.maximumUtcDay}</p>}<dl>{Object.entries(decision.policy).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></Panel>
}
export function Coverage({ data }: { data: FlightCrewCoverage }) {
  return <Panel title={`Flight ${data.flightId} crew coverage`}><p>{data.minimumStaffingSatisfied ? 'Minimum staffing satisfied' : 'Coverage incomplete'}</p>{data.roles.map(r => <p key={r.role}>{roleLabels[r.role]}: {r.active} active / {r.required} required; {r.missing} missing</p>)}{data.reasons.map(r => <p key={r.code}>{r.code}: {r.explanation}</p>)}<p>{data.policyNotice}</p></Panel>
}
