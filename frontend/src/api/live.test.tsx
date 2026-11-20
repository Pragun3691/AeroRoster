import { render, screen } from '@testing-library/react'
import { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { readPages, roleLabels, utcInput, utcInstant } from './live'
import { ReferenceData } from '../components/common/ReferenceData'

afterEach(() => vi.restoreAllMocks())
describe('live reference data', () => {
  it('reads paginated resources beyond the first page', async () => {
    const read = vi.fn().mockResolvedValueOnce({ data: { items: [{ id: 1 }], last: false } }).mockResolvedValueOnce({ data: { items: [{ id: 2 }], last: true } })
    expect(await readPages(read)).toEqual([{ id: 1 }, { id: 2 }])
    expect(read).toHaveBeenNthCalledWith(2, { page: 1, size: 200 })
  })
  it('renders reference data from the documented array endpoints', async () => {
    const get = vi.spyOn(apiClient, 'get').mockImplementation(async url => ({ data: url === '/aircraft-types' ? [{ id: 1, model: 'A320', manufacturer: 'Airbus', capacity: 180, minCabinCrew: 4 }] : [] }))
    render(<ReferenceData />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByText('A320')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('/airports', { params: undefined })
    expect(get).toHaveBeenCalledWith('/qualifications')
    expect(screen.getByText('No airports available')).toBeInTheDocument()
  })
  it('shows reference failures without a demo fallback', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('offline'))
    render(<ReferenceData />)
    expect(await screen.findByRole('alert')).toHaveTextContent('network')
    expect(screen.queryByText('A320')).not.toBeInTheDocument()
  })
  it('maps COPILOT explicitly and preserves UTC seconds and fractional precision', () => {
    expect(roleLabels.COPILOT).toBe('Co-pilot')
    expect(utcInstant(utcInput('2026-11-19T05:30:12.123456Z'))).toBe('2026-11-19T05:30:12.123456Z')
  })
  it('normalizes space separator, trailing Z, UTC labels, and missing seconds to ISO-8601 UTC with T and Z', () => {
    expect(utcInstant('2026-12-25 06:00:00')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('2026-12-25 06:00:00 UTC')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('2026-12-25T06:00:00Z')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('2026-12-25T06:00')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('2026-12-25 06:00')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('  2026-12-25T06:00:00  ')).toBe('2026-12-25T06:00:00Z')
    expect(utcInstant('')).toBe('')
  })
})
