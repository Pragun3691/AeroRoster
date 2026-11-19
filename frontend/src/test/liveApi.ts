import { AxiosError } from 'axios'
import { vi } from 'vitest'
import { apiClient } from '../api/client'
import type { AircraftView, CrewView, FlightView, AssignmentView, CrewQualificationView, UnavailabilityView, EligibilityDecision } from '../types/api'
export const crew: CrewView[] = [{ id: 1, name: 'Aarav Mehta', role: 'PILOT', licenseNo: 'ATPL-4821', experienceYears: 8, contactNo: '9000000000', active: true, version: 3 }, { id: 2, name: 'Nisha Rao', role: 'COPILOT', licenseNo: 'CPL-9184', experienceYears: 4, contactNo: '9000000001', active: false, version: 0 }]
export const aircraft: AircraftView[] = [{ id: 1, registration: 'VT-LIVE', aircraftTypeId: 1, model: 'A320', active: true, version: 4 }]
export const flights: FlightView[] = [{ id: 1, flightNumber: 'AR101', sourceAirportId: 1, sourceIataCode: 'DEL', destinationAirportId: 2, destinationIataCode: 'BOM', aircraftId: 1, aircraftRegistration: 'VT-LIVE', aircraftTypeId: 1, departureTime: '2026-11-19T08:12:13.123456Z', arrivalTime: '2026-11-19T10:20:00Z', status: 'SCHEDULED', version: 5 }]
export const assignments: AssignmentView[] = [{ id: 1, crewId: 1, flightId: 1, flightNumber: 'AR101', assignedRole: 'PILOT', dutyStartTime: '2026-11-19T07:12:13Z', dutyEndTime: '2026-11-19T10:50:00Z', status: 'ACTIVE', createdAt: '2026-11-18T00:00:00Z', version: 6 }]
export const held: CrewQualificationView[] = [{ id: 10, crewId: 1, qualificationId: 1, aircraftTypeId: 1, model: 'A320', requiredRole: 'PILOT', validUntil: null, version: 7 }]
export const periods: UnavailabilityView[] = [{ id: 1, crewId: 1, startTime: '2026-11-20T00:00:00Z', endTime: '2026-11-21T00:00:00Z', reason: 'Annual leave', version: 8 }]
export const policy = { preflightBuffer: 'PT1H', postflightBuffer: 'PT30M', minimumRest: 'PT10H', maximumRolling24Hours: 'PT10H', maximumRolling7Days: 'PT40H', maximumUtcDay: 'PT14H' }
export const eligible: EligibilityDecision = { eligible: true, dutyWindow: null, dutyLoad: null, policy, reasons: [], policyNotice: 'Illustrative academic settings, not legal compliance.' }
export const coverage = { flightId: 1, flightStatus: 'SCHEDULED', roles: [{ role: 'PILOT', required: 1, active: 1, missing: 0 }], minimumStaffingSatisfied: false, reasons: [{ code: 'MISSING_COPILOT', explanation: 'Need copilot' }], policyNotice: eligible.policyNotice }
export const page = <T,>(items: T[]) => ({ items, page: 0, size: 200, totalElements: items.length, totalPages: items.length ? 1 : 0, first: true, last: true })
export const failure = (status: number, code: string) => new AxiosError('failure', undefined, undefined, undefined, { status, statusText: '', headers: {}, config: { headers: {} } as never, data: { code, message: 'Backend message', fieldErrors: [{ field: 'name', message: 'Invalid field' }], eligibility: null } })
export function mockApi() {
  const get = vi.spyOn(apiClient, 'get').mockImplementation((async (url: string) => {
    let data: unknown
    if (url === '/crew') data = page(crew)
    else if (url === '/aircraft') data = page(aircraft)
    else if (url === '/flights') data = page(flights)
    else if (url === '/crew/1') data = crew[0]
    else if (url === '/aircraft/1') data = aircraft[0]
    else if (url === '/flights/1') data = flights[0]
    else if (url === '/assignments/1') data = assignments[0]
    else if (url.endsWith('/coverage')) data = coverage
    else if (url.endsWith('/conflicts')) data = { conflicts: true }
    else if (url.endsWith('/assignments')) data = page(assignments)
    else if (url.endsWith('/flights')) data = page(flights)
    else if (url.endsWith('/unavailability')) data = page(periods.filter(x => url === `/crew/${x.crewId}/unavailability`))
    else if (url.endsWith('/unavailability/1')) data = periods[0]
    else if (url === '/qualifications') data = [{ id: 1, aircraftTypeId: 1, model: 'A320', requiredRole: 'PILOT' }]
    else if (url.endsWith('/qualifications')) data = held.filter(x => url === `/crew/${x.crewId}/qualifications`)
    else if (url.endsWith('/qualifications/1')) data = held[0]
    else if (url === '/aircraft-types') data = [{ id: 1, model: 'A320', manufacturer: 'Airbus', capacity: 180, minCabinCrew: 4 }]
    else if (url === '/airports') data = [{ id: 1, iataCode: 'DEL', name: 'Delhi', city: 'Delhi', country: 'India' }, { id: 2, iataCode: 'BOM', name: 'Mumbai', city: 'Mumbai', country: 'India' }]
    else throw failure(404, 'NOT_FOUND')
    return { data }
  }) as never)
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: crew[0] } as never)
  const put = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: crew[0] } as never)
  const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: crew[0] } as never)
  const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: undefined } as never)
  return { get, post, put, patch, remove }
}
