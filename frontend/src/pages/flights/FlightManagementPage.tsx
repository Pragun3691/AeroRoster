import { useRemote } from '../../api/useRemote'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { flightService } from '../../api/services/flightService'
import {
  readAircraft,
  readFlights,
  readPages,
  readReferences,
  utcDisplay,
  utcInput,
  utcInstant,
} from '../../api/live'
import type { AircraftView, AircraftTypeView, AirportView, FlightRequest, FlightView } from '../../types/api'
import { Button } from '../../components/common/Controls'
import {
  Cell,
  Coverage,
  Field,
  LiveHeader,
  MutationForm,
  Panel,
  RecordLink,
  Register,
  RecordStatus,
  RemoteState,
  Select,
} from '../../components/common/LiveData'

async function readDirectory() {
  const [items, aircraft, refs] = await Promise.all([
    readFlights(),
    readAircraft(),
    readReferences(),
  ])
  return { items, aircraft, ...refs }
}

export function FlightManagementPage() {
  const { flightId } = useParams()
  return flightId ? <Profile key={flightId} id={Number(flightId)} /> : <Directory />
}

function Directory() {
  const state = useRemote(readDirectory)
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('ALL')
  const [date, setDate] = useState('')
  const [sort, setSort] = useState('departure')
  const [edit, setEdit] = useState<FlightView | 'new' | null>(null)

  const back = () => {
    setEdit(null)
    state.reload()
  }

  if (edit && state.data) {
    return (
      <FlightForm
        original={edit === 'new' ? undefined : edit}
        aircraft={state.data.aircraft}
        airports={state.data.airports}
        types={state.data.types}
        back={back}
      />
    )
  }

  const shown = state.data?.items
    .filter(x =>
      `${x.flightNumber} ${x.sourceIataCode} ${x.destinationIataCode}`
        .toLowerCase()
        .includes(q.toLowerCase())
    )
    .filter(x => status === 'ALL' || x.status === status)
    .filter(x => !date || x.departureTime.startsWith(date))
    .sort((a, b) =>
      sort === 'departure'
        ? a.departureTime.localeCompare(b.departureTime)
        : a.flightNumber.localeCompare(b.flightNumber)
    )

  return (
    <div className="space-y-6">
      <LiveHeader title="Flight directory">
        <Button disabled={!state.data} onClick={() => setEdit('new')}>
          Add flight
        </Button>
      </LiveHeader>

      <RemoteState state={state} />

      <section
        className="grid gap-4 rounded-xl border border-line bg-white p-5 shadow-card sm:grid-cols-2 lg:grid-cols-5 items-end"
        aria-label="Flight filters"
      >
        <Field label="Search flights">
          <input
            className="control w-full"
            placeholder="Flight number, origin, or destination"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Field>
        <Select
          label="Flight status"
          value={status}
          onChange={setStatus}
          options={[
            ['ALL', 'All statuses'],
            ['SCHEDULED', 'Scheduled'],
            ['COMPLETED', 'Completed'],
            ['CANCELLED', 'Cancelled'],
          ]}
        />
        <Field label="Departure date">
          <input
            aria-label="Departure date"
            className="control w-full"
            type="date"
            value={date}
            onChange={e => setDate(e.target.value)}
          />
        </Field>
        <Select
          label="Sort by"
          value={sort}
          onChange={setSort}
          options={[
            ['departure', 'Departure time'],
            ['flightNumber', 'Flight number'],
          ]}
        />
        <Button
          secondary
          className="h-10 self-end"
          onClick={() => {
            setQ('')
            setStatus('ALL')
            setDate('')
            setSort('departure')
          }}
        >
          Reset
        </Button>
      </section>

      {shown && (
        <Register
          headings={['Flight', 'Route', 'Departure', 'Arrival', 'Aircraft', 'Status', 'Edit']}
          empty={!shown.length}
        >
          {shown.map(x => (
            <tr key={x.id} className="border-b border-line transition-colors hover:bg-sand/30">
              <Cell>
                <RecordLink to={`/flights/${x.id}`}>{x.flightNumber}</RecordLink>
              </Cell>
              <Cell>
                {x.sourceIataCode} to {x.destinationIataCode}
              </Cell>
              <Cell>{utcDisplay(x.departureTime)}</Cell>
              <Cell>{utcDisplay(x.arrivalTime)}</Cell>
              <Cell>{x.aircraftRegistration}</Cell>
              <Cell>
                <RecordStatus value={x.status} />
              </Cell>
              <Cell>
                <div className="flex items-center gap-2">
                  <Button
                    secondary
                    disabled={x.status !== 'SCHEDULED'}
                    onClick={() => setEdit(x)}
                  >
                    Edit
                  </Button>
                  {x.status === 'SCHEDULED' && (
                    <RecordLink
                      to={`/flights/${x.id}/assign`}
                      className="text-xs font-semibold text-teal hover:underline whitespace-nowrap"
                    >
                      Assign crew
                    </RecordLink>
                  )}
                </div>
              </Cell>
            </tr>
          ))}
        </Register>
      )}
    </div>
  )
}

