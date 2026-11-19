import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { CrewQualificationView, ExpiryUpdateRequest, QualificationGrantRequest } from '../../types/api'
export const qualificationService = {
  list: (crewId: number) => apiClient.get<CrewQualificationView[]>(`/crew/${crewId}/qualifications`),
  get: (crewId: number, qualificationId: number) => apiClient.get<CrewQualificationView>(`/crew/${crewId}/qualifications/${qualificationId}`),
  grant: (crewId: number, request: QualificationGrantRequest) => apiClient.post<CrewQualificationView>(`/crew/${crewId}/qualifications`, request),
  updateExpiry: (crewId: number, heldQualification: CrewQualificationView, request: ExpiryUpdateRequest) => apiClient.put<CrewQualificationView>(`/crew/${crewId}/qualifications/${heldQualification.qualificationId}/expiry`, request, { headers: expectedVersionHeaders(heldQualification) }),
  revoke: (crewId: number, heldQualification: CrewQualificationView) => apiClient.delete<void>(`/crew/${crewId}/qualifications/${heldQualification.qualificationId}`, { headers: expectedVersionHeaders(heldQualification) }),
}
