import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { UnavailabilityManagementPage } from './UnavailabilityManagementPage'

const page = (path = '/unavailability') => render(
  <MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/unavailability" element={<UnavailabilityManagementPage />} />
    <Route path="/unavailability/:unavailabilityId" element={<UnavailabilityManagementPage />} />
  </Routes></MemoryRouter>,
)

describe('UnavailabilityManagementPage', () => {
  it('renders metrics, UTC times, and the contract-shaped conflict snapshot', () => {
    page()
    expect(screen.getByText('Total periods')).toBeInTheDocument()
    expect(screen.getAllByText(/UTC/).length).toBeGreaterThan(0)
    expect(screen.getByText('Conflict reported')).toBeInTheDocument()
  })

  it('searches, filters, sorts, and shows a no-results state', async () => {
    const user = userEvent.setup()
    page()
    await user.type(screen.getByPlaceholderText(/search crew/i), 'Medical')
    expect(screen.getByRole('link', { name: 'Vikram Shah' })).toBeInTheDocument()
    await user.clear(screen.getByPlaceholderText(/search crew/i))
    await user.selectOptions(screen.getByLabelText('Crew member'), '101')
    expect(screen.getByRole('link', { name: 'Aarav Mehta' })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Sort by'), 'crew')
    fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-11-01' } })
    expect(screen.getByText(/No unavailability records match/i)).toBeInTheDocument()
  })

  it('renders detail and unknown-record states', () => {
    const detail = page('/unavailability/801')
    expect(screen.getByText('Period details')).toBeInTheDocument()
    detail.unmount()
    page('/unavailability/bad')
    expect(screen.getByText('Unavailability record unavailable')).toBeInTheDocument()
  })

  it('validates an edit when the end time does not follow the start time', async () => {
    const user = userEvent.setup()
    page()

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[0])
    fireEvent.change(screen.getByLabelText('Start time (UTC)'), { target: { value: '2026-11-22T10:00' } })
    fireEvent.change(screen.getByLabelText('End time (UTC)'), { target: { value: '2026-11-22T09:00' } })
    await user.click(screen.getByRole('button', { name: /review demo update/i }))

    expect(screen.getByText('End time must be after start time.')).toBeInTheDocument()
  })

  it('validates, confirms a local addition, and confirms a removal', async () => {
    const user = userEvent.setup()
    const directory = page()
    await user.click(screen.getByRole('button', { name: /add unavailability/i }))
    await user.click(screen.getByRole('button', { name: /review demo addition/i }))
    expect(screen.getByText('Crew member is required.')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Crew member'), '101')
    fireEvent.change(screen.getByLabelText('Start time (UTC)'), { target: { value: '2026-11-26T08:00' } })
    fireEvent.change(screen.getByLabelText('End time (UTC)'), { target: { value: '2026-11-26T16:00' } })
    await user.type(screen.getByLabelText('Reason'), 'Leave')
    await user.click(screen.getByRole('button', { name: /review demo addition/i }))
    await user.click(screen.getByRole('button', { name: /confirm local demo action/i }))
    expect(screen.getByText('Total periods')).toBeInTheDocument()
    directory.unmount()
    page('/unavailability/801')
    await user.click(screen.getByRole('button', { name: /review removal/i }))
    await user.click(screen.getByRole('button', { name: /confirm demo removal/i }))
    expect(screen.getByText('Unavailability record unavailable')).toBeInTheDocument()
  })
})
