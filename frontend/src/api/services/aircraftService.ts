import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { ActivityRequest, AircraftRequest, AircraftView, FlightView, PageResponse, PaginationParams } from '../../types/api'
export interface AircraftSearchParams extends PaginationParams { q?: string; active?: boolean; aircraftTypeId?: number }
export const aircraftService = {
  search: (params: AircraftSearchParams) => apiClient.get<PageResponse<AircraftView>>('/aircraft', { params }),
  get: (aircraftId: number) => apiClient.get<AircraftView>(`/aircraft/${aircraftId}`),
  create: (request: AircraftRequest) => apiClient.post<AircraftView>('/aircraft', request),
  update: (aircraft: AircraftView, request: AircraftRequest) => apiClient.put<AircraftView>(`/aircraft/${aircraft.id}`, request, { headers: expectedVersionHeaders(aircraft) }),
  setActivity: (aircraft: AircraftView, request: ActivityRequest) => apiClient.patch<AircraftView>(`/aircraft/${aircraft.id}/activity`, request, { headers: expectedVersionHeaders(aircraft) }),
  flights: (aircraftId: number, params: PaginationParams) => apiClient.get<PageResponse<FlightView>>(`/aircraft/${aircraftId}/flights`, { params }),
}
