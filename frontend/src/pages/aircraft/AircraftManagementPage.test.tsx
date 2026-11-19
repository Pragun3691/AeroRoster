import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AircraftManagementPage } from './AircraftManagementPage'
import { failure, mockApi } from '../../test/liveApi'
let api: ReturnType<typeof mockApi>
beforeEach(() => { api=mockApi() }); afterEach(() => vi.restoreAllMocks())
const directory=() => render(<MemoryRouter><AircraftManagementPage /></MemoryRouter>)
describe('Live aircraft management', () => {
 it('loads fleet and read-only type fields', async () => { directory(); expect(await screen.findByText('VT-LIVE')).toBeInTheDocument(); expect(screen.getByText('180')).toBeInTheDocument() })
 it('filters search, type and status with an empty state', async () => { directory(); await screen.findByText('VT-LIVE'); await userEvent.selectOptions(screen.getByLabelText('Status'),'false'); expect(screen.getByText('No records match')).toBeInTheDocument(); await userEvent.selectOptions(screen.getByLabelText('Status'),'true'); await userEvent.selectOptions(screen.getByLabelText('Aircraft type'),'1'); await userEvent.type(screen.getByPlaceholderText('Search registration or model'),'absent'); expect(screen.queryByText('VT-LIVE')).not.toBeInTheDocument() })
 it('requires registration and type on add', async () => { directory(); await screen.findByText('VT-LIVE'); await userEvent.click(screen.getByText('Add aircraft')); await userEvent.click(screen.getByText('Review operation')); expect(api.post).not.toHaveBeenCalled(); expect(screen.getByLabelText('Registration')).toBeRequired() })
 it('preserves edit values, sends original version and refreshes', async () => { directory(); await screen.findByText('VT-LIVE'); await userEvent.click(screen.getByRole('button', { name: 'Edit' })); expect(screen.getByLabelText('Registration')).toHaveValue('VT-LIVE'); await userEvent.click(screen.getByText('Review operation')); await userEvent.click(screen.getByText('Confirm operation')); expect(api.put).toHaveBeenCalledWith('/aircraft/1',{registration:'VT-LIVE',aircraftTypeId:1},{headers:{'X-Expected-Version':'1:4'}}); await waitFor(() => expect(api.get.mock.calls.filter(([u])=>u==='/aircraft').length).toBe(2)) })
 it('renders details and actual associated flights', async () => { render(<MemoryRouter initialEntries={['/aircraft/1']}><Routes><Route path="/aircraft/:aircraftId" element={<AircraftManagementPage />} /></Routes></MemoryRouter>); expect(await screen.findByText('Associated flights')).toBeInTheDocument(); expect(screen.getByText('AR101')).toBeInTheDocument() })
 it('retains a rejected draft on stale version', async () => { directory(); await screen.findByText('VT-LIVE'); await userEvent.click(screen.getByRole('button', { name: 'Edit' })); api.put.mockRejectedValue(failure(409,'STALE_VERSION')); await userEvent.click(screen.getByText('Review operation')); await userEvent.click(screen.getByText('Confirm operation')); expect(await screen.findByRole('alert')).toHaveTextContent('stale-version'); expect(screen.getByLabelText('Registration')).toHaveValue('VT-LIVE'); expect(screen.getByText('Review operation')).toBeDisabled() })
})
