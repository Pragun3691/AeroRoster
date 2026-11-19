import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { ActivityRequest, AssignmentView, BackendCrewRole, CrewRequest, CrewView, PageResponse, PaginationParams } from '../../types/api'
export interface CrewSearchParams extends PaginationParams { q?: string; role?: BackendCrewRole; active?: boolean }
export const crewService = {
  search: (params: CrewSearchParams) => apiClient.get<PageResponse<CrewView>>('/crew', { params }),
  get: (crewId: number) => apiClient.get<CrewView>(`/crew/${crewId}`),
  create: (request: CrewRequest) => apiClient.post<CrewView>('/crew', request),
  update: (crew: CrewView, request: CrewRequest) => apiClient.put<CrewView>(`/crew/${crew.id}`, request, { headers: expectedVersionHeaders(crew) }),
  setActivity: (crew: CrewView, request: ActivityRequest) => apiClient.patch<CrewView>(`/crew/${crew.id}/activity`, request, { headers: expectedVersionHeaders(crew) }),
  assignments: (crewId: number, params: PaginationParams & { activeOnly?: boolean }) => apiClient.get<PageResponse<AssignmentView>>(`/crew/${crewId}/assignments`, { params }),
}
