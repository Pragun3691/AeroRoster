import { render,screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter,Route,Routes } from 'react-router-dom'
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest'
import { UnavailabilityManagementPage } from './UnavailabilityManagementPage'
import { failure,mockApi } from '../../test/liveApi'
let api:ReturnType<typeof mockApi>;beforeEach(()=>{api=mockApi()});afterEach(()=>vi.restoreAllMocks())
const directory=()=>render(<MemoryRouter><UnavailabilityManagementPage/></MemoryRouter>)
const confirm=async()=>{await userEvent.click(screen.getByText('Review operation'));await userEvent.click(screen.getByText('Confirm operation'))}
describe('Live unavailability',()=>{
 it('renders actual crew periods and UTC timestamps',async()=>{directory();expect(await screen.findByText('Annual leave')).toBeInTheDocument();expect(screen.getByText('2026-11-20 00:00:00 UTC')).toBeInTheDocument()})
 it('filters crew, dates, search and sort',async()=>{directory();await screen.findByText('Annual leave');await userEvent.selectOptions(screen.getByLabelText('Crew member'),'2');expect(screen.getByText('No records match')).toBeInTheDocument();await userEvent.selectOptions(screen.getByLabelText('Crew member'),'ALL');await userEvent.selectOptions(screen.getByLabelText('Sort by'),'end');await userEvent.type(screen.getByLabelText('Start date'),'2026-11-22');expect(screen.getByText('No records match')).toBeInTheDocument()})
 it('validates required creation fields',async()=>{directory();await screen.findByText('Annual leave');await userEvent.click(screen.getByText('Add unavailability'));await userEvent.click(screen.getByText('Review operation'));expect(api.post).not.toHaveBeenCalled();expect(screen.getByLabelText('Reason')).toBeRequired()})
 it('preserves editable data and sends UTC request with original version',async()=>{directory();await screen.findByText('Annual leave');await userEvent.click(screen.getByRole('button',{name:'Edit'}));expect(screen.getByLabelText('Reason')).toHaveValue('Annual leave');await confirm();expect(api.put).toHaveBeenCalledWith('/crew/1/unavailability/1',{startTime:'2026-11-20T00:00:00Z',endTime:'2026-11-21T00:00:00Z',reason:'Annual leave'},{headers:{'X-Expected-Version':'1:8'}})})
 it('displays actual conflict endpoint result with scoped limitations',async()=>{render(<MemoryRouter initialEntries={['/unavailability/1']}><Routes><Route path="/unavailability/:unavailabilityId" element={<UnavailabilityManagementPage/>}/></Routes></MemoryRouter>);await userEvent.click(await screen.findByText('Check period overlap'));expect(await screen.findByText('Overlap reported')).toBeInTheDocument();expect(api.get).toHaveBeenCalledWith('/crew/1/unavailability/conflicts',{params:{start:'2026-11-20T00:00:00Z',end:'2026-11-21T00:00:00Z'}})})
 it('confirms removal with period identity and handles empty 204',async()=>{directory();await screen.findByText('Annual leave');await userEvent.click(screen.getByText('Review removal'));expect(api.remove).not.toHaveBeenCalled();await confirm();expect(api.remove).toHaveBeenCalledWith('/crew/1/unavailability/1',{headers:{'X-Expected-Version':'1:8'}})})
 it('keeps input and surfaces active-duty business conflicts',async()=>{directory();await screen.findByText('Annual leave');await userEvent.click(screen.getByRole('button',{name:'Edit'}));api.put.mockRejectedValue(failure(409,'BUSINESS_CONFLICT'));await confirm();expect(await screen.findByRole('alert')).toHaveTextContent('conflict');expect(screen.getByLabelText('Reason')).toHaveValue('Annual leave')})
})
