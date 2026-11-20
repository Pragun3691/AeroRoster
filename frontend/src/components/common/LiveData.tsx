import { createContext, useContext, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, Info, Loader2 } from 'lucide-react'
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
  Name: 'name',
  Role: 'role',
  'Licence number': 'licenseNo',
  'Experience years': 'experienceYears',
  'Contact number': 'contactNo',
  Registration: 'registration',
  'Aircraft type': 'aircraftTypeId',
  'Flight number': 'flightNumber',
  'Origin airport': 'sourceAirportId',
  'Destination airport': 'destinationAirportId',
  Aircraft: 'aircraftId',
  'Departure time (UTC)': 'departureTime',
  'Arrival time (UTC)': 'arrivalTime',
  'Crew member': 'crewId',
  'Assigned role': 'assignedRole',
  Flight: 'flightId',
  'Qualification requirement': 'qualificationId',
  'Valid until (blank clears expiry)': 'validUntil',
  'Start time (UTC)': 'startTime',
  'End time (UTC)': 'endTime',
  Reason: 'reason',
}

export function RecordStatus({
  value,
}: {
  value: 'ACTIVE' | 'CANCELLED' | 'SCHEDULED' | 'COMPLETED'
}) {
  const labels = {
    ACTIVE: 'Active',
    CANCELLED: 'Cancelled',
    SCHEDULED: 'Scheduled',
    COMPLETED: 'Completed',
  }
  const tone =
    value === 'CANCELLED'
      ? 'neutral'
      : value === 'SCHEDULED'
        ? 'info'
        : 'success'

  return <StatusBadge tone={tone}>{labels[value]}</StatusBadge>
}

export function Failure({ error }: { error: NormalizedApiError }) {
  const isStale = error.kind === 'stale-version'

  return (
    <section
      role="alert"
      className={`rounded-lg border-l-4 p-4 text-sm shadow-xs ${
        isStale
          ? 'border-amber-600 bg-amber-50/90 text-amber-950'
          : 'border-red-600 bg-red-50/80 text-red-950'
      }`}
    >
      <div className="flex items-start gap-2.5">
        <AlertTriangle
          size={18}
          className={`mt-0.5 shrink-0 ${isStale ? 'text-amber-600' : 'text-red-600'}`}
          aria-hidden="true"
        />
        <div className="space-y-1.5 flex-1">
          <p className="font-semibold tracking-tight">
            {error.kind}
            {error.code ? ` · ${error.code}` : ''}: {error.message}
          </p>

          {isStale && (
            <div className="rounded-md border border-amber-300 bg-white/70 p-2.5 text-xs text-amber-900 shadow-2xs">
              <p>
                The record changed. Your draft is preserved. Close the draft, reload the record and review it before starting a new edit. This edit cannot be retried.
              </p>
            </div>
          )}

          {error.fieldErrors.length > 0 && (
            <div className="mt-2 space-y-1 text-xs">
              {error.fieldErrors.map((field, index) => (
                <p key={index} className="font-medium text-red-800">
                  {field.field}: {field.message}
                </p>
              ))}
            </div>
          )}

          {error.eligibility && (
            <div className="mt-3">
              <Eligibility decision={error.eligibility} />
            </div>
          )}
        </div>
      </div>
    </section>
  )
}

export function RemoteState({
  state,
}: {
  state: {
    loading: boolean
    error?: NormalizedApiError
    reload: () => void
  }
}) {
  return (
    <>
      {state.loading && (
        <div className="flex items-center gap-2.5 rounded-lg border border-line bg-white px-4 py-3 text-sm text-muted shadow-xs">
          <Loader2 size={16} className="animate-spin text-teal" aria-hidden="true" />
          <p role="status">Loading records…</p>
        </div>
      )}
      {state.error && (
        <div className="space-y-3">
          <Failure error={state.error} />
          <div>
            <Button secondary onClick={state.reload}>
              Reload records
            </Button>
          </div>
        </div>
      )}
    </>
  )
}

export function LiveHeader({
  title,
  children,
}: {
  title: string
  children?: ReactNode
}) {
  return (
    <PageHeader
      eyebrow="Local operations"
      title={title}
      description="Database-backed records. Scheduling decisions remain backend-owned."
      action={children}
    />
  )
}