function FlightForm({
  original,
  aircraft,
  airports,
  types,
  back,
}: {
  original?: FlightView
  aircraft: AircraftView[]
  airports: AirportView[]
  types?: AircraftTypeView[]
  back: () => void
}) {
  const [v, setV] = useState<FlightRequest>(
    original
      ? {
          flightNumber: original.flightNumber,
          sourceAirportId: original.sourceAirportId,
          destinationAirportId: original.destinationAirportId,
          aircraftId: original.aircraftId,
          departureTime: utcInput(original.departureTime),
          arrivalTime: utcInput(original.arrivalTime),
        }
      : {
          flightNumber: '',
          sourceAirportId: 0,
          destinationAirportId: 0,
          aircraftId: 0,
          departureTime: '',
          arrivalTime: '',
        }
  )

  const selectedAircraft = aircraft.find(a => a.id === v.aircraftId)
  const selectedType = types?.find(t => t.id === selectedAircraft?.aircraftTypeId)

  return (
    <MutationForm
      title={original ? `Edit ${original.flightNumber}` : 'Add flight'}
      done={back}
      close={back}
      prepare={() => {
        const request = {
          ...v,
          departureTime: utcInstant(v.departureTime),
          arrivalTime: utcInstant(v.arrivalTime),
        }
        return () => (original ? flightService.update(original, request) : flightService.create(request))
      }}
    >
      <Field label="Flight number">
        <input
          aria-label="Flight number"
          required
          maxLength={15}
          className="control w-full"
          value={v.flightNumber}
          onChange={e => setV({ ...v, flightNumber: e.target.value })}
        />
      </Field>
      {(['sourceAirportId', 'destinationAirportId'] as const).map((key, index) => (
        <Field key={key} label={index ? 'Destination airport' : 'Origin airport'}>
          <select
            required
            aria-label={index ? 'Destination airport' : 'Origin airport'}
            className="control w-full"
            value={v[key] || ''}
            onChange={e => setV({ ...v, [key]: Number(e.target.value) })}
          >
            <option value="">Select airport</option>
            {airports.map(x => (
              <option key={x.id} value={x.id}>
                {x.iataCode} | {x.name}
              </option>
            ))}
          </select>
        </Field>
      ))}
      <Field label="Aircraft">
        <select
          aria-label="Aircraft"
          required
          className="control w-full"
          value={v.aircraftId || ''}
          onChange={e => setV({ ...v, aircraftId: Number(e.target.value) })}
        >
          <option value="">Select aircraft</option>
          {aircraft.map(x => (
            <option key={x.id} value={x.id}>
              {x.registration} — {x.model} ({x.active ? 'Active' : 'Inactive'})
            </option>
          ))}
        </select>
      </Field>
      {selectedAircraft && (
        <div className="rounded-xl border border-line bg-sand/30 p-4 space-y-2 text-sm" data-testid="aircraft-crew-requirements">
          <div className="font-semibold text-ink flex items-center justify-between">
            <span>Aircraft Staffing & Specification: {selectedAircraft.registration} ({selectedAircraft.model})</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-teal-100 text-teal-800 font-medium">
              Required complement: {2 + (selectedType?.minCabinCrew ?? 2)} crew
            </span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs text-muted">
            <div><span className="font-semibold text-ink">Registration:</span> {selectedAircraft.registration}</div>
            <div><span className="font-semibold text-ink">Model / Type:</span> {selectedAircraft.model}</div>
            <div><span className="font-semibold text-ink">Passenger Capacity:</span> {selectedType?.capacity ?? 'N/A'}</div>
            <div><span className="font-semibold text-ink">Required Pilot:</span> 1</div>
            <div><span className="font-semibold text-ink">Required Co-pilot:</span> 1</div>
            <div><span className="font-semibold text-ink">Required Cabin Crew:</span> {selectedType?.minCabinCrew ?? 2}</div>
          </div>
          <p className="text-xs text-muted">
            Flight will be created with 0 crew assignments (Incomplete coverage). Assignments must be assigned subsequently via guided crew assignment.
          </p>
        </div>
      )}
      {(['departureTime', 'arrivalTime'] as const).map((key, index) => (
        <Field key={key} label={index ? 'Arrival time (UTC)' : 'Departure time (UTC)'}>
          <input
            required
            aria-label={index ? 'Arrival time (UTC)' : 'Departure time (UTC)'}
            type="text"
            placeholder="YYYY-MM-DDTHH:mm:ss (UTC)"
            className="control w-full"
            value={v[key]}
            onChange={e => setV({ ...v, [key]: e.target.value })}
          />
        </Field>
      ))}
    </MutationForm>
  )
}

