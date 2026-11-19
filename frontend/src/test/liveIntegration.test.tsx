import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CrewManagementPage } from '../pages/crew/CrewManagementPage'
import { AircraftManagementPage } from '../pages/aircraft/AircraftManagementPage'
import { FlightManagementPage } from '../pages/flights/FlightManagementPage'
import { QualificationsManagementPage } from '../pages/qualifications/QualificationsManagementPage'
import { AssignmentsManagementPage } from '../pages/assignments/AssignmentsManagementPage'
import { UnavailabilityManagementPage } from '../pages/unavailability/UnavailabilityManagementPage'
import { DashboardPage } from '../pages/DashboardPage'
import { MutationForm } from '../components/common/LiveData'
import { failure, mockApi, page } from './liveApi'

let api: ReturnType<typeof mockApi>
beforeEach(() => { api = mockApi() })
afterEach(() => vi.restoreAllMocks())
const modules = [
 { name: 'crew', component: CrewManagementPage, ready: 'Aarav Mehta', edit: 'Edit' },
 { name: 'aircraft', component: AircraftManagementPage, ready: 'VT-LIVE', edit: 'Edit' },
 { name: 'flights', component: FlightManagementPage, ready: 'AR101', edit: 'Edit' },
 { name: 'qualifications', component: QualificationsManagementPage, ready: 'Qualification summary', edit: 'Update expiry' },
 { name: 'assignments', component: AssignmentsManagementPage, ready: 'Assignment summary', edit: '' },
 { name: 'unavailability', component: UnavailabilityManagementPage, ready: 'Annual leave', edit: 'Edit' },
 { name: 'dashboard', component: DashboardPage, ready: 'Total crew', edit: '' },
]
const outcomes = [[400,'VALIDATION_ERROR','validation'],[404,'NOT_FOUND','not-found'],[409,'BUSINESS_CONFLICT','conflict'],[409,'STALE_VERSION','stale-version'],[428,'PRECONDITION_REQUIRED','precondition'],[500,'INTERNAL_ERROR','server']] as const
const confirm = async () => { await userEvent.click(screen.getByText('Review operation')); await userEvent.click(screen.getByText('Confirm operation')) }

describe.each(modules)('$name read boundaries', ({ component: Component }) => {
 it('shows loading until the network resolves', () => { api.get.mockImplementation(() => new Promise(() => {})); render(<MemoryRouter><Component /></MemoryRouter>); expect(screen.getAllByRole('status')[0]).toHaveTextContent('Loading') })
 it('handles empty database collections without demo records', async () => { api.get.mockImplementation((async (url: string) => ({ data: ['/crew','/aircraft','/flights'].includes(url) ? page([]) : [] })) as never); render(<MemoryRouter><Component /></MemoryRouter>); await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument()); expect(screen.queryByText('Aarav Mehta')).not.toBeInTheDocument(); expect(screen.queryByRole('alert')).not.toBeInTheDocument() })
 it.each(outcomes)('shows HTTP %s %s', async (status, code, kind) => { api.get.mockRejectedValue(failure(status,code)); render(<MemoryRouter><Component /></MemoryRouter>); expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(kind) })
 it.each([['network',new AxiosError('offline')],['timeout',new AxiosError('timeout','ECONNABORTED')]] as const)('shows %s without mock fallback', async (kind,error) => { api.get.mockRejectedValue(error); render(<MemoryRouter><Component /></MemoryRouter>); expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(kind); expect(screen.queryByText('Aarav Mehta')).not.toBeInTheDocument() })
})
describe.each(modules.filter(x => Boolean(x.edit)))('$name mutation boundaries', ({ component: Component, ready, edit }) => {
 it.each(outcomes)('surfaces HTTP %s %s and retains the draft', async (status,code,kind) => { render(<MemoryRouter><Component /></MemoryRouter>); await screen.findByText(ready); await userEvent.click(screen.getAllByRole('button',{name:edit})[0]); api.put.mockRejectedValue(failure(status,code)); await confirm(); expect(await screen.findByRole('alert')).toHaveTextContent(kind); expect(api.put).toHaveBeenCalledTimes(1); if (code === 'STALE_VERSION') expect(screen.getByText('Review operation')).toBeDisabled() })
})
describe('assignment mutation errors', () => {
 it.each(outcomes)('retains original cancellation on HTTP %s %s', async(status,code,kind)=>{render(<MemoryRouter initialEntries={['/assignments/1']}><Routes><Route path="/assignments/:assignmentId" element={<AssignmentsManagementPage/>}/></Routes></MemoryRouter>);await userEvent.click(await screen.findByText('Review cancellation'));api.put.mockRejectedValue(failure(status,code));await confirm();expect(await screen.findByRole('alert')).toHaveTextContent(kind);expect(api.put).toHaveBeenCalledWith('/assignments/1/cancellation',{},{headers:{'X-Expected-Version':'1:6'}})})
})
describe('mutation submission control',()=>{
 it('prevents duplicate confirmation while a write is pending and refreshes automatically once',async()=>{
  let resolve:()=>void=()=>{};const commit=vi.fn(()=>new Promise<void>(r=>{resolve=r})),done=vi.fn()
  render(<MutationForm title="Test write" prepare={()=>commit} done={done} close={()=>{}} />)
  await userEvent.click(screen.getByText('Review operation'));await userEvent.dblClick(screen.getByText('Confirm operation'));expect(commit).toHaveBeenCalledTimes(1);expect(screen.getByText(/Saving/)).toBeDisabled();resolve();await waitFor(()=>expect(done).toHaveBeenCalledTimes(1))
 })
})

