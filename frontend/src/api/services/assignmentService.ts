import { apiClient } from '../client'
import { expectedVersionHeaders } from '../versioning'
import type { AssignmentRequest, AssignmentView, EligibilityDecision } from '../../types/api'
export const assignmentService = {
  eligibility: (request: AssignmentRequest) => apiClient.post<EligibilityDecision>('/assignments/eligibility', request),
  create: (request: AssignmentRequest) => apiClient.post<AssignmentView>('/assignments', request),
  get: (assignmentId: number) => apiClient.get<AssignmentView>(`/assignments/${assignmentId}`),
  cancel: (assignment: AssignmentView) => apiClient.put<AssignmentView>(`/assignments/${assignment.id}/cancellation`, {}, { headers: expectedVersionHeaders(assignment) }),
  reassign: (assignment: AssignmentView) => apiClient.post<AssignmentView>(`/assignments/${assignment.id}/reassignments`, {}, { headers: expectedVersionHeaders(assignment) }),
}
