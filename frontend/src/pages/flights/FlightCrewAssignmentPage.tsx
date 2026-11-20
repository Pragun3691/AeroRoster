import { useState, useMemo, useRef, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useRemote } from '../../api/useRemote'
import { flightService } from '../../api/services/flightService'
import { assignmentService } from '../../api/services/assignmentService'
import { normalizeApiError } from '../../api/errors'
import type { NormalizedApiError } from '../../api/errors'
import {
  readCrew,
  readFlights,
  readAircraft,
  readReferences,
  utcDisplay,
  roleLabels,
} from '../../api/live'
import type {
  AssignmentRequest,
  AssignmentView,
  BackendCrewRole,
  CrewView,
  EligibilityDecision,
  FlightView,
  FlightCrewCoverage,
  AircraftView,
  AircraftTypeView,
} from '../../types/api'
import { Button } from '../../components/common/Controls'
import {
  Cell,
  LiveHeader,
  Panel,
  RecordLink,
  RecordStatus,
  Register,
  RemoteState,
} from '../../components/common/LiveData'

interface PageData {
  flight: FlightView
  coverage: FlightCrewCoverage
  assignments: AssignmentView[]
  crew: CrewView[]
  allFlights: FlightView[]
  aircraft: AircraftView[]
  types: AircraftTypeView[]
}

export function FlightCrewAssignmentPage() {
  const { flightId } = useParams()
  const navigate = useNavigate()
  const id = Number(flightId)

  const load = useMemo(
    () => async (): Promise<PageData> => {
      const [flightRes, coverageRes, assignRes, crewList, allFlights, aircraftList, refs] =
        await Promise.all([
          flightService.get(id),
          flightService.coverage(id),
          flightService.assignments(id, { page: 0, size: 200, activeOnly: false }),
          readCrew(),
          readFlights(),
          readAircraft(),
          readReferences(),
        ])
      return {
        flight: flightRes.data,
        coverage: coverageRes.data,
        assignments: assignRes.data.items,
        crew: crewList,
        allFlights,
        aircraft: aircraftList,
        types: refs.types,
      }
    },
    [id]
  )

  const state = useRemote(load)

  return (
    <div className="space-y-6">
      <LiveHeader title="Guided flight crew assignment">
        <div className="flex items-center gap-3">
          <RecordLink to={`/flights/${id}`}>Flight details</RecordLink>
          <RecordLink to="/flights">Flight directory</RecordLink>
          <RecordLink to="/assignments">Assignments</RecordLink>
        </div>
      </LiveHeader>

      <RemoteState state={state} />

      {state.data && (
        <AssignmentWorkflow
          data={state.data}
          reload={state.reload}
          onSwitchFlight={nextId => navigate(`/flights/${nextId}/assign`)}
        />
      )}
    </div>
  )
}