describe('successful API-backed creation workflows',()=>{
 it('creates a crew member with exact editable fields and no version header',async()=>{render(<MemoryRouter><CrewManagementPage/></MemoryRouter>);await userEvent.click(screen.getByText('Add crew'));for(const [label,value] of [['Name','New crew'],['Licence number','CPL-NEW'],['Contact number','9000000000']])await userEvent.type(screen.getByLabelText(label),value);await userEvent.selectOptions(screen.getByLabelText('Role'),'COPILOT');await confirm();expect(api.post).toHaveBeenCalledWith('/crew',{name:'New crew',role:'COPILOT',licenseNo:'CPL-NEW',experienceYears:0,contactNo:'9000000000'});await screen.findByText('Aarav Mehta')})
 it('registers aircraft and refreshes the fleet',async()=>{render(<MemoryRouter><AircraftManagementPage/></MemoryRouter>);await screen.findByText('VT-LIVE');await userEvent.click(screen.getByText('Add aircraft'));await userEvent.type(screen.getByLabelText('Registration'),'VT-NEW');await userEvent.selectOptions(screen.getByLabelText('Aircraft type'),'1');await confirm();expect(api.post).toHaveBeenCalledWith('/aircraft',{registration:'VT-NEW',aircraftTypeId:1});await waitFor(()=>expect(api.get.mock.calls.filter(([u])=>u==='/aircraft').length).toBe(2))})
 it('creates flights with UTC-labelled times serialized to Z',async()=>{render(<MemoryRouter><FlightManagementPage/></MemoryRouter>);await screen.findByText('AR101');await userEvent.click(screen.getByText('Add flight'));await userEvent.type(screen.getByLabelText('Flight number'),'AR202');await userEvent.selectOptions(screen.getByLabelText('Origin airport'),'1');await userEvent.selectOptions(screen.getByLabelText('Destination airport'),'2');await userEvent.selectOptions(screen.getByLabelText('Aircraft'),'1');await userEvent.type(screen.getByLabelText('Departure time (UTC)'),'2026-12-01T08:00:00');await userEvent.type(screen.getByLabelText('Arrival time (UTC)'),'2026-12-01T10:00:00');await confirm();expect(api.post).toHaveBeenCalledWith('/flights',{flightNumber:'AR202',sourceAirportId:1,destinationAirportId:2,aircraftId:1,departureTime:'2026-12-01T08:00:00Z',arrivalTime:'2026-12-01T10:00:00Z'})})
 it('creates crew unavailability without an expected-version header',async()=>{render(<MemoryRouter><UnavailabilityManagementPage/></MemoryRouter>);await screen.findByText('Annual leave');await userEvent.click(screen.getByText('Add unavailability'));await userEvent.selectOptions(screen.getByLabelText('Crew member'),'1');await userEvent.type(screen.getByLabelText('Start time (UTC)'),'2026-12-01T08:00:00');await userEvent.type(screen.getByLabelText('End time (UTC)'),'2026-12-01T10:00:00');await userEvent.type(screen.getByLabelText('Reason'),'Leave');await confirm();expect(api.post).toHaveBeenCalledWith('/crew/1/unavailability',{startTime:'2026-12-01T08:00:00Z',endTime:'2026-12-01T10:00:00Z',reason:'Leave'})})
})
