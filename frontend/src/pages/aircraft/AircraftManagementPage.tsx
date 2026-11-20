import { useRemote } from '../../api/useRemote'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { aircraftService } from '../../api/services/aircraftService'
import { readAircraft, readPages, readReferences, utcDisplay } from '../../api/live'
import type { AircraftRequest, AircraftView, AircraftTypeView } from '../../types/api'
import { Button } from '../../components/common/Controls'
import { ReferenceData } from '../../components/common/ReferenceData'
import {
  Cell,
  Field,
  LiveHeader,
  MutationForm,
  Panel,
  RecordLink,
  Register,
  RemoteState,
  Select,
} from '../../components/common/LiveData'

async function readDirectory() {
  const [items, refs] = await Promise.all([readAircraft(), readReferences()])
  return { items, ...refs }
}

export function AircraftManagementPage() {
  const { aircraftId } = useParams()
  return aircraftId ? <Profile key={aircraftId} id={Number(aircraftId)} /> : <Directory />
}

function Directory() {
  const state = useRemote(readDirectory)
  const [q, setQ] = useState('')
  const [type, setType] = useState('ALL')
  const [active, setActive] = useState('ALL')
  const [sort, setSort] = useState('registration')
  const [edit, setEdit] = useState<AircraftView | 'new' | null>(null)

  const back = () => {
    setEdit(null)
    state.reload()
  }

  if (edit && state.data) {
    return <AircraftForm original={edit === 'new' ? undefined : edit} types={state.data.types} back={back} />
  }

  const shown = state.data?.items
    .filter(x => `${x.registration} ${x.model}`.toLowerCase().includes(q.toLowerCase()))
    .filter(x => type === 'ALL' || String(x.aircraftTypeId) === type)
    .filter(x => active === 'ALL' || String(x.active) === active)
    .sort((a, b) =>
      sort === 'model'
        ? a.model.localeCompare(b.model)
        : a.registration.localeCompare(b.registration)
    )

  return (
    <div className="space-y-6">
      <LiveHeader title="Aircraft directory">
        <Button disabled={!state.data} onClick={() => setEdit('new')}>
          Add aircraft
        </Button>
      </LiveHeader>

      <RemoteState state={state} />

      <section
        className="grid gap-4 rounded-xl border border-line bg-white p-5 shadow-card sm:grid-cols-2 lg:grid-cols-4 items-end"
        aria-label="Aircraft filters"
      >
        <Field label="Search aircraft">
          <input
            className="control w-full"
            placeholder="Search registration or model"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Field>
        <Select
          label="Aircraft type"
          value={type}
          onChange={setType}
          options={[
            ['ALL', 'All types'],
            ...(state.data?.types.map(x => [String(x.id), x.model] as [string, string]) ?? []),
          ]}
        />
        <Select
          label="Status"
          value={active}
          onChange={setActive}
          options={[
            ['ALL', 'All statuses'],
            ['true', 'Active'],
            ['false', 'Inactive'],
          ]}
        />
        <Select
          label="Sort"
          value={sort}
          onChange={setSort}
          options={[
            ['registration', 'Registration'],
            ['model', 'Aircraft type'],
          ]}
        />
      </section>

      <p className="text-xs text-muted">
        Administrative status does not establish operational availability.
      </p>

      {shown && (
        <Register
          headings={['ID', 'Registration', 'Type', 'Manufacturer', 'Capacity', 'Cabin minimum', 'Status', 'Edit']}
          empty={!shown.length}
        >
          {shown.map(x => {
            const t = state.data!.types.find(t => t.id === x.aircraftTypeId)
            return (
              <tr key={x.id} className="border-b border-line transition-colors hover:bg-sand/30">
                <Cell>{x.id}</Cell>
                <Cell>
                  <RecordLink to={`/aircraft/${x.id}`}>{x.registration}</RecordLink>
                </Cell>
                <Cell>{x.model}</Cell>
                <Cell>{t?.manufacturer ?? 'Unavailable'}</Cell>
                <Cell>{t?.capacity ?? 'Unavailable'}</Cell>
                <Cell>{t?.minCabinCrew ?? 'Unavailable'}</Cell>
                <Cell>{x.active ? 'Active' : 'Inactive'}</Cell>
                <Cell>
                  <Button secondary onClick={() => setEdit(x)}>
                    Edit
                  </Button>
                </Cell>
              </tr>
            )
          })}
        </Register>
      )}

      <ReferenceData />
    </div>
  )
}

