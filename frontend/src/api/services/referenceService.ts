import { apiClient } from '../client'
import type { AircraftTypeView, AirportView, QualificationView } from '../../types/api'
export const referenceService = {
  airports: (q?: string) => apiClient.get<AirportView[]>('/airports', { params: q ? { q } : undefined }),
  airport: (airportId: number) => apiClient.get<AirportView>(`/airports/${airportId}`),
  aircraftTypes: () => apiClient.get<AircraftTypeView[]>('/aircraft-types'),
  aircraftType: (aircraftTypeId: number) => apiClient.get<AircraftTypeView>(`/aircraft-types/${aircraftTypeId}`),
  qualifications: () => apiClient.get<QualificationView[]>('/qualifications'),
  qualificationsForType: (aircraftTypeId: number) => apiClient.get<QualificationView[]>(`/aircraft-types/${aircraftTypeId}/qualifications`),
}