function AssignmentWorkflow({
  data,
  reload,
  onSwitchFlight,
}: {
  data: PageData
  reload: () => void
  onSwitchFlight: (id: number) => void
}) {
  const { flight, coverage, assignments, crew, allFlights, aircraft, types } = data

  const selectedAircraft = aircraft.find(a => a.id === flight.aircraftId)
  const selectedType = types.find(t => t.id === flight.aircraftTypeId)
  const activeAssignments = assignments.filter(a => a.status === 'ACTIVE')

  const minCabinCrew = selectedType?.minCabinCrew ?? 2
  const requiredPilots = 1
  const requiredCopilots = 1
  const totalRequired = requiredPilots + requiredCopilots + minCabinCrew

  const activePilots = activeAssignments.filter(a => a.assignedRole === 'PILOT')
  const activeCopilots = activeAssignments.filter(a => a.assignedRole === 'COPILOT')
  const activeCabinCrew = activeAssignments.filter(a => a.assignedRole === 'CABIN_CREW')

  const missingPilots = Math.max(0, requiredPilots - activePilots.length)
  const missingCopilots = Math.max(0, requiredCopilots - activeCopilots.length)
  const missingCabinCrew = Math.max(0, minCabinCrew - activeCabinCrew.length)

  const totalAssigned = activeAssignments.length

  const [cancelBusy, setCancelBusy] = useState<number | null>(null)
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  async function handleCancel(assignment: AssignmentView) {
    setCancelBusy(assignment.id)
    setNotification(null)
    try {
      await assignmentService.cancel(assignment)
      setNotification({
        type: 'success',
        message: `Assignment for Crew ID ${assignment.crewId} (${roleLabels[assignment.assignedRole]}) was cancelled. Vacancy reopened.`,
      })
      reload()
    } catch (err) {
      const norm = normalizeApiError(err)
      setNotification({
        type: 'error',
        message: norm.message || 'Failed to cancel assignment',
      })
    } finally {
      setCancelBusy(null)
    }
  }

  return (
    <div className="space-y-6">
      {/* Flight Selector Switcher */}
      <section
        className="rounded-xl border border-line bg-white p-4 shadow-card flex flex-wrap items-center justify-between gap-4"
        aria-label="Flight switcher"
      >
        <div className="flex items-center gap-3 flex-1 min-w-[280px]">
          <span className="text-xs font-semibold text-muted uppercase tracking-wider">
            Active Flight:
          </span>
          <select
            aria-label="Select active flight"
            className="control font-medium text-ink flex-1"
            value={flight.id}
            onChange={e => onSwitchFlight(Number(e.target.value))}
          >
            {allFlights.map(f => (
              <option key={f.id} value={f.id}>
                {f.flightNumber} ({f.sourceIataCode} → {f.destinationIataCode}) · {f.status} · Aircraft {f.aircraftRegistration}
              </option>
            ))}
          </select>
        </div>
        <div className="text-xs text-muted">
          Flight ID: <span className="font-semibold text-ink">{flight.id}</span>
        </div>
      </section>

      {/* Notification Banner */}
      {notification && (
        <div
          role="alert"
          className={`rounded-xl border p-4 text-sm flex items-center justify-between ${
            notification.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
              : 'border-red-200 bg-red-50 text-red-900'
          }`}
        >
          <span>{notification.message}</span>
          <button
            type="button"
            className="text-xs font-semibold underline hover:no-underline ml-4"
            onClick={() => setNotification(null)}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Flight Information Panel */}
      <Panel title="Flight identity & aircraft specifications">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted">Flight & Route</span>
            <p className="text-base font-bold text-ink">
              {flight.flightNumber} ({flight.sourceIataCode} → {flight.destinationIataCode})
            </p>
            <RecordStatus value={flight.status} />
          </div>
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted">Schedule (UTC)</span>
            <p className="text-sm text-ink">
              <span className="font-semibold">Dep:</span> {utcDisplay(flight.departureTime)}
            </p>
            <p className="text-sm text-ink">
              <span className="font-semibold">Arr:</span> {utcDisplay(flight.arrivalTime)}
            </p>
          </div>
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted">Assigned Aircraft</span>
            <p className="text-base font-bold text-ink">
              {flight.aircraftRegistration} ({selectedAircraft?.model ?? 'A320'})
            </p>
            <p className="text-xs text-muted">
              {selectedType?.model ?? 'Type'} · {selectedType?.manufacturer ?? 'Manufacturer'}
            </p>
          </div>
          <div className="space-y-1">
            <span className="text-xs font-medium text-muted">Seating & Staffing Baseline</span>
            <p className="text-sm text-ink">
              Capacity: <span className="font-semibold">{selectedType?.capacity ?? 'N/A'} pax</span>
            </p>
            <p className="text-xs text-muted">
              Standard: 1 Pilot · 1 Co-pilot · {minCabinCrew} Cabin crew ({totalRequired} total)
            </p>
          </div>
        </div>
      </Panel>

      {/* Staffing Requirements & Live Coverage Summary Table (Feature E) */}
      <Panel title="Flight crew staffing requirements & coverage">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-line">
            <div className="flex items-center gap-3">
              <span className="text-sm font-semibold text-ink">Coverage Status:</span>
              {coverage.minimumStaffingSatisfied ? (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                  <span className="h-2 w-2 rounded-full bg-emerald-600 animate-pulse" />
                  MINIMUM STAFFING SATISFIED
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800 border border-amber-300">
                  <span className="h-2 w-2 rounded-full bg-amber-600" />
                  INCOMPLETE STAFFING ({totalAssigned} / {totalRequired} assigned)
                </span>
              )}
            </div>
            <div className="text-xs text-muted">
              Progress: <span className="font-bold text-ink">{totalAssigned}</span> of{' '}
              <span className="font-bold text-ink">{totalRequired}</span> crew positions filled
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden border border-line">
            <div
              className={`h-2.5 rounded-full transition-all duration-300 ${
                coverage.minimumStaffingSatisfied ? 'bg-emerald-600' : 'bg-teal'
              }`}
              style={{ width: `${Math.min(100, Math.round((totalAssigned / totalRequired) * 100))}%` }}
            />
          </div>

          {/* Requirements Table */}
          <div className="overflow-x-auto rounded-lg border border-line bg-white">
            <table className="w-full text-left text-sm" aria-label="Crew requirements table">
              <thead className="bg-sand/40 border-b border-line text-xs font-semibold text-muted uppercase tracking-wider">
                <tr>
                  <th className="p-3">Role</th>
                  <th className="p-3 text-right">Required</th>
                  <th className="p-3 text-right">Assigned</th>
                  <th className="p-3 text-right">Missing</th>
                  <th className="p-3 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                <tr className="hover:bg-sand/20">
                  <td className="p-3 font-medium text-ink">Captain / Pilot in Command (PILOT)</td>
                  <td className="p-3 text-right font-semibold">{requiredPilots}</td>
                  <td className="p-3 text-right font-semibold text-teal">{activePilots.length}</td>
                  <td className="p-3 text-right font-semibold text-amber-700">{missingPilots}</td>
                  <td className="p-3 text-center">
                    {missingPilots === 0 ? (
                      <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                        Satisfied
                      </span>
                    ) : (
                      <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                        1 Needed
                      </span>
                    )}
                  </td>
                </tr>
                <tr className="hover:bg-sand/20">
                  <td className="p-3 font-medium text-ink">First Officer / Co-Pilot (COPILOT)</td>
                  <td className="p-3 text-right font-semibold">{requiredCopilots}</td>
                  <td className="p-3 text-right font-semibold text-teal">{activeCopilots.length}</td>
                  <td className="p-3 text-right font-semibold text-amber-700">{missingCopilots}</td>
                  <td className="p-3 text-center">
                    {missingCopilots === 0 ? (
                      <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                        Satisfied
                      </span>
                    ) : (
                      <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                        1 Needed
                      </span>
                    )}
                  </td>
                </tr>
                <tr className="hover:bg-sand/20">
                  <td className="p-3 font-medium text-ink">Cabin Crew (CABIN_CREW)</td>
                  <td className="p-3 text-right font-semibold">{minCabinCrew}</td>
                  <td className="p-3 text-right font-semibold text-teal">{activeCabinCrew.length}</td>
                  <td className="p-3 text-right font-semibold text-amber-700">{missingCabinCrew}</td>
                  <td className="p-3 text-center">
                    {missingCabinCrew === 0 ? (
                      <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                        Satisfied
                      </span>
                    ) : (
                      <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                        {missingCabinCrew} Needed
                      </span>
                    )}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {coverage.reasons.length > 0 && (
            <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3 text-xs text-amber-900 space-y-1">
              <span className="font-bold">Pending Scheduling Requirements:</span>
              <ul className="list-disc list-inside space-y-0.5 pl-1">
                {coverage.reasons.map((r, i) => (
                  <li key={i}>{r.explanation}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </Panel>

      {/* Active Assignments List (Feature F) */}
      <Panel title={`Currently assigned flight crew (${activeAssignments.length} active)`}>
        {activeAssignments.length === 0 ? (
          <div className="rounded-lg border border-dashed border-line p-6 text-center text-sm text-muted">
            No crew members are currently assigned to this flight. Use the guided slots below to assign eligible crew.
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {activeAssignments.map(a => {
              const member = crew.find(c => c.id === a.crewId)
              return (
                <div
                  key={a.id}
                  className="rounded-xl border border-line bg-white p-4 shadow-sm space-y-2 flex flex-col justify-between"
                >
                  <div className="space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-teal uppercase tracking-wider">
                        {roleLabels[a.assignedRole]}
                      </span>
                      <span className="text-xs font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                        Active
                      </span>
                    </div>
                    <p className="text-sm font-bold text-ink">{member?.name ?? `Crew ${a.crewId}`}</p>
                    <p className="text-xs text-muted">Licence: {member?.licenseNo ?? 'N/A'}</p>
                    <p className="text-xs text-muted">
                      Duty: {utcDisplay(a.dutyStartTime)} to {utcDisplay(a.dutyEndTime)}
                    </p>
                  </div>
                  <div className="pt-2 border-t border-line flex items-center justify-between">
                    <span className="text-xs text-muted">ID: {a.id}</span>
                    <Button
                      secondary
                      className="text-xs py-1 px-2 text-red-600 hover:text-red-700"
                      disabled={cancelBusy === a.id}
                      onClick={() => handleCancel(a)}
                    >
                      {cancelBusy === a.id ? 'Cancelling...' : 'Cancel assignment'}
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </Panel>

      {/* Guided Role-Specific Assignment Section (Feature C & D) */}
      <Panel title="Guided role-specific crew assignment">
        <p className="text-xs text-muted mb-4">
          Select eligible candidates from role-specific dropdowns. The backend validates qualifications, duty windows, rest intervals, and availability before assignment.
        </p>

        <div className="space-y-6">
          {/* Slot: Pilot in Command */}
          <RoleAssignmentSlot
            slotTitle="Captain / Pilot in Command (PILOT)"
            role="PILOT"
            flight={flight}
            crewList={crew}
            activeAssignments={activeAssignments}
            onAssigned={() => {
              reload()
              setNotification({ type: 'success', message: 'Captain / Pilot successfully assigned.' })
            }}
          />

          {/* Slot: First Officer / Co-Pilot */}
          <RoleAssignmentSlot
            slotTitle="First Officer / Co-Pilot (COPILOT)"
            role="COPILOT"
            flight={flight}
            crewList={crew}
            activeAssignments={activeAssignments}
            onAssigned={() => {
              reload()
              setNotification({ type: 'success', message: 'First Officer / Co-Pilot successfully assigned.' })
            }}
          />

          {/* Slots: Cabin Crew (Dynamic based on minCabinCrew) */}
          {Array.from({ length: minCabinCrew }).map((_, index) => (
            <RoleAssignmentSlot
              key={`cabin-${index}`}
              slotTitle={`Cabin Crew Position ${index + 1} of ${minCabinCrew} (CABIN_CREW)`}
              role="CABIN_CREW"
              slotIndex={index}
              flight={flight}
              crewList={crew}
              activeAssignments={activeAssignments}
              onAssigned={() => {
                reload()
                setNotification({
                  type: 'success',
                  message: `Cabin Crew Position ${index + 1} successfully assigned.`,
                })
              }}
            />
          ))}
        </div>
      </Panel>

      {/* Assignment History Audit Log (Feature F) */}
      <Panel title="Flight assignment audit history">
        <p className="text-xs text-muted mb-3">
          Historical and active assignments for this flight. Cancelled assignments preserve historical records and audit versions.
        </p>
        <Register
          headings={['Assignment ID', 'Crew Member', 'Role', 'Duty Window', 'Status', 'Created']}
          empty={!assignments.length}
        >
          {assignments.map(a => {
            const member = crew.find(c => c.id === a.crewId)
            return (
              <tr key={a.id} className="border-b border-line transition-colors hover:bg-sand/30">
                <Cell>
                  <RecordLink to={`/assignments/${a.id}`}>Assignment {a.id}</RecordLink>
                </Cell>
                <Cell>{member?.name ?? `Crew ${a.crewId}`}</Cell>
                <Cell>{roleLabels[a.assignedRole]}</Cell>
                <Cell>
                  {utcDisplay(a.dutyStartTime)} to {utcDisplay(a.dutyEndTime)}
                </Cell>
                <Cell>
                  <RecordStatus value={a.status} />
                </Cell>
                <Cell>{utcDisplay(a.createdAt)}</Cell>
              </tr>
            )
          })}
        </Register>
      </Panel>
    </div>
  )
}

function RoleAssignmentSlot({
  slotTitle,
  role,
  slotIndex = 0,
  flight,
  crewList,
  activeAssignments,
  onAssigned,
}: {
  slotTitle: string
  role: BackendCrewRole
  slotIndex?: number
  flight: FlightView
  crewList: CrewView[]
  activeAssignments: AssignmentView[]
  onAssigned: () => void
}) {
  const roleAssignments = activeAssignments.filter(a => a.assignedRole === role)
  const currentAssigned = roleAssignments[slotIndex]

  const assignedCrewIds = new Set(activeAssignments.map(a => a.crewId))
  const candidateCrew = crewList
    .filter(c => c.role === role)
    .filter(c => !assignedCrewIds.has(c.id))

  const [selectedCrewId, setSelectedCrewId] = useState<number>(0)
  const [preview, setPreview] = useState<{
    decision: EligibilityDecision
    request: AssignmentRequest
  } | null>(null)
  const [error, setError] = useState<NormalizedApiError | null>(null)
  const [checking, setChecking] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const generation = useRef(0)

  useEffect(() => {
    setSelectedCrewId(0)
    setPreview(null)
    setError(null)
  }, [currentAssigned?.id, flight.id])

  async function handleCandidateChange(crewId: number) {
    setSelectedCrewId(crewId)
    setPreview(null)
    setError(null)
    if (!crewId) return

    const ticket = ++generation.current
    setChecking(true)
    const request: AssignmentRequest = {
      crewId,
      flightId: flight.id,
      assignedRole: role,
    }

    try {
      const response = await assignmentService.eligibility(request)
      if (ticket === generation.current) {
        setPreview({ decision: response.data, request })
      }
    } catch (err) {
      if (ticket === generation.current) {
        setError(normalizeApiError(err))
      }
    } finally {
      if (ticket === generation.current) {
        setChecking(false)
      }
    }
  }

  async function handleAssign() {
    if (!preview || !preview.decision.eligible) return
    setSubmitting(true)
    setError(null)
    try {
      await assignmentService.create(preview.request)
      setSelectedCrewId(0)
      setPreview(null)
      onAssigned()
    } catch (err) {
      setError(normalizeApiError(err))
    } finally {
      setSubmitting(false)
    }
  }

  // Already filled slot view
  if (currentAssigned) {
    const member = crewList.find(c => c.id === currentAssigned.crewId)
    return (
      <div className="rounded-xl border border-emerald-200 bg-emerald-50/40 p-4 space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-xs font-bold text-ink uppercase tracking-wider">{slotTitle}</span>
          <span className="text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full">
            ✓ Position Filled
          </span>
        </div>
        <p className="text-sm font-semibold text-ink">
          {member?.name ?? `Crew ${currentAssigned.crewId}`} ({member?.licenseNo ?? 'N/A'})
        </p>
        <p className="text-xs text-muted">
          Active duty window: {utcDisplay(currentAssigned.dutyStartTime)} to{' '}
          {utcDisplay(currentAssigned.dutyEndTime)}
        </p>
      </div>
    )
  }

  // Vacant slot view with role-specific dropdown & live validation
  return (
    <div className="rounded-xl border border-line bg-white p-4 shadow-sm space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-bold text-ink uppercase tracking-wider">{slotTitle}</span>
        <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
          Position Vacant
        </span>
      </div>

      <div className="grid gap-3 sm:grid-cols-3 items-end">
        <div className="sm:col-span-2">
          <label className="text-xs font-medium text-slate-700 block mb-1">
            Select candidate ({roleLabels[role]} roster)
          </label>
          <select
            aria-label={`Select candidate for ${slotTitle}`}
            className="control w-full"
            value={selectedCrewId || ''}
            onChange={e => handleCandidateChange(Number(e.target.value))}
            disabled={checking || submitting}
          >
            <option value="">-- Choose {roleLabels[role]} candidate --</option>
            {candidateCrew.map(c => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.licenseNo}) · {c.experienceYears} yrs exp · {c.active ? 'Active' : 'Inactive'}
              </option>
            ))}
          </select>
        </div>

        <div>
          <Button
            type="button"
            className="w-full"
            disabled={!preview?.decision.eligible || submitting || checking}
            onClick={handleAssign}
          >
            {submitting ? 'Assigning...' : checking ? 'Checking...' : `Assign ${roleLabels[role]}`}
          </Button>
        </div>
      </div>

      {checking && (
        <p className="text-xs text-teal animate-pulse">
          Evaluating backend scheduling rules, aircraft qualifications & duty windows...
        </p>
      )}

      {error && (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800 space-y-1">
          <span className="font-bold">Backend Scheduling Conflict / Error:</span>
          <p>{error.message}</p>
        </div>
      )}

      {preview && (
        <div className="space-y-2 pt-2">
          {preview.decision.eligible ? (
            <div
              data-testid="eligibility-eligible"
              className="rounded-lg border border-emerald-200 bg-emerald-50/70 p-3 text-xs text-emerald-900 space-y-1.5"
            >
              <div className="flex items-center gap-2 font-bold text-emerald-800">
                <span className="h-2 w-2 rounded-full bg-emerald-600" />
                <span>Backend Eligibility Check Passed — Ready to Assign</span>
              </div>
              {preview.decision.dutyWindow && (
                <p className="text-emerald-800">
                  Calculated Duty Window: {utcDisplay(preview.decision.dutyWindow.start)} to{' '}
                  {utcDisplay(preview.decision.dutyWindow.end)}
                </p>
              )}
              {preview.decision.dutyLoad && (
                <p className="text-muted text-[11px]">
                  Duty Load: 24h {preview.decision.dutyLoad.maximumRolling24Hours} · 7d{' '}
                  {preview.decision.dutyLoad.maximumRolling7Days}
                </p>
              )}
            </div>
          ) : (
            <div
              data-testid="eligibility-ineligible"
              className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900 space-y-1.5"
            >
              <div className="flex items-center gap-2 font-bold text-amber-800">
                <span className="h-2 w-2 rounded-full bg-amber-600" />
                <span>Candidate Ineligible for this Flight</span>
              </div>
              <ul className="list-disc list-inside space-y-1 pl-1">
                {preview.decision.reasons.map((r, i) => (
                  <li key={i}>
                    <span className="font-semibold">{r.code}:</span> {r.explanation}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
