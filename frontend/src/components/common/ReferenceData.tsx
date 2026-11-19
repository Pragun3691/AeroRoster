import { useRemote } from '../../api/useRemote'
import { readReferences } from '../../api/live'
import { Panel, RemoteState } from './LiveData'
export function ReferenceData() {
  const state = useRemote(readReferences)
  return <><RemoteState state={state} />{state.data && <Panel title="Aircraft types and airports"><div className="grid gap-5 md:grid-cols-2">{state.data.types.map(type => <article key={type.id}><h3 className="font-semibold">{type.model}</h3><p>{type.manufacturer} · Capacity {type.capacity} · Minimum cabin crew {type.minCabinCrew}</p></article>)}</div><p>{state.data.airports.map(x => `${x.iataCode} (${x.city}, ${x.country})`).join(' · ') || 'No airports available'}</p><p>{state.data.requirements.map(x => `${x.model}: ${x.requiredRole}`).join(' · ') || 'No qualification requirements available'}</p></Panel>}</>
}