function Profile({ id }: { id: number }) {
  const load = useMemo(
    () => async () => {
      const record = (await flightService.get(id)).data
      const [coverage, assignments, aircraft, refs] = await Promise.all([
        flightService.coverage(id),
        readPages(params => flightService.assignments(id, { ...params, activeOnly: false })),
        readAircraft(),
        readReferences(),
      ])
      return { record, coverage: coverage.data, assignments, aircraft, ...refs }
    },
    [id]
  )

  const state = useRemote(load)
  const [action, setAction] = useState<'edit' | 'cancel' | 'complete' | null>(null)
  const x = state.data?.record
  const back = () => {
    setAction(null)
    state.reload()
  }

  if (x && action === 'edit') {
    return (
      <FlightForm
        original={x}
        aircraft={state.data!.aircraft}
        airports={state.data!.airports}
        types={state.data!.types}
        back={back}
      />
    )
  }

  return (
    <div className="space-y-6">
      <LiveHeader title={x?.flightNumber ?? 'Flight detail'}>
        <RecordLink to="/flights">Directory</RecordLink>
      </LiveHeader>

      <RemoteState state={state} />

      {x && (
        <>
          <Panel title="Flight identity and route">
            <p className="text-sm font-semibold text-ink">
              {x.sourceIataCode} to {x.destinationIataCode} | {x.status}
            </p>
            <p className="text-sm text-muted">
              {x.aircraftRegistration} | Aircraft {x.aircraftId} | Type {x.aircraftTypeId}
            </p>
            <p className="text-sm text-muted">
              {utcDisplay(x.departureTime)} to {utcDisplay(x.arrivalTime)}
            </p>
            {x.status === 'SCHEDULED' && (
              <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-line">
                <RecordLink
                  to={`/flights/${x.id}/assign`}
                  className="button primary inline-flex items-center"
                >
                  Assign crew
                </RecordLink>
                <Button secondary onClick={() => setAction('edit')}>
                  Edit
                </Button>
                <Button secondary onClick={() => setAction('cancel')}>
                  Review cancellation
                </Button>
                <Button secondary onClick={() => setAction('complete')}>
                  Review completion
                </Button>
              </div>
            )}
          </Panel>

          {(action === 'cancel' || action === 'complete') && (
            <MutationForm
              title={action === 'cancel' ? 'Cancel flight' : 'Complete flight'}
              prepare={() => () =>
                action === 'cancel'
                  ? flightService.cancel(x)
                  : flightService.updateStatus(x, { status: 'COMPLETED' })}
              done={back}
              close={back}
            />
          )}

          <Coverage data={state.data!.coverage} />

          <Panel title="Flight assignment history">
            {state.data!.assignments.length > 0 ? (
              <div className="divide-y divide-line rounded-lg border border-line bg-slate-50/50">
                {state.data!.assignments.map(a => (
                  <p key={a.id} className="p-3 text-sm text-ink">
                    <RecordLink to={`/assignments/${a.id}`}>Assignment {a.id}</RecordLink> | Crew {a.crewId} | {a.assignedRole} | {a.status}
                  </p>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted">No assignment history</p>
            )}
          </Panel>
        </>
      )}
    </div>
  )
}
