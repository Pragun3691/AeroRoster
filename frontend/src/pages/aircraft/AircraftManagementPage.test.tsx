import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AircraftManagementPage } from './AircraftManagementPage'
const directory = () => render(<MemoryRouter><AircraftManagementPage /></MemoryRouter>)
describe('Aircraft management', () => {
  it('renders directory and type reference', () => { directory(); expect(screen.getByRole('heading', { name: 'Aircraft directory' })).toBeInTheDocument(); expect(screen.getByText('VT-AXR')).toBeInTheDocument(); expect(screen.getByText('Aircraft type reference')).toBeInTheDocument() })
  it('searches, filters and sorts', async () => { const user=userEvent.setup(); directory(); await user.type(screen.getByPlaceholderText('Search registration or model'),'ARJ'); expect(screen.getByText('VT-ARJ')).toBeInTheDocument(); expect(screen.queryByText('VT-AXR')).not.toBeInTheDocument() })
  it('filters active state and no results', async () => { const user=userEvent.setup(); directory(); const selects=screen.getAllByRole('combobox'); await user.selectOptions(selects[1],'INACTIVE'); expect(screen.getByText('VT-ARJ')).toBeInTheDocument(); await user.type(screen.getByPlaceholderText('Search registration or model'),'none'); expect(screen.getByText('No aircraft records match')).toBeInTheDocument() })
  it('validates add and edit forms', async () => { const user=userEvent.setup(); directory(); await user.click(screen.getByRole('button',{name:/add aircraft/i})); await user.click(screen.getByRole('button',{name:/validate aircraft/i})); expect(screen.getByText('Registration is required.')).toBeInTheDocument(); expect(screen.getByText('Aircraft type is required.')).toBeInTheDocument() })
  it('preserves record values in edit', async () => { const user=userEvent.setup(); directory(); await user.click(screen.getAllByRole('button',{name:'Edit'})[0]); expect(screen.getByDisplayValue('VT-ARC')).toBeInTheDocument(); await user.clear(screen.getByDisplayValue('VT-ARC')); await user.click(screen.getByRole('button',{name:/validate changes/i})); expect(screen.getByText('Registration is required.')).toBeInTheDocument() })
  it('renders aircraft profile and flight associations', () => { render(<MemoryRouter initialEntries={['/aircraft/201']}><Routes><Route path="/aircraft/:aircraftId" element={<AircraftManagementPage/>}/></Routes></MemoryRouter>); expect(screen.getByRole('heading',{name:'VT-AXR'})).toBeInTheDocument(); expect(screen.getByText('Associated scheduled flights')).toBeInTheDocument(); expect(screen.getByText('AR 218')).toBeInTheDocument() })
})
