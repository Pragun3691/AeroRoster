export type CrewRole = 'PILOT' | 'CO_PILOT' | 'CABIN_CREW'
export type FlightStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export interface ApiError { code: string; message: string; timestamp: string; path: string; fieldErrors: Array<{ field: string; message: string }>; eligibility: unknown | null }
export interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface VersionedResource { id: number; version: number }
