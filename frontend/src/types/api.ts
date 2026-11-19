/** Existing demo UI enum; deliberately retained until a later API migration maps it. */
export type CrewRole = 'PILOT' | 'CO_PILOT' | 'CABIN_CREW'
/** Frozen OpenAPI 1.1.0 enum used by API service contracts. */
export type BackendCrewRole = 'PILOT' | 'COPILOT' | 'CABIN_CREW'
export type FlightStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type AssignmentStatus = 'ACTIVE' | 'CANCELLED'
export type SchedulingReasonCode = 'CREW_NOT_FOUND' | 'FLIGHT_NOT_FOUND' | 'CREW_INACTIVE' | 'FLIGHT_NOT_SCHEDULED' | 'AIRCRAFT_INACTIVE' | 'ROLE_MISMATCH' | 'MISSING_QUALIFICATION' | 'QUALIFICATION_EXPIRED' | 'INVALID_DUTY_WINDOW' | 'DUPLICATE_ACTIVE_ASSIGNMENT' | 'FLIGHT_ROLE_FILLED' | 'CREW_UNAVAILABLE' | 'CREW_TIME_CONFLICT' | 'INSUFFICIENT_REST' | 'ROLLING_24_HOUR_LIMIT' | 'ROLLING_7_DAY_LIMIT' | 'UTC_DAILY_DUTY_LIMIT' | 'MISSING_PILOT' | 'MISSING_COPILOT' | 'MISSING_CABIN_CREW' | 'EXCESS_PILOTS' | 'EXCESS_COPILOTS'
export interface VersionedResource { id: number; version: number }
export interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface ApiFieldError { field: string; message: string }
export interface ApiError { code: string; message: string; timestamp: string; path: string; fieldErrors: ApiFieldError[]; eligibility: EligibilityDecision | null }
export interface CrewRequest { name: string; role: BackendCrewRole; licenseNo: string; experienceYears: number; contactNo: string }
export interface CrewView extends VersionedResource { name: string; role: BackendCrewRole; licenseNo: string; experienceYears: number; contactNo: string; active: boolean }
export interface AircraftRequest { registration: string; aircraftTypeId: number }
export interface AircraftView extends VersionedResource { registration: string; aircraftTypeId: number; model: string; active: boolean }
export interface AircraftTypeView { id: number; model: string; manufacturer: string; capacity: number; minCabinCrew: number }
export interface AirportView { id: number; iataCode: string; name: string; city: string; country: string }
export interface QualificationView { id: number; aircraftTypeId: number; model: string; requiredRole: BackendCrewRole }
export interface CrewQualificationView extends VersionedResource { crewId: number; qualificationId: number; aircraftTypeId: number; model: string; requiredRole: BackendCrewRole; validUntil: string | null }
export interface QualificationGrantRequest { qualificationId: number; validUntil: string | null }
export interface ExpiryUpdateRequest { validUntil: string | null }
export interface FlightRequest { flightNumber: string; sourceAirportId: number; destinationAirportId: number; aircraftId: number; departureTime: string; arrivalTime: string }
export interface FlightView extends VersionedResource { flightNumber: string; sourceAirportId: number; sourceIataCode: string; destinationAirportId: number; destinationIataCode: string; aircraftId: number; aircraftRegistration: string; aircraftTypeId: number; departureTime: string; arrivalTime: string; status: FlightStatus }
export interface AssignmentRequest { crewId: number; flightId: number; assignedRole: BackendCrewRole }
export interface AssignmentView extends VersionedResource { crewId: number; flightId: number; flightNumber: string; assignedRole: BackendCrewRole; dutyStartTime: string; dutyEndTime: string; status: AssignmentStatus; createdAt: string }
export interface DutyWindow { start: string; end: string }
export interface DutyLoad { maximumRolling24Hours: string; maximumRolling7Days: string; maximumUtcDay: string }
export interface SchedulingPolicy { preflightBuffer: string; postflightBuffer: string; minimumRest: string; maximumRolling24Hours: string; maximumRolling7Days: string; maximumUtcDay: string }
export interface SchedulingReason { code: SchedulingReasonCode; explanation: string }
export interface EligibilityDecision { eligible: boolean; dutyWindow: DutyWindow | null; dutyLoad: DutyLoad | null; reasons: SchedulingReason[]; policy: SchedulingPolicy; policyNotice: string }
export interface RoleCoverage { role: BackendCrewRole; required: number; active: number; missing: number }
export interface FlightCrewCoverage { flightId: number; flightStatus: FlightStatus; roles: RoleCoverage[]; minimumStaffingSatisfied: boolean; reasons: SchedulingReason[]; policyNotice: string }
export interface UnavailabilityRequest { startTime: string; endTime: string; reason: string }
export interface UnavailabilityView extends VersionedResource { crewId: number; startTime: string; endTime: string; reason: string }
export interface UnavailabilityConflictView { conflicts: boolean }
export interface ActivityRequest { active: boolean }
export interface FlightStatusRequest { status: FlightStatus }
export interface PaginationParams { page?: number; size?: number }
