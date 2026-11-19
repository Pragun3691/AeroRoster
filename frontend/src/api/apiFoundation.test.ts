import { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { normalizeApiError } from './errors'
import { assignmentService } from './services/assignmentService'
import { crewService } from './services/crewService'
import { aircraftService } from './services/aircraftService'
import { flightService } from './services/flightService'
import { qualificationService } from './services/qualificationService'
import { crew, aircraft, flights, held, assignments, periods } from '../test/liveApi'
import { unavailabilityService } from './services/unavailabilityService'
import { expectedVersion, expectedVersionHeaders } from './versioning'

const responseError = (status: number, code: string) => new AxiosError('failed', undefined, undefined, undefined, { status, statusText: '', headers: {}, config: { headers: {} } as never, data: { code, message: 'Readable backend message', timestamp: '2026-11-19T00:00:00Z', path: '/api/v1/test', fieldErrors: [{ field: 'name', message: 'required' }], eligibility: null } })

afterEach(() => vi.restoreAllMocks())
describe('API foundation', () => {
  it('uses the relative same-origin API base URL and JSON defaults', () => {
    expect(apiClient.defaults.baseURL).toBe('/api/v1')
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

it('uses the original target snapshot for all thirteen protected service operations', async () => {
 const put=vi.spyOn(apiClient,'put').mockResolvedValue({data:{}} as never),patch=vi.spyOn(apiClient,'patch').mockResolvedValue({data:{}} as never),post=vi.spyOn(apiClient,'post').mockResolvedValue({data:{}} as never),remove=vi.spyOn(apiClient,'delete').mockResolvedValue({data:undefined} as never)
 await crewService.update(crew[0],{name:'Edited',role:'PILOT',licenseNo:'ABC',experienceYears:1,contactNo:'9000000000'})
 await crewService.setActivity(crew[0],{active:false})
 await aircraftService.update(aircraft[0],{registration:'VT-NEW',aircraftTypeId:1})
 await aircraftService.setActivity(aircraft[0],{active:false})
 await flightService.update(flights[0],{flightNumber:'AR101',sourceAirportId:1,destinationAirportId:2,aircraftId:1,departureTime:flights[0].departureTime,arrivalTime:flights[0].arrivalTime})
 await flightService.updateStatus(flights[0],{status:'COMPLETED'})
 await flightService.cancel(flights[0])
 await qualificationService.updateExpiry(1,held[0],{validUntil:null})
 await qualificationService.revoke(1,held[0])
 await assignmentService.cancel(assignments[0])
 await assignmentService.reassign(assignments[0])
 await unavailabilityService.update(1,periods[0],{startTime:periods[0].startTime,endTime:periods[0].endTime,reason:'Updated'})
 await unavailabilityService.remove(1,periods[0])
 for(const [spy,path,token] of [[put,'/crew/1','1:3'],[patch,'/crew/1/activity','1:3'],[put,'/aircraft/1','1:4'],[patch,'/aircraft/1/activity','1:4'],[put,'/flights/1','1:5'],[patch,'/flights/1/status','1:5'],[put,'/flights/1/cancellation','1:5'],[put,'/crew/1/qualifications/1/expiry','10:7'],[put,'/assignments/1/cancellation','1:6'],[post,'/assignments/1/reassignments','1:6'],[put,'/crew/1/unavailability/1','1:8']] as const) expect(spy).toHaveBeenCalledWith(path,expect.anything(),{headers:{'X-Expected-Version':token}})
 expect(remove).toHaveBeenCalledWith('/crew/1/qualifications/1',{headers:{'X-Expected-Version':'10:7'}})
 expect(remove).toHaveBeenCalledWith('/crew/1/unavailability/1',{headers:{'X-Expected-Version':'1:8'}})
})
