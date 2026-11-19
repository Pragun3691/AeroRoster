import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { AssignmentView, FlightCrewCoverage, FlightRequest, FlightStatus, FlightStatusRequest, FlightView, PageResponse, PaginationParams } from '../../types/api'
export interface FlightSearchParams extends PaginationParams { flightNumber?: string; status?: FlightStatus; aircraftId?: number; departureFrom?: string; departureUntil?: string }
export const flightService = {
  search: (params: FlightSearchParams) => apiClient.get<PageResponse<FlightView>>('/flights', { params }),
  get: (flightId: number) => apiClient.get<FlightView>(`/flights/${flightId}`),
  create: (request: FlightRequest) => apiClient.post<FlightView>('/flights', request),
  update: (flight: FlightView, request: FlightRequest) => apiClient.put<FlightView>(`/flights/${flight.id}`, request, { headers: expectedVersionHeaders(flight) }),
  updateStatus: (flight: FlightView, request: FlightStatusRequest) => apiClient.patch<FlightView>(`/flights/${flight.id}/status`, request, { headers: expectedVersionHeaders(flight) }),
  cancel: (flight: FlightView) => apiClient.put<FlightView>(`/flights/${flight.id}/cancellation`, {}, { headers: expectedVersionHeaders(flight) }),
  assignments: (flightId: number, params: PaginationParams & { activeOnly?: boolean }) => apiClient.get<PageResponse<AssignmentView>>(`/flights/${flightId}/assignments`, { params }),
  coverage: (flightId: number) => apiClient.get<FlightCrewCoverage>(`/flights/${flightId}/coverage`),
}
