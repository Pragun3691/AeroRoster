import { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { normalizeApiError } from './errors'
import { assignmentService } from './services/assignmentService'
import { crewService } from './services/crewService'
import { unavailabilityService } from './services/unavailabilityService'
import { expectedVersion, expectedVersionHeaders } from './versioning'

const responseError = (status: number, code: string) => new AxiosError('failed', undefined, undefined, undefined, { status, statusText: '', headers: {}, config: { headers: {} } as never, data: { code, message: 'Readable backend message', timestamp: '2026-11-19T00:00:00Z', path: '/api/v1/test', fieldErrors: [{ field: 'name', message: 'required' }], eligibility: null } })

afterEach(() => vi.restoreAllMocks())
describe('API foundation', () => {
  it('uses the configurable local API base URL and JSON defaults', () => {
    expect(apiClient.defaults.baseURL).toMatch(/api\/v1$/)
    expect(apiClient.defaults.headers.common.Accept).toContain('application/json')
  })
  it('normalizes validation, not-found, stale conflict, business conflict, precondition, network, timeout, and malformed responses', () => {
    expect(normalizeApiError(responseError(400, 'VALIDATION_ERROR'))).toMatchObject({ kind: 'validation', fieldErrors: [{ field: 'name' }] })
    expect(normalizeApiError(responseError(404, 'NOT_FOUND')).kind).toBe('not-found')
    expect(normalizeApiError(responseError(409, 'STALE_VERSION')).kind).toBe('stale-version')
    expect(normalizeApiError(responseError(409, 'BUSINESS_CONFLICT'))).toMatchObject({ kind: 'conflict', code: 'BUSINESS_CONFLICT' })
    expect(normalizeApiError(responseError(428, 'PRECONDITION_REQUIRED')).kind).toBe('precondition')
    expect(normalizeApiError(new AxiosError('offline')).kind).toBe('network')
    expect(normalizeApiError(new AxiosError('slow', 'ECONNABORTED')).kind).toBe('timeout')
    expect(normalizeApiError(responseError(500, 'INTERNAL_ERROR')).kind).toBe('server')
  })
  it('constructs and validates expected-version headers', () => {
    expect(expectedVersion(12, 3)).toBe('12:3')
    expect(expectedVersionHeaders({ id: 12, version: 3 })).toEqual({ 'X-Expected-Version': '12:3' })
    expect(() => expectedVersion(0, 1)).toThrow(/positive resource id/i)
  })
  it('constructs scoped service endpoints, query params, and headers only for conditional mutations', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: {} } as never)
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: {} } as never)
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} } as never)
    await crewService.search({ q: 'Mehta', active: true, page: 1, size: 25 })
    await unavailabilityService.update(7, { id: 9, crewId: 7, startTime: '2026-11-19T10:00:00Z', endTime: '2026-11-19T11:00:00Z', reason: 'Leave', version: 4 }, { startTime: '2026-11-19T10:00:00Z', endTime: '2026-11-19T11:00:00Z', reason: 'Leave' })
    await assignmentService.create({ crewId: 7, flightId: 8, assignedRole: 'PILOT' })
    expect(get).toHaveBeenCalledWith('/crew', { params: { q: 'Mehta', active: true, page: 1, size: 25 } })
    expect(put).toHaveBeenCalledWith('/crew/7/unavailability/9', expect.anything(), { headers: { 'X-Expected-Version': '9:4' } })
    expect(post).toHaveBeenCalledWith('/assignments', { crewId: 7, flightId: 8, assignedRole: 'PILOT' })
  })
})