function AircraftForm({
  original,
  types,
  back,
}: {
  original?: AircraftView
  types: AircraftTypeView[]
  back: () => void
}) {
  const [v, setV] = useState<AircraftRequest>({
    registration: original?.registration ?? '',
    aircraftTypeId: original?.aircraftTypeId ?? 0,
  })

  return (
    <MutationForm
      title={original ? `Edit ${original.registration}` : 'Add aircraft'}
      done={back}
      close={back}
      prepare={() => {
        const request = { ...v }
        return () => (original ? aircraftService.update(original, request) : aircraftService.create(request))
      }}
    >
      <Field label="Registration">
        <input
          aria-label="Registration"
          required
          maxLength={24}
          className="control w-full"
          value={v.registration}
          onChange={e => setV({ ...v, registration: e.target.value })}
        />
      </Field>
      <Field label="Aircraft type">
        <select
          aria-label="Aircraft type"
          required
          className="control w-full"
          value={v.aircraftTypeId || ''}
          onChange={e => setV({ ...v, aircraftTypeId: Number(e.target.value) })}
        >
          <option value="">Select a reference type</option>
          {types.map(t => (
            <option key={t.id} value={t.id}>
              {t.model}
            </option>
          ))}
        </select>
      </Field>
    </MutationForm>
  )
}

function Profile({ id }: { id: number }) {
  const load = useMemo(
    () => async () => {
      const record = (await aircraftService.get(id)).data
      const [flights, refs] = await Promise.all([
        readPages(params => aircraftService.flights(id, params)),
        readReferences(),
      ])
      return { record, flights, ...refs }
    },
    [id]
  )

  const state = useRemote(load)
  const [action, setAction] = useState<'edit' | 'activity' | null>(null)
  const back = () => {
    setAction(null)
    state.reload()
  }
  const x = state.data?.record

  if (x && action === 'edit') {
    return <AircraftForm original={x} types={state.data!.types} back={back} />
  }

  return (
    <div className="space-y-6">
      <LiveHeader title={x?.registration ?? 'Aircraft profile'}>
        <RecordLink to="/aircraft">Directory</RecordLink>
      </LiveHeader>

      <RemoteState state={state} />

      {x && (
        <>
          <Panel title="Aircraft details">
            <p className="text-sm font-medium text-ink">
              {x.model} | {x.active ? 'Active' : 'Inactive'}
            </p>
            <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-line">
              <Button secondary onClick={() => setAction('edit')}>
                Edit
              </Button>
              <Button secondary onClick={() => setAction('activity')}>
                {x.active ? 'Review deactivation' : 'Review activation'}
              </Button>
            </div>
          </Panel>

          {action === 'activity' && (
            <MutationForm
              title={x.active ? 'Deactivate aircraft' : 'Activate aircraft'}
              prepare={() => () => aircraftService.setActivity(x, { active: !x.active })}
              done={back}
              close={back}
            />
          )}

          <Panel title="Associated flights">
            {state.data!.flights.length > 0 ? (
              <div className="divide-y divide-line rounded-lg border border-line bg-slate-50/50">
                {state.data!.flights.map(f => (
                  <p key={f.id} className="p-3 text-sm text-ink">
                    <RecordLink to={`/flights/${f.id}`}>{f.flightNumber}</RecordLink> | {f.sourceIataCode} to {f.destinationIataCode} | {utcDisplay(f.departureTime)} | {f.status}
                  </p>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted">No associated flights</p>
            )}
          </Panel>

          <ReferenceData />
        </>
      )}
    </div>
  )
}
