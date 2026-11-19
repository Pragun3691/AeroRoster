import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { PageResponse, PaginationParams, UnavailabilityConflictView, UnavailabilityRequest, UnavailabilityView } from '../../types/api'
export const unavailabilityService = {
  list: (crewId: number, params: PaginationParams) => apiClient.get<PageResponse<UnavailabilityView>>(`/crew/${crewId}/unavailability`, { params }),
  get: (crewId: number, periodId: number) => apiClient.get<UnavailabilityView>(`/crew/${crewId}/unavailability/${periodId}`),
  create: (crewId: number, request: UnavailabilityRequest) => apiClient.post<UnavailabilityView>(`/crew/${crewId}/unavailability`, request),
  update: (crewId: number, period: UnavailabilityView, request: UnavailabilityRequest) => apiClient.put<UnavailabilityView>(`/crew/${crewId}/unavailability/${period.id}`, request, { headers: expectedVersionHeaders(period) }),
  remove: (crewId: number, period: UnavailabilityView) => apiClient.delete<void>(`/crew/${crewId}/unavailability/${period.id}`, { headers: expectedVersionHeaders(period) }),
  conflicts: (crewId: number, start: string, end: string) => apiClient.get<UnavailabilityConflictView>(`/crew/${crewId}/unavailability/conflicts`, { params: { start, end } }),
}
