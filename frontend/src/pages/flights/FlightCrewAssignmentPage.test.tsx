import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FlightCrewAssignmentPage } from './FlightCrewAssignmentPage'
import { eligible, mockApi } from '../../test/liveApi'

let api: ReturnType<typeof mockApi>
beforeEach(() => {
  api = mockApi()
})
afterEach(() => {
  vi.restoreAllMocks()
})

const renderPage = (flightId = 1) =>
  render(
    <MemoryRouter initialEntries={[`/flights/${flightId}/assign`]}>
      <Routes>
        <Route path="/flights/:flightId/assign" element={<FlightCrewAssignmentPage />} />
      </Routes>
    </MemoryRouter>
  )

describe('Guided flight crew assignment workflow', () => {
  it('loads flight details, aircraft type specs and staffing requirements', async () => {
    renderPage(1)
    expect(await screen.findByText('Guided flight crew assignment')).toBeInTheDocument()
    expect(screen.getAllByText(/AR101/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/VT-LIVE/).length).toBeGreaterThan(0)
    expect(screen.getByText('Flight crew staffing requirements & coverage')).toBeInTheDocument()
    expect(screen.getByText(/INCOMPLETE STAFFING/)).toBeInTheDocument()
    expect(screen.getAllByText('Captain / Pilot in Command (PILOT)').length).toBeGreaterThan(0)
    expect(screen.getAllByText('First Officer / Co-Pilot (COPILOT)').length).toBeGreaterThan(0)
  })

  it('renders currently assigned crew and allows cancelling active assignments', async () => {
    renderPage(1)
    expect(await screen.findByText(/Currently assigned flight crew/)).toBeInTheDocument()
    expect(screen.getAllByText('Aarav Mehta').length).toBeGreaterThan(0)

    const cancelButton = screen.getByRole('button', { name: 'Cancel assignment' })
    expect(cancelButton).toBeInTheDocument()

    await userEvent.click(cancelButton)
    expect(api.put).toHaveBeenCalledWith(
      '/assignments/1/cancellation',
      {},
      { headers: { 'X-Expected-Version': '1:6' } }
    )
    expect(await screen.findByText(/Assignment for Crew ID 1.*cancelled/)).toBeInTheDocument()
  })

  it('evaluates candidate eligibility via backend and enables assignment when eligible', async () => {
    renderPage(1)
    expect(await screen.findByText('Guided flight crew assignment')).toBeInTheDocument()

    api.post.mockResolvedValueOnce({
      data: {
        ...eligible,
        eligible: true,
        dutyWindow: { start: '2026-11-19T07:12:13Z', end: '2026-11-19T10:50:00Z' },
        dutyLoad: { maximumRolling24Hours: 'PT3H38M', maximumRolling7Days: 'PT3H38M', maximumUtcDay: 'PT3H38M' },
      },
    } as never)

    const copilotSelect = screen.getByLabelText(/Select candidate for First Officer \/ Co-Pilot/)
    expect(copilotSelect).toBeInTheDocument()
    await userEvent.selectOptions(copilotSelect, '2')

    expect(api.post).toHaveBeenCalledWith('/assignments/eligibility', {
      crewId: 2,
      flightId: 1,
      assignedRole: 'COPILOT',
    })

    expect(await screen.findByTestId('eligibility-eligible')).toBeInTheDocument()
    expect(screen.getByText(/Backend Eligibility Check Passed/)).toBeInTheDocument()

    const assignButton = screen.getByRole('button', { name: 'Assign Co-pilot' })
    expect(assignButton).toBeEnabled()

    api.post.mockResolvedValueOnce({
      data: {
        id: 2,
        crewId: 2,
        flightId: 1,
        flightNumber: 'AR101',
        assignedRole: 'COPILOT',
        dutyStartTime: '2026-11-19T07:12:13Z',
        dutyEndTime: '2026-11-19T10:50:00Z',
        status: 'ACTIVE',
        createdAt: '2026-11-20T00:00:00Z',
        version: 1,
      },
    } as never)

    await userEvent.click(assignButton)
    expect(api.post).toHaveBeenCalledWith('/assignments', {
      crewId: 2,
      flightId: 1,
      assignedRole: 'COPILOT',
    })
  })

  it('displays backend rejection reasons when candidate is ineligible and prevents assignment', async () => {
    renderPage(1)
    expect(await screen.findByText('Guided flight crew assignment')).toBeInTheDocument()

    api.post.mockResolvedValueOnce({
      data: {
        ...eligible,
        eligible: false,
        reasons: [
          {
            code: 'MISSING_QUALIFICATION',
            explanation: 'Crew lacks the aircraft-type qualification for the requested role',
          },
          {
            code: 'CREW_INACTIVE',
            explanation: 'Crew member must be active',
          },
        ],
      },
    } as never)

    const copilotSelect = screen.getByLabelText(/Select candidate for First Officer \/ Co-Pilot/)
    await userEvent.selectOptions(copilotSelect, '2')

    expect(await screen.findByTestId('eligibility-ineligible')).toBeInTheDocument()
    expect(screen.getByText(/Candidate Ineligible for this Flight/)).toBeInTheDocument()
    expect(
      screen.getByText(/Crew lacks the aircraft-type qualification for the requested role/)
    ).toBeInTheDocument()

    const assignButton = screen.getByRole('button', { name: 'Assign Co-pilot' })
    expect(assignButton).toBeDisabled()
  })
})
