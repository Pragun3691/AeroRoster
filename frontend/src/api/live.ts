import { aircraftService } from './services/aircraftService'
import { crewService } from './services/crewService'
import { flightService } from './services/flightService'
import { referenceService } from './services/referenceService'
import type { PageResponse, PaginationParams } from '../types/api'

/** Read every page: a directory or metric must never silently use only page zero. */
export async function readPages<T>(read: (params: PaginationParams) => Promise<{ data: PageResponse<T> }>): Promise<T[]> {
  const items: T[] = []
  for (let page = 0; ; page++) {
    const { data } = await read({ page, size: 200 })
    items.push(...data.items)
    if (data.last) return items
  }
}
export const readCrew = () => readPages(params => crewService.search(params))
export const readAircraft = () => readPages(params => aircraftService.search(params))
export const readFlights = () => readPages(params => flightService.search(params))
export async function readReferences() {
  const [airports, types, requirements] = await Promise.all([
    referenceService.airports(), referenceService.aircraftTypes(), referenceService.qualifications(),
  ])
  return { airports: airports.data, types: types.data, requirements: requirements.data }
}
export const roleLabels = { PILOT: 'Pilot', COPILOT: 'Co-pilot', CABIN_CREW: 'Cabin crew' } as const
export const utcInput = (instant: string) => instant.replace(/Z$/, '')
export const utcInstant = (value: string) => `${value}Z`
export const utcDisplay = (instant: string) => `${instant.replace('T', ' ').replace(/Z$/, '')} UTC`