export function Panel({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <section className="panel-card">
      <h2 className="panel-header">{title}</h2>
      <div className="space-y-4 p-5 sm:p-6">{children}</div>
    </section>
  )
}

export function Field({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  const errors = useContext(MutationErrors).filter(
    x => x.field === fieldNames[label] || x.field.endsWith(`.${fieldNames[label]}`)
  )

  return (
    <label className="block text-sm font-semibold text-ink">
      <span>{label}</span>
      <div className="mt-1.5">{children}</div>
      {errors.map((x, index) => (
        <span key={index} className="mt-1.5 flex items-center gap-1 text-xs font-medium text-red-700">
          <AlertTriangle size={12} aria-hidden="true" />
          <span>{x.message}</span>
        </span>
      ))}
    </label>
  )
}

export function Select({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: (readonly [string, string])[]
}) {
  return (
    <Field label={label}>
      <select
        aria-label={label}
        className="control w-full"
        value={value}
        onChange={e => onChange(e.target.value)}
      >
        {options.map(([key, text]) => (
          <option key={key} value={key}>
            {text}
          </option>
        ))}
      </select>
    </Field>
  )
}

export function Register({
  headings,
  children,
  empty,
}: {
  headings: string[]
  children: ReactNode
  empty: boolean
}) {
  return empty ? (
    <EmptyState
      title="No records match"
      detail="There are no records for the current selection. Adjust the filters or add a record."
    />
  ) : (
    <DataTable>
      <TableHead>
        {headings.map(x => (
          <th className="px-4 py-3.5" key={x}>
            {x}
          </th>
        ))}
      </TableHead>
      <tbody className="divide-y divide-line">{children}</tbody>
    </DataTable>
  )
}

export function Cell({ children }: { children: ReactNode }) {
  return <td className="px-4 py-3.5 text-sm text-ink">{children}</td>
}

export function RecordLink({
  to,
  children,
}: {
  to: string
  children: ReactNode
}) {
  return (
    <Link
      className="font-semibold text-navy transition-colors hover:text-teal hover:underline"
      to={to}
    >
      {children}
    </Link>
  )
}

export function NotFound() {
  return (
    <EmptyState
      title="Record unavailable"
      detail="The requested record is not present. Return to the directory or reload records."
    />
  )
}

/** Freeze command + original version at review; disable duplicates and stale retries. */
export function MutationForm({
  title,
  children,
  prepare,
  done,
  close,
}: {
  title: string
  children?: ReactNode
  prepare: () => () => Promise<unknown>
  done: () => void
  close: () => void
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
    try {
      await command()
      setSaved(true)
      done()
    } catch (e) {
      setError(normalizeApiError(e))
      setCommand(null)
    } finally {
      submitting.current = false
      setBusy(false)
    }
  }

  return (
    <MutationErrors.Provider value={error?.fieldErrors ?? []}>
      <Panel title={title}>
        {error && <Failure error={error} />}

        {saved ? (
          <div className="space-y-3 rounded-lg border border-emerald-200 bg-emerald-50/70 p-4 shadow-xs">
            <div className="flex items-center gap-2 text-sm font-semibold text-emerald-800">
              <CheckCircle2 size={18} className="text-emerald-600" aria-hidden="true" />
              <p role="status">Saved successfully.</p>
            </div>
            <div>
              <Button onClick={done}>Reload affected records</Button>
            </div>
          </div>
        ) : (
          <form
            onSubmit={e => {
              e.preventDefault()
              if (!busy && error?.kind !== 'stale-version') setCommand(() => prepare())
            }}
            className="space-y-4"
          >
            <fieldset disabled={busy || Boolean(command)} className="grid gap-5 md:grid-cols-2">
              {children}
            </fieldset>

            {command ? (
              <section
                role="dialog"
                aria-label="Confirm operation"
                className="mt-4 space-y-3 rounded-lg border border-amber-300 bg-amber-50/90 p-5 shadow-xs"
              >
                <div className="flex items-center gap-2 text-sm font-semibold text-amber-900">
                  <Info size={18} className="text-amber-700" aria-hidden="true" />
                  <p>Confirm {title.toLowerCase()} using the reviewed values? Backend checks apply.</p>
                </div>
                <div className="flex flex-wrap items-center gap-3 pt-2">
                  <Button disabled={busy} onClick={commit}>
                    {busy ? 'Saving…' : 'Confirm operation'}
                  </Button>
                  <Button secondary disabled={busy} onClick={() => setCommand(null)}>
                    Keep editing
                  </Button>
                </div>
              </section>
            ) : (
              <div className="pt-2">
                <Button type="submit" disabled={busy || error?.kind === 'stale-version'}>
                  Review operation
                </Button>
              </div>
            )}
          </form>
        )}

        {!saved && (
          <div className="pt-2">
            <Button secondary disabled={busy} onClick={close}>
              Discard draft and reload
            </Button>
          </div>
        )}
      </Panel>
    </MutationErrors.Provider>
  )
}

export function Eligibility({ decision }: { decision: EligibilityDecision }) {
  return (
    <Panel title={decision.eligible ? 'Eligible' : 'Not eligible'}>
      <div className="space-y-3 text-sm">
        <p className="text-xs text-muted">{decision.policyNotice}</p>

        {decision.reasons.length > 0 && (
          <div className="space-y-1 rounded-md border border-red-200 bg-red-50/60 p-3 text-xs text-red-900">
            {decision.reasons.map(r => (
              <p key={r.code} className="font-medium">
                {r.code}: {r.explanation}
              </p>
            ))}
          </div>
        )}

        {decision.dutyWindow && (
          <div className="rounded-md border border-line bg-slate-50/60 p-3">
            <p className="text-xs font-semibold text-muted">Computed Duty Window</p>
            <p className="font-mono text-xs font-semibold text-ink">
              Duty: {decision.dutyWindow.start} to {decision.dutyWindow.end}
            </p>
          </div>
        )}

        {decision.dutyLoad && (
          <div className="rounded-md border border-line bg-slate-50/60 p-3 text-xs">
            <p className="font-semibold text-muted">Evaluated Duty Loads</p>
            <p className="font-mono text-ink">
              Duty load: 24h {decision.dutyLoad.maximumRolling24Hours}; 7d {decision.dutyLoad.maximumRolling7Days}; UTC day {decision.dutyLoad.maximumUtcDay}
            </p>
          </div>
        )}

        <div className="pt-2">
          <p className="mb-2 text-xs font-bold uppercase tracking-wider text-muted">Scheduling Policy Limits</p>
          <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-3">
            {Object.entries(decision.policy).map(([key, value]) => (
              <div key={key} className="rounded border border-line-subtle bg-white p-2 shadow-2xs">
                <dt className="text-[11px] text-muted">{key}</dt>
                <dd className="font-mono font-bold text-ink">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </Panel>
  )
}

export function Coverage({ data }: { data: FlightCrewCoverage }) {
  const isSatisfied = data.minimumStaffingSatisfied

  return (
    <Panel title={`Flight ${data.flightId} crew coverage`}>
      <div className="space-y-3.5 text-sm">
        <p className="font-semibold text-ink">
          {isSatisfied ? 'Minimum staffing satisfied' : 'Coverage incomplete'}
        </p>

        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {data.roles.map(r => (
            <div
              key={r.role}
              className={`rounded-lg border p-3 text-xs shadow-2xs ${
                r.missing > 0
                  ? 'border-amber-200 bg-amber-50/50 text-amber-950'
                  : 'border-emerald-200 bg-emerald-50/50 text-emerald-950'
              }`}
            >
              <p className="font-bold">{roleLabels[r.role]}</p>
              <p className="mt-0.5">
                {roleLabels[r.role]}: {r.active} active / {r.required} required; {r.missing} missing
              </p>
            </div>
          ))}
        </div>

        {data.reasons.length > 0 && (
          <div className="space-y-1 rounded-md border border-line bg-slate-50/80 p-3 text-xs">
            {data.reasons.map(r => (
              <p key={r.code} className="text-muted">
                {r.code}: {r.explanation}
              </p>
            ))}
          </div>
        )}

        <p className="text-xs text-muted">{data.policyNotice}</p>
      </div>
    </Panel>
  )
}
