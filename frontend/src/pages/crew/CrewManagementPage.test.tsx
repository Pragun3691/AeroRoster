import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CrewManagementPage } from './CrewManagementPage'
import { crew, failure, mockApi } from '../../test/liveApi'
let api: ReturnType<typeof mockApi>
beforeEach(() => { api = mockApi() })
afterEach(() => vi.restoreAllMocks())
const directory = () => render(<MemoryRouter><CrewManagementPage /></MemoryRouter>)
const edit = async () => { directory(); await screen.findByText('Aarav Mehta'); await userEvent.click(screen.getAllByRole('button', { name: 'Edit' })[0]) }
const confirm = async () => { await userEvent.click(screen.getByRole('button', { name: 'Review operation' })); await userEvent.click(screen.getByRole('button', { name: 'Confirm operation' })) }
describe('Live crew management', () => {
  it('loads database records and maps backend role', async () => { directory(); expect(screen.getByRole('status')).toHaveTextContent('Loading'); expect(await screen.findByText('Nisha Rao')).toBeInTheDocument(); expect(screen.getAllByText('Co-pilot').length).toBeGreaterThan(0) })
  it('searches licence and filters role/status', async () => { directory(); await screen.findByText('Aarav Mehta'); await userEvent.type(screen.getByPlaceholderText('Search name or licence'), '9184'); expect(screen.queryByText('Aarav Mehta')).not.toBeInTheDocument(); await userEvent.clear(screen.getByPlaceholderText('Search name or licence')); await userEvent.selectOptions(screen.getByLabelText('Role'), 'COPILOT'); await userEvent.selectOptions(screen.getByLabelText('Status'), 'false'); expect(screen.getByText('Nisha Rao')).toBeInTheDocument() })
  it('shows empty search results', async () => { directory(); await screen.findByText('Aarav Mehta'); await userEvent.type(screen.getByPlaceholderText('Search name or licence'), 'absent'); expect(screen.getByText('No records match')).toBeInTheDocument() })
  it('requires add fields and does not submit invalid forms', async () => { directory(); await userEvent.click(screen.getByText('Add crew')); await userEvent.click(screen.getByText('Review operation')); expect(api.post).not.toHaveBeenCalled(); expect(screen.getByLabelText('Name')).toBeRequired() })
  it('preserves loaded edit values and original version through confirmation', async () => { await edit(); expect(screen.getByLabelText('Name')).toHaveValue('Aarav Mehta'); await userEvent.clear(screen.getByLabelText('Name')); await userEvent.type(screen.getByLabelText('Name'), 'Edited'); await confirm(); expect(api.put).toHaveBeenCalledWith('/crew/1', { name: 'Edited', role: 'PILOT', licenseNo: crew[0].licenseNo, experienceYears: 8, contactNo: crew[0].contactNo }, { headers: { 'X-Expected-Version': '1:3' } }); await waitFor(() => expect(api.get.mock.calls.filter(([u]) => u === '/crew').length).toBe(2)) })
  it('renders profile qualification and assignment history', async () => { render(<MemoryRouter initialEntries={['/crew/1']}><Routes><Route path="/crew/:crewId" element={<CrewManagementPage />} /></Routes></MemoryRouter>); expect(await screen.findByText('Personnel record')).toBeInTheDocument(); expect(screen.getByText('Qualification summary')).toBeInTheDocument(); expect(screen.getByText('Assignment history')).toBeInTheDocument() })
  it.each([[400,'VALIDATION_ERROR','validation'],[404,'NOT_FOUND','not-found'],[409,'BUSINESS_CONFLICT','conflict'],[409,'STALE_VERSION','stale-version'],[428,'PRECONDITION_REQUIRED','precondition'],[500,'INTERNAL_ERROR','server']])('displays %s %s without losing the draft', async (status,code,kind) => { await edit(); api.put.mockRejectedValue(failure(Number(status),String(code))); await confirm(); expect(await screen.findByRole('alert')).toHaveTextContent(String(kind)); expect(screen.getByLabelText('Name')).toHaveValue('Aarav Mehta'); if(code === 'STALE_VERSION') expect(screen.getByText('Review operation')).toBeDisabled() })
})
