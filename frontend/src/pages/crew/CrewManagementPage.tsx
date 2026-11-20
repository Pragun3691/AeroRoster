import { useRemote } from '../../api/useRemote'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { crewService } from '../../api/services/crewService'
import { qualificationService } from '../../api/services/qualificationService'
import { readCrew, readPages, roleLabels, utcDisplay } from '../../api/live'
import type { CrewRequest, CrewView, BackendCrewRole } from '../../types/api'
import { Button } from '../../components/common/Controls'
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

export function CrewManagementPage() {
  const { crewId } = useParams()
  return crewId ? <Profile key={crewId} id={Number(crewId)} /> : <Directory />
}

function Directory() {
  const state = useRemote(readCrew)
  const [q, setQ] = useState('')
  const [role, setRole] = useState('ALL')
  const [active, setActive] = useState('ALL')
  const [sort, setSort] = useState('name')
  const [edit, setEdit] = useState<CrewView | 'new' | null>(null)

  const back = () => {
    setEdit(null)
    state.reload()
  }

  if (edit) {
    return <CrewForm original={edit === 'new' ? undefined : edit} back={back} />
  }

  const shown = state.data
    ?.filter(x => `${x.name} ${x.licenseNo}`.toLowerCase().includes(q.toLowerCase()))
    .filter(x => role === 'ALL' || x.role === role)
    .filter(x => active === 'ALL' || String(x.active) === active)
    .sort((a, b) =>
      sort === 'experience'
        ? b.experienceYears - a.experienceYears
        : a.name.localeCompare(b.name)
    )

  return (
    <div className="space-y-6">
      <LiveHeader title="Crew directory">
        <Button onClick={() => setEdit('new')}>Add crew</Button>
      </LiveHeader>

      <RemoteState state={state} />

      <section
        className="grid gap-4 rounded-xl border border-line bg-white p-5 shadow-card sm:grid-cols-2 lg:grid-cols-4 items-end"
        aria-label="Crew filters"
      >
        <Field label="Search crew">
          <input
            className="control w-full"
            placeholder="Search name or licence"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </Field>
        <Select
          label="Role"
          value={role}
          onChange={setRole}
          options={[['ALL', 'All roles'], ...Object.entries(roleLabels)]}
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
            ['name', 'Name A-Z'],
            ['experience', 'Experience'],
          ]}
        />
      </section>

      {shown && (
        <Register
          headings={['ID', 'Crew member', 'Role', 'Licence', 'Experience', 'Contact', 'Status', 'Edit']}
          empty={!shown.length}
        >
          {shown.map(x => (
            <tr key={x.id} className="border-b border-line transition-colors hover:bg-sand/30">
              <Cell>{x.id}</Cell>
              <Cell>
                <RecordLink to={`/crew/${x.id}`}>{x.name}</RecordLink>
              </Cell>
              <Cell>{roleLabels[x.role]}</Cell>
              <Cell>{x.licenseNo}</Cell>
              <Cell>{x.experienceYears} years</Cell>
              <Cell>{x.contactNo}</Cell>
              <Cell>{x.active ? 'Active' : 'Inactive'}</Cell>
              <Cell>
                <Button secondary onClick={() => setEdit(x)}>
                  Edit
                </Button>
              </Cell>
            </tr>
          ))}
        </Register>
      )}
    </div>
  )
}

function CrewForm({
  original,
  back,
}: {
  original?: CrewView
  back: () => void
}) {
  const [v, setV] = useState<CrewRequest>(
    original
      ? {
          name: original.name,
          role: original.role,
          licenseNo: original.licenseNo,
          experienceYears: original.experienceYears,
          contactNo: original.contactNo,
        }
      : {
          name: '',
          role: 'PILOT',
          licenseNo: '',
          experienceYears: 0,
          contactNo: '',
        }
  )

  return (
    <MutationForm
      title={original ? `Edit ${original.name}` : 'Add crew member'}
      done={back}
      close={back}
      prepare={() => {
        const request = { ...v }
        return () => (original ? crewService.update(original, request) : crewService.create(request))
      }}
    >
      <Field label="Name">
        <input
          aria-label="Name"
          required
          maxLength={100}
          className="control w-full"
          value={v.name}
          onChange={e => setV({ ...v, name: e.target.value })}
        />
      </Field>
      <Select
        label="Role"
        value={v.role}
        onChange={role => setV({ ...v, role: role as BackendCrewRole })}
        options={Object.entries(roleLabels)}
      />
      <Field label="Licence number">
        <input
          aria-label="Licence number"
          required
          maxLength={30}
          className="control w-full"
          value={v.licenseNo}
          onChange={e => setV({ ...v, licenseNo: e.target.value })}
        />
      </Field>
      <Field label="Experience years">
        <input
          aria-label="Experience years"
          required
          min={0}
          step={1}
          type="number"
          className="control w-full"
          value={v.experienceYears}
          onChange={e => setV({ ...v, experienceYears: Number(e.target.value) })}
        />
      </Field>
      <Field label="Contact number">
        <input
          aria-label="Contact number"
          required
          maxLength={20}
          className="control w-full"
          value={v.contactNo}
          onChange={e => setV({ ...v, contactNo: e.target.value })}
        />
      </Field>
    </MutationForm>
  )
}

function Profile({ id }: { id: number }) {
  const load = useMemo(
    () => async () => {
      const record = (await crewService.get(id)).data
      const [qualifications, assignments] = await Promise.all([
        qualificationService.list(id),
        readPages(params => crewService.assignments(id, { ...params, activeOnly: false })),
      ])
      return { record, qualifications: qualifications.data, assignments }
    },
    [id]
  )

  const state = useRemote(load)
  const [action, setAction] = useState<'edit' | 'activity' | null>(null)
  const back = () => {
    setAction(null)
    state.reload()
  }

  if (state.data && action === 'edit') {
    return <CrewForm original={state.data.record} back={back} />
  }

  const x = state.data?.record

  return (
    <div className="space-y-6">
      <LiveHeader title={x?.name ?? 'Crew profile'}>
        <RecordLink to="/crew">Directory</RecordLink>
      </LiveHeader>

      <RemoteState state={state} />

      {x && (
        <>
          <Panel title="Personnel record">
            <p className="text-sm font-medium text-ink">
              {roleLabels[x.role]} | {x.licenseNo} | {x.experienceYears} years | {x.contactNo} | {x.active ? 'Active' : 'Inactive'}
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
              title={x.active ? 'Deactivate crew' : 'Activate crew'}
              prepare={() => () => crewService.setActivity(x, { active: !x.active })}
              done={back}
              close={back}
            />
          )}

          <Panel title="Qualification summary">
            {state.data!.qualifications.length > 0 ? (
              <div className="divide-y divide-line rounded-lg border border-line bg-slate-50/50">
                {state.data!.qualifications.map(q => (
                  <p key={q.id} className="p-3 text-sm text-ink">
                    {q.model} | {roleLabels[q.requiredRole]} | {q.validUntil ?? 'No expiry'}
                  </p>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted">No held qualifications</p>
            )}
          </Panel>

          <Panel title="Assignment history">
            {state.data!.assignments.length > 0 ? (
              <div className="divide-y divide-line rounded-lg border border-line bg-slate-50/50">
                {state.data!.assignments.map(a => (
                  <p key={a.id} className="p-3 text-sm text-ink">
                    <RecordLink to={`/assignments/${a.id}`}>{a.flightNumber}</RecordLink> | {roleLabels[a.assignedRole]} | {a.status} | {utcDisplay(a.dutyStartTime)}
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
