import { render,screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { mockApi,page } from '../test/liveApi'
let api:ReturnType<typeof mockApi>;beforeEach(()=>{api=mockApi()});afterEach(()=>vi.restoreAllMocks())
const dashboard=()=>render(<MemoryRouter><DashboardPage/></MemoryRouter>)
describe('Live dashboard',()=>{
 it('loads real resource metrics and marks unsupported metrics unavailable',async()=>{dashboard();expect(await screen.findByText('Total crew')).toBeInTheDocument();expect(screen.getByText('Active crew')).toBeInTheDocument();expect(screen.getByText(/Unavailable: the frozen API has no activity feed/)).toBeInTheDocument();expect(screen.queryByText('31')).not.toBeInTheDocument()})
 it('renders backend coverage alerts and filters actual flights',async()=>{dashboard();expect(await screen.findByRole('table',{name:'Flight operations'})).toBeInTheDocument();expect(screen.getByText('AR101 coverage incomplete')).toBeInTheDocument();await userEvent.selectOptions(screen.getByLabelText('Flight view'),'attention');expect(screen.getByText('AR101')).toBeInTheDocument()})
 it('renders empty backend collections without invented records',async()=>{api.get.mockImplementation((async(url:string)=>({data:['/crew','/flights','/aircraft'].includes(url)?page([]):[]})) as never);dashboard();expect(await screen.findByText('No flights match this view')).toBeInTheDocument();expect(screen.queryByText('AR101')).not.toBeInTheDocument()})
})
